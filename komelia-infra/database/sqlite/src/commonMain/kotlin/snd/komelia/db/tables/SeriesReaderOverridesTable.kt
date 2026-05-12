package snd.komelia.db.tables

import org.jetbrains.exposed.v1.core.Table

/**
 * Per-series user override for the paged/panels reader's reading direction.
 * Stored locally only — never synced to the Komga server (the server's
 * series metadata is shared across all Komga users on the instance, so we
 * mustn't push per-user preferences there).
 */
object SeriesReaderOverridesTable : Table("SeriesReaderOverrides") {
    val seriesId = text("series_id")
    val readingDirection = text("reading_direction")

    override val primaryKey = PrimaryKey(seriesId)
}
