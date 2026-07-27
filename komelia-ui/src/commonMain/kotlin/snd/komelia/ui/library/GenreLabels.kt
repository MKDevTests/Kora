package snd.komelia.ui.library

/**
 * Helpers for the Genre tab. Genres are Komga series *tags* in the
 * `kora:genre:<slug>` namespace, tagged by hand in Komga — there is no
 * classifier or script that re-tags the library, so nothing overwrites a manual
 * edit and the admin genre editor can write these tags freely.
 *
 * When writing them, only `kora:genre:*` may be touched: a series' other tags
 * carry real meaning (`kora:hidden` hides it for everyone) and replacing the
 * whole tag list would silently drop them.
 */
object GenreLabels {
    const val PREFIX = "kora:genre:"

    fun isGenreTag(tag: String): Boolean = tag.startsWith(PREFIX)

    fun slugOf(tag: String): String = tag.removePrefix(PREFIX)

    /** Curated French display names for the known genres; de-slug fallback otherwise. */
    private val labels: Map<String, String> = mapOf(
        "fantastique-surnaturel" to "Fantastique & Surnaturel",
        "fantasy" to "Fantasy",
        "science-fiction" to "Science-fiction",
        "comedie" to "Comédie",
        "action" to "Action",
        "romance" to "Romance",
        "drame" to "Drame",
        "policier-crime" to "Policier & Crime",
        "aventure" to "Aventure",
        "historique" to "Historique",
        "horreur" to "Horreur",
        "thriller-suspense" to "Thriller & Suspense",
        "mystere" to "Mystère",
        "super-heros" to "Super-héros",
        "tranche-de-vie" to "Tranche de vie",
        "societe" to "Société",
        "guerre-militaire" to "Guerre & Militaire",
        "documentaire-biographie" to "Documentaire & Biographie",
        "western" to "Western",
        "sport-arts-martiaux" to "Sport & Arts martiaux",
        "jeunesse" to "Jeunesse",
        "espionnage" to "Espionnage",
    )

    /**
     * The full taxonomy, sorted by display label so the admin editor reads
     * alphabetically rather than in map-declaration order. Fixed vocabulary on
     * purpose: the point of the genre tab is a small, stable list, not a
     * free-form tag cloud (that's what Komga's own tags are for).
     */
    val allSlugs: List<String> = labels.keys.sortedBy { labels.getValue(it).lowercase() }

    /** Tag string for a slug, e.g. "fantasy" -> "kora:genre:fantasy". */
    fun tagOf(slug: String): String = PREFIX + slug

    /** Pretty display name for a slug: curated label, else "foo-bar" -> "Foo Bar". */
    fun label(slug: String): String =
        labels[slug] ?: slug.split('-')
            .filter { it.isNotBlank() }
            .joinToString(" ") { word -> word.replaceFirstChar { it.uppercaseChar() } }

    /**
     * Maps a cover image's FILE NAME to a genre slug, for the bulk cover import.
     *
     * Accepts the naming the covers were authored with — "BD_Action.png",
     * "Comics Fantasy.png", "Mangas_SuperHéros.png" — by normalising away case,
     * accents and separators, then, if that fails, dropping a leading library
     * name ("BD_", "Comics ", …) and retrying.
     *
     * Returns null when nothing matches, so the caller can report the file as
     * unrecognised instead of guessing wrong.
     */
    fun slugForFileName(fileName: String): String? {
        val base = fileName.substringBeforeLast('.')
        return matchNormalised(base)
            ?: base.dropWhile { it != '_' && it != ' ' && it != '-' }
                .drop(1)
                .takeIf { it.isNotBlank() }
                ?.let { matchNormalised(it) }
    }

    /**
     * The leading LIBRARY token of a cover file name — "BD_Action" -> "BD",
     * "Comics Fantasy" -> "Comics" — or null when the name carries no library
     * prefix ("Espionnage", "Tranche_de_vie").
     *
     * Needed because the same genre exists in several libraries: Bd_Action,
     * Comics_Action and Manga_Action all resolve to `action`, so without the
     * prefix an import would apply all three and the last one would win.
     *
     * A separator only counts as a prefix boundary when what FOLLOWS it is
     * itself a recognisable genre — otherwise the separator belongs to the genre
     * ("Tranche_de_vie" is not library "Tranche").
     */
    fun libraryTokenOfFileName(fileName: String): String? {
        val base = fileName.substringBeforeLast('.')
        val idx = base.indexOfFirst { it == '_' || it == ' ' || it == '-' }
        if (idx <= 0) return null
        return if (matchNormalised(base.substring(idx + 1)) != null) base.substring(0, idx) else null
    }

    /**
     * Whether a cover file's library token belongs to [libraryName]. Tolerant of
     * singular/plural and case ("Manga" matches a "Mangas" library).
     */
    fun libraryTokenMatches(token: String, libraryName: String): Boolean {
        val a = normalise(token)
        val b = normalise(libraryName)
        if (a.isEmpty() || b.isEmpty()) return false
        return a.startsWith(b) || b.startsWith(a)
    }

    private fun matchNormalised(raw: String): String? {
        val key = normalise(raw)
        if (key.isBlank()) return null
        allSlugs.firstOrNull { normalise(it) == key }?.let { return it }
        fileNameAliases[key]?.let { return it }
        // "Policier" -> policier-crime, "Guerre" -> guerre-militaire, …: the file
        // uses the short form of a compound slug.
        return allSlugs.firstOrNull { normalise(it).startsWith(key) && key.length >= 4 }
    }

    /** Short forms that aren't a prefix of their slug, so can't be inferred. */
    private val fileNameAliases: Map<String, String> = mapOf(
        "sf" to "science-fiction",
        "sciencefiction" to "science-fiction",
        "histoire" to "historique",
        "tranchevie" to "tranche-de-vie",
        "tranchedevie" to "tranche-de-vie",
        "documentaires" to "documentaire-biographie",
        "documentaire" to "documentaire-biographie",
        "doc" to "documentaire-biographie",
        "biographie" to "documentaire-biographie",
        "crime" to "policier-crime",
        "suspense" to "thriller-suspense",
        "surnaturel" to "fantastique-surnaturel",
        "militaire" to "guerre-militaire",
        "martiaux" to "sport-arts-martiaux",
        "sport" to "sport-arts-martiaux",
        "heros" to "super-heros",
    )

    /** Lowercase, strip accents, drop everything that isn't a letter or digit. */
    private fun normalise(value: String): String = buildString {
        for (ch in value.lowercase()) {
            val folded = ACCENTS[ch] ?: ch
            if (folded.isLetterOrDigit()) append(folded)
        }
    }

    private val ACCENTS: Map<Char, Char> = buildMap {
        "àâäá".forEach { put(it, 'a') }
        "éèêë".forEach { put(it, 'e') }
        "îïí".forEach { put(it, 'i') }
        "ôöó".forEach { put(it, 'o') }
        "ûüù".forEach { put(it, 'u') }
        put('ç', 'c')
    }

    /**
     * Display-ready genre names extracted from a series' tags: only the
     * `kora:genre:*` ones, mapped to their pretty label, in tag order, de-duped.
     * Used by the series detail screen to show a "Genres : …" line.
     */
    fun genreDisplayNames(tags: List<String>): List<String> =
        tags.asSequence()
            .filter { isGenreTag(it) }
            .map { label(slugOf(it)) }
            .distinct()
            .toList()
}
