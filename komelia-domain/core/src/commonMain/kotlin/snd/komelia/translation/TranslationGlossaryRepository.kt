package snd.komelia.translation

import kotlinx.coroutines.flow.Flow
import snd.komga.client.series.KomgaSeriesId

/**
 * Read/write for the per-series translation glossary.
 *
 * A term with no series applies everywhere. A series-scoped term with the same
 * source wins over the global one, which is what lets a word mean one thing in
 * one universe and something else in another.
 */
interface TranslationGlossaryRepository {

    /**
     * Terms in force while reading [seriesId]: the global ones, overridden by
     * that series' own. Returned as source-to-target, ready for TermGlossary.
     */
    suspend fun termsFor(seriesId: KomgaSeriesId?): Map<String, String>

    /** Reactive version, so an edit shows on the next page without a reload. */
    fun observeTermsFor(seriesId: KomgaSeriesId?): Flow<Map<String, String>>

    /** Everything scoped to one series, for its editing screen. */
    suspend fun list(seriesId: KomgaSeriesId?): List<GlossaryTerm>

    /** Adds or replaces a term. A blank [target] keeps the term as it is. */
    suspend fun put(seriesId: KomgaSeriesId?, source: String, target: String)

    suspend fun delete(seriesId: KomgaSeriesId?, source: String)

    /** Every term, every series. */
    suspend fun listAll(): List<GlossaryTerm>

    suspend fun replaceAll(terms: List<GlossaryTerm>)
}
