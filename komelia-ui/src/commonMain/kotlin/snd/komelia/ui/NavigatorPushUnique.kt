package snd.komelia.ui

import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator

/**
 * Pushes [screen], or goes back to the copy already on the stack.
 *
 * Voyager keys a screen's saved state by [Screen.key], and Compose refuses two
 * identical keys in one SaveableStateHolder. Kora's detail screens key on the
 * content they show — SeriesScreen on the series id, BookScreen on the book id,
 * OneshotScreen on the series id — so any path that leads back to something
 * already open pushes a duplicate key and the application dies:
 *
 *     IllegalArgumentException: Key 0R875EY6FB432:screen was used multiple times
 *         at SaveableStateHolderImpl.SaveableStateProvider(SaveableStateHolder.kt:88)
 *
 * Observed twice on the tablet, 2026-08-20 and 2026-08-22. It is a single
 * gesture away: a series, its other edition, and that edition's other edition
 * is the first series again. Or a series, one of its volumes, and the volume's
 * "parent series" button. The two guards that existed compared the target to
 * the CURRENT screen only, which neither of those paths trips.
 *
 * Going back to the open copy is also the better behaviour: the user returns
 * to where they were in it, instead of to a second, empty copy of the same
 * screen with the first still buried underneath.
 *
 * This is the rule MainScreen already applies for the reader's exit
 * destination, generalised so every push gets it.
 */
fun Navigator.pushUnique(screen: Screen) {
    if (items.any { it.key == screen.key }) popUntil { it.key == screen.key }
    else push(screen)
}
