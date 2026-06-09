package snd.komelia.ui.settings.experimental

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.core.screen.Screen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import snd.komelia.settings.CommonSettingsRepository
import snd.komelia.ui.LocalViewModelFactory
import snd.komelia.ui.common.components.SwitchWithLabel
import snd.komelia.ui.settings.SettingsScreenContainer

class ExperimentalSettingsScreen : Screen {

    @Composable
    override fun Content() {
        val viewModelFactory = LocalViewModelFactory.current
        val vm = rememberScreenModel { viewModelFactory.getExperimentalSettingsViewModel() }
        LaunchedEffect(Unit) { vm.initialize() }

        SettingsScreenContainer("Experimental") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "These features are experimental — they may change or be removed in a future version.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp),
                )

                SwitchWithLabel(
                    checked = vm.genreTabEnabled,
                    onCheckedChange = vm::onGenreTabEnabledChange,
                    label = { Text("Genre tab") },
                    supportingText = { Text("Adds a Genre tab to each library, grouping series by their kora:genre:* tags. Requires a connection to the server.") },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}

class ExperimentalSettingsViewModel(
    private val settingsRepository: CommonSettingsRepository,
) : ScreenModel {
    var genreTabEnabled by mutableStateOf(false)
        private set

    suspend fun initialize() {
        genreTabEnabled = settingsRepository.getExperimentalGenreTab().first()
    }

    fun onGenreTabEnabledChange(enabled: Boolean) {
        genreTabEnabled = enabled
        screenModelScope.launch { settingsRepository.putExperimentalGenreTab(enabled) }
    }
}
