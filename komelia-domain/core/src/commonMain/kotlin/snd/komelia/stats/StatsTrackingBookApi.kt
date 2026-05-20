package snd.komelia.stats

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.StateFlow
import snd.komelia.komga.api.KomgaBookApi
import snd.komga.client.book.KomgaBookId
import snd.komga.client.book.KomgaBookReadProgressUpdateRequest
import snd.komga.client.book.R2Progression
import kotlin.time.Clock

private val logger = KotlinLogging.logger {}

/** A R2Progression value at or above this is treated as "finished". The
 *  image reader emits exactly 1.0 when the last page is reached
 *  (`page / totalPages`), but float comparisons can land just under after
 *  re-encoding, so we accept anything ≥ 0.999. */
private const val COMPLETION_PROGRESSION_THRESHOLD = 0.999f

/**
 * Side-effect wrapper around any [KomgaBookApi] implementation. Forwards
 * every call to [delegate]; in addition, records a
 * [ReadingEvent.Type.COMPLETED] row whenever a book is finished, no
 * matter which write path the caller used.
 *
 * Komga has two write paths for "book finished":
 *
 *  1. `markReadProgress(bookId, request { completed = true })` — used by
 *     the epub reader, "mark as read" menu items, bulk actions, offline
 *     sync, local-file viewer.
 *  2. `updateReadiumProgression(bookId, R2Progression(... progression = 1.0))`
 *     — used by the **image reader** (ReaderState.updateCacheAndPush).
 *     Komga marks the book completed server-side when the progression
 *     reaches the last page; our decorator detects this client-side by
 *     watching the `locations.progression` field.
 *
 * The previous version only hooked path (1); finishing a comic in the
 * image reader silently bypassed the stats log. Hooking both paths is
 * needed to capture every completion.
 *
 * Idempotency is delegated to [ReadingEventsRepository.record] (INSERT
 * OR IGNORE on (bookId, type)). Repeated writes for the same book — e.g.
 * a re-read — won't inflate counts.
 *
 * Honors the [statsEnabled] toggle: when off, the completion is still
 * persisted to Komga as usual, but no ReadingEvent is logged.
 *
 * Logging is best-effort. A failure inside [readingEvents.record] is
 * swallowed so the user's reading action never fails because the stats
 * table is locked, disk full, etc.
 */
class StatsTrackingBookApi(
    private val delegate: KomgaBookApi,
    private val readingEvents: ReadingEventsRepository,
    private val statsEnabled: StateFlow<Boolean>,
    private val completionEvents: BookCompletionEvents,
    private val clock: Clock = Clock.System,
) : KomgaBookApi by delegate {

    override suspend fun markReadProgress(
        bookId: KomgaBookId,
        request: KomgaBookReadProgressUpdateRequest,
    ) {
        delegate.markReadProgress(bookId, request)
        if (request.completed == true) {
            recordCompletion(bookId)
        }
    }

    override suspend fun updateReadiumProgression(
        bookId: KomgaBookId,
        progression: R2Progression,
    ) {
        delegate.updateReadiumProgression(bookId, progression)
        val frac = progression.locator.locations?.progression ?: 0f
        if (frac >= COMPLETION_PROGRESSION_THRESHOLD) {
            recordCompletion(bookId)
        }
    }

    private suspend fun recordCompletion(bookId: KomgaBookId) {
        // Always broadcast UI completion events — even when stats
        // logging is disabled, the "Just finished?" modal is a
        // separate user-facing concern that should still fire.
        try {
            completionEvents.publish(bookId)
        } catch (e: Throwable) {
            logger.debug(e) { "Failed to publish completion event for $bookId" }
        }

        if (!statsEnabled.value) return
        try {
            readingEvents.record(
                bookId = bookId,
                type = ReadingEvent.Type.COMPLETED,
                at = clock.now(),
            )
        } catch (e: Throwable) {
            logger.warn(e) { "Failed to record COMPLETED event for $bookId; stats may be inaccurate" }
        }
    }
}
