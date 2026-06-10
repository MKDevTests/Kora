package snd.komelia.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import snd.komelia.settings.CommonSettingsRepository
import snd.komga.client.series.KomgaSeriesId

/**
 * App-wide handle for the experimental Ignore List, exposed via [LocalIgnoreList]
 * so any series menu / bulk action can ignore or restore a series without
 * threading callbacks. Backed by per-server settings; [onChanged] nudges the
 * current screen to reload so an ignored series disappears immediately.
 */
class IgnoreListController(
    val enabled: StateFlow<Boolean>,
    val ignoredIds: StateFlow<Set<String>>,
    private val settingsRepository: CommonSettingsRepository,
    private val scope: CoroutineScope,
    private val onChanged: () -> Unit,
) {
    fun isIgnored(id: KomgaSeriesId): Boolean = id.value in ignoredIds.value

    fun ignore(ids: Collection<KomgaSeriesId>) {
        if (ids.isEmpty()) return
        scope.launch {
            settingsRepository.putIgnoredSeriesIds(ignoredIds.value + ids.map { it.value })
            onChanged()
        }
    }

    fun unignore(ids: Collection<KomgaSeriesId>) {
        if (ids.isEmpty()) return
        val remove = ids.map { it.value }.toSet()
        scope.launch {
            settingsRepository.putIgnoredSeriesIds(ignoredIds.value - remove)
            onChanged()
        }
    }
}
