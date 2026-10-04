package com.cherry.butler.core.data

import kotlinx.serialization.json.JsonObject
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import com.cherry.butler.core.data.remote.dto.creatorColorOrNull
import com.cherry.butler.core.data.remote.dto.shownDescription
import com.cherry.butler.core.config.JanitorConfig
import com.cherry.butler.core.data.remote.CommunityRemoteSource
import com.cherry.butler.core.data.remote.dto.CharacterDto
import com.cherry.butler.core.data.remote.dto.CommentAuthorDto
import com.cherry.butler.core.data.remote.dto.NotificationDto
import com.cherry.butler.core.markdown.fillNames
import com.cherry.butler.core.markdown.plainPreview
import com.cherry.butler.core.model.Character
import com.cherry.butler.core.util.IsoTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import javax.inject.Inject
import javax.inject.Singleton

/** Who wrote a comment or reply. */
data class CommentAuthor(val name: String, val avatarUrl: String?, val verified: Boolean)

data class Comment(
    val id: String,
    val author: CommentAuthor,
    val content: String,
    val createdAt: Long?,
    val likes: Int,
    val replyCount: Int,
    val pinned: Boolean,
    val liked: Boolean = false,
)

data class Reply(val id: String, val author: CommentAuthor, val content: String, val createdAt: Long?, val likes: Int)

data class PublishedChatCard(
    val slug: String,
    val title: String,
    val description: String,
    val publisherName: String,
    val publisherAvatar: String?,
    val messageCount: Int,
    val views: Int,
    val publishedAt: Long?,
)

data class PublishedLine(val messageId: Long, val isBot: Boolean, val text: String)

/** One reaction emoji from Janitor's own set, with its picture. */
data class Emoji(val id: String, val label: String, val imageUrl: String, val quick: Boolean)

/** How many of one emoji a line has, and whether one of them is the viewer's. */
data class ReactionTally(val emoji: String, val count: Int, val mine: Boolean)

/** The social side of a published chat: the viewer's favourite, the counts, every line's reactions. */
data class PublishedSocial(
    val favorited: Boolean,
    val favoriteCount: Int,
    val commentCount: Int,
    val viewCount: Int,
    val reactions: Map<Long, List<ReactionTally>>,
)

data class ChatComment(
    val id: String,
    val author: CommentAuthor,
    val userId: String?,
    val content: String,
    val createdAt: Long?,
    val likes: Int,
    val liked: Boolean,
    val replyCount: Int,
)

data class PublishedTranscript(
    val id: Long,
    val title: String,
    val description: String,
    val characterId: String?,
    val characterName: String,
    val characterAvatar: String?,
    val personaName: String?,
    val personaAvatar: String?,
    val publisherName: String,
    val lines: List<PublishedLine>,
)

/** Where tapping a notification goes. */
sealed interface NotificationTarget {
    data class PublishedChat(val slug: String) : NotificationTarget
    data class Character(val id: String) : NotificationTarget
}

data class Notice(
    val id: String,
    val subject: String,
    val body: String,
    val createdAt: Long?,
    val isRead: Boolean,
    val imageUrl: String?,
    val target: NotificationTarget?,
)

/**
 * The community surface: comments on a character and their replies, similar characters,
 * published chats, and the notifications feed. Read-only except replying to a comment,
 * the one write whose request shape has been captured.
 */
