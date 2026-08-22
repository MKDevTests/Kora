package snd.komelia.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import snd.komelia.ui.common.menus.bulk.BottomPopupBulkActionsPanel
import snd.komelia.ui.common.menus.bulk.BulkActionsContainer
import snd.komelia.ui.common.menus.bulk.SeriesBulkActionsContent
import snd.komelia.ui.platform.BackPressHandler
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import snd.komelia.homefilters.SeriesHomeScreenFilter
import snd.komelia.ui.favorites.FavoritesScreen
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import snd.komelia.ui.LoadState
import snd.komelia.ui.LocalAccentColor
import snd.komelia.ui.LocalFloatingToolbarPadding
import snd.komelia.ui.LocalHazeState
import snd.komelia.ui.LocalKomgaState
import snd.komelia.ui.LocalOfflineMode
import snd.komelia.ui.LocalRawStatusBarHeight
import snd.komelia.ui.LocalReloadEvents
import snd.komelia.ui.LocalTheme
import snd.komelia.ui.LocalTransparentNavBarPadding
import snd.komelia.ui.LocalUseFloatingNavigationBar
import snd.komelia.ui.LocalUseNewLibraryUI2
import snd.komelia.ui.LocalFloatingActionButton
import snd.komelia.ui.LocalViewModelFactory
import snd.komelia.ui.ReloadableScreen
import snd.komelia.ui.topbar.NewTopAppBar
import snd.komelia.ui.book.bookScreen
import snd.komelia.ui.common.ContinueReadingFab
import snd.komelia.ui.common.FloatingFAB
import snd.komelia.ui.common.components.ErrorContent
import snd.komelia.ui.home.edit.FilterEditScreen
import snd.komelia.ui.platform.ScreenPullToRefreshBox
import snd.komelia.ui.reader.readerScreen
import snd.komelia.ui.series.seriesScreen
import snd.komga.client.library.KomgaLibraryId
import snd.komelia.ui.pushUnique

class HomeScreen(private val libraryId: KomgaLibraryId? = null) : ReloadableScreen {

    // Stable, distinct key per Home variant (root vs library-rooted). HomeScreen
    // was the only ReloadableScreen relying on Voyager's default key; two Home
    // instances sharing it clashed in SaveableStateHolder during replaceAll
    // transitions ("Key ... was used multiple times").
    override val key: ScreenKey = "HomeScreen_${libraryId?.value ?: "root"}"

