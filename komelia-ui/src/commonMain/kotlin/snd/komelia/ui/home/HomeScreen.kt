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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
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

class HomeScreen(private val libraryId: KomgaLibraryId? = null) : ReloadableScreen {

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
                                    filters = vm.currentFilters.collectAsState().value,
                                    activeFilterNumber = vm.activeFilterNumber.collectAsState().value,
                                    onFilterChange = vm::onFilterChange,

                                    cardWidth = vm.cardWidth.collectAsState().value,
                                    onSeriesClick = { navigator push seriesScreen(it) },
                                    seriesMenuActions = vm.seriesMenuActions(),
                                    bookMenuActions = vm.bookMenuActions(),
                                    onBookClick = { navigator push bookScreen(it) },
                                    onBookReadClick = { book, markProgress ->
                                        navigator.parent?.push(
                                            readerScreen(
                                                book = book,
                                                markReadProgress = markProgress,
                                                onExit = { lastReadBook ->
                                                    if (lastReadBook.id != book.id) {
                                                        vm.reload()
                                                    }
                                                }
                                            )
                                        )
                                    },
                                )

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
                            navigator.parent?.push(
                                readerScreen(
                                    book = book,
                                    markReadProgress = true,
                                    onExit = { lastReadBook ->
                                        if (lastReadBook.id != book.id) vm.reload()
                                    },
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
