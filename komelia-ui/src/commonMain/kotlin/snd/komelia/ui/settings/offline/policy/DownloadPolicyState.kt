package snd.komelia.ui.settings.offline.policy

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import snd.komelia.AppNotifications
import snd.komelia.offline.settings.OfflineSettingsRepository
import snd.komelia.offline.sync.DownloadCleaner
import snd.komelia.KomgaAuthenticationState
import snd.komelia.offline.sync.AutoDownloadPlanner

/**
 * The download policy screen's state.
 *
 * Every setting is written straight through to the repository rather than held
 * as a draft: these are switches and choices, not a form, and there is nothing
 * to validate across them. [usedBytes] is the one value that is not a setting —
 * it is measured, so it is refreshed on entry and after a manual cleanup.
 */
class DownloadPolicyState(
    private val settingsRepository: OfflineSettingsRepository,
    private val downloadCleaner: DownloadCleaner,
    private val autoDownloadPlanner: AutoDownloadPlanner,
    private val authState: KomgaAuthenticationState,
    private val appNotifications: AppNotifications,
    private val coroutineScope: CoroutineScope,
) {
    val wifiOnly = settingsRepository.getDownloadWifiOnly()
        .stateIn(coroutineScope, SharingStarted.Eagerly, false)
    val whileChargingOnly = settingsRepository.getDownloadWhileChargingOnly()
        .stateIn(coroutineScope, SharingStarted.Eagerly, false)
    val storageLimitMb = settingsRepository.getDownloadStorageLimitMb()
        .stateIn(coroutineScope, SharingStarted.Eagerly, 4096)
    val cleanupReadAfterDays = settingsRepository.getCleanupReadAfterDays()
        .stateIn(coroutineScope, SharingStarted.Eagerly, 0)
    val cleanupIncludeManual = settingsRepository.getCleanupIncludeManual()
        .stateIn(coroutineScope, SharingStarted.Eagerly, false)

    val autoDownloadEnabled = settingsRepository.getAutoDownloadEnabled()
        .stateIn(coroutineScope, SharingStarted.Eagerly, false)
    val autoDownloadMaxSeries = settingsRepository.getAutoDownloadMaxSeries()
        .stateIn(coroutineScope, SharingStarted.Eagerly, 5)
    val autoDownloadBooksAhead = settingsRepository.getAutoDownloadBooksAhead()
        .stateIn(coroutineScope, SharingStarted.Eagerly, 4)
    val autoDownloadLibraryIds = settingsRepository.getAutoDownloadLibraryIds()
        .stateIn(coroutineScope, SharingStarted.Eagerly, emptySet())

    val libraries = authState.libraries

    val usedBytes = MutableStateFlow(0L)
    val isCleaning = MutableStateFlow(false)

    suspend fun initialize() {
        refreshUsedBytes()
    }

    fun onWifiOnlyChange(enabled: Boolean) {
        coroutineScope.launch { settingsRepository.putDownloadWifiOnly(enabled) }
    }

    fun onWhileChargingOnlyChange(enabled: Boolean) {
        coroutineScope.launch { settingsRepository.putDownloadWhileChargingOnly(enabled) }
    }

    fun onStorageLimitChange(limitMb: Int) {
        coroutineScope.launch { settingsRepository.putDownloadStorageLimitMb(limitMb) }
    }

    fun onCleanupReadAfterDaysChange(days: Int) {
        coroutineScope.launch { settingsRepository.putCleanupReadAfterDays(days) }
    }

    fun onCleanupIncludeManualChange(enabled: Boolean) {
        coroutineScope.launch { settingsRepository.putCleanupIncludeManual(enabled) }
    }

    fun onCleanupNow() {
        coroutineScope.launch {
            isCleaning.value = true
            try {
                appNotifications.runCatchingToNotifications {
                    val result = downloadCleaner.clean()
                    // Deletion is queued, so the measured size still counts the
                    // condemned books. Show the projection the cleaner already
                    // computed instead of a number that is about to be wrong.
                    usedBytes.value = result.remainingBytes
                }
            } finally {
                isCleaning.value = false
            }
        }
    }

    fun onAutoDownloadEnabledChange(enabled: Boolean) {
        coroutineScope.launch {
            settingsRepository.putAutoDownloadEnabled(enabled)
            // Turning it on is the moment the user expects something to
            // happen; waiting for the next book to close would look broken.
            if (enabled) autoDownloadPlanner.requestRun(force = true)
        }
    }

    fun onAutoDownloadMaxSeriesChange(count: Int) {
        coroutineScope.launch { settingsRepository.putAutoDownloadMaxSeries(count) }
    }

    fun onAutoDownloadBooksAheadChange(count: Int) {
        coroutineScope.launch { settingsRepository.putAutoDownloadBooksAhead(count) }
    }

    fun onAutoDownloadLibraryToggle(libraryId: String) {
        coroutineScope.launch {
            val current = autoDownloadLibraryIds.value
            val next = if (libraryId in current) current - libraryId else current + libraryId
            settingsRepository.putAutoDownloadLibraryIds(next)
        }
    }

    private suspend fun refreshUsedBytes() {
        usedBytes.value = downloadCleaner.downloadedBytes()
    }
}
