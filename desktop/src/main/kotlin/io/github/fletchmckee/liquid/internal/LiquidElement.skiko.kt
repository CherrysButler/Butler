// Copyright 2025, Colin McKee
// SPDX-License-Identifier: Apache-2.0
package io.github.fletchmckee.liquid.internal

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import io.github.fletchmckee.liquid.LiquidScope
import io.github.fletchmckee.liquid.LiquidState
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

internal fun liquidElement(
  liquidState: LiquidState,
  block: LiquidScope.() -> Unit,
): AbstractLiquidElement<out AbstractLiquidNode> = LiquidElement(liquidState, block)

// Using `positionOnScreen()` results in incorrect positioning for jvm.
internal fun LayoutCoordinates.liquidPositionOnScreen(): Offset = positionInWindow()

internal class LiquidElement(
  liquidState: LiquidState,
  block: LiquidScope.() -> Unit,
) : AbstractLiquidElement<LiquidNode>(liquidState, block) {
  override fun create() = LiquidNode(liquidState, block)
}

internal class LiquidNode(
  liquidState: LiquidState,
  block: LiquidScope.() -> Unit,
) : AbstractLiquidNode(liquidState, block) {
  // Butler: Apple's blur is not one radius (LiquidShader.kt). The shader runs over two copies
  // of the picture, one blurred by the whole radius and one by half, and its two results are
  // added.
  private val liquidShader = RuntimeShaderBuilder(RuntimeEffect.makeForShader(LiquidShader))
  private val sharperShader = RuntimeShaderBuilder(RuntimeEffect.makeForShader(LiquidShader))
  private var cachedBlurImageFilter: ImageFilter? = null

  override fun createRenderEffect(): RenderEffect? = with(reusableScope) {
    // We shouldn't have empty bounds at this point, but set the RenderEffect to null if we do.
    if (size.isUnspecified) return null

    liquidShader.updateLiquidUniforms(level = 0f)
    sharperShader.updateLiquidUniforms(level = 1f)
    val blurEffect = when {
      sigma > 0f ->
        cachedBlurImageFilter
          ?.takeUnless { mutatedFields has Fields.Frost }
          ?: ImageFilter.makeBlur(
            // Butler: across, then up and down (LiquidScope.stretch).
            sigmaX = (sigma / stretch).coerceAtLeast(0.5f),
            sigmaY = sigma * stretch,
            mode = FilterTileMode.CLAMP,
          )

      else -> null
    }.also { cachedBlurImageFilter = it }

    val whole = ImageFilter.makeRuntimeShader(
      runtimeShaderBuilder = liquidShader,
      shaderNames = arrayOf("content"),
      inputs = arrayOf(blurEffect),
    )
    // Half the radius; under a pixel of blur it is the picture as it is.
    val halfSigma = if (sigma > 0f) 0.57735f * (frostRadius / 2f) + 0.5f else 0f
    val sharperBlur = if (frostRadius / 2f < 1f) null else ImageFilter.makeBlur(
      sigmaX = (halfSigma / stretch).coerceAtLeast(0.5f),
      sigmaY = halfSigma * stretch,
      mode = FilterTileMode.CLAMP,
    )
    val sharper = ImageFilter.makeRuntimeShader(
      runtimeShaderBuilder = sharperShader,
      shaderNames = arrayOf("content"),
      inputs = arrayOf(sharperBlur),
    )
    return ImageFilter.makeBlend(org.jetbrains.skia.BlendMode.PLUS, whole, sharper, null).asComposeRenderEffect()
  }

  private fun RuntimeShaderBuilder.updateLiquidUniforms(level: Float) = with(reusableScope) {
    uniform("level", level)
    uniform("size", size.width, size.height)
    uniform("cornerRadii", cornerRadii)
    // Butler: the uniforms of Apple's recipe (LiquidShader.kt says what each is).
    uniform("density", density.density)
    uniform("refraction", refraction)
    uniform("curve", curve)
    uniform("outer", outer)
    uniform("dispersion", dispersion)
    uniform("tint", colorComponents)
    uniform("range", range.x, range.y)
    uniform("saturation", saturation)
    uniform("edge", edge)
    uniform("light", light.x, light.y)
    uniform("glow", glowAt.x, glowAt.y, glow.x, glow.y)
    // Butler: a pane over a moving picture is lit by the way its edge faces (LiquidShader.kt).
    uniform("place", 0f)
  }
}
