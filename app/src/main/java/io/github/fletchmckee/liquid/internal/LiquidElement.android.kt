// Copyright 2025, Colin McKee
// SPDX-License-Identifier: Apache-2.0
package io.github.fletchmckee.liquid.internal

import android.graphics.BlendMode
import android.graphics.RenderEffect.createBlendModeEffect
import android.graphics.RenderEffect.createBlurEffect
import android.graphics.RenderEffect.createChainEffect
import android.graphics.RenderEffect.createRuntimeShaderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionOnScreen
import io.github.fletchmckee.liquid.LiquidScope
import io.github.fletchmckee.liquid.LiquidState

// Android 12 and lower can't reference a RuntimeShader, so this is split into separate
// node types. Once minSdk is 33, this can be simplified to LiquidElement only.
internal fun liquidElement(
  liquidState: LiquidState,
  block: LiquidScope.() -> Unit,
): AbstractLiquidElement<out AbstractLiquidNode> = when {
  Build.VERSION.SDK_INT >= 33 -> LiquidElement(liquidState, block)
  else -> LiquidBackupElement(liquidState, block)
}

// Using `positionOnScreen()` so that dialogs/popups share the same logic as other views.
internal fun LayoutCoordinates.liquidPositionOnScreen(): Offset = positionOnScreen()

@RequiresApi(33)
internal class LiquidElement(
  liquidState: LiquidState,
  block: LiquidScope.() -> Unit,
) : AbstractLiquidElement<LiquidNode>(liquidState, block) {
  override fun create() = LiquidNode(liquidState, block)
}

@RequiresApi(33)
internal class LiquidNode(
  liquidState: LiquidState,
  block: LiquidScope.() -> Unit,
) : AbstractLiquidNode(liquidState, block) {
  // Butler: Apple's blur is not one radius (LiquidShader.kt). The shader runs over two copies
  // of the picture, one blurred by the whole radius and one by half, and its two results are
  // added. Each copy has its own shader, since `level` differs.
  private val liquidShader = RuntimeShader(LiquidShader)
  private val sharperShader = RuntimeShader(LiquidShader)
  private var cachedBlurEffect: android.graphics.RenderEffect? = null

  override fun createRenderEffect(): RenderEffect? = with(reusableScope) {
    // We shouldn't have empty bounds at this point, but set the RenderEffect to null if we do.
    if (size.isUnspecified) return null

    liquidShader.updateLiquidUniforms(level = 0f)
    sharperShader.updateLiquidUniforms(level = 1f)
    val liquidEffect = createRuntimeShaderEffect(liquidShader, "content")
    // Half the radius; under a pixel of blur it is the picture as it is.
    val sharperEffect = createRuntimeShaderEffect(sharperShader, "content").let { sharper ->
      val half = frostRadius / 2f
      if (half < 1f) {
        sharper
      } else {
        createChainEffect(sharper, createBlurEffect((half / stretch).coerceAtLeast(0.5f), half * stretch, Shader.TileMode.CLAMP))
      }
    }

    if (frostRadius < 1f) {
      return createBlendModeEffect(liquidEffect, sharperEffect, BlendMode.PLUS).asComposeRenderEffect()
    }

    val blurEffect = cachedBlurEffect
      ?.takeUnless { mutatedFields has Fields.Frost }
      ?: createBlurEffect(
        // Butler: across, then up and down (LiquidScope.stretch).
        (frostRadius / stretch).coerceAtLeast(0.5f),
        frostRadius * stretch,
        Shader.TileMode.CLAMP,
      ).also { cachedBlurEffect = it }

    return createBlendModeEffect(createChainEffect(liquidEffect, blurEffect), sharperEffect, BlendMode.PLUS).asComposeRenderEffect()
  }

  private fun RuntimeShader.updateLiquidUniforms(level: Float) = with(reusableScope) {
    setFloatUniform("level", level)
    setFloatUniform("size", size.width, size.height)
    setFloatUniform("cornerRadii", cornerRadii)
    // Butler: the uniforms of Apple's recipe (LiquidShader.kt says what each is).
    setFloatUniform("density", density.density)
    setFloatUniform("refraction", refraction)
    setFloatUniform("curve", curve)
    setFloatUniform("outer", outer)
    setFloatUniform("dispersion", dispersion)
    setColorUniform("tint", argbColor)
    setFloatUniform("range", range.x, range.y)
    setFloatUniform("saturation", saturation)
    setFloatUniform("edge", edge)
    setFloatUniform("light", light.x, light.y)
    setFloatUniform("glow", glowAt.x, glowAt.y, glow.x, glow.y)
  }
}
