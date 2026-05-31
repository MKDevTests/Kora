package snd.komelia

import android.content.Context
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import coil3.ImageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import snd.komelia.autobackup.autobackupPeriodicWorkName
import snd.komelia.ui.settings.diagnostics.BackgroundTask
import snd.komelia.ui.settings.diagnostics.CacheUsage
import snd.komelia.ui.settings.diagnostics.DiagnosticsDataSource
import snd.komelia.ui.settings.diagnostics.OfflineUsage
import java.io.File

/**
 * Android implementation of [DiagnosticsDataSource]. All sizing / WorkManager
 * reads run on [Dispatchers.IO]. Cache directories mirror the layout created in
 * AndroidAppModule (under [Context.getCacheDir]); the offline downloads default
 * to filesDir/offline, the default in OfflineSettings.
 */
class AndroidDiagnosticsDataSource(
    private val context: Context,
    private val coilImageLoader: ImageLoader,
) : DiagnosticsDataSource {

    override val isSupported = true

    override suspend fun cacheUsage(): CacheUsage = withContext(Dispatchers.IO) {
        CacheUsage(
            imageDiskBytes = dirSize(File(context.cacheDir, "coil3_disk_cache")),
            readerCacheBytes = dirSize(File(context.cacheDir, "komelia_reader_cache")),
            httpCacheBytes = dirSize(File(context.cacheDir, "okhttp")),
            // app + offline SQLite files (incl. -wal/-shm), across server variants.
            databaseBytes = context.filesDir.listFiles()
                ?.filter { it.isFile && it.name.contains(".sqlite") }
                ?.sumOf { it.length() } ?: 0L,
        )
    }

    override suspend fun offlineUsage(): OfflineUsage = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "offline")
        OfflineUsage(totalBytes = dirSize(dir), location = dir.absolutePath)
    }

    override suspend fun backgroundTasks(): List<BackgroundTask> = withContext(Dispatchers.IO) {
        val wm = WorkManager.getInstance(context)
        val tasks = mutableListOf<BackgroundTask>()

        val autobackupState = runCatching {
            wm.getWorkInfosForUniqueWorkFlow(autobackupPeriodicWorkName).first().firstOrNull()?.state
        }.getOrNull()
        tasks += BackgroundTask(
            label = "Automatic backup",
            state = autobackupState?.toDisplay() ?: "Not scheduled",
        )

        val runningCount = runCatching {
            wm.getWorkInfosFlow(WorkQuery.fromStates(listOf(WorkInfo.State.RUNNING))).first().size
        }.getOrDefault(0)
        tasks += BackgroundTask(
            label = "Running jobs",
            state = runningCount.toString(),
            detail = "incl. active offline downloads",
        )
        tasks
    }

    override suspend fun clearImageCache() {
        withContext(Dispatchers.IO) {
            runCatching { coilImageLoader.diskCache?.clear() }
            runCatching { coilImageLoader.memoryCache?.clear() }
            runCatching { File(context.cacheDir, "komelia_reader_cache").deleteRecursively() }
        }
    }

    private fun dirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        return dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
    }

    private fun WorkInfo.State.toDisplay(): String = when (this) {
        WorkInfo.State.ENQUEUED -> "Scheduled"
        WorkInfo.State.RUNNING -> "Running"
        WorkInfo.State.SUCCEEDED -> "Last run OK"
        WorkInfo.State.FAILED -> "Last run failed"
        WorkInfo.State.BLOCKED -> "Blocked"
        WorkInfo.State.CANCELLED -> "Cancelled"
    }
}
