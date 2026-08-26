package snd.komelia.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.NotoSerif_Bold
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import org.jetbrains.compose.resources.Font
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import dev.chrisbanes.haze.materials.HazeMaterials
import kotlinx.coroutines.launch
import snd.komelia.ui.LoadState.Error
import snd.komelia.ui.LoadState.Loading
import snd.komelia.ui.LoadState.Success
import snd.komelia.ui.LoadState.Uninitialized
import snd.komelia.ui.LocalAccentColor
import snd.komelia.ui.LocalTheme
import snd.komelia.ui.Theme
import snd.komelia.ui.LocalKomgaState
import snd.komelia.ui.LocalMainScreenViewModel
import snd.komelia.ui.LocalOfflineMode
import snd.komelia.ui.LocalRawStatusBarHeight
import snd.komelia.ui.LocalReloadEvents
import snd.komelia.ui.LocalFloatingToolbarPadding
import snd.komelia.ui.LocalHazeState
import snd.komelia.ui.LocalStrings
import snd.komelia.ui.LocalUseNewLibraryUI2
import snd.komelia.ui.LocalViewModelFactory
import snd.komelia.ui.ReloadableScreen
import snd.komelia.ui.collection.CollectionScreen
import snd.komelia.ui.common.ContinueReadingFab
import snd.komelia.ui.common.components.AppFilterChipDefaults
import snd.komelia.ui.common.components.AppSuggestionChipDefaults
import snd.komelia.ui.common.components.ErrorContent
import snd.komelia.ui.common.components.LoadingMaxSizeIndicator
import snd.komelia.ui.common.components.PageSizeSelectionDropdown
import snd.komelia.ui.common.menus.LibraryActionsMenu
import snd.komelia.ui.common.menus.LibraryMenuActions
import snd.komelia.ui.topbar.NewTopAppBar
import snd.komelia.ui.library.LibraryTab.COLLECTIONS
import snd.komelia.ui.library.LibraryTab.FOR_YOU
import snd.komelia.ui.library.LibraryTab.GENRE
import snd.komelia.ui.library.LibraryTab.READ_LISTS
import snd.komelia.ui.library.LibraryTab.SERIES
import snd.komelia.ui.library.view.ForYouContent
import snd.komelia.ui.library.view.LibraryCollectionsContent
import snd.komelia.ui.library.view.LibraryReadListsContent
import snd.komelia.ui.platform.BackPressHandler
import snd.komelia.ui.platform.ScreenPullToRefreshBox
import snd.komelia.ui.readlist.ReadListScreen
import snd.komelia.ui.book.bookScreen
import snd.komelia.ui.reader.readerScreen
import snd.komelia.ui.common.cards.BookImageCard
import snd.komelia.ui.common.menus.BookMenuActions
import snd.komelia.ui.series.activeFilters
import snd.komelia.ui.series.list.SeriesListContent
import snd.komelia.ui.series.seriesScreen
import snd.komelia.komga.api.model.KomeliaBook
import snd.komga.client.common.KomgaAuthor
import snd.komga.client.library.KomgaLibrary
import snd.komga.client.library.KomgaLibraryId
import snd.komga.client.series.KomgaSeriesStatus
import kotlin.jvm.Transient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import snd.komelia.ui.pushUnique

