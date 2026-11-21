package com.cherry.butler.core.lorebook

import androidx.room.TypeConverter
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

internal object SecondaryKeywordRuleMapper : KSerializer<SecondaryKeywordRule> {
    @JvmStatic
    @TypeConverter
    fun toCode(rule: SecondaryKeywordRule): Int = when (rule) {
        SecondaryKeywordRule.MatchAny -> 0
        SecondaryKeywordRule.NotAll -> 1
        SecondaryKeywordRule.MatchNone -> 2
        SecondaryKeywordRule.MatchAll -> 3
    }

    @JvmStatic
    @TypeConverter
    fun fromCode(code: Int?): SecondaryKeywordRule =
        SecondaryKeywordRule.entries.firstOrNull { toCode(it) == code } ?: SecondaryKeywordRule.MatchAny

    override val descriptor = PrimitiveSerialDescriptor("SecondaryKeywordRule", PrimitiveKind.INT)

    override fun serialize(encoder: Encoder, value: SecondaryKeywordRule) = encoder.encodeInt(toCode(value))

    override fun deserialize(decoder: Decoder): SecondaryKeywordRule = fromCode(decoder.decodeInt())
}
