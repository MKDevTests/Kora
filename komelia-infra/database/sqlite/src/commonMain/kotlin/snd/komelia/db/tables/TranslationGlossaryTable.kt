package snd.komelia.db.tables

import org.jetbrains.exposed.v1.core.Table

/**
 * Terms the page translator is not allowed to decide for itself. See V99.
 *
 * [seriesId] is empty for entries that apply everywhere; a series-scoped row
 * with the same source term wins over the global one.
 */
object TranslationGlossaryTable : Table("TranslationGlossary") {
    val seriesId = text("series_id")
    val sourceTerm = text("source_term")

    /** Empty means "keep the source word as it is", which is the name case. */
    val targetTerm = text("target_term")
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(seriesId, sourceTerm)
}
