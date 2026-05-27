package snd.komelia.db.settings

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.path
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert
import snd.komelia.db.ExposedRepository
import snd.komelia.db.ImageReaderSettings
import snd.komelia.db.defaultBookId
import snd.komelia.db.tables.ImageReaderSettingsTable
import snd.komelia.image.ReduceKernel
import snd.komelia.image.UpsamplingMode
import snd.komelia.image.UpscaleMode
import snd.komelia.settings.model.ContinuousReadingDirection
import snd.komelia.settings.model.LayoutScaleType
import snd.komelia.settings.model.NcnnEngine
import snd.komelia.settings.model.NcnnUpscalerSettings
import snd.komelia.settings.model.OcrEngine
import snd.komelia.settings.model.OcrLanguage
import snd.komelia.settings.model.OcrSettings
import snd.komelia.settings.model.PageDisplayLayout
import snd.komelia.settings.model.PagedReadingDirection
import snd.komelia.settings.model.PanelsFullPageDisplayMode
import snd.komelia.settings.model.RapidOcrModel
import snd.komelia.settings.model.ReaderFlashColor
import snd.komelia.settings.model.ReaderTapNavigationMode
import snd.komelia.settings.model.ReaderType

class ExposedImageReaderSettingsRepository(database: Database) : ExposedRepository(database) {

    suspend fun get(): ImageReaderSettings? {
        return transaction {
            ImageReaderSettingsTable.selectAll()
                .where { ImageReaderSettingsTable.bookId.eq(defaultBookId) }
                .firstOrNull()
                ?.let {

                    ImageReaderSettings(
                        readerType = ReaderType.valueOf(it[ImageReaderSettingsTable.readerType]),
                        stretchToFit = it[ImageReaderSettingsTable.stretchToFit],
                        ncnnUpscalerSettings = NcnnUpscalerSettings(
                            enabled = it[ImageReaderSettingsTable.ncnnEnabled],
                            engine = NcnnEngine.valueOf(it[ImageReaderSettingsTable.ncnnEngine]),
                            model = it[ImageReaderSettingsTable.ncnnModel],
                            gpuId = it[ImageReaderSettingsTable.ncnnGpuId],
                            ttaMode = it[ImageReaderSettingsTable.ncnnTtaMode],
                            numThreads = it[ImageReaderSettingsTable.ncnnNumThreads],
                            upscaleOnLoad = it[ImageReaderSettingsTable.ncnnUpscaleOnLoad],
                            upscaleThreshold = it[ImageReaderSettingsTable.ncnnUpscaleThreshold],
                            ncnnUpscalerUrl = it[ImageReaderSettingsTable.ncnnUpscalerUrl],
                        ),
                        ocrSettings = OcrSettings(
                            enabled = it[ImageReaderSettingsTable.ocrEnabled],
                            selectedLanguage = OcrLanguage.valueOf(it[ImageReaderSettingsTable.ocrLanguage]),
                            engine = OcrEngine.valueOf(it[ImageReaderSettingsTable.ocrEngine]),
                            rapidOcrModel = RapidOcrModel.valueOf(it[ImageReaderSettingsTable.ocrRapidOcrModel]),
                            mergeBoxes = it[ImageReaderSettingsTable.ocrMergeBoxes],
                        ),
                        pagedScaleType = LayoutScaleType.valueOf(it[ImageReaderSettingsTable.pagedScaleType]),
                        pagedReadingDirection = PagedReadingDirection.valueOf(it[ImageReaderSettingsTable.pagedReadingDirection]),
                        pagedPageLayout = PageDisplayLayout.valueOf(it[ImageReaderSettingsTable.pagedPageLayout]),
                        continuousReadingDirection = ContinuousReadingDirection.valueOf(it[ImageReaderSettingsTable.continuousReadingDirection]),
                        continuousPadding = it[ImageReaderSettingsTable.continuousPadding],
                        continuousPageSpacing = it[ImageReaderSettingsTable.continuousPageSpacing],
                        cropBorders = it[ImageReaderSettingsTable.cropBorders],
                        flashOnPageChange = it[ImageReaderSettingsTable.flashOnPageChange],
                        flashDuration = it[ImageReaderSettingsTable.flashDuration],
                        flashEveryNPages = it[ImageReaderSettingsTable.flashEveryNPages],
                        flashWith = ReaderFlashColor.valueOf(it[ImageReaderSettingsTable.flashWith]),
                        downsamplingKernel = ReduceKernel.valueOf(it[ImageReaderSettingsTable.downsamplingKernel]),
                        linearLightDownsampling = it[ImageReaderSettingsTable.linearLightDownsampling],
                        upsamplingMode = UpsamplingMode.valueOf(it[ImageReaderSettingsTable.upsamplingMode]),
                        loadThumbnailPreviews = it[ImageReaderSettingsTable.loadThumbnailPreviews],
                        volumeKeysNavigation = it[ImageReaderSettingsTable.volumeKeysNavigation],
                        ortUpscalerMode = UpscaleMode.valueOf(it[ImageReaderSettingsTable.ortUpscalerMode]),
                        ortUpscalerUserModelPath = it[ImageReaderSettingsTable.ortUpscalerUserModelPath]
                            ?.let { PlatformFile(it) },
                        ortUpscalerDeviceId = it[ImageReaderSettingsTable.ortDeviceId],
                        ortUpscalerTileSize = it[ImageReaderSettingsTable.ortUpscalerTileSize],
                        panelsFullPageDisplayMode = it[ImageReaderSettingsTable.panelsFullPageDisplayMode]
                            .let { mode -> PanelsFullPageDisplayMode.valueOf(mode) },
                        pagedReaderTapToZoom = it[ImageReaderSettingsTable.pagedReaderTapToZoom],
                        panelReaderTapToZoom = it[ImageReaderSettingsTable.panelReaderTapToZoom],
                        pagedReaderAdaptiveBackground = it[ImageReaderSettingsTable.pagedReaderAdaptiveBackground],
                        panelReaderAdaptiveBackground = it[ImageReaderSettingsTable.panelReaderAdaptiveBackground],
                        tapNavigationMode = it[ImageReaderSettingsTable.tapNavigationMode]
                            .let { mode -> ReaderTapNavigationMode.valueOf(mode) },
                        panelDetectionUrl = it[ImageReaderSettingsTable.panelDetectionUrl],
                        rapidOcrModelsUrl = it[ImageReaderSettingsTable.rapidOcrModelsUrl],
                        imageCacheSizeLimitMb = it[ImageReaderSettingsTable.imageCacheSizeLimitMb],
                        pagedSplitDoublePages = it[ImageReaderSettingsTable.pagedSplitDoublePages],
                        pagedReaderAutoDirection = it[ImageReaderSettingsTable.pagedReaderAutoDirection],
                        pagedAutoSkipBlankPages = it[ImageReaderSettingsTable.pagedAutoSkipBlankPages],
                        pagedAutoDetectWebtoon = it[ImageReaderSettingsTable.pagedAutoDetectWebtoon],
                        continuousReaderStopAtEnd = it[ImageReaderSettingsTable.continuousReaderStopAtEnd],
                        keepProgressBarVisibleWhileReading = it[ImageReaderSettingsTable.keepProgressBarVisibleWhileReading],
                    )
                }
        }
    }

