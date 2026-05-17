package snd.komelia.ui.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import snd.komelia.AppNotifications
import snd.komelia.komga.api.KomgaBookApi
import snd.komelia.komga.api.KomgaSeriesApi
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.settings.CommonSettingsRepository
import snd.komelia.ui.LoadState
import snd.komga.client.book.KomgaBookSearch
import snd.komga.client.common.KomgaPageRequest
import snd.komga.client.common.KomgaSort
import snd.komga.client.library.KomgaLibrary
import snd.komga.client.library.KomgaLibraryId
import snd.komga.client.search.allOfBooks
import snd.komga.client.search.allOfSeries
import snd.komga.client.series.KomgaSeries
import snd.komga.client.series.KomgaSeriesSearch

@OptIn(FlowPreview::class)
class SearchViewModel(
    private val seriesApi: KomgaSeriesApi,
    private val bookApi: KomgaBookApi,
    private val appNotifications: AppNotifications,
    private val libraries: StateFlow<List<KomgaLibrary>>,
    private val settingsRepository: CommonSettingsRepository,
) : StateScreenModel<LoadState<Unit>>(LoadState.Uninitialized) {

    /**
     * Whether to append Lucene fuzzy syntax (~1) to query terms. Loaded from
     * settings in [initialize], persisted via [onFuzzyEnabledChange]. Drives
     * the [toFuzzyQuery] transform and the search bar's "≈ Fuzzy" chip.
     */
    var fuzzyEnabled by mutableStateOf(true)
        private set

    var seriesResults by mutableStateOf<List<KomgaSeries>>(emptyList())
        private set
    var seriesCurrentPage by mutableStateOf(1)
        private set
    var seriesTotalPages by mutableStateOf(1)
        private set

    var bookResults by mutableStateOf<List<KomeliaBook>>(emptyList())
        private set
    var bookCurrentPage by mutableStateOf(1)
        private set
    var bookTotalPages by mutableStateOf(1)
        private set

    var query by mutableStateOf("")

    var selectedLibraryId by mutableStateOf<KomgaLibraryId?>(null)
        private set

    val availableLibraries: StateFlow<List<KomgaLibrary>> = libraries

    fun onSelectedLibraryChange(libraryId: KomgaLibraryId?) {
        selectedLibraryId = libraryId
        reload()
    }

    fun onFuzzyEnabledChange(enabled: Boolean) {
        if (fuzzyEnabled == enabled) return
        fuzzyEnabled = enabled
        screenModelScope.launch {
            settingsRepository.putSearchFuzzyEnabled(enabled)
        }
        reload()
    }

    private var userSelectedTab by mutableStateOf(SearchResultsTab.SERIES)
    var currentTab by mutableStateOf(SearchResultsTab.SERIES)
        private set

    suspend fun initialize(initialQuery: String?) {
        if (state.value != LoadState.Uninitialized && initialQuery == query) return
        mutableState.value = LoadState.Loading
        fuzzyEnabled = settingsRepository.getSearchFuzzyEnabled().first()
        initialQuery?.let { query = it }
        loadSearchResults()

        snapshotFlow { query }
            .drop(if (initialQuery != null) 1 else 0)
            .debounce {
                if (it.isBlank()) 0
                else 500
            }
            .distinctUntilChanged()
            .onEach { loadSearchResults() }
            .launchIn(screenModelScope)
        mutableState.value = LoadState.Success(Unit)
    }

    fun reload() {
        screenModelScope.launch {
            mutableState.value = LoadState.Loading
            loadSearchResults()
            mutableState.value = LoadState.Success(Unit)
        }
    }

    private suspend fun loadSearchResults() {
        currentTab = userSelectedTab
        loadSeriesPage(1)
        loadBooksPage(1)
        if (seriesResults.isEmpty() && bookResults.isNotEmpty() && currentTab == SearchResultsTab.SERIES) {
            currentTab = SearchResultsTab.BOOKS
        } else if (bookResults.isEmpty() && seriesResults.isNotEmpty() && currentTab == SearchResultsTab.BOOKS) {
            currentTab = SearchResultsTab.SERIES
        }
    }

    fun onSeriesPageChange(pageNumber: Int) {
        screenModelScope.launch {
            mutableState.value = LoadState.Loading
            loadSeriesPage(pageNumber)
            mutableState.value = LoadState.Success(Unit)
        }
    }

    private suspend fun loadSeriesPage(pageNumber: Int) {
        appNotifications.runCatchingToNotifications {
            val libId = selectedLibraryId
            val fuzzy = query.toFuzzyQuery()
            val search = if (libId != null) {
                KomgaSeriesSearch(
                    condition = allOfSeries { library { isEqualTo(libId) } }.toSeriesCondition(),
                    fullTextSearch = fuzzy,
                )
            } else {
                KomgaSeriesSearch(fullTextSearch = fuzzy)
            }
            val page = seriesApi.getSeriesList(
                search,
                KomgaPageRequest(
                    pageIndex = pageNumber - 1,
                    size = 10,
                    sort = if (query.isBlank()) KomgaSort.KomgaSeriesSort.byLastModifiedDateDesc() else KomgaSort.Unsorted
                )
            )

            seriesCurrentPage = page.number + 1
            seriesTotalPages = page.totalPages
            seriesResults = page.content
        }.onFailure { mutableState.value = LoadState.Error(it) }
    }

    fun onBookPageChange(pageNumber: Int) {
        screenModelScope.launch {
            mutableState.value = LoadState.Loading
            loadBooksPage(pageNumber)
            mutableState.value = LoadState.Success(Unit)
        }
    }

    private suspend fun loadBooksPage(pageNumber: Int) {
        appNotifications.runCatchingToNotifications {
            val libId = selectedLibraryId
            val fuzzy = query.toFuzzyQuery()
            val search = if (libId != null) {
                KomgaBookSearch(
                    condition = allOfBooks { library { isEqualTo(libId) } }.toBookCondition(),
                    fullTextSearch = fuzzy,
                )
            } else {
                KomgaBookSearch(fullTextSearch = fuzzy)
            }
            val page = bookApi.getBookList(
                search,
                KomgaPageRequest(
                    pageIndex = pageNumber - 1,
                    size = 10,
                    sort = if (query.isBlank()) KomgaSort.KomgaBooksSort.byLastModifiedDateDesc() else KomgaSort.Unsorted
                )
            )

            bookCurrentPage = page.number + 1
            bookTotalPages = page.totalPages
            bookResults = page.content
        }.onFailure { mutableState.value = LoadState.Error(it) }
    }

    fun onSearchTypeChange(type: SearchResultsTab) {
        this.currentTab = type
        this.userSelectedTab = type
    }

    enum class SearchResultsTab {
        SERIES,
        BOOKS,
    }

    /**
     * Append Lucene fuzzy syntax (~1 = Levenshtein distance 1) to each query
     * term long enough to tolerate a typo without producing noise. Lets
     * "narito" match "Naruto", "darogon" match "dragon", etc.
     *
     * Komga backs full-text search with Lucene (Hibernate Search 6), which
     * understands ~N natively, so the fuzziness is evaluated server-side and
     * doesn't widen what we have to fetch.
     *
     * Conservative rules to avoid degrading "exact" searches:
     *  - Empty / blank query → returned as-is (Komga interprets as "list all").
     *  - Already contains Lucene operators (~, ^, *, ?, quotes, +/-, : etc.)
     *    → assume the user knows what they're typing and pass through.
     *  - Terms shorter than 4 chars → no fuzziness (a 3-char ~1 matches
     *    almost everything and slows Komga down).
     */
    private fun String.toFuzzyQuery(): String {
        if (!fuzzyEnabled) return this
        val trimmed = trim()
        if (trimmed.isEmpty()) return trimmed
        if (trimmed.any { it in "\"+-*?~^()[]{}:\\/" }) return trimmed
        return trimmed
            .split(Regex("\\s+"))
            .joinToString(" ") { term ->
                if (term.length >= 4) "$term~1" else term
            }
    }
}

data class SearchResults(
    val series: List<KomgaSeries>,
    val books: List<KomeliaBook>
)
