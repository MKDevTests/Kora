package snd.komelia.db.tables

import org.jetbrains.exposed.v1.core.Table

/** Remembered first page of books per series (V93). */
object SeriesBooksCacheTable : Table("SeriesBooksCache") {
    val seriesId = text("series_id")
    val booksJson = text("books_json").default("[]")
    val pageSize = integer("page_size").default(20)
    val totalPages = integer("total_pages").default(1)
    val updatedAt = text("updated_at").default("")

    override val primaryKey = PrimaryKey(seriesId)
}
