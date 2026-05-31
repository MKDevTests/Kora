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
}
