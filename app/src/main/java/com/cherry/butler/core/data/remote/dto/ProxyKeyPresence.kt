package com.cherry.butler.core.data.remote.dto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/**
 * Reads a proxy `api_key` field and keeps only whether it is non-empty. The key string
 * is never stored in a field, so it cannot leak through a `toString`, a log line, or a
 * saved state bundle.
 */
object ProxyKeyPresence : KSerializer<Boolean> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("ProxyKeyPresence", PrimitiveKind.BOOLEAN)

    override fun deserialize(decoder: Decoder): Boolean {
        val element = (decoder as? JsonDecoder)?.decodeJsonElement() ?: return decoder.decodeString().isNotEmpty()
        return element is JsonPrimitive && element !is JsonNull && element.content.isNotEmpty()
    }

    override fun serialize(encoder: Encoder, value: Boolean) {
        error("A proxy key is never written from a decoded config")
    }
}
