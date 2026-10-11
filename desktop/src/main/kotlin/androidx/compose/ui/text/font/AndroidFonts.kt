package androidx.compose.ui.text.font

import java.io.File

/** Font(file): a font the user added. */
fun Font(
    file: File,
    weight: FontWeight = FontWeight.Normal,
    style: FontStyle = FontStyle.Normal,
): Font = androidx.compose.ui.text.platform.Font(file, weight, style)