    suspend fun save(settings: ImageReaderSettings) {
        transaction {
            ImageReaderSettingsTable.upsert {
                it[bookId] = defaultBookId
                it[readerType] = settings.readerType.name
                it[stretchToFit] = settings.stretchToFit

                it[ncnnEnabled] = settings.ncnnUpscalerSettings.enabled
                it[ncnnEngine] = settings.ncnnUpscalerSettings.engine.name
                it[ncnnModel] = settings.ncnnUpscalerSettings.model
                it[ncnnGpuId] = settings.ncnnUpscalerSettings.gpuId
                it[ncnnTtaMode] = settings.ncnnUpscalerSettings.ttaMode
                it[ncnnNumThreads] = settings.ncnnUpscalerSettings.numThreads
                it[ncnnUpscaleOnLoad] = settings.ncnnUpscalerSettings.upscaleOnLoad
                it[ncnnUpscaleThreshold] = settings.ncnnUpscalerSettings.upscaleThreshold
                it[ncnnUpscalerUrl] = settings.ncnnUpscalerSettings.ncnnUpscalerUrl

                it[ocrEnabled] = settings.ocrSettings.enabled
                it[ocrLanguage] = settings.ocrSettings.selectedLanguage.name
                it[ocrEngine] = settings.ocrSettings.engine.name
                it[ocrRapidOcrModel] = settings.ocrSettings.rapidOcrModel.name
                it[ocrMergeBoxes] = settings.ocrSettings.mergeBoxes

                it[pagedScaleType] = settings.pagedScaleType.name
                it[pagedReadingDirection] = settings.pagedReadingDirection.name
                it[pagedPageLayout] = settings.pagedPageLayout.name
                it[continuousReadingDirection] = settings.continuousReadingDirection.name
                it[continuousPadding] = settings.continuousPadding
                it[continuousPageSpacing] = settings.continuousPageSpacing
                it[cropBorders] = settings.cropBorders
                it[flashOnPageChange] = settings.flashOnPageChange
                it[flashDuration] = settings.flashDuration
                it[flashEveryNPages] = settings.flashEveryNPages
                it[flashWith] = settings.flashWith.name
                it[downsamplingKernel] = settings.downsamplingKernel.name
                it[linearLightDownsampling] = settings.linearLightDownsampling
                it[loadThumbnailPreviews] = settings.loadThumbnailPreviews
                it[volumeKeysNavigation] = settings.volumeKeysNavigation
                it[upsamplingMode] = settings.upsamplingMode.name
                it[ortUpscalerMode] = settings.ortUpscalerMode.name
                it[ortUpscalerUserModelPath] = settings.ortUpscalerUserModelPath?.path
                it[ortDeviceId] = settings.ortUpscalerDeviceId
                it[ortUpscalerTileSize] = settings.ortUpscalerTileSize
                it[panelsFullPageDisplayMode] = settings.panelsFullPageDisplayMode.name
                it[pagedReaderTapToZoom] = settings.pagedReaderTapToZoom
                it[panelReaderTapToZoom] = settings.panelReaderTapToZoom
                it[pagedReaderAdaptiveBackground] = settings.pagedReaderAdaptiveBackground
                it[panelReaderAdaptiveBackground] = settings.panelReaderAdaptiveBackground
                it[tapNavigationMode] = settings.tapNavigationMode.name
                it[panelDetectionUrl] = settings.panelDetectionUrl
                it[rapidOcrModelsUrl] = settings.rapidOcrModelsUrl
                it[imageCacheSizeLimitMb] = settings.imageCacheSizeLimitMb
                it[pagedSplitDoublePages] = settings.pagedSplitDoublePages
                it[pagedReaderAutoDirection] = settings.pagedReaderAutoDirection
                it[pagedAutoSkipBlankPages] = settings.pagedAutoSkipBlankPages
                it[pagedAutoDetectWebtoon] = settings.pagedAutoDetectWebtoon
                it[continuousReaderStopAtEnd] = settings.continuousReaderStopAtEnd
                it[keepProgressBarVisibleWhileReading] = settings.keepProgressBarVisibleWhileReading
            }
        }
    }
}
