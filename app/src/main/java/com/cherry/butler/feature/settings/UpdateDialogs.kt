package com.cherry.butler.feature.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.cherry.butler.R
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.core.design.PlateLevel
import com.cherry.butler.core.design.PlateText
import com.cherry.butler.core.design.SheetShape
import com.cherry.butler.core.update.UpdateChecker
import com.cherry.butler.feature.chats.KeyButton
import com.cherry.butler.ui.components.SheetHandle
import com.cherry.butler.ui.components.SheetScrim

/** Opens a release page in the browser. Butler never downloads or installs the update itself. */
fun openReleasePage(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/**
 * A newer Butler is out. Butler's own sheet: the bow tie and the version as a plate, what
 * the release says changed (its first lines, from GitHub), then the way to it. No decoration;
 * the sheet rising is the only motion. [onStopAsking], for the automatic check only, turns
 * that check off.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateSheet(
    version: String,
    current: String,
    url: String,
    notes: List<String>,
    onDismiss: () -> Unit,
    onStopAsking: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = SheetShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        scrimColor = SheetScrim,
        dragHandle = { SheetHandle() },
    ) {
        Column(modifier = Modifier.navigationBarsPadding().padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painterResource(R.drawable.widget_bowtie),
                    contentDescription = null,
                    modifier = Modifier.size(width = 44.dp, height = 28.dp),
                )
                Spacer(Modifier.width(14.dp))
                Column {
                    PlateText("Butler $version", PlateLevel.Head)
                    Text("You have $current", style = MaterialTheme.typography.labelMedium, color = ButlerTheme.colors.textLow)
                }
            }
            if (notes.isNotEmpty()) {
                Column(
                    modifier = Modifier.padding(top = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    notes.forEach { line ->
                        Row {
                            Text("–", style = MaterialTheme.typography.bodyMedium, color = ButlerTheme.colors.textLow, modifier = Modifier.width(16.dp))
                            Text(line, style = MaterialTheme.typography.bodyMedium, color = ButlerTheme.colors.textMed)
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                KeyButton("Later", onClick = onDismiss, modifier = Modifier.weight(1f).height(48.dp))
                KeyButton(
                    "Get it on GitHub",
                    onClick = { openReleasePage(context, url); onDismiss() },
                    primary = true,
                    modifier = Modifier.weight(1.6f).height(48.dp),
                )
            }
            if (onStopAsking != null) {
                TextButton(
                    onClick = { onStopAsking(); onDismiss() },
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp),
                ) { Text("Stop checking on open", style = MaterialTheme.typography.labelLarge, color = ButlerTheme.colors.textLow) }
            }
        }
    }
}

/** A tapped check: a newer version gets the sheet; anything else is a short toast. */
@Composable
fun UpdateResult(result: UpdateChecker.Result, current: String, onDone: () -> Unit) {
    val context = LocalContext.current
    when (result) {
        is UpdateChecker.Result.Available -> UpdateSheet(result.version, current, result.url, result.notes, onDismiss = onDone)
        else -> LaunchedEffect(result) {
            val text = when (result) {
                is UpdateChecker.Result.UpToDate -> "You're on the latest, ${result.version}"
                UpdateChecker.Result.OtherSource -> "This copy updates through F-Droid"
                is UpdateChecker.Result.Failed -> "Couldn't reach GitHub"
                else -> ""
            }
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
            onDone()
        }
    }
}

/**
 * The automatic check, each time the app opens, when the user has it on. Silent unless a
 * newer version is out.
 */
@Composable
fun UpdatePrompt(checker: UpdateChecker) {
    var found by remember { mutableStateOf<UpdateChecker.Result.Available?>(null) }
    LaunchedEffect(Unit) { found = runCatching { checker.checkOnOpen() }.getOrNull() }
    found?.let { available ->
        UpdateSheet(
            version = available.version,
            current = checker.current,
            url = available.url,
            notes = available.notes,
            onDismiss = { found = null },
            onStopAsking = { checker.setAuto(false) },
        )
    }
}
