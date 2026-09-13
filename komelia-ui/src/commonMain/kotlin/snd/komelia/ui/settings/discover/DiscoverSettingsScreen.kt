package snd.komelia.ui.settings.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import snd.komelia.ui.LoadState
import snd.komelia.ui.LocalLibraries
import snd.komelia.ui.LocalStrings
import snd.komelia.ui.LocalViewModelFactory
import snd.komelia.ui.common.components.SwitchWithLabel
import snd.komelia.ui.common.components.LoadingMaxSizeIndicator
import snd.komelia.ui.settings.SettingsScreenContainer

/**
 * The Discover tab's own settings page.
 *
 * Everything about the feature lives here — the opt-in switch included — so
 * that turning it on and reading what it sends are the same gesture.
 */
class DiscoverSettingsScreen : Screen {
    override val key: String = "discover_settings"

    @Composable
    override fun Content() {
        val viewModelFactory = LocalViewModelFactory.current
        val vm = rememberScreenModel { viewModelFactory.getDiscoverSettingsViewModel() }
        LaunchedEffect(Unit) { vm.initialize() }

        SettingsScreenContainer(title = LocalStrings.current.discover.title) {
            when (vm.state.collectAsState().value) {
                is LoadState.Error, LoadState.Uninitialized, LoadState.Loading -> LoadingMaxSizeIndicator()
                is LoadState.Success -> DiscoverSettings(vm)
            }
        }
    }
}

@Composable
private fun DiscoverSettings(vm: DiscoverSettingsViewModel) {
    val libraries = LocalLibraries.current.collectAsState().value
    val strings = LocalStrings.current.discover

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column {
            SwitchWithLabel(
                label = { Text(strings.settingsEnable) },
                checked = vm.discoverEnabled,
                onCheckedChange = vm::onDiscoverEnabledChange,
            )
            Text(
                text = strings.settingsEnableHelp,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp),
            )
            Text(
                text = strings.settingsPrivacy,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp, top = 4.dp),
            )
        }

        HorizontalDivider()

        Column {
            Text(
                text = strings.settingsLibrariesTitle,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = strings.settingsLibrariesHelp,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
            libraries.forEach { library ->
                SwitchWithLabelRow(
                    label = library.name,
                    checked = library.id.value in vm.seedLibraryIds,
                    onCheckedChange = { vm.onLibraryToggle(library.id.value, it) },
                )
            }
            Text(
                text = strings.settingsLibrariesExclusion,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun SwitchWithLabelRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
