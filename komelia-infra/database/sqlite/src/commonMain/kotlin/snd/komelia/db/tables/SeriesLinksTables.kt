package snd.komelia.db.tables

import org.jetbrains.exposed.v1.core.Table

/**
 * Local-only "Other versions" of a series (same work, different language /
 * edition). Series sharing a [groupId] are versions of each other. Symmetric:
 * membership is undirected. Never synced to the Komga server.
 */
object SeriesVersionsTable : Table("SeriesVersions") {
    val seriesId = text("series_id")
    val groupId = text("group_id")

    override val primaryKey = PrimaryKey(seriesId)
}

/**
 * Local-only typed "Related series" edges (sequel / prequel / spin-off /
 * related). Both directions are stored with inverse types, so each series
 * sees its relations directly. Never synced to the Komga server.
 */
object SeriesRelationsTable : Table("SeriesRelations") {
    val fromSeriesId = text("from_series_id")
    val toSeriesId = text("to_series_id")
    val relation = text("relation")

    override val primaryKey = PrimaryKey(fromSeriesId, toSeriesId)
}
