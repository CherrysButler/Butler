package com.cherry.butler.core.data

import android.graphics.BitmapFactory
import com.cherry.butler.core.model.CharacterDetail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A character as a card PNG (V2) that SillyTavern, Chub, RisuAI and the rest open: its picture,
 * with its definition written inside. The definition comes from the page when it's open (or
 * the character is yours); when its creator hides it but allows proxies, from the prompt
 * Janitor assembles for a proxy ([ProxyDefinition]); with neither there is none to export.
 */
@Singleton
class CardExport @Inject constructor(
    private val client: OkHttpClient,
    private val proxyDefinition: com.cherry.butler.core.generation.ProxyDefinition,
) {
    /** Where a character's definition can be read from. */
    enum class Source { Page, Proxy, None }

    fun source(c: CharacterDetail, mine: Boolean): Source = when {
        (mine || c.showDefinition) && !c.personality.isNullOrBlank() -> Source.Page
        c.allowProxy -> Source.Proxy
        else -> Source.None
    }

    /** [c] as a card, its definition read from [source]; [chatId] is the chat a proxy read asks in. */
    suspend fun card(c: CharacterDetail, source: Source, chatId: Long?): CharacterCard {
        val page = card(c)
        return when (source) {
            Source.Page -> page
            Source.Proxy -> proxyDefinition.read(chatId ?: error("A chat with the character is needed.")).let { d ->
                // A hidden character's page leaves out its first intro (Janitor sends it as null)
                // and shows the rest. The first is the chat's opening line, from the bundle; the
                // ones the page shows are the alternates, less the one the chat opened with.
                val intro = d.firstMessage.ifBlank { c.firstMessages.firstOrNull().orEmpty() }
                page.copy(
                    description = d.personality,
                    scenario = d.scenario.ifBlank { page.scenario },
                    exampleMessages = d.exampleDialogs.ifBlank { page.exampleMessages },
                    firstMessage = intro,
                    alternateGreetings = c.firstMessages.filter { it.isNotBlank() && !sameIntro(it, intro, c) },
                )
            }
            Source.None -> error("This character's definition is hidden.")
        }
    }

    /** [card] written into [c]'s picture: the file name and bytes. */
    suspend fun png(c: CharacterDetail, card: CharacterCard): Pair<String, ByteArray> = withContext(Dispatchers.IO) {
        val picture = c.avatarUrl?.let { url ->
            runCatching {
                client.newCall(Request.Builder().url(url).get().build()).execute().use { r ->
                    if (r.isSuccessful) r.body?.bytes() else null
                }
            }.getOrNull()
        }
        val png = picture?.let(::asPng) ?: BLANK
        val name = c.name.replace(Regex("[\\\\/:*?\"<>|]+"), " ").trim().ifBlank { "character" }.take(80)
        "$name.png" to CharacterCard.writePng(png, card)
    }

    /** Janitor's pictures are WebP; a card is a PNG. */
    private fun asPng(bytes: ByteArray): ByteArray? {
        if (CharacterCard.isPng(bytes)) return bytes
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        return ByteArrayOutputStream().use { out ->
            if (bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)) out.toByteArray() else null
        }.also { bitmap.recycle() }
    }

    companion object {
        /**
         * Whether two intros are the same line: the page's has `{{char}}`/`{{user}}` (or `{char}`)
         * where the bundle's has the character's name and `{{user}}` put back, so both are read
         * alike, spacing and case aside.
         */
        internal fun sameIntro(a: String, b: String, c: CharacterDetail): Boolean {
            val char = (c.chatName?.takeIf { it.isNotBlank() } ?: c.name)
            fun norm(s: String) = s
                .replace(Regex("\\{\\{?char\\}\\}?", RegexOption.IGNORE_CASE), char)
                .replace(Regex("\\{\\{?user\\}\\}?", RegexOption.IGNORE_CASE), "{{user}}")
                .replace(Regex("\\s+"), " ").trim().lowercase()
            return norm(a) == norm(b)
        }

        /**
         * [c] as a card: Janitor's personality is the card's definition (its one box for who the
         * character is), the first intro the first message and the rest alternate greetings,
         * the page's description the creator's notes.
         */
        fun card(c: CharacterDetail): CharacterCard = CharacterCard(
            name = c.name,
            nickname = c.chatName.orEmpty(),
            description = c.personality.orEmpty(),
            scenario = c.scenario.orEmpty(),
            firstMessage = c.firstMessages.firstOrNull().orEmpty(),
            alternateGreetings = c.firstMessages.drop(1).filter { it.isNotBlank() },
            exampleMessages = c.exampleDialogs.orEmpty(),
            creatorNotes = CharacterCard.plainText(c.description),
            tags = (c.tagNames.map { n -> n.filter { it.isLetterOrDigit() || it == ' ' || it == '-' }.trim() } + c.customTags)
                .filter { it.isNotBlank() }.distinctBy { it.lowercase() } + listOfNotNull("NSFW".takeIf { c.isNsfw }),
            creator = c.creatorName,
            characterVersion = "1.0",
        )

        /** A grey 400×600 card for a character with no picture. */
        private val BLANK: ByteArray = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAZAAAAJYCAIAAADDjiffAAAFzElEQVR42u3UMQ0AMAgAwVI7JCz494YKBpI7CT98ZPUDuOBLABgW" +
            "gGEBhgVgWACGBRgWgGEBGBZgWACGBWBYgGEBGBaAYQGGBWBYAIYFGBaAYQEYFmBYAIYFYFiAYQEYFoBhAYYFYFiAYQEYFoBhAYYF" +
            "YFgAhgUYFoBhARgWYFgAhgVgWIBhARgWgGEBhgVgWACGBRgWgGEBGBZgWACGBWBYgGEBGBaAYQGGBWBYgGEBGBaAYQGGBWBYAIYF" +
            "GBaAYQEYFmBYAIYFYFiAYQEYFoBhAYYFYFgAhgUYFoBhARgWYFgAhgVgWIBhARgWgGEBhgVgWIBhARgWgGEBhgVgWACGBRgWgGEB" +
            "GBZgWACGBWBYgGEBGBaAYQGGBWBYAIYFGBaAYQEYFmBYAIYFYFiAYQEYFoBhAYYFYFiAYQEYFoBhAYYFYFgAhgUYFoBhARgWYFgA" +
            "hgVgWIBhARgWgGEBhgVgWACGBRgWgGEBGBZgWACGBWBYgGEBGBaAYQGGBWBYgGEBGBaAYQGGBWBYAIYFGBaAYQEYFmBYAIYFYFiA" +
            "YQEYFoBhAYYFYFgAhgUYFoBhARgWYFgAhgVgWIBhARgWgGEBhgVgWIBhARgWgGEBhgVgWACGBRgWgGEBGBZgWACGBWBYgGEBGBaA" +
            "YQGGBWBYAIYFGBaAYQEYFmBYAIYFYFiAYQEYFoBhAYYFYFiAYQEYFoBhAYYFYFgAhgUYFoBhARgWYFgAhgVgWIBhARgWgGEBhgVg" +
            "WACGBRgWgGEBGBZgWACGBWBYgGEBGBaAYQGGBWBYgGEBGBaAYQGGBWBYAIYFGBaAYQEYFmBYAIYFYFiAYQEYFoBhAYYFYFgAhgUY" +
            "FoBhARgWYFgAhgVgWIBhARgWYFgSAIYFYFiAYQEYFoBhAYYFYFgAhgUYFoBhARgWYFgAhgVgWIBhARgWgGEBhgVgWACGBRgWgGEB" +
            "GBZgWACGBWBYgGEBGBZgWACGBWBYgGEBGBaAYQGGBWBYAIYFGBaAYQEYFmBYAIYFYFiAYQEYFoBhAYYFYFgAhgUYFoBhARgWYFgA" +
            "hgVgWIBhARgWYFgAhgVgWIBhARgWgGEBhgVgWACGBRgWgGEBGBZgWACGBWBYgGEBGBaAYQGGBWBYAIYFGBaAYQEYFmBYAIYFYFiA" +
            "YQEYFmBYAIYFYFiAYQEYFoBhAYYFYFgAhgUYFoBhARgWYFgAhgVgWIBhARgWgGEBhgVgWACGBRgWgGEBGBZgWACGBWBYgGEBGBZg" +
            "WACGBWBYgGEBGBaAYQGGBWBYAIYFGBaAYQEYFmBYAIYFYFiAYQEYFoBhAYYFYFgAhgUYFoBhARgWYFgAhgVgWIBhARgWYFgAhgVg" +
            "WIBhARgWgGEBhgVgWACGBRgWgGEBGBZgWACGBWBYgGEBGBaAYQGGBWBYAIYFGBaAYQEYFmBYAIYFYFiAYQEYFmBYAIYFYFiAYQEY" +
            "FoBhAYYFYFgAhgUYFoBhARgWYFgAhgVgWIBhARgWgGEBhgVgWACGBRgWgGEBGBZgWACGBWBYgGEBGBZgWACGBWBYgGEBGBaAYQGG" +
            "BWBYAIYFGBaAYQEYFmBYAIYFYFiAYQEYFoBhAYYFYFgAhgUYFoBhARgWYFgAhgVgWIBhARgWYFgAhgVgWIBhARgWgGEBhgVgWACG" +
            "BRgWgGEBGBZgWACGBWBYgGEBGBaAYQGGBWBYAIYFGBaAYQEYFmBYAIYFGJYEgGEBGBZgWACGBWBYgGEBGBaAYQGGBWBYAIYFGBaA" +
            "YQEYFmBYAIYFYFiAYQEYFoBhAYYFYFgAhgUYFoBhARgWYFgAhgUYFoBhARgWYFgAhgVgWIBhARgWgGEBhgVgWACGBRgWgGEBGBZg" +
            "WACGBWBYgGEBGBaAYQGGBWBYAIYFGBaAYQEYFmBYAIYFGBaAYQEYFmBYAIYFYFiAYQEYFoBhAYYFYFgAhgUYFoBhARgWYFgAhgVg" +
            "WIBhARgWgGEBhgVgWACGBRgWwK4Bg5MFPMK92PEAAAAASUVORK5CYII=",
        )
    }
}
