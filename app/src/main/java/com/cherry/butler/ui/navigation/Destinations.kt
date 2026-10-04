package com.cherry.butler.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.ui.graphics.vector.ImageVector
import com.cherry.butler.R

/** Top-level destinations shown in the bottom navigation bar. */
enum class TopLevelDestination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
) {
    Browse("browse", R.string.nav_browse, Icons.Rounded.Home),
    Chats("chats", R.string.nav_chats, Icons.Rounded.Forum),
    Notifications("notifications", R.string.nav_notifications, Icons.Rounded.Notifications),
    Profile("profile", R.string.nav_profile, Icons.Rounded.Person),
    Settings("settings", R.string.nav_settings, Icons.Rounded.Tune),
}
