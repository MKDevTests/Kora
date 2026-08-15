package snd.komelia.settings.model

import kotlinx.serialization.Serializable

/**
 * On-device translation of the page being read. Off by default: it costs a
 * ~30MB model download per language and only makes sense once OCR is on, since
 * it translates what the OCR found.
 */
@Serializable
data class TranslationSettings(
    val enabled: Boolean = false,
    val source: TranslationLanguage = TranslationLanguage.ENGLISH,
    val target: TranslationLanguage = TranslationLanguage.FRENCH,
)

/**
 * The subset of ML Kit's languages we offer. [code] is the BCP-47 tag ML Kit
 * identifies its models by, so the Android side never hard-codes a mapping
 * table of its own.
 */
@Serializable
enum class TranslationLanguage(val code: String) {
    ENGLISH("en"),
    FRENCH("fr"),
    JAPANESE("ja"),
    SPANISH("es"),
    GERMAN("de"),
    ITALIAN("it"),
    PORTUGUESE("pt"),
    DUTCH("nl"),
}
