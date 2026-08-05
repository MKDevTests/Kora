package snd.komelia.db.tables

import org.jetbrains.exposed.v1.core.Table

/** Series the user answered "not interested" to (V88). */
object SuggestionDismissedTable : Table("SuggestionDismissed") {
    val seriesId = text("series_id")
    val dismissedAt = text("dismissed_at")

    override val primaryKey = PrimaryKey(seriesId)
}
