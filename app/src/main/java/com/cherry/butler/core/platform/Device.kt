package com.cherry.butler.core.platform

/** What Butler runs on, as the words say it: "kept on this phone". The desktop has its own copy. */
object Device {
    const val NAME = "phone"

    /** Whether a blur can be drawn behind a view: Android 12 on. */
    val canBlurBehind: Boolean get() = android.os.Build.VERSION.SDK_INT >= 31
}
