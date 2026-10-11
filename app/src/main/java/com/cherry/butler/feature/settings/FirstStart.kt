package com.cherry.butler.feature.settings

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cherry.butler.BuildConfig
import com.cherry.butler.R
import com.cherry.butler.core.design.AppTheme
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.core.design.ChatStyle
import com.cherry.butler.core.design.Motion
import com.cherry.butler.core.design.Pill
import com.cherry.butler.core.design.tones
import com.cherry.butler.ui.components.LocalPointerMotion

/**
 * The first start: on a fresh install, once signed in, a few steps to make Butler one's own
 * (the look, the thinking line, typing) before the first chat. After an update it is what's
 * new instead, with the same steps a tap away. Every choice is the one Settings holds, made
 * with Settings' own pieces, and everything here can be changed there later.
 */
@Composable
fun FirstStartHost() {
    val context = LocalContext.current
    val seen = remember { FirstStartRecord(context) }
    var mode by remember { mutableStateOf(seen.mode()) }
    val showing = mode ?: return
    val viewModel: SettingsViewModel = hiltViewModel()
    FirstStartFrame {
        FirstStart(
            viewModel = viewModel,
            startWithNews = showing == FirstStartRecord.Mode.News,
            onDone = {
                seen.markSeen()
                mode = null
            },
        )
    }
}

/** Whether the guide (or what's new) has been seen for this version, kept on the device. */
private class FirstStartRecord(private val context: Context) {
    enum class Mode { Welcome, News }

    private val prefs = context.getSharedPreferences("butler_prefs", Context.MODE_PRIVATE)

    fun mode(): Mode? {
        val last = prefs.getString(KEY, null)
        return when {
            last == BuildConfig.VERSION_NAME -> null
            last != null -> Mode.News
            // Never seen: a fresh install gets the guide; a phone that had Butler before this
            // version (the record is new in 0.4.2) gets what's new.
            updatedInstall() -> Mode.News
            else -> Mode.Welcome
        }
    }

    private fun updatedInstall(): Boolean = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.lastUpdateTime - info.firstInstallTime > 60_000L
    }.getOrDefault(false)

    fun markSeen() { prefs.edit().putString(KEY, BuildConfig.VERSION_NAME).apply() }

    private companion object {
        const val KEY = "first_start_seen"
    }
}

/** The whole screen on a phone; on the desktop a card over the dimmed app. */
@Composable
private fun FirstStartFrame(content: @Composable () -> Unit) {
    val wide = LocalPointerMotion.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (wide) Color.Black.copy(alpha = 0.6f) else MaterialTheme.colorScheme.background)
            // Nothing behind it takes a click while it is up.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = if (wide) {
                Modifier
                    .padding(24.dp)
                    .widthIn(max = 620.dp)
                    .heightIn(max = 760.dp)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
            } else {
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
            },
        ) { content() }
    }
}

private enum class Step { News, Welcome, Look, Thinking, Typing, Done }

@Composable
private fun FirstStart(viewModel: SettingsViewModel, startWithNews: Boolean, onDone: () -> Unit) {
    val steps = if (startWithNews) {
        listOf(Step.News, Step.Look, Step.Thinking, Step.Typing, Step.Done)
    } else {
        listOf(Step.Welcome, Step.Look, Step.Thinking, Step.Typing, Step.Done)
    }
    var at by remember { mutableIntStateOf(0) }
    val step = steps[at]
    androidx.activity.compose.BackHandler(enabled = at > 0) { at-- }
    Column(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = step,
            transitionSpec = { fadeIn(Motion.enter()) togetherWith fadeOut(Motion.exit()) },
            modifier = Modifier.weight(1f),
            label = "first-start",
        ) { shown ->
            // Centred both ways while it fits; a long step scrolls from the top. Lines stop at
            // a reading width.
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(min = maxHeight).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              Column(Modifier.widthIn(max = 560.dp).fillMaxWidth()) {
                when (shown) {
                    Step.News -> NewsStep()
                    Step.Welcome -> WelcomeStep()
                    Step.Look -> LookStep(viewModel)
                    Step.Thinking -> {
                        StepHead("The thinking line", "What shows while a reply is thought out, and what a finished thought folds to.")
                        ThinkingSection(viewModel)
                    }
                    Step.Typing -> {
                        StepHead("Typing", "How your own lines are written.")
                        RichTypingSection(viewModel)
                        WriteForMeSection(viewModel)
                    }
                    Step.Done -> DoneStep()
                }
              }
            }
            }
        }
        StepKeys(
            at = at,
            count = steps.size,
            step = step,
            onBack = { at-- },
            onNext = { if (at == steps.lastIndex) onDone() else at++ },
            onSkip = onDone,
        )
    }
}

@Composable
private fun StepHead(title: String, line: String?) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        if (line != null) {
            Spacer(Modifier.height(6.dp))
            Text(line, style = MaterialTheme.typography.bodyMedium, color = ButlerTheme.colors.textMed)
        }
    }
}

