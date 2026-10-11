package androidx.core.net

fun String.toUri(): android.net.Uri = android.net.Uri.parse(this)
