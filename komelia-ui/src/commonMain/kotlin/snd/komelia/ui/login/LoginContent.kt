package snd.komelia.ui.login

import androidx.compose.foundation.clickable
import snd.komelia.ui.KoraShapes
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import snd.komelia.settings.model.ServerProfile
import snd.komelia.ui.LocalPlatform
import snd.komelia.ui.common.components.DropdownChoiceMenu
import snd.komelia.ui.common.components.LabeledEntry
import snd.komelia.ui.common.components.ServerAddressFields
import snd.komelia.ui.common.components.withTextFieldNavigation
import snd.komelia.ui.platform.PlatformType
import snd.komelia.ui.platform.PlatformType.DESKTOP
import snd.komelia.ui.platform.PlatformType.MOBILE
import snd.komelia.ui.platform.cursorForHand
import snd.komelia.ui.LocalStrings


@Composable
fun LoginContent(
    viewModel: LoginViewModel,
    onOfflineSelect: () -> Unit,
) {
    val autoLoginError = viewModel.autoLoginError
    val canGoOfflineAsCurrentUser by viewModel.canGoOfflineAsCurrentUser.collectAsState(false)
    val goOfflineAsCurrentUser = viewModel::offlineLogin
    val onAutoLoginRetry = viewModel::retryAutoLogin

    var showAutoLoginError by remember { mutableStateOf(true) }
    if (autoLoginError != null && showAutoLoginError) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                autoLoginError,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error
            )
            Text(
                LocalStrings.current.ui.addressMayHaveChanged,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // The usual cause of "cannot connect" on a home server: its
                // address changed. Fixing it here keeps the profile, its
                // favorites, stats and settings; a new server would not.
                Button(onClick = {
                    showAutoLoginError = false
                    viewModel.startEditAddress()
                }) { Text(LocalStrings.current.ui.editServerAddress) }
                Button(onClick = onAutoLoginRetry) { Text(LocalStrings.current.ui.retry) }
                if (canGoOfflineAsCurrentUser) {
                    Button(onClick = goOfflineAsCurrentUser) { Text(LocalStrings.current.ui.goOffline2) }
                }
                TextButton(onClick = { showAutoLoginError = false }) { Text(LocalStrings.current.ui.loginWithAnotherAccount) }
            }
            UpdateCheckRow(viewModel)
        }
    } else {
        val platform = LocalPlatform.current
        when (platform) {
            MOBILE, DESKTOP -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(LocalStrings.current.ui.komgaLogin)
                LoginForm(
                    viewModel = viewModel,
                    onOfflineSelect = onOfflineSelect,
                    textFieldsModifier = Modifier.width(420.dp)
                )
            }

            PlatformType.WEB_KOMF -> Column(
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val uriHandler = LocalUriHandler.current
                Column {
                    Text(LocalStrings.current.ui.fullFeaturedWebClientFor)
                    Text(
                        LocalStrings.current.ui.requiresAddingThisHostAnd,
                        color = MaterialTheme.colorScheme.secondary,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier.clickable {
                            uriHandler.openUri("https://komga.org/docs/installation/configuration/#komga_cors_allowed_origins--komgacorsallowed-origins-origins")
                        }.padding(2.dp).cursorForHand()
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    LoginForm(
                        viewModel = viewModel,
                        onOfflineSelect = onOfflineSelect,
                        textFieldsModifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

    }

}

@Composable
fun ColumnScope.LoginForm(
    viewModel: LoginViewModel,
    onOfflineSelect: () -> Unit,
    textFieldsModifier: Modifier
) {
    val serverProfiles by viewModel.serverProfiles.collectAsState(emptyList())
    val serverOptions: List<LabeledEntry<ServerProfile?>> = remember(serverProfiles) {
        serverProfiles.map { LabeledEntry<ServerProfile?>(it, serverLabel(it)) } +
                LabeledEntry<ServerProfile?>(null, "Connect to a new server")
    }

    if (serverProfiles.isNotEmpty()) {
        // The list is refreshed when a profile's address follows its active
        // one; show the refreshed copy, not the one picked earlier.
        val selectedOption: LabeledEntry<ServerProfile?> = viewModel.selectedServerProfile?.let { picked ->
            val current = serverProfiles.firstOrNull { it.id == picked.id } ?: picked
            LabeledEntry<ServerProfile?>(current, serverLabel(current))
        } ?: LabeledEntry<ServerProfile?>(null, "Connect to a new server")

        DropdownChoiceMenu(
            selectedOption = selectedOption,
            options = serverOptions,
            onOptionChange = { viewModel.onServerProfileSelect(it.value) },
            label = { Text(LocalStrings.current.ui.server) },
            inputFieldModifier = textFieldsModifier
        )
        Spacer(Modifier.height(10.dp))
    }

    if (viewModel.showNewServerFields) {
        val coroutineScope = rememberCoroutineScope()
        val (first, second, third) = remember { FocusRequester.createRefs() }

        ServerAddressFields(
            url = viewModel.url,
            onUrlChange = { viewModel.url = it },
            modifier = textFieldsModifier,
            hostFieldModifier = Modifier
                .withTextFieldNavigation()
                .focusRequester(first),
            portFieldModifier = Modifier
                .withTextFieldNavigation()
                .focusProperties { next = second },
        )

        OutlinedTextField(
            value = viewModel.user,
            onValueChange = { viewModel.user = it },
            label = { Text(LocalStrings.current.ui.username) },
            modifier = textFieldsModifier
                .withTextFieldNavigation()
                .focusRequester(second)
                .focusProperties { next = third }
        )

        OutlinedTextField(
            value = viewModel.password,
            onValueChange = { viewModel.password = it },
            visualTransformation = PasswordVisualTransformation(),
            label = { Text(LocalStrings.current.ui.password) },
            modifier = textFieldsModifier
                .withTextFieldNavigation(
                    onEnterPress = { coroutineScope.launch { viewModel.loginWithCredentials() } }
                )
                .focusRequester(third),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )
    } else {
        val strings = LocalStrings.current.ui
        if (viewModel.editingAddress) {
            val serverName = viewModel.selectedServerProfile?.let { picked ->
                (serverProfiles.firstOrNull { it.id == picked.id } ?: picked).name
            } ?: ""
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = textFieldsModifier
                    .background(MaterialTheme.colorScheme.surfaceContainerLow, KoraShapes.medium)
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        strings.newAddressOf(serverName),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = viewModel::cancelEditAddress) { Text(strings.cancel) }
                }
                ServerAddressFields(
                    url = viewModel.newAddress,
                    onUrlChange = { viewModel.newAddress = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    strings.addressChangeNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else if (viewModel.selectedServerProfile != null) {
            TextButton(onClick = viewModel::startEditAddress) { Text(strings.editServerAddress) }
        }

        OutlinedTextField(
            value = viewModel.password,
            onValueChange = { viewModel.password = it },
            visualTransformation = PasswordVisualTransformation(),
            label = { Text(if (viewModel.editingAddress) strings.passwordIfNeeded else strings.password) },
            modifier = textFieldsModifier.withTextFieldNavigation(
                onEnterPress = { viewModel.loginWithCredentials() }
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )
    }

    viewModel.addressError?.let { error ->
        val strings = LocalStrings.current.ui
        Text(
            when (error) {
                is LoginViewModel.AddressError.Unreachable -> strings.addressUnreachable(error.url)
                LoginViewModel.AddressError.PasswordNeeded -> strings.sessionExpiredEnterPassword
                LoginViewModel.AddressError.Unavailable -> strings.addressChangeUnavailable
            },
            style = TextStyle(color = MaterialTheme.colorScheme.error),
            modifier = textFieldsModifier,
        )
    }

    if (viewModel.userLoginError != null) {
        Text(viewModel.userLoginError!!, style = TextStyle(color = MaterialTheme.colorScheme.error))
    }

    Row(horizontalArrangement = Arrangement.spacedBy(50.dp)) {
        if (viewModel.offlineIsAvailable.collectAsState().value) {
            TextButton(onClick = onOfflineSelect) { Text(LocalStrings.current.ui.offlineMode) }
        }
        Button(
            onClick = { viewModel.loginWithCredentials() },
            enabled = !viewModel.addressBusy,
        ) {
            if (viewModel.addressBusy) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(18.dp).width(18.dp))
            else Text(LocalStrings.current.ui.login)
        }
    }

    Spacer(Modifier.imePadding())
}

/** "Palantir — http://…" when the server was named, else "http://… (user)" as before. */
private fun serverLabel(profile: ServerProfile): String =
    if (profile.name.isNotBlank() && profile.name.trim().trimEnd('/') != profile.url.trim().trimEnd('/'))
        "${profile.name} — ${profile.url}"
    else "${profile.url} (${profile.username})"

/**
 * "Kora 1.8.x · Check for updates" under the connection error: an update
 * comes from GitHub, so it must not wait for a server that cannot be
 * reached — that is exactly when a fixed version is needed.
 */
@Composable
private fun UpdateCheckRow(viewModel: LoginViewModel) {
    if (!viewModel.canCheckForUpdates) return
    val strings = LocalStrings.current.ui
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "Kora ${viewModel.appVersion} ·",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when (viewModel.updateCheck) {
            LoginViewModel.UpdateCheck.Checking ->
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(14.dp).width(14.dp))
            LoginViewModel.UpdateCheck.UpToDate ->
                Text(strings.appUpToDate, style = MaterialTheme.typography.bodySmall)
            LoginViewModel.UpdateCheck.Failed ->
                TextButton(onClick = viewModel::checkForUpdatesNow) {
                    Text(strings.updateCheckFailed, color = MaterialTheme.colorScheme.error)
                }
            null -> TextButton(onClick = viewModel::checkForUpdatesNow) { Text(strings.checkForUpdate) }
        }
    }
}

@Composable
fun LoginLoadingContent(onCancel: () -> Unit) {
    var showCancelButton by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(5000)
        showCancelButton = true
    }
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        CircularProgressIndicator()
        if (showCancelButton) {
            Spacer(Modifier.height(100.dp))
            Button(onClick = onCancel) { Text(LocalStrings.current.ui.cancelLoginAttempt) }
        }

    }
}
