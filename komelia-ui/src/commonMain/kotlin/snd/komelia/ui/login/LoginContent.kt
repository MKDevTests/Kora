package snd.komelia.ui.login

import androidx.compose.foundation.clickable
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
import snd.komelia.ui.common.components.OutlinedHttpTextField
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

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(onClick = { showAutoLoginError = false }) { Text(LocalStrings.current.ui.loginWithAnotherAccount) }
                if (canGoOfflineAsCurrentUser) {
                    Button(onClick = goOfflineAsCurrentUser) { Text(LocalStrings.current.ui.goOffline2) }
                }

                Button(onClick = onAutoLoginRetry) { Text(LocalStrings.current.ui.retry) }
            }
        }
    } else {
        val platform = LocalPlatform.current
        when (platform) {
            MOBILE, DESKTOP -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(LocalStrings.current.ui.komgaLogin)
                LoginForm(
                    viewModel = viewModel,
                    onOfflineSelect = onOfflineSelect,
                    textFieldsModifier = Modifier
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
        serverProfiles.map { LabeledEntry<ServerProfile?>(it, "${it.url} (${it.username})") } +
                LabeledEntry<ServerProfile?>(null, "Connect to a new server")
    }

    if (serverProfiles.isNotEmpty()) {
        val selectedOption: LabeledEntry<ServerProfile?> = viewModel.selectedServerProfile?.let {
            LabeledEntry<ServerProfile?>(it, "${it.url} (${it.username})")
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

        OutlinedHttpTextField(
            value = viewModel.url,
            onValueChange = { viewModel.url = it },
            label = { Text(LocalStrings.current.ui.serverUrl) },
            modifier = textFieldsModifier
                .withTextFieldNavigation()
                .focusRequester(first)
                .focusProperties { next = second },
            placeholder = { Text("localhost:25600") }
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
        OutlinedTextField(
            value = viewModel.password,
            onValueChange = { viewModel.password = it },
            visualTransformation = PasswordVisualTransformation(),
            label = { Text(LocalStrings.current.ui.password) },
            modifier = textFieldsModifier.withTextFieldNavigation(
                onEnterPress = { viewModel.loginWithCredentials() }
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )
    }

    if (viewModel.userLoginError != null) {
        Text(viewModel.userLoginError!!, style = TextStyle(color = MaterialTheme.colorScheme.error))
    }

    Row(horizontalArrangement = Arrangement.spacedBy(50.dp)) {
        if (viewModel.offlineIsAvailable.collectAsState().value) {
            TextButton(onClick = onOfflineSelect) { Text(LocalStrings.current.ui.offlineMode) }
        }
        Button(onClick = { viewModel.loginWithCredentials() }) { Text(LocalStrings.current.ui.login) }
    }

    Spacer(Modifier.imePadding())
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
