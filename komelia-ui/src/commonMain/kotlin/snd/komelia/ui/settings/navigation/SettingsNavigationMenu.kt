package snd.komelia.ui.settings.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import snd.komelia.ui.LocalOfflineMode
import snd.komelia.ui.LocalTransparentNavBarPadding
import snd.komelia.ui.dialogs.ConfirmationDialog
import snd.komelia.ui.settings.account.AccountSettingsScreen
import snd.komelia.ui.settings.analysis.MediaAnalysisScreen
import snd.komelia.ui.settings.announcements.AnnouncementsScreen
import snd.komelia.ui.settings.appearance.AppSettingsScreen
import snd.komelia.ui.settings.backup.BackupSettingsScreen
import snd.komelia.ui.settings.diagnostics.DiagnosticsScreen
import snd.komelia.ui.settings.experimental.ExperimentalSettingsScreen
import snd.komelia.ui.settings.experimental.IgnoreListScreen
import snd.komelia.ui.settings.experimental.HiddenSeriesScreen
import snd.komelia.ui.settings.navigation.NavigationSettingsScreen
import snd.komelia.ui.settings.servers.AppServerManagementScreen
import snd.komelia.ui.settings.authactivity.AuthenticationActivityScreen
import snd.komelia.ui.settings.epub.EpubReaderSettingsScreen
import snd.komelia.ui.settings.transcription.TranscriptionSettingsScreen
import snd.komelia.ui.settings.imagereader.ImageReaderSettingsScreen
import snd.komelia.ui.settings.komf.general.KomfSettingsScreen
import snd.komelia.ui.settings.komf.jobs.KomfJobsScreen
import snd.komelia.ui.settings.komf.notifications.KomfNotificationSettingsScreen
import snd.komelia.ui.settings.komf.processing.KomfProcessingSettingsScreen
import snd.komelia.ui.settings.komf.providers.KomfProvidersSettingsScreen
import snd.komelia.ui.settings.offline.OfflineSettingsScreen
import snd.komelia.ui.settings.server.ServerSettingsScreen
import snd.komelia.ui.settings.updates.AppUpdatesScreen
import snd.komelia.ui.settings.users.UsersScreen
import snd.komf.api.MediaServer.KOMGA
import snd.komga.client.user.KomgaUser
import snd.webview.webviewIsAvailable

private data class NavEntry(
    val label: String,
    val isSelected: Boolean,
    val trailingContent: (@Composable () -> Unit)? = null,
    val onClick: () -> Unit,
)

