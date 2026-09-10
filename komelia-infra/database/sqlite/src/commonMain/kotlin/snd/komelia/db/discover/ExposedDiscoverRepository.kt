package snd.komelia.db.discover

import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.batchUpsert
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert
import snd.komelia.db.ExposedRepository
import snd.komelia.db.JsonDbDefault
import snd.komelia.db.tables.DiscoverDismissedTable
import snd.komelia.db.tables.DiscoverScanStateTable
import snd.komelia.db.tables.DiscoverSourceMapTable
import snd.komelia.db.tables.DiscoverSuggestionsTable
import snd.komelia.discover.DiscoverRepository
import snd.komelia.discover.DiscoverScanState
import snd.komelia.discover.DiscoverSourceLink
import snd.komelia.discover.DiscoverSuggestion
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * SQLite-backed store for the Discover tab (V105).
 *
 * Timestamps are epoch millis in a TEXT column, like the similarity index next
 * door: they round-trip without a parser, and an unreadable one degrades to
 * "never" rather than throwing.
 */
class ExposedDiscoverRepository(
    database: Database,
) : ExposedRepository(database), DiscoverRepository {

    override suspend fun linksOf(seriesIds: Collection<String>): Map<String, DiscoverSourceLink> {
        if (seriesIds.isEmpty()) return emptyMap()
        return transaction {
            seriesIds.chunked(READ_CHUNK).flatMap { chunk ->
                DiscoverSourceMapTable.selectAll()
                    .where { DiscoverSourceMapTable.seriesId.inList(chunk) }
                    .map { row ->
                        DiscoverSourceLink(
                            seriesId = row[DiscoverSourceMapTable.seriesId],
                            source = row[DiscoverSourceMapTable.sourceName],
                            externalId = row[DiscoverSourceMapTable.externalId],
                            resolvedAt = row[DiscoverSourceMapTable.resolvedAt].toInstantOrNull(),
                        )
                    }
            }.associateBy { it.seriesId }
        }
    }

    override suspend fun putLinks(links: Collection<DiscoverSourceLink>) {
        if (links.isEmpty()) return
        val now = Clock.System.now()
        transaction {
            links.chunked(WRITE_CHUNK).forEach { chunk ->
                DiscoverSourceMapTable.batchUpsert(chunk) { link ->
                    this[DiscoverSourceMapTable.seriesId] = link.seriesId
                    this[DiscoverSourceMapTable.sourceName] = link.source
                    this[DiscoverSourceMapTable.externalId] = link.externalId
                    this[DiscoverSourceMapTable.resolvedAt] = (link.resolvedAt ?: now).asStored()
                }
            }
        }
    }

    override suspend fun topSuggestions(limit: Int): List<DiscoverSuggestion> {
        if (limit <= 0) return emptyList()
        return transaction {
            // Dismissals are user gestures, so this stays a handful of rows.
            // Over-fetching by exactly that many and filtering in memory can
            // never return a short page, which a plain LIMIT would.
            val dismissed = DiscoverDismissedTable
                .select(DiscoverDismissedTable.externalId)
                .mapTo(mutableSetOf()) { it[DiscoverDismissedTable.externalId] }

            DiscoverSuggestionsTable.selectAll()
                .orderBy(DiscoverSuggestionsTable.score, SortOrder.DESC)
                .limit(limit + dismissed.size)
                .asSequence()
                .filterNot { it[DiscoverSuggestionsTable.externalId] in dismissed }
                .take(limit)
                .map { row ->
                    DiscoverSuggestion(
                        externalId = row[DiscoverSuggestionsTable.externalId],
                        source = row[DiscoverSuggestionsTable.sourceName],
                        title = row[DiscoverSuggestionsTable.title],
                        url = row[DiscoverSuggestionsTable.url],
                        imageUrl = row[DiscoverSuggestionsTable.imageUrl],
                        year = row[DiscoverSuggestionsTable.year],
                        rating = row[DiscoverSuggestionsTable.rating],
                        ratingVotes = row[DiscoverSuggestionsTable.ratingVotes],
                        status = row[DiscoverSuggestionsTable.status],
                        description = row[DiscoverSuggestionsTable.description],
                        genres = row[DiscoverSuggestionsTable.genres].asStringList(),
                        authors = row[DiscoverSuggestionsTable.authors].asStringList(),
                        publishers = row[DiscoverSuggestionsTable.publishers].asStringList(),
                        score = row[DiscoverSuggestionsTable.score],
                        becauseOf = runCatching {
                            JsonDbDefault.decodeFromString<List<String>>(row[DiscoverSuggestionsTable.becauseOf])
                        }.getOrElse { emptyList() },
                        updatedAt = row[DiscoverSuggestionsTable.updatedAt].toInstantOrNull(),
                    )
                }
                .toList()
        }
    }

    override suspend fun replaceSuggestions(suggestions: Collection<DiscoverSuggestion>) {
        val now = Clock.System.now()
        transaction {
            DiscoverSuggestionsTable.deleteAll()
            suggestions.chunked(WRITE_CHUNK).forEach { chunk ->
                DiscoverSuggestionsTable.batchUpsert(chunk) { suggestion ->
                    this[DiscoverSuggestionsTable.externalId] = suggestion.externalId
                    this[DiscoverSuggestionsTable.sourceName] = suggestion.source
                    this[DiscoverSuggestionsTable.title] = suggestion.title
                    this[DiscoverSuggestionsTable.url] = suggestion.url
                    this[DiscoverSuggestionsTable.imageUrl] = suggestion.imageUrl
                    this[DiscoverSuggestionsTable.year] = suggestion.year
                    this[DiscoverSuggestionsTable.rating] = suggestion.rating
                    this[DiscoverSuggestionsTable.ratingVotes] = suggestion.ratingVotes
                    this[DiscoverSuggestionsTable.status] = suggestion.status
                    this[DiscoverSuggestionsTable.description] = suggestion.description
                    this[DiscoverSuggestionsTable.genres] = JsonDbDefault.encodeToString(suggestion.genres)
                    this[DiscoverSuggestionsTable.authors] = JsonDbDefault.encodeToString(suggestion.authors)
                    this[DiscoverSuggestionsTable.publishers] = JsonDbDefault.encodeToString(suggestion.publishers)
                    this[DiscoverSuggestionsTable.score] = suggestion.score
                    this[DiscoverSuggestionsTable.becauseOf] =
                        JsonDbDefault.encodeToString(suggestion.becauseOf)
                    this[DiscoverSuggestionsTable.updatedAt] = (suggestion.updatedAt ?: now).asStored()
                }
            }
        }
    }

    override suspend fun dismiss(externalId: String) {
        transaction {
            DiscoverDismissedTable.upsert {
                it[DiscoverDismissedTable.externalId] = externalId
                it[DiscoverDismissedTable.dismissedAt] = Clock.System.now().asStored()
            }
            DiscoverSuggestionsTable.deleteWhere { DiscoverSuggestionsTable.externalId eq externalId }
        }
    }

    override suspend fun dismissedIds(): Set<String> = transaction {
        DiscoverDismissedTable
            .select(DiscoverDismissedTable.externalId)
            .mapTo(mutableSetOf()) { it[DiscoverDismissedTable.externalId] }
    }

    override suspend fun clearDismissed() {
        transaction { DiscoverDismissedTable.deleteAll() }
    }

    override suspend fun scanState(): DiscoverScanState = transaction {
        DiscoverScanStateTable.selectAll()
            .where { DiscoverScanStateTable.id eq STATE_ROW }
            .firstOrNull()
            ?.let { row ->
                DiscoverScanState(
                    lastRunAt = row[DiscoverScanStateTable.lastRunAt].toInstantOrNull(),
                    lastError = row[DiscoverScanStateTable.lastError],
                )
            }
            ?: DiscoverScanState(lastRunAt = null, lastError = "")
    }

    override suspend fun putScanState(state: DiscoverScanState) {
        transaction {
            DiscoverScanStateTable.upsert {
                it[DiscoverScanStateTable.id] = STATE_ROW
                it[DiscoverScanStateTable.lastRunAt] = state.lastRunAt?.asStored() ?: ""
                it[DiscoverScanStateTable.lastError] = state.lastError
            }
        }
    }
}

/** A row whose JSON is unreadable costs one field, never the whole card. */
private fun String.asStringList(): List<String> =
    runCatching { JsonDbDefault.decodeFromString<List<String>>(this) }.getOrElse { emptyList() }

private fun Instant.asStored(): String = toEpochMilliseconds().toString()

private fun String.toInstantOrNull(): Instant? =
    toLongOrNull()?.let { Instant.fromEpochMilliseconds(it) }

private const val STATE_ROW = 1
private const val WRITE_CHUNK = 500
private const val READ_CHUNK = 500
