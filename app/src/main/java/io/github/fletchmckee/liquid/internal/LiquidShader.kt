// Copyright 2025, Colin McKee
// SPDX-License-Identifier: Apache-2.0
package io.github.fletchmckee.liquid.internal

// Butler: the body of `main` is no longer Liquid's. It follows Apple's own glass, as recovered
// from QuartzCore's `glass_background_lpf` (macOS 26) and transcribed in spacedouut/liquid-glass
// (github.com/spacedouut/liquid-glass, app.js): the same steps in the same order, with the
// numbers AppKit sets on a regular NSGlassEffectView. What each step is for is said where it is.
// Liquid's signed-distance functions and its anti-aliased edge are kept as they were.
//
//
// It is one pane of glass and one rule for all of it: the picture is moved only where the
// surface curves, which is the rim, so the middle is a dead zone by itself, not by decree.
//
// Apple's blur is not the same all over the pane either. It reads a smaller, softer copy of
// the picture (a mip) for each pixel, chosen by how far in the thing it is looking at lies:
// the whole blur at the pane's middle, down to half of it a point from the edge. Butler has no
// mips, but the blur never falls below half, so two copies do: one blurred by the whole radius
// and one by half. The shader runs once over each (`level`), takes from each the share Apple's
// curve gives it, and the two are added. That is what reading between two mips is.
//
// Left out: the "bleed" of far, very blurred colour onto large panes, which wants a third and
// much softer copy, and the shadow, which Butler draws with the platform's own.
internal const val LiquidShader = """
  uniform shader content;
  uniform float2 size;
  uniform float4 cornerRadii;
  // Which copy of the picture `content` is: 0 blurred by the whole radius, 1 by half of it.
  uniform float level;
  // Pixels in a point: Apple's amounts are in points.
  uniform float density;
  // The rim: how deep it runs in from the edge, and how far the picture is pulled at the edge
  // itself (points). Apple: 20 and 60.
  uniform float refraction;
  uniform float curve;
  // How much of the outward-bent picture is laid over the last point of the rim. Apple: 0.3.
  uniform float outer;
  // Colour fringing: how far apart red and blue are read at the edge (points). 0, as Apple
  // leaves it on macOS, is none.
  uniform float dispersion;
  // The fill the face is laid under, and how thick (its alpha).
  layout(color) uniform half4 tint;
  // The face's black and white: what the darkest and the brightest thing behind become.
  uniform float2 range;
  uniform float saturation;
  // The rim highlight: how strong, and where its key light comes from (a unit vector, y down).
  uniform float edge;
  uniform float2 light;
  uniform float4 glow;

  const float AA_WIDTH_PX = 1.5;
  const half3 LUMA = half3(0.2126, 0.7152, 0.0722);

  float computeSdf(in float2 p, in float2 b, in half4 r) {
    r.xy = (p.x > 0.0) ? r.xy : r.zw; // xy is right quadrant, zw is left
    r.x = (p.y > 0.0) ? r.x : r.y; // x is bottom quadrant, y is top
    float2 q = abs(p) - b + r.x;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r.x;
  }

  // See `sdgBox` for reference:
  // https://iquilezles.org/articles/distgradfunctions2d/
  float2 computeSdfNormal(float2 pos, float2 shapeSize, half4 vr) {
    vr.xy = (pos.x > 0.0) ? vr.xy : vr.zw;
    vr.x = (pos.y > 0.0) ? vr.x : vr.y;
    float r = vr.x;
    float2 w = abs(pos) - (shapeSize - r);
    float2 s = float2(pos.x < 0.0 ? -1.0 : 1.0, pos.y < 0.0 ? -1.0 : 1.0);
    float g = max(w.x, w.y);
    float2 q = max(w, 0.0);
    float l = length(q);

    // Multiply by s to restore quadrant signs.
    return s * ((g > 0.0) ? q / l : ((w.x > w.y) ? float2(1.0, 0.0) : float2(0.0, 1.0)));
  }

  // Apple's `edgeOffset`: how far the picture is moved at a point `d` from the edge (negative
  // inside). All of `amount` at the edge, none `1 / edgeInv` in, and between the two the fall
  // of a quarter circle, so nearly all of the bend sits in the last few points before the edge.
  float edgeOffset(float d, float edgeInv, float amount) {
    float s = saturate(edgeInv * -d);
    return amount - amount * saturate(sqrt(s * (2.0 - s)));
  }

  // Apple's blur for a thing lying `x` points from the edge (negative inside), as this copy's
  // share of it. Apple's curve (BlurDistance and BlurOpacity on a regular pane): the whole
  // radius from the pane's middle line inward, falling evenly to half of it one point from the
  // edge. A radius between the half and the whole is read between the two copies, by its
  // logarithm, as between two mips.
  float share(float x, float side) {
    float thin = saturate((x + 0.5 * side) / max(0.5 * side - 1.0, 0.001));
    float whole = log2(2.0 - thin);
    return level < 0.5 ? whole : 1.0 - whole;
  }

  // One of the highlight's two lights: full where the edge faces `toward`, none from 80
  // degrees off it (Apple's cone, 160 degrees wide).
  float cone(float2 n, float2 toward) {
    float off = acos(clamp(dot(n, toward), -1.0, 1.0));
    return max(cos(min(off / 1.3962634, 1.0) * 1.5707963), 0.0);
  }

  half4 main(float2 fragCoord) {
    float minDimension = min(size.x, size.y);
    float2 center = size * 0.5;
    float2 shapeCoord = (fragCoord - center) / minDimension;
    float2 shapeSize = size / minDimension;
    float2 halfShapeSize = shapeSize * 0.5;
    half4 shapeVr = half4(cornerRadii);
    float shapeSdf = computeSdf(shapeCoord, halfShapeSize, shapeVr);

    // Using 1.5 softens jagged pixels around corners without softening the edge too much.
    float aaWidth = AA_WIDTH_PX / minDimension;
    if (shapeSdf > aaWidth) {
      return half4(0.0);
    }

    // The distance to the edge in points (negative inside), the way the edge faces there, and
    // the pane's short side in points, which Apple scales the outer bend by.
    float d = shapeSdf * minDimension / density;
    float2 n = computeSdfNormal(shapeCoord, halfShapeSize, shapeVr);
    float side = minDimension / density;

    // 1. The picture behind, pulled in at the rim: at the edge it is read from `curve` points
    //    further in, and by `refraction` points in it is where it would be anyway. The rim so
    //    shows what is under the middle of the pane, squeezed toward the edge.
    //    `part` is how much of this pixel comes from this copy of the picture: the blur is
    //    chosen by where the thing looked at lies, which is where the pull lands.
    half4 behind = content.eval(fragCoord);
    half4 seen = behind;
    float part = share(d, side);
    if (curve > 0.0 && refraction > 0.0) {
      float pull = edgeOffset(d, 1.0 / refraction, -curve);
      seen = content.eval(fragCoord + pull * density * n);
      part = share(pull + d, side);
      // The fringe, as Apple's foreground pass splits it: red read a step further out along
      // the edge's normal, blue a step further in, green where it was. The step is whole at
      // the edge and gone where the rim ends, so the flat of the pane has none.
      if (dispersion > 0.0) {
        float split = edgeOffset(d, 1.0 / refraction, dispersion);
        half4 red = content.eval(fragCoord + (pull + split) * density * n);
        half4 blue = content.eval(fragCoord + (pull - split) * density * n);
        if (red.a > 0.0 && blue.a > 0.0) {
          seen = half4(red.r, seen.g, blue.b, seen.a);
        }
      }
      // 2. Over the last point before the edge, some of the picture bent the other way: from a
      //    fifth of the pane's short side outward. A thin line of what lies just past the edge.
      if (outer > 0.0) {
        float push = edgeOffset(d, 1.0 / max(0.125 * side, 0.001), 0.2 * side);
        float2 at = clamp(fragCoord + push * density * n, float2(0.5), size - 0.5);
        // Each of the two is taken at its own blur, so they are joined by their shares.
        float mixed = outer * saturate(d + 1.0);
        float far = share(push + d, side);
        float both = (1.0 - mixed) * part + mixed * far;
        if (both > 0.0) {
          seen = (seen * ((1.0 - mixed) * part) + content.eval(at) * (mixed * far)) / both;
        }
        part = both;
      }
      if (seen.a <= 0.0) {
        seen = behind;
      }
    }
    // Nothing of this pixel is this copy's.
    if (part <= 0.0) {
      return half4(0.0);
    }
    half3 rgb = seen.rgb / max(seen.a, 0.0001);

    // 3. The face. What is behind keeps its colour but its light and dark are brought into a
    //    band, `range`: the darkest thing becomes the band's black and the brightest its white.
    //    Then the fill is laid over it. This is why the glass is never as dark or as bright as
    //    what is behind it, and why what is written on it can always be read.
    float span = range.y - range.x - 1.0;
    half3 face = rgb + span * dot(rgb, LUMA);
    face = mix(half3(dot(face, LUMA)), face, saturation);
    rgb = (1.0 - tint.a) * (face + range.x) + tint.rgb * tint.a;

    // 4. A shade less light everywhere but the last point before the edge, which is left as it
    //    is: the rim is a breath brighter than the face.
    rgb *= mix(1.0, 0.97, saturate(-(d + 1.0)));
    rgb = saturate(rgb);

    // Butler: a light inside the glass, falling off from where it was touched.
    if (glow.w > 0.0) {
      float2 away = (fragCoord - glow.xy) / max(glow.z, 1.0);
      rgb = saturate(rgb + glow.w * exp(-dot(away, away)));
    }

    // 5. The rim's highlight: a white line one point wide just inside the edge, lit by two
    //    lights opposite each other, the key and its fill. Each lights the edge that faces it
    //    and fades to nothing 80 degrees round, so the two leave a gap on either side between
    //    them. On a macOS pane the key comes from the lower right and the fill from the upper
    //    left: the upper right and the lower left of the rim are the gaps.
    if (edge > 0.0) {
      float into = saturate(-d);
      float band = 1.0 - mix(into, sqrt(into * (2.0 - into)), 0.7);
      rgb = mix(rgb, half3(1.0), saturate(band * edge * (cone(n, light) + cone(n, -light))));
    }

    // Centers the AA gradient to the edge of our shape.
    float aaAlpha = 1.0 - smoothstep(-aaWidth * 0.5, aaWidth * 0.5, shapeSdf);
    // This copy's share of the pixel; the other copy's is added to it.
    return half4(rgb, 1.0) * (aaAlpha * part);
  }
"""
