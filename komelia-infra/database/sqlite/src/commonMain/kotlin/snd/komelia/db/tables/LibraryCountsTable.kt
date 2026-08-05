package snd.komelia.db.tables

import org.jetbrains.exposed.v1.core.Table

/** Tab counts per library (V90). */
object LibraryCountsTable : Table("LibraryCounts") {
    val libraryId = text("library_id")
    val collections = integer("collections").default(0)
    val readLists = integer("read_lists").default(0)
    val genres = integer("genres").default(0)
    val updatedAt = text("updated_at").default("")

    override val primaryKey = PrimaryKey(libraryId)
}
