package snd.komelia.ui.stats

import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import snd.komelia.stats.ReadingStats

/**
 * The last computed reading statistics, kept for the life of the process.
 *
 * The card is accessory by design: it is a summary of what you have already
 * read, and nothing about it is urgent. Recomputing it cost 2.66 s of serialised
 * SQL on a pool of one connection, plus three API calls competing with the home
 * shelves, and it ran on every composition of the home screen.
 *
 * So it is computed at most once per [TTL] within a session. A book finished
 * five minutes ago may not be counted yet; that is the accepted trade, stated
 * out loud. Opening the full statistics screen always recomputes, which is the
 * way to ask for fresh numbers on purpose.
 */
object ReadingStatsCache {
    private val TTL = 6.hours

    private var value: ReadingStats? = null
    private var computedAt: Instant? = null

    /** The remembered value, or null when nothing has been computed yet. */
    fun peek(): ReadingStats? = value

    fun isFresh(now: Instant = Clock.System.now()): Boolean {
        val at = computedAt ?: return false
        return now - at < TTL
    }

    fun put(stats: ReadingStats, now: Instant = Clock.System.now()) {
        value = stats
        computedAt = now
    }

    /** Drops the memo so the next read recomputes. Used by the full screen. */
    fun invalidate() {
        computedAt = null
    }
}