class LibraryScreen(
    val libraryId: KomgaLibraryId? = null,
    @Transient
    private val seriesFilter: SeriesScreenFilter? = null
) : ReloadableScreen {

    override val key: ScreenKey = "${libraryId}_${seriesFilter.hashCode()}"

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModelFactory = LocalViewModelFactory.current
        val vm = rememberScreenModel(libraryId?.value) { viewModelFactory.getLibraryViewModel(libraryId) }
        val reloadEvents = LocalReloadEvents.current

        LaunchedEffect(libraryId) {
            vm.initialize(seriesFilter)
            reloadEvents.collect { vm.reload() }
        }
        DisposableEffect(Unit) {
            vm.startKomgaEventHandler()
            onDispose { vm.stopKomgaEventHandler() }
        }

        ScreenPullToRefreshBox(screenState = vm.state, onRefresh = vm::reload) {
            when (val state = vm.state.collectAsState().value) {
                is Error -> ErrorContent(message = state.exception.message ?: "Unknown Error", onReload = vm::reload)
                Uninitialized, Loading, is Success -> {
                    val useNewUI2 = LocalUseNewLibraryUI2.current
                    val theme = LocalTheme.current
                    val showToolbar = vm.showToolbar.collectAsState().value
                    val floatToolbar = theme.transparentBars && showToolbar
                    val library = vm.library.collectAsState().value

                    val (totalCountInfo, onPageSizeChange) = when (vm.currentTab) {
                        SERIES -> {
                            val state = vm.seriesTabState
                            Triple(
                                state.totalSeriesCount,
                                "series",
                                state.pageLoadSize.collectAsState().value
                            ) to state::onPageSizeChange
                        }

                        COLLECTIONS -> {
                            val state = vm.collectionsTabState
                            Triple(
                                state.totalCollections,
                                if (state.totalCollections > 1) "collections" else "collection",
                                state.pageSize
                            ) to state::onPageSizeChange
                        }

                        READ_LISTS -> {
                            val state = vm.readListsTabState
                            Triple(
                                state.totalReadLists,
                                if (state.totalReadLists > 1) "read lists" else "read list",
                                state.pageSize
                            ) to state::onPageSizeChange
                        }

                        GENRE -> {
                            Triple(
                                vm.genresCount,
                                if (vm.genresCount > 1) "genres" else "genre",
                                50
                            ) to { _: Int -> }
                        }

                        FOR_YOU -> {
                            val count = vm.forYouTabState.suggestions.size
                            Triple(
                                count,
                                if (count > 1) "suggestions" else "suggestion",
                                50
                            ) to { _: Int -> }
                        }
                    }
                    val (totalCount, countLabel, pageSize) = totalCountInfo

                    val toolbarContent: @Composable () -> Unit = {
                        LibraryToolBar(
                            library = library,
                            libraryActions = vm.libraryActions(),
                            totalCount = totalCount,
                            countLabel = countLabel,
                            pageSize = pageSize,
                            onPageSizeChange = onPageSizeChange,
                            sortOrder = if (vm.currentTab == SERIES) vm.seriesTabState.filterState.state.collectAsState().value.sortOrder else null,
                            onSortChange = if (vm.currentTab == SERIES) vm.seriesTabState.filterState::onSortOrderChange else null
                        )
                    }

                    val segmentedButtons = @Composable {
                        LibrarySegmentedButtons(
                            currentTab = vm.currentTab,
                            collectionsCount = vm.collectionsCount,
                            readListsCount = vm.readListsCount,
                            genresCount = vm.genresCount,
                            onBrowseClick = vm::toBrowseTab,
                            onCollectionsClick = vm::toCollectionsTab,
                            onReadListsClick = vm::toReadListsTab,
                            onGenreClick = vm::toGenreTab,
                            onForYouClick = vm::toForYouTab
                        )
                    }

                    // "Continue reading" ignores the filter — it is the library's
                    // in-progress books, whole. On a screen opened from an author
                    // chip that reads as a contradiction: the grid holds the one
                    // series by that author, and right above it sits a shelf of
                    // unrelated books. Hiding the chip as well as the row, so the
                    // toggle does not offer to bring the contradiction back.
                    val showContinueReading =
                        seriesFilter == null && vm.showContinueReading.collectAsState().value
                    val newUI2BeforeContent = @Composable {
                        val gridPadding = if (useNewUI2) 10.dp else 20.dp
                        val density = LocalDensity.current
                        Surface(
                            modifier = Modifier
                                .layout { measurable, constraints ->
                                    val insetPx = with(density) { gridPadding.roundToPx() }
                                    val placeable = measurable.measure(
                                        constraints.copy(maxWidth = constraints.maxWidth + insetPx * 2)
                                    )
                                    layout(constraints.maxWidth, placeable.height) {
                                        placeable.place(-insetPx, 0)
                                    }
                                }
                                .fillMaxWidth(),
                            shape = RectangleShape,
                            color = MaterialTheme.colorScheme.background,
                        ) {
                            Column {
                                LibraryHeaderSection(
                                    library = library,
                                    totalCount = totalCount,
                                    countLabel = countLabel,
                                    pageSize = pageSize,
                                    onPageSizeChange = onPageSizeChange,
                                    sortOrder = if (vm.currentTab == SERIES) vm.seriesTabState.filterState.state.collectAsState().value.sortOrder else null,
                                    onSortChange = if (vm.currentTab == SERIES) vm.seriesTabState.filterState::onSortOrderChange else null,
                                    modifier = Modifier.padding(horizontal = gridPadding)
                                )
                                LibraryTabChips(
                                    currentTab = vm.currentTab,
                                    collectionsCount = vm.collectionsCount,
                                    readListsCount = vm.readListsCount,
                                    genresCount = vm.genresCount,
                                    showContinueReading = showContinueReading,
                                    showReadingChip = seriesFilter == null,
                                    onReadingClick = vm::toggleContinueReading,
                                    onBrowseClick = vm::toBrowseTab,
                                    onCollectionsClick = vm::toCollectionsTab,
                                    onReadListsClick = vm::toReadListsTab,
                                    onGenreClick = vm::toGenreTab,
                                    onForYouClick = vm::toForYouTab,
                                    randomSeriesEnabled = vm.seriesTabState.totalSeriesCount > 0,
                                    onRandomSeriesClick = {
                                        vm.seriesTabState.openRandomSeries { navigator.pushUnique(seriesScreen(it)) }
                                    },
                                    contentPadding = PaddingValues(horizontal = gridPadding)
                                )
                                if (showContinueReading) {
                                    ContinueReadingSection(
                                        books = vm.keepReadingBooks,
                                        bookMenuActions = vm.seriesTabState.bookMenuActions(),
                                        onBookClick = { navigator.pushUnique(bookScreen(it)) },
                                        onBookReadClick = { book, mark ->
                                            navigator.pushUnique(
                                                readerScreen(
                                                    book = book,
                                                    markReadProgress = mark,
                                                    onExit = { lastReadBook ->
                                                        // Progress changed whether or not the reader
                                                        // moved on to another volume, so the shelf has
                                                        // to be re-read either way — guarding on "a
                                                        // different book" is what left it stale until
                                                        // a manual refresh. A different book means the
                                                        // rest of the screen moved too, hence the full
                                                        // reload there.
                                                        if (lastReadBook.id != book.id) vm.reload()
                                                        else vm.refreshKeepReading()
                                                    }
                                                )
                                            )
                                        },
                                        gridPadding = gridPadding,
                                        cardWidth = vm.cardWidth.collectAsState().value
                                    )
                                }
                                Spacer(Modifier.height(10.dp))
                            }
                        }
                    }

                    val tabContent: @Composable () -> Unit = {
                        val beforeContent = if (useNewUI2) newUI2BeforeContent else segmentedButtons
                        when (vm.currentTab) {
                            SERIES -> BrowseTab(vm.seriesTabState, beforeContent)
                            COLLECTIONS -> CollectionsTab(vm.collectionsTabState, beforeContent)
                            READ_LISTS -> ReadListsTab(vm.readListsTabState, beforeContent)
                            GENRE -> GenreTab(vm.genreTabState, beforeContent)
                            FOR_YOU -> ForYouContent(
                                state = vm.forYouTabState,
                                onSeriesClick = { navigator.pushUnique(seriesScreen(it)) },
                                beforeContent = beforeContent,
                            )
                        }
                    }

                    if (useNewUI2) {
                        val barHeight = 45.dp
                        val statusBarHeight = if (theme.transparentBars) LocalRawStatusBarHeight.current else 0.dp
                        val screenHazeState = if (theme.transparentBars) rememberHazeState() else null
                        val containerColor = if (theme.type == Theme.ThemeType.DARK) Color(43, 43, 43)
                        else MaterialTheme.colorScheme.surfaceVariant
                        CompositionLocalProvider(
                            LocalFloatingToolbarPadding provides barHeight + statusBarHeight,
                            LocalHazeState provides screenHazeState,
                        ) {
                            // Wire the Continue-reading FAB into the
                            // MainScreen far-right slot so it sits
                            // immediately to the right of the filter
                            // button (which is in the regular right slot)
                            // and on the same horizontal line as the
                            // floating nav island. Same slot mechanism as
                            // HomeScreen — keeps the bottom row layout
                            // consistent across screens.
                            val fabFarRight = snd.komelia.ui.LocalFloatingActionButtonFarRight.current
                            DisposableEffect(libraryId) {
                                fabFarRight.value = this@LibraryScreen to {
                                    ContinueReadingFab(
                                        bookApi = vm.bookApi,
                                        libraryId = libraryId,
                                        onOpenBook = { book ->
                                            navigator.parent?.pushUnique(
                                                readerScreen(
                                                    book = book,
                                                    markReadProgress = true,
                                                    onExit = { },
                                                )
                                            )
                                        },
                                    )
                                }
                                onDispose {
                                    if (fabFarRight.value?.first == this@LibraryScreen) {
                                        fabFarRight.value = null
                                    }
                                }
                            }
                            Box(Modifier.fillMaxSize()) {
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .background(containerColor)
                                        .then(if (screenHazeState != null) Modifier.hazeSource(screenHazeState) else Modifier)
                                ) {
                                    tabContent()
                                }
                                NewTopAppBar(library = library, libraryActions = vm.libraryActions())
                            }
                        }
                    } else if (floatToolbar) {
                        val toolbarHazeState = if (theme.transparentBars) rememberHazeState() else null
                        CompositionLocalProvider(
                            LocalHazeState provides toolbarHazeState,
                            LocalFloatingToolbarPadding provides 64.dp,
                        ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Box(
                                Modifier.fillMaxSize()
                                    .then(if (toolbarHazeState != null) Modifier.hazeSource(toolbarHazeState) else Modifier)
                            ) {
                                tabContent()
                            }
                            toolbarContent()
                        }
                        }
                    } else {
                        Column {
                            if (showToolbar) toolbarContent()
                            tabContent()
                        }
                    }
                }
            }
            BackPressHandler { navigator.pop() }
        }
    }

    @Composable
    private fun BrowseTab(seriesTabState: LibrarySeriesTabState, beforeContent: @Composable () -> Unit) {
        val navigator = LocalNavigator.currentOrThrow
        LaunchedEffect(libraryId) { seriesTabState.initialize(seriesFilter) }
        DisposableEffect(Unit) {
            seriesTabState.startKomgaEventHandler()
            onDispose { seriesTabState.stopKomgaEventHandler() }
        }

        val currentFilter = seriesTabState.filterState.state.collectAsState().value
        val currentLetter = currentFilter.letterFilter
        val activeFilters = currentFilter.activeFilters()
        val combinedBeforeContent: @Composable () -> Unit = {
            Column {
                beforeContent()
                // Above the letters rather than below: these say what the grid
                // already is, the letters offer to narrow it further.
                ActiveFilterChipsRow(
                    filters = activeFilters,
                    onRemove = seriesTabState.filterState::remove,
                    onClearAll = seriesTabState.filterState::reset,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                )
                LetterFilterBar(
                    selected = currentLetter,
                    onLetterClick = seriesTabState.filterState::onLetterFilterChange,
                )
                // The previous results stay on screen while the new ones load,
                // which is the right call — but a query that takes ten seconds
                // then looks like a tap that did nothing. Two pixels of movement
                // under the letters answer that, without hiding the list.
                if (seriesTabState.isRefreshing) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp))
                } else {
                    Spacer(Modifier.height(2.dp))
                }
            }
        }

        when (val state = seriesTabState.state.collectAsState().value) {
            is Error -> ErrorContent(
                message = state.exception.message ?: "Unknown Error",
                onReload = seriesTabState::reload
            )

            else -> {
                SeriesListContent(
                    series = seriesTabState.series,
                    downloadedSeriesIds = seriesTabState.downloadedSeriesIds,
                    seriesActions = seriesTabState.seriesMenuActions(),
                    seriesTotalCount = seriesTabState.totalSeriesCount,
                    onSeriesClick = {
                        seriesTabState.registerSeriesListContext(it)
                        navigator.pushUnique(seriesScreen(it))
                    },

                    editMode = seriesTabState.isInEditMode.collectAsState().value,
                    onEditModeChange = seriesTabState::onEditModeChange,
                    selectedSeries = seriesTabState.selectedSeries,
                    onSeriesSelect = seriesTabState::onSeriesSelect,

                    filterState = seriesTabState.filterState,
                    chapterSeriesFilter = seriesTabState.chapterSeriesFilter,
                    onChapterSeriesFilterCycle = seriesTabState::onChapterSeriesFilterCycle,

                    currentPage = seriesTabState.currentSeriesPage,
                    totalPages = seriesTabState.totalSeriesPages,
                    pageSize = seriesTabState.pageLoadSize.collectAsState().value,
                    onPageSizeChange = seriesTabState::onPageSizeChange,
                    onPageChange = seriesTabState::onPageChange,

                    minSize = seriesTabState.cardWidth.collectAsState().value,
                    beforeContent = combinedBeforeContent
                )
            }
        }
    }

    @Composable
    private fun CollectionsTab(collectionsTabState: LibraryCollectionsTabState, beforeContent: @Composable () -> Unit) {
        val navigator = LocalNavigator.currentOrThrow
        LaunchedEffect(libraryId) { collectionsTabState.initialize() }
        DisposableEffect(Unit) {
            collectionsTabState.startKomgaEventHandler()
            onDispose { collectionsTabState.stopKomgaEventHandler() }
        }

        val searchQuery = collectionsTabState.searchQuery.collectAsState().value
        val sortOrder = collectionsTabState.sortOrder.collectAsState().value
        val combinedBeforeContent: @Composable () -> Unit = {
            Column {
                beforeContent()
                CollectionReadListSearchSortToolbar(
                    searchQuery = searchQuery,
                    onSearchQueryChange = collectionsTabState::onSearchQueryChange,
                    sortOrder = sortOrder,
                    onSortOrderChange = collectionsTabState::onSortOrderChange,
                    placeholder = LocalStrings.current.ui.searchCollections,
                )
            }
        }

        when (val state = collectionsTabState.state.collectAsState().value) {
            Uninitialized -> LoadingMaxSizeIndicator()
            is Error -> ErrorContent(
                message = state.exception.message ?: "Unknown Error",
                onReload = collectionsTabState::reload
            )

            else -> {
                val loading = state is Loading
                LibraryCollectionsContent(
                    collections = collectionsTabState.collections,
                    collectionsTotalCount = collectionsTabState.totalCollections,
                    onCollectionClick = { navigator push CollectionScreen(it) },
                    onCollectionDelete = collectionsTabState::onCollectionDelete,
                    isLoading = loading,

                    totalPages = collectionsTabState.totalPages,
                    currentPage = collectionsTabState.currentPage,
                    pageSize = collectionsTabState.pageSize,
                    onPageChange = collectionsTabState::onPageChange,
                    onPageSizeChange = collectionsTabState::onPageSizeChange,

                    minSize = collectionsTabState.cardWidth.collectAsState().value,
                    beforeContent = combinedBeforeContent,
                    progressOf = { collectionsTabState.progressByCollection[it] },
                    onProgressNeeded = collectionsTabState::loadProgressFor,
                )

            }
        }

    }

    @Composable
    private fun ReadListsTab(readListTabState: LibraryReadListsTabState, beforeContent: @Composable () -> Unit) {
        val navigator = LocalNavigator.currentOrThrow
        LaunchedEffect(libraryId) { readListTabState.initialize() }
        DisposableEffect(Unit) {
            readListTabState.startKomgaEventHandler()
            onDispose { readListTabState.stopKomgaEventHandler() }
        }

        val searchQuery = readListTabState.searchQuery.collectAsState().value
        val sortOrder = readListTabState.sortOrder.collectAsState().value
        val combinedBeforeContent: @Composable () -> Unit = {
            Column {
                beforeContent()
                CollectionReadListSearchSortToolbar(
                    searchQuery = searchQuery,
                    onSearchQueryChange = readListTabState::onSearchQueryChange,
                    sortOrder = sortOrder,
                    onSortOrderChange = readListTabState::onSortOrderChange,
                    placeholder = LocalStrings.current.ui.searchReadlists,
                )
            }
        }

        when (val state = readListTabState.state.collectAsState().value) {
            Uninitialized -> LoadingMaxSizeIndicator()
            is Error -> Text(LocalStrings.current.ui.error)
            else -> {
                val loading = state is Loading
                LibraryReadListsContent(
                    readLists = readListTabState.readLists,
                    readListsTotalCount = readListTabState.totalReadLists,
                    onReadListClick = { navigator push ReadListScreen(it) },
                    onReadListDelete = readListTabState::onReadListDelete,
                    isLoading = loading,

                    totalPages = readListTabState.totalPages,
                    currentPage = readListTabState.currentPage,
                    pageSize = readListTabState.pageSize,
                    onPageChange = readListTabState::onPageChange,
                    onPageSizeChange = readListTabState::onPageSizeChange,

                    minSize = readListTabState.cardWidth.collectAsState().value,
                    beforeContent = combinedBeforeContent,
                    progressOf = { readListTabState.progressByReadList[it] },
                    onProgressNeeded = readListTabState::loadProgressFor,
                )
            }
        }
    }

    @Composable
    private fun GenreTab(genreTabState: LibraryGenreTabState, beforeContent: @Composable () -> Unit) {
        val navigator = LocalNavigator.currentOrThrow
        LaunchedEffect(libraryId) { genreTabState.initialize() }

        var coverPickFor by remember { mutableStateOf<GenreTile?>(null) }
        var renameFor by remember { mutableStateOf<GenreTile?>(null) }

        when (val state = genreTabState.state.collectAsState().value) {
            is Error -> ErrorContent(
                message = state.exception.message ?: "Unknown Error",
                onReload = genreTabState::reload,
            )

            else -> {
                if (genreTabState.genres.isEmpty() && state !is Success) {
                    LoadingMaxSizeIndicator()
                } else {
                    val appearance = genreTabState.tileAppearance.collectAsState().value
                    GenreGridContent(
                        genres = genreTabState.genres,
                        minSize = appearance.minSize,
                        overriddenSlugs = genreTabState.overriddenSlugs,
                        onGenreClick = { tile ->
                            navigator.pushUnique(GenreSeriesScreen(libraryId, tile.tag, tile.label))
                        },
                        onChooseCover = { coverPickFor = it },
                        onRename = { renameFor = it },
                        onResetOverride = { genreTabState.resetOverride(it.slug) },
                        textBelow = appearance.textBelow,
                        showCount = appearance.showCount,
                        beforeContent = beforeContent,
                        onImportCovers = genreTabState::importCovers,
                    )
                }
            }
        }

        coverPickFor?.let { tile ->
            GenreCoverPickerDialog(
                genreLabel = tile.label,
                loadGenreSeries = { genreTabState.seriesForGenre(tile.tag) },
                searchSeries = { genreTabState.searchSeriesInLibrary(it) },
                onPick = { seriesId ->
                    genreTabState.setCover(tile.slug, seriesId)
                    coverPickFor = null
                },
                onPickLocal = { bytes ->
                    genreTabState.setLocalCover(tile.slug, bytes)
                    coverPickFor = null
                },
                onDismiss = { coverPickFor = null },
            )
        }
        renameFor?.let { tile ->
            GenreRenameDialog(
                current = tile.label,
                onConfirm = { newLabel ->
                    genreTabState.setLabel(tile.slug, newLabel)
                    renameFor = null
                },
                onDismiss = { renameFor = null },
            )
        }
    }

}

