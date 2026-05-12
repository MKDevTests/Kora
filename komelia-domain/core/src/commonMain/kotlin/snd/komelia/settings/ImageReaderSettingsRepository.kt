package snd.komelia.settings

import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.flow.Flow
import snd.komelia.image.ReduceKernel
import snd.komelia.image.UpsamplingMode
import snd.komelia.image.UpscaleMode
import snd.komelia.settings.model.ContinuousReadingDirection
import snd.komelia.settings.model.LayoutScaleType
import snd.komelia.settings.model.NcnnUpscalerSettings
import snd.komelia.settings.model.OcrSettings
import snd.komelia.settings.model.PageDisplayLayout
import snd.komelia.settings.model.PagedReadingDirection
import snd.komelia.settings.model.PanelsFullPageDisplayMode
import snd.komelia.settings.model.ReaderFlashColor
import snd.komelia.settings.model.ReaderTapNavigationMode
import snd.komelia.settings.model.ReaderType

interface ImageReaderSettingsRepository {
    fun getReaderType(): Flow<ReaderType>
    suspend fun putReaderType(type: ReaderType)

    fun getNcnnUpscalerSettings(): Flow<NcnnUpscalerSettings>
    suspend fun putNcnnUpscalerSettings(settings: NcnnUpscalerSettings)

    fun getOcrSettings(): Flow<OcrSettings>
    suspend fun putOcrSettings(settings: OcrSettings)

    fun getStretchToFit(): Flow<Boolean>
    suspend fun putStretchToFit(stretch: Boolean)

    fun getCropBorders(): Flow<Boolean>
    suspend fun putCropBorders(trim: Boolean)

    fun getPagedReaderScaleType(): Flow<LayoutScaleType>
    suspend fun putPagedReaderScaleType(type: LayoutScaleType)

    fun getPagedReaderReadingDirection(): Flow<PagedReadingDirection>
    suspend fun putPagedReaderReadingDirection(direction: PagedReadingDirection)

    fun getPagedReaderDisplayLayout(): Flow<PageDisplayLayout>
    suspend fun putPagedReaderDisplayLayout(layout: PageDisplayLayout)

    fun getContinuousReaderReadingDirection(): Flow<ContinuousReadingDirection>
    suspend fun putContinuousReaderReadingDirection(direction: ContinuousReadingDirection)

    fun getContinuousReaderPadding(): Flow<Float>
    suspend fun putContinuousReaderPadding(padding: Float)

    fun getContinuousReaderPageSpacing(): Flow<Int>
    suspend fun putContinuousReaderPageSpacing(spacing: Int)

    fun getFlashOnPageChange(): Flow<Boolean>
    suspend fun putFlashOnPageChange(flash: Boolean)

    fun getFlashDuration(): Flow<Long>
    suspend fun putFlashDuration(duration: Long)

    fun getFlashEveryNPages(): Flow<Int>
    suspend fun putFlashEveryNPages(pages: Int)

    fun getFlashWith(): Flow<ReaderFlashColor>
    suspend fun putFlashWith(color: ReaderFlashColor)

    fun getDownsamplingKernel(): Flow<ReduceKernel>
    suspend fun putDownsamplingKernel(kernel: ReduceKernel)

    fun getLinearLightDownsampling(): Flow<Boolean>
    suspend fun putLinearLightDownsampling(linear: Boolean)

    fun getUpsamplingMode(): Flow<UpsamplingMode>
    suspend fun putUpsamplingMode(mode: UpsamplingMode)

    fun getLoadThumbnailPreviews(): Flow<Boolean>
    suspend fun putLoadThumbnailPreviews(load: Boolean)

    fun getVolumeKeysNavigation(): Flow<Boolean>
    suspend fun putVolumeKeysNavigation(enable: Boolean)

    fun getUpscalerMode(): Flow<UpscaleMode>
    suspend fun putUpscalerMode(mode: UpscaleMode)

    fun getOnnxRuntimeDeviceId(): Flow<Int>
    suspend fun putOnnxRuntimeDeviceId(deviceId: Int)

    fun getOnnxRuntimeTileSize(): Flow<Int>
    suspend fun putOnnxRuntimeTileSize(tileSize: Int)

    fun getUpscalerOnnxModel(): Flow<PlatformFile?>
    suspend fun putUpscalerOnnxModel(name: PlatformFile?)

    fun getPanelsFullPageDisplayMode(): Flow<PanelsFullPageDisplayMode>
    suspend fun putPanelsFullPageDisplayMode(mode: PanelsFullPageDisplayMode)

    fun getPagedReaderTapToZoom(): Flow<Boolean>
    suspend fun putPagedReaderTapToZoom(enabled: Boolean)

    fun getPanelReaderTapToZoom(): Flow<Boolean>
    suspend fun putPanelReaderTapToZoom(enabled: Boolean)

    fun getPagedReaderAdaptiveBackground(): Flow<Boolean>
    suspend fun putPagedReaderAdaptiveBackground(enabled: Boolean)

    fun getPanelReaderAdaptiveBackground(): Flow<Boolean>
    suspend fun putPanelReaderAdaptiveBackground(enabled: Boolean)

    fun getReaderTapNavigationMode(): Flow<ReaderTapNavigationMode>
    suspend fun putReaderTapNavigationMode(mode: ReaderTapNavigationMode)

    fun getPanelDetectionUrl(): Flow<String>
    suspend fun putPanelDetectionUrl(url: String)

    fun getRapidOcrModelsUrl(): Flow<String>
    suspend fun putRapidOcrModelsUrl(url: String)

    fun getImageCacheSizeLimitMb(): Flow<Long>
    suspend fun putImageCacheSizeLimitMb(size: Long)

    fun getPagedReaderSplitDoublePages(): Flow<Boolean>
    suspend fun putPagedReaderSplitDoublePages(enabled: Boolean)

    fun getPagedReaderAutoDirection(): Flow<Boolean>
    suspend fun putPagedReaderAutoDirection(enabled: Boolean)

    fun getPagedAutoSkipBlankPages(): Flow<Boolean>
    suspend fun putPagedAutoSkipBlankPages(enabled: Boolean)

    fun getPagedAutoDetectWebtoon(): Flow<Boolean>
    suspend fun putPagedAutoDetectWebtoon(enabled: Boolean)
}