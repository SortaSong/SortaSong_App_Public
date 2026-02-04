package com.sortasong.sortasong.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * Custom serializer for trackNr that accepts both String and Int from JSON
 */
object TrackNrSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("trackNr", PrimitiveKind.STRING)
    
    override fun serialize(encoder: Encoder, value: String) {
        encoder.encodeString(value)
    }
    
    override fun deserialize(decoder: Decoder): String {
        return if (decoder is JsonDecoder) {
            val element = decoder.decodeJsonElement()
            when (element) {
                is JsonPrimitive -> {
                    // Try as int first, then as string
                    element.intOrNull?.toString() ?: element.content
                }
                else -> element.toString()
            }
        } else {
            decoder.decodeString()
        }
    }
}

/**
 * Represents a track in a custom game loaded from game_info.json
 */
@Serializable
data class CustomTrack(
    @Serializable(with = TrackNrSerializer::class)
    val trackNr: String,
    val song: String,
    val artist: String,
    val releaseDate: String,
    val releaseYear: Int,
    val originalFileName: String? = null
)

/**
 * Represents the structure of a game_info.json file for custom games
 */
@Serializable
data class CustomGameInfo(
    val game: String,
    val folderName: String,
    val linkIdentifier: String = "",
    val cardPurchaseUrl: String = "",
    val hasPhysicalCards: Boolean = false,
    val isCustom: Boolean = true,
    val createdAt: String? = null,
    val tracks: List<CustomTrack>
)