@Composable
private fun ContinueReadingSection(
    books: List<KomeliaBook>,
    bookMenuActions: BookMenuActions,
    onBookClick: (KomeliaBook) -> Unit,
    onBookReadClick: (KomeliaBook, Boolean) -> Unit,
    gridPadding: Dp,
    cardWidth: Dp,
) {
    if (books.isEmpty()) return

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
    ) {
        Text(
            LocalStrings.current.ui.continueReading,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(start = gridPadding, end = gridPadding, bottom = 12.dp)
        )
        val rowState = rememberLazyListState()
        // The row now starts from a remembered list, so it keeps its scroll
        // offset across a refresh. A book just read moves to the head — and
        // stayed off-screen to the left, which is the one place the user was
        // going to look. Snap back when the head actually changes; scrolling
        // done by hand is left alone.
        val headId = books.firstOrNull()?.id?.value
        LaunchedEffect(headId) {
            if (headId != null && rowState.firstVisibleItemIndex > 0) rowState.scrollToItem(0)
        }
        LazyRow(
            state = rowState,
            contentPadding = PaddingValues(horizontal = gridPadding),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            items(books, key = { it.id.value }) { book ->
                BookImageCard(
                    book = book,
                    onBookReadClick = { onBookReadClick(book, it) },
                    onBookClick = { onBookClick(book) },
                    bookMenuActions = bookMenuActions,
                    showSeriesTitle = true,
                    modifier = Modifier.width(cardWidth),
                )
            }
        }
    }
}

