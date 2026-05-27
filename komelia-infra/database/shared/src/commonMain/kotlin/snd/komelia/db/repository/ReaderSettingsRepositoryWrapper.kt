package snd.komelia.db.repository

import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.flow.Flow
import snd.komelia.db.ImageReaderSettings
import snd.komelia.db.SettingsStateWrapper
import snd.komelia.image.ReduceKernel
import snd.komelia.image.UpsamplingMode
import snd.komelia.image.UpscaleMode
import snd.komelia.settings.ImageReaderSettingsRepository
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

class ReaderSettingsRepositoryWrapper(
    val wrapper: SettingsStateWrapper<ImageReaderSettings>,
) : ImageReaderSettingsRepository {

    override fun getReaderType(): Flow<ReaderType> {
        return wrapper.mapState { it.readerType }
    }

    override suspend fun putReaderType(type: ReaderType) {
        wrapper.transform { settings -> settings.copy(readerType = type) }
    }

    override fun getNcnnUpscalerSettings(): Flow<NcnnUpscalerSettings> {
        return wrapper.mapState { it.ncnnUpscalerSettings }
    }

    override suspend fun putNcnnUpscalerSettings(settings: NcnnUpscalerSettings) {
        wrapper.transform { it.copy(ncnnUpscalerSettings = settings) }
    }

    override fun getOcrSettings(): Flow<OcrSettings> {
        return wrapper.mapState { it.ocrSettings }
    }

    override suspend fun putOcrSettings(settings: OcrSettings) {
        wrapper.transform { it.copy(ocrSettings = settings) }
    }

    override fun getStretchToFit(): Flow<Boolean> {
        return wrapper.mapState { it.stretchToFit }
    }

    override suspend fun putStretchToFit(stretch: Boolean) {
        wrapper.transform { it.copy(stretchToFit = stretch) }
    }

    override fun getCropBorders(): Flow<Boolean> {
        return wrapper.mapState { it.cropBorders }
    }

    override suspend fun putCropBorders(trim: Boolean) {
        wrapper.transform { it.copy(cropBorders = trim) }
    }

    override fun getPagedReaderScaleType(): Flow<LayoutScaleType> {
        return wrapper.mapState { it.pagedScaleType }
    }

    override suspend fun putPagedReaderScaleType(type: LayoutScaleType) {
        wrapper.transform { it.copy(pagedScaleType = type) }
    }

    override fun getPagedReaderReadingDirection(): Flow<PagedReadingDirection> {
        return wrapper.mapState { it.pagedReadingDirection }
    }

    override suspend fun putPagedReaderReadingDirection(direction: PagedReadingDirection) {
        wrapper.transform { it.copy(pagedReadingDirection = direction) }
    }

    override fun getPagedReaderDisplayLayout(): Flow<PageDisplayLayout> {
        return wrapper.mapState { it.pagedPageLayout }
    }

    override suspend fun putPagedReaderDisplayLayout(layout: PageDisplayLayout) {
        wrapper.transform { it.copy(pagedPageLayout = layout) }
    }

    override fun getContinuousReaderReadingDirection(): Flow<ContinuousReadingDirection> {
        return wrapper.mapState { it.continuousReadingDirection }
    }

    override suspend fun putContinuousReaderReadingDirection(direction: ContinuousReadingDirection) {
        wrapper.transform { it.copy(continuousReadingDirection = direction) }
    }

    override fun getContinuousReaderPadding(): Flow<Float> {
        return wrapper.mapState { it.continuousPadding }
    }

    override suspend fun putContinuousReaderPadding(padding: Float) {
        wrapper.transform { it.copy(continuousPadding = padding) }
    }

    override fun getContinuousReaderPageSpacing(): Flow<Int> {
        return wrapper.mapState { it.continuousPageSpacing }
    }

    override suspend fun putContinuousReaderPageSpacing(spacing: Int) {
        wrapper.transform { it.copy(continuousPageSpacing = spacing) }
    }

    override fun getFlashOnPageChange(): Flow<Boolean> {
        return wrapper.mapState { it.flashOnPageChange }
    }

    override suspend fun putFlashOnPageChange(flash: Boolean) {
        wrapper.transform { it.copy(flashOnPageChange = flash) }
    }

    override fun getFlashDuration(): Flow<Long> {
        return wrapper.mapState { it.flashDuration }
    }

    override suspend fun putFlashDuration(duration: Long) {
        wrapper.transform { it.copy(flashDuration = duration) }
    }

    override fun getFlashEveryNPages(): Flow<Int> {
        return wrapper.mapState { it.flashEveryNPages }
    }

    override suspend fun putFlashEveryNPages(pages: Int) {
        wrapper.transform { it.copy(flashEveryNPages = pages) }
    }

    override fun getFlashWith(): Flow<ReaderFlashColor> {
        return wrapper.mapState { it.flashWith }
    }

    override suspend fun putFlashWith(color: ReaderFlashColor) {
        wrapper.transform { it.copy(flashWith = color) }
    }

    override fun getDownsamplingKernel(): Flow<ReduceKernel> {
        return wrapper.mapState { it.downsamplingKernel }
    }

    override suspend fun putDownsamplingKernel(kernel: ReduceKernel) {
        wrapper.transform { it.copy(downsamplingKernel = kernel) }
    }

    override fun getLinearLightDownsampling(): Flow<Boolean> {
        return wrapper.mapState { it.linearLightDownsampling }
    }

    override suspend fun putLinearLightDownsampling(linear: Boolean) {
        wrapper.transform { it.copy(linearLightDownsampling = linear) }
    }

    override fun getUpsamplingMode(): Flow<UpsamplingMode> {
        return wrapper.mapState { it.upsamplingMode }
    }

    override suspend fun putUpsamplingMode(mode: UpsamplingMode) {
        wrapper.transform { it.copy(upsamplingMode = mode) }
    }

    override fun getLoadThumbnailPreviews(): Flow<Boolean> {
        return wrapper.mapState { it.loadThumbnailPreviews }
    }

    override suspend fun putLoadThumbnailPreviews(load: Boolean) {
        wrapper.transform { it.copy(loadThumbnailPreviews = load) }
    }

    override fun getVolumeKeysNavigation(): Flow<Boolean> {
        return wrapper.mapState { it.volumeKeysNavigation }
    }

    override suspend fun putVolumeKeysNavigation(enable: Boolean) {
        wrapper.transform { it.copy(volumeKeysNavigation = enable) }
    }

    override fun getUpscalerMode(): Flow<UpscaleMode> {
        return wrapper.mapState { it.ortUpscalerMode }
    }

    override suspend fun putUpscalerMode(mode: UpscaleMode) {
        wrapper.transform { it.copy(ortUpscalerMode = mode) }
    }

    override fun getOnnxRuntimeDeviceId(): Flow<Int> {
        return wrapper.mapState { it.ortUpscalerDeviceId }
    }

    override suspend fun putOnnxRuntimeDeviceId(deviceId: Int) {
        wrapper.transform { it.copy(ortUpscalerDeviceId = deviceId) }
    }

    override fun getOnnxRuntimeTileSize(): Flow<Int> {
        return wrapper.mapState { it.ortUpscalerTileSize }
    }

    override suspend fun putOnnxRuntimeTileSize(tileSize: Int) {
        wrapper.transform { it.copy(ortUpscalerTileSize = tileSize) }
    }

    override fun getUpscalerOnnxModel(): Flow<PlatformFile?> {
        return wrapper.mapState { it.ortUpscalerUserModelPath }
    }

    override suspend fun putUpscalerOnnxModel(name: PlatformFile?) {
        wrapper.transform { it.copy(ortUpscalerUserModelPath = name) }
    }

    override fun getPanelsFullPageDisplayMode(): Flow<PanelsFullPageDisplayMode> {
        return wrapper.mapState { it.panelsFullPageDisplayMode }
    }

    override suspend fun putPanelsFullPageDisplayMode(mode: PanelsFullPageDisplayMode) {
        wrapper.transform { it.copy(panelsFullPageDisplayMode = mode) }
    }

    override fun getPagedReaderTapToZoom(): Flow<Boolean> {
        return wrapper.mapState { it.pagedReaderTapToZoom }
    }

    override suspend fun putPagedReaderTapToZoom(enabled: Boolean) {
        wrapper.transform { it.copy(pagedReaderTapToZoom = enabled) }
    }

    override fun getPanelReaderTapToZoom(): Flow<Boolean> {
        return wrapper.mapState { it.panelReaderTapToZoom }
    }

    override suspend fun putPanelReaderTapToZoom(enabled: Boolean) {
        wrapper.transform { it.copy(panelReaderTapToZoom = enabled) }
    }

    override fun getPagedReaderAdaptiveBackground(): Flow<Boolean> {
        return wrapper.mapState { it.pagedReaderAdaptiveBackground }
    }

    override suspend fun putPagedReaderAdaptiveBackground(enabled: Boolean) {
        wrapper.transform { it.copy(pagedReaderAdaptiveBackground = enabled) }
    }

    override fun getPanelReaderAdaptiveBackground(): Flow<Boolean> {
        return wrapper.mapState { it.panelReaderAdaptiveBackground }
    }

    override suspend fun putPanelReaderAdaptiveBackground(enabled: Boolean) {
        wrapper.transform { it.copy(panelReaderAdaptiveBackground = enabled) }
    }

    override fun getReaderTapNavigationMode(): Flow<ReaderTapNavigationMode> {
        return wrapper.mapState { it.tapNavigationMode }
    }

    override suspend fun putReaderTapNavigationMode(mode: ReaderTapNavigationMode) {
        wrapper.transform { it.copy(tapNavigationMode = mode) }
    }

    override fun getPanelDetectionUrl(): Flow<String> {
        return wrapper.mapState { it.panelDetectionUrl }
    }

    override suspend fun putPanelDetectionUrl(url: String) {
        wrapper.transform { it.copy(panelDetectionUrl = url) }
    }

    override fun getRapidOcrModelsUrl(): Flow<String> {
        return wrapper.mapState { it.rapidOcrModelsUrl }
    }

    override suspend fun putRapidOcrModelsUrl(url: String) {
        wrapper.transform { it.copy(rapidOcrModelsUrl = url) }
    }

    override fun getImageCacheSizeLimitMb(): Flow<Long> {
        return wrapper.mapState { it.imageCacheSizeLimitMb }
    }

    override suspend fun putImageCacheSizeLimitMb(size: Long) {
        wrapper.transform { it.copy(imageCacheSizeLimitMb = size) }
    }

    override fun getPagedReaderSplitDoublePages(): Flow<Boolean> {
        return wrapper.mapState { it.pagedSplitDoublePages }
    }

    override suspend fun putPagedReaderSplitDoublePages(enabled: Boolean) {
        wrapper.transform { it.copy(pagedSplitDoublePages = enabled) }
    }

    override fun getPagedReaderAutoDirection(): Flow<Boolean> {
        return wrapper.mapState { it.pagedReaderAutoDirection }
    }

    override suspend fun putPagedReaderAutoDirection(enabled: Boolean) {
        wrapper.transform { it.copy(pagedReaderAutoDirection = enabled) }
    }

    override fun getPagedAutoSkipBlankPages(): Flow<Boolean> {
        return wrapper.mapState { it.pagedAutoSkipBlankPages }
    }

    override suspend fun putPagedAutoSkipBlankPages(enabled: Boolean) {
        wrapper.transform { it.copy(pagedAutoSkipBlankPages = enabled) }
    }

    override fun getPagedAutoDetectWebtoon(): Flow<Boolean> {
        return wrapper.mapState { it.pagedAutoDetectWebtoon }
    }

    override suspend fun putPagedAutoDetectWebtoon(enabled: Boolean) {
        wrapper.transform { it.copy(pagedAutoDetectWebtoon = enabled) }
    }

    override fun getContinuousReaderStopAtEnd(): Flow<Boolean> {
        return wrapper.mapState { it.continuousReaderStopAtEnd }
    }

    override suspend fun putContinuousReaderStopAtEnd(enabled: Boolean) {
        wrapper.transform { it.copy(continuousReaderStopAtEnd = enabled) }
    }

    override fun getKeepProgressBarVisibleWhileReading(): Flow<Boolean> {
        return wrapper.mapState { it.keepProgressBarVisibleWhileReading }
    }

    override suspend fun putKeepProgressBarVisibleWhileReading(enabled: Boolean) {
        wrapper.transform { it.copy(keepProgressBarVisibleWhileReading = enabled) }
    }
}