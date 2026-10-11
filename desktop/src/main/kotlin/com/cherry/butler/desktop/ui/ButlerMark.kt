package com.cherry.butler.desktop.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The bow tie (res/drawable/widget_bowtie.xml) as code, for the window's icon. Reading the
 * drawable at start-up brought in Java's whole XML parser (some 180 classes) for six paths.
 */
internal val ButlerMark: ImageVector by lazy {
    val red = SolidColor(Color(0xFFEA5A4F))
    val shade = SolidColor(Color(0xFFC44036))
    ImageVector.Builder("ButlerMark", 44.dp, 23.dp, 64f, 33f)
        .addPath(addPathNodes("M34.9,13.4 L49.4,1 L62,7.6 L55.8,16.5 L62,25.4 L49.4,32 L34.9,19.6 Z"), fill = red)
        .addPath(addPathNodes("M29.1,13.4 L14.6,1 L2,7.6 L8.2,16.5 L2,25.4 L14.6,32 L29.1,19.6 Z"), fill = red)
        .addPath(addPathNodes("M35.3,14.9 L49.4,2.5 L49.4,5.2 L35.7,16.5 Z"), fill = shade)
        .addPath(addPathNodes("M28.7,14.9 L14.6,2.5 L14.6,5.2 L28.3,16.5 Z"), fill = shade)
        .addPath(
            addPathNodes("M27.9,11.5 H34.1 A0.8,0.8 0 0 1 34.9,12.3 V20.7 A0.8,0.8 0 0 1 34.1,21.5 H27.9 A0.8,0.8 0 0 1 27.1,20.7 V12.3 A0.8,0.8 0 0 1 27.9,11.5 Z"),
            fill = shade,
        )
        .addPath(
            addPathNodes("M28.6,12.7 H33.4 A0.3,0.3 0 0 1 33.7,13 V20 A0.3,0.3 0 0 1 33.4,20.3 H28.6 A0.3,0.3 0 0 1 28.3,20 V13 A0.3,0.3 0 0 1 28.6,12.7 Z"),
            fill = red,
        )
        .build()
}

/**
 * The bow tie centred on a square, for the taskbar and the tray. Windows draws those icons
 * square, so the wide mark on its own came out squeezed from the sides.
 */
internal val ButlerIcon: ImageVector by lazy {
    val mark = ButlerMark
    ImageVector.Builder("ButlerIcon", 64.dp, 64.dp, 64f, 64f)
        .addGroup(translationY = (64f - 33f) / 2f)
        .apply {
            for (i in 0 until mark.root.size) {
                val path = mark.root[i] as androidx.compose.ui.graphics.vector.VectorPath
                addPath(path.pathData, fill = path.fill)
            }
        }
        .clearGroup()
        .build()
}
