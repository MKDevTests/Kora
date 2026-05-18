package snd.komelia.ui.settings.navigation

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import snd.komelia.ui.LoadState
import snd.komelia.ui.LocalViewModelFactory
import snd.komelia.ui.common.components.LoadingMaxSizeIndicator
import snd.komelia.ui.settings.SettingsScreenContainer

class NavigationSettingsScreen : Screen {

    @Composable
    override fun Content() {
        val viewModelFactory = LocalViewModelFactory.current
        val vm = rememberScreenModel { viewModelFactory.getNavigationSettingsViewModel() }
        LaunchedEffect(Unit) { vm.initialize() }
        val state = vm.state.collectAsState()

        SettingsScreenContainer("Navigation") {
            when (val result = state.value) {
                is LoadState.Error -> Text("${result::class.simpleName}: ${result.exception.message}")
                LoadState.Uninitialized, LoadState.Loading -> LoadingMaxSizeIndicator()
                is LoadState.Success -> NavigationSettingsContent(
                    libraryDropdownInTitle = vm.libraryDropdownInTitle,
                    onLibraryDropdownInTitleChange = vm::onLibraryDropdownInTitleChange,
                    startupScreen = vm.startupScreen,
                    onStartupScreenChange = vm::onStartupScreenChange,
                )
            }
        }
    }
}