@Singleton
class CommunityRepository @Inject constructor(
    private val remote: CommunityRemoteSource,
    private val profile: ProfileRepository,
) {
    private val _unread = MutableStateFlow(0)
    /** Unread notifications, for the bell's badge. */
    val unread: StateFlow<Int> = _unread.asStateFlow()

    suspend fun comments(characterId: String, page: Int): List<Comment> =
        remote.comments(characterId, page).map {
            Comment(it.id, it.author.toAuthor(), it.content, IsoTime.parseMillis(it.createdAt), it.likeCount, it.commentCount, it.isPinned, it.isLikedByUser)
        }

    suspend fun replies(commentId: String): List<Reply> =
        remote.replies(commentId).map { Reply(it.id, it.author.toAuthor(), it.content, IsoTime.parseMillis(it.createdAt), it.likeCount) }
            .sortedBy { it.createdAt ?: 0L }

    suspend fun reply(commentId: String, content: String) = remote.reply(commentId, content.trim())

    suspend fun postComment(characterId: String, content: String, isLike: Boolean) = remote.postComment(characterId, content.trim(), isLike)

    suspend fun deleteComment(commentId: String) = remote.deleteComment(commentId)

    /** The signed-in user's handle, to know which comments are theirs. */
    suspend fun myUserName(): String? = runCatching { profile.profile().userName }.getOrNull()

    suspend fun myUserId(): String? = runCatching { profile.profile().id }.getOrNull()

    suspend fun isFavorite(characterId: String) = remote.isFavorite(characterId)
    suspend fun favoriteCount(characterId: String) = remote.favoriteCount(characterId)
    suspend fun setFavorite(characterId: String, on: Boolean) = remote.setFavorite(characterId, on)
    suspend fun isFollowing(userId: String) = remote.isFollowing(userId)
    suspend fun setFollowing(userId: String, on: Boolean) = remote.setFollowing(userId, on)
    suspend fun toggleLike(commentId: String) = remote.toggleCommentLike(commentId)

    suspend fun similar(characterId: String): List<Character> = remote.similar(characterId).map { it.toCharacter() }

    suspend fun publishedChats(characterId: String): Pair<List<PublishedChatCard>, Int> {
        val page = remote.publishedChats(characterId)
        return page.chats.map { c ->
            PublishedChatCard(
                slug = c.slug,
                title = c.title?.takeIf { it.isNotBlank() } ?: "Untitled",
                description = c.description.orEmpty().plainPreview(200),
                publisherName = c.publisher?.username.orEmpty(),
                publisherAvatar = c.publisher?.avatar,
                messageCount = c.messageCount,
                views = (c.stats?.get("view_count") as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 0,
                publishedAt = IsoTime.parseMillis(c.publishedAt),
            )
        } to page.total
    }

    suspend fun publishedChat(slug: String): PublishedTranscript {
        val d = remote.publishedChat(slug)
        val persona = d.personas.firstOrNull { it.isDefault } ?: d.personas.firstOrNull()
        val charName = d.character?.chatName?.takeIf { it.isNotBlank() } ?: d.character?.name.orEmpty()
        return PublishedTranscript(
            id = d.chat?.id ?: 0L,
            title = d.chat?.title?.takeIf { it.isNotBlank() } ?: "Published chat",
            description = d.chat?.description.orEmpty(),
            characterId = d.character?.id,
            characterName = d.character?.name.orEmpty(),
            characterAvatar = JanitorConfig.avatarUrl(d.character?.avatar),
            personaName = persona?.name,
            personaAvatar = persona?.avatar,
            publisherName = d.publisher?.username.orEmpty(),
            lines = d.chatMessages.filter { it.isMain }.sortedBy { IsoTime.parseMillis(it.createdAt) ?: 0L }
                .map { PublishedLine(it.id, it.isBot, it.message.fillNames(user = persona?.name, char = charName, markUser = true)) },
        )
    }

    private var emojiCache: List<Emoji>? = null

    /** Janitor's reaction set, quick ones first; fetched once per process. */
    suspend fun emojis(): List<Emoji> = emojiCache ?: remote.emojiDefinitions().all
        .sortedWith(compareByDescending<com.cherry.butler.core.data.remote.dto.EmojiDto> { it.isQuick }.thenBy { it.sortOrder })
        .map { Emoji(it.id, it.label, JanitorConfig.MEDIA_BASE + it.img, it.isQuick) }
        .also { emojiCache = it }

    /** Viewer and activity together: the favourite, the counts and each line's reactions. */
    suspend fun publishedSocial(chatId: Long): PublishedSocial = coroutineScope {
        val viewer = async { runCatching { remote.publishedViewer(chatId) }.getOrNull() }
        val activity = async { runCatching { remote.publishedActivity(chatId) }.getOrNull() }
        val v = viewer.await()
        val a = activity.await()
        val mine = v?.userReactions.orEmpty().mapNotNull { r -> r.messageId()?.let { it to r.emoji() } }.toSet()
        val tallies = HashMap<Long, MutableMap<String, Int>>()
        a?.reactions.orEmpty().forEach { r ->
            val id = r.messageId() ?: return@forEach
            val emoji = r.emoji() ?: return@forEach
            val count = (r["count"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 1
            tallies.getOrPut(id) { LinkedHashMap() }.merge(emoji, count, Int::plus)
        }
        // The viewer's own reactions count even when the activity feed hasn't caught up.
        mine.forEach { (id, emoji) -> if (emoji != null) tallies.getOrPut(id) { LinkedHashMap() }.putIfAbsent(emoji, 1) }
        fun stat(key: String) = (a?.stats?.get(key) as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 0
        PublishedSocial(
            favorited = v?.isFavorited ?: false,
            favoriteCount = stat("favorite_count"),
            commentCount = stat("comment_count"),
            viewCount = stat("view_count"),
            reactions = tallies.mapValues { (id, m) -> m.map { (e, n) -> ReactionTally(e, n, (id to e) in mine) } },
        )
    }

    suspend fun registerView(chatId: Long) = runCatching { remote.registerView(chatId) }

    suspend fun react(chatId: Long, messageId: Long, emojiId: String, on: Boolean) = remote.react(chatId, messageId, emojiId, on)

    suspend fun setPublishedFavorite(chatId: Long, on: Boolean) = remote.setPublishedFavorite(chatId, on)

    /** One page of a published chat's comments, deleted ones left out; second value: more pages. */
    suspend fun chatComments(chatId: Long, page: Int): Pair<List<ChatComment>, Boolean> {
        val d = remote.chatComments(chatId, page)
        val list = d.comments.filter { it.deletedAt == null }.map { c ->
            ChatComment(
                id = c.id,
                author = CommentAuthor(name = c.userName.orEmpty(), avatarUrl = JanitorConfig.personaAvatarUrl(c.userAvatar), verified = c.userVerified),
                userId = c.userId,
                content = c.content,
                createdAt = IsoTime.parseMillis(c.createdAt),
                likes = c.likeCount,
                liked = c.isLikedByUser,
                replyCount = c.replyCount,
            )
        }
        return list to d.hasMore
    }

    suspend fun postChatComment(chatId: Long, content: String) = remote.postChatComment(chatId, content.trim())

    suspend fun likeChatComment(commentId: String, on: Boolean): Int? = remote.likeChatComment(commentId, on)

    suspend fun deleteChatComment(commentId: String) = remote.deleteChatComment(commentId)

    private fun JsonObject.messageId(): Long? =
        ((this["message_id"] ?: this["messageId"]) as? JsonPrimitive)?.contentOrNull?.toLongOrNull()

    private fun JsonObject.emoji(): String? =
        ((this["emoji"] ?: this["emoji_id"] ?: this["emojiId"]) as? JsonPrimitive)?.contentOrNull

    suspend fun notifications(): List<Notice> {
        val me = profile.profile().id
        val list = remote.notifications(me).notifications.filterNot { it.isArchived }.map { it.toNotice() }
        // Seen is read: the list keeps its marks for this visit, the bell clears.
        if (list.any { !it.isRead }) runCatching { remote.markAllRead(me) }
            .onSuccess { markedReadAt = System.nanoTime(); _unread.value = 0 }
            .onFailure { _unread.value = list.count { n -> !n.isRead } }
        else _unread.value = 0
        return list
    }

    suspend fun notificationPreferences(): Map<String, Boolean> = remote.notificationPreferences(profile.profile().id)

    suspend fun setNotificationPreference(workflow: String, active: Boolean) =
        remote.setNotificationPreference(profile.profile().id, workflow, active)

    /** When the list was last marked read; a count asked for before then is out of date. */
    @Volatile private var markedReadAt = 0L

    suspend fun refreshUnread() {
        val askedAt = System.nanoTime()
        runCatching { remote.unreadCount(profile.profile().id) }
            .onSuccess { if (askedAt > markedReadAt) _unread.value = it }
    }
}

private fun CommentAuthorDto?.toAuthor() = CommentAuthor(
    name = this?.userName?.takeIf { it.isNotBlank() } ?: "Someone",
    avatarUrl = this?.avatar,
    verified = this?.isVerified == true,
)

private fun CharacterDto.toCharacter(): Character {
    val desc = shownDescription
    return Character(
        id = id, name = name, description = desc, avatar = avatar,
        creatorName = creatorName, creatorVerified = creatorVerified, creatorPlusBadge = creatorPlusBadge,
        chatCount = stats.chat, messageCount = stats.message,
        isNsfw = isNsfw, isImageNsfw = isImageNsfw,
        tagNames = tags.map { it.name }, customTags = customTags,
        blurb = desc.plainPreview(220).fillNames(user = null, char = name),
        totalTokens = totalTokens,
        isProxyEnabled = isProxyEnabled,
        creatorColor = creatorColorOrNull,
    )
}

private val CHARACTER_ID = Regex("""/characters?/([0-9a-fA-F-]{36})""")

private fun NotificationDto.toNotice(): Notice {
    fun field(key: String) = (data?.get(key) as? JsonPrimitive)?.contentOrNull
    val url = redirect?.url.orEmpty()
    val target = when {
        url.startsWith("/chats/public/") -> NotificationTarget.PublishedChat(url.removePrefix("/chats/public/").substringBefore('/').substringBefore('?'))
        else -> CHARACTER_ID.find(url)?.let { NotificationTarget.Character(it.groupValues[1]) }
            ?: field("characterId")?.let { NotificationTarget.Character(it) }
    }
    return Notice(
        id = id,
        subject = subject.replaceFirstChar { it.uppercase() },
        body = body,
        createdAt = IsoTime.parseMillis(createdAt),
        isRead = isRead,
        imageUrl = JanitorConfig.avatarUrl(field("characterAvatar")),
        target = target,
    )
}
