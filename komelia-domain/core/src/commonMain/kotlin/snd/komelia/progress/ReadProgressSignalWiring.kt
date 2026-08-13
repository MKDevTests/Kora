package snd.komelia.progress

import snd.komelia.komga.api.KomgaApi
import snd.komelia.komga.api.KomgaBookApi
import snd.komga.client.book.KomgaBookId
import snd.komga.client.book.KomgaBookReadProgressUpdateRequest
import snd.komga.client.book.R2Progression

/**
 * Returns a [KomgaApi] identical to the receiver except that every read-progress
 * write on its book API also raises [ReadProgressChanges].
 *
 * Applied at the global api selector, like withStatsTracking, so it covers every
 * write path at once — image reader, epub readers, mark as read/unread, bulk
 * actions, offline flush — instead of asking each of the fifteen call sites to
 * remember to signal.
 */
fun KomgaApi.withReadProgressSignal(): KomgaApi {
    val signallingBookApi = ReadProgressSignalBookApi(bookApi)
    return object : KomgaApi by this {
        override val bookApi: KomgaBookApi = signallingBookApi
    }
}

/**
 * Forwards everything to [delegate]; raises [ReadProgressChanges] after the
 * three calls that change how far the user has read.
 *
 * Only AFTER the delegate returns, and only when it returned normally: a write
 * that threw changed nothing on the server, and telling Home to re-query for it
 * would just make it re-read the same rows.
 */
class ReadProgressSignalBookApi(
    private val delegate: KomgaBookApi,
) : KomgaBookApi by delegate {

    override suspend fun markReadProgress(
        bookId: KomgaBookId,
        request: KomgaBookReadProgressUpdateRequest,
    ) {
        delegate.markReadProgress(bookId, request)
        ReadProgressChanges.notifyChanged()
    }

    override suspend fun deleteReadProgress(bookId: KomgaBookId) {
        delegate.deleteReadProgress(bookId)
        ReadProgressChanges.notifyChanged()
    }

    override suspend fun updateReadiumProgression(bookId: KomgaBookId, progression: R2Progression) {
        delegate.updateReadiumProgression(bookId, progression)
        ReadProgressChanges.notifyChanged()
    }
}
