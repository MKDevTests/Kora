package snd.komelia

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import snd.komelia.autobackup.AutobackupWorker
import snd.komelia.offline.sync.DownloadWorker
import snd.komelia.ui.DependencyContainer

private val logger = KotlinLogging.logger { }

class MyWorkerFactory(
    private val dependencies: Flow<DependencyContainer>
) : WorkerFactory() {

    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? {
        return runBlocking {
            val container = dependencies.first()
            when (workerClassName) {
                DownloadWorker::class.java.name -> {
                    val offline = container.offlineDependencies
                    DownloadWorker(
                        context = appContext,
                        workerParams = workerParameters,
                        downloadService = offline.downloadService,
                        logsJournalRepository = offline.repositories.logJournalRepository,
                        sharedEvents = offline.bookDownloadEvents,
                    )
                }
                AutobackupWorker::class.java.name -> AutobackupWorker(
                    context = appContext,
                    workerParams = workerParameters,
                    backupService = container.backupService,
                    settingsRepository = container.appRepositories.settingsRepository,
                )
                else -> {
                    logger.warn { "Unknown worker class: $workerClassName" }
                    null
                }
            }
        }
    }
}
