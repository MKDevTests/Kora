package snd.komelia.db

import io.github.vinceglb.filekit.PlatformFile
import kotlinx.serialization.Serializable
import snd.komelia.image.ReduceKernel
import snd.komelia.image.UpsamplingMode
import snd.komelia.image.UpscaleMode
import snd.komelia.settings.model.NightModeSettings
import snd.komelia.settings.model.ContinuousReadingDirection
import snd.komelia.settings.model.LayoutScaleType
import snd.komelia.settings.model.NcnnUpscalerSettings
import snd.komelia.settings.model.OcrLanguage
import snd.komelia.settings.model.OcrSettings
import snd.komelia.settings.model.PageDisplayLayout
import snd.komelia.settings.model.PagedReadingDirection
import snd.komelia.settings.model.PanelsFullPageDisplayMode
import snd.komelia.settings.model.ReaderFlashColor
import snd.komelia.settings.model.ReaderTapNavigationMode
import snd.komelia.settings.model.ReaderType
import snd.komelia.settings.model.ReaderType.PAGED

@Serializable
data class ImageReaderSettings(
    val readerType: ReaderType = PAGED,
    val stretchToFit: Boolean = true,
    val ncnnUpscalerSettings: NcnnUpscalerSettings = NcnnUpscalerSettings(),
    val ocrSettings: OcrSettings = OcrSettings(),
    val translationSettings: snd.komelia.settings.model.TranslationSettings =
        snd.komelia.settings.model.TranslationSettings(),
    val pagedScaleType: LayoutScaleType = LayoutScaleType.SCREEN,
    val pagedReadingDirection: PagedReadingDirection = PagedReadingDirection.LEFT_TO_RIGHT,
    val pagedPageLayout: PageDisplayLayout = PageDisplayLayout.SINGLE_PAGE,
    val continuousReadingDirection: ContinuousReadingDirection = ContinuousReadingDirection.TOP_TO_BOTTOM,
    val continuousPadding: Float = 0f,
    val continuousPageSpacing: Int = 0,
    val cropBorders: Boolean = false,

    val flashOnPageChange: Boolean = false,
    val flashDuration: Long = 100L,
    val flashEveryNPages: Int = 1,
    val flashWith: ReaderFlashColor = ReaderFlashColor.BLACK,
    val downsamplingKernel: ReduceKernel = ReduceKernel.LANCZOS3,
    val linearLightDownsampling: Boolean = false,
    val upsamplingMode: UpsamplingMode = UpsamplingMode.CATMULL_ROM,
    val loadThumbnailPreviews: Boolean = true,
    val volumeKeysNavigation: Boolean = false,

    val ortUpscalerMode: UpscaleMode = UpscaleMode.NONE,
    val ortUpscalerUserModelPath: PlatformFile? = null,
    val ortUpscalerDeviceId: Int = 0,
    val ortUpscalerTileSize: Int = 512,

    val panelsFullPageDisplayMode: PanelsFullPageDisplayMode = PanelsFullPageDisplayMode.BOTH,
    val pagedReaderTapToZoom: Boolean = true,
    val panelReaderTapToZoom: Boolean = false,
    val pagedReaderAdaptiveBackground: Boolean = true,
    val panelReaderAdaptiveBackground: Boolean = true,
    val tapNavigationMode: ReaderTapNavigationMode = ReaderTapNavigationMode.LEFT_RIGHT,
    val panelDetectionUrl: String = PANEL_DETECTION_DEFAULT_GITHUB_URL,
    val rapidOcrModelsUrl: String = RAPID_OCR_MODELS_DEFAULT_URL,
    val imageCacheSizeLimitMb: Long = 1024L,
    val pagedSplitDoublePages: Boolean = false,
    val pagedReaderAutoDirection: Boolean = true,
    val pagedAutoSkipBlankPages: Boolean = false,
    val pagedAutoDetectWebtoon: Boolean = false,
    /** Webtoons use the panel-by-panel smart reader. When false they fall back
     *  to plain continuous scroll (tap advances ~80% of the screen). */
    val webtoonSmartScroll: Boolean = true,
    val continuousReaderStopAtEnd: Boolean = true,
    /**
     * Double-tap zoom in the continuous reader. The paged reader has had its
     * own toggle forever; webtoon reading was stuck with zoom on because this
     * one did not exist. Defaults to on = the previous behaviour.
     */
    val continuousReaderTapToZoom: Boolean = true,
    /**
     * Accessibility: detect speech bubbles and invert only their pixels
     * (white bubble + black text -> black bubble + white text), leaving the
     * artwork untouched. Reduces glare for light-sensitive readers. Off by
     * default — detection is heuristic and adds per-page work.
     */
    val invertSpeechBubbles: Boolean = false,
    /**
     * Reader night mode: a warm tint over the page to cut the blue a white
     * manga page throws at you in the dark. Image reader only — the app
     * chrome is already dark and the epub reader has its own themes.
     */
    val nightMode: NightModeSettings = NightModeSettings(),
    /**
     * Minimal-UI-while-reading toggle (v1.0.11). When true, the reader's
     * "hidden controls" state is replaced by a slim bottom strip showing
     * only the [prev book] [progress slider] [next book] row plus the top
     * bar. Tapping reveals the full controls; tap again returns here.
     * When false: identical to the legacy behavior (controls fully hidden
     * by default, full UI on tap).
     */
    val keepProgressBarVisibleWhileReading: Boolean = false,
) {
    companion object {
        const val PANEL_DETECTION_DEFAULT_ORIGINAL_URL =
            "https://github.com/Snd-R/komelia-onnxruntime/releases/download/model/rf-detr-med.onnx.zip"
        const val PANEL_DETECTION_DEFAULT_GITHUB_URL =
            "https://github.com/eserero/Sipurra/releases/download/model/rf-detr-med.onnx.zip"
        /**
         * Only what the reader actually loads: the PP-OCRv6 small detector and
         * recogniser, the PP-OCRv6 tiny detector behind the Fast switch, the
         * orientation classifier and the 18708-entry dictionary. 27 MB against
         * the 105 MB of the first v6 bundle, which still carried the whole v4
         * family nothing reads any more.
         *
         * Nobody is expected to type this. Installs still holding one of the
         * older URLs below are moved onto it by V98, so the field is already
         * right when the download screen opens.
         */
        const val RAPID_OCR_MODELS_DEFAULT_URL =
            "https://github.com/MKDevTests/Kora/releases/download/model-v6.1/RapidOcrModels-v6.1.zip"
        const val RAPID_OCR_MODELS_V6_URL =
            "https://github.com/MKDevTests/Kora/releases/download/model-v6/RapidOcrModels-v6.zip"
        const val RAPID_OCR_MODELS_UPSTREAM_URL =
            "https://github.com/eserero/Sipurra/releases/download/model/RapidOcrModels.zip"
    }
}