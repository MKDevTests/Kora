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
        // Redact line by line while streaming. Reading every file into one
        // String and then running three whole-string Regex.replace over it
        // allocated ~4 copies of the entire log set at once — a 20MB cap is
        // 40MB in UTF-16, and the export OOM'd on the StringBuilder growth.
        buildString {
            append("Kora logs export\n")
            append("Generated: ${Date()}\n")
            append("(server URLs, emails and tokens redacted)\n\n")
            var truncated = false
            files.forEach { f ->
                if (truncated) return@forEach
                append("=== ${f.name} ===\n")
                runCatching {
                    f.useLines { lines ->
                        for (line in lines) {
                            if (length >= MAX_EXPORT_CHARS) {
                                truncated = true
                                break
                            }
                            append(redactLine(line))
                            append('\n')
                        }
                    }
                }.onFailure { append("<unreadable>") }
                append("\n\n")
            }
            if (truncated) {
                append("=== export truncated at ${MAX_EXPORT_CHARS / 1_000_000}MB ===\n")
            }
        }
    }

    override fun setLogCap(cap: LogSizeCap) {
        LogSettings.setCapMb(context, cap.totalMb)
    }

    private fun logDir(): File = File(context.getExternalFilesDir(null) ?: context.filesDir, "komelia/logs")

    /**
     * Strip server URLs, emails and auth/token/cookie/password values from one
     * log line. Applied per line rather than to the whole export: the patterns
     * never span a newline, so the result is identical, but peak memory stays
     * proportional to a line instead of to the entire log set.
     */
    private fun redactLine(line: String): String {
        var t = line
        t = URL_PATTERN.replace(t, "[url]")
        t = EMAIL_PATTERN.replace(t, "[email]")
        t = SECRET_PATTERN.replace(t) { m -> "${m.groupValues[1]}=[redacted]" }
        return t
    }

    private companion object {
        /** Ceiling on the exported text. Above this the export is what OOM'd. */
        const val MAX_EXPORT_CHARS = 4_000_000

        // Compiled once rather than per line.
        val URL_PATTERN = Regex("""https?://\S+""")
        val EMAIL_PATTERN = Regex("""[\w.+-]+@[\w.-]+\.\w{2,}""")
        val SECRET_PATTERN =
            Regex("""(?i)\b(authorization|bearer|token|password|cookie|api[_-]?key)\b\s*[:=]\s*\S+""")
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
