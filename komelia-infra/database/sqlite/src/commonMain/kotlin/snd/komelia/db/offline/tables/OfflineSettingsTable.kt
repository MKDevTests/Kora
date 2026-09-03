package snd.komelia.db.offline.tables

import org.jetbrains.exposed.v1.core.Table

object OfflineSettingsTable : Table("SETTINGS") {
    val version = integer("version")
    val isOfflineModeEnabled = bool("is_offline_mode_enabled")
    val userId = text("user_id").nullable()
    val serverId = text("server_id").nullable()
    val downloadDirectory = text("download_directory")
    val readProgressSyncDate = long("read_progress_sync_date").nullable()
    val dataSyncDate = long("data_sync_date").nullable()

    val downloadWifiOnly = bool("download_wifi_only")
    val downloadWhileChargingOnly = bool("download_while_charging_only")
    val downloadStorageLimitMb = integer("download_storage_limit_mb")
    val cleanupReadAfterDays = integer("cleanup_read_after_days")
    val cleanupIncludeManual = bool("cleanup_include_manual")

    override val primaryKey = PrimaryKey(version)
}