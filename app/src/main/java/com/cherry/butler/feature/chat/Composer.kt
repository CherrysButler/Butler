package com.cherry.butler.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.cherry.butler.core.design.Motion
import androidx.compose.runtime.getValue
import androidx.compose.animation.togetherWith
import androidx.compose.animation.scaleIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedContent
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.core.data.PersonaOption
import com.cherry.butler.ui.components.Avatar
import androidx.compose.foundation.layout.Spacer
import com.cherry.butler.ui.components.HairlineRule
import com.cherry.butler.ui.components.hairlineFrame
import com.cherry.butler.ui.components.card
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip

/**
 * The text window. A ruled field on the ground, and one red advance key at the right;
 * while a reply is being written the key becomes Stop. Nothing else lives here: the
 * draft is already in saved state before it is ever sent.
 */
@Composable
fun Composer(
    draft: String,
    onDraftChanged: (String) -> Unit,
    busy: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
    persona: PersonaOption? = null,
    onPickPersona: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
            .imePadding(),
    ) {
        // One field across the whole width, as Janitor's message box: the persona's face at
        // its start, the words, the send key at its end. The keys sit inside the field, so the
        // writing room is the screen's width and not what two keys leave of it.
        Row(
            modifier = Modifier
                .padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 10.dp)
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .card(shape = MaterialTheme.shapes.medium, color = ButlerTheme.colors.surfaceHigh)
                .padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            if (onPickPersona != null) {
                PersonaChip(persona = persona, onClick = onPickPersona)
                Spacer(Modifier.width(10.dp))
            }
            Box(modifier = Modifier.weight(1f).heightIn(min = 40.dp).padding(vertical = 7.dp), contentAlignment = Alignment.CenterStart) {
                BasicTextField(
                    value = draft,
                    onValueChange = onDraftChanged,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        if (draft.isEmpty()) {
                            Text(
                                text = "Write…",
                                style = MaterialTheme.typography.bodyLarge,
                                color = ButlerTheme.colors.textLow,
                            )
                        }
                        inner()
                    },
                )
            }
            Spacer(Modifier.width(8.dp))
            AdvanceKey(busy = busy, enabled = draft.isNotBlank(), onSend = onSend, onStop = onStop)
        }
    }
}

/**
 * Who the next line goes out as: the persona's face on a key, where Janitor puts it in its
 * message box. Tapping it opens the persona list; the pick holds for this chat.
 */
@Composable
private fun PersonaChip(persona: PersonaOption?, onClick: () -> Unit) {
    val name = persona?.name.orEmpty()
    Avatar(
        url = persona?.avatarUrl,
        name = name,
        size = 40.dp,
        initialStyle = MaterialTheme.typography.labelLarge,
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .semantics { contentDescription = if (name.isEmpty()) "Choose persona" else "Playing as $name. Change persona" },
    )
}

/**
 * Send when there is something to send; Stop while the character is writing. One shape for
 * both: a filled key. Stop is the red key with a soft square, so it reads as the same
 * control in its other state rather than an alarm.
 */
@Composable
private fun AdvanceKey(busy: Boolean, enabled: Boolean, onSend: () -> Unit, onStop: () -> Unit) {
    val red = MaterialTheme.colorScheme.primary
    val live = busy || enabled
    // At rest the key is a quiet glyph on the field; it fills red the moment there is a draft.
    val fill by animateColorAsState(if (live) red else androidx.compose.ui.graphics.Color.Transparent, animationSpec = Motion.enter(Motion.SHORT), label = "key-fill")
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(MaterialTheme.shapes.small)
            .background(fill)
            .clickable(enabled = live, onClick = if (busy) onStop else onSend)
            .semantics { contentDescription = if (busy) "Stop" else "Send" },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = busy,
            transitionSpec = { (fadeIn(Motion.enter(Motion.SHORT)) + scaleIn(Motion.enter(Motion.SHORT), initialScale = 0.6f)) togetherWith fadeOut(Motion.exit(100)) },
            label = "key-glyph",
        ) { stopping ->
            if (stopping) {
                Box(Modifier.size(14.dp).background(MaterialTheme.colorScheme.onPrimary, RoundedCornerShape(3.dp)))
            } else {
                Icon(
                    Icons.AutoMirrored.Rounded.Send,
                    contentDescription = null,
                    tint = if (enabled) MaterialTheme.colorScheme.onPrimary else ButlerTheme.colors.textLow,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}
