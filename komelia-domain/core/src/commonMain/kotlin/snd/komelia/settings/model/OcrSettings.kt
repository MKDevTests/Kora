package snd.komelia.settings.model

import kotlinx.serialization.Serializable

@Serializable
data class OcrSettings(
    val enabled: Boolean = false,
    val selectedLanguage: OcrLanguage = OcrLanguage.LATIN,
    val engine: OcrEngine = OcrEngine.ML_KIT,
    val mergeBoxes: Boolean = true,
    val rapidOcrModel: RapidOcrModel = RapidOcrModel.ENGLISH_CHINESE,
)

@Serializable
enum class OcrLanguage {
    LATIN,
    CHINESE,
    DEVANAGARI,
    JAPANESE,
    KOREAN
}

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
    ENGLISH_CHINESE,
    ENGLISH_ONLY,
    LATIN_MULTILINGUAL,
    JAPANESE,
    KOREAN,
    ARABIC,
    HEBREW
}
