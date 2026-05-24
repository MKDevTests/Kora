package snd.komelia

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import snd.komelia.ratings.SeriesRatingsRepository
import snd.komelia.stats.ReadingEventsRepository
import snd.komga.client.user.KomgaUserId

private val logger = KotlinLogging.logger {}

/**
 * One-shot job that tags legacy pre-v1.0.10 rows (where `komga_user_id`
 * is NULL) with the current Komga user's id. Runs once per app start,
 * waits for the first authenticated session, then updates both
 * `reading_events` and `series_ratings` in a single pass.
 *
 * Idempotent: after success, the WHERE-IS-NULL filter matches 0 rows on
 * subsequent runs (same app session reopens, fresh-install with no legacy
 * data). Safe to launch unconditionally from app startup.
 *
 * Why not a flag in AppSettings? It would mean another migration just for
 * a one-time signal. The UPDATE itself is cheap when there's nothing to
 * do (indexed scan over the NULL slice), so we just let it run.
 */
class UserScopeBackfillJob(
    private val readingEvents: ReadingEventsRepository,
    private val seriesRatings: SeriesRatingsRepository,
    private val currentUserId: StateFlow<KomgaUserId?>,
) {
    suspend fun run() {
        val userId = currentUserId.filterNotNull().first()
        runCatching {
            val events = readingEvents.backfillNullUserIds(userId)
            val ratings = seriesRatings.backfillNullUserIds(userId)
            if (events > 0 || ratings > 0) {
                logger.info { "User-scope backfill tagged $events reading_events + $ratings series_ratings with user ${userId.value}" }
            }
        }.onFailure {
            // Don't crash app startup if a transient DB lock or other
            // hiccup blocks the backfill — the next session will retry.
            logger.warn(it) { "User-scope backfill failed; will retry on next launch" }
        }
    }
}
