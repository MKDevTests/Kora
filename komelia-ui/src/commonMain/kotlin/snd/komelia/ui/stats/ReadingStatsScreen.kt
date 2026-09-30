package snd.komelia.ui.stats

import snd.komelia.ui.LocalLibraries
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
import snd.komelia.ui.LocalStrings

class ReadingStatsScreen : Screen {

    @Composable
    override fun Content() {
        val viewModelFactory = LocalViewModelFactory.current
        val vm = rememberScreenModel { viewModelFactory.getReadingStatsViewModel() }
        LaunchedEffect(Unit) { vm.initialize() }
        val state = vm.state.collectAsState()
        val excluded = vm.excludedLibraryIds.collectAsState().value
        val libraries = LocalLibraries.current.collectAsState().value

        SettingsScreenContainer(LocalStrings.current.ui.myReadingStats) {
            when (val result = state.value) {
                is LoadState.Error -> {
                    Text("${result::class.simpleName}: ${result.exception.message}")
                    StatsSettingsEntry(libraries.count { it.id.value in excluded })
                }
                LoadState.Uninitialized, LoadState.Loading -> LoadingMaxSizeIndicator()
                is LoadState.Success -> {
                    ReadingStatsContent(
                        stats = result.value,
                        onRefresh = vm::refresh,
                    )
                    StatsSettingsEntry(libraries.count { it.id.value in excluded })
                }
            }
        }
    }
}
