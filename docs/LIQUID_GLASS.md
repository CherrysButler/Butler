# Liquid glass

How Butler's glass is built, where its numbers come from, and what was tried and thrown away
on the way. Written for whoever touches it next, and for anyone building the same effect on
Android or in Skia.

Glass is used on the frame only, in the Lifted look: the floating nav bar, the chat's top bar
and the message box. Text that is read never sits on glass.

## Where the code is

| What | Where |
|---|---|
| The shader (all of the look) | `app/src/main/java/io/github/fletchmckee/liquid/internal/LiquidShader.kt` |
| **The numbers, written once** (`object Glass`), and the two kinds of pane made from them | `app/src/main/java/com/cherry/butler/ui/components/Glass.kt` |
| The shader as a paint, for a pane on plain ground | `.../liquid/internal/LiquidStill.android.kt`, desktop `LiquidStill.skiko.kt` |
| The app's one drop-down menu | `.../ui/components/Menu.kt` |
| The effect graph on Android | `.../liquid/internal/LiquidElement.android.kt` |
| The effect graph on the desktop (Skia) | `desktop/src/main/kotlin/io/github/fletchmckee/liquid/internal/LiquidElement.skiko.kt` |
| Live sliders for the numbers (temporary) | `.../ui/components/GlassTuner.kt` |
| Offscreen renders to compare against references | `desktop/src/test/kotlin/.../ui/GlassRefShot.kt`, `GlassBarShot.kt`, `GlassLabShot.kt`, `GlassPaneShot.kt` |

