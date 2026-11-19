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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.cherry.butler.core.config.JanitorEndpoint
import kotlinx.coroutines.launch

val LocalSelectEndpoint = staticCompositionLocalOf<suspend (JanitorEndpoint) -> Boolean> { { false } }

@Composable
fun JanitorEndpointPicker(
    active: JanitorEndpoint,
    onSelect: suspend (JanitorEndpoint) -> Boolean,
    row: @Composable (host: String, open: () -> Unit) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var switching by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<JanitorEndpoint?>(null) }
    var failed by remember { mutableStateOf(false) }

    row(active.host) {
        failed = false
        selected = active
    }

    selected?.let { choice ->
        AlertDialog(
            onDismissRequest = { if (!switching) selected = null },
            title = { Text("Janitor endpoint") },
            text = {
                Column {
                    val endpoints = JanitorEndpoint.entries
                    SegmentedChoice(
                        options = endpoints.map { it.host },
                        selected = endpoints.indexOf(choice),
                        onSelect = { if (!switching) selected = endpoints[it] },
                    )
                    Text(
                        if (failed) "Couldn’t switch endpoints. Try again." else
                            "Butler will restart. Each endpoint keeps its own sign-in.",
                        color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            dismissButton = { TextButton(enabled = !switching, onClick = { selected = null }) { Text("Cancel") } },
            confirmButton = {
                TextButton(
                    enabled = choice != active && !switching,
                    onClick = {
                        switching = true
                        scope.launch {
                            if (onSelect(choice)) selected = null else failed = true
                            switching = false
                        }
                    },
                ) { Text("Switch and restart") }
            },
        )
    }
}
