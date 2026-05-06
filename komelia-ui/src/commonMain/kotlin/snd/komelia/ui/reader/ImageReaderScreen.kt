package snd.komelia.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.zIndex
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.ui.BookSiblingsContext
import snd.komelia.ui.LoadState
import snd.komelia.ui.LoadState.Success
import snd.komelia.ui.LocalViewModelFactory
import snd.komelia.ui.LocalWindowState
import snd.komelia.ui.MainScreen
import snd.komelia.ui.book.bookScreen
import snd.komelia.ui.color.view.ColorCorrectionScreen
import snd.komelia.ui.login.LoginScreen
import snd.komelia.ui.common.components.ErrorContent
import snd.komelia.ui.platform.PlatformTitleBar
import snd.komelia.ui.platform.canIntegrateWithSystemBar
import snd.komelia.ui.reader.image.ReaderViewModel
import snd.komelia.ui.reader.image.common.ReaderContent
import snd.komga.client.book.KomgaBookId
import snd.komga.client.book.MediaProfile.DIVINA
import snd.komga.client.book.MediaProfile.EPUB
import snd.komga.client.book.MediaProfile.PDF
import kotlin.jvm.Transient

fun readerScreen(
    book: KomeliaBook,
    markReadProgress: Boolean,
    bookSiblingsContext: BookSiblingsContext? = null,
    onExit: ((KomeliaBook) -> Unit)? = null,
): Screen {
    val context = bookSiblingsContext ?: BookSiblingsContext.Series()
    val mediaProfile = book.media.mediaProfile
    return when {
        mediaProfile == DIVINA || mediaProfile == PDF || book.media.epubDivinaCompatible -> {
            ImageReaderScreen(
                bookId = book.id,
                markReadProgress = markReadProgress,
                bookSiblingsContext = context,
                onExit = onExit,
            )
        }
        mediaProfile == EPUB -> EpubScreen(
            bookId = book.id,
            bookSiblingsContext = context,
            markReadProgress = markReadProgress,
            book = book,
            onExit = onExit,
        )

        else -> error("Unsupported book format")
    }
}

