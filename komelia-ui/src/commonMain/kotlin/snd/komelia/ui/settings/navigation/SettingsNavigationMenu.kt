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
import snd.komelia.ui.settings.chapters.ChapterManagementScreen
import snd.komelia.ui.settings.account.AccountSettingsScreen
import snd.komelia.ui.settings.analysis.MediaAnalysisScreen
import snd.komelia.ui.settings.announcements.AnnouncementsScreen
import snd.komelia.ui.settings.appearance.AppSettingsScreen
import snd.komelia.ui.settings.backup.BackupSettingsScreen
import snd.komelia.ui.settings.diagnostics.DiagnosticsScreen
import snd.komelia.ui.settings.experimental.ExperimentalSettingsScreen
import snd.komelia.ui.settings.experimental.IgnoreListScreen
import snd.komelia.ui.settings.experimental.HiddenSeriesScreen
import snd.komelia.ui.settings.maintenance.MaintenanceScreen
import snd.komelia.ui.settings.toolkit.ToolkitScreen
import snd.komelia.ui.settings.navigation.NavigationSettingsScreen
import snd.komelia.ui.settings.servers.AppServerManagementScreen
import snd.komelia.ui.settings.authactivity.AuthenticationActivityScreen
import snd.komelia.ui.settings.epub.EpubReaderSettingsScreen
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
import snd.komelia.chapters.normalizeForMatch
import snd.komelia.ui.LocalStrings

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
            title = LocalStrings.current.ui.appSettings,
            query = query,
            entries = buildList {
                add(
                    NavEntry(
                        label = LocalStrings.current.ui.appearance,
                        onClick = { onNavigation(AppSettingsScreen()) },
                        isSelected = currentScreen is AppSettingsScreen,
                    )
                )
                add(
                    NavEntry(
                        label = LocalStrings.current.ui.navigation,
                        onClick = { onNavigation(NavigationSettingsScreen()) },
                        isSelected = currentScreen is NavigationSettingsScreen,
                    )
                )
                add(
                    NavEntry(
                        label = LocalStrings.current.ui.connectedServers,
                        onClick = { onNavigation(AppServerManagementScreen()) },
                        isSelected = currentScreen is AppServerManagementScreen,
                    )
                )
                add(
                    NavEntry(
                        label = LocalStrings.current.ui.imageReader,
                        onClick = { onNavigation(ImageReaderSettingsScreen()) },
                        isSelected = currentScreen is ImageReaderSettingsScreen,
                    )
                )
                if (webviewIsAvailable()) {
                    add(
                        NavEntry(
                            label = LocalStrings.current.ui.epubReader,
                            onClick = { onNavigation(EpubReaderSettingsScreen()) },
                            isSelected = currentScreen is EpubReaderSettingsScreen,
                        )
                    )
                }
                if (updatesEnabled) {
                    add(
                        NavEntry(
                            label = LocalStrings.current.ui.updates,
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
                        label = LocalStrings.current.ui.offlineMode2,
                        onClick = { onNavigation(OfflineSettingsScreen()) },
                        isSelected = currentScreen is OfflineSettingsScreen,
                    )
                )
                add(
                    NavEntry(
                        label = LocalStrings.current.ui.backupRestore2,
                        onClick = { onNavigation(BackupSettingsScreen()) },
                        isSelected = currentScreen is BackupSettingsScreen,
                    )
                )
                add(
                    NavEntry(
                        label = LocalStrings.current.ui.diagnostics,
                        onClick = { onNavigation(DiagnosticsScreen()) },
                        isSelected = currentScreen is DiagnosticsScreen,
                    )
                )
            }
        )

        FilteredSettingsGroup(
            // Not "Experimental" any more: the genre tab, the ignore list and
            // hidden series all shipped and all answer the same question --
            // what shows up in the library. Not "Administration" either: two
            // of the three are every user's business.
            title = LocalStrings.current.ui.contentSettings,
            query = query,
            entries = buildList {
                add(
                    NavEntry(
                        label = LocalStrings.current.ui.genreTab,
                        onClick = { onNavigation(ExperimentalSettingsScreen()) },
                        isSelected = currentScreen is ExperimentalSettingsScreen,
                    )
                )
                add(
                    NavEntry(
                        label = LocalStrings.current.ui.ignoreList,
                        onClick = { onNavigation(IgnoreListScreen()) },
                        isSelected = currentScreen is IgnoreListScreen,
                    )
                )
                if (isAdmin) {
                    add(
                        NavEntry(
                            label = LocalStrings.current.ui.sRiesMasquEs,
                            onClick = { onNavigation(HiddenSeriesScreen()) },
                            isSelected = currentScreen is HiddenSeriesScreen,
                        )
                    )
                }
            }
        )

        // Whole group hidden from non-admins: these tools write server-side
        // metadata that Komga rejects for them anyway (403).
        if (isAdmin) {
            FilteredSettingsGroup(
                title = LocalStrings.current.ui.admin,
                query = query,
                entries = buildList {
                    add(
                        NavEntry(
                            label = LocalStrings.current.ui.maintenance,
                            onClick = { onNavigation(MaintenanceScreen()) },
                            isSelected = currentScreen is MaintenanceScreen,
                        )
                    )
                    add(
                        NavEntry(
                            label = LocalStrings.current.ui.komgaToolkit,
                            onClick = { onNavigation(ToolkitScreen()) },
                            isSelected = currentScreen is ToolkitScreen,
                        )
                    )
                    add(
                        NavEntry(
                            label = LocalStrings.current.ui.chapterManagement,
                            onClick = { onNavigation(ChapterManagementScreen()) },
                            isSelected = currentScreen is ChapterManagementScreen,
                        )
                    )
                }
            )
        }

        if (!isOffline) {
            FilteredSettingsGroup(
                title = LocalStrings.current.ui.userSettings,
                query = query,
                entries = buildList {
                    add(
                        NavEntry(
                            label = LocalStrings.current.ui.myAccount,
                            onClick = { onNavigation(AccountSettingsScreen()) },
                            isSelected = currentScreen is AccountSettingsScreen,
                        )
                    )
                    add(
                        NavEntry(
                            label = LocalStrings.current.ui.myAuthenticationActivity,
                            onClick = { onNavigation(AuthenticationActivityScreen(true)) },
                            isSelected = currentScreen is AuthenticationActivityScreen && currentScreen.forMe,
                        )
                    )
                }
            )

            if (isAdmin) {
                FilteredSettingsGroup(
                    title = LocalStrings.current.ui.serverSettings,
                    query = query,
                    entries = buildList {
                        add(
                            NavEntry(
                                label = LocalStrings.current.ui.general,
                                onClick = { onNavigation(ServerSettingsScreen()) },
                                isSelected = currentScreen is ServerSettingsScreen,
                            )
                        )
                        add(
                            NavEntry(
                                label = LocalStrings.current.ui.users,
                                onClick = { onNavigation(UsersScreen()) },
                                isSelected = currentScreen is UsersScreen,
                            )
                        )
                        add(
                            NavEntry(
                                label = LocalStrings.current.ui.authenticationActivity,
                                onClick = { onNavigation(AuthenticationActivityScreen(false)) },
                                isSelected = currentScreen is AuthenticationActivityScreen && !currentScreen.forMe,
                            )
                        )
                        add(
                            NavEntry(
                                label = LocalStrings.current.ui.mediaManagement,
                                onClick = { onNavigation(MediaAnalysisScreen()) },
                                isSelected = currentScreen is MediaAnalysisScreen,
                                trailingContent = if (hasMediaErrors) {
                                    { ErrorIndicator() }
                                } else null
                            )
                        )
                        add(
                            NavEntry(
                                label = LocalStrings.current.ui.announcements,
                                onClick = { onNavigation(AnnouncementsScreen()) },
                                isSelected = currentScreen is AnnouncementsScreen,
                            )
                        )
                    }
                )
            }

            if (isAdmin) {
                FilteredSettingsGroup(
                    title = LocalStrings.current.ui.komfSettings,
                    query = query,
                    entries = buildList {
                        add(
                            NavEntry(
                                label = LocalStrings.current.ui.connection,
                                onClick = { onNavigation(KomfSettingsScreen()) },
                                isSelected = currentScreen is KomfSettingsScreen,
                            )
                        )
                        if (komfEnabled) {
                            add(
                                NavEntry(
                                    label = LocalStrings.current.ui.processing2,
                                    onClick = { onNavigation(KomfProcessingSettingsScreen(KOMGA)) },
                                    isSelected = currentScreen is KomfProcessingSettingsScreen,
                                )
                            )
                            add(
                                NavEntry(
                                    label = LocalStrings.current.ui.providers,
                                    onClick = { onNavigation(KomfProvidersSettingsScreen()) },
                                    isSelected = currentScreen is KomfProvidersSettingsScreen,
                                )
                            )
                            add(
                                NavEntry(
                                    label = LocalStrings.current.ui.notifications,
                                    onClick = { onNavigation(KomfNotificationSettingsScreen()) },
                                    isSelected = currentScreen is KomfNotificationSettingsScreen,
                                )
                            )
                            add(
                                NavEntry(
                                    label = LocalStrings.current.ui.jobHistory,
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
            title = LocalStrings.current.ui.actions,
            query = query,
            entries = listOf(
                NavEntry(
                    label = LocalStrings.current.ui.logOut,
                    onClick = { showLogoutConfirmation = true },
                    isSelected = false,
                )
            )
        )

        Spacer(Modifier.height(LocalTransparentNavBarPadding.current))

        if (showLogoutConfirmation) {
            ConfirmationDialog(
                title = LocalStrings.current.ui.logOut,
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
    // Both sides folded, not just lowercased. Fourteen of this menu's
    // thirty-seven labels carry an accent — "Séries masquées", "Boîte à outils
    // Komga", "Général" — and a soft keyboard does not put one there by
    // accident, so "serie" found nothing at all.
    val needle = normalizeForMatch(query)
    val visible = entries.filter { needle.isEmpty() || normalizeForMatch(it.label).contains(needle) }
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
        placeholder = { Text(LocalStrings.current.ui.searchSettings) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotBlank()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = LocalStrings.current.ui.clear)
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
