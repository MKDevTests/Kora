package snd.komelia.db

import io.github.vinceglb.filekit.PlatformFile
import kotlinx.serialization.Serializable
import snd.komelia.image.ReduceKernel
import snd.komelia.image.UpsamplingMode
import snd.komelia.image.UpscaleMode
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
    val continuousReaderStopAtEnd: Boolean = true,
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
        const val RAPID_OCR_MODELS_DEFAULT_URL =
            "https://github.com/eserero/Sipurra/releases/download/model/RapidOcrModels.zip"
    }
}