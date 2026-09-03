package snd.komelia.offline

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import snd.komelia.offline.action.OfflineActions
import snd.komelia.offline.api.OfflineKomgaApi
import snd.komelia.offline.mediacontainer.BookContentExtractors
import snd.komelia.offline.sync.AutoDownloadPlanner
import snd.komelia.offline.sync.BookDownloadService
import snd.komelia.offline.sync.OfflineScannerService
import snd.komelia.offline.sync.model.DownloadEvent
import snd.komelia.offline.tasks.OfflineTaskEmitter
import snd.komga.client.sse.KomgaEvent
import snd.komelia.offline.sync.DownloadCleaner

data class OfflineDependencies(
    val actions: OfflineActions,
    val taskEmitter: OfflineTaskEmitter,
    val komgaEvents: SharedFlow<KomgaEvent>,
    val bookDownloadEvents: MutableSharedFlow<DownloadEvent>,
    val downloadService: BookDownloadService,
    val offlineScannerService: OfflineScannerService,
    val downloadCleaner: DownloadCleaner,
    val autoDownloadPlanner: AutoDownloadPlanner,

    val repositories: OfflineRepositories,
    val fileService: BookContentExtractors,
    val komgaApi: OfflineKomgaApi,
)