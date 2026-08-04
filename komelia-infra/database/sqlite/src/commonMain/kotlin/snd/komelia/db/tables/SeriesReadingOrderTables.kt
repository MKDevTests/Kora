package snd.komelia.db.tables

import org.jetbrains.exposed.v1.core.Table

/** Series the user designated as the start of its franchise (V87). */
object SeriesReadingOrderOriginalTable : Table("SeriesReadingOrderOriginal") {
    val seriesId = text("series_id")

    override val primaryKey = PrimaryKey(seriesId)
}

/**
 * Last computed reading-order graph per original series, titles included.
 *
 * Derived data: dropped whenever a link changes. It is cached for the titles —
 * one Komga lookup per box, which is what would be felt on every visit — not
 * for the graph maths, which is local and instant.
 */
object SeriesReadingOrderCacheTable : Table("SeriesReadingOrderCache") {
    val originalSeriesId = text("original_series_id")
    val graph = text("graph")
    val builtAt = text("built_at")

    override val primaryKey = PrimaryKey(originalSeriesId)
}
