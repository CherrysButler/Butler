package com.cherry.butler.core.data.remote

import kotlinx.serialization.json.JsonPrimitive
import com.cherry.butler.core.data.remote.dto.PersonaGroupDto
import com.cherry.butler.core.config.JanitorConfig
import com.cherry.butler.core.data.remote.dto.PersonaDto
import com.cherry.butler.core.data.remote.dto.ProfileCountsDto
import com.cherry.butler.core.data.remote.dto.ProfileDto
import com.cherry.butler.core.network.ApiCall
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import com.cherry.butler.core.data.remote.dto.PersonaPatch
import com.cherry.butler.core.data.remote.dto.PronounsDto
import kotlinx.serialization.json.JsonNull
import com.cherry.butler.core.data.remote.dto.UploadSlotDto
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Profile and personas. `GET /profiles/mine` carries the entire AI configuration
 * including the user's proxy API keys (docs/JANITOR_API.md §17.1), so its result must never be
 * logged or written to the mirror — it is held in memory by the repository and forwarded
 * into the generation envelope as-is.
 */
@Singleton
class ProfileRemoteSource @Inject constructor(
    private val apiCall: ApiCall,
    private val json: Json,
) {
    private val base = JanitorConfig.BACKEND_BASE
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    suspend fun mine(): ProfileDto {
        val request = Request.Builder().url("$base/profiles/mine").get().build()
        return apiCall.execute(request) { body -> json.decodeFromString(ProfileDto.serializer(), body) }
    }

    suspend fun counts(): ProfileCountsDto {
        val request = Request.Builder().url("$base/profiles/mine/counts").get().build()
        return apiCall.execute(request) { body -> json.decodeFromString(ProfileCountsDto.serializer(), body) }
    }

    /**
     * One key at a time, exactly as the official client does (docs/JANITOR_API.md §21.1). Sending
     * the whole config back would ship the user's keys over the wire on every toggle and
     * clobber fields added server-side.
     */
    suspend fun patchMine(patch: JsonObject) {
        val request = Request.Builder()
            .url("$base/profiles/mine")
            .patch(patch.toString().toRequestBody(jsonMedia))
            .build()
        apiCall.execute(request) { }
    }

    suspend fun patchConfigKey(key: String, value: kotlinx.serialization.json.JsonElement) {
        patchMine(buildJsonObject { put("config", buildJsonObject { put(key, value) }) })
    }

    /** Full update; the server wants every field and the persona's own id in the body. */
    suspend fun patchPersona(body: PersonaPatch) {
        val request = Request.Builder()
            .url("$base/personas/${body.id}")
            .patch(json.encodeToString(PersonaPatch.serializer(), body).toRequestBody(jsonMedia))
            .build()
        apiCall.execute(request, maxAttempts = 1) { }
    }

    /**
     * Step one of an upload (§23.2): `avatar` lands in the persona picture folder, ready at
     * once; `profile-avatar` lands in `profile-avatar-pending/` and waits for approval.
     */
    suspend fun uploadSlot(type: String, extension: String): UploadSlotDto {
        val body = buildJsonObject { put("extension", extension); put("type", type) }
        val request = Request.Builder().url("$base/upload/uploadFile").post(body.toString().toRequestBody(jsonMedia)).build()
        return apiCall.execute(request, maxAttempts = 1) { raw -> json.decodeFromString(UploadSlotDto.serializer(), raw) }
    }

    /**
     * `POST /personas {name, appearance, avatar, pronouns, groupId}` → 201 and the new row
     * (verified 2026-10-04; `avatar` may be empty).
     */
    suspend fun createPersona(name: String, appearance: String, avatar: String, pronouns: PronounsDto?): PersonaDto {
        val body = buildJsonObject {
            put("name", name)
            put("appearance", appearance)
            put("avatar", avatar)
            put("pronouns", pronouns?.let { json.encodeToJsonElement(PronounsDto.serializer(), it) } ?: JsonNull)
            put("groupId", JsonNull)
        }
        val request = Request.Builder().url("$base/personas").post(body.toString().toRequestBody(jsonMedia)).build()
        return apiCall.execute(request, maxAttempts = 1) { raw -> json.decodeFromString(PersonaDto.serializer(), raw) }
    }

    /** `DELETE /personas/{id}` → 200 `true` (verified 2026-10-04). */
    suspend fun deletePersona(id: String) {
        val request = Request.Builder().url("$base/personas/$id").delete().build()
        apiCall.execute(request, maxAttempts = 1) { }
    }

    // ---- persona groups (all verified live 2026-10-05, docs/JANITOR_API.md §4.6) --------

    suspend fun groups(): List<PersonaGroupDto> {
        val request = Request.Builder().url("$base/persona-groups/mine").get().build()
        return apiCall.execute(request) { body -> json.decodeFromString(ListSerializer(PersonaGroupDto.serializer()), body) }
    }

    /** `POST /persona-groups {name, description, color}` → 201 and the group. */
    suspend fun createGroup(name: String, color: String): PersonaGroupDto {
        val body = buildJsonObject { put("name", name); put("description", ""); put("color", color) }
        val request = Request.Builder().url("$base/persona-groups").post(body.toString().toRequestBody(jsonMedia)).build()
        return apiCall.execute(request, maxAttempts = 1) { raw -> json.decodeFromString(PersonaGroupDto.serializer(), raw) }
    }

    /** `PATCH /persona-groups/{id} {name, color}` → 200 and the group. */
    suspend fun updateGroup(id: String, name: String, color: String): PersonaGroupDto {
        val body = buildJsonObject { put("name", name); put("color", color) }
        val request = Request.Builder().url("$base/persona-groups/$id").patch(body.toString().toRequestBody(jsonMedia)).build()
        return apiCall.execute(request, maxAttempts = 1) { raw -> json.decodeFromString(PersonaGroupDto.serializer(), raw) }
    }

    /** `DELETE /persona-groups/{id}` → `true`; its personas stay, ungrouped. */
    suspend fun deleteGroup(id: String) {
        val request = Request.Builder().url("$base/persona-groups/$id").delete().build()
        apiCall.execute(request, maxAttempts = 1) { }
    }

    /** `PATCH /persona-groups/reorder {groups: [{id, order}]}` (50 at most) → `true`. */
    suspend fun reorderGroups(ids: List<String>) {
        val body = buildJsonObject {
            put("groups", kotlinx.serialization.json.buildJsonArray {
                ids.forEachIndexed { i, id -> add(buildJsonObject { put("id", id); put("order", i + 1) }) }
            })
        }
        val request = Request.Builder().url("$base/persona-groups/reorder").patch(body.toString().toRequestBody(jsonMedia)).build()
        apiCall.execute(request, maxAttempts = 1) { }
    }

    /** `PATCH /personas/{id}/group {groupId}`; null takes it out of any group. */
    suspend fun movePersona(personaId: String, groupId: String?) {
        val body = buildJsonObject { put("groupId", groupId?.let(::JsonPrimitive) ?: JsonNull) }
        val request = Request.Builder().url("$base/personas/$personaId/group").patch(body.toString().toRequestBody(jsonMedia)).build()
        apiCall.execute(request, maxAttempts = 1) { }
    }

    /** A bare array, not an envelope. */
    suspend fun personas(): List<PersonaDto> {
        val request = Request.Builder().url("$base/personas/mine").get().build()
        return apiCall.execute(request) { body ->
            json.decodeFromString(ListSerializer(PersonaDto.serializer()), body)
        }
    }
}
