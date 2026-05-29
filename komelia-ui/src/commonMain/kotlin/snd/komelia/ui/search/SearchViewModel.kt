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
import snd.komelia.komga.api.KomgaReferentialApi
import snd.komelia.komga.api.KomgaSeriesApi
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.settings.CommonSettingsRepository
import snd.komelia.ui.LoadState
import snd.komga.client.book.KomgaBookSearch
import snd.komga.client.common.KomgaPageRequest
import snd.komga.client.common.KomgaSort
import snd.komga.client.library.KomgaLibrary
import snd.komga.client.library.KomgaLibraryId
import snd.komga.client.search.KomgaSearchCondition
import snd.komga.client.search.allOfBooks
import snd.komga.client.search.allOfSeries
import snd.komga.client.series.KomgaSeries
import snd.komga.client.series.KomgaSeriesSearch

@OptIn(FlowPreview::class)
class SearchViewModel(
    private val seriesApi: KomgaSeriesApi,
    private val bookApi: KomgaBookApi,
    private val referentialApi: KomgaReferentialApi,
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

    // Authors tab: list of matching author names (role-agnostic), and the
    // currently drilled-into author with their series + books.
    var authorNames by mutableStateOf<List<String>>(emptyList())
        private set

    var selectedAuthor by mutableStateOf<String?>(null)
        private set

    var authorSeriesResults by mutableStateOf<List<KomgaSeries>>(emptyList())
        private set
    var authorSeriesCurrentPage by mutableStateOf(1)
        private set
    var authorSeriesTotalPages by mutableStateOf(1)
        private set

    var authorBookResults by mutableStateOf<List<KomeliaBook>>(emptyList())
        private set
    var authorBookCurrentPage by mutableStateOf(1)
        private set
    var authorBookTotalPages by mutableStateOf(1)
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
            .onEach {
                selectedAuthor = null
                loadSearchResults()
            }
            .launchIn(screenModelScope)
        mutableState.value = LoadState.Success(Unit)
    }

    fun reload() {
        screenModelScope.launch {
            mutableState.value = LoadState.Loading
            loadSearchResults()
            selectedAuthor?.let {
                loadAuthorSeriesPage(1)
                loadAuthorBooksPage(1)
            }
            mutableState.value = LoadState.Success(Unit)
        }
    }

    private suspend fun loadSearchResults() {
        currentTab = userSelectedTab
        loadSeriesPage(1)
        loadBooksPage(1)
        loadAuthorNames()
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

    private suspend fun loadAuthorNames() {
        appNotifications.runCatchingToNotifications {
            authorNames = referentialApi.getAuthorsNames(query.ifBlank { null })
        }.onFailure { mutableState.value = LoadState.Error(it) }
    }

    fun onAuthorSelected(name: String) {
        selectedAuthor = name
        screenModelScope.launch {
            mutableState.value = LoadState.Loading
            loadAuthorSeriesPage(1)
            loadAuthorBooksPage(1)
            mutableState.value = LoadState.Success(Unit)
        }
    }

    fun clearSelectedAuthor() {
        selectedAuthor = null
    }

    fun onAuthorSeriesPageChange(pageNumber: Int) {
        screenModelScope.launch {
            mutableState.value = LoadState.Loading
            loadAuthorSeriesPage(pageNumber)
            mutableState.value = LoadState.Success(Unit)
        }
    }

    fun onAuthorBookPageChange(pageNumber: Int) {
        screenModelScope.launch {
            mutableState.value = LoadState.Loading
            loadAuthorBooksPage(pageNumber)
            mutableState.value = LoadState.Success(Unit)
        }
    }

    private suspend fun loadAuthorSeriesPage(pageNumber: Int) {
        val authorName = selectedAuthor ?: return
        appNotifications.runCatchingToNotifications {
            val libId = selectedLibraryId
            val condition = allOfSeries {
                author { isEqualTo(KomgaSearchCondition.AuthorMatch(authorName, null)) }
                libId?.let { library { isEqualTo(it) } }
            }.toSeriesCondition()
            val page = seriesApi.getSeriesList(
                KomgaSeriesSearch(condition = condition),
                KomgaPageRequest(
                    pageIndex = pageNumber - 1,
                    size = 10,
                    sort = KomgaSort.KomgaSeriesSort.byLastModifiedDateDesc()
                )
            )
            authorSeriesCurrentPage = page.number + 1
            authorSeriesTotalPages = page.totalPages
            authorSeriesResults = page.content
        }.onFailure { mutableState.value = LoadState.Error(it) }
    }

    private suspend fun loadAuthorBooksPage(pageNumber: Int) {
        val authorName = selectedAuthor ?: return
        appNotifications.runCatchingToNotifications {
            val libId = selectedLibraryId
            val condition = allOfBooks {
                author { isEqualTo(KomgaSearchCondition.AuthorMatch(authorName, null)) }
                libId?.let { library { isEqualTo(it) } }
            }.toBookCondition()
            val page = bookApi.getBookList(
                KomgaBookSearch(condition = condition),
                KomgaPageRequest(
                    pageIndex = pageNumber - 1,
                    size = 10,
                    sort = KomgaSort.KomgaBooksSort.byLastModifiedDateDesc()
                )
            )
            authorBookCurrentPage = page.number + 1
            authorBookTotalPages = page.totalPages
            authorBookResults = page.content
        }.onFailure { mutableState.value = LoadState.Error(it) }
    }

    enum class SearchResultsTab {
        SERIES,
        BOOKS,
        AUTHORS,
    }

    /**
     * Append Lucene fuzzy syntax (~1 = Levenshtein distance 1) to each query
     * term so typos are tolerated. Lets "narito" match "Naruto", "darogon"
     * match "dragon", etc.
     *
     * Komga's full-text search is Lucene-backed (Hibernate Search 6), so the
     * fuzziness is evaluated server-side and doesn't widen what we fetch.
     *
     * Pass-through (raw query, no fuzzy syntax) when:
     *  - Empty / blank.
     *  - The query already contains Lucene operators (~, ^, *, ?, quotes,
     *    +/-, : etc.) — assume the user knows what they're typing.
     *  - ANY term is shorter than 4 characters. Komga relies on its own
     *    prefix-expansion magic for short / partial terms (e.g. "star w"
     *    matches "Star Wars" because "w" is treated as a prefix of "wars"),
     *    but mixing Lucene ~N syntax with that magic disables it: "star~1 w"
     *    finds nothing because Komga now treats "w" as an exact term. Drop
     *    fuzziness entirely whenever the query has any short term — the
     *    user is likely mid-typing or using a known abbreviation and exact
     *    prefix is what they want.
     */
    private fun String.toFuzzyQuery(): String {
        if (!fuzzyEnabled) return this
        val trimmed = trim()
        if (trimmed.isEmpty()) return trimmed
        if (trimmed.any { it in "\"+-*?~^()[]{}:\\/" }) return trimmed
        val terms = trimmed.split(Regex("\\s+"))
        if (terms.any { it.length < 4 }) return trimmed
        return terms.joinToString(" ") { "$it~1" }
    }
}

data class SearchResults(
    val series: List<KomgaSeries>,
    val books: List<KomeliaBook>
)
