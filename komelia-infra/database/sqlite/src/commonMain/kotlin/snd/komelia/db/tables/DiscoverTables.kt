package snd.komelia.db.tables

import org.jetbrains.exposed.v1.core.Table

/**
 * Which MangaUpdates series a local series is (V105). Resolved once and kept:
 * the link is usually already in the Komga metadata, and where it is not a
 * title search costs a request that must not repeat on every pass.
 */
object DiscoverSourceMapTable : Table("DiscoverSourceMap") {
    val seriesId = text("series_id")
    /** Named `sourceName`: `source` is taken by Exposed's ColumnSet. */
    val sourceName = text("source")

    /**
     * Null means "looked for, not found" once [resolvedAt] is set — deliberately
     * distinguishable from a series that has never been looked at, so a miss is
     * skipped for a while rather than paid for weekly.
     */
    val externalId = text("external_id").nullable()
    val resolvedAt = text("resolved_at")

    override val primaryKey = PrimaryKey(seriesId)
}

/**
 * What the last pass produced. The tab reads this table and nothing else.
 */
object DiscoverSuggestionsTable : Table("DiscoverSuggestions") {
    val externalId = text("external_id")
    /** Named `sourceName`: `source` is taken by Exposed's ColumnSet. */
    val sourceName = text("source")
    val title = text("title")
    val url = text("url")
    val imageUrl = text("image_url")
    val year = text("year")
    val rating = double("rating")
    val ratingVotes = integer("rating_votes")
    val status = text("status")
    val description = text("description")

    /** JSON arrays: short lists, read whole, never queried into. */
    val genres = text("genres")
    val authors = text("authors")
    val publishers = text("publishers")
    val score = double("score")

    /** JSON array of the local series ids that produced this suggestion. */
    val becauseOf = text("because_of")
    val updatedAt = text("updated_at")

    override val primaryKey = PrimaryKey(externalId)
}

/**
 * Suggestions waved away. A separate table on purpose: a refresh wipes the
 * results, and the dismissals must survive it.
 */
object DiscoverDismissedTable : Table("DiscoverDismissed") {
    val externalId = text("external_id")
    val dismissedAt = text("dismissed_at")

    override val primaryKey = PrimaryKey(externalId)
}

/** Single row, id 1. */
object DiscoverScanStateTable : Table("DiscoverScanState") {
    val id = integer("id")
    val lastRunAt = text("last_run_at")
    val lastError = text("last_error")

    override val primaryKey = PrimaryKey(id)
}
