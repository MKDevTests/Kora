package snd.komelia.readingorder

import snd.komga.client.series.KomgaSeriesId

/**
 * Which series a franchise is read FROM, and the last graph computed for it.
 *
 * Two separate concerns on purpose:
 *  - the **original** flag is the user's decision and must survive everything;
 *  - the **cached graph** is derived and thrown away whenever links change.
 *
 * Caching exists for the titles, not the maths: the graph itself is computed
 * from two local queries in microseconds, but naming its boxes costs one Komga
 * lookup per series (there is no "series in this id list" query), and those are
 * the seconds the user would feel on every visit.
 */
interface ReadingOrderRepository {

    /** Series the user designated as the start of its franchise. */
    suspend fun isOriginal(seriesId: KomgaSeriesId): Boolean

    /** All designated originals — used to find the one inside a franchise. */
    suspend fun originals(): Set<String>

    suspend fun setOriginal(seriesId: KomgaSeriesId, isOriginal: Boolean)

    /** Last graph computed for this original, or null if none/stale. */
    suspend fun getCached(originalSeriesId: KomgaSeriesId): ReadingOrderGraph?

    /**
     * The cached graph CONTAINING [seriesId], whichever series roots it.
     *
     * Finding the root means walking the links first — up to twelve requests,
     * two to three seconds — and the cache used to be consulted only after
     * that walk, so a graph that was already known still made the user wait for
     * it. Looked up by member, it can be drawn at once and corrected behind.
     */
    suspend fun getCachedContaining(seriesId: KomgaSeriesId): ReadingOrderGraph?

    suspend fun putCached(graph: ReadingOrderGraph)

    /**
     * Drops every cached graph. Called on any link change: a link added
     * anywhere can reshape a franchise the user is not currently looking at,
     * and the caches are a handful of small rows.
     */
    suspend fun invalidateAll()
}
