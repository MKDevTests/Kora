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
        // One series credits "Kentaro Miura" AND "Kentarô MIURA": the same key
        // twice, which counted that author twice in the score and listed him
        // twice in the reasons. First spelling wins.
        .distinctBy { it.key }

    fun isEmpty(): Boolean =
        authors.isEmpty() && genres.isEmpty() && tags.isEmpty() && bookTags.isEmpty() && publisher == null
}

/** One namespaced term. [role] is only set for authors. */
data class Feature(
    val family: TermFamily,
    val value: String,
    val role: String? = null,
) {
    /**
     * Namespaced, normalised key — what the inverted index matches on. [value]
     * keeps the spelling to display.
     *
     * The normalisation is not cosmetic: the manga library spells the same
     * author 126 different ways by case alone ("Leiji MATSUMOTO" vs "Leiji
     * Matsumoto") and 35 more by accent ("Kentaro Miura" vs "Kentarô MIURA"),
     * and each spelling was scoring as a different person.
     *
     * Computed once rather than on every read: the engine reads this several
     * times per series while building the inverted index, and again on every
     * scoring pass.
     */
    val key: String by lazy(LazyThreadSafetyMode.NONE) {
        val folded = when (family) {
            // Free-text families only. `kora:genre:*` is a curated slug list,
            // and an author or a publisher is a name — singularising either
            // would be wrong rather than merely useless.
            TermFamily.TAG, TermFamily.BOOK_TAG -> foldTagTerm(value)
            else -> foldTerm(value)
        }
        "${family.prefix}:$folded"
    }
}

/**
 * [foldTerm] plus what it takes to stop one idea counting as three.
 *
 * Measured on the real libraries: 4394 distinct tags collapse to 3973, 421 of
 * them absorbed into 386 clusters. `ninjas` (36 series), `ninja` (16) and
 * `ninja/s` (7) were three unrelated terms; so were `wars`, `war` and `war/s`
 * on 216 series between them. Every one of the 66 clusters the singular step
 * creates was read: none of them merges two different ideas.
 *
 * Three steps. `/s` — how this library writes "or plural" — folds onto the
 * plural; separators collapse to a single space; a trailing `s` on the last
 * word is dropped. Only the LAST word: "monster girls" and "monster girl" are
 * the same tag, "girls love" and "girl love" would not be.
 *
 * The result is a key and is never displayed, so a linguistically wrong
 * singular ("medicines" -> "medicine", "physics" -> "physic") costs nothing as
 * long as it is deterministic. Only a collision between two different ideas
 * would cost something, and the measurement found none.
 *
 * Mirrored by fold_tag_term() in scripts/similar-bench/kora_similar.py, which
 * the fixture test holds to the letter — hence the explicit ASCII ranges rather
 * than isLetterOrDigit(), whose definition differs between the two languages.
 */
fun foldTagTerm(value: String): String {
    val folded = foldTerm(value)
    val builder = StringBuilder(folded.length)
    var index = 0
    while (index < folded.length) {
        val char = folded[index]
        val next = folded.getOrNull(index + 1)
        val afterNext = folded.getOrNull(index + 2)
        when {
            char == '/' && next == 's' &&
                (afterNext == null || !(afterNext in 'a'..'z' || afterNext in '0'..'9')) -> {
                builder.append('s')
                index += 2
            }

            char == ' ' || char == '_' || char == '-' -> {
                if (builder.isNotEmpty() && builder[builder.length - 1] != ' ') builder.append(' ')
                index++
            }

            else -> {
                builder.append(char)
                index++
            }
        }
    }
    val collapsed = builder.toString().trim(' ')
    if (collapsed.length < 2) return collapsed
    val lastWord = collapsed.substring(collapsed.lastIndexOf(' ') + 1)
    val plural = lastWord.length > 3 && lastWord.endsWith("s") && !lastWord.endsWith("ss")
    return if (plural) collapsed.substring(0, collapsed.length - 1) else collapsed
}

/**
 * Lowercase + strip the diacritics this library actually contains (French,
 * romanised Japanese macrons, a few Slavic names).
 *
 * An explicit table rather than a Unicode normaliser: there is none in common
 * Kotlin, and the Python bench has to fold **identically** or the two sides
 * would score differently — which the fixture test would catch, loudly, but
 * only after the tuning had already gone astray.
 */
fun foldTerm(value: String): String {
    val lower = value.lowercase()
    if (lower.all { it.code < 0x80 }) return lower
    return buildString(lower.length) {
        for (char in lower) {
            val index = ACCENTED.indexOf(char)
            when {
                index >= 0 -> append(PLAIN[index])
                char == 'œ' -> append("oe")
                char == 'æ' -> append("ae")
                char == 'ß' -> append("ss")
                else -> append(char)
            }
        }
    }
}

private const val ACCENTED = "àáâãäåāăçćčđďèéêëēĕėęěìíîïĩīĭįñńňòóôõöøōŏőùúûüũūŭůýÿŷšśşžźżřŕţťļłğ"
private const val PLAIN = /*     */ "aaaaaaaacccddeeeeeeeeeiiiiiiiinnnooooooooouuuuuuuuyyyssszzzrrttllg"

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
