package snd.komelia.db.tables

import org.jetbrains.exposed.v1.core.Table

/** Series pairs the admin declared "not the same work" (V103). */
object DuplicateIgnoredTable : Table("DuplicateIgnored") {
    val pairKey = text("pair_key")
    val ignoredAt = text("ignored_at")

    override val primaryKey = PrimaryKey(pairKey)
}
