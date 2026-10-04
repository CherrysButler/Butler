package com.cherry.butler.core.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/*
 * Profile and persona wire shapes — verified 2026-09-23 (docs/JANITOR_API.md §17.1, §24).
 */

/**
 * `GET /profiles/mine`.
 *
 * [config] is kept as a raw object on purpose: it is ~30 keys including the user's
 * proxy API keys and prompts, it is forwarded into the generation envelope as
 * `userConfig`, and `PATCH /profiles/mine` takes one key at a time. Modelling it field
 * by field would invite exactly the read-modify-write that ships the keys back over the
 * wire and clobbers fields added server-side.
 */
@Serializable
data class ProfileDto(
    val id: String,
    val avatar: String? = null,
    val name: String = "",
    @SerialName("user_name") val userName: String = "",
    @SerialName("about_me") val aboutMe: String? = null,
    @SerialName("is_verified") val isVerified: Boolean = false,
    val config: JsonObject? = null,
    /** The default persona's appearance: the profile *is* the default persona (§26, §29). */
    @SerialName("profile") val appearance: String? = null,
)

/** `GET /personas/mine` — a bare array. */
@Serializable
data class PersonaDto(
    val id: String,
    val name: String = "",
    val avatar: String? = null,
    /** The persona's description. The field is `appearance`, not `description`. */
    val appearance: String? = null,
    /** An object, not a string enum — the earlier docs had this wrong. */
    val pronouns: PronounsDto? = null,
    val groupId: String? = null,
    val order: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class PronounsDto(
    val subjective: String? = null,
    val objective: String? = null,
    val possessive: String? = null,
    val possessivePronoun: String? = null,
    val reflexive: String? = null,
)

/** `GET /profiles/mine/counts`. */
@Serializable
data class ProfileCountsDto(
    @SerialName("character_count") val characterCount: Int = 0,
    @SerialName("persona_count") val personaCount: Int = 0,
    @SerialName("script_count") val scriptCount: Int = 0,
)

/** `PATCH /personas/{id}`: the whole persona, its own id included (verified 2026-10-03, §29). */
@Serializable
data class PersonaPatch(
    val id: String,
    val name: String,
    val appearance: String,
    val avatar: String,
    val pronouns: PronounsDto?,
)

/** `POST /upload/uploadFile` → a presigned URL to PUT the bytes to, and the name to save. */
@Serializable
data class UploadSlotDto(val url: String, val filename: String)
