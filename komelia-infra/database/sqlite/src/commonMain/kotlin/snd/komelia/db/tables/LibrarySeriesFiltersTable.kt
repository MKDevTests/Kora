package snd.komelia.db.tables

import org.jetbrains.exposed.v1.core.Table

object LibrarySeriesFiltersTable : Table("LibrarySeriesFilters") {
    val libraryId = text("library_id")
    val filterJson = text("filter_json")

    override val primaryKey = PrimaryKey(libraryId)
}
