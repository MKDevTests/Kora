package snd.komelia.ui.settings.discover

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import snd.komelia.settings.CommonSettingsRepository
import snd.komelia.ui.LoadState

/**
 * Settings for the Discover tab, kept in their own screen rather than folded
 * into Navigation: this is the only feature in the app that talks to a third
 * party, and the switch that turns it on belongs next to the explanation of
 * what gets sent, not in a list of layout preferences.
 */
class DiscoverSettingsViewModel(
    private val settingsRepository: CommonSettingsRepository,
) : StateScreenModel<LoadState<Unit>>(LoadState.Uninitialized) {

    var discoverEnabled by mutableStateOf(false)
        private set

    /** Empty means every library — see [onLibraryToggle]. */
    var seedLibraryIds by mutableStateOf<Set<String>>(emptySet())
        private set

    suspend fun initialize() {
        if (state.value !is LoadState.Uninitialized) return
        mutableState.value = LoadState.Loading
        discoverEnabled = settingsRepository.getDiscoverEnabled().first()
        seedLibraryIds = settingsRepository.getDiscoverLibraryIds().first()
        mutableState.value = LoadState.Success(Unit)
    }

    fun onDiscoverEnabledChange(enabled: Boolean) {
        discoverEnabled = enabled
        screenModelScope.launch { settingsRepository.putDiscoverEnabled(enabled) }
    }

    /**
     * Ticking every library and ticking none are the same thing — both mean
     * "use everything" — so unticking the last one falls back to all rather
     * than leaving a seed that can never produce anything.
     */
    fun onLibraryToggle(libraryId: String, checked: Boolean) {
        val next = if (checked) seedLibraryIds + libraryId else seedLibraryIds - libraryId
        seedLibraryIds = next
        screenModelScope.launch { settingsRepository.putDiscoverLibraryIds(next) }
    }
}
