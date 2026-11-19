package com.cherry.butler.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.cherry.butler.core.config.JanitorConfig
import com.cherry.butler.core.config.JanitorEndpoint
import com.cherry.butler.core.config.JanitorEndpointPrefs
import com.cherry.butler.feature.settings.ChoiceRow

val LocalEndpointRestart = staticCompositionLocalOf<() -> Boolean> { { false } }

@Composable
fun JanitorEndpointPicker(row: @Composable (host: String, open: () -> Unit) -> Unit) {
    val context = LocalContext.current
    val restart = LocalEndpointRestart.current
    val active = JanitorConfig.endpoint
    var selected by remember { mutableStateOf<JanitorEndpoint?>(null) }
    var failed by remember { mutableStateOf(false) }

    row(active.host) {
        failed = false
        selected = active
    }

    selected?.let { choice ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text("Janitor endpoint") },
            text = {
                Column {
                    JanitorEndpoint.entries.forEach { endpoint ->
                        ChoiceRow(endpoint.host, null, choice == endpoint, onClick = { selected = endpoint })
                    }
                    Text(
                        if (failed) "Couldn’t switch endpoints. Try again." else
                            "Butler will restart. Each endpoint keeps its own sign-in.",
                        color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            dismissButton = { TextButton(onClick = { selected = null }) { Text("Cancel") } },
            confirmButton = {
                TextButton(
                    enabled = choice != active,
                    onClick = {
                        val prefs = JanitorEndpointPrefs(context.applicationContext)
                        if (prefs.save(choice) && restart()) {
                            selected = null
                        } else {
                            prefs.save(active)
                            failed = true
                        }
                    },
                ) { Text("Switch and restart") }
            },
        )
    }
}
