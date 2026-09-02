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
    /** Lowercased Komga language (V104), null when the row predates it. */
    val language: String? = null,
)

/**
 * A row's identity, without its terms.
 *
 * The duplicate finder compares titles and reads nothing else, and
 * [SimilarityIndexRepository.entriesOf] would JSON-decode one term blob per
 * series to hand it data it never opens — twelve thousand decodes for a sweep
 * that otherwise costs a tenth of a second.
 */
data class SimilarityIndexTitle(
    val seriesId: String,
    val libraryId: String,
    val titleSort: String,
    /**
     * Lowercased Komga language, or null when this row was written before V104
     * added the column. Null is unknown, never "no language" — see
     * [SeriesSimilarityIndexTable].
     */
    val language: String? = null,
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
    /**
     * Distinct genre slugs of the library, or null when no build has recorded
     * one yet (V100). Null is not zero: a library really holding no genre must
     * not be re-counted the slow way on every visit.
     */
    val genreCount: Int? = null,
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

    /**
     * Ids only, for callers that compare membership rather than read terms.
     *
     * [entriesOf] JSON-decodes one term blob per series; a sweep that just
     * wants to know which ids are on disk was paying thousands of decodes for
     * nothing.
     */
    suspend fun seriesIdsOf(libraryId: String): List<String>

    /**
     * Every indexed series of every library, identity only.
     *
     * Deliberately not per-library: the duplicate sweep wants the whole
     * catalogue and one query beats six.
     */
    suspend fun allTitles(): List<SimilarityIndexTitle>

    /** How many series [libraryId] has indexed, without loading any of them. */
    suspend fun countOf(libraryId: String): Int

    /** One series, by id — a primary-key read, unlike [entriesOf]. */
    suspend fun entryOf(seriesId: String): SimilarityIndexEntry?

    /** Insert or replace [entries]. Chunked internally; safe on thousands. */
    suspend fun upsertAll(entries: List<SimilarityIndexEntry>)

    /** Drops every row of [libraryId] — a full rebuild starts from empty. */
    suspend fun deleteLibrary(libraryId: String)

    /** Drops single series (Komga deleted them, or they moved library). */
    suspend fun deleteSeries(seriesIds: Collection<String>)

    suspend fun stateOf(libraryId: String): SimilarityIndexState?

    suspend fun putState(state: SimilarityIndexState)

    /**
     * Records [count] as the library's genre count without touching the rest
     * of its state. Used to fill in the blank left by an index built before
     * V100, so the expensive count happens once instead of on every visit.
     *
     * A no-op when the library has no state row: there is nothing to describe
     * yet, and the next build will write the count itself.
     */
    suspend fun putGenreCount(libraryId: String, count: Int)
}

/** Index rows as the engine consumes them. */
fun List<SimilarityIndexEntry>.toIndexedSeries(): List<IndexedSeries> =
    map { IndexedSeries(seriesId = it.seriesId, terms = it.terms) }
