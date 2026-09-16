package snd.komelia.ui.settings.servers

import snd.komelia.ui.common.components.KoraChipDefaults
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
                        onRename = { vm.renameServer(profile, it) },
                        onSwitch = { vm.switchServer(profile) }
                    )
                    if (isCurrent) {
                        AlternateUrlsSection(
                            activeUrl = if (activeUrl.isNotBlank()) activeUrl else profile.url,
                            alternates = alternateUrls,
                            onAdd = vm::addAlternateUrl,
                            onRemove = vm::removeAlternateUrl,
                            onSwitch = vm::switchToUrl,
                            onTest = vm::testUrl,
                            probeResults = vm.probeResults.collectAsState().value,
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
        onRename: (String) -> Unit,
        onSwitch: () -> Unit
    ) {
        var showDeleteConfirmation by remember { mutableStateOf(false) }
        var showRename by remember { mutableStateOf(false) }
        val strings = LocalStrings.current.ui
        val confirm = LocalStrings.current.confirm

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
                    enabled = false,
                    colors = KoraChipDefaults.suggestionChipColors(),
                    border = KoraChipDefaults.border,
                )
            } else {
                Button(onClick = onSwitch) {
                    Text(LocalStrings.current.ui.switchToThisServer)
                }
            }

            IconButton(onClick = { showRename = true }) {
                Icon(Icons.Default.Edit, contentDescription = strings.renameServer)
            }
            IconButton(onClick = { showDeleteConfirmation = true }) {
                Icon(Icons.Default.Delete, contentDescription = strings.deleteServer)
            }
        }

        if (showRename) {
            RenameServerDialog(
                current = profile.name,
                onConfirm = { onRename(it); showRename = false },
                onDismiss = { showRename = false },
            )
        }

        if (showDeleteConfirmation) {
            ConfirmationDialog(
                title = strings.deleteServerProfile,
                body = confirm.deleteServerProfile(profile.name),
                buttonConfirm = strings.delete,
                buttonConfirmColor = MaterialTheme.colorScheme.error,
                onDialogConfirm = onDelete,
                onDialogDismiss = { showDeleteConfirmation = false }
            )
        }
    }

    /**
     * The profile's label is the URL typed at the first login and nothing ever
     * changed it -- so after moving the server to another address through the
     * alternates below, the old IP stayed as the title. The label is internal
     * to the app: no request uses it.
     */
    @Composable
    private fun RenameServerDialog(
        current: String,
        onConfirm: (String) -> Unit,
        onDismiss: () -> Unit,
    ) {
        val strings = LocalStrings.current.ui
        var name by remember(current) { mutableStateOf(current) }
        val valid = name.isNotBlank()
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(strings.renameServer) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(strings.serverName) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = { onConfirm(name.trim()) }, enabled = valid) { Text(strings.save) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(strings.cancel) }
            },
        )
    }

    /**
     * Manage the alternate URLs of the *currently connected* server. The
     * active URL is shown first; spares can be switched to or removed, and new
     * ones added. All URLs share the same server profile and per-server DB, so
     * reading stats, ratings and links stay unified whichever address is used.
     */
    /**
     * "Test" next to an address, then what the probe found: "reachable, 84 ms"
     * or "unreachable (ConnectTimeoutException, 5 s)". The same probe the
     * automatic failover runs, so the answer is the one that would decide a
     * switch.
     */
    @Composable
    private fun ProbeButton(
        url: String,
        probeResults: Map<String, snd.komelia.failover.ServerProbe.Result?>,
        onTest: (String) -> Unit,
    ) {
        val strings = LocalStrings.current
        if (url !in probeResults) {
            TextButton(onClick = { onTest(url) }) { Text(strings.ui.testUrl) }
            return
        }
        when (val result = probeResults[url]) {
            null -> androidx.compose.material3.CircularProgressIndicator(
                modifier = Modifier.padding(horizontal = 12.dp).size(18.dp),
                strokeWidth = 2.dp,
            )

            is snd.komelia.failover.ServerProbe.Result.Reachable -> TextButton(onClick = { onTest(url) }) {
                Text(strings.counts.urlReachable(result.millis), color = MaterialTheme.colorScheme.primary)
            }

            is snd.komelia.failover.ServerProbe.Result.Unreachable -> TextButton(onClick = { onTest(url) }) {
                Text(strings.counts.urlUnreachable(result.reason, result.millis), color = MaterialTheme.colorScheme.error)
            }
        }
    }

    @Composable
    private fun AlternateUrlsSection(
        activeUrl: String,
        alternates: List<String>,
        onAdd: (String) -> Unit,
        onRemove: (String) -> Unit,
        onSwitch: (String) -> Unit,
        onTest: (String) -> Unit,
        probeResults: Map<String, snd.komelia.failover.ServerProbe.Result?>,
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
                SuggestionChip(onClick = {}, enabled = false, label = { Text(LocalStrings.current.ui.active2) }, colors = KoraChipDefaults.suggestionChipColors(), border = KoraChipDefaults.border)
                Text(
                    activeUrl,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                ProbeButton(activeUrl, probeResults, onTest)
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
                    ProbeButton(url, probeResults, onTest)
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
                body = LocalStrings.current.confirm.switchActiveUrl(target),
                buttonConfirm = LocalStrings.current.ui.switch,
                onDialogConfirm = {
                    onSwitch(target)
                    pendingSwitch = null
                },
                onDialogDismiss = { pendingSwitch = null }
            )
        }
    }
}
