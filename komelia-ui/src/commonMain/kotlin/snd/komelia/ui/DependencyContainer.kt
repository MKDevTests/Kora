package snd.komelia.ui

import coil3.ImageLoader
import coil3.PlatformContext
import kotlinx.coroutines.flow.StateFlow
import snd.komelia.AppNotifications
import snd.komelia.AppRepositories
import snd.komelia.AppWindowState
import snd.komelia.KomgaAuthenticationState
import snd.komelia.ManagedKomgaEvents
import snd.komelia.image.BookImageLoader
import snd.komelia.image.KomeliaImageDecoder
import snd.komelia.image.KomeliaPanelDetector
import snd.komelia.image.KomeliaUpscaler
import snd.komelia.image.ReaderImageFactory
import snd.komelia.image.processing.BlankPageDetector
import snd.komelia.image.processing.ColorCorrectionStep
import snd.komelia.komga.api.KomgaApi
import snd.komelia.komga.api.LocalFileApiProvider
import snd.komelia.offline.OfflineDependencies
import snd.komelia.onnxruntime.OnnxRuntime
import snd.komelia.ui.strings.AppStrings
import snd.komelia.updates.AppUpdater
import snd.komelia.updates.OnnxModelDownloader
import snd.komelia.updates.OnnxRuntimeInstaller
import snd.komelia.updates.RapidOcrModelDownloader
import snd.komelia.updates.WhisperModelDownloader
import snd.komf.client.KomfClientFactory

import snd.komelia.sync.ReaderSyncService

data class DependencyContainer(
    val appStrings: StateFlow<AppStrings>,
    val appRepositories: AppRepositories,
    val readerSyncService: ReaderSyncService,
    val komgaApi: StateFlow<KomgaApi>,

    val isOffline: StateFlow<Boolean>,
    val komfClientFactory: KomfClientFactory,
    val appNotifications: AppNotifications,
    val komgaSharedState: KomgaAuthenticationState,
    val komgaEvents: ManagedKomgaEvents,
    val appUpdater: AppUpdater?,

    val coilContext: PlatformContext,
    val coilImageLoader: ImageLoader,

    val imageDecoder: KomeliaImageDecoder,
    val bookImageLoader: BookImageLoader,
    val readerImageFactory: ReaderImageFactory,
    val ocrService: snd.komelia.image.OcrService,

    val windowState: AppWindowState,
    val colorCorrectionStep: ColorCorrectionStep,
    val blankPageDetector: BlankPageDetector,

    val onnxRuntimeInstaller: OnnxRuntimeInstaller?,
    val onnxModelDownloader: OnnxModelDownloader?,
    val whisperModelDownloader: WhisperModelDownloader?,
    val rapidOcrModelDownloader: RapidOcrModelDownloader?,
    val onnxRuntime: OnnxRuntime?,
    val upscaler: KomeliaUpscaler?,
    val panelDetector: KomeliaPanelDetector?,

    val offlineDependencies: OfflineDependencies,
    val onBookChange: () -> Unit = {},
    val onEpubCacheClear: () -> Unit = {},
    val localFileApiProvider: LocalFileApiProvider? = null,
)

