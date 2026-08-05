package snd.komelia.db.tables

import org.jetbrains.exposed.v1.core.Table

/** Remembered "Keep reading" row per library (V91). */
object KeepReadingCacheTable : Table("KeepReadingCache") {
    val libraryId = text("library_id")
    val booksJson = text("books_json").default("[]")
    val updatedAt = text("updated_at").default("")

    override val primaryKey = PrimaryKey(libraryId)
}
