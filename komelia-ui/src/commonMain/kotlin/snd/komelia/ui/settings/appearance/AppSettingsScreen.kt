package snd.komelia.ui.settings.appearance

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import snd.komelia.ui.LoadState
import snd.komelia.ui.LocalStrings
import snd.komelia.ui.LocalViewModelFactory
import snd.komelia.ui.common.components.LoadingMaxSizeIndicator
import snd.komelia.ui.settings.SettingsScreenContainer

/** Appearance: a summary of five pages (see AppearanceSections.kt). */
class AppSettingsScreen : Screen {

    @Composable
    override fun Content() {
        val viewModelFactory = LocalViewModelFactory.current
        val navigator = LocalNavigator.currentOrThrow
        val vm = rememberScreenModel { viewModelFactory.getAppearanceViewModel() }
        // Runs again each time a sub-page is popped: the summaries have to
        // show what was just changed there.
        LaunchedEffect(Unit) {
            vm.initialize()
            vm.refresh()
        }
        val state = vm.state.collectAsState()

        SettingsScreenContainer(LocalStrings.current.ui.appearance) {
            when (val result = state.value) {
                is LoadState.Error -> Text("${result::class.simpleName}: ${result.exception.message}")
                LoadState.Uninitialized, LoadState.Loading -> LoadingMaxSizeIndicator()
                is LoadState.Success -> AppearanceHub(vm, onOpen = { navigator.push(AppearanceSectionScreen(it)) })
            }
        }
    }
}