The plumbing (capturing what is behind a pane, running a shader over it) is the
[Liquid](https://github.com/FletchMcKee/liquid) library, vendored into the sources. Its
signed-distance functions and its anti-aliased edge are kept. The body of the shader is no
longer Liquid's.

## Where the look comes from

The steps and the numbers are Apple's own. macOS 26 draws its glass with a shader in
QuartzCore called `glass_background_lpf`. That shader was recovered and written out line for
line in [spacedouut/liquid-glass](https://github.com/spacedouut/liquid-glass) (`app.js`,
`QC_FS` for the shader and `qcRecipe()` for the numbers AppKit sets on a regular
`NSGlassEffectView`). Butler's shader follows those steps in the same order.

Two other sources were read and are not what Butler follows:
[arya3d/liquid-glass-webgpu](https://github.com/arya3d/liquid-glass-webgpu) scales the lookup
toward the pane's centre and adds a white ring, with no blur or tint.
[imadrahmoune.com/liquid-glass](https://imadrahmoune.com/liquid-glass/) bends a ray with
`refract()` at an index of 1.5, and says itself that it is a guess made without an Apple
device. [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass) uses the same
rim curve as Apple and was useful as a second opinion on the size of the pull (below).

## The model

It is one pane of glass with one rule for all of it: **the picture behind is moved only
where the surface curves, and the surface curves only at the rim.** The middle of a pane is a
dead zone because nothing bends there, not because it is treated as a separate layer.

All lengths below are points (dp on Android). `d` is the distance from a pixel to the pane's
edge, negative inside. `n` is the unit vector the edge faces at that pixel (the gradient of the
signed distance). `S` is the pane's short side.

### The curve everything uses

```glsl
float edgeOffset(float d, float edgeInv, float amount) {
  float s = saturate(edgeInv * -d);            // 0 at the edge, 1 at 1/edgeInv points in
  return amount - amount * saturate(sqrt(s * (2.0 - s)));
}
```

All of `amount` at the edge, none `1 / edgeInv` points in, and between the two the fall of a
quarter circle. Nearly all of the movement sits in the last few points before the edge.

### 1. The rim: the picture pulled inward

```glsl
float pull = edgeOffset(d, 1.0 / rimDepth, -rimPull);
seen = picture(fragCoord + pull * n);
```

At the edge the picture is read from `rimPull` points further in; `rimDepth` points in it is
where it would be anyway. Apple: depth 20, pull 60, the same for every size of pane.

Because the pull is larger than the depth, the rim **folds**: going inward from the edge, the
place being looked at first comes back toward the edge and then moves away again. The rim
therefore shows a flipped, squeezed copy of what lies up to `rimPull` points in. This is the
bend that reads as glass, and it is also the source of the one real problem (see "The pull").

### 2. The outer line

Over the last point before the edge, 30% of a second sample is mixed in. That sample is moved
the other way, outward, by `0.2 S` falling over `0.125 S`. It is a thin line of what lies just
past the pane.

### 3. The face

What is behind keeps its colour, but its light and dark are brought into a band, and then a
fill is laid over it:

```glsl
half3 face = rgb + (white - black - 1.0) * luma(rgb);   // light and dark into [black, white]
face = mix(half3(luma(face)), face, saturation);
rgb = (1.0 - fill.a) * (face + black) + fill.rgb * fill.a;
```

| Look | black | white | fill |
|---|---|---|---|
| Dark | 0.2 | 0.6 | black, 0.4 |
| Light, short side 64 or more | 0.5 | 1.03 | white, 0.4 |
| Light, short side under 64 | 0.85 | 1.03 | white, 0.25 |

This is why the glass is never as dark or as bright as what is behind it, and why whatever is
written on it can be read. It is the largest single part of the look. A plain tint laid over a
blur does not give it.

### 4. The hold

Everything is multiplied by 0.97 except the last point before the edge. The rim is a breath
brighter than the face.

### 5. The rim highlight

A white line one point wide just inside the edge, lit by two lights opposite each other: the
key from the lower right and its fill from the upper left.

```glsl
float cone(float2 n, float2 toward) {                 // 160 degrees wide
  float off = acos(clamp(dot(n, toward), -1.0, 1.0));
  return max(cos(min(off / radians(80.0), 1.0) * radians(90.0)), 0.0);
}
float into = saturate(-d);                             // across the one-point line
float band = 1.0 - mix(into, sqrt(into * (2.0 - into)), 0.7);
rgb = mix(rgb, white, saturate(band * 0.5 * (cone(n, key) + cone(n, -key))));
```

Each light fades to nothing 80 degrees round, so the two leave a gap 20 degrees wide on either
side between them: the upper right and the lower left of the rim. The gap is what makes it a
highlight and not an outline. It is only visible on a corner round enough to have an arc there,
which is one reason the bars are capsules.

### The blur is not the same all over

This was the last piece ported and the one that made the difference.

Apple reads a smaller, softer copy of the picture (a mip) for each pixel, chosen by where the
thing being looked at lies. With `x = pull + d` (the distance from the edge of the place the
pull lands):

```
thin   = saturate((x + 0.5 S) / (0.5 S - 1))    // 0 from the pane's middle line inward, 1 a point from the edge
radius = (1 - 0.5 * thin) * R                   // the whole radius down to half of it
```

So the blur is whole at the middle of the pane and half near the edge. It never falls below
half.

Butler has no mips. A `RenderEffect` hands the shader one picture. But a radius between half
and whole is exactly what lies between two neighbouring mips, so two copies are enough: one
blurred by `R` and one by `R / 2`. The shader runs once over each (`level` says which), takes
from each the share Apple's curve gives it, and the two results are added:

```glsl
float whole = log2(2.0 - thin);                  // log2(radius / (R / 2))
float part  = level < 0.5 ? whole : 1.0 - whole;
return half4(rgb, 1.0) * (aaAlpha * part);
```

Every step after sampling (face, hold, highlight) is a straight-line function of the colour,
so applying it to each copy and adding the results is the same as applying it once to the
mixture.

### Colour fringing

Off. Apple leaves it off on macOS. When on, red is read a step further out along `n` and blue
a step further in, the step sized by `edgeOffset` so the flat of the pane has none. It is in
the shader (`dispersion`, points at the edge) and reads as a magnifying lens more than as
glass.

## One source of truth

The numbers are written in one place, `object Glass` in `Glass.kt` (rim depth, rim pull,
outer mix, fringe, highlight, blur). The tuner starts from them and holds none of its own.
The face (black, white, fill by size and by light or dark) is `faceFor` in the same file.
Nothing else in the app repeats a number or decides for itself whether it is glass:

- `glassOn()` is whether the frame's glass runs (Depth Lifted, not on battery saver).
- `glassAvailable()` is whether surfaces are glass (the same, and a device that can run the
  shader as a paint: Android 13 on, every PC).
- `Modifier.glassSurface(state, shape)` is a pane over a moving picture: bars, the message box.
- `Modifier.glassPane(shape, lift, clip, tint)` is a pane on plain ground: cards, the search
  box, the sort bar, chips, tags, menus.

`Modifier.card()` turns into `glassPane` by itself with Depth Lifted, so every card in the app
(settings sections, bot cards, bubbles, panels) is glass without being told. A card given a
colour that means something (a warning, a custom look) keeps that colour. With Depth Flat, on
battery saver, or before Android 13, every one of these draws as it did before glass.

### A pane on plain ground

A card on the app's ground has nothing behind it to bend, so capturing and blurring the screen
for it would cost a lot and show nothing. It runs the same shader as a paint instead
(`StillGlass`), with the ground's one colour as the picture behind and `level = -1` (one copy,
no shares). The rim pull, outer line and fringe are zero, since one colour bent is the same
colour. What is left is the face and the rim highlight, which are exactly the live glass's, so
a card and a bar are visibly one material.

- Its light is at rest: cards do not turn their highlight with the phone's tilt.
- `tint` washes the pane with a colour: the accent for a chosen chip, a tag chip's own.
- A bot's tags are not panes. On a pill that small the two-light rim showed as two short
  arcs, which read as a smudge; they are a soft fill of the tag's colour.
- On light ground the rim's light is white on white, so a pane without a shadow gets a
  hairline. Panes with a shadow are told from the ground by it.
- On Android all still panes share one compiled `RuntimeShader`; each sets its numbers and
  draws at once (a draw keeps the shader as it stands when drawn). Compiling one per pane
  would be paid for every card and tag in a list.
- The ground is taken to be the theme's background. A pane over a picture (a bubble over a
  chat background) is therefore as solid as the card it replaced, not see-through.
- Most menus (`ButlerMenu`) are still panes too. They open in a window of their own, where
  the screen behind cannot be sampled. The ones drawn inside the app's own window are
  see-through: see "Glass that flows" below.

### The rim's gaps on a box

Apple lights the rim by the way an edge faces. A box's straight edge faces one way from end
to end, so a box came out lit evenly along all four sides, with only its small corner arcs
dark: the gaps that make it read as glass were hardly there. For panes on plain ground the
facing is turned toward where the point lies from the pane's middle (uniform `place`, from
`Glass.RIM_GAPS`, 1.5): the light is full at the corners that face a light and dies along
the sides on its way to the two that face neither. 0 is Apple's own, which the bars and the
message box keep.

