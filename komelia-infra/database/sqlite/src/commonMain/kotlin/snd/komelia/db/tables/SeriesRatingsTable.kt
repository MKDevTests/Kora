package snd.komelia.db.tables

import org.jetbrains.exposed.v1.core.Table

/**
 * Local per-series user star ratings (1..5). See V64 migration.
 */
object SeriesRatingsTable : Table("series_ratings") {
    val seriesId = text("series_id")
    val stars = integer("stars")
    val ratedAt = long("rated_at")
    /**
     * Owning Komga user (server-issued UUID). Nullable on legacy rows
     * inserted before v1.0.10; backfilled at first post-upgrade auth.
     * See V66 migration.
     */
    val komgaUserId = text("komga_user_id").nullable()
    override val primaryKey = PrimaryKey(seriesId)
}
