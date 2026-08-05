package snd.komelia.ui.settings.servers

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import snd.komelia.settings.model.ServerProfile
import snd.komelia.ui.LocalViewModelFactory
import snd.komelia.ui.dialogs.ConfirmationDialog
import snd.komelia.ui.login.LoginScreen
import snd.komelia.ui.settings.SettingsScreenContainer
import snd.komelia.ui.LocalStrings

class AppServerManagementScreen : Screen {

    @Composable
    override fun Content() {
        val rootNavigator = LocalNavigator.currentOrThrow.parent ?: LocalNavigator.currentOrThrow
        val viewModelFactory = LocalViewModelFactory.current
        val vm = rememberScreenModel { viewModelFactory.getAppServerManagementViewModel() }
        val serverProfiles by vm.serverProfiles.collectAsState(emptyList())
        val currentServer by vm.currentServer.collectAsState()
        val activeUrl by vm.activeServerUrl.collectAsState()
        val alternateUrls by vm.alternateServerUrls.collectAsState()

        SettingsScreenContainer(title = LocalStrings.current.ui.manageConnectedServers) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                serverProfiles.forEach { profile ->
                    val isCurrent = profile.id == currentServer?.id
                    ServerProfileItem(
                        profile = profile,
                        isCurrent = isCurrent,
                        displayUrl = if (isCurrent && activeUrl.isNotBlank()) activeUrl else profile.url,
                        onDelete = { vm.deleteServer(profile) },
                        onSwitch = { vm.switchServer(profile) }
                    )
                    if (isCurrent) {
                        AlternateUrlsSection(
                            activeUrl = if (activeUrl.isNotBlank()) activeUrl else profile.url,
                            alternates = alternateUrls,
                            onAdd = vm::addAlternateUrl,
                            onRemove = vm::removeAlternateUrl,
                            onSwitch = vm::switchToUrl,
                        )
                    }
                    HorizontalDivider()
                }

                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = { vm.addNewServer() },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(LocalStrings.current.ui.connectToANewServer)
                }
            }
        }
    }

    @Composable
    private fun ServerProfileItem(
        profile: ServerProfile,
        isCurrent: Boolean,
        displayUrl: String,
        onDelete: () -> Unit,
        onSwitch: () -> Unit
    ) {
        var showDeleteConfirmation by remember { mutableStateOf(false) }

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(profile.name, style = MaterialTheme.typography.titleMedium)
                Text(displayUrl, style = MaterialTheme.typography.bodyMedium)
                Text("User: ${profile.username}", style = MaterialTheme.typography.bodySmall)
            }

            if (isCurrent) {
                SuggestionChip(
                    onClick = {},
                    label = { Text(LocalStrings.current.ui.current2) },
                    enabled = false
                )
            } else {
                Button(onClick = onSwitch) {
                    Text(LocalStrings.current.ui.switchToThisServer)
                }
            }

            IconButton(onClick = { showDeleteConfirmation = true }) {
                Icon(Icons.Default.Delete, contentDescription = LocalStrings.current.ui.deleteServer)
            }
        }

        if (showDeleteConfirmation) {
            ConfirmationDialog(
                title = LocalStrings.current.ui.deleteServerProfile,
                body = "Are you sure you want to delete the profile for ${profile.name}? This will also delete all local settings and offline data associated with this server.",
                buttonConfirm = "Delete",
                buttonConfirmColor = MaterialTheme.colorScheme.error,
                onDialogConfirm = onDelete,
                onDialogDismiss = { showDeleteConfirmation = false }
            )
        }
    }

    /**
     * Manage the alternate URLs of the *currently connected* server. The
     * active URL is shown first; spares can be switched to or removed, and new
     * ones added. All URLs share the same server profile and per-server DB, so
     * reading stats, ratings and links stay unified whichever address is used.
     */
    @Composable
    private fun AlternateUrlsSection(
        activeUrl: String,
        alternates: List<String>,
        onAdd: (String) -> Unit,
        onRemove: (String) -> Unit,
        onSwitch: (String) -> Unit,
    ) {
        var newUrl by remember { mutableStateOf("") }
        var pendingSwitch by remember { mutableStateOf<String?>(null) }

        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(LocalStrings.current.ui.alternateUrlsForThisServer, style = MaterialTheme.typography.titleSmall)
            Text(
                LocalStrings.current.ui.addOtherAddressesThatReach,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SuggestionChip(onClick = {}, enabled = false, label = { Text(LocalStrings.current.ui.active2) })
                Text(
                    activeUrl,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            alternates.forEach { url ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        url,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { pendingSwitch = url }) { Text(LocalStrings.current.ui.switch) }
                    IconButton(onClick = { onRemove(url) }) {
                        Icon(Icons.Default.Delete, contentDescription = LocalStrings.current.ui.removeUrl)
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = newUrl,
                    onValueChange = { newUrl = it },
                    label = { Text("http://192.168.x.x:25600 or https://…") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        onAdd(newUrl)
                        newUrl = ""
                    },
                    enabled = newUrl.isNotBlank()
                ) {
                    Icon(Icons.Default.Add, contentDescription = LocalStrings.current.ui.addUrl)
                }
            }
        }

        pendingSwitch?.let { target ->
            ConfirmationDialog(
                title = LocalStrings.current.ui.switchActiveUrl,
                body = "Reconnect to the same server using:\n\n$target\n\nYour stats, ratings and links stay unified.",
                buttonConfirm = "Switch",
                onDialogConfirm = {
                    onSwitch(target)
                    pendingSwitch = null
                },
                onDialogDismiss = { pendingSwitch = null }
            )
        }
    }
}
