package snd.komelia.progress

import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Process-wide "this app just moved read progress" signal.
 *
 * Home used to learn about progress ONLY from the Komga SSE stream. That works
 * right up until the stream is down, reconnecting, or simply slower than the
 * user — and then the Keep-reading shelf shows what was true before the book
 * was opened, forever, until a manual refresh. The library screen never had the
 * problem because it passes its own onExit callback to the reader; Home only
 * does that for books opened FROM Home.
 *
 * So: a local signal, emitted by [ReadProgressSignalBookApi] on every write we
 * make ourselves, consumed by Home to mark its progress shelves dirty. The SSE
 * event stays as the path for progress made on ANOTHER device — this one can't
 * see that, by construction.
 *
 * Same shape as SeriesLinksChanges: fire-and-forget, dropping the oldest rather
 * than suspending, because "something moved" doesn't accumulate — one late
 * emission is as good as eight.
 */
object ReadProgressChanges {
    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 8, onBufferOverflow = DROP_OLDEST)

    val changes: SharedFlow<Unit> = _changes

    fun notifyChanged() {
        _changes.tryEmit(Unit)
    }
}
