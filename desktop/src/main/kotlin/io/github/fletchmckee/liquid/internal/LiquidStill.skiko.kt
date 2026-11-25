// Butler's addition to Liquid (not from the library): the glass shader as a paint, for a pane
// standing on ground of one colour, where there is nothing behind it to capture.
package io.github.fletchmckee.liquid.internal

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.Matrix33
import org.jetbrains.skia.SamplingMode
import androidx.compose.ui.graphics.toArgb
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.Shader

/** Whether this device can run the glass shader as a paint: every PC. */
internal val stillGlassSupported: Boolean get() = true

/** Compiled once: every pane's paint is built from it. */
private val StillEffect: RuntimeEffect by lazy { RuntimeEffect.makeForShader(LiquidShader) }

/**
 * The glass over plain ground. The same shader the live glass runs (LiquidShader.kt), given a
 * single colour for the picture behind it, so a pane on plain ground and a pane over a moving
 * picture are one glass. Nothing is captured or blurred: it is one cheap pass.
 *
 * A pane may be given a picture of its own to look through in place of the ground (a small
 * soft copy, laid over a slot of the pane and mirrored past it). There is something to bend
 * then, so the rim is given its depth and pull.
 *
 * A pane keeps one of these: [set] when its size or look changes, [shader] each time it draws.
 */
internal class StillGlass {
  private var made: Shader? = null
  private var seen: Shader? = null
  private var depth = 0f
  private var pull = 0f
  private var outer = 0f

  /** The pane looks through [picture], which lies over [slot] of it; null is the plain ground again. Before [set]. */
  fun look(picture: ImageBitmap?, slot: Rect, depth: Float, pull: Float, outer: Float) {
    seen = if (picture == null || slot.isEmpty) {
      null
    } else {
      val bitmap = picture.asSkiaBitmap()
      Image.makeFromBitmap(bitmap).makeShader(
        FilterTileMode.MIRROR,
        FilterTileMode.MIRROR,
        SamplingMode.MITCHELL,
        Matrix33.makeTranslate(slot.left, slot.top).makeConcat(Matrix33.makeScale(slot.width / bitmap.width, slot.height / bitmap.height)),
      )
    }
    this.depth = if (seen == null) 0f else depth
    this.pull = if (seen == null) 0f else pull
    this.outer = if (seen == null) 0f else outer
  }

  fun set(
    size: Size,
    cornerRadii: FloatArray,
    density: Float,
    ground: Color,
    fill: Color,
    black: Float,
    white: Float,
    highlight: Float,
    light: Offset,
    place: Float,
  ) {
    val builder = RuntimeShaderBuilder(StillEffect)
    builder.child("content", seen ?: Shader.makeColor(ground.toArgb()))
    // The whole pane from this one copy (no second, sharper one to share it with).
    builder.uniform("level", -1f)
    builder.uniform("size", size.width, size.height)
    builder.uniform("cornerRadii", cornerRadii)
    builder.uniform("density", density)
    // On plain ground these are 0: one colour bent is the same colour.
    builder.uniform("refraction", depth)
    builder.uniform("curve", pull)
    builder.uniform("outer", outer)
    builder.uniform("dispersion", 0f)
    builder.uniform("tint", floatArrayOf(fill.red, fill.green, fill.blue, fill.alpha))
    builder.uniform("range", black, white)
    builder.uniform("saturation", 1f)
    builder.uniform("edge", highlight)
    builder.uniform("light", light.x, light.y)
    builder.uniform("place", place)
    builder.uniform("glow", floatArrayOf(0f, 0f, 1f, 0f))
    made = builder.makeShader()
  }

  /** The paint for this pane, as it was last [set]. */
  fun shader(): androidx.compose.ui.graphics.Shader? = made
}