### Glass that flows

`GlassFlow.kt`. What makes a pane liquid is how it moves, not its colour. A coloured rim was
tried three ways for the round key (a tint, a tinge, the key's own mark refracted into the
rim) and read as a button with an outline each time.

- **`GlassKey`**, the round key that makes something new: under a finger it gives like a
  drop (wider and flatter, its pull rising by up to 16), and tapped it flows open into the
  pane its choices are on. One pane throughout.
- **`GlassFlowMenu`**, Browse's Popular and Trending: the pane opens downward out of the key
  that was tapped. The key stays where it is as the pane's top row, showing what is picked,
  and only the other choices come out under it. (Opened round the key so the pick kept its
  place in the list, it flew up out of reach and jumped as it re-ordered on a new pick.)
- These panes are drawn at the app's root beside the page (`GlassOverlay`), not inside it,
  so they look through the same page the nav island does and nothing more is captured.
- Neither holds the screen. A touch elsewhere shuts the pane and still lands: a catcher laid
  over the page as its sibling shares the touch (`sharePointerInputWithSiblings`). It has to
  stay until that finger lifts; removed the moment it shut the pane, it took the page's
  touch down with it.
- `ButlerMenu` does the same on a touch screen by not taking the window's focus, hears Back
  itself, and ignores being reopened by the very touch that shut it.

### A bot card looks through its own picture

