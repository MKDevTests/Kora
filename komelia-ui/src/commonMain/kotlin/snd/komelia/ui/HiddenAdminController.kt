package snd.komelia.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import snd.komelia.hidden.HiddenSeriesController
import snd.komga.client.series.KomgaSeriesId

/**
 * App-wide handle for the admin "hide for everyone" (kora:hidden) feature,
 * exposed via [LocalHiddenAdmin] so any series menu / bulk action can hide or
 * unhide a series. Only meaningful for a Komga admin — the server rejects the
 * tag write for non-admins (403), so callers gate the action on roleAdmin().
 * [onChanged] nudges the current screen to reload so a hidden series disappears
 * (or reappears) immediately.
 */
class HiddenAdminController(
    private val controller: HiddenSeriesController,
    private val scope: CoroutineScope,
    private val onChanged: () -> Unit,
) {
    val hiddenIds: StateFlow<Set<String>> = controller.hiddenIds

    fun isHidden(id: KomgaSeriesId): Boolean = id.value in hiddenIds.value

    fun hide(ids: Collection<KomgaSeriesId>) = act { controller.hide(ids.map { it.value }) }

    fun unhide(ids: Collection<KomgaSeriesId>) = act { controller.unhide(ids.map { it.value }) }

    private fun act(block: suspend () -> Unit) {
        scope.launch {
            runCatching { block() }
            onChanged()
        }
    }
}
