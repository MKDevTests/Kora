package snd.komelia.ui.settings.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import snd.komelia.ui.LoadState
import snd.komelia.ui.LocalLibraries
import snd.komelia.ui.LocalViewModelFactory
import snd.komelia.ui.common.components.LoadingMaxSizeIndicator
import snd.komelia.ui.platform.rememberWidgetLibraryFilter
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
                is LoadState.Success -> Column {
                    NavigationSettingsContent(
                        libraryDropdownInTitle = vm.libraryDropdownInTitle,
                        onLibraryDropdownInTitleChange = vm::onLibraryDropdownInTitleChange,
                        startupScreen = vm.startupScreen,
                        onStartupScreenChange = vm::onStartupScreenChange,
                        statsEnabled = vm.statsEnabled,
                        onStatsEnabledChange = vm::onStatsEnabledChange,
                        statsInBottomNav = vm.statsInBottomNav,
                        onStatsInBottomNavChange = vm::onStatsInBottomNavChange,
                        nextReleasesInBottomNav = vm.nextReleasesInBottomNav,
                        onNextReleasesInBottomNavChange = vm::onNextReleasesInBottomNavChange,
                        aniListLinkSuggestionsEnabled = vm.aniListLinkSuggestionsEnabled,
                        onAniListLinkSuggestionsEnabledChange = vm::onAniListLinkSuggestionsEnabledChange,
                        shareLinksViaKomga = vm.shareLinksViaKomga,
                        onShareLinksViaKomgaChange = vm::onShareLinksViaKomgaChange,
                    )
                    WidgetLibrarySection()
                }
            }
        }
    }
}

/**
 * Library filter for the Android "Next book up" widget. Null from
 * [rememberWidgetLibraryFilter] (desktop/web: no widget) hides the whole
 * section. The pick is applied on the widget's next refresh — which happens
 * automatically when the app goes to background.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WidgetLibrarySection() {
    val filter = rememberWidgetLibraryFilter() ?: return
    val libraries = LocalLibraries.current.collectAsState().value
    if (libraries.isEmpty()) return

    Column(modifier = Modifier.padding(top = 20.dp)) {
        HorizontalDivider()
        Text(
            "Widget « Prochain tome »",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            "Limiter le widget à une bibliothèque. Appliqué à sa prochaine " +
                "mise à jour (automatique en quittant l'app).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = filter.libraryId == null,
                onClick = { filter.set(null) },
                label = { Text("Toutes") },
            )
            libraries.forEach { library ->
                FilterChip(
                    selected = filter.libraryId == library.id.value,
                    onClick = { filter.set(library.id.value) },
                    label = { Text(library.name) },
                )
            }
        }
    }
}
