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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MoreVert
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
import snd.komelia.ui.common.components.AppFilterChipDefaults
import snd.komelia.ui.common.components.AppSuggestionChipDefaults
import snd.komelia.ui.common.components.ErrorContent
import snd.komelia.ui.common.components.LoadingMaxSizeIndicator
import snd.komelia.ui.common.components.PageSizeSelectionDropdown
import snd.komelia.ui.common.menus.LibraryActionsMenu
import snd.komelia.ui.common.menus.LibraryMenuActions
import snd.komelia.ui.topbar.NewTopAppBar
import snd.komelia.ui.library.LibraryTab.COLLECTIONS
import snd.komelia.ui.library.LibraryTab.READ_LISTS
import snd.komelia.ui.library.LibraryTab.SERIES
import snd.komelia.ui.library.view.LibraryCollectionsContent
import snd.komelia.ui.library.view.LibraryReadListsContent
import snd.komelia.ui.platform.BackPressHandler
import snd.komelia.ui.platform.ScreenPullToRefreshBox
import snd.komelia.ui.readlist.ReadListScreen
import snd.komelia.ui.book.bookScreen
import snd.komelia.ui.reader.readerScreen
import snd.komelia.ui.common.cards.BookImageCard
import snd.komelia.ui.common.menus.BookMenuActions
import snd.komelia.ui.series.list.SeriesListContent
import snd.komelia.ui.series.seriesScreen
import snd.komelia.komga.api.model.KomeliaBook
import snd.komga.client.common.KomgaAuthor
import snd.komga.client.library.KomgaLibrary
import snd.komga.client.library.KomgaLibraryId
import snd.komga.client.series.KomgaSeriesStatus
import kotlin.jvm.Transient

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
                            onBrowseClick = vm::toBrowseTab,
                            onCollectionsClick = vm::toCollectionsTab,
                            onReadListsClick = vm::toReadListsTab
                        )
                    }

                    val showContinueReading = vm.showContinueReading.collectAsState().value
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
                                    showContinueReading = showContinueReading,
                                    onReadingClick = vm::toggleContinueReading,
                                    onBrowseClick = vm::toBrowseTab,
                                    onCollectionsClick = vm::toCollectionsTab,
                                    onReadListsClick = vm::toReadListsTab,
                                    randomSeriesEnabled = vm.seriesTabState.totalSeriesCount > 0,
                                    onRandomSeriesClick = {
                                        vm.seriesTabState.openRandomSeries { navigator.push(seriesScreen(it)) }
                                    },
                                    contentPadding = PaddingValues(horizontal = gridPadding)
                                )
                                if (showContinueReading) {
                                    ContinueReadingSection(
                                        books = vm.keepReadingBooks,
                                        bookMenuActions = vm.seriesTabState.bookMenuActions(),
                                        onBookClick = { navigator.push(bookScreen(it)) },
                                        onBookReadClick = { book, mark ->
                                            navigator.push(
                                                readerScreen(
                                                    book = book,
                                                    markReadProgress = mark,
                                                    onExit = { lastReadBook ->
                                                        if (lastReadBook.id != book.id) {
                                                            vm.reload()
                                                        }
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
                    onSeriesClick = { navigator.push(seriesScreen(it)) },

                    editMode = seriesTabState.isInEditMode.collectAsState().value,
                    onEditModeChange = seriesTabState::onEditModeChange,
                    selectedSeries = seriesTabState.selectedSeries,
                    onSeriesSelect = seriesTabState::onSeriesSelect,

                    filterState = seriesTabState.filterState,

                    currentPage = seriesTabState.currentSeriesPage,
                    totalPages = seriesTabState.totalSeriesPages,
                    pageSize = seriesTabState.pageLoadSize.collectAsState().value,
                    onPageSizeChange = seriesTabState::onPageSizeChange,
                    onPageChange = seriesTabState::onPageChange,

                    minSize = seriesTabState.cardWidth.collectAsState().value,
                    beforeContent = beforeContent
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
                    beforeContent = beforeContent
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

        when (val state = readListTabState.state.collectAsState().value) {
            Uninitialized -> LoadingMaxSizeIndicator()
            is Error -> Text("Error")
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
                    beforeContent = beforeContent
                )
            }
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
            "Continue Reading",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(start = gridPadding, end = gridPadding, bottom = 12.dp)
        )
        LazyRow(
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
            Text(
                library?.name ?: "All Libraries",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontFamily = notoSerif,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp,
                ),
                modifier = Modifier.weight(1f)
            )

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
    showContinueReading: Boolean,
    onReadingClick: () -> Unit,
    onBrowseClick: () -> Unit,
    onCollectionsClick: () -> Unit,
    onReadListsClick: () -> Unit,
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
        if (collectionsCount > 0 || readListsCount > 0) {
            item {
                FilterChip(
                    selected = currentTab == SERIES,
                    onClick = onBrowseClick,
                    label = { Text("Series") },
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
                        label = { Text("Collections") },
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
                        label = { Text("Read Lists") },
                        colors = chipColors,
                        shape = AppFilterChipDefaults.shape(),
                        border = AppFilterChipDefaults.filterChipBorder(currentTab == READ_LISTS),
                    )
                }
            }
        }

        item {
            FilterChip(
                selected = showContinueReading,
                onClick = onReadingClick,
                label = { Text("Reading") },
                colors = chipColors,
                shape = AppFilterChipDefaults.shape(),
                border = AppFilterChipDefaults.filterChipBorder(showContinueReading),
            )
        }

        if (currentTab == SERIES) {
            item {
                FilterChip(
                    selected = false,
                    enabled = randomSeriesEnabled,
                    onClick = onRandomSeriesClick,
                    label = { Text(LocalStrings.current.seriesFilter.sortRandom) },
                    colors = chipColors,
                    shape = AppFilterChipDefaults.shape(),
                    border = AppFilterChipDefaults.filterChipBorder(false),
                )
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
    onBrowseClick: () -> Unit,
    onCollectionsClick: () -> Unit,
    onReadListsClick: () -> Unit,
) {
    if (collectionsCount > 0 || readListsCount > 0) {
        val tabCount = getTabCount(collectionsCount, readListsCount)
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
                label = { Text("Series") },
                colors = colors
            )
            var index = 1
            if (collectionsCount > 0) {
                SegmentedButton(
                    selected = currentTab == COLLECTIONS,
                    onClick = onCollectionsClick,
                    shape = SegmentedButtonDefaults.itemShape(index = index++, count = tabCount),
                    label = { Text("Collections") },
                    colors = colors
                )
            }
            if (readListsCount > 0) {
                SegmentedButton(
                    selected = currentTab == READ_LISTS,
                    onClick = onReadListsClick,
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = tabCount),
                    label = { Text("Read Lists") },
                    colors = colors
                )
            }
        }
    }
}

private fun getTabCount(collectionsCount: Int, readListsCount: Int): Int {
    var count = 1
    if (collectionsCount > 0) count++
    if (readListsCount > 0) count++
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