    @Composable
    override fun Content() {
        val viewModelFactory = LocalViewModelFactory.current
        val isOffline = LocalOfflineMode.current.value
        val serverUrl = LocalKomgaState.current.serverUrl.value

        val vmKey = remember(libraryId, isOffline, serverUrl) {
            buildString {
                libraryId?.let { append(it.value) }
                append(serverUrl)
                append(isOffline.toString())
            }
        }
        val vm = rememberScreenModel(vmKey) { viewModelFactory.getHomeViewModel() }
        val navigator = LocalNavigator.currentOrThrow
        val reloadEvents = LocalReloadEvents.current

        LaunchedEffect(Unit) {
            vm.initialize()
            reloadEvents.collect { vm.reload() }
        }

        DisposableEffect(Unit) {
            vm.startKomgaEventsHandler()
            onDispose { vm.stopKomgaEventsHandler() }
        }

        val accentColor = LocalAccentColor.current
        val useNewUI2 = LocalUseNewLibraryUI2.current
        val theme = LocalTheme.current
        val barHeight = 45.dp
        val statusBarHeight = if (theme.transparentBars) LocalRawStatusBarHeight.current else 0.dp
        val floatingPadding = if (useNewUI2) barHeight + statusBarHeight else 0.dp
        val screenHazeState = if (useNewUI2 && theme.transparentBars) rememberHazeState() else null
        CompositionLocalProvider(
            LocalFloatingToolbarPadding provides floatingPadding,
            LocalHazeState provides screenHazeState,
        ) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .then(if (screenHazeState != null) Modifier.hazeSource(screenHazeState) else Modifier)
                ) {
                    ScreenPullToRefreshBox(screenState = vm.state, onRefresh = vm::reload) {
                        when (val state = vm.state.collectAsState().value) {
                            is LoadState.Error -> ErrorContent(
                                message = state.exception.message ?: "Unknown Error",
                                onReload = vm::reload
                            )

                            else ->
                                HomeContent(
                                    // Render only enabled shelves; the editor still
                                    // receives the full list (vm.currentFilters) so
                                    // disabled shelves can be toggled back on.
                                    // Render only enabled shelves; the editor still
                                    // receives the full list (vm.currentFilters) so
                                    // disabled shelves can be toggled back on.
                                    filters = vm.currentFilters.collectAsState().value
                                        .filter { it.filter.enabled },
                                    activeFilterNumber = vm.activeFilterNumber.collectAsState().value,
                                    onFilterChange = vm::onFilterChange,

                                    cardWidth = vm.cardWidth.collectAsState().value,
                                    onSeriesClick = { navigator push seriesScreen(it) },
                                    seriesMenuActions = vm.seriesMenuActions(),
                                    bookMenuActions = vm.bookMenuActions(),
                                    onBookClick = { navigator push bookScreen(it) },
                                    onBookReadClick = { book, markProgress ->
                                        navigator.parent?.pushUnique(
                                            readerScreen(
                                                book = book,
                                                markReadProgress = markProgress,
                                                // Always refresh: read progress changes even when
                                                // you leave on the SAME book, and "Keep reading"
                                                // has to reflect it without a manual pull.
                                                onExit = { vm.refreshAfterReading() }
                                            )
                                        )
                                    },
                                    selectedSeries = vm.selectedSeries.collectAsState().value,
                                    onSeriesSelect = vm::onSeriesSelect,
                                    onShelfClick = { shelf ->
                                        // Favorites has a real screen of its own,
                                        // with the per-library filter and its own
                                        // actions; the generic shelf detail is a
                                        // read-only "top N" view. Send the user to
                                        // the full one.
                                        navigator.pushUnique(
                                            if (shelf is SeriesHomeScreenFilter.Favorites) FavoritesScreen()
                                            else ShelfDetailScreen(shelf)
                                        )
                                    },
                                )

                        }

                        val selectedHomeSeries = vm.selectedSeries.collectAsState().value
                        if (selectedHomeSeries.isNotEmpty()) {
                            val displayed = vm.currentFilters.collectAsState().value
                                .mapNotNull { (it as? SeriesFilterData)?.series }
                                .flatten()
                                .distinctBy { it.id }
                            BulkActionsContainer(
                                onCancel = vm::clearSelection,
                                selectedCount = selectedHomeSeries.size,
                                allSelected = displayed.isNotEmpty() && selectedHomeSeries.size >= displayed.size,
                                onSelectAll = { vm.toggleSelectAll(displayed) },
                            ) {}
                            BottomPopupBulkActionsPanel {
                                SeriesBulkActionsContent(selectedHomeSeries, true)
                            }
                            BackPressHandler { vm.clearSelection() }
                        }

                        // Two FABs in the bottom row, side-by-side:
                        //   [nav-island] [Edit] [Continue]
                        //
                        // Edit goes into the regular right slot (existing
                        // behaviour). Continue goes into the new far-right
                        // slot so the layout in MainScreen places it
                        // immediately to the right of Edit on the same
                        // horizontal line as the floating nav island.
                        val openContinueBook: (snd.komelia.komga.api.model.KomeliaBook) -> Unit = { book ->
                            navigator.parent?.pushUnique(
                                readerScreen(
                                    book = book,
                                    markReadProgress = true,
                                    onExit = { vm.refreshAfterReading() },
                                )
                            )
                        }
                        val useFloatingNavigationBar = LocalUseFloatingNavigationBar.current
                        val fab = LocalFloatingActionButton.current
                        val fabFarRight = snd.komelia.ui.LocalFloatingActionButtonFarRight.current
                        if (useFloatingNavigationBar) {
                            DisposableEffect(Unit) {
                                fab.value = this@HomeScreen to {
                                    FloatingFAB(
                                        icon = Icons.Rounded.Edit,
                                        onClick = { navigator.replaceAll(FilterEditScreen(vm.currentFilters.value)) },
                                        accentColor = accentColor,
                                    )
                                }
                                fabFarRight.value = this@HomeScreen to {
                                    ContinueReadingFab(
                                        bookApi = vm.bookApi,
                                        libraryId = null,
                                        accentColor = accentColor,
                                        onOpenBook = openContinueBook,
                                    )
                                }
                                onDispose {
                                    if (fab.value?.first == this@HomeScreen) fab.value = null
                                    if (fabFarRight.value?.first == this@HomeScreen) fabFarRight.value = null
                                }
                            }
                        } else {
                            // Floating nav bar disabled → no slot mechanism.
                            // Put both FABs in a Row anchored to the bottom-
                            // right corner, Continue on the right (corner).
                            val extraBottomPadding = LocalTransparentNavBarPadding.current
                            androidx.compose.foundation.layout.Row(
                                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .then(if (extraBottomPadding == 0.dp) Modifier.windowInsetsPadding(WindowInsets.navigationBars) else Modifier)
                                    .padding(bottom = 16.dp + extraBottomPadding, end = 16.dp)
                            ) {
                                FloatingActionButton(
                                    onClick = { navigator.replaceAll(FilterEditScreen(vm.currentFilters.value)) },
                                    containerColor = accentColor ?: MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = if (accentColor != null) {
                                        if (accentColor.luminance() > 0.5f) Color.Black else Color.White
                                    } else MaterialTheme.colorScheme.onPrimaryContainer,
                                    shape = RoundedCornerShape(16.dp),
                                ) {
                                    Icon(Icons.Rounded.Edit, null)
                                }
                                ContinueReadingFab(
                                    bookApi = vm.bookApi,
                                    libraryId = null,
                                    accentColor = accentColor,
                                    onOpenBook = openContinueBook,
                                )
                            }
                        }
                    }
                }
                if (useNewUI2) {
                    NewTopAppBar()
                }
            }
        }
    }
}
