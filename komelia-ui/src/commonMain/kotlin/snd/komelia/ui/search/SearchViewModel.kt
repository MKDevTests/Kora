package snd.komelia.ui.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import snd.komelia.AppNotifications
import snd.komelia.hidden.HIDDEN_TAG
import snd.komelia.komga.api.KomgaBookApi
import snd.komelia.komga.api.KomgaReferentialApi
import snd.komelia.komga.api.KomgaSeriesApi
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.settings.CommonSettingsRepository
import snd.komelia.ui.LoadState
import snd.komelia.ui.common.authorRolesOrder
import snd.komga.client.book.KomgaBookSearch
import snd.komga.client.common.KomgaPageRequest
import snd.komga.client.common.KomgaSort
import snd.komga.client.common.pencillerRole
import snd.komga.client.common.writerRole
import snd.komga.client.library.KomgaLibrary
import snd.komga.client.library.KomgaLibraryId
import snd.komga.client.search.KomgaSearchCondition
import snd.komga.client.search.KomgaSearchOperator
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

    /**
     * The search in flight. A new one cancels it, so opening the tab and
     * immediately typing abandons the newest-additions request instead of
     * queueing behind it.
     */
    private var searchJob: Job? = null

    /**
     * The credits the Authors tab is about — writing and drawing, not
     * lettering, inking or translation.
     *
     * The tab used to list every name in every role, because the endpoint it
     * called ([KomgaReferentialApi.getAuthorsNames]) takes a search string and
     * nothing else: translators and editors came back alongside the authors.
     *
     * Which roles count is not decided here. Settings → Appearance already
     * carries that answer for the series and book screens, so the search tab
     * reads the same setting: filter off means the historical writer +
     * penciller, filter on means whatever the user kept. Hiding every role
     * leaves the tab empty, which is what asking for no roles means.
     *
     * Read once per screen model, like [fuzzyEnabled]; a change in settings
     * applies the next time the search screen is opened.
     */
    private var authorRoles: List<String> = listOf(writerRole, pencillerRole)

    private suspend fun resolveAuthorRoles(): List<String> {
        val filterEnabled = settingsRepository.getAuthorRolesFilterEnabled().first()
        if (!filterEnabled) return listOf(writerRole, pencillerRole)
        val hidden = settingsRepository.getHiddenAuthorRoles().first()
        return authorRolesOrder.filterNot { it in hidden }
    }

    /** `author is (name, writer)` OR the same for every other counted role. */
    private fun authorRoleConditions(name: String) = authorRoles.map { role ->
        KomgaSearchCondition.Author(
            KomgaSearchOperator.Is(KomgaSearchCondition.AuthorMatch(name, role))
        )
    }

    /** True once anything has been fetched — see [SearchScreen]'s loading branch. */
    val hasAnyResults: Boolean
        get() = seriesResults.isNotEmpty() || bookResults.isNotEmpty() || authorNames.isNotEmpty()

    suspend fun initialize(initialQuery: String?) {
        // Preserve the in-memory results (and the current tab) when the user
        // returns to the search tab after opening a result. The tab opens with
        // initialQuery == null, so the old `initialQuery == query` guard failed
        // once anything had been typed (null != "naruto") and re-ran the whole
        // search on every back-navigation. Treat a null/blank seed as "no new
        // query requested" and skip the reload when already initialized. A
        // genuinely new seed (AppBar search → replace(SearchScreen(it))) is
        // non-blank and still falls through to load.
        if (state.value != LoadState.Uninitialized && (initialQuery.isNullOrBlank() || initialQuery == query)) return
        mutableState.value = LoadState.Loading
        fuzzyEnabled = settingsRepository.getSearchFuzzyEnabled().first()
        authorRoles = resolveAuthorRoles()
        initialQuery?.let { query = it }

        // The first load goes through this collector rather than beside it.
        // It used to be launched AFTER an inline loadSearchResults(), so while
        // the newest-additions list was loading nothing was watching `query`:
        // whatever was typed during those seconds was only noticed once the
        // list came back. snapshotFlow emits the current value on collection,
        // so the initial load is that first emission — undelayed, because a
        // blank query (the tab's own opening) and the seed handed in by the
        // app bar both bypass the typing debounce.
        snapshotFlow { query }
            .distinctUntilChanged()
            .debounce { if (it.isBlank() || it == initialQuery) 0 else 500 }
            .onEach {
                selectedAuthor = null
                startSearch()
            }
            .launchIn(screenModelScope)
    }

    fun reload() {
        startSearch(reloadAuthor = true)
    }

    private fun startSearch(reloadAuthor: Boolean = false) {
        searchJob?.cancel()
        searchJob = screenModelScope.launch {
            mutableState.value = LoadState.Loading
            loadSearchResults()
            if (reloadAuthor && selectedAuthor != null) {
                loadAuthorSeriesPage(1)
                loadAuthorBooksPage(1)
            }
            mutableState.value = LoadState.Success(Unit)
        }
    }

    /**
     * Series, books and author names are three independent requests; they used
     * to run one after the other, so the wait was their sum. Four in flight is
     * the app-wide ceiling, and three is under it.
     */
    private suspend fun loadSearchResults() = coroutineScope {
        currentTab = userSelectedTab
        listOf(
            async { loadSeriesPage(1) },
            async { loadBooksPage(1) },
            async { loadAuthorNames() },
        ).awaitAll()
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
                    condition = allOfSeries {
                        library { isEqualTo(libId) }
                        tag { isNotEqualTo(HIDDEN_TAG) }
                    }.toSeriesCondition(),
                    fullTextSearch = fuzzy,
                )
            } else {
                KomgaSeriesSearch(
                    condition = allOfSeries { tag { isNotEqualTo(HIDDEN_TAG) } }.toSeriesCondition(),
                    fullTextSearch = fuzzy,
                )
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

    /**
     * One request per counted role — /authors takes a single role, and there is
     * no names endpoint that takes any. Two in flight at most, so that with the
     * series and book requests running alongside this stays at the app-wide
     * ceiling of four rather than stampeding the server.
     */
    private suspend fun loadAuthorNames() {
        val roles = authorRoles
        if (roles.isEmpty()) {
            authorNames = emptyList()
            return
        }
        appNotifications.runCatchingToNotifications {
            val search = query.ifBlank { null }
            // The library chip scopes the series and book tabs; it scopes this
            // one too. /authors filters server-side, so it costs nothing.
            val libraryIds = listOfNotNull(selectedLibraryId)
            val limit = Semaphore(2)
            authorNames = coroutineScope {
                roles.map { role ->
                    async {
                        limit.withPermit {
                            referentialApi.getAuthors(
                                search = search,
                                role = role,
                                libraryIds = libraryIds,
                                pageRequest = KomgaPageRequest(unpaged = true),
                            ).content.map { it.name }
                        }
                    }
                }.awaitAll().flatten().distinct().sorted()
            }
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
        val roleConditions = authorRoleConditions(authorName)
        if (roleConditions.isEmpty()) return
        appNotifications.runCatchingToNotifications {
            val libId = selectedLibraryId
            // The role has to be pinned here too. `AuthorMatch(name, null)`
            // matches ANY role, so filtering the names list alone would have
            // moved the problem rather than fixed it: picking a name would
            // still have pulled in the series that name only translated.
            val condition = KomgaSearchCondition.AllOfSeries(
                listOf(
                    KomgaSearchCondition.AnyOfSeries(roleConditions),
                    allOfSeries {
                        libId?.let { library { isEqualTo(it) } }
                        tag { isNotEqualTo(HIDDEN_TAG) }
                    }.toSeriesCondition(),
                )
            )
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
        val roleConditions = authorRoleConditions(authorName)
        if (roleConditions.isEmpty()) return
        appNotifications.runCatchingToNotifications {
            val libId = selectedLibraryId
            val condition = KomgaSearchCondition.AllOfBook(
                buildList<KomgaSearchCondition.BookCondition> {
                    add(KomgaSearchCondition.AnyOfBook(roleConditions))
                    // Only when there is one — an AllOf with an empty AllOf
                    // inside is not a condition worth sending.
                    if (libId != null) {
                        add(allOfBooks { library { isEqualTo(libId) } }.toBookCondition())
                    }
                }
            )
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
