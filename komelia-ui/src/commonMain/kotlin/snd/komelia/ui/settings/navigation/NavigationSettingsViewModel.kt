package snd.komelia.ui.settings.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import snd.komelia.settings.CommonSettingsRepository
import snd.komelia.settings.model.StartupScreen
import snd.komelia.ui.LoadState

/**
 * ViewModel for Settings → App Settings → Navigation. Hosts the two
 * configurable behaviours added when the library-title dropdown landed:
 *   - [libraryDropdownInTitle]: whether the big page title is a tappable
 *     library switcher dropdown or plain text.
 *   - [startupScreen]: which screen the app navigates to on cold start.
 *
 * Mirrors AppSettingsViewModel's pattern (mutableState + suspend init).
 */
class NavigationSettingsViewModel(
    private val settingsRepository: CommonSettingsRepository,
) : StateScreenModel<LoadState<Unit>>(LoadState.Uninitialized) {

    var libraryDropdownInTitle by mutableStateOf(true)
        private set
    var startupScreen by mutableStateOf(StartupScreen.HOME)
        private set
    var statsEnabled by mutableStateOf(true)
        private set
    var statsInBottomNav by mutableStateOf(false)
        private set

    suspend fun initialize() {
        if (state.value !is LoadState.Uninitialized) return
        mutableState.value = LoadState.Loading
        libraryDropdownInTitle = settingsRepository.getLibraryDropdownInTitle().first()
        startupScreen = settingsRepository.getStartupScreen().first()
        statsEnabled = settingsRepository.getStatsEnabled().first()
        statsInBottomNav = settingsRepository.getStatsInBottomNav().first()
        mutableState.value = LoadState.Success(Unit)
    }

    fun onLibraryDropdownInTitleChange(enabled: Boolean) {
        libraryDropdownInTitle = enabled
        screenModelScope.launch { settingsRepository.putLibraryDropdownInTitle(enabled) }
    }

    fun onStartupScreenChange(screen: StartupScreen) {
        startupScreen = screen
        screenModelScope.launch { settingsRepository.putStartupScreen(screen) }
    }

    fun onStatsEnabledChange(enabled: Boolean) {
        statsEnabled = enabled
        screenModelScope.launch { settingsRepository.putStatsEnabled(enabled) }
    }

    fun onStatsInBottomNavChange(enabled: Boolean) {
        statsInBottomNav = enabled
        screenModelScope.launch { settingsRepository.putStatsInBottomNav(enabled) }
    }
}
