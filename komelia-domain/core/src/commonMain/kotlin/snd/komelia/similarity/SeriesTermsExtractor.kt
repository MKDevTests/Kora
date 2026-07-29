package snd.komelia.similarity

import snd.komga.client.series.KomgaSeries

/**
 * Mirrors GenreLabels.PREFIX in the UI module. Duplicated on purpose rather
 * than inverting the module dependency for a single string — if the namespace
 * ever changes, both move together.
 */
private const val GENRE_PREFIX = "kora:genre:"

/** The curated tag namespace. Everything else under `kora:` is a marker. */
private const val TAG_PREFIX = "kora:tag:"

/**
 * Tags that carry app state instead of taste, and must never be scored.
 *
 * They looked harmless until the bench ran on the real library: `nextrelease:`
 * tags are one per series (volume + date), so their rarity weight is the
 * highest of any term — two series sharing a release date would have outranked
 * two series sharing an author. `kora:hidden` is on 57 manga series and means
 * "an admin hid this", which is not a genre.
 */
fun isSimilarityMarkerTag(tag: String): Boolean = tag.lowercase().let {
    it.startsWith("nextrelease:") ||
        (it.startsWith("kora:") && !it.startsWith(GENRE_PREFIX) && !it.startsWith(TAG_PREFIX))
}

/**
 * Turns a series into the handful of terms the scorer needs.
 *
 * Called on every series of a page while indexing, so it must keep NOTHING it
 * doesn't score: the caller drops the KomgaSeries right after. Holding the full
 * objects for a few thousand series — summaries included, twice over — is what
 * would turn an index build into a memory spike on a tablet.
 */
fun KomgaSeries.toSimilarityTerms(): SeriesTerms {
    val seriesTags = metadata.tags.map { it.trim() }
        .filter { it.isNotEmpty() && !isSimilarityMarkerTag(it) }

    // `kora:genre:*` is the curated taxonomy and scores highest; everything else
    // is a plain tag. Splitting here keeps the weighting honest.
    val genres = seriesTags.filter { it.startsWith(GENRE_PREFIX) }
        .map { it.removePrefix(GENRE_PREFIX).lowercase() }
        .toSet()
    val tags = seriesTags.filterNot { it.startsWith(GENRE_PREFIX) }
        .map { it.lowercase() }
        .toSet()

    // One entry per author name: a name credited under several roles keeps the
    // most significant one, so "writer + colorist" doesn't score as a colorist.
    val authors = booksMetadata.authors
        .mapNotNull { author ->
            val name = author.name.trim()
            if (name.isEmpty()) null else name to author.role.trim().lowercase()
        }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, roles) -> roles.minByOrNull { ROLE_RANK[it] ?: ROLE_RANK.size } ?: roles.first() }

    return SeriesTerms(
        authors = authors,
        genres = genres,
        tags = tags,
        bookTags = booksMetadata.tags.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet(),
        publisher = metadata.publisher.trim().lowercase().ifEmpty { null },
    )
}

/** Most-significant-first, for picking one role out of several credits. */
private val ROLE_RANK: Map<String, Int> = listOf(
    "writer", "artist", "penciller", "inker", "colorist", "letterer", "translator",
).withIndex().associate { (index, role) -> role to index }
