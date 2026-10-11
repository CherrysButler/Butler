package com.cherry.butler.core.design

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.cherry.butler.core.data.FontPrefs

/**
 * A font key (FontPrefs) as a family. An added font brings each of its faces with the weight
 * and slant its file declares, so bold and italic text use the real faces, and only a face
 * the family lacks is made up. A font that isn't here falls back to the phone's sans.
 */
fun fontFamilyOf(fonts: FontPrefs, key: String): FontFamily = when (key) {
    "atkinson" -> ReadingFamily
    "system" -> FontFamily.Default
    "serif" -> FontFamily.Serif
    "mono" -> FontFamily.Monospace
    "lora" -> LoraFamily
    "nunito" -> NunitoFamily
    "lexend" -> LexendFamily
    "comic" -> ComicNeueFamily
    else -> fonts.facesOf(key).takeIf { it.isNotEmpty() }
        ?.let { faces -> FontFamily(faces.map { Font(it.file, FontWeight(it.weight), if (it.italic) FontStyle.Italic else FontStyle.Normal) }) }
        ?: FontFamily.Default
}
