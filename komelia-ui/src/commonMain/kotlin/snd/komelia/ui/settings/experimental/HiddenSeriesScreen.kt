package snd.komelia.ui.settings.experimental

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import kotlinx.coroutines.launch
import snd.komelia.hidden.HiddenSeriesController
import snd.komelia.komga.api.KomgaSeriesApi
import snd.komelia.ui.LocalViewModelFactory
import snd.komelia.ui.settings.SettingsScreenContainer
import snd.komga.client.series.KomgaSeries
import snd.komga.client.series.KomgaSeriesId

/**
 * Admin-only screen listing every series hidden for everyone (the kora:hidden
 * tag) so the admin can unhide them — hidden series are filtered out of every
 * list, so this is the only place to reach them in-app.
 */
class HiddenSeriesScreen : Screen {

    @Composable
    override fun Content() {
        val viewModelFactory = LocalViewModelFactory.current
        val vm = rememberScreenModel { viewModelFactory.getHiddenSeriesViewModel() }
        LaunchedEffect(Unit) { vm.initialize() }

        SettingsScreenContainer("Séries masquées") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Séries masquées pour TOUS les utilisateurs Kora via le tag kora:hidden " +
                        "(admin uniquement, partagé par le serveur Komga).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp),
                )

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${vm.hidden.size} masquée(s)",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    if (vm.hidden.isNotEmpty()) {
                        TextButton(onClick = vm::unhideAll) { Text("Tout réafficher") }
                    }
                }

                if (vm.hidden.isEmpty()) {
                    Text(
                        "Aucune série masquée. Appui long sur une série → « Masquer pour tous ».",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 20.dp),
                    )
                } else {
                    vm.hidden.forEach { series ->
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
                            IconButton(onClick = { vm.unhide(series.id) }) {
                                Icon(Icons.Default.Visibility, contentDescription = "Réafficher")
                            }
                        }
                    }
                }
            }
        }
    }
}

class HiddenSeriesViewModel(
    private val controller: HiddenSeriesController?,
    private val seriesApi: KomgaSeriesApi,
) : ScreenModel {
    var hidden by mutableStateOf<List<KomgaSeries>>(emptyList())
        private set

    suspend fun initialize() {
        controller?.refresh()
        loadHidden()
    }

    private suspend fun loadHidden() {
        val ids = controller?.hiddenIds?.value ?: emptySet()
        // getOneSeries is not hidden-filtered, so a hidden series still resolves.
        hidden = ids.mapNotNull { id ->
            runCatching { seriesApi.getOneSeries(KomgaSeriesId(id)) }.getOrNull()
        }.sortedBy { it.metadata.title.lowercase() }
    }

    fun unhide(id: KomgaSeriesId) {
        val c = controller ?: return
        screenModelScope.launch {
            c.unhide(listOf(id.value))
            hidden = hidden.filterNot { it.id == id }
        }
    }

    fun unhideAll() {
        val c = controller ?: return
        screenModelScope.launch {
            c.unhide(hidden.map { it.id.value })
            hidden = emptyList()
        }
    }
}
