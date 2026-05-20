package snd.komelia.stats

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import snd.komga.client.book.KomgaBookId

/**
 * In-process broadcast of "book just completed" events. Populated by
 * [StatsTrackingBookApi] when it sees a completion (either via the
 * `markReadProgress(completed=true)` write path or the
 * `updateReadiumProgression(progression>=0.999)` one). Subscribed by
 * the UI to show the "Just finished?" modal.
 *
 * Distinct from [ReadingEventsRepository] which persists completions
 * for stats — this flow is transient UI notification only. Replay=0
 * so a fresh subscriber doesn't get spammed with old completions, and
 * a small buffer absorbs bursts (e.g. bulk mark-as-read).
 */
class BookCompletionEvents {
    private val _events = MutableSharedFlow<KomgaBookId>(replay = 0, extraBufferCapacity = 8)
    val events: SharedFlow<KomgaBookId> = _events.asSharedFlow()

    suspend fun publish(bookId: KomgaBookId) {
        _events.emit(bookId)
    }
}
