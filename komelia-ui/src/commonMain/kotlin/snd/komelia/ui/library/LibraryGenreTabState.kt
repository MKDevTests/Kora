package snd.komelia.ui.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import snd.komelia.AppNotifications
import snd.komelia.komga.api.KomgaReferentialApi
import snd.komelia.komga.api.KomgaSeriesApi
import snd.komelia.ui.LoadState
import snd.komelia.ui.LoadState.Loading
import snd.komelia.ui.LoadState.Success
import snd.komelia.ui.LoadState.Uninitialized
import snd.komga.client.common.KomgaPageRequest
import snd.komga.client.common.KomgaSort
import snd.komga.client.library.KomgaLibrary
import snd.komga.client.search.allOfSeries
import snd.komga.client.series.KomgaSeriesId
import snd.komga.client.series.KomgaSeriesSearch

/**
 * Backs the experimental Genre tab. Discovers a library's genres from its
 * `kora:genre:*` series tags (one referential call) and, for each, fetches the
 * count + a representative cover. The catalog is held in memory; a genre's
 * series are listed live by [GenreSeriesScreen]. Refreshed on pull-to-refresh.
 */
class LibraryGenreTabState(
    private val seriesApi: KomgaSeriesApi,
    private val referentialApi: KomgaReferentialApi,
    private val appNotifications: AppNotifications,
    private val library: StateFlow<KomgaLibrary?>,
    val cardWidth: StateFlow<Dp>,
) : StateScreenModel<LoadState<Unit>>(Uninitialized) {

    var genres: List<GenreTile> by mutableStateOf(emptyList())
        private set

    fun initialize() {
        if (state.value !is Uninitialized) return
        screenModelScope.launch { loadGenres() }
    }

    fun reload() {
        screenModelScope.launch { loadGenres() }
    }

    private suspend fun loadGenres() {
        val lib = library.value ?: return
        appNotifications.runCatchingToNotifications {
            if (genres.isEmpty()) mutableState.value = Loading

            val genreTags = referentialApi.getSeriesTags(libraryId = lib.id)
                .filter { GenreLabels.isGenreTag(it) }

            val tiles = coroutineScope {
                genreTags.map { genreTag ->
                    async {
                        val page = seriesApi.getSeriesList(
                            KomgaSeriesSearch(
                                condition = allOfSeries {
                                    library { isEqualTo(lib.id) }
                                    tag { isEqualTo(genreTag) }
                                }.toSeriesCondition()
                            ),
                            KomgaPageRequest(
                                pageIndex = 0,
                                size = 1,
                                sort = KomgaSort.KomgaSeriesSort.byTitleAsc(),
                            )
                        )
                        GenreTile(
                            tag = genreTag,
                            slug = GenreLabels.slugOf(genreTag),
                            label = GenreLabels.label(GenreLabels.slugOf(genreTag)),
                            count = page.totalElements,
                            coverSeriesId = page.content.firstOrNull()?.id,
                        )
                    }
                }.awaitAll()
            }

            genres = tiles.filter { it.count > 0 }.sortedByDescending { it.count }
            mutableState.value = Success(Unit)
        }.onFailure { mutableState.value = LoadState.Error(it) }
    }
}

data class GenreTile(
    val tag: String,
    val slug: String,
    val label: String,
    val count: Int,
    val coverSeriesId: KomgaSeriesId?,
)
