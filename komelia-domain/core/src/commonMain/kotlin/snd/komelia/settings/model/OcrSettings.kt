package snd.komelia.settings.model

import kotlinx.serialization.Serializable

@Serializable
data class OcrSettings(
    val enabled: Boolean = false,
    val selectedLanguage: OcrLanguage = OcrLanguage.LATIN,
    val engine: OcrEngine = OcrEngine.RAPID_OCR,
    val mergeBoxes: Boolean = true,
    val rapidOcrModel: RapidOcrModel = RapidOcrModel.PP_OCR_V6_SMALL,
    val speedMode: OcrSpeedMode = OcrSpeedMode.NORMAL,
)

/**
 * Which detector finds the text. Only the detector changes — recognition is
 * PP-OCRv6 small either way, because that is where reading quality comes from
 * and where the tiny model loses the most (81.3 to 73.5 on the PaddleOCR
 * average, and 68.4 to 54.7 on artistic text).
 *
 * Detection is worth choosing between because it runs on the whole page rather
 * than on crops: measured here, 1.7-1.9 s of a 3.3-4.4 s scan on a 1400x1993
 * comic page, and 0.65-0.74 s of 0.7-3.5 s on a 835x1200 manga page.
 */
@Serializable
enum class OcrSpeedMode {
    /** PP-OCRv6 small detector. Finds the SFX and the awkward panels. */
    NORMAL,

    /**
     * PP-OCRv6 tiny detector (0.43M parameters). Costs 1.3 points of recall on
     * printed English and 5.2 on artistic text — which on a comic page means
     * sound effects and lettering over artwork, not dialogue. Falls back to
     * NORMAL when the tiny model is not in the downloaded bundle.
     */
    FAST,
}

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
