// Butler's addition to Liquid (not from the library): the glass shader as a paint, for a pane
// standing on ground of one colour, where there is nothing behind it to capture.
package io.github.fletchmckee.liquid.internal

import android.graphics.BitmapShader
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb

/** Whether this device can run the glass shader as a paint: Android 13 on. */
internal val stillGlassSupported: Boolean get() = Build.VERSION.SDK_INT >= 33

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
  private var size = Size.Zero
  private var cornerRadii = FloatArray(4)
  private var density = 1f
  private var ground = 0
  private var fill = 0
  private var black = 0f
  private var white = 1f
  private var highlight = 0f
  private var light = Offset.Zero
  private var place = 0f
  private var seen: Shader? = null
  private var depth = 0f
  private var pull = 0f
  private var outer = 0f

  /** The pane looks through [picture], which lies over [slot] of it; null is the plain ground again. */
  fun look(picture: ImageBitmap?, slot: Rect, depth: Float, pull: Float, outer: Float) {
    seen = if (picture == null || slot.isEmpty || Build.VERSION.SDK_INT < 33) null else Shared33.picture(picture, slot)
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
    this.place = place
    this.size = size
    this.cornerRadii = cornerRadii
    this.density = density
    this.ground = ground.toArgb()
    this.fill = fill.toArgb()
    this.black = black
    this.white = white
    this.highlight = highlight
    this.light = light
  }

  /** The paint for this pane, to draw with now; null where the shader cannot run. */
  fun shader(): androidx.compose.ui.graphics.Shader? = if (Build.VERSION.SDK_INT >= 33) Shared33.with(this) else null

  /**
   * One compiled shader for every pane: compiling it is the only cost worth counting, and a
   * list of cards with tags on them would pay it for each. A draw takes the shader as its
   * numbers stand when it is drawn, so each pane sets its own and draws at once. Drawing is
   * on the main thread only.
   */
  @RequiresApi(33)
  private object Shared33 {
    private val shader by lazy { RuntimeShader(LiquidShader) }
    private var groundOf = 0
    private var groundPaint: Shader? = null

    fun picture(picture: ImageBitmap, slot: Rect): Shader {
      val bitmap = picture.asAndroidBitmap()
      return BitmapShader(bitmap, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR).apply {
        filterMode = BitmapShader.FILTER_MODE_LINEAR
        setLocalMatrix(
          Matrix().apply {
            setScale(slot.width / bitmap.width, slot.height / bitmap.height)
            postTranslate(slot.left, slot.top)
          },
        )
      }
    }

    fun with(pane: StillGlass): Shader {
      val behind = groundPaint.takeIf { groundOf == pane.ground }
        ?: LinearGradient(0f, 0f, 1f, 0f, pane.ground, pane.ground, Shader.TileMode.CLAMP).also {
          groundOf = pane.ground
          groundPaint = it
        }
      shader.setInputShader("content", pane.seen ?: behind)
      // The whole pane from this one copy (no second, sharper one to share it with).
      shader.setFloatUniform("level", -1f)
      shader.setFloatUniform("size", pane.size.width, pane.size.height)
      shader.setFloatUniform("cornerRadii", pane.cornerRadii)
      shader.setFloatUniform("density", pane.density)
      // On plain ground these are 0: one colour bent is the same colour.
      shader.setFloatUniform("refraction", pane.depth)
      shader.setFloatUniform("curve", pane.pull)
      shader.setFloatUniform("outer", pane.outer)
      shader.setFloatUniform("dispersion", 0f)
      shader.setColorUniform("tint", pane.fill)
      shader.setFloatUniform("range", pane.black, pane.white)
      shader.setFloatUniform("saturation", 1f)
      shader.setFloatUniform("edge", pane.highlight)
      shader.setFloatUniform("light", pane.light.x, pane.light.y)
      shader.setFloatUniform("place", pane.place)
      shader.setFloatUniform("glow", 0f, 0f, 1f, 0f)
      return shader
    }
  }
}
