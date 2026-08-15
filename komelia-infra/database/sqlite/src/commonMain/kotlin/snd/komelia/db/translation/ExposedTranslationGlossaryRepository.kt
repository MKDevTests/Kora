package snd.komelia.db.translation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert
import snd.komelia.db.ExposedRepository
import snd.komelia.db.tables.TranslationGlossaryTable
import snd.komelia.translation.GlossaryTerm
import snd.komelia.translation.TranslationGlossaryRepository
import snd.komga.client.series.KomgaSeriesId
import kotlin.time.Clock

/** Sentinel for "applies to every series"; the primary key forbids a null. */
private const val GLOBAL = ""

class ExposedTranslationGlossaryRepository(
    database: Database,
) : ExposedRepository(database), TranslationGlossaryRepository {

    /**
     * Broadcast of the series a write touched, so an open reader picks up an
     * edit on its next page. A global term emits [GLOBAL] and every observer
     * refreshes, since a global term is in force everywhere.
     */
    private val writeEvents = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 8)

    override suspend fun termsFor(seriesId: KomgaSeriesId?): Map<String, String> {
        val scope = seriesId?.value ?: GLOBAL
        return transaction {
            val rows = TranslationGlossaryTable
                .selectAll()
                .where {
                    TranslationGlossaryTable.seriesId.eq(GLOBAL)
                        .or(TranslationGlossaryTable.seriesId.eq(scope))
                }
                // Global first, so the series' own row overwrites it when both
                // spell the same source term.
                .orderBy(TranslationGlossaryTable.seriesId to SortOrder.ASC)
                .toList()
            rows.associate { row ->
                row[TranslationGlossaryTable.sourceTerm] to
                    row[TranslationGlossaryTable.targetTerm]
                        .ifEmpty { row[TranslationGlossaryTable.sourceTerm] }
            }
        }
    }

    override fun observeTermsFor(seriesId: KomgaSeriesId?): Flow<Map<String, String>> {
        val scope = seriesId?.value ?: GLOBAL
        return writeEvents
            .filter { it == scope || it == GLOBAL }
            .map { termsFor(seriesId) }
            .onStart { emit(termsFor(seriesId)) }
    }

    override suspend fun list(seriesId: KomgaSeriesId?): List<GlossaryTerm> {
        val scope = seriesId?.value ?: GLOBAL
        return transaction {
            TranslationGlossaryTable
                .selectAll()
                .where { TranslationGlossaryTable.seriesId.eq(scope) }
                .orderBy(TranslationGlossaryTable.sourceTerm to SortOrder.ASC)
                .map { it.toModel() }
        }
    }

    override suspend fun put(seriesId: KomgaSeriesId?, source: String, target: String) {
        val term = source.trim()
        require(term.isNotEmpty()) { "a glossary term needs a source word" }
        val scope = seriesId?.value ?: GLOBAL
        val now = Clock.System.now().toEpochMilliseconds()
        transaction {
            TranslationGlossaryTable.upsert {
                it[TranslationGlossaryTable.seriesId] = scope
                it[sourceTerm] = term
                // An empty target is stored as such rather than as a copy of
                // the source: it is the difference between "leave this word
                // alone" and "this word happens to translate to itself", and
                // only the first should follow a later change of source.
                it[targetTerm] = target.trim()
                it[createdAt] = now
            }
        }
        writeEvents.emit(scope)
    }

    override suspend fun delete(seriesId: KomgaSeriesId?, source: String) {
        val term = source.trim()
        val scope = seriesId?.value ?: GLOBAL
        transaction {
            TranslationGlossaryTable.deleteWhere {
                TranslationGlossaryTable.seriesId.eq(scope)
                    .and(TranslationGlossaryTable.sourceTerm.eq(term))
            }
        }
        writeEvents.emit(scope)
    }

    override suspend fun listAll(): List<GlossaryTerm> {
        return transaction {
            TranslationGlossaryTable
                .selectAll()
                .orderBy(
                    TranslationGlossaryTable.seriesId to SortOrder.ASC,
                    TranslationGlossaryTable.sourceTerm to SortOrder.ASC,
                )
                .map { it.toModel() }
        }
    }

    override suspend fun replaceAll(terms: List<GlossaryTerm>) {
        val now = Clock.System.now().toEpochMilliseconds()
        // Wipe and refill inside one transaction, so a crash mid-restore can
        // not leave the reader with half a glossary.
        transaction {
            TranslationGlossaryTable.deleteAll()
            terms.forEach { term ->
                val source = term.source.trim()
                if (source.isEmpty()) return@forEach
                TranslationGlossaryTable.insert {
                    it[seriesId] = term.seriesId?.value ?: GLOBAL
                    it[sourceTerm] = source
                    it[targetTerm] = if (term.isNameOnly) "" else term.target.trim()
                    it[createdAt] = now
                }
            }
        }
        // Every observer has to reconsider, so emit the wildcard.
        writeEvents.emit(GLOBAL)
    }

    private fun ResultRow.toModel(): GlossaryTerm {
        val source = get(TranslationGlossaryTable.sourceTerm)
        val scope = get(TranslationGlossaryTable.seriesId)
        return GlossaryTerm(
            seriesId = scope.takeIf { it.isNotEmpty() }?.let { KomgaSeriesId(it) },
            source = source,
            target = get(TranslationGlossaryTable.targetTerm).ifEmpty { source },
        )
    }
}
