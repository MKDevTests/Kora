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
     * The full taxonomy, in the order it should be offered to the admin editor.
     * Fixed on purpose: the point of the genre tab is a small, stable vocabulary,
     * not a free-form tag cloud (that's what Komga's own tags are for).
     */
    val allSlugs: List<String> = labels.keys.toList()

    /** Tag string for a slug, e.g. "fantasy" -> "kora:genre:fantasy". */
    fun tagOf(slug: String): String = PREFIX + slug

    /** Pretty display name for a slug: curated label, else "foo-bar" -> "Foo Bar". */
    fun label(slug: String): String =
        labels[slug] ?: slug.split('-')
            .filter { it.isNotBlank() }
            .joinToString(" ") { word -> word.replaceFirstChar { it.uppercaseChar() } }

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
