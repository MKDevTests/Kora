package snd.komelia.db.tables

import org.jetbrains.exposed.v1.core.Table

/**
 * Local per-series user star ratings (1..5). See V64 migration.
 */
object SeriesRatingsTable : Table("series_ratings") {
    val seriesId = text("series_id")
    val stars = integer("stars")
    val ratedAt = long("rated_at")
    override val primaryKey = PrimaryKey(seriesId)
}
