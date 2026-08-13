package snd.komelia.ui.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import snd.komelia.AppNotifications
import snd.komelia.komga.api.KomgaBookApi
import snd.komelia.komga.api.KomgaCollectionsApi
import snd.komelia.komga.api.KomgaLibraryApi
import snd.komelia.komga.api.KomgaReadListApi
import snd.komelia.komga.api.KomgaReferentialApi
import snd.komelia.komga.api.KomgaSeriesApi
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.offline.tasks.OfflineTaskEmitter
import snd.komelia.settings.CommonSettingsRepository
import snd.komelia.ui.LoadState
import snd.komelia.ui.LoadState.Error
import snd.komelia.ui.LoadState.Loading
import snd.komelia.ui.LoadState.Success
import snd.komelia.ui.LoadState.Uninitialized
import snd.komelia.ui.common.cards.defaultCardWidth
import snd.komelia.ui.common.menus.LibraryMenuActions
import snd.komelia.ui.library.LibraryTab.COLLECTIONS
import snd.komelia.ui.library.LibraryTab.FOR_YOU
import snd.komelia.ui.library.LibraryTab.GENRE
import snd.komelia.ui.library.LibraryTab.READ_LISTS
import snd.komelia.ui.library.LibraryTab.SERIES
import snd.komga.client.book.KomgaBookSearch
import snd.komga.client.book.KomgaReadStatus
import snd.komga.client.common.KomgaPageRequest
import snd.komga.client.common.KomgaSort.KomgaBooksSort
import snd.komga.client.library.KomgaLibrary
import snd.komga.client.library.KomgaLibraryId
import snd.komga.client.search.allOfBooks
import snd.komga.client.sse.KomgaEvent
import snd.komga.client.sse.KomgaEvent.CollectionAdded
import snd.komga.client.sse.KomgaEvent.CollectionDeleted
import snd.komga.client.sse.KomgaEvent.ReadListAdded
import snd.komga.client.sse.KomgaEvent.ReadListDeleted

/**
 * Process-wide cache of each library's item counts (collections / read lists /
 * genres), keyed by library id. The library screen is torn down and rebuilt on
 * every library switch (navigateToLibrary does a replaceAll), so without this a
 * return to a library re-fetches every count behind a spinner. With it the
 * cached counts show instantly and refresh silently. Cleared on process restart.
 */
private object LibraryCountsCache {
    data class Counts(val collections: Int, val readLists: Int, val genres: Int)

    private val byLibrary = mutableMapOf<String, Counts>()
    fun get(libraryId: String): Counts? = byLibrary[libraryId]
    fun put(libraryId: String, counts: Counts) {
        byLibrary[libraryId] = counts
    }
}

