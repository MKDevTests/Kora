package snd.komelia.ui

import coil3.ImageLoader
import coil3.PlatformContext
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.flow.StateFlow
import snd.komelia.AppNotifications
import snd.komelia.AppRepositories
import snd.komelia.AppWindowState
import snd.komelia.KomgaAuthenticationState
import snd.komelia.ManagedKomgaEvents
import snd.komelia.backup.BackupService
import snd.komelia.image.BookImageLoader
import snd.komelia.image.KomeliaImageDecoder
import snd.komelia.image.KomeliaPanelDetector
import snd.komelia.image.KomeliaUpscaler
import snd.komelia.image.ReaderImageFactory
import snd.komelia.image.processing.BlankPageDetector
import snd.komelia.image.processing.ColorCorrectionStep
import snd.komelia.komga.api.KomgaApi
import snd.komelia.komga.api.LocalFileApiProvider
import kotlinx.coroutines.flow.SharedFlow
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.nextbook.NextBookService
import snd.komelia.offline.OfflineDependencies
import snd.komelia.onnxruntime.OnnxRuntime
import snd.komelia.stats.BookCompletionEvents
import snd.komelia.ui.strings.AppStrings
import snd.komelia.updates.AppUpdater
import snd.komelia.updates.OnnxModelDownloader
import snd.komelia.updates.OnnxRuntimeInstaller
import snd.komelia.updates.RapidOcrModelDownloader
import snd.komelia.updates.ReleaseNotesService
import snd.komelia.updates.WhisperModelDownloader
import snd.komf.client.KomfClientFactory

import snd.komelia.sync.ReaderSyncService

data class DependencyContainer(
    val appStrings: StateFlow<AppStrings>,
    val appRepositories: AppRepositories,
    val backupService: BackupService,
    val readerSyncService: ReaderSyncService,
    val komgaApi: StateFlow<KomgaApi>,

    val isOffline: StateFlow<Boolean>,
    val komfClientFactory: KomfClientFactory,
    val appNotifications: AppNotifications,
    val komgaSharedState: KomgaAuthenticationState,
    val komgaEvents: ManagedKomgaEvents,
    val appUpdater: AppUpdater?,
    val releaseNotesService: ReleaseNotesService,
    val bookCompletionEvents: BookCompletionEvents,

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
    val nextBookService: NextBookService,
    /**
     * Stream of books to open in the reader, emitted by the home-screen
     * widget's tap handler after fetching the [KomeliaBook] from the
     * server. Null on platforms without the widget.
     */
    val widgetBookToOpenFlow: SharedFlow<KomeliaBook>? = null,
    val onBookChange: () -> Unit = {},
    val onEpubCacheClear: () -> Unit = {},
    val localFileApiProvider: LocalFileApiProvider? = null,
    /**
     * Fires a one-shot autobackup run, used by the settings "Backup now"
     * button and by the first-run feedback right after the user toggles
     * the feature on. Defaults to a no-op so non-Android platforms (where
     * the autobackup section is hidden anyway) compile cleanly.
     */
    val runAutobackupNow: () -> Unit = {},
    /**
     * Pulls a persistable folder URI out of a [PlatformFile] returned by
     * the storage permission dialog. Android wraps a SAF tree URI; other
     * platforms don't expose persistable URIs the same way, so the
     * default returns null.
     */
    val extractPersistableFolderUri: (PlatformFile) -> String? = { null },
)

