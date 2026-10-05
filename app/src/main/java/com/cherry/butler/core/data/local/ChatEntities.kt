package com.cherry.butler.core.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A chat as mirrored to disk, one row per server chat. Ordered by [lastMessageAt] so the
 * chats tab reads from disk in the server's order without a network call.
 */
@Entity(
    tableName = "chats",
    indices = [Index(value = ["lastMessageAt"])],
)
data class ChatEntity(
    @PrimaryKey val id: Long,
    val characterId: String,
    val characterName: String,
    val characterAvatar: String?,
    val characterDeleted: Boolean,
    /** Epoch millis; null for a chat with no messages yet. */
    val lastMessageAt: Long?,
    val lastMessagePreview: String?,
    val messageCount: Int,
    val isPublic: Boolean,
    /** The chat owner's user id — the envelope's `chat.user_id` and `profile.id`. */
    val userId: String,
    val personaId: String?,
    /**
     * `personas[0]` from the chat detail. Its `appearance` is what the official client
     * sends as `profile.user_appearance` when no persona is explicitly selected —
     * verified against the capture; it is not `about_me`.
     */
    val defaultPersonaId: String?,
    val defaultPersonaAppearance: String?,
    /** The name that fills `{{user}}` in this chat: the chat's persona, else the default one. */
    val personaName: String? = null,
    val summary: String?,
    val summaryChatId: Long?,
    /** True once `GET /chats/{id}` has been mirrored, so the transcript can trust its rows. */
    val detailLoaded: Boolean,
    val cachedAt: Long,
    /** The folders this chat is in, as `,id,id,` so a folder can be matched with LIKE; "" for none. */
    @ColumnInfo(defaultValue = "") val folderIds: String = "",
    /**
     * The character's openings, from the chat detail's `character.first_messages`. The
     * character endpoint hides them on mobile ("hidden on mobile (N tokens)"); the chat
     * detail carries the real text (verified 2026-10-05). The server seeds the first one.
     */
    @ColumnInfo(defaultValue = "") val intros: List<String> = emptyList(),
)

/** `[a, b]` → `,a,b,` (and nothing → ""), the form [ChatEntity.folderIds] keeps. */
fun encodeFolderIds(ids: Collection<String>): String = if (ids.isEmpty()) "" else ids.joinToString(",", prefix = ",", postfix = ",")

fun decodeFolderIds(stored: String): List<String> = stored.split(',').filter { it.isNotEmpty() }

/**
 * What the user did to a character's row that Janitor has no field for: pinned to the top,
 * or archived out of the main list. Kept only on this phone.
 */
@Entity(tableName = "group_marks")
data class GroupMarkEntity(
    @PrimaryKey val characterId: String,
    /** When it was pinned (newest pin first); null if not pinned. */
    val pinnedAt: Long?,
    val archived: Boolean,
)

/**
 * One character the user has talked to, folded from their chat rows: the shape the chats
 * tab reads. Produced by a GROUP BY query, so it is a projection, not a table.
 */
data class ChatGroupRow(
    val characterId: String,
    val characterName: String,
    val characterAvatar: String?,
    val characterDeleted: Boolean,
    val latestChatId: Long,
    /** 0 when no chat with this character has a message yet. */
    val lastMessageAt: Long,
    val lastMessagePreview: String?,
    val personaName: String?,
    val chatCount: Int,
    val pinned: Boolean = false,
)

/**
 * One transcript row. Rows exist **before** the server knows about them — a queued send,
 * a reply still streaming — so the primary key is local and [serverId] is nullable.
 * SQLite treats multiple NULLs in a unique index as distinct, which is exactly the
 * semantics wanted: many unconfirmed rows, never two rows for one server message.
 *
 * This is the concrete form of "persist before the network": the composer writes here
 * first, and the transcript renders from here alone.
 */
@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["serverId"], unique = true),
        Index(value = ["chatId", "createdAt"]),
    ],
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val serverId: Long?,
    val chatId: Long,
    val isBot: Boolean,
    val isMain: Boolean,
    val text: String,
    /** Epoch millis. Local rows get the device clock; server rows get the server's `created_at`. */
    val createdAt: Long,
    val rating: String?,
    val personaId: String?,
    val generationRequestIds: List<String>,
    /**
     * Extracted reasoning. The server never returns this (verified §24), so it lives only
     * here — the client-local column the official client fakes with `_localThinkingContent`.
     */
    val thinking: String?,
    /** null once complete; [MessageStreamState] while text is still arriving or was cut off. */
    val streamState: String?,
    val cachedAt: Long,
)

object MessageStreamState {
    /** Deltas are arriving; text is a prefix of the final reply. */
    const val STREAMING = "streaming"
    /** The stream died after some text arrived. Recoverable with `CONTINUE`, not a regenerate. */
    const val PARTIAL = "partial"
}

/**
 * The outbox. One row per send attempt, owning the whole sequence the API leaves to the
 * client (user message → generation → bot message → `is_main`), so a crash between steps
 * resumes from the step that was reached rather than leaving the chat inconsistent
 * server-side.
 */
@Entity(
    tableName = "send_jobs",
    indices = [Index(value = ["chatId"]), Index(value = ["state"])],
)
data class SendJobEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chatId: Long,
    val characterId: String,
    /** The user message this job posts; null for ALTERNATIVE / CONTINUE, which add none. */
    val userMessageLocalId: Long?,
    /** The row the reply is written into. */
    val botMessageLocalId: Long?,
    /** NEW, ALTERNATIVE, CONTINUE — the wire value of `generateMode`. */
    val mode: String,
    val state: String,
    /** The step to resume at after [SendJobState.WAITING_RETRY]; null means from the start. */
    val resumeState: String?,
    val attempt: Int,
    /** Epoch millis before which the job must not be retried (backoff). */
    val nextAttemptAt: Long,
    val lastError: String?,
    /** Whether [lastError] is worth retrying automatically — the ApiError verdict, persisted. */
    val lastErrorRetryable: Boolean,
    val generationRequestId: String?,
    val createdAt: Long,
    val updatedAt: Long,
    /**
     * A one-off instruction for this reply only ("be more enthusiastic"): added to the
     * request as an out-of-character note, never saved to the chat.
     */
    @ColumnInfo(defaultValue = "NULL") val guidance: String? = null,
)

object SendJobState {
    const val QUEUED = "queued"
    const val POSTING_USER = "posting_user"
    const val GENERATING = "generating"
    const val POSTING_BOT = "posting_bot"
    const val SELECTING = "selecting"
    const val COMPLETE = "complete"
    /** Retryable failure; [SendJobEntity.nextAttemptAt] says when. */
    const val WAITING_RETRY = "waiting_retry"
    /** Terminal. Needs the user. */
    const val FAILED = "failed"
    const val CANCELLED = "cancelled"

    val active = setOf(QUEUED, POSTING_USER, GENERATING, POSTING_BOT, SELECTING, WAITING_RETRY)
}
