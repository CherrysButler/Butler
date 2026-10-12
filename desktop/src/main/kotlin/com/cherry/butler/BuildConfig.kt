package com.cherry.butler

/** Android's generated BuildConfig, for the desktop build. DEBUG comes from -Dbutler.debug. */
object BuildConfig {
    @JvmField val DEBUG: Boolean = System.getProperty("butler.debug") == "true"
    const val APPLICATION_ID = "com.cherry.butler"
    const val VERSION_NAME = "0.4.3"
    const val VERSION_CODE = 10
    @JvmField val DEV_MIRROR: Boolean = false
    const val BUILD_TYPE = "release"
}
