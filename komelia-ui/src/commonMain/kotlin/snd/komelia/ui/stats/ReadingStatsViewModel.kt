package snd.komelia.ui.stats

import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.Flow
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
    excludedLibraryIds: Flow<Set<String>> = flowOf(emptySet()),
) : StateScreenModel<LoadState<ReadingStats>>(LoadState.Uninitialized) {

    val excludedLibraryIds: StateFlow<Set<String>> =
        excludedLibraryIds.stateIn(screenModelScope, SharingStarted.Eagerly, emptySet())

    init {
        // Back from the stats settings with a library switched on or off:
        // the figures on screen answer the old question, recompute.
        excludedLibraryIds.drop(1).onEach { load() }.launchIn(screenModelScope)
    }

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
            // Opening this screen IS the way to ask for fresh numbers, so it
            // always recomputes -- and hands the result to the home card, which
            // otherwise waits out its own memo.
            snd.komelia.ui.stats.ReadingStatsCache.put(stats)
            mutableState.value = LoadState.Success(stats)
        } catch (t: Throwable) {
            logger.error(t) { "ReadingStatsService.compute failed" }
            mutableState.value = LoadState.Error(t)
        }
    }
}
