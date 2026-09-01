package snd.komelia.ui.book

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import kotlinx.coroutines.launch
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.ui.BookSiblingsContext
import snd.komelia.ui.LocalReloadEvents
import snd.komelia.ui.LocalViewModelFactory
import snd.komelia.ui.ReloadableScreen
import snd.komelia.ui.library.LibraryScreen
import snd.komelia.ui.oneshot.OneshotScreen
import snd.komelia.ui.platform.BackPressHandler
import snd.komelia.ui.platform.ScreenPullToRefreshBox
import snd.komelia.ui.reader.ImageReaderScreen
import snd.komelia.ui.reader.readerScreen
import snd.komelia.ui.readlist.ReadListScreen
import snd.komelia.ui.series.SeriesScreen
import snd.komga.client.book.KomgaBookId
import snd.komga.client.series.KomgaSeries
import snd.komga.client.series.KomgaSeriesId
import kotlin.jvm.Transient

import snd.komelia.ui.LocalAccentColor
import snd.komelia.ui.LocalPlatform
import snd.komelia.ui.LocalUseNewLibraryUI
import snd.komelia.ui.book.immersive.ImmersiveBookContent
import snd.komelia.ui.platform.PlatformType
import snd.komelia.ui.pushUnique

fun bookScreen(
    book: KomeliaBook,
    bookSiblingsContext: BookSiblingsContext? = null,
    /** The book's series, when the caller has it — carried through to the reader
     *  so opening a volume by tapping its card is as cheap as the read button. */
    series: KomgaSeries? = null,
): Screen {
    val context = bookSiblingsContext ?: BookSiblingsContext.Series()
    return if (book.oneshot) OneshotScreen(book, context)
    else BookScreen(
        book = book,
        bookSiblingsContext = context,
        series = series,
    )
}

