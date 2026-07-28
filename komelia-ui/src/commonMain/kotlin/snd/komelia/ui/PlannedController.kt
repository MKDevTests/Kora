package snd.komelia.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import snd.komelia.settings.CommonSettingsRepository
import snd.komga.client.series.KomgaSeriesId

/**
 * App-wide handle for the local per-user "Planned" (a lire) series, exposed
 * via [LocalPlanned] so any series menu / bulk action can toggle a series
 * without threading callbacks. Backed by per-server settings (a local
 * series-id set, never sent to the server), independent from [FavoritesController]
 * — a series can be both favorited and planned.
 *
 * Deliberately fires NO screen-reload broadcast, for the same reason as
 * [FavoritesController]: the server knows nothing about this list, so
 * re-querying the visible listing is waste, and on a randomly-sorted library it
 * re-rolled the whole order. Views read [plannedIds] and update on their own.
 */
class PlannedController(
    val plannedIds: StateFlow<Set<String>>,
    private val settingsRepository: CommonSettingsRepository,
    private val scope: CoroutineScope,
) {
    fun isPlanned(id: KomgaSeriesId): Boolean = id.value in plannedIds.value

    fun add(ids: Collection<KomgaSeriesId>) {
        if (ids.isEmpty()) return
        scope.launch {
            settingsRepository.putPlannedSeriesIds(plannedIds.value + ids.map { it.value })
        }
    }

    fun remove(ids: Collection<KomgaSeriesId>) {
        if (ids.isEmpty()) return
        val remove = ids.map { it.value }.toSet()
        scope.launch {
            settingsRepository.putPlannedSeriesIds(plannedIds.value - remove)
        }
    }

    fun toggle(id: KomgaSeriesId) {
        if (isPlanned(id)) remove(listOf(id)) else add(listOf(id))
    }
}
