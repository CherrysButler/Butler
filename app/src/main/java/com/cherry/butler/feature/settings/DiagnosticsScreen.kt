package com.cherry.butler.feature.settings

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.core.diagnostics.Diagnostics
import com.cherry.butler.feature.chats.KeyButton
import com.cherry.butler.ui.components.SkeletonBlock
import com.cherry.butler.ui.components.card
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The report a user sends when something is wrong: built on the phone, shown in full
 * before anything happens to it, then copied or shared by hand. Butler never sends it.
 */
@Composable
fun DiagnosticsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var report by remember { mutableStateOf<String?>(null) }
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { report = withContext(Dispatchers.IO) { Diagnostics.report(context) } }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()) {
        Row(modifier = Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
            }
            Text("Report a problem", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        }
        Text(
            text = "Butler doesn't send anything on its own. This is what it knows about recent failures, with keys, tokens and addresses taken out. Read it, then copy it or share it with whoever is helping you.",
            style = MaterialTheme.typography.bodySmall,
            color = ButlerTheme.colors.textMed,
            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 10.dp),
        )
        Row(modifier = Modifier.padding(horizontal = 16.dp)) {
            KeyButton(
                label = if (copied) "Copied" else "Copy",
                onClick = { report?.let { clipboard.setText(AnnotatedString(it)); copied = true } },
                enabled = report != null,
            )
            Spacer(Modifier.width(8.dp))
            KeyButton(
                label = "Share",
                primary = true,
                enabled = report != null,
                onClick = {
                    val text = report ?: return@KeyButton
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_SUBJECT, "Butler diagnostics")
                        .putExtra(Intent.EXTRA_TEXT, text)
                    context.startActivity(Intent.createChooser(send, "Share the report"))
                },
            )
        }
        Spacer(Modifier.height(12.dp))
        val text = report
        if (text == null) {
            Column(modifier = Modifier.padding(16.dp)) { repeat(6) { SkeletonBlock(width = 280.dp, height = 10.dp); Spacer(Modifier.height(10.dp)) } }
        } else {
            SelectionContainer {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp),
                    color = ButlerTheme.colors.textMed,
                    softWrap = false,
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .fillMaxWidth()
                        .card(color = ButlerTheme.colors.chrome)
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState()),
                )
            }
        }
    }
}
