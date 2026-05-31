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
import snd.komelia.ui.settings.diagnostics.LogInfo
import snd.komelia.ui.settings.diagnostics.LogSizeCap
import snd.komelia.ui.settings.diagnostics.OfflineUsage
import java.io.File
import java.util.Date

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

    override suspend fun logInfo(): LogInfo = withContext(Dispatchers.IO) {
        LogInfo(
            totalBytes = dirSize(logDir()),
            cap = LogSizeCap.fromMb(LogSettings.getCapMb(context)),
        )
    }

    override suspend fun readRecentLogs(): String = withContext(Dispatchers.IO) {
        val dir = logDir()
        if (!dir.exists()) return@withContext "No log directory at ${dir.absolutePath}"
        val files = listOf("komelia.log", "last_session_logcat.txt", "java_crash_report.txt")
        buildString {
            files.forEach { name ->
                val f = File(dir, name)
                if (f.exists()) {
                    append("=== $name ===\n")
                    append(runCatching { f.readLines().takeLast(300).joinToString("\n") }.getOrDefault("<unreadable>"))
                    append("\n\n")
                }
            }
        }.ifBlank { "No log files in ${dir.absolutePath}" }
    }

    override suspend fun exportLogs(): String = withContext(Dispatchers.IO) {
        val dir = logDir()
        if (!dir.exists()) return@withContext "No logs."
        val files = dir.listFiles()
            ?.filter { it.isFile && (it.name.endsWith(".log") || it.name.endsWith(".txt")) }
            ?.sortedBy { it.name }
            ?: emptyList()
        val raw = buildString {
            append("Kora logs export\n")
            append("Generated: ${Date()}\n")
            append("(server URLs, emails and tokens redacted)\n\n")
            files.forEach { f ->
                append("=== ${f.name} ===\n")
                append(runCatching { f.readText() }.getOrDefault("<unreadable>"))
                append("\n\n")
            }
        }
        redact(raw)
    }

    override fun setLogCap(cap: LogSizeCap) {
        LogSettings.setCapMb(context, cap.totalMb)
    }

    private fun logDir(): File = File(context.getExternalFilesDir(null) ?: context.filesDir, "komelia/logs")

    /** Strip server URLs, emails and auth/token/cookie/password values from exported logs. */
    private fun redact(text: String): String {
        var t = text
        t = Regex("""https?://\S+""").replace(t, "[url]")
        t = Regex("""[\w.+-]+@[\w.-]+\.\w{2,}""").replace(t, "[email]")
        t = Regex("""(?i)\b(authorization|bearer|token|password|cookie|api[_-]?key)\b\s*[:=]\s*\S+""")
            .replace(t) { m -> "${m.groupValues[1]}=[redacted]" }
        return t
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
