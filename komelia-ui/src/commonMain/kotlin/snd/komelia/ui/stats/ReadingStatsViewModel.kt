package snd.komelia.ui.stats

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.launch
import snd.komelia.stats.ReadingStats
import snd.komelia.stats.ReadingStatsService
import snd.komelia.ui.LoadState

private val logger = KotlinLogging.logger {}

/**
 * Loads a [ReadingStats] snapshot via [ReadingStatsService] and exposes
 * it as a [LoadState]. The screen recomputes once on first entry and
 * on every explicit [refresh] (e.g. pull-to-refresh).
 *
 * Compute is performed off the UI thread (the underlying repository
 * already uses Dispatchers.IO via the Exposed wrapper).
 */
class ReadingStatsViewModel(
    private val service: ReadingStatsService,
) : StateScreenModel<LoadState<ReadingStats>>(LoadState.Uninitialized) {

    suspend fun initialize() {
        if (state.value !is LoadState.Uninitialized) return
        load()
    }

    fun refresh() {
        screenModelScope.launch { load() }
    }

    private suspend fun load() {
        mutableState.value = LoadState.Loading
        try {
            val stats = service.compute()
            mutableState.value = LoadState.Success(stats)
        } catch (t: Throwable) {
            logger.error(t) { "ReadingStatsService.compute failed" }
            mutableState.value = LoadState.Error(t)
        }
    }
}
