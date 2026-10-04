package com.cherry.butler.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.cherry.butler.R
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.core.security.AppLock
import com.cherry.butler.feature.chats.KeyButton

/**
 * What covers Butler while it's locked: the ground, the bowtie, one way in. The phone's own
 * prompt comes up by itself, so most of the time this is seen for a blink.
 */
@Composable
fun LockScreen(lock: AppLock) {
    val activity = LocalContext.current as? FragmentActivity
    LaunchedEffect(Unit) { activity?.let { lock.authenticate(it) } }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Nothing beneath is reachable while this is up.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(painterResource(R.drawable.widget_bowtie), contentDescription = "Butler", modifier = Modifier.size(width = 66.dp, height = 42.dp))
        Spacer(Modifier.height(20.dp))
        Text("Butler is locked", style = MaterialTheme.typography.titleMedium, color = ButlerTheme.colors.textMed)
        Spacer(Modifier.height(24.dp))
        KeyButton(label = "Unlock", onClick = { activity?.let { lock.authenticate(it) } }, primary = true)
    }
}