@Composable
private fun LibraryHeaderSection(
    library: KomgaLibrary?,
    totalCount: Int,
    countLabel: String,
    pageSize: Int,
    onPageSizeChange: (Int) -> Unit,
    sortOrder: LibrarySeriesTabState.SeriesSort? = null,
    onSortChange: ((LibrarySeriesTabState.SeriesSort) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val notoSerif = FontFamily(Font(Res.font.NotoSerif_Bold, FontWeight.Bold))
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val mainScreenVm = snd.komelia.ui.LocalMainScreenViewModel.current
            val libraries = mainScreenVm.libraries.collectAsState().value
            val showDropdown = mainScreenVm.libraryDropdownInTitle.collectAsState().value
            val titleStyle = MaterialTheme.typography.headlineLarge.copy(
                fontFamily = notoSerif,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp,
            )
            val titleLabel = library?.name ?: "All Libraries"
            if (showDropdown) {
                snd.komelia.ui.common.components.LibraryTitleSelector(
                    label = titleLabel,
                    titleStyle = titleStyle,
                    libraries = libraries,
                    currentLibraryId = library?.id,
                    onPickHome = { mainScreenVm.navigateToHome() },
                    onPickLibrary = { libId -> mainScreenVm.navigateToLibrary(libId) },
                    modifier = Modifier.weight(1f),
                )
            } else {
                Text(
                    titleLabel,
                    style = titleStyle,
                    modifier = Modifier.weight(1f),
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (sortOrder != null && onSortChange != null) {
                    LibrarySortDropdown(sortOrder, onSortChange)
                    Spacer(Modifier.width(8.dp))
                }
                PageSizeSelectionDropdown(currentSize = pageSize, onPageSizeChange = onPageSizeChange)
            }
        }
        if (totalCount > 0) {
            Text(
                "$totalCount ${countLabel.uppercase()}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp,
            )
        }
    }
}

