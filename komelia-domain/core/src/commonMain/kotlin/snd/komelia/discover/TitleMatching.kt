package snd.komelia.discover

/**
 * Decides whether a MangaUpdates result really is the local series, and builds
 * the queries worth asking it.
 *
 * This exists because the API's search never says no. Measured on the 43 seed
 * series of the real library (2026-09-10): every one of them got a result, and
 * 37 were nonsense — "Les Profs" matched "Sakitcho Dakedemo", "Titeuf" matched
 * "Colorful", "Poutine - L'ascension d'un dictateur" matched "Solo Slime's
 * Ascension". That is what a manga database does when asked about
 * franco-belgian comics, which are half this library.
 *
 * The rule is exact title equality, nothing weaker. Two samples decided it:
 *
 *  - 45 manga whose shelf name is a single significant word (Hellsing, Kingdom,
 *    Beastars, Doubt, Rainbow, Persona…): 43 resolved.
 *  - the 43 real seed series: 5 of 6 manga kept, and 0 of 37 comics accepted.
 *
 * A word-overlap rule was tried first and rejected on the same data: it
 * resolved none of the one-word manga and still let two comics through.
 */

/**
 * True when [candidateTitle] or [hitTitle] is exactly this series.
 *
 * [hitTitle] is the alias the API says it matched on, and it is what makes
 * French shelves work at all: "Iruma à l'école des démons" shares nothing with
 * "Mairimashita! Iruma-kun", but the hit title is the French alias and matches
 * exactly. It travels inside the search response, so the check costs no
 * request.
 *
 * Comparing against every result of the page rather than the first is what
 * finally resolved "Gantz (Perfect Edition)" to Gantz instead of to the
 * spin-off Gantz:G, which was ranked above it.
 */
internal fun titleMatches(localTitle: String, candidateTitle: String, hitTitle: String?): Boolean {
    val wanted = localTitleVariants(localTitle).map { normalizeTitle(it) }.filter { it.isNotEmpty() }
    if (wanted.isEmpty()) return false
    val offered = setOf(
        normalizeTitle(stripSourceLanguage(candidateTitle)),
        normalizeTitle(stripSourceLanguage(hitTitle.orEmpty())),
    )
    return wanted.any { it in offered }
}

/**
 * The forms of [title] worth comparing and searching on, best first.
 *
 * Komga shelf names carry decorations that mean something to their owner and
 * nothing to a database — "(Chap)" on 711 series here, then "(EN)", "(Univers)",
 * "(Couleur)", "(INT)", "(Light Novel)", "(Perfect Edition)". They are the
 * user's own convention and stay untouched in Komga; it is this side that has
 * to look past them.
 *
 * Trailing articles get a second variant with the article put back in front:
 * "Attaque Des Titans (l') - Before the Fall" only matches its French alias as
 * "L'Attaque Des Titans - Before the Fall", and "Fable (The)" as "The Fable".
 */
fun localTitleVariants(title: String): List<String> {
    var stripped = title.trim()
    var article: String? = null
    // Anywhere, not just at the end: a displaced article sits mid-title in
    // "Attaque Des Titans (l') - Before the Fall". Loops because a title can
    // carry two, as in "Something (Chap) (EN)".
    while (true) {
        val match = ANY_PARENTHESES.findAll(stripped)
            .firstOrNull { it.groupValues[1].trim().lowercase() in ARTICLES || it.groupValues[1].trim().lowercase() in DECORATIONS }
            ?: break
        val inside = match.groupValues[1].trim()
        if (inside.lowercase() in ARTICLES) article = inside
        stripped = (stripped.take(match.range.first) + " " + stripped.drop(match.range.last + 1))
            .replace(DOUBLE_SPACE, " ")
            .trim()
            .trim('-', ' ')
    }
    if (stripped.isEmpty()) return listOf(title.trim())
    return if (article == null) listOf(stripped)
    else listOf(withArticleInFront(article, stripped), stripped)
}

/**
 * Drops the language MangaUpdates appends to a localised alias.
 *
 * Its French aliases come back as "008 Apprenti espion (French)" and
 * "Centaures (French)". Without this, exact equality misses them — it is the
 * whole reason those two failed the first time this was measured.
 */
internal fun stripSourceLanguage(title: String): String {
    val match = TRAILING_PARENTHESES.find(title) ?: return title
    if (match.groupValues[1].trim().lowercase() !in SOURCE_LANGUAGES) return title
    return title.removeRange(match.range).trim()
}

/** Lowercased, unaccented, letters and digits only, single-spaced. */
internal fun normalizeTitle(title: String): String {
    val builder = StringBuilder(title.length)
    for (raw in title.lowercase()) {
        val ch = ACCENTS[raw] ?: raw
        when {
            ch.isLetterOrDigit() -> builder.append(ch)
            builder.isNotEmpty() && builder.last() != ' ' -> builder.append(' ')
        }
    }
    return builder.toString().trim()
}

/**
 * Capitalised on the way back in: the shelf writes it lowercase inside the
 * parentheses, and comparison lowercases everything anyway, but a title that
 * starts in lowercase reads as a bug in a log line.
 */
private fun withArticleInFront(article: String, rest: String): String {
    val head = article.replaceFirstChar { it.uppercaseChar() }
    return if (head.endsWith("'")) head + rest else "$head $rest"
}

private val TRAILING_PARENTHESES = Regex("""\s*\(([^()]{1,24})\)\s*$""")
private val ANY_PARENTHESES = Regex("""\(([^()]{1,24})\)""")
private val DOUBLE_SPACE = Regex("""\s{2,}""")

/**
 * Shelf decorations, measured by frequency over the 3938 Mangas and Light
 * Novels of this library.
 */
private val DECORATIONS = setOf(
    "chap", "en", "univers", "couleur", "int", "light novel", "perfect edition",
    "vf", "vo", "vostfr", "color", "colour", "deluxe", "collector",
)

private val ARTICLES = setOf("le", "la", "les", "l'", "the", "un", "une", "der", "die", "das")

/** The languages MangaUpdates tags its aliases with. */
private val SOURCE_LANGUAGES = setOf(
    "french", "english", "spanish", "german", "italian", "portuguese", "russian",
    "polish", "dutch", "turkish", "thai", "vietnamese", "indonesian", "arabic",
    "chinese", "korean", "japanese", "brazilian portuguese", "latin american spanish",
)

/**
 * Accent folding by hand: `java.text.Normalizer` does not exist in common code,
 * and the alternative is a dependency for a table of twenty characters.
 */
private val ACCENTS: Map<Char, Char> = buildMap {
    "àâäáã".forEach { put(it, 'a') }
    "éèêë".forEach { put(it, 'e') }
    "îïíì".forEach { put(it, 'i') }
    "ôöóòõ".forEach { put(it, 'o') }
    "ûüúù".forEach { put(it, 'u') }
    put('ç', 'c')
    put('ñ', 'n')
    put('ÿ', 'y')
}
