package snd.komelia.nextbook

import kotlinx.coroutines.flow.StateFlow
import snd.komelia.komga.api.KomgaApi
import snd.komelia.komga.api.model.KomeliaBook
import snd.komga.client.book.KomgaBookSearch
import snd.komga.client.book.KomgaReadStatus
import snd.komga.client.common.KomgaPageRequest
import snd.komga.client.common.KomgaSort
import snd.komga.client.search.allOfBooks

/**
 * Returns the books the user is currently reading, aggregated across every
 * library on the active server. Powers the home-screen "Next book up"
 * widget.
 *
 * Mirrors the Home screen's "Keep reading" shelf — same query (`read_status
 * = IN_PROGRESS`), same sort (most recent read activity first). NOT the
 * Komga "on deck" endpoint, which also folds in first-unread books from
 * already-started series — the user wants what they're actively in the
 * middle of, not what's queued next.
 *
 * Returns a [Result] so the widget can distinguish empty-but-successful
 * (no in-progress books, render the celebratory tile) from a
 * network/auth failure (fall back to the cached list).
 */
class NextBookService(
    private val komgaApi: StateFlow<KomgaApi>,
) {
    suspend fun getNextUpBooks(limit: Int = 3): Result<List<KomeliaBook>> = runCatching {
        val api = komgaApi.value
        val page = api.bookApi.getBookList(
            search = KomgaBookSearch(
                allOfBooks {
                    readStatus { isEqualTo(KomgaReadStatus.IN_PROGRESS) }
                }.toBookCondition()
            ),
            pageRequest = KomgaPageRequest(
                sort = KomgaSort.KomgaBooksSort.byReadDate(KomgaSort.Direction.DESC),
                size = limit,
            ),
        )
        page.content
    }
}