A pane on plain ground can be given a picture to look through in place of the ground
(`glassPane(picture = …)`, reached through `card(picture = …)`). A bot card gives it the bot's
own picture, so the picture's colours run out from its edges under the name and the footer,
and the card's rim bends them with the full recipe (rim depth, pull and outer line are on
again, since there is something to bend).

- The picture is a **soft copy** (`SoftPicture`, one per platform): 32 pixels wide, brought
  down by halves so every pixel counts, then blurred by 2.2 of its own pixels
  (`Glass.LEAK_WIDTH`, `Glass.LEAK_SOFT`). Drawn at card size that is a heavy blur for the
  price of a texture read. No screen capture, no blur pass, no layer.
- It is made from the picture the card has just loaded, off the main thread, so nothing is
  fetched or decoded twice. Copies are kept by picture (400 of them).
- It is laid exactly over the picture's slot in the card and **mirrored** past it, so the
  colour at the picture's bottom edge is the colour the footer starts with: no seam.
- The sharp picture is drawn over its slot as before; the glass shows above and below it.
- Cards with a creator's own colours keep those and do not leak.

### The message box blurs more as it opens

`glassSurface(opened = { 0..1 })`: the blur rises from `Glass.BLUR` (3) to `Glass.BLUR_OPEN`
(12) as the box opens with the keyboard, since words sit on it then. The tuner has it as
"Open blur".

## How it is wired

- A pane's content source is marked with `Modifier.frostSource(state)`; the pane itself is
  `Modifier.glassSurface(state, shape)`.
- **Android 13 and up:** two `RuntimeShader`s on the same source (they differ only in
  `level`). One is chained after a blur of `R`, the other after a blur of `R / 2`, and the two
  are joined with `RenderEffect.createBlendModeEffect(whole, half, BlendMode.PLUS)`.
  `createRuntimeShaderEffect` binds one input only, which is why this is two effects and a
  blend and not one shader with two children.
- **Desktop (Skia):** the same two passes, `ImageFilter.makeRuntimeShader` over
  `ImageFilter.makeBlur`, joined with `ImageFilter.makeBlend(BlendMode.PLUS, …)`.
- **Android 12:** no `RuntimeShader`. Liquid's fallback frosts and tints without the lens.
  Before Android 12 nothing can blur behind a view, so the pane is nearly solid.
- The shader is given `density` and works in points, so one recipe serves every screen.
- Panes are cut with plain round corners, even where the app's shapes are continuous
  ("squircle") ones. The shader measures a round corner; lit along one curve and cut along
  another, the rim lost its light at every corner.
- A touch lights the glass from inside under the finger and swells slim panes a little
  (`GlassTouch`). The message box's glass does neither: redrawing under the caret made it
  flicker.
- The key light turns with the phone's tilt (gravity sensor, only while the app is in front)
  or with the pointer on a PC.

## Butler's numbers

| Number | Butler | Apple (macOS, regular pane) | Why it differs |
|---|---|---|---|
| Rim depth | 20 | 20 | |
| **Rim pull** | **35** | 60 | See below |
| Outer line mix | 0.3 | 0.3 | |
| Blur (Compose radius, dp) | 3 | by size, about 2.8 to 8.4 | Chosen by eye on a phone |
| Colour fringing | 0 | 0 | |
| Highlight | 0.5 | 0.5 | |
| Face | as the table above | the same | |

Apple's blur by size, for reference: the input is `clamp(4 - (160 - S) / 42, 4/3, 4)`, the
radius in pixels is `1.6 × input × scale`, read off a mip pyramid. Worked through, that
softens about as a Compose blur of `2.1 × input` dp does (`frostFor` in `Glass.kt`).

### The pull

Apple's pull of 60 does not change with the pane's size. On a pane well over 60 tall that is
fine. Butler's bars are 56 tall. A rim that looks 60 points in from one edge of a 56-point bar
is looking at the other edge, so something scrolling out under the top edge shows up flipped
along the bottom rim as it goes. It reads as the content bouncing back, and an iPhone's tab bar
does not do it.

The pull and the bend are the same number, so there is no setting that keeps all of the bend
and none of the reflection. **35 is the sweet spot, found by eye on a real phone with the
tuner:** the bend still reads as glass, and the far edge stops showing up in the near one.
For comparison, Kyant's library uses a pull equal to the rim depth (24 and 24) on its
iPhone-style tab bar, and at most twice the depth elsewhere.

