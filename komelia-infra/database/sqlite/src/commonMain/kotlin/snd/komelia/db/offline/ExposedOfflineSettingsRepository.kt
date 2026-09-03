package snd.komelia.db.offline

import io.github.vinceglb.filekit.PlatformFile
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert
import snd.komelia.db.ExposedRepository
import snd.komelia.db.OfflineSettings
import snd.komelia.db.offline.tables.OfflineSettingsTable
import snd.komelia.offline.server.model.OfflineMediaServerId
import snd.komelia.offline.user.model.OfflineUser
import snd.komga.client.user.KomgaUserId
import kotlin.time.Instant

class ExposedOfflineSettingsRepository(database: Database) : ExposedRepository(database) {

    suspend fun get(): OfflineSettings? {
        return transaction {
            OfflineSettingsTable.selectAll()
                .firstOrNull()
                ?.toOfflineSettings()
        }
    }

    suspend fun save(settings: OfflineSettings) {
        transaction {
            OfflineSettingsTable.upsert {
                it[version] = 1
                it[OfflineSettingsTable.isOfflineModeEnabled] = settings.isOfflineModeEnabled
                it[OfflineSettingsTable.userId] = settings.userId.value
                it[OfflineSettingsTable.serverId] = settings.serverId?.value
                it[OfflineSettingsTable.downloadDirectory] = settings.downloadDirectory.toString()
                it[OfflineSettingsTable.readProgressSyncDate] = settings.readProgressSyncDate?.epochSeconds
                it[OfflineSettingsTable.dataSyncDate] = settings.dataSyncDate?.epochSeconds
                it[OfflineSettingsTable.downloadWifiOnly] = settings.downloadWifiOnly
                it[OfflineSettingsTable.downloadWhileChargingOnly] = settings.downloadWhileChargingOnly
                it[OfflineSettingsTable.downloadStorageLimitMb] = settings.downloadStorageLimitMb
                it[OfflineSettingsTable.cleanupReadAfterDays] = settings.cleanupReadAfterDays
                it[OfflineSettingsTable.cleanupIncludeManual] = settings.cleanupIncludeManual
            }
        }
    }

    private fun ResultRow.toOfflineSettings(): OfflineSettings {
        return OfflineSettings(
            isOfflineModeEnabled = this[OfflineSettingsTable.isOfflineModeEnabled],
            userId = this[OfflineSettingsTable.userId]?.let { KomgaUserId(it) } ?: OfflineUser.ROOT,
            serverId = this[OfflineSettingsTable.serverId]?.let { OfflineMediaServerId(it) },
            downloadDirectory = PlatformFile(this[OfflineSettingsTable.downloadDirectory]),
            readProgressSyncDate = this[OfflineSettingsTable.readProgressSyncDate]?.let { Instant.fromEpochSeconds(it) },
            dataSyncDate = this[OfflineSettingsTable.dataSyncDate]?.let { Instant.fromEpochSeconds(it) },
            downloadWifiOnly = this[OfflineSettingsTable.downloadWifiOnly],
            downloadWhileChargingOnly = this[OfflineSettingsTable.downloadWhileChargingOnly],
            downloadStorageLimitMb = this[OfflineSettingsTable.downloadStorageLimitMb],
            cleanupReadAfterDays = this[OfflineSettingsTable.cleanupReadAfterDays],
            cleanupIncludeManual = this[OfflineSettingsTable.cleanupIncludeManual],
        )
    }
}