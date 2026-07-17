package snd.komelia.ui.common

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Shared Json for on-disk caches of Komga API models.
 *
 * `ignoreUnknownKeys` so a snapshot written by an older Kora (or a newer Komga
 * model) never hard-fails the cache.
 */
val komgaCacheJson: Json = Json { ignoreUnknownKeys = true }

/**
 * Works around a genuine round-trip bug in komga-client's
 * `KomgaReadingDirectionSerializer` (verified against 0.10.3 bytecode).
 *
 * Its `serialize` writes `encodeNull()` when the value is null, but its
 * `deserialize` calls `decodeString()` unconditionally — so re-reading anything
 * it wrote for a null throws:
 *
 *     Expected string literal but 'null' literal was found
 *         at $.series[0].metadata.readingDirection
 *
 * Omitting the key instead is not an option either — the field is required:
 *
 *     MissingFieldException: Field 'readingDirection' is required
 *
 * So a KomgaSeries whose readingDirection is null cannot survive a round-trip as
 * written. It never bites against the live server (Komga sends a string), only
 * when Kora re-encodes its own objects — i.e. exactly what a disk cache does.
 *
 * The escape is the deserializer's own blank branch:
 *
 *     val s = decodeString(); if (s.isBlank()) null else KomgaReadingDirection.valueOf(s)
 *
 * A blank string decodes back to null. So we encode null as `""` — the library's
 * own "no reading direction" encoding — and the value round-trips exactly. No
 * data is invented: `""` in, null out.
 *
 * Apply to the encoded tree before writing. Decoding needs no counterpart.
 */
fun JsonElement.encodeNullReadingDirectionAsBlank(): JsonElement = when (this) {
    is JsonObject -> JsonObject(
        mapValues { (key, value) ->
            if (key == "readingDirection" && value is JsonNull) JsonPrimitive("")
            else value.encodeNullReadingDirectionAsBlank()
        }
    )

    is JsonArray -> JsonArray(map { it.encodeNullReadingDirectionAsBlank() })
    else -> this
}
