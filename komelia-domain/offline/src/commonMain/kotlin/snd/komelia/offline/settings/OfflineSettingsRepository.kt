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
}