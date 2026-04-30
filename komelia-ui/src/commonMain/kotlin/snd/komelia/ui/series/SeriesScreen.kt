package snd.komelia.ui.series

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalUriHandler
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import snd.komelia.ui.BookSiblingsContext
import snd.komelia.ui.LoadState.Error
import snd.komelia.ui.LocalKomgaState
import snd.komelia.ui.LocalReloadEvents
import snd.komelia.ui.LocalViewModelFactory
import snd.komelia.ui.ReloadableScreen
import snd.komelia.ui.book.bookScreen
import snd.komelia.ui.collection.CollectionScreen
import snd.komelia.ui.common.components.ErrorContent
import snd.komelia.ui.library.LibraryScreen
import snd.komelia.ui.oneshot.OneshotScreen
import snd.komelia.ui.platform.BackPressHandler
import snd.komelia.ui.platform.ScreenPullToRefreshBox
import snd.komelia.ui.reader.readerScreen
import snd.komelia.ui.series.SeriesViewModel.SeriesTab
import snd.komelia.ui.series.view.SeriesContent
import snd.komga.client.library.KomgaLibraryId
import snd.komga.client.series.KomgaSeries
import snd.komga.client.series.KomgaSeriesId
import kotlin.jvm.Transient

import snd.komelia.ui.LocalAccentColor
import snd.komelia.ui.LocalPlatform
import snd.komelia.ui.LocalUseNewLibraryUI
import snd.komelia.ui.series.immersive.ImmersiveSeriesContent
import snd.komelia.ui.platform.PlatformType

fun seriesScreen(series: KomgaSeries): Screen =
    if (series.oneshot) OneshotScreen(series, BookSiblingsContext.Series())
    else SeriesScreen(series)

