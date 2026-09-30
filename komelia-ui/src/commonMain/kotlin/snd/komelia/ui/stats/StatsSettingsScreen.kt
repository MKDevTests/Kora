package snd.komelia.ui.stats

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import snd.komelia.settings.CommonSettingsRepository
import snd.komelia.ui.LocalLibraries
import snd.komelia.ui.LocalStrings
import snd.komelia.ui.LocalViewModelFactory
import snd.komelia.ui.settings.SettingsScreenContainer
import snd.komelia.ui.settings.components.SettingsCard
import snd.komelia.ui.settings.components.SettingsNavRow
import snd.komelia.ui.settings.components.SettingsRowDivider
import snd.komelia.ui.settings.components.SettingsSwitchRow
import snd.komelia.ui.settings.components.settingsTint

/**
 * Which libraries the reading statistics count. A library switched off leaves
 * every figure: the Komga counts and "recently read" through a server-side
 * condition, the local log (week, month, streak, pages, charts) through the
 * library recorded with each finished book.
 */
class StatsSettingsScreen : Screen {

    @Composable
    override fun Content() {
        val factory = LocalViewModelFactory.current
        val vm = rememberScreenModel { factory.getStatsSettingsViewModel() }
        val excluded by vm.excludedLibraryIds.collectAsState()
        val libraries = LocalLibraries.current.collectAsState().value
        val s = LocalStrings.current.ui

        SettingsScreenContainer(s.statsSettings) {
            SettingsCard(s.statsLibrariesCounted) {
                libraries.forEachIndexed { index, library ->
                    if (index > 0) SettingsRowDivider()
                    SettingsSwitchRow(
                        label = library.name,
                        checked = library.id.value !in excluded,
                        onCheckedChange = { vm.setCounted(library.id.value, it) },
                    )
                }
            }
            Text(
                s.statsLibrariesCountedDesc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
            Text(
                s.statsPagesCarryoverNote,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
        }
    }
}

class StatsSettingsViewModel(
    private val settingsRepository: CommonSettingsRepository,
) : ScreenModel {
    val excludedLibraryIds: StateFlow<Set<String>> = settingsRepository.getStatsExcludedLibraryIds()
        .stateIn(screenModelScope, SharingStarted.Eagerly, emptySet())

    fun setCounted(libraryId: String, counted: Boolean) {
        val current = excludedLibraryIds.value
        val next = if (counted) current - libraryId else current + libraryId
        if (next == current) return
        screenModelScope.launch {
            settingsRepository.putStatsExcludedLibraryIds(next)
            // The home card would otherwise keep the old figures for hours.
            ReadingStatsCache.invalidate()
        }
    }
}

/**
 * The way into [StatsSettingsScreen] from the statistics page itself, shown
 * whatever the page holds: with every library switched off the page is empty,
 * and this row must still be there to switch one back on.
 */
@Composable
fun StatsSettingsEntry(excludedCount: Int) {
    val navigator = LocalNavigator.currentOrThrow
    val s = LocalStrings.current.ui
    SettingsCard {
        SettingsNavRow(
            label = s.statsSettings,
            summary = if (excludedCount == 0) s.statsAllLibraries else s.statsLibrariesExcluded(excludedCount),
            icon = Icons.Rounded.Tune,
            iconTint = settingsTint(MaterialTheme.colorScheme.primary),
            onClick = { navigator.push(StatsSettingsScreen()) },
        )
    }
}
