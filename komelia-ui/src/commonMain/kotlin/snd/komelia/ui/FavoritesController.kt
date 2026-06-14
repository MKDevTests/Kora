package snd.komelia.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import snd.komelia.settings.CommonSettingsRepository
import snd.komga.client.series.KomgaSeriesId

/**
 * App-wide handle for the local per-user Favorites, exposed via [LocalFavorites]
 * so any series menu / bulk action can favorite or unfavorite a series without
 * threading callbacks. Backed by per-server settings (a local series-id set,
 * never sent to the server). [onChanged] nudges the current screen to reload so
 * the Favorites view reflects the change immediately.
 */
class FavoritesController(
    val favoriteIds: StateFlow<Set<String>>,
    private val settingsRepository: CommonSettingsRepository,
    private val scope: CoroutineScope,
    private val onChanged: () -> Unit,
) {
    fun isFavorite(id: KomgaSeriesId): Boolean = id.value in favoriteIds.value

    fun add(ids: Collection<KomgaSeriesId>) {
        if (ids.isEmpty()) return
        scope.launch {
            settingsRepository.putFavoriteSeriesIds(favoriteIds.value + ids.map { it.value })
            onChanged()
        }
    }

    fun remove(ids: Collection<KomgaSeriesId>) {
        if (ids.isEmpty()) return
        val remove = ids.map { it.value }.toSet()
        scope.launch {
            settingsRepository.putFavoriteSeriesIds(favoriteIds.value - remove)
            onChanged()
        }
    }

    fun toggle(id: KomgaSeriesId) {
        if (isFavorite(id)) remove(listOf(id)) else add(listOf(id))
    }
}
