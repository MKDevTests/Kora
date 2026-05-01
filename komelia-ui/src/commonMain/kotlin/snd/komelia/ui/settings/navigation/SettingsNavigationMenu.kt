package snd.komelia.ui.settings.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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

    Column(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        SettingsGroup(title = "App Settings") {
            SettingsListItem(
                label = "Appearance",
                onClick = { onNavigation(AppSettingsScreen()) },
                isSelected = currentScreen is AppSettingsScreen,
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            SettingsListItem(
                label = "Connected Servers",
                onClick = { onNavigation(AppServerManagementScreen()) },
                isSelected = currentScreen is AppServerManagementScreen,
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            SettingsListItem(
                label = "Image Reader",
                onClick = { onNavigation(ImageReaderSettingsScreen()) },
                isSelected = currentScreen is ImageReaderSettingsScreen,
            )
            if (webviewIsAvailable()) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingsListItem(
                    label = "Epub Reader",
                    onClick = { onNavigation(EpubReaderSettingsScreen()) },
                    isSelected = currentScreen is EpubReaderSettingsScreen,
                )
            }
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            SettingsListItem(
                label = "Transcription",
                onClick = { onNavigation(TranscriptionSettingsScreen()) },
                isSelected = currentScreen is TranscriptionSettingsScreen,
            )
            if (updatesEnabled) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingsListItem(
                    label = "Updates",
                    onClick = { onNavigation(AppUpdatesScreen()) },
                    isSelected = currentScreen is AppUpdatesScreen,
                    trailingContent = if (newVersionIsAvailable) {
                        { ErrorIndicator() }
                    } else null
                )
            }
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            SettingsListItem(
                label = "Offline Mode",
                onClick = { onNavigation(OfflineSettingsScreen()) },
                isSelected = currentScreen is OfflineSettingsScreen,
            )
        }

        if (!isOffline) {
            SettingsGroup(title = "User Settings") {
                SettingsListItem(
                    label = "My Account",
                    onClick = { onNavigation(AccountSettingsScreen()) },
                    isSelected = currentScreen is AccountSettingsScreen,
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingsListItem(
                    label = "My Authentication Activity",
                    onClick = { onNavigation(AuthenticationActivityScreen(true)) },
                    isSelected = currentScreen is AuthenticationActivityScreen && currentScreen.forMe,
                )
            }

            if (isAdmin) {
                SettingsGroup(title = "Server Settings") {
                    SettingsListItem(
                        label = "General",
                        onClick = { onNavigation(ServerSettingsScreen()) },
                        isSelected = currentScreen is ServerSettingsScreen,
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsListItem(
                        label = "Users",
                        onClick = { onNavigation(UsersScreen()) },
                        isSelected = currentScreen is UsersScreen,
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsListItem(
                        label = "Authentication Activity",
                        onClick = { onNavigation(AuthenticationActivityScreen(false)) },
                        isSelected = currentScreen is AuthenticationActivityScreen && !currentScreen.forMe,
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsListItem(
                        label = "Media Management",
                        onClick = { onNavigation(MediaAnalysisScreen()) },
                        isSelected = currentScreen is MediaAnalysisScreen,
                        trailingContent = if (hasMediaErrors) {
                            { ErrorIndicator() }
                        } else null
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsListItem(
                        label = "Announcements",
                        onClick = { onNavigation(AnnouncementsScreen()) },
                        isSelected = currentScreen is AnnouncementsScreen,
                    )
                }
            }

            if (isAdmin) {
                SettingsGroup(title = "Komf Settings") {
                    SettingsListItem(
                        label = "Connection",
                        onClick = { onNavigation(KomfSettingsScreen()) },
                        isSelected = currentScreen is KomfSettingsScreen,
                    )
                    if (komfEnabled) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsListItem(
                            label = "Processing",
                            onClick = { onNavigation(KomfProcessingSettingsScreen(KOMGA)) },
                            isSelected = currentScreen is KomfProcessingSettingsScreen,
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsListItem(
                            label = "Providers",
                            onClick = { onNavigation(KomfProvidersSettingsScreen()) },
                            isSelected = currentScreen is KomfProvidersSettingsScreen,
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsListItem(
                            label = "Notifications",
                            onClick = { onNavigation(KomfNotificationSettingsScreen()) },
                            isSelected = currentScreen is KomfNotificationSettingsScreen,
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsListItem(
                            label = "Job History",
                            onClick = { onNavigation(KomfJobsScreen()) },
                            isSelected = currentScreen is KomfJobsScreen,
                        )
                    }
                }
            }
        }

        var showLogoutConfirmation by remember { mutableStateOf(false) }
        SettingsGroup(title = "Actions") {
            SettingsListItem(
                label = "Log Out",
                onClick = { showLogoutConfirmation = true },
                isSelected = false,
            )
        }

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
