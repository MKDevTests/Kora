package snd.komelia.similarity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Everything the similarity scorer knows about one series, and nothing else.
 *
 * Deliberately not a trimmed KomgaSeries: summaries are the bulk of the payload
 * and useless here, and a library holds several thousand of these. Short
 * @SerialName keys because this is stored as JSON, once per series.
 */
@Serializable
data class SeriesTerms(
    /** Author name -> role ("writer", "penciller", …), lowercased. */
    @SerialName("a") val authors: Map<String, String> = emptyMap(),
    /** `kora:genre:*` slugs — the curated, trustworthy signal. */
    @SerialName("g") val genres: Set<String> = emptySet(),
    /** Series tags that aren't genres (`kora:tag:*` and free ones). */
    @SerialName("t") val tags: Set<String> = emptySet(),
    /** Tags aggregated from the books, a separate and noisier gisement. */
    @SerialName("bt") val bookTags: Set<String> = emptySet(),
    @SerialName("p") val publisher: String? = null,
) {
    /**
     * The series as (family, term) pairs — what both the inverted index and the
     * scorer actually consume. Terms are namespaced by family so an author named
     * "Action" can never collide with the Action genre.
     */
    fun features(): List<Feature> = buildList {
        authors.forEach { (name, role) -> add(Feature(TermFamily.AUTHOR, name, role)) }
        genres.forEach { add(Feature(TermFamily.GENRE, it)) }
        tags.forEach { add(Feature(TermFamily.TAG, it)) }
        bookTags.forEach { add(Feature(TermFamily.BOOK_TAG, it)) }
        publisher?.takeIf { it.isNotBlank() }?.let { add(Feature(TermFamily.PUBLISHER, it)) }
    }

    fun isEmpty(): Boolean =
        authors.isEmpty() && genres.isEmpty() && tags.isEmpty() && bookTags.isEmpty() && publisher == null
}

/** One namespaced term. [role] is only set for authors. */
data class Feature(
    val family: TermFamily,
    val value: String,
    val role: String? = null,
) {
    /** Namespaced key, so families never collide in the inverted index. */
    val key: String get() = "${family.prefix}:$value"
}

enum class TermFamily(val prefix: String) {
    AUTHOR("a"),
    GENRE("g"),
    TAG("t"),
    BOOK_TAG("bt"),
    PUBLISHER("p"),
}

/** A series in the index: its id plus its terms. */
data class IndexedSeries(
    val seriesId: String,
    val terms: SeriesTerms,
)
