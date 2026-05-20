package snd.komelia.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import snd.komelia.komga.api.KomgaBookApi
import snd.komelia.komga.api.model.KomeliaBook
import snd.komga.client.book.KomgaBookSearch
import snd.komga.client.book.KomgaReadStatus
import snd.komga.client.common.KomgaPageRequest
import snd.komga.client.common.KomgaSort
import snd.komga.client.library.KomgaLibraryId
import snd.komga.client.search.allOfBooks

/**
 * One-tap "Continue reading" floating action button. Looks up the most
 * recently read in-progress book (optionally scoped to a single library
 * via [libraryId]) and, on click, hands it to [onOpenBook] so the
 * caller can push the appropriate reader screen.
 *
 * Renders nothing while the lookup is in-flight or when there is no
 * in-progress book — fresh installs and finished users get no
 * dangling button.
 *
 * Visual style matches the existing island-styled FloatingFAB so the
 * button blends with the rest of the floating-nav vocabulary. Callers
 * are responsible for positioning the FAB inside a Box (typically
 * Alignment.BottomStart on Home — to avoid the Edit FAB at BottomEnd —
 * and Alignment.BottomEnd on Library where no other FAB exists).
 *
 * @param libraryId null → app-wide last read; non-null → scope to that
 *   library so a user on a Library screen jumps to the last book they
 *   were reading in *that* library, not the global last.
 */
@Composable
fun ContinueReadingFab(
    bookApi: KomgaBookApi,
    libraryId: KomgaLibraryId? = null,
    accentColor: Color? = null,
    onOpenBook: (KomeliaBook) -> Unit,
    modifier: Modifier = Modifier,
) {
    var lastBook by remember(libraryId) { mutableStateOf<KomeliaBook?>(null) }

    LaunchedEffect(libraryId) {
        // The DSL field for "filter by library" is `library`, not
        // `libraryId` — and naming the local same as the DSL field
        // would shadow it. Capture the scope arg under a different
        // name to keep the DSL block readable.
        val scopeLibrary = libraryId
        lastBook = runCatching {
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
        }.getOrNull()
    }

    val book = lastBook ?: return

    FloatingFAB(
        icon = Icons.AutoMirrored.Rounded.MenuBook,
        onClick = { onOpenBook(book) },
        accentColor = accentColor,
        modifier = modifier,
    )
}
