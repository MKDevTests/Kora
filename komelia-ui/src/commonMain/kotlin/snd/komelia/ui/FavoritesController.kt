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
 * never sent to the server).
 *
 * Deliberately fires NO screen-reload broadcast. Favoriting changes nothing the
 * server knows about, so re-querying the visible listing is pure waste — and on
 * a randomly-sorted library it re-rolled the whole order, which looked like the
 * app shuffling itself for no reason. Everything that shows favorites reads
 * [favoriteIds] and updates on its own.
 */
class FavoritesController(
    val favoriteIds: StateFlow<Set<String>>,
    private val settingsRepository: CommonSettingsRepository,
    private val scope: CoroutineScope,
) {
    fun isFavorite(id: KomgaSeriesId): Boolean = id.value in favoriteIds.value

    fun add(ids: Collection<KomgaSeriesId>) {
        if (ids.isEmpty()) return
        scope.launch {
            settingsRepository.putFavoriteSeriesIds(favoriteIds.value + ids.map { it.value })
        }
    }

    fun remove(ids: Collection<KomgaSeriesId>) {
        if (ids.isEmpty()) return
        val remove = ids.map { it.value }.toSet()
        scope.launch {
            settingsRepository.putFavoriteSeriesIds(favoriteIds.value - remove)
        }
    }

    fun toggle(id: KomgaSeriesId) {
        if (isFavorite(id)) remove(listOf(id)) else add(listOf(id))
    }
}