@Composable
private fun WelcomeStep() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 40.dp).widthIn(max = 440.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(painterResource(R.drawable.widget_bowtie), contentDescription = null, modifier = Modifier.size(width = 88.dp, height = 56.dp))
        Spacer(Modifier.height(24.dp))
        Text("Welcome to Butler", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(12.dp))
        Text(
            "A few choices to make it yours: how it looks, what shows while a reply is thought out, and how you type. " +
                "Everything here is in Settings too, to change whenever you like.",
            style = MaterialTheme.typography.bodyLarge,
            color = ButlerTheme.colors.textMed,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

/** What 0.4.2 brought, for someone who had Butler before it. */
@Composable
private fun NewsStep() {
    StepHead("What's new in ${BuildConfig.VERSION_NAME}", "A look at what changed. The next steps set up the new choices.")
    val news = listOf(
        "Write for me" to "Writes in its own panel now: guide it with quick picks or your own words, and pick the voice (I, she, you).",
        "Reply length" to "Short, medium or long, told to the model itself, on proxies and JLLM.",
        "Compact mode" to "Near the context limit, older messages fold into a summary and the latest stay word for word.",
        "The thinking line" to "Three looks while a reply is thought out, and three for the finished thought.",
        "Personas" to "Export and import them, edit the default one from a chat, find chats by persona.",
        "Look profiles" to "Save a whole look, put it back on in a tap, share it as a file.",
        "Fonts and text" to "Added fonts with real bold and italics, and app text up to 175%.",
        "Rich typing" to "Marks pair up as you type, the way a code editor does.",
        "New reply" to "Hold it to ask again without the guidance sheet.",
    )
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        news.forEach { (title, line) ->
            Row {
                Box(Modifier.padding(top = 7.dp).size(7.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text(line, style = MaterialTheme.typography.bodyMedium, color = ButlerTheme.colors.textMed)
                }
            }
        }
    }
}

@Composable
private fun LookStep(viewModel: SettingsViewModel) {
    val current by viewModel.theme.collectAsStateWithLifecycle()
    val custom by viewModel.custom.collectAsStateWithLifecycle()
    val chatStyle by viewModel.chatStyle.collectAsStateWithLifecycle()
    var customizing by remember { mutableStateOf(false) }
    StepHead("The look", "Pick a theme; the app changes as you tap.")
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AppTheme.entries.forEach { theme ->
            ThemeTile(theme, theme.tones(custom), theme == current, Modifier.weight(1f)) {
                viewModel.setTheme(theme)
                if (theme == AppTheme.Custom) customizing = true
            }
        }
    }
    if (current == AppTheme.Custom) {
        TextButton(onClick = { customizing = true }, shape = Pill, modifier = Modifier.padding(start = 12.dp)) {
            Text("Custom colours", color = MaterialTheme.colorScheme.primary)
        }
    }
    Text(
        "Chat layout",
        style = MaterialTheme.typography.labelMedium,
        color = ButlerTheme.colors.textMed,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 6.dp),
    )
    val styles = ChatStyle.entries
    com.cherry.butler.ui.components.SegmentedChoice(
        options = styles.map { it.label },
        selected = styles.indexOf(chatStyle).coerceAtLeast(0),
        onSelect = { i -> viewModel.setChatStyle(styles[i]) },
        modifier = Modifier.padding(horizontal = 8.dp),
    )
    Text(
        chatStyle.blurb,
        style = MaterialTheme.typography.bodySmall,
        color = ButlerTheme.colors.textLow,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp),
    )
    if (customizing) CustomLookSheet(viewModel, onDismiss = { customizing = false })
}

@Composable
private fun DoneStep() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 40.dp).widthIn(max = 440.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(painterResource(R.drawable.widget_bowtie), contentDescription = null, modifier = Modifier.size(width = 66.dp, height = 42.dp))
        Spacer(Modifier.height(20.dp))
        Text("All set", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(12.dp))
        Text(
            "There's more in Settings when you want it: Butler specials (Butter mode, Highlights), Compact mode under Chat, " +
                "fonts, chat backgrounds and look profiles.",
            style = MaterialTheme.typography.bodyLarge,
            color = ButlerTheme.colors.textMed,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

/** Back, the step dots, and Next (Skip on the first step; Start on the last). */
@Composable
private fun StepKeys(at: Int, count: Int, step: Step, onBack: () -> Unit, onNext: () -> Unit, onSkip: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            if (at == 0) {
                TextButton(onClick = onSkip, shape = Pill) { Text("Skip", color = ButlerTheme.colors.textMed) }
            } else {
                TextButton(onClick = onBack, shape = Pill) { Text("Back", color = MaterialTheme.colorScheme.onSurface) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(count) { i ->
                Box(
                    Modifier
                        .size(if (i == at) 8.dp else 6.dp)
                        .clip(CircleShape)
                        .background(if (i == at) MaterialTheme.colorScheme.primary else ButlerTheme.colors.rule),
                )
            }
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            Box(
                modifier = Modifier
                    .clip(Pill)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(onClick = onNext)
                    .padding(horizontal = 22.dp, vertical = 11.dp),
            ) {
                Text(
                    when {
                        at == count - 1 -> "Start"
                        step == Step.News -> "Set up"
                        at == 0 -> "Let's go"
                        else -> "Next"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}
