package snd.komelia.db.links

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import snd.komelia.db.ExposedRepository
import snd.komelia.db.tables.SeriesRelationsTable
import snd.komelia.db.tables.SeriesVersionsTable
import snd.komelia.links.SeriesLinksRepository
import snd.komelia.links.SeriesRelation
import snd.komelia.links.SeriesRelationEdge
import snd.komelia.links.SeriesRelationType
import snd.komga.client.series.KomgaSeriesId

class ExposedSeriesLinksRepository(
    database: Database,
) : ExposedRepository(database), SeriesLinksRepository {

    // ----- Versions (symmetric group) -----

    override suspend fun versionsOf(seriesId: KomgaSeriesId): List<KomgaSeriesId> {
        return transaction {
            val group = groupOf(seriesId.value) ?: return@transaction emptyList()
            SeriesVersionsTable.selectAll()
                .where { SeriesVersionsTable.groupId eq group }
                .map { KomgaSeriesId(it[SeriesVersionsTable.seriesId]) }
                .filter { it.value != seriesId.value }
        }
    }

    override suspend fun linkVersion(a: KomgaSeriesId, b: KomgaSeriesId) {
        if (a == b) return
        transaction {
            val groupA = groupOf(a.value)
            val groupB = groupOf(b.value)
            // Reuse an existing group when there is one; otherwise seed a new
            // group label from a's id (unique, opaque — only used as a key).
            val target = groupA ?: groupB ?: a.value
            if (groupB != null && groupB != target) {
                SeriesVersionsTable.update({ SeriesVersionsTable.groupId eq groupB }) {
                    it[SeriesVersionsTable.groupId] = target
                }
            }
            if (groupA != null && groupA != target) {
                SeriesVersionsTable.update({ SeriesVersionsTable.groupId eq groupA }) {
                    it[SeriesVersionsTable.groupId] = target
                }
            }
            SeriesVersionsTable.upsert {
                it[SeriesVersionsTable.seriesId] = a.value
                it[SeriesVersionsTable.groupId] = target
            }
            SeriesVersionsTable.upsert {
                it[SeriesVersionsTable.seriesId] = b.value
                it[SeriesVersionsTable.groupId] = target
            }
        }
    }

    override suspend fun unlinkVersion(seriesId: KomgaSeriesId) {
        transaction {
            val group = groupOf(seriesId.value) ?: return@transaction
            SeriesVersionsTable.deleteWhere { SeriesVersionsTable.seriesId eq seriesId.value }
            // A group of one is meaningless — dissolve the leftover member too.
            val remaining = SeriesVersionsTable.selectAll()
                .where { SeriesVersionsTable.groupId eq group }
                .map { it[SeriesVersionsTable.seriesId] }
            if (remaining.size == 1) {
                SeriesVersionsTable.deleteWhere { SeriesVersionsTable.seriesId eq remaining.first() }
            }
        }
    }

    /** group_id of [seriesId], or null. Must be called inside a transaction. */
    private fun groupOf(seriesId: String): String? =
        SeriesVersionsTable.selectAll()
            .where { SeriesVersionsTable.seriesId eq seriesId }
            .firstOrNull()
            ?.get(SeriesVersionsTable.groupId)

    // ----- Relations (typed, bidirectional) -----

    override suspend fun relationsOf(seriesId: KomgaSeriesId): List<SeriesRelation> {
        return transaction {
            SeriesRelationsTable.selectAll()
                .where { SeriesRelationsTable.fromSeriesId eq seriesId.value }
                .mapNotNull { row ->
                    val type = parseType(row[SeriesRelationsTable.relation]) ?: return@mapNotNull null
                    SeriesRelation(KomgaSeriesId(row[SeriesRelationsTable.toSeriesId]), type)
                }
        }
    }

    override suspend fun linkRelation(from: KomgaSeriesId, to: KomgaSeriesId, type: SeriesRelationType) {
        if (from == to) return
        transaction {
            upsertRelation(from.value, to.value, type)
            upsertRelation(to.value, from.value, type.inverse())
        }
    }

    override suspend fun unlinkRelation(a: KomgaSeriesId, b: KomgaSeriesId) {
        transaction {
            SeriesRelationsTable.deleteWhere {
                (SeriesRelationsTable.fromSeriesId eq a.value) and (SeriesRelationsTable.toSeriesId eq b.value)
            }
            SeriesRelationsTable.deleteWhere {
                (SeriesRelationsTable.fromSeriesId eq b.value) and (SeriesRelationsTable.toSeriesId eq a.value)
            }
        }
    }

    private fun upsertRelation(from: String, to: String, type: SeriesRelationType) {
        SeriesRelationsTable.upsert {
            it[SeriesRelationsTable.fromSeriesId] = from
            it[SeriesRelationsTable.toSeriesId] = to
            it[SeriesRelationsTable.relation] = type.name
        }
    }

    private fun parseType(name: String): SeriesRelationType? =
        SeriesRelationType.entries.firstOrNull { it.name == name }

    // ----- Backup -----

    override suspend fun getAllVersions(): Map<KomgaSeriesId, String> {
        return transaction {
            SeriesVersionsTable.selectAll().associate {
                KomgaSeriesId(it[SeriesVersionsTable.seriesId]) to it[SeriesVersionsTable.groupId]
            }
        }
    }

    override suspend fun replaceAllVersions(byGroup: Map<KomgaSeriesId, String>) {
        transaction {
            SeriesVersionsTable.deleteAll()
            byGroup.forEach { (id, group) ->
                SeriesVersionsTable.upsert {
                    it[SeriesVersionsTable.seriesId] = id.value
                    it[SeriesVersionsTable.groupId] = group
                }
            }
        }
    }

    override suspend fun getAllRelations(): List<SeriesRelationEdge> {
        return transaction {
            SeriesRelationsTable.selectAll().mapNotNull { row ->
                val type = parseType(row[SeriesRelationsTable.relation]) ?: return@mapNotNull null
                SeriesRelationEdge(
                    KomgaSeriesId(row[SeriesRelationsTable.fromSeriesId]),
                    KomgaSeriesId(row[SeriesRelationsTable.toSeriesId]),
                    type,
                )
            }
        }
    }

    override suspend fun replaceAllRelations(edges: List<SeriesRelationEdge>) {
        transaction {
            SeriesRelationsTable.deleteAll()
            edges.forEach { upsertRelation(it.from.value, it.to.value, it.type) }
        }
    }
}
