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
 * One engine. ML Kit was removed: on comic pages it missed whole bubbles, a
 * detection weakness no setting fixed, and PP-OCRv6 reads the same pages.
 *
 * A row still holding "ML_KIT" is read back as [RAPID_OCR] — see the enum
 * lookup in ExposedImageReaderSettingsRepository, which falls back rather than
 * throwing on a name that no longer exists.
 */
@Serializable
enum class OcrEngine {
    RAPID_OCR
}

@Serializable
enum class RapidOcrModel {
    /**
     * PP-OCRv6 small, two generations ahead of the v4 models it replaced: it
     * detects the bubbles they missed, and covers 50 languages (English,
     * Japanese and the Latin scripts) with a single recogniser instead of one
     * model per script. Needs the v6 bundle — see RAPID_OCR_MODELS_DEFAULT_URL.
     *
     * A row still naming one of the old v4 models is read back as this one.
     */
    PP_OCR_V6_SMALL
}