class SeriesScreen(
    val seriesId: KomgaSeriesId,
    @Transient
    private val series: KomgaSeries? = null,
    @Transient
    private val startingTab: SeriesTab? = SeriesTab.BOOKS,
) : ReloadableScreen {

    constructor(series: KomgaSeries, startingTab: SeriesTab = SeriesTab.BOOKS) : this(
        series.id,
        series,
        startingTab
    )

    override val key: ScreenKey = seriesId.toString()

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModelFactory = LocalViewModelFactory.current
        val vm = rememberScreenModel(seriesId.value) {
            viewModelFactory.getSeriesViewModel(seriesId, series, startingTab)
        }
        val reloadEvents = LocalReloadEvents.current
        LaunchedEffect(seriesId) {
            vm.initialize()
            val series = vm.series.value
            if (series != null && series.oneshot) {
                navigator.replace(OneshotScreen(series, BookSiblingsContext.Series()))
                return@LaunchedEffect
            }
            reloadEvents.collect { vm.reload() }
        }

        DisposableEffect(Unit) {
            vm.startKomgaEventHandler()
            onDispose { vm.stopKomgaEventHandler() }
        }

        val platform = LocalPlatform.current
        val useNewUI = LocalUseNewLibraryUI.current
        val series = vm.series.collectAsState().value
        val uriHandler = LocalUriHandler.current
        val serverUrl = LocalKomgaState.current.serverUrl.collectAsState().value
        val openInKomga: (() -> Unit)? = series?.let {
            { uriHandler.openUri("$serverUrl/series/${it.id.value}") }
        }
        if (platform == PlatformType.MOBILE && useNewUI && series != null) {
            ImmersiveSeriesContent(
                series = series,
                library = vm.library.collectAsState().value,
                accentColor = LocalAccentColor.current,
                onLibraryClick = { navigator.push(LibraryScreen(it.id)) },
                seriesMenuActions = vm.seriesMenuActions(),
                onFilterClick = { filter -> navigator.push(LibraryScreen(series.libraryId, filter)) },
                currentTab = vm.currentTab,
                onTabChange = vm::onTabChange,
                booksState = vm.booksState,
                onBookClick = { navigator push bookScreen(it, BookSiblingsContext.Series(vm.booksState.filterState.state.value)) },
                onBookReadClick = { book, markProgress ->
                    navigator.parent?.push(
                        readerScreen(
                            book = book,
                            markReadProgress = markProgress,
                            bookSiblingsContext = BookSiblingsContext.Series(vm.booksState.filterState.state.value),
                            onExit = { lastReadBook ->
                                if (lastReadBook.id != book.id) {
                                    vm.reload()
                                }
                            }
                        )
                    )
                },
                collectionsState = vm.collectionsState,
                onCollectionClick = { navigator.push(CollectionScreen(it.id)) },
                onSeriesClick = { s ->
                    navigator.push(
                        if (s.oneshot) OneshotScreen(s, BookSiblingsContext.Series())
                        else SeriesScreen(s, vm.currentTab)
                    )
                },
                onBackClick = { onBackPress(navigator, series.libraryId) },
                onDownload = vm::onDownload,
                initiallyExpanded = vm.isExpanded,
                onExpandChange = { vm.isExpanded = it },
                onOpenInKomga = openInKomga,
                onRandomSiblingClick = {
                    vm.openRandomSiblingSeries { newSeries ->
                        navigator.replace(seriesScreen(newSeries))
                    }
                },
                onPreviousSiblingSeriesClick = {
                    vm.openPreviousSiblingSeries { newSeries ->
                        navigator.replace(seriesScreen(newSeries))
                    }
                },
                onNextSiblingSeriesClick = {
                    vm.openNextSiblingSeries { newSeries ->
                        navigator.replace(seriesScreen(newSeries))
                    }
                },
                hasPreviousSiblingSeries = vm.hasPreviousSiblingSeries.collectAsState().value,
                hasNextSiblingSeries = vm.hasNextSiblingSeries.collectAsState().value,
            )

            BackPressHandler { onBackPress(navigator, series.libraryId) }
            return
        }

        ScreenPullToRefreshBox(screenState = vm.state, onRefresh = vm::reload) {
            when (val state = vm.state.collectAsState().value) {
                is Error -> ErrorContent(
                    message = state.exception.message ?: "Unknown Error",
                    onReload = vm::reload
                )

                else -> {
                    SeriesContent(
                        series = vm.series.collectAsState().value,
                        library = vm.library.collectAsState().value,
                        onLibraryClick = { navigator.push(LibraryScreen(it.id)) },
                        seriesMenuActions = vm.seriesMenuActions(),
                        onFilterClick = { filter ->
                            val series = requireNotNull(vm.series.value)
                            navigator.push(LibraryScreen(series.libraryId, filter))
                        },

                        currentTab = vm.currentTab,
                        onTabChange = vm::onTabChange,

                        booksState = vm.booksState,
                        onBookClick = { navigator push bookScreen(it, BookSiblingsContext.Series(vm.booksState.filterState.state.value)) },
                        onBookReadClick = { book, markProgress ->
                            navigator.parent?.push(
                                readerScreen(
                                    book = book,
                                    markReadProgress = markProgress,
                                    bookSiblingsContext = BookSiblingsContext.Series(vm.booksState.filterState.state.value),
                                    onExit = { lastReadBook ->
                                        if (lastReadBook.id != book.id) {
                                            vm.reload()
                                        }
                                    }
                                )
                            )
                        },

                        collectionsState = vm.collectionsState,
                        onCollectionClick = { collection -> navigator.push(CollectionScreen(collection.id)) },
                        onSeriesClick = { series ->
                            navigator.push(
                                if (series.oneshot) OneshotScreen(series, BookSiblingsContext.Series())
                                else SeriesScreen(series, vm.currentTab)
                            )
                        },
                        onDownload = vm::onDownload,
                        onOpenInKomga = openInKomga,
                        onRandomSiblingClick = {
                            vm.openRandomSiblingSeries { newSeries ->
                                navigator.replace(seriesScreen(newSeries))
                            }
                        },
                    )
                }
            }

            BackPressHandler {
                vm.series.value?.let { onBackPress(navigator, it.libraryId) }
            }
        }
    }

    private fun onBackPress(navigator: Navigator, libraryId: KomgaLibraryId?) {
        if (navigator.canPop) {
            navigator.pop()
        } else {
            libraryId?.let { navigator replaceAll LibraryScreen(it) }
        }
    }
}
