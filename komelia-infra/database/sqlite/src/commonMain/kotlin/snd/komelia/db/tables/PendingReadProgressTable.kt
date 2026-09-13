package snd.komelia.db.tables

import org.jetbrains.exposed.v1.core.Table

/** Read progress the server has not acknowledged yet, one row per book (V112). */
object PendingReadProgressTable : Table("PendingReadProgress") {
    val bookId = text("book_id")
    val page = integer("page")
    val totalPages = integer("total_pages")
    val modified = text("modified")

    override val primaryKey = PrimaryKey(bookId)
}