@Composable
private fun LibraryTabChips(
    currentTab: LibraryTab,
    collectionsCount: Int,
    readListsCount: Int,
    genresCount: Int = 0,
    showContinueReading: Boolean,
    // False on a chip-scoped library, where the shelf this toggles is not
    // filtered and would contradict the grid below it.
    showReadingChip: Boolean = true,
    onReadingClick: () -> Unit,
    onBrowseClick: () -> Unit,
    onCollectionsClick: () -> Unit,
    onReadListsClick: () -> Unit,
    onGenreClick: () -> Unit = {},
    onForYouClick: () -> Unit = {},
    randomSeriesEnabled: Boolean = false,
    onRandomSeriesClick: () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val chipColors = AppFilterChipDefaults.filterChipColors()
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // "For you" exists in every library, so the Series chip is now always
        // shown too — otherwise a library without collections would offer no way
        // back from the suggestions.
        run {
            item {
                FilterChip(
                    selected = currentTab == SERIES,
                    onClick = onBrowseClick,
                    label = { Text(LocalStrings.current.ui.series) },
                    colors = chipColors,
                    shape = AppFilterChipDefaults.shape(),
                    border = AppFilterChipDefaults.filterChipBorder(currentTab == SERIES),
                )
            }
            if (collectionsCount > 0) {
                item {
                    FilterChip(
                        selected = currentTab == COLLECTIONS,
                        onClick = onCollectionsClick,
                        label = { Text(LocalStrings.current.ui.collections) },
                        colors = chipColors,
                        shape = AppFilterChipDefaults.shape(),
                        border = AppFilterChipDefaults.filterChipBorder(currentTab == COLLECTIONS),
                    )
                }
            }
            if (readListsCount > 0) {
                item {
                    FilterChip(
                        selected = currentTab == READ_LISTS,
                        onClick = onReadListsClick,
                        label = { Text(LocalStrings.current.ui.readLists) },
                        colors = chipColors,
                        shape = AppFilterChipDefaults.shape(),
                        border = AppFilterChipDefaults.filterChipBorder(currentTab == READ_LISTS),
                    )
                }
            }
            if (genresCount > 0) {
                item {
                    FilterChip(
                        selected = currentTab == GENRE,
                        onClick = onGenreClick,
                        label = { Text(LocalStrings.current.ui.genres) },
                        colors = chipColors,
                        shape = AppFilterChipDefaults.shape(),
                        border = AppFilterChipDefaults.filterChipBorder(currentTab == GENRE),
                    )
                }
            }
            item {
                FilterChip(
                    selected = currentTab == FOR_YOU,
                    onClick = onForYouClick,
                    label = { Text(LocalStrings.current.ui.forYou) },
                    colors = chipColors,
                    shape = AppFilterChipDefaults.shape(),
                    border = AppFilterChipDefaults.filterChipBorder(currentTab == FOR_YOU),
                )
            }
        }

        if (showReadingChip) {
            item {
                FilterChip(
                    selected = showContinueReading,
                    onClick = onReadingClick,
                    label = { Text(LocalStrings.current.ui.reading) },
                    colors = chipColors,
                    shape = AppFilterChipDefaults.shape(),
                    border = AppFilterChipDefaults.filterChipBorder(showContinueReading),
                )
            }
        }

        if (currentTab == SERIES) {
            item {
                FilledTonalIconButton(
                    onClick = onRandomSeriesClick,
                    enabled = randomSeriesEnabled,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier.border(
                        BorderStroke(1.5.dp, Color.Black),
                        CircleShape
                    )
                ) {
                    Icon(
                        Icons.Rounded.Casino,
                        contentDescription = LocalStrings.current.seriesFilter.sortRandom
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryToolBar(
    library: KomgaLibrary?,
    libraryActions: LibraryMenuActions,
    totalCount: Int,
    countLabel: String,
    pageSize: Int,
    onPageSizeChange: (Int) -> Unit,
    sortOrder: LibrarySeriesTabState.SeriesSort? = null,
    onSortChange: ((LibrarySeriesTabState.SeriesSort) -> Unit)? = null,
) {
    var showOptionsMenu by remember { mutableStateOf(false) }
    val isAdmin = LocalKomgaState.current.authenticatedUser.collectAsState().value?.roleAdmin() ?: true
    val isOffline = LocalOfflineMode.current.collectAsState().value
    val mainScreenVm = LocalMainScreenViewModel.current
    val coroutineScope = rememberCoroutineScope()

    val theme = LocalTheme.current
    val hazeState = LocalHazeState.current
    val hazeStyle = if (theme.transparentBars && hazeState != null) HazeMaterials.thin(theme.topBarContainerColor) else null
    Box(modifier = Modifier.fillMaxWidth()) {
        TopAppBar(
            windowInsets = WindowInsets(0),
            colors = if (theme.transparentBars && hazeState != null)
                TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            else if (theme.transparentBars)
                TopAppBarDefaults.topAppBarColors(containerColor = theme.topBarContainerColor)
            else
                TopAppBarDefaults.topAppBarColors(),
            modifier = if (hazeState != null && hazeStyle != null)
                Modifier.hazeEffect(hazeState) { style = hazeStyle }
            else Modifier,
            title = {
                Text(
                    library?.let { library.name } ?: "All Libraries",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            navigationIcon = {
                IconButton(onClick = { coroutineScope.launch { mainScreenVm.toggleNavBar() } }) {
                    Icon(Icons.Rounded.Menu, contentDescription = null)
                }
            },
            actions = {
                if (totalCount != 0) {
                    SuggestionChip(
                        onClick = {},
                        label = { Text("$totalCount $countLabel") },
                        shape = AppSuggestionChipDefaults.shape(),
                        modifier = Modifier.padding(end = 5.dp)
                    )

                    if (sortOrder != null && onSortChange != null) {
                        LibrarySortDropdown(sortOrder, onSortChange)
                    }
                    PageSizeSelectionDropdown(pageSize, onPageSizeChange)
                }

                if (library != null && (isAdmin || isOffline)) {
                    Box {
                        IconButton(
                            onClick = { showOptionsMenu = true }
                        ) {
                            Icon(
                                Icons.Rounded.MoreVert,
                                contentDescription = null,
                            )
                        }

                        LibraryActionsMenu(
                            library = library,
                            actions = libraryActions,
                            expanded = showOptionsMenu,
                            onDismissRequest = { showOptionsMenu = false }
                        )
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibrarySegmentedButtons(
    currentTab: LibraryTab,
    collectionsCount: Int,
    readListsCount: Int,
    genresCount: Int = 0,
    onBrowseClick: () -> Unit,
    onCollectionsClick: () -> Unit,
    onReadListsClick: () -> Unit,
    onGenreClick: () -> Unit = {},
    onForYouClick: () -> Unit = {},
) {
    // Always rendered now: every library has a "For you" tab, so the row is
    // never empty and Series always has a way back.
    run {
        val tabCount = getTabCount(collectionsCount, readListsCount, genresCount)
        val accentColor = LocalAccentColor.current
        val colors = if (accentColor != null) {
            SegmentedButtonDefaults.colors(
                activeContainerColor = accentColor,
                activeContentColor = if (accentColor.luminance() > 0.5f) Color.Black else Color.White
            )
        } else {
            SegmentedButtonDefaults.colors()
        }

        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            SegmentedButton(
                selected = currentTab == SERIES,
                onClick = onBrowseClick,
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = tabCount),
                label = { Text(LocalStrings.current.ui.series) },
                colors = colors
            )
            var index = 1
            if (collectionsCount > 0) {
                SegmentedButton(
                    selected = currentTab == COLLECTIONS,
                    onClick = onCollectionsClick,
                    shape = SegmentedButtonDefaults.itemShape(index = index++, count = tabCount),
                    label = { Text(LocalStrings.current.ui.collections) },
                    colors = colors
                )
            }
            if (readListsCount > 0) {
                SegmentedButton(
                    selected = currentTab == READ_LISTS,
                    onClick = onReadListsClick,
                    shape = SegmentedButtonDefaults.itemShape(index = index++, count = tabCount),
                    label = { Text(LocalStrings.current.ui.readLists) },
                    colors = colors
                )
            }
            if (genresCount > 0) {
                SegmentedButton(
                    selected = currentTab == GENRE,
                    onClick = onGenreClick,
                    shape = SegmentedButtonDefaults.itemShape(index = index++, count = tabCount),
                    label = { Text(LocalStrings.current.ui.genres) },
                    colors = colors
                )
            }
            SegmentedButton(
                selected = currentTab == FOR_YOU,
                onClick = onForYouClick,
                shape = SegmentedButtonDefaults.itemShape(index = index, count = tabCount),
                label = { Text(LocalStrings.current.ui.forYou) },
                colors = colors
            )
        }
    }
}

private fun getTabCount(collectionsCount: Int, readListsCount: Int, genresCount: Int = 0): Int {
    // Series + For you always exist; the rest depend on the library's content.
    var count = 2
    if (collectionsCount > 0) count++
    if (readListsCount > 0) count++
    if (genresCount > 0) count++
    return count
}

@Composable
private fun LibrarySortDropdown(
    currentSort: LibrarySeriesTabState.SeriesSort,
    onSortChange: (LibrarySeriesTabState.SeriesSort) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                Icons.Default.FilterList,
                contentDescription = null
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            LibrarySeriesTabState.SeriesSort.entries.forEach { sort ->
                DropdownMenuItem(
                    text = { Text(LocalStrings.current.seriesFilter.forSeriesSort(sort)) },
                    onClick = {
                        onSortChange(sort)
                        expanded = false
                    },
                    modifier = if (sort == currentSort) Modifier.background(MaterialTheme.colorScheme.secondaryContainer) else Modifier,
                    colors = if (sort == currentSort) {
                        MenuDefaults.itemColors(
                            textColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    } else MenuDefaults.itemColors()
                )
            }
        }
    }
}

data class SeriesScreenFilter(
    val publicationStatus: List<KomgaSeriesStatus>? = null,
    val ageRating: List<Int>? = null,
    val language: List<String>? = null,
    val publisher: List<String>? = null,
    val genres: List<String>? = null,
    val tags: List<String>? = null,
    val authors: List<KomgaAuthor>? = null,
)
