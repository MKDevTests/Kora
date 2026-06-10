package snd.komelia.ui.settings.experimental

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
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
import snd.komelia.ui.LocalAccentColor
import snd.komelia.ui.LocalViewModelFactory
import snd.komelia.ui.common.components.AppSlider
import snd.komelia.ui.common.components.AppSliderDefaults
import snd.komelia.ui.common.components.SwitchWithLabel
import snd.komelia.ui.platform.cursorForHand
import snd.komelia.ui.settings.SettingsScreenContainer
import kotlin.math.roundToInt

class ExperimentalSettingsScreen : Screen {

    @Composable
    override fun Content() {
        val viewModelFactory = LocalViewModelFactory.current
        val vm = rememberScreenModel { viewModelFactory.getExperimentalSettingsViewModel() }
        LaunchedEffect(Unit) { vm.initialize() }
        val accentColor = LocalAccentColor.current

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

                if (vm.genreTabEnabled) {
                    HorizontalDivider()
                    Text(
                        "Genre tile appearance",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 4.dp),
                    )
                    SwitchWithLabel(
                        checked = vm.genreTilesCustom,
                        onCheckedChange = vm::onGenreTilesCustomChange,
                        label = { Text("Custom tile appearance") },
                        supportingText = { Text("Give genre tiles their own size and text style instead of the global card appearance.") },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    )

                    if (vm.genreTilesCustom) {
                        Text(
                            "Tile size: ${vm.genreTileWidth}dp",
                            modifier = Modifier.padding(horizontal = 10.dp),
                        )
                        AppSlider(
                            value = vm.genreTileWidth.toFloat(),
                            onValueChange = { vm.onGenreTileWidthChange(it.roundToInt()) },
                            valueRange = 100f..350f,
                            colors = AppSliderDefaults.colors(accentColor = accentColor),
                            modifier = Modifier.cursorForHand().padding(end = 20.dp),
                        )
                        SwitchWithLabel(
                            checked = vm.genreTileTextBelow,
                            onCheckedChange = vm::onGenreTileTextBelowChange,
                            label = { Text("Title below cover") },
                            supportingText = { Text("Otherwise the title is overlaid on the cover.") },
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        )
                        SwitchWithLabel(
                            checked = vm.genreTileShowCount,
                            onCheckedChange = vm::onGenreTileShowCountChange,
                            label = { Text("Show series count") },
                            supportingText = { Text("Show the number of series under each genre.") },
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

class ExperimentalSettingsViewModel(
    private val settingsRepository: CommonSettingsRepository,
) : ScreenModel {
    var genreTabEnabled by mutableStateOf(false)
        private set
    var genreTilesCustom by mutableStateOf(false)
        private set
    var genreTileWidth by mutableStateOf(170)
        private set
    var genreTileTextBelow by mutableStateOf(false)
        private set
    var genreTileShowCount by mutableStateOf(true)
        private set

    suspend fun initialize() {
        genreTabEnabled = settingsRepository.getExperimentalGenreTab().first()
        genreTilesCustom = settingsRepository.getGenreTilesCustomAppearance().first()
        genreTileWidth = settingsRepository.getGenreTileWidth().first()
        genreTileTextBelow = settingsRepository.getGenreTileTextBelow().first()
        genreTileShowCount = settingsRepository.getGenreTileShowCount().first()
    }

    fun onGenreTabEnabledChange(enabled: Boolean) {
        genreTabEnabled = enabled
        screenModelScope.launch { settingsRepository.putExperimentalGenreTab(enabled) }
    }

    fun onGenreTilesCustomChange(enabled: Boolean) {
        genreTilesCustom = enabled
        screenModelScope.launch { settingsRepository.putGenreTilesCustomAppearance(enabled) }
    }

    fun onGenreTileWidthChange(width: Int) {
        genreTileWidth = width
        screenModelScope.launch { settingsRepository.putGenreTileWidth(width) }
    }

    fun onGenreTileTextBelowChange(below: Boolean) {
        genreTileTextBelow = below
        screenModelScope.launch { settingsRepository.putGenreTileTextBelow(below) }
    }

    fun onGenreTileShowCountChange(show: Boolean) {
        genreTileShowCount = show
        screenModelScope.launch { settingsRepository.putGenreTileShowCount(show) }
    }
}
