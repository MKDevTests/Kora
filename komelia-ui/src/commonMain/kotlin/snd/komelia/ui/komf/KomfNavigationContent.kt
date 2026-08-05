package snd.komelia.ui.komf

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import snd.komelia.ui.login.LoginScreen
import snd.komelia.ui.settings.komf.general.KomfSettingsScreen
import snd.komelia.ui.settings.komf.jobs.KomfJobsScreen
import snd.komelia.ui.settings.komf.notifications.KomfNotificationSettingsScreen
import snd.komelia.ui.settings.komf.processing.KomfProcessingSettingsScreen
import snd.komelia.ui.settings.komf.providers.KomfProvidersSettingsScreen
import snd.komelia.ui.settings.navigation.SettingsGroup
import snd.komelia.ui.settings.navigation.SettingsListItem
import snd.komf.api.MediaServer.KAVITA
import snd.komf.api.MediaServer.KOMGA
import snd.komelia.ui.LocalStrings

@Composable
fun KomfNavigationContent(
    currentScreen: Screen,
    onNavigation: (Screen) -> Unit = {},
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        SettingsGroup {
            SettingsListItem(
                label = LocalStrings.current.ui.komgaWebui,
                onClick = { onNavigation(LoginScreen()) },
                isSelected = currentScreen is LoginScreen,
            )
        }

        SettingsGroup(title = LocalStrings.current.ui.komfSettings) {
            SettingsListItem(
                label = LocalStrings.current.ui.connection,
                onClick = { onNavigation(KomfSettingsScreen(integrationToggleEnabled = false, showKavitaSettings = true)) },
                isSelected = currentScreen is KomfSettingsScreen,
            )
            HorizontalDivider()
            SettingsListItem(
                label = LocalStrings.current.ui.komgaProcessing,
                onClick = { onNavigation(KomfProcessingSettingsScreen(KOMGA)) },
                isSelected = currentScreen is KomfProcessingSettingsScreen && currentScreen.serverType == KOMGA,
            )
            HorizontalDivider()
            SettingsListItem(
                label = LocalStrings.current.ui.kavitaProcessing,
                onClick = { onNavigation(KomfProcessingSettingsScreen(KAVITA)) },
                isSelected = currentScreen is KomfProcessingSettingsScreen && currentScreen.serverType == KAVITA,
            )
            HorizontalDivider()
            SettingsListItem(
                label = LocalStrings.current.ui.providers,
                onClick = { onNavigation(KomfProvidersSettingsScreen()) },
                isSelected = currentScreen is KomfProvidersSettingsScreen,
            )
            HorizontalDivider()
            SettingsListItem(
                label = LocalStrings.current.ui.notifications,
                onClick = { onNavigation(KomfNotificationSettingsScreen()) },
                isSelected = currentScreen is KomfNotificationSettingsScreen,
            )
            HorizontalDivider()
            SettingsListItem(
                label = LocalStrings.current.ui.jobHistory,
                onClick = { onNavigation(KomfJobsScreen(false)) },
                isSelected = currentScreen is KomfJobsScreen,
            )
        }
    }
}
