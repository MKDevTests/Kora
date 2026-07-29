package snd.komelia.similarity

import kotlin.time.Instant

/**
 * One stored row of the term index: a series' identity plus the terms the
 * scorer needs. [titleSort] is kept so the bench (and any debugging view) can
 * name a series without a server round-trip; nothing else about the series is
 * stored — see [SeriesTerms].
 */
data class SimilarityIndexEntry(
    val seriesId: String,
    val libraryId: String,
    val titleSort: String,
    val terms: SeriesTerms,
)

/**
 * Per-library build state. Kept separate from the rows so "is this library
 * indexed, and how stale is it" is one cheap read instead of a count over
 * several thousand rows — the question every screen opening asks.
 */
data class SimilarityIndexState(
    val libraryId: String,
    val builtAt: Instant?,
    val seriesCount: Int,
)

/**
 * Local persistence of the similarity term index. Purely derived data: it is a
 * cache of what the Komga server already holds, rebuildable at any time, so it
 * is deliberately NOT part of the backup bundle.
 *
 * The index stores terms, never similarity scores. A stored score matrix would
 * be invalidated by every weight tweak — and tuning the weights is exactly what
 * this feature's next phase does. Scoring is rebuilt in memory by
 * [SimilarityEngine] from these rows, which costs milliseconds.
 */
interface SimilarityIndexRepository {

    /** Every indexed series of [libraryId]. The scorer's whole input. */
    suspend fun entriesOf(libraryId: String): List<SimilarityIndexEntry>

    /** Insert or replace [entries]. Chunked internally; safe on thousands. */
    suspend fun upsertAll(entries: List<SimilarityIndexEntry>)

    /** Drops every row of [libraryId] — a full rebuild starts from empty. */
    suspend fun deleteLibrary(libraryId: String)

    /** Drops single series (Komga deleted them, or they moved library). */
    suspend fun deleteSeries(seriesIds: Collection<String>)

    suspend fun stateOf(libraryId: String): SimilarityIndexState?

    suspend fun putState(state: SimilarityIndexState)
}

/** Index rows as the engine consumes them. */
fun List<SimilarityIndexEntry>.toIndexedSeries(): List<IndexedSeries> =
    map { IndexedSeries(seriesId = it.seriesId, terms = it.terms) }
