package snd.komelia.ui.settings.diagnostics

/**
 * Platform-provided data for Diagnostics v2 (cache sizes, offline storage,
 * background tasks). Read-only observability except for [clearImageCache],
 * which is safe (the cache regenerates on demand).
 *
 * Only Android ships a real implementation; other platforms inherit
 * [EmptyDiagnosticsDataSource] (the v2 sections stay hidden via [isSupported]).
 */
interface DiagnosticsDataSource {

    /** Whether this platform exposes the v2 diagnostics sections at all. */
    val isSupported: Boolean get() = false

    /** On-disk cache sizes, in bytes. Computed off the main thread. */
    suspend fun cacheUsage(): CacheUsage

    /** Offline-download storage usage. */
    suspend fun offlineUsage(): OfflineUsage

    /** Snapshot of known background (WorkManager) jobs. */
    suspend fun backgroundTasks(): List<BackgroundTask>

    /**
     * Clear the image caches (disk + in-memory) and the reader scratch cache.
     * Safe: covers freed in the background and re-fetched on demand.
     */
    suspend fun clearImageCache()

    /** Total size of the app log directory + the active size cap. */
    suspend fun logInfo(): LogInfo

    /** Tail of the app log files for in-app viewing (already trimmed). */
    suspend fun readRecentLogs(): String

    /** Full log text with secrets redacted, ready to write to a shared file. */
    suspend fun exportLogs(): String

    /** Persist the log size cap. Takes effect on the next app start. */
    fun setLogCap(cap: LogSizeCap)
}

/** App log directory usage + the active size cap. */
data class LogInfo(
    val totalBytes: Long,
    val cap: LogSizeCap,
)

/** User-selectable total budget for the on-device log files. */
enum class LogSizeCap(val totalMb: Int) {
    MB_20(20),
    MB_50(50),
    MB_100(100);

    companion object {
        fun fromMb(mb: Int): LogSizeCap = entries.firstOrNull { it.totalMb == mb } ?: MB_20
    }
}

/** Byte sizes of the app's on-disk caches. */
data class CacheUsage(
    val imageDiskBytes: Long,
    val readerCacheBytes: Long,
    val httpCacheBytes: Long,
    val databaseBytes: Long,
) {
    val totalBytes: Long get() = imageDiskBytes + readerCacheBytes + httpCacheBytes + databaseBytes
}

/** Offline-download storage usage. */
data class OfflineUsage(
    val totalBytes: Long,
    /** Human-readable location of the download directory. */
    val location: String,
)

/** One background job's current state for display. */
data class BackgroundTask(
    val label: String,
    val state: String,
    val detail: String? = null,
)

/** No-op fallback for platforms without diagnostics support. */
object EmptyDiagnosticsDataSource : DiagnosticsDataSource {
    override suspend fun cacheUsage() = CacheUsage(0, 0, 0, 0)
    override suspend fun offlineUsage() = OfflineUsage(0, "—")
    override suspend fun backgroundTasks() = emptyList<BackgroundTask>()
    override suspend fun clearImageCache() {}
    override suspend fun logInfo() = LogInfo(0, LogSizeCap.MB_20)
    override suspend fun readRecentLogs() = ""
    override suspend fun exportLogs() = ""
    override fun setLogCap(cap: LogSizeCap) {}
}