class LibraryViewModel(
    private val libraryApi: KomgaLibraryApi,
    private val collectionApi: KomgaCollectionsApi,
    private val readListsApi: KomgaReadListApi,
    private val taskEmitter: OfflineTaskEmitter,
    val bookApi: KomgaBookApi,
    seriesApi: KomgaSeriesApi,
    private val referentialApi: KomgaReferentialApi,

    private val appNotifications: AppNotifications,
    private val komgaEvents: SharedFlow<KomgaEvent>,
    libraryFlow: Flow<KomgaLibrary?>,
    private val libraryId: KomgaLibraryId?,
    private val settingsRepository: CommonSettingsRepository,
    private val librarySeriesFiltersRepository: snd.komelia.libraryfilters.LibrarySeriesFiltersRepository,
    private val similarityIndexRepository: snd.komelia.similarity.SimilarityIndexRepository,
    private val libraryCountsRepository: snd.komelia.library.LibraryCountsRepository,
    private val keepReadingRepository: snd.komelia.library.KeepReadingRepository,
    similarityIndexBuilder: snd.komelia.similarity.SimilarityIndexBuilder?,
    seriesRatingsRepository: snd.komelia.ratings.SeriesRatingsRepository,
    suggestionFeedbackRepository: snd.komelia.similarity.SuggestionFeedbackRepository,
    hiddenSeriesIds: StateFlow<Set<String>>,
) : StateScreenModel<LoadState<Unit>>(Uninitialized) {
    val library = libraryFlow.onEach { settingsRepository.putLastSelectedLibraryId(it?.id) }
        .stateIn(screenModelScope, SharingStarted.Eagerly, null)
    val cardWidth = settingsRepository.getCardWidth().map { Dp(it.toFloat()) }
        .stateIn(screenModelScope, SharingStarted.Eagerly, defaultCardWidth.dp)
    val showContinueReading = settingsRepository.getShowContinueReading()
        .stateIn(screenModelScope, SharingStarted.Eagerly, true)

    var currentTab by mutableStateOf(SERIES)
    var collectionsCount by mutableStateOf(0)
        private set
    var readListsCount by mutableStateOf(0)
        private set
    var genresCount by mutableStateOf(0)
        private set

    val genreTabEnabled = settingsRepository.getExperimentalGenreTab()
        .stateIn(screenModelScope, SharingStarted.Eagerly, false)

    var keepReadingBooks by mutableStateOf<List<KomeliaBook>>(emptyList())
        private set

    private val reloadEventsEnabled = MutableStateFlow(true)
    private val reloadJobsFlow = MutableSharedFlow<Unit>(1, 0, DROP_OLDEST)

    val seriesTabState = LibrarySeriesTabState(
        bookApi = bookApi,
        seriesApi = seriesApi,
        referentialApi = referentialApi,
        notifications = appNotifications,
        komgaEvents = komgaEvents,
        settingsRepository = settingsRepository,
        libraryFlow = library,
        libraryId = libraryId,
        taskEmitter = taskEmitter,
        librarySeriesFiltersRepository = librarySeriesFiltersRepository,
        seriesRatingsRepository = seriesRatingsRepository,
    )
    val collectionsTabState = LibraryCollectionsTabState(
        collectionApi = collectionApi,
        appNotifications = appNotifications,
        events = komgaEvents,
        library = library,
        cardWidth = cardWidth
    )
    val readListsTabState = LibraryReadListsTabState(
        readListApi = readListsApi,
        appNotifications = appNotifications,
        komgaEvents = komgaEvents,
        library = library,
        cardWidth = cardWidth
    )
    val genreTabState = LibraryGenreTabState(
        seriesApi = seriesApi,
        referentialApi = referentialApi,
        appNotifications = appNotifications,
        settingsRepository = settingsRepository,
        library = library,
        cardWidth = cardWidth,
    )
    private val forYouSuggester = snd.komelia.ui.suggestions.ForYouSuggester(
        seriesApi = seriesApi,
        repository = similarityIndexRepository,
        indexBuilder = similarityIndexBuilder,
        ratingsRepository = seriesRatingsRepository,
        favoriteSeriesIds = settingsRepository.getFavoriteSeriesIds(),
        // Same rule as the series "Similar" tab: hidden and ignored series are
        // indexed but never proposed.
        excludedSeriesIds = combine(
            settingsRepository.getIgnoreListEnabled(),
            settingsRepository.getIgnoredSeriesIds(),
            hiddenSeriesIds,
        ) { enabled, ignored, hidden -> (if (enabled) ignored else emptySet()) + hidden },
        feedbackRepository = suggestionFeedbackRepository,
    )

    val forYouTabState = LibraryForYouTabState(
        library = library,
        notifications = appNotifications,
        suggester = forYouSuggester,
        feedbackRepository = suggestionFeedbackRepository,
        screenModelScope = screenModelScope,
        cardWidth = cardWidth,
    )
    val showToolbar = seriesTabState.isInEditMode.map { !it }
        .stateIn(screenModelScope, SharingStarted.Eagerly, true)

    fun initialize(seriesFilter: SeriesScreenFilter? = null) {
        if (state.value !is Uninitialized) return

        if (seriesFilter != null) toBrowseTab()

        // Two independent requests, so two coroutines. Chained, "Keep reading"
        // waited for the counts refresh — which nothing waits on any more, the
        // counts being remembered — and only then asked for its own books: five
        // to ten seconds before the row appeared, for one request of work.
        screenModelScope.launch { loadItemCounts() }
        screenModelScope.launch { loadKeepReadingBooks() }
        startKomgaEventListener()

        reloadJobsFlow.onEach {
            reloadEventsEnabled.first { it }
            screenModelScope.launch { loadItemCounts() }
            loadKeepReadingBooks()
            delay(1000)
        }.launchIn(screenModelScope)
    }

    fun reload() {
        mutableState.value = Loading
        screenModelScope.launch { loadItemCounts() }
        screenModelScope.launch {
            loadKeepReadingBooks()
            when (currentTab) {
                SERIES -> seriesTabState.reload()
                COLLECTIONS -> collectionsTabState.reload()
                READ_LISTS -> readListsTabState.reload()
                GENRE -> genreTabState.reload()
                FOR_YOU -> forYouTabState.reload()
            }
        }
    }

    private suspend fun loadItemCounts() {
        if (state.value is Error) return

        // Paint the last known counts immediately, from memory within a session
        // and from disk across app starts. Measured on the real server, asking
        // for them costs 839 ms (collections), 4.5 s (genres) and 6.9 s (read
        // lists) — the chips used to appear seven seconds after their screen.
        val libraryKey = libraryId?.value
        if (state.value !is Success) {
            val remembered = snd.komelia.perf.PerfTrace.measure(
                // Screen opened -> chips can draw. The refresh below is timed
                // separately as library.counts: it no longer holds anything up.
                label = "library.chipsReady",
                // 1 = painted from memory or disk, 0 = nothing known yet and the
                // chips have to wait for the server. Which of the two happens is
                // the whole question here.
                count = { it: LibraryCountsCache.Counts? -> if (it == null) 0 else 1 },
            ) {
                libraryKey?.let { key ->
                    LibraryCountsCache.get(key)
                        ?: libraryCountsRepository.get(key)?.let {
                            LibraryCountsCache.Counts(it.collections, it.readLists, it.genres)
                        }
                }
            }
            remembered?.let { cached ->
                collectionsCount = cached.collections
                readListsCount = cached.readLists
                genresCount = cached.genres
                applyTabFallback()
                mutableState.value = Success(Unit)
            }
        }

        appNotifications.runCatchingToNotifications {
            // Only show the spinner when there is nothing on screen yet.
            if (state.value !is Success) mutableState.value = Loading
            val pageRequest = KomgaPageRequest(size = 0)
            val libraryIds = listOfNotNull(libraryId)

            // The three counts are independent — fetch them concurrently
            // instead of one network round-trip after another.
            // Measured individually, and the whole set as well: the three run
            // concurrently but share one connection pool with the series grid,
            // so "which one is slow" and "how long before the chips can appear"
            // are two different questions. The genre count is the suspect —
            // it downloads every tag of the library to count the kora:genre ones.
            snd.komelia.perf.PerfTrace.measure("library.counts") {
                coroutineScope {
                    val collectionsDeferred = async {
                        snd.komelia.perf.PerfTrace.measure("library.counts.collections") {
                            collectionApi.getAll(libraryIds = libraryIds, pageRequest = pageRequest).totalElements
                        }
                    }
                    val readListsDeferred = async {
                        snd.komelia.perf.PerfTrace.measure("library.counts.readlists") {
                            readListsApi.getAll(libraryIds = libraryIds, pageRequest = pageRequest).totalElements
                        }
                    }
                    val genresDeferred = async {
                        if (genreTabEnabled.value) countGenres() else 0
                    }
                    collectionsCount = collectionsDeferred.await()
                    readListsCount = readListsDeferred.await()
                    genresCount = genresDeferred.await()
                }
            }

            libraryKey?.let {
                LibraryCountsCache.put(it, LibraryCountsCache.Counts(collectionsCount, readListsCount, genresCount))
                libraryCountsRepository.put(
                    it,
                    snd.komelia.library.LibraryCounts(collectionsCount, readListsCount, genresCount),
                )
            }
            applyTabFallback()
            mutableState.value = Success(Unit)
        }.onFailure { mutableState.value = Error(it) }
    }

    /**
     * How many genres the library holds.
     *
     * Read from the local term index when it exists: the index already stores
     * each series' `kora:genre:*` terms, so this is a SQLite read instead of
     * downloading every tag of the library — 3482 of them on the manga one, to
     * end up with a number under thirty. The server is asked only when the
     * library was never indexed.
     */
    private suspend fun countGenres(): Int {
        val key = libraryId?.value
        if (key != null) {
            val indexed = snd.komelia.perf.PerfTrace.measure("library.counts.genres.local") {
                similarityIndexRepository.entriesOf(key)
            }
            if (indexed.isNotEmpty()) {
                return indexed.flatMap { it.terms.genres }.distinct().size
            }
        }
        return snd.komelia.perf.PerfTrace.measure(
            label = "library.counts.genres",
            count = { tags: Int -> tags },
        ) {
            runCatching {
                referentialApi.getSeriesTags(libraryId = libraryId)
                    .count { GenreLabels.isGenreTag(it) }
            }.getOrDefault(0)
        }
    }

    private fun applyTabFallback() {
        if (collectionsCount == 0 && currentTab == COLLECTIONS) currentTab = SERIES
        if (readListsCount == 0 && currentTab == READ_LISTS) currentTab = SERIES
        if (genresCount == 0 && currentTab == GENRE) currentTab = SERIES
    }

    private suspend fun loadKeepReadingBooks() {
        val libId = libraryId ?: return

        // Draw the row from the last known answer while the server is asked
        // again. The request costs one to three seconds here, and an empty row
        // for that long — at the top of the screen, where the user is heading —
        // is worse than a book that turns out to be finished.
        if (keepReadingBooks.isEmpty()) {
            val remembered = snd.komelia.perf.PerfTrace.measure(
                label = "library.keepReading.remembered",
                count = { books: List<*> -> books.size },
            ) { keepReadingRepository.get(libId.value) }
            if (remembered.isNotEmpty() && keepReadingBooks.isEmpty()) {
                keepReadingBooks = remembered
            }
        }

        appNotifications.runCatchingToNotifications {
            keepReadingBooks = snd.komelia.perf.PerfTrace.measure(
                label = "library.keepReading",
                count = { books: List<*> -> books.size },
            ) {
                // The `search` overload, not `conditionBuilder`: this is a browse
                // query, and only that overload goes through the chapter filter
                // (see ChapterFilteringBookApi). Same request either way.
                bookApi.getBookList(
                    search = KomgaBookSearch(
                        allOfBooks {
                            library { isEqualTo(libId) }
                            readStatus { isEqualTo(KomgaReadStatus.IN_PROGRESS) }
                        }.toBookCondition()
                    ),
                    pageRequest = KomgaPageRequest(
                        sort = KomgaBooksSort.byReadDateDesc(),
                        size = 20
                    )
                ).content
            }
            keepReadingRepository.put(libId.value, keepReadingBooks)
        }
    }

    /**
     * Re-reads the "Keep reading" shelf only — one request, no spinner. Called
     * on the way back from the reader: the progress of the book just read has
     * changed even when no other volume was opened.
     */
    fun refreshKeepReading() {
        screenModelScope.launch { loadKeepReadingBooks() }
    }

    fun toggleContinueReading() {
        screenModelScope.launch {
            settingsRepository.putShowContinueReading(!showContinueReading.value)
        }
    }

    fun toBrowseTab() {
        currentTab = SERIES
    }

    fun toCollectionsTab() {
        currentTab = COLLECTIONS
    }

    fun toReadListsTab() {
        currentTab = READ_LISTS
    }

    fun toGenreTab() {
        currentTab = GENRE
    }

    fun toForYouTab() {
        currentTab = FOR_YOU
    }

    fun libraryActions() = LibraryMenuActions(libraryApi, appNotifications, taskEmitter, screenModelScope)

    fun stopKomgaEventHandler() {
        reloadEventsEnabled.value = false
    }

    fun startKomgaEventHandler() {
        reloadEventsEnabled.value = true

    }

    private fun startKomgaEventListener() {
        komgaEvents.onEach { event ->
            when (event) {
                is ReadListAdded, is ReadListDeleted -> reloadJobsFlow.tryEmit(Unit)
                is CollectionAdded, is CollectionDeleted -> reloadJobsFlow.tryEmit(Unit)
                is KomgaEvent.ReadProgressSeriesChanged,
                is KomgaEvent.ReadProgressSeriesDeleted -> reloadJobsFlow.tryEmit(Unit)
                // Book-level progress too: finishing a volume elsewhere (series
                // screen, another device) has to drop it from "Keep reading"
                // without waiting for a manual refresh. The collector already
                // throttles, so a burst of page pushes costs one reload.
                is KomgaEvent.ReadProgressChanged,
                is KomgaEvent.ReadProgressDeleted -> reloadJobsFlow.tryEmit(Unit)

                else -> {}
            }
        }.launchIn(screenModelScope)
    }
}

enum class LibraryTab {
    SERIES,
    COLLECTIONS,
    READ_LISTS,
    GENRE,
    FOR_YOU
}

