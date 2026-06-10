package snd.komelia.ui.settings.experimental

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.core.screen.Screen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import snd.komelia.komga.api.KomgaSeriesApi
import snd.komelia.settings.CommonSettingsRepository
import snd.komelia.ui.LocalViewModelFactory
import snd.komelia.ui.common.components.SwitchWithLabel
import snd.komelia.ui.settings.SettingsScreenContainer
import snd.komga.client.series.KomgaSeries
import snd.komga.client.series.KomgaSeriesId

class IgnoreListScreen : Screen {

    @Composable
    override fun Content() {
        val viewModelFactory = LocalViewModelFactory.current
        val vm = rememberScreenModel { viewModelFactory.getIgnoreListViewModel() }
        LaunchedEffect(Unit) { vm.initialize() }

        SettingsScreenContainer("Ignore List") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SwitchWithLabel(
                    checked = vm.enabled,
                    onCheckedChange = vm::onEnabledChange,
                    label = { Text("Enable ignore list") },
                    supportingText = { Text("Ignored series and their books are hidden everywhere (libraries, collections, search, home, genres). Local only — never sent to the server.") },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                )

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${vm.ignored.size} ignored",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    if (vm.ignored.isNotEmpty()) {
                        TextButton(onClick = vm::removeAll) { Text("Restore all") }
                    }
                }

                if (vm.ignored.isEmpty()) {
                    Text(
                        "No ignored series. Long-press a series and choose Ignore.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 20.dp),
                    )
                } else {
                    vm.ignored.forEach { series ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                series.metadata.title,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { vm.remove(series.id) }) {
                                Icon(Icons.Default.Visibility, contentDescription = "Restore")
                            }
                        }
                    }
                }
            }
        }
    }
}

class IgnoreListViewModel(
    private val settingsRepository: CommonSettingsRepository,
    private val seriesApi: KomgaSeriesApi,
) : ScreenModel {
    var enabled by mutableStateOf(false)
        private set
    var ignored by mutableStateOf<List<KomgaSeries>>(emptyList())
        private set

    suspend fun initialize() {
        enabled = settingsRepository.getIgnoreListEnabled().first()
        loadIgnored()
    }

    private suspend fun loadIgnored() {
        val ids = settingsRepository.getIgnoredSeriesIds().first()
        // getOneSeries is not ignore-filtered, so an ignored series still resolves.
        ignored = ids.mapNotNull { id ->
            runCatching { seriesApi.getOneSeries(KomgaSeriesId(id)) }.getOrNull()
        }.sortedBy { it.metadata.title.lowercase() }
    }

    fun onEnabledChange(value: Boolean) {
        enabled = value
        screenModelScope.launch { settingsRepository.putIgnoreListEnabled(value) }
    }

    fun remove(id: KomgaSeriesId) {
        screenModelScope.launch {
            val current = settingsRepository.getIgnoredSeriesIds().first()
            settingsRepository.putIgnoredSeriesIds(current - id.value)
            ignored = ignored.filterNot { it.id == id }
        }
    }

    fun removeAll() {
        screenModelScope.launch {
            settingsRepository.putIgnoredSeriesIds(emptySet())
            ignored = emptyList()
        }
    }
}
