package snd.komelia.db.tables

import org.jetbrains.exposed.v1.core.Table

object SavedSeriesFiltersTable : Table("SavedSeriesFilters") {
    val id = text("id")
    val libraryId = text("library_id")
    val name = text("name")
    val position = integer("position")
    val filterJson = text("filter_json")

    override val primaryKey = PrimaryKey(id)
}
