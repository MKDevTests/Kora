package snd.komelia.ui.home

import snd.komelia.ui.strings.ShelfStrings

/**
 * The name to show for a Home shelf.
 *
 * Shelf names are user data — stored per user and editable — so they cannot be
 * translated at the source like the rest of the interface. A shelf whose name
 * is still exactly the one shipped with the app is translated; a shelf the user
 * renamed keeps the name they gave it, in whatever language they typed it.
 *
 * Not a @Composable: half the call sites are inside a lazy-grid block, which is
 * not a composition. The strings are read once by the caller instead.
 */
fun shelfLabel(label: String, strings: ShelfStrings): String = when (label) {
    "Keep reading" -> strings.keepReading
    "On deck" -> strings.onDeck
    "Recently released books" -> strings.recentlyReleasedBooks
    "Recently added books" -> strings.recentlyAddedBooks
    "Recently added series" -> strings.recentlyAddedSeries
    "Recently updated series" -> strings.recentlyUpdatedSeries
    "Recently read books" -> strings.recentlyReadBooks
    else -> label
}
