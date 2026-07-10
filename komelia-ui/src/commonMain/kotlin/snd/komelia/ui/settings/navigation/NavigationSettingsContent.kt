package snd.komelia.ui.settings.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import snd.komelia.settings.model.StartupScreen
import snd.komelia.ui.common.components.DropdownChoiceMenu
import snd.komelia.ui.common.components.LabeledEntry
import snd.komelia.ui.common.components.SwitchWithLabel

@Composable
fun NavigationSettingsContent(
    libraryDropdownInTitle: Boolean,
    onLibraryDropdownInTitleChange: (Boolean) -> Unit,
    startupScreen: StartupScreen,
    onStartupScreenChange: (StartupScreen) -> Unit,
    statsEnabled: Boolean,
    onStatsEnabledChange: (Boolean) -> Unit,
    statsInBottomNav: Boolean,
    onStatsInBottomNavChange: (Boolean) -> Unit,
    nextReleasesInBottomNav: Boolean,
    onNextReleasesInBottomNavChange: (Boolean) -> Unit,
    aniListLinkSuggestionsEnabled: Boolean,
    onAniListLinkSuggestionsEnabledChange: (Boolean) -> Unit,
    shareLinksViaKomga: Boolean,
    onShareLinksViaKomgaChange: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Library switcher in title -------------------------------------
        Column {
            SwitchWithLabel(
                label = { Text("Library switcher in page title") },
                checked = libraryDropdownInTitle,
                onCheckedChange = onLibraryDropdownInTitleChange,
            )
            Text(
                text = "When on, tapping 'Home' / library-name at the top of " +
                    "the screen opens a dropdown listing all libraries for " +
                    "one-tap switching. When off, the title is plain text " +
                    "and you switch libraries via the side drawer (☰) only.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp),
            )
        }

        Spacer(Modifier.size(4.dp))

        // Startup screen ------------------------------------------------
        Column {
            val options = remember {
                listOf(
                    LabeledEntry(StartupScreen.HOME, "Home"),
                    LabeledEntry(StartupScreen.LAST_LIBRARY, "Last opened library"),
                )
            }
            val selected = remember(startupScreen) {
                options.first { it.value == startupScreen }
            }
            DropdownChoiceMenu(
                selectedOption = selected,
                options = options,
                onOptionChange = { onStartupScreenChange(it.value) },
                label = { Text("Startup screen") },
                inputFieldModifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "Which screen Kora opens to when you launch the app. " +
                    "'Home' shows your curated landing page; 'Last opened " +
                    "library' jumps straight into the library you were " +
                    "browsing previously.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp, top = 4.dp),
            )
        }

        Spacer(Modifier.size(4.dp))

        // Reading Stats master switch -----------------------------------
        Column {
            SwitchWithLabel(
                label = { Text("Reading stats") },
                checked = statsEnabled,
                onCheckedChange = onStatsEnabledChange,
            )
            Text(
                text = "Tracks book completions and shows your reading " +
                    "activity (books finished, streak, monthly chart). " +
                    "When off, no events are logged and the stats surface " +
                    "is hidden. Lifetime counters always come straight from " +
                    "your Komga server; time-bounded counters start at zero " +
                    "from the moment you enable this.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp),
            )
        }

        // Stats in bottom nav ------------------------------------------
        Column {
            SwitchWithLabel(
                label = { Text("Show stats in bottom navigation") },
                checked = statsInBottomNav,
                onCheckedChange = onStatsInBottomNavChange,
                enabled = statsEnabled,
            )
            Text(
                text = "Adds a dedicated stats button to the bottom navigation " +
                    "bar (next to Home / Search / Library). When off, the stats " +
                    "page is still reachable from the Home screen card.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp),
            )
        }

        // Upcoming releases in bottom nav --------------------------------
        Column {
            SwitchWithLabel(
                label = { Text("Show upcoming releases in bottom navigation") },
                checked = nextReleasesInBottomNav,
                onCheckedChange = onNextReleasesInBottomNavChange,
            )
            Text(
                text = "Adds a dedicated button to the bottom navigation bar for " +
                    "the cross-library upcoming releases calendar. When off, the " +
                    "page is still reachable from the Home screen card.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp),
            )
        }

        // AniList link suggestions (opt-in, online) ---------------------
        Column {
            SwitchWithLabel(
                label = { Text("AniList link suggestions (online)") },
                checked = aniListLinkSuggestionsEnabled,
                onCheckedChange = onAniListLinkSuggestionsEnabledChange,
            )
            Text(
                text = "Adds an \"Analyze with AniList\" button on a series' Links " +
                    "tab that looks up related series (sequel, prequel, spin-off) " +
                    "to suggest — you confirm each link, nothing is added " +
                    "automatically. Off by default: when on, the series title you " +
                    "analyze is sent to the public AniList API (anilist.co). No " +
                    "account or data of yours is stored there.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp),
            )
        }

        // Share series links via Komga (read all, write admin) ----------
        Column {
            SwitchWithLabel(
                label = { Text("Share series links via Komga") },
                checked = shareLinksViaKomga,
                onCheckedChange = onShareLinksViaKomgaChange,
            )
            Text(
                text = "Reads shared series relations from the Komga server (visible to " +
                    "everyone) on top of your private local links. When on, admins publish " +
                    "their links to the server; non-admins keep writing locally. Off by " +
                    "default: everything stays local on this device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}
