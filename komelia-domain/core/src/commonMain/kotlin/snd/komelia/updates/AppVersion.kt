package snd.komelia.updates

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.time.Instant

@Serializable(with = AppVersionSerializer::class)
data class AppVersion(
    val major: Int,
    val minor: Int,
    val patch: Int
) : Comparable<AppVersion> {

    companion object {
        val current = AppVersion(1, 4, 6)

        fun fromString(value: String): AppVersion =
            fromStringOrNull(value) ?: error("Can't parse version number")

        /**
         * Lenient parse: null when [value] isn't a version at all.
         *
         * A repository may carry releases that aren't app builds — an asset pack
         * tagged `genre-covers`, for instance. Those used to make the whole
         * update check throw, so ONE non-version release hid every real one.
         */
        fun fromStringOrNull(value: String): AppVersion? {
            val version = value.removePrefix("v").split(".")
            val numbers = version.map { part -> part.takeWhile { it.isDigit() }.toIntOrNull() ?: return null }
            return when (numbers.size) {
                3 -> AppVersion(numbers[0], numbers[1], numbers[2])
                2 -> AppVersion(numbers[0], numbers[1], 0)
                else -> null
            }
        }
    }

    override fun compareTo(other: AppVersion): Int {
        return compareBy(
            AppVersion::major,
            AppVersion::minor,
            AppVersion::patch
        ).compare(this, other)
    }

    override fun toString(): String {
        return "$major.$minor.$patch"
    }
}

data class AppRelease(
    val version: AppVersion,
    val publishDate: Instant,
    val releaseNotesBody: String,
    val htmlUrl: String,

    val assetName: String?,
    val assetUrl: String?,
)

object AppVersionSerializer : KSerializer<AppVersion> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("AppVersion", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): AppVersion {
        val version = decoder.decodeString()
        return AppVersion.fromString(version)
    }

    override fun serialize(encoder: Encoder, value: AppVersion) {
        encoder.encodeString(value.toString())
    }
}