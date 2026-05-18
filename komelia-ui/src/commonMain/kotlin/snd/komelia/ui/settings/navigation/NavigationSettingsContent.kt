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
    }
}
