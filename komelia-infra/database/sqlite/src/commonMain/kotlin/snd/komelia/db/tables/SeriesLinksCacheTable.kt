package snd.komelia.db.tables

import org.jetbrains.exposed.v1.core.Table

/** Remembered Links tab per series (V92). */
object SeriesLinksCacheTable : Table("SeriesLinksCache") {
    val seriesId = text("series_id")
    val linksJson = text("links_json").default("{}")
    val updatedAt = text("updated_at").default("")

    override val primaryKey = PrimaryKey(seriesId)
}