@Composable
fun SettingsNavigationMenu(
    hasMediaErrors: Boolean,
    komfEnabled: Boolean,
    updatesEnabled: Boolean,
    newVersionIsAvailable: Boolean,
    currentScreen: Screen,
    onNavigation: (Screen) -> Unit = {},
    onLogout: () -> Unit,
    user: KomgaUser?,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    val isAdmin = remember(user) { user?.roleAdmin() ?: true }
    val isOffline = LocalOfflineMode.current.collectAsState().value
    var query by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        SettingsSearchField(query = query, onQueryChange = { query = it })

        FilteredSettingsGroup(
            title = "App Settings",
            query = query,
            entries = buildList {
                add(
                    NavEntry(
                        label = "Appearance",
                        onClick = { onNavigation(AppSettingsScreen()) },
                        isSelected = currentScreen is AppSettingsScreen,
                    )
                )
                add(
                    NavEntry(
                        label = "Navigation",
                        onClick = { onNavigation(NavigationSettingsScreen()) },
                        isSelected = currentScreen is NavigationSettingsScreen,
                    )
                )
                add(
                    NavEntry(
                        label = "Connected Servers",
                        onClick = { onNavigation(AppServerManagementScreen()) },
                        isSelected = currentScreen is AppServerManagementScreen,
                    )
                )
                add(
                    NavEntry(
                        label = "Image Reader",
                        onClick = { onNavigation(ImageReaderSettingsScreen()) },
                        isSelected = currentScreen is ImageReaderSettingsScreen,
                    )
                )
                if (webviewIsAvailable()) {
                    add(
                        NavEntry(
                            label = "Epub Reader",
                            onClick = { onNavigation(EpubReaderSettingsScreen()) },
                            isSelected = currentScreen is EpubReaderSettingsScreen,
                        )
                    )
                }
                add(
                    NavEntry(
                        label = "Transcription",
                        onClick = { onNavigation(TranscriptionSettingsScreen()) },
                        isSelected = currentScreen is TranscriptionSettingsScreen,
                    )
                )
                if (updatesEnabled) {
                    add(
                        NavEntry(
                            label = "Updates",
                            onClick = { onNavigation(AppUpdatesScreen()) },
                            isSelected = currentScreen is AppUpdatesScreen,
                            trailingContent = if (newVersionIsAvailable) {
                                { ErrorIndicator() }
                            } else null
                        )
                    )
                }
                add(
                    NavEntry(
                        label = "Offline Mode",
                        onClick = { onNavigation(OfflineSettingsScreen()) },
                        isSelected = currentScreen is OfflineSettingsScreen,
                    )
                )
                add(
                    NavEntry(
                        label = "Backup & Restore",
                        onClick = { onNavigation(BackupSettingsScreen()) },
                        isSelected = currentScreen is BackupSettingsScreen,
                    )
                )
                add(
                    NavEntry(
                        label = "Diagnostics",
                        onClick = { onNavigation(DiagnosticsScreen()) },
                        isSelected = currentScreen is DiagnosticsScreen,
                    )
                )
            }
        )

        FilteredSettingsGroup(
            title = "Experimental",
            query = query,
            entries = buildList {
                add(
                    NavEntry(
                        label = "Genre tab",
                        onClick = { onNavigation(ExperimentalSettingsScreen()) },
                        isSelected = currentScreen is ExperimentalSettingsScreen,
                    )
                )
                add(
                    NavEntry(
                        label = "Ignore List",
                        onClick = { onNavigation(IgnoreListScreen()) },
                        isSelected = currentScreen is IgnoreListScreen,
                    )
                )
                if (isAdmin) {
                    add(
                        NavEntry(
                            label = "Séries masquées",
                            onClick = { onNavigation(HiddenSeriesScreen()) },
                            isSelected = currentScreen is HiddenSeriesScreen,
                        )
                    )
                }
            }
        )

        if (!isOffline) {
            FilteredSettingsGroup(
                title = "User Settings",
                query = query,
                entries = buildList {
                    add(
                        NavEntry(
                            label = "My Account",
                            onClick = { onNavigation(AccountSettingsScreen()) },
                            isSelected = currentScreen is AccountSettingsScreen,
                        )
                    )
                    add(
                        NavEntry(
                            label = "My Authentication Activity",
                            onClick = { onNavigation(AuthenticationActivityScreen(true)) },
                            isSelected = currentScreen is AuthenticationActivityScreen && currentScreen.forMe,
                        )
                    )
                }
            )

            if (isAdmin) {
                FilteredSettingsGroup(
                    title = "Server Settings",
                    query = query,
                    entries = buildList {
                        add(
                            NavEntry(
                                label = "General",
                                onClick = { onNavigation(ServerSettingsScreen()) },
                                isSelected = currentScreen is ServerSettingsScreen,
                            )
                        )
                        add(
                            NavEntry(
                                label = "Users",
                                onClick = { onNavigation(UsersScreen()) },
                                isSelected = currentScreen is UsersScreen,
                            )
                        )
                        add(
                            NavEntry(
                                label = "Authentication Activity",
                                onClick = { onNavigation(AuthenticationActivityScreen(false)) },
                                isSelected = currentScreen is AuthenticationActivityScreen && !currentScreen.forMe,
                            )
                        )
                        add(
                            NavEntry(
                                label = "Media Management",
                                onClick = { onNavigation(MediaAnalysisScreen()) },
                                isSelected = currentScreen is MediaAnalysisScreen,
                                trailingContent = if (hasMediaErrors) {
                                    { ErrorIndicator() }
                                } else null
                            )
                        )
                        add(
                            NavEntry(
                                label = "Announcements",
                                onClick = { onNavigation(AnnouncementsScreen()) },
                                isSelected = currentScreen is AnnouncementsScreen,
                            )
                        )
                    }
                )
            }

            if (isAdmin) {
                FilteredSettingsGroup(
                    title = "Komf Settings",
                    query = query,
                    entries = buildList {
                        add(
                            NavEntry(
                                label = "Connection",
                                onClick = { onNavigation(KomfSettingsScreen()) },
                                isSelected = currentScreen is KomfSettingsScreen,
                            )
                        )
                        if (komfEnabled) {
                            add(
                                NavEntry(
                                    label = "Processing",
                                    onClick = { onNavigation(KomfProcessingSettingsScreen(KOMGA)) },
                                    isSelected = currentScreen is KomfProcessingSettingsScreen,
                                )
                            )
                            add(
                                NavEntry(
                                    label = "Providers",
                                    onClick = { onNavigation(KomfProvidersSettingsScreen()) },
                                    isSelected = currentScreen is KomfProvidersSettingsScreen,
                                )
                            )
                            add(
                                NavEntry(
                                    label = "Notifications",
                                    onClick = { onNavigation(KomfNotificationSettingsScreen()) },
                                    isSelected = currentScreen is KomfNotificationSettingsScreen,
                                )
                            )
                            add(
                                NavEntry(
                                    label = "Job History",
                                    onClick = { onNavigation(KomfJobsScreen()) },
                                    isSelected = currentScreen is KomfJobsScreen,
                                )
                            )
                        }
                    }
                )
            }
        }

        var showLogoutConfirmation by remember { mutableStateOf(false) }
        FilteredSettingsGroup(
            title = "Actions",
            query = query,
            entries = listOf(
                NavEntry(
                    label = "Log Out",
                    onClick = { showLogoutConfirmation = true },
                    isSelected = false,
                )
            )
        )

        Spacer(Modifier.height(LocalTransparentNavBarPadding.current))

        if (showLogoutConfirmation) {
            ConfirmationDialog(
                title = "Log Out",
                body = "Are you sure you want to logout?",
                buttonConfirm = "Log Out",
                buttonConfirmColor = MaterialTheme.colorScheme.errorContainer,

                onDialogConfirm = onLogout,
                onDialogDismiss = { showLogoutConfirmation = false })
        }
    }
}

/**
 * A group of [SettingsListItem]s filtered by [query] (case-insensitive
 * substring match on the label). Renders nothing — not even the group
 * title — when no entry in the group matches, so searching doesn't leave
 * empty section headers on screen.
 */
@Composable
private fun FilteredSettingsGroup(
    title: String,
    entries: List<NavEntry>,
    query: String,
) {
    val visible = entries.filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }
    if (visible.isEmpty()) return

    SettingsGroup(title = title) {
        visible.forEachIndexed { index, entry ->
            SettingsListItem(
                label = entry.label,
                onClick = entry.onClick,
                isSelected = entry.isSelected,
                trailingContent = entry.trailingContent,
            )
            if (index != visible.lastIndex) HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        }
    }
}

/** Filters the settings menu above by entry label as the user types. */
@Composable
private fun SettingsSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("Search settings") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotBlank()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear")
                }
            }
        },
        singleLine = true,
        shape = CircleShape,
        colors = OutlinedTextFieldDefaults.colors(),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
    )
}
