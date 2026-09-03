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

    val autoDownloadEnabled = bool("auto_download_enabled")
    val autoDownloadMaxSeries = integer("auto_download_max_series")
    val autoDownloadBooksAhead = integer("auto_download_books_ahead")
    val autoDownloadLibraryIds = text("auto_download_library_ids")
    val autoDownloadPinnedSeriesIds = text("auto_download_pinned_series_ids")
    val autoDownloadExcludedSeriesIds = text("auto_download_excluded_series_ids")

    override val primaryKey = PrimaryKey(version)
}