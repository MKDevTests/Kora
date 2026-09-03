package snd.komelia.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import snd.komelia.offline.settings.OfflineSettingsRepository
import snd.komelia.offline.sync.AutoDownloadPlanner
import snd.komga.client.series.KomgaSeriesId

/**
 * App-wide handle for the per-series bounds on automatic downloads, exposed
 * via [LocalAutoDownload] so a series menu can pin or exclude without
 * threading callbacks.
 *
 * Pinned and excluded are mutually exclusive by construction rather than by
 * convention: a series that is both would make the planner's answer depend on
 * the order it happens to read the two lists, and the user asking for both
 * clearly means the last thing they pressed.
 *
 * Pinning nudges the planner immediately — pinning a series is a request for
 * it to be ready, and waiting up to ten minutes for the throttle would read as
 * the button having done nothing.
 */
class AutoDownloadController(
    val pinnedIds: StateFlow<Set<String>>,
    val excludedIds: StateFlow<Set<String>>,
    private val settingsRepository: OfflineSettingsRepository,
    private val planner: AutoDownloadPlanner,
    private val scope: CoroutineScope,
) {
    fun isPinned(id: KomgaSeriesId): Boolean = id.value in pinnedIds.value
    fun isExcluded(id: KomgaSeriesId): Boolean = id.value in excludedIds.value

    fun togglePinned(id: KomgaSeriesId) {
        scope.launch {
            if (isPinned(id)) {
                settingsRepository.putAutoDownloadPinnedSeriesIds(pinnedIds.value - id.value)
            } else {
                settingsRepository.putAutoDownloadPinnedSeriesIds(pinnedIds.value + id.value)
                settingsRepository.putAutoDownloadExcludedSeriesIds(excludedIds.value - id.value)
                planner.requestRun(force = true)
            }
        }
    }

    fun toggleExcluded(id: KomgaSeriesId) {
        scope.launch {
            if (isExcluded(id)) {
                settingsRepository.putAutoDownloadExcludedSeriesIds(excludedIds.value - id.value)
            } else {
                settingsRepository.putAutoDownloadExcludedSeriesIds(excludedIds.value + id.value)
                settingsRepository.putAutoDownloadPinnedSeriesIds(pinnedIds.value - id.value)
            }
        }
    }
}
