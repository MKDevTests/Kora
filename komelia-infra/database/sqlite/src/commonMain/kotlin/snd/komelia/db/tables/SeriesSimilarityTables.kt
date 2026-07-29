package snd.komelia.db.tables

import org.jetbrains.exposed.v1.core.Table

/**
 * Local term index feeding the "Similar series" tab (V85). One row per series,
 * holding only what the scorer reads — never the summaries, which are the bulk
 * of the Komga payload and useless here.
 *
 * Terms live as one compact JSON blob rather than a row per term: a few
 * thousand small rows load in milliseconds, where the normalised form would be
 * tens of thousands. The inverted index is rebuilt in memory on use.
 */
object SeriesSimilarityIndexTable : Table("SeriesSimilarityIndex") {
    val seriesId = text("series_id")
    val libraryId = text("library_id")
    val titleSort = text("title_sort")
    val terms = text("terms")
    val updatedAt = text("updated_at")

    override val primaryKey = PrimaryKey(seriesId)
}

/**
 * Build state per library, so "is the index usable / how stale is it" is one
 * cheap read instead of a count over the rows above.
 */
object SeriesSimilarityIndexStateTable : Table("SeriesSimilarityIndexState") {
    val libraryId = text("library_id")
    val builtAt = text("built_at")
    val seriesCount = integer("series_count")

    override val primaryKey = PrimaryKey(libraryId)
}