Things that do not fix it, all tried:

- Cutting the pull to about the rim depth (20). The reflection goes and so does the look.
- Blurring harder. The reflection is still there, only soft.
- Drawing the rim as a separate sharp or blurred band over a flat middle. It looks close in a
  still and is the wrong model: the band has an edge of its own, a tint of its own and a blur
  of its own, none of which come from the glass.

## Not ported

- **The bleed.** On panes over 64 points Apple mixes in a far, very blurred, outward sample
  where the face is dark, so nearby colour spills onto the glass. It wants a third, much softer
  copy of the picture.
- **The shadow.** Apple's is part of the shader and is tinted by what is under it. Butler uses
  the platform's elevation shadow.
- **Light and dark flipping.** Small Apple panes switch between a light and a dark face
  depending on what is behind them, and their symbols switch with them. Butler's face follows
  the app's look.
- **The minimum blur.** Apple captures what is behind at a quarter of the resolution, which
  puts a floor under the blur.

## What went wrong on the way

Worth reading before changing anything.

1. **A summary is not the source.** The first attempt was built from a description of the
   technique and a guessed lens (a bevel and Snell's law). It was plausible and wrong. The real
   shader was one search away and nothing like the guess. Read the code being copied.
2. **The face matters more than the lens.** Tint over blur looks milky or dirty however good
   the refraction is. The luma band under a fill is what makes it Apple's.
3. **The rim highlight has to follow the real edge.** With continuous corners on the clip and
   round corners in the shader, the highlight vanished at every corner and stayed on the
   straight edges, the exact opposite of what was wanted.
4. **Light added to bright ground overflows.** A rim light added to a pixel that is already
   near white runs past 1, and a colour past its alpha comes out as stray red, blue and yellow
   pixels. Clamp before returning.
5. **A fix that removes the complaint can remove the look.** Each of the three failed fixes
   above made the reflection go away and was rejected within a minute of being seen on the
   phone. Change one number at a time and keep a commit to go back to.
6. **Judge it on the device, moving.** Offscreen renders caught wrong tones and broken edges.
   They did not catch the reflection, which only exists when something scrolls.

## Checking it

- `GlassRefShot` renders Butler's glass over the wallpaper the reference playground uses, with
  the same panes. Run the playground (<https://spacedouut.github.io/liquid-glass/>, the query
  string takes any parameter, for example `?qClear=0&qDark=1&w=388&h=60&radius=30`) and lay the
  two side by side. Set `GLASS_REF=<wallpaper.jpg>,<out dir>`.
- `GlassBarShot` renders a capsule bar over a chat list at three scroll positions, to lay
  beside frames of a recording of an iPhone's tab bar. Set `GLASS_BAR=<out dir>`.
- The **glass tuner** (Settings, Look, under Depth) floats a panel of sliders over any screen
  and moves the glass live. It is a tool, not a feature, and should go once the numbers are
  final; its values then become constants in `Glass.kt`.
- `./gradlew :app:assembleQuick` builds the release app under the release key without R8 and
  the resource shrinker. It installs over the release app, keeps its data, and builds in well
  under a minute. It is never shipped.

## Open questions

- The numbers are macOS's. Nobody has the iPhone's. A recording of an iOS 26 tab bar shows a
  narrower rim than these numbers give and no text in it; whether iOS uses a different recipe
  or the same one with other inputs is not known.
- Butler's render comes out slightly less saturated than the reference in places. Not tracked
  down.

## Credits

- [Liquid](https://github.com/FletchMcKee/liquid) by Colin McKee, Apache-2.0: the capture and
  effect plumbing, the signed-distance functions and the anti-aliased edge. The vendored files
  keep their headers and say where Butler changed them.
- [spacedouut/liquid-glass](https://github.com/spacedouut/liquid-glass): the recovered shader
  and the measured numbers. The repository carries no licence file. Butler's shader is its own
  code following the same steps, not a copy of that file.
- [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass), Apache-2.0: read
  for its numbers, no code taken.
- Apple, for the design.
