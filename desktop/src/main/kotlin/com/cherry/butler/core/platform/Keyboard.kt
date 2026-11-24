package com.cherry.butler.core.platform

import androidx.compose.runtime.Composable

/** A PC has no on-screen keyboard to come and go. */
@Composable
fun keyboardStaying(): Boolean = false

/** Never up: a PC's box opens on its own beat. */
@Composable
fun keyboardRise(): Float = 0f
