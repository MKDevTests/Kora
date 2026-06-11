package snd.komelia.ui.library

/**
 * Helpers for the experimental Genre tab. Genres are Komga series *tags* in the
 * `kora:genre:<slug>` namespace, written by the user's external classifier. Kora
 * only ever reads them.
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
