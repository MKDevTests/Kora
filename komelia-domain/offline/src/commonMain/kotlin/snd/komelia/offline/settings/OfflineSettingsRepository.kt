package snd.komelia.offline.settings

import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.flow.Flow
import snd.komga.client.user.KomgaUserId
import kotlin.time.Instant

interface OfflineSettingsRepository {
    fun getOfflineMode(): Flow<Boolean>
    suspend fun putOfflineMode(offline: Boolean)
    fun getUserId(): Flow<KomgaUserId>
    suspend fun putUserId(userId: KomgaUserId)

    fun getReadProgressSyncDate(): Flow<Instant?>
    suspend fun putReadProgressSyncDate(timestamp: Instant)

    fun getDataSyncDate(): Flow<Instant?>
    suspend fun putDataSyncDate(timestamp: Instant)


    fun getDownloadDirectory(): Flow<PlatformFile>
    suspend fun putDownloadDirectory(path: PlatformFile)

    /**
     * The download policy: what the app is allowed to fetch, keep and delete
     * on its own.
     *
     * Every one of these is a brake. Without them an automatic download on
     * this catalogue reaches fifteen gigabytes, which is why they exist before
     * anything downloads by itself.
     */
    fun getDownloadWifiOnly(): Flow<Boolean>
    suspend fun putDownloadWifiOnly(enabled: Boolean)

    fun getDownloadWhileChargingOnly(): Flow<Boolean>
    suspend fun putDownloadWhileChargingOnly(enabled: Boolean)

    fun getDownloadStorageLimitMb(): Flow<Int>
    suspend fun putDownloadStorageLimitMb(limitMb: Int)

    /** Days after a book is read before the cleaner may drop it. 0 = never. */
    fun getCleanupReadAfterDays(): Flow<Int>
    suspend fun putCleanupReadAfterDays(days: Int)

    /** Whether the cleaner may also drop books downloaded by hand. */
    fun getCleanupIncludeManual(): Flow<Boolean>
    suspend fun putCleanupIncludeManual(enabled: Boolean)

    /**
     * The planner: what the app may fetch ahead of the reader, and how much.
     *
     * Disabled by default. Everything here narrows the scope — a series count,
     * a depth, a library list, a pin list and an exclusion list — because the
     * unbounded version of this feature is fifteen gigabytes.
     */
    fun getAutoDownloadEnabled(): Flow<Boolean>
    suspend fun putAutoDownloadEnabled(enabled: Boolean)

    fun getAutoDownloadMaxSeries(): Flow<Int>
    suspend fun putAutoDownloadMaxSeries(count: Int)

    fun getAutoDownloadBooksAhead(): Flow<Int>
    suspend fun putAutoDownloadBooksAhead(count: Int)

    /** Libraries the planner may draw from. Empty means all of them. */
    fun getAutoDownloadLibraryIds(): Flow<Set<String>>
    suspend fun putAutoDownloadLibraryIds(ids: Set<String>)

    fun getAutoDownloadPinnedSeriesIds(): Flow<Set<String>>
    suspend fun putAutoDownloadPinnedSeriesIds(ids: Set<String>)

    fun getAutoDownloadExcludedSeriesIds(): Flow<Set<String>>
    suspend fun putAutoDownloadExcludedSeriesIds(ids: Set<String>)
}