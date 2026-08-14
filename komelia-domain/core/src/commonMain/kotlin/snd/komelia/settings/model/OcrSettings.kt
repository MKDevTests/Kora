package snd.komelia.settings.model

import kotlinx.serialization.Serializable

@Serializable
data class OcrSettings(
    val enabled: Boolean = false,
    val selectedLanguage: OcrLanguage = OcrLanguage.LATIN,
    val engine: OcrEngine = OcrEngine.RAPID_OCR,
    val mergeBoxes: Boolean = true,
    val rapidOcrModel: RapidOcrModel = RapidOcrModel.PP_OCR_V6_SMALL,
)

@Serializable
enum class OcrLanguage {
    LATIN,
    CHINESE,
    DEVANAGARI,
    JAPANESE,
    KOREAN
}

/**
 * ML Kit is no longer offered in the UI: on comic pages it misses whole
 * bubbles, which is a detection weakness no setting fixes. The value stays so
 * that settings persisted before that decision still deserialise, and the
 * implementation stays as the only engine that needs no model download.
 */
@Serializable
enum class OcrEngine {
    ML_KIT,
    RAPID_OCR
}

@Serializable
enum class RapidOcrModel {
    /**
     * PP-OCRv6 small, two generations ahead of the v4 models below: it detects
     * the bubbles the others miss, and covers 50 languages (English, Japanese
     * and the Latin scripts) with a single recogniser. Needs the v6 bundle —
     * see RAPID_OCR_MODELS_DEFAULT_URL.
     */
    PP_OCR_V6_SMALL,

    // v4 models, kept only so that a setting persisted before v6 arrived still
    // deserialises. They are not offered in the UI: v6 reads the same pages
    // better and covers every language they split between them.
    ENGLISH_CHINESE,
    ENGLISH_ONLY,
    LATIN_MULTILINGUAL,
    JAPANESE,
    KOREAN,
    ARABIC,
    HEBREW
}
