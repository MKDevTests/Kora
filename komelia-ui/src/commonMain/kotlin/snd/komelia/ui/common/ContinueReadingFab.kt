package snd.komelia.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import snd.komelia.progress.ReadProgressChanges
import snd.komelia.komga.api.KomgaBookApi
import snd.komelia.komga.api.model.KomeliaBook
import snd.komga.client.book.KomgaBookSearch
import snd.komga.client.book.KomgaReadStatus
import snd.komga.client.common.KomgaPageRequest
import snd.komga.client.common.KomgaSort
import snd.komga.client.library.KomgaLibraryId
import snd.komga.client.search.allOfBooks

/**
 * The most recently read in-progress book (optionally scoped to a single
 * library via [libraryId]), kept current as reading progress changes.
 * Null while the lookup is in flight or when nothing is in progress.
 *
 * Shared by the "Continue reading" button and Home's hero card, so the two
 * never disagree and cost one query, not two.
 *
 * @param libraryId null → app-wide last read; non-null → scope to that
 *   library so a user on a Library screen jumps to the last book they
 *   were reading in *that* library, not the global last.
 */
@Composable
fun rememberLastReadBook(
    bookApi: KomgaBookApi,
    libraryId: KomgaLibraryId? = null,
): State<KomeliaBook?> {
    val lastBook = remember(libraryId) { mutableStateOf<KomeliaBook?>(null) }

    // The DSL field for "filter by library" is `library`, not `libraryId`
    // — and naming the local same as the DSL field would shadow it. Capture
    // the scope arg under a different name to keep the DSL block readable.
    val scopeLibrary = libraryId
    // Result, not a nullable: getOrNull() would collapse "the server says you
    // have finished everything" and "the request failed" into the same answer,
    // and those want opposite handling — clear the button, or keep what it had.
    val fetchLastBook: suspend () -> Result<KomeliaBook?> = {
        runCatching {
            val condition = allOfBooks {
                readStatus { isEqualTo(KomgaReadStatus.IN_PROGRESS) }
                if (scopeLibrary != null) {
                    library { isEqualTo(scopeLibrary) }
                }
            }.toBookCondition()
            bookApi.getBookList(
                search = KomgaBookSearch(condition),
                pageRequest = KomgaPageRequest(
                    sort = KomgaSort.KomgaBooksSort.byReadDate(KomgaSort.Direction.DESC),
                    size = 1,
                ),
            ).content.firstOrNull()
        }
    }

    LaunchedEffect(libraryId) {
        fetchLastBook().onSuccess { lastBook.value = it }

        // Then follow read progress. This asked once and never again, so the
        // most prominent button in the app could offer a volume the user had
        // since finished — it kept the answer from whenever the screen was
        // first composed.
        //
        // collectLatest + delay is the debounce: the reader writes progress on
        // every page, so a reading session ends in a burst of signals and one
        // query is the right cost for all of them.
        ReadProgressChanges.changes.collectLatest {
            delay(1_000)
            fetchLastBook().onSuccess { lastBook.value = it }
        }
    }
    return lastBook
}

/**
 * One-tap "Continue reading" floating action button: looks the book up
 * itself ([rememberLastReadBook]) and, on click, hands it to [onOpenBook] so
 * the caller can push the appropriate reader screen.
 */
@Composable
fun ContinueReadingFab(
    bookApi: KomgaBookApi,
    libraryId: KomgaLibraryId? = null,
    accentColor: Color? = null,
    onOpenBook: (KomeliaBook) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lastBook by rememberLastReadBook(bookApi, libraryId)
    ContinueReadingFab(book = lastBook, accentColor = accentColor, onOpenBook = onOpenBook, modifier = modifier)
}

/**
 * The same button for a caller that already holds the book.
 *
 * Renders nothing when there is no in-progress book — fresh installs and
 * finished users get no dangling button. Visual style matches the
 * island-styled FloatingFAB so it blends with the floating-nav vocabulary.
 */
@Composable
fun ContinueReadingFab(
    book: KomeliaBook?,
    accentColor: Color? = null,
    onOpenBook: (KomeliaBook) -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = book ?: return

    FloatingFAB(
        icon = Icons.AutoMirrored.Rounded.MenuBook,
        onClick = { onOpenBook(current) },
        accentColor = accentColor,
        modifier = modifier,
    )
}