class ImageReaderScreen(
    private val bookId: KomgaBookId,
    private val bookSiblingsContext: BookSiblingsContext,
    private val markReadProgress: Boolean = true,
    @Transient private val onExit: ((KomeliaBook) -> Unit)? = null,
) : Screen {

    @Composable
    override fun Content() {
        val coroutineScope = rememberCoroutineScope()
        val navigator = LocalNavigator.currentOrThrow
        val viewModelFactory = LocalViewModelFactory.current
        val vm = rememberScreenModel(bookId.value) {
            viewModelFactory.getBookReaderViewModel(
                navigator = navigator,
                markReadProgress = markReadProgress,
                bookSiblingsContext = bookSiblingsContext,
                bookId = bookId,
            )
        }

        //FIXME: do outside of composition? No proper multiplatform way to do it in viewmodel
        // restore current book when app process is killed in background on Android
        var currentBookId by rememberSaveable { mutableStateOf(bookId.value) }
        LaunchedEffect(Unit) {
            // Ensure no stale return-nav intent leaks from a previous reader session
            // whose caller didn't consume it.
            ReaderNavigationIntent.pending.value = null
            val bookId = KomgaBookId(currentBookId)
            vm.initialize(bookId)
            val book = vm.readerState.booksState.value?.currentBook
            if (book != null && book.media.mediaProfile != DIVINA && book.media.mediaProfile != PDF) {
                navigator.replace(
                    readerScreen(
                        book = book,
                        bookSiblingsContext = bookSiblingsContext,
                        markReadProgress = markReadProgress,
                        onExit = onExit,
                    )
                )
            }
            vm.readerState.booksState.filterNotNull()
                .collect { currentBookId = it.currentBook.id.value }
        }

        val vmState = vm.readerState.state.collectAsState(Dispatchers.Main.immediate)
        val serverUnavailableDialogVisible by vm.readerState.serverUnavailableDialogVisible
            .collectAsState(Dispatchers.Main.immediate)
        val currentBook = vm.readerState.booksState.collectAsState().value?.currentBook

        Column {
            PlatformTitleBar(Modifier.zIndex(10f), false) {
                if (canIntegrateWithSystemBar()) {
                    val isFullscreen = LocalWindowState.current.isFullscreen.collectAsState(false)
                    if (currentBook != null && !isFullscreen.value) {
                        TitleBarContent(
                            title = currentBook.metadata.title,
                            onExit = { onExit(navigator, currentBook) }
                        )
                    }
                }
            }

            when (val result = vmState.value) {
                is LoadState.Error -> ErrorContent(
                    exception = result.exception,
                    onExit = { onExit(navigator, currentBook) },
                    onReload = { coroutineScope.launch { vm.initialize(bookId) } }
                )

                LoadState.Loading, LoadState.Uninitialized -> LoadIndicator()
                is Success -> ReaderScreenContent(vm)
            }
        }

        if (serverUnavailableDialogVisible) {
            ServerUnavailableDialog(
                onDismiss = { vm.readerState.dismissServerUnavailableDialog() },
                onRetry = {
                    vm.readerState.dismissServerUnavailableDialog()
                    coroutineScope.launch { vm.initialize(bookId) }
                },
                onGoOffline = {
                    vm.readerState.dismissServerUnavailableDialog()
                    val rootNavigator = navigator.parent ?: navigator
                    rootNavigator.replaceAll(LoginScreen())
                }
            )
        }
    }

    @Composable
    fun ReaderScreenContent(vm: ReaderViewModel) {
        val navigator = LocalNavigator.currentOrThrow
        val windowState = LocalWindowState.current
        val keepScreenOn by vm.readerState.keepReaderScreenOn.collectAsState()
        DisposableEffect(keepScreenOn) {
            windowState.setKeepScreenOn(keepScreenOn)
            onDispose { windowState.setKeepScreenOn(false) }
        }

        ReaderContent(
            commonReaderState = vm.readerState,
            pagedReaderState = vm.pagedReaderState,
            continuousReaderState = vm.continuousReaderState,
            panelsReaderState = vm.panelsReaderState,
            screenScaleState = vm.screenScaleState,
            onnxRuntimeSettingsState = vm.onnxRuntimeSettingsState,
            ncnnSettingsState = vm.ncnnSettingsState,
            isColorCorrectionActive = vm.colorCorrectionIsActive.collectAsState(false).value,

            onColorCorrectionClick = {
                vm.readerState.booksState.value?.currentBook?.let { book ->
                    val page = vm.readerState.readProgressPage.value
                    navigator push ColorCorrectionScreen(book.id, page)
                }
            },
            onExit = { onExit(navigator, vm.readerState.booksState.value?.currentBook) }
        )
    }

    @Composable
    private fun LoadIndicator() {
        var showLoadIndicator by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            delay(100)
            showLoadIndicator = true
        }

        if (showLoadIndicator)
            Box(
                contentAlignment = Alignment.TopStart,
                modifier = Modifier.fillMaxSize()
            ) {
                LinearProgressIndicator(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    trackColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = .3f),
                    modifier = Modifier.scale(scaleX = 1f, scaleY = 3f).fillMaxWidth()
                )
            }

    }

    private fun onExit(navigator: Navigator, book: KomeliaBook?) {
        if (navigator.canPop) {
            book?.let { onExit?.invoke(it) }
            navigator.pop()
        } else if (book != null) {
            onExit?.invoke(book)
            navigator.replace(MainScreen(bookScreen(book)))
        }
    }
}

@Composable
private fun ServerUnavailableDialog(
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onGoOffline: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Server Unavailable") },
        text = { Text("Could not connect to the server. Would you like to switch to offline mode?") },
        confirmButton = {
            TextButton(onClick = onGoOffline) { Text("Go Offline") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                TextButton(onClick = onRetry) { Text("Retry") }
            }
        }
    )
}