class BookScreen(
    val bookId: KomgaBookId,
    private val bookSiblingsContext: BookSiblingsContext,
    @Transient
    val book: KomeliaBook? = null,
    // Pass-through only: this screen never loads a series of its own (see
    // BookViewModel), it just forwards the caller's copy to the reader.
    @Transient
    private val series: KomgaSeries? = null,
) : ReloadableScreen {
    constructor(
        book: KomeliaBook,
        bookSiblingsContext: BookSiblingsContext,
        series: KomgaSeries? = null,
    ) : this(book.id, bookSiblingsContext, book, series)

    override val key: ScreenKey = bookId.toString()

    @Composable
    override fun Content() {
        val viewModelFactory = LocalViewModelFactory.current
        val vm = rememberScreenModel(bookId.value) {
            viewModelFactory.getBookViewModel(bookId, book, bookSiblingsContext)
        }
        val navigator = LocalNavigator.currentOrThrow
        val reloadEvents = LocalReloadEvents.current

        LaunchedEffect(Unit) {
            vm.initialize()
            vm.book.value?.let { if (it.oneshot) navigator.replace(OneshotScreen(it, bookSiblingsContext)) }
            reloadEvents.collect { vm.reload() }
        }
        DisposableEffect(Unit) {
            vm.startKomgaEventsHandler()
            onDispose { vm.stopKomgaEventHandler() }
        }

        val platform = LocalPlatform.current
        val useNewUI = LocalUseNewLibraryUI.current
        if (platform == PlatformType.MOBILE && useNewUI) {
            val book = vm.book.collectAsState().value ?: return
            val siblings = vm.siblingBooks.collectAsState().value

            val coroutineScope = rememberCoroutineScope()
            ImmersiveBookContent(
                onGenreClick = { slug ->
                    val libraryId = vm.book.value?.libraryId
                    navigator.pushUnique(
                        snd.komelia.ui.library.GenreSeriesScreen(
                            libraryId = libraryId,
                            genreTag = snd.komelia.ui.library.GenreLabels.tagOf(slug),
                            genreLabel = snd.komelia.ui.library.GenreLabels.label(slug),
                        )
                    )
                },
                seriesGenres = vm.seriesGenres,
                book = book,
                siblingBooks = siblings,
                library = vm.library,
                accentColor = LocalAccentColor.current,
                onLibraryClick = { navigator.pushUnique(LibraryScreen(it.id)) },
                bookMenuActions = vm.bookMenuActions,
                onBackClick = { onBackPress(navigator, book.seriesId) },
                onReadBook = { selectedBook, markReadProgress ->
                    navigator.parent?.pushUnique(
                        readerScreen(
                            book = selectedBook,
                            markReadProgress = markReadProgress,
                            bookSiblingsContext = bookSiblingsContext,
                            series = series,
                            // Refresh whichever book we came back on. Leaving on
                            // the SAME book is the normal case and used to do
                            // nothing at all, so the page kept showing the pages-
                            // remaining and last-read date from before the session
                            // until you navigated away and back.
                            onExit = { lastReadBook ->
                                if (lastReadBook.id != book.id) vm.setCurrentBook(lastReadBook)
                                else vm.refreshAfterReading()
                            }
                        )
                    )
                },
                onDownload = vm::onBookDownload,
                onFilterClick = { filter ->
                    navigator.pushUnique(LibraryScreen(book.libraryId, filter))
                },
                readLists = vm.readListsState.readLists,
                onReadListClick = { navigator.pushUnique(ReadListScreen(it.id)) },
                onReadListBookPress = { listBook, readList ->
                    if (listBook.id != book.id) navigator.pushUnique(
                        bookScreen(
                            book = listBook,
                            bookSiblingsContext = BookSiblingsContext.ReadList(readList.id)
                        )
                    )
                },
                cardWidth = vm.cardWidth.collectAsState().value,
                onSeriesClick = { seriesId -> navigator.pushUnique(SeriesScreen(seriesId)) },
                publisher = vm.publisher.collectAsState().value,
                onBookChange = vm::setCurrentBook,
                initiallyExpanded = vm.isExpanded,
                onExpandChange = { vm.isExpanded = it }
            )
            BackPressHandler { onBackPress(navigator, book.seriesId) }
            return
        }

        val book = vm.book.collectAsState().value

        val coroutineScope = rememberCoroutineScope()
        ScreenPullToRefreshBox(
            screenState = vm.state,
            onRefresh = vm::reload
        ) {
            BookScreenContent(
                onGenreClick = { slug ->
                    val libraryId = vm.book.value?.libraryId
                    navigator.pushUnique(
                        snd.komelia.ui.library.GenreSeriesScreen(
                            libraryId = libraryId,
                            genreTag = snd.komelia.ui.library.GenreLabels.tagOf(slug),
                            genreLabel = snd.komelia.ui.library.GenreLabels.label(slug),
                        )
                    )
                },
                seriesGenres = vm.seriesGenres,
                library = vm.library,
                book = book,
                bookMenuActions = vm.bookMenuActions,
                onBookReadPress = { markReadProgress ->
                    navigator.parent?.pushUnique(
                        if (book != null) readerScreen(
                            book = book,
                            bookSiblingsContext = bookSiblingsContext,
                            markReadProgress = markReadProgress,
                            series = series,
                            // Refresh whichever book we came back on. Leaving on
                            // the SAME book is the normal case and used to do
                            // nothing at all, so the page kept showing the pages-
                            // remaining and last-read date from before the session
                            // until you navigated away and back.
                            onExit = { lastReadBook ->
                                if (lastReadBook.id != book.id) vm.setCurrentBook(lastReadBook)
                                else vm.refreshAfterReading()
                            }
                        )
                        else ImageReaderScreen(
                            bookId = bookId,
                            bookSiblingsContext = bookSiblingsContext,
                            markReadProgress = markReadProgress,
                            // initialize() returns early once the screen is
                            // loaded, so the else branch has to be a real refresh.
                            onExit = { lastReadBook ->
                                if (lastReadBook.id != bookId) coroutineScope.launch { vm.initialize() }
                                else vm.refreshAfterReading()
                            }
                        )
                    )
                },
                onBookDownload = vm::onBookDownload,
                onBookDownloadDelete = vm::onBookDownloadDelete,

                readLists = vm.readListsState.readLists,
                onReadListClick = { navigator.pushUnique(ReadListScreen(it.id)) },
                onReadListBookPress = { book, readList ->
                    if (book.id != bookId) navigator.pushUnique(
                        bookScreen(
                            book = book,
                            bookSiblingsContext = BookSiblingsContext.ReadList(readList.id)
                        )
                    )
                },
                onParentSeriesPress = { book?.seriesId?.let { seriesId -> navigator.pushUnique(SeriesScreen(seriesId)) } },
                onFilterClick = { filter ->
                    navigator.pushUnique(LibraryScreen(requireNotNull(book?.libraryId), filter))
                },
                cardWidth = vm.cardWidth.collectAsState().value,
            )

            BackPressHandler { onBackPress(navigator, book?.seriesId) }
        }
    }

    private fun onBackPress(navigator: Navigator, seriesId: KomgaSeriesId?) {
        if (navigator.canPop) {
            navigator.pop()
        } else {
            when (val context = bookSiblingsContext) {
                is BookSiblingsContext.ReadList -> navigator.replace(ReadListScreen(context.id))
                is BookSiblingsContext.Series -> seriesId?.let { navigator.replace(SeriesScreen(it)) }
            }

        }
    }

}