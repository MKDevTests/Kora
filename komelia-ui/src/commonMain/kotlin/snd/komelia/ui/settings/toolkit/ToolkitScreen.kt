package snd.komelia.ui.settings.toolkit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import snd.komelia.toolkit.ToolkitFunction
import snd.komelia.toolkit.ToolkitSource
import snd.komelia.ui.LocalLibraries
import snd.komelia.ui.LocalViewModelFactory
import snd.komelia.ui.platform.rememberToolkitSettings
import snd.komelia.ui.settings.SettingsScreenContainer

/**
 * Admin screen for the Komga Toolkit automation. Configure URL + token, test
 * connectivity, then launch any of the 4 functions. The flow (progress →
 * preview → confirm → summary) is driven by the process-scoped
 * [ToolkitJobRunner], so leaving and returning keeps a running job alive.
 */
class ToolkitScreen : Screen {

    @Composable
    override fun Content() {
        val settings = rememberToolkitSettings()
        val factory = LocalViewModelFactory.current
        val vm = rememberScreenModel { factory.getToolkitViewModel() }
        val libraries = LocalLibraries.current.collectAsState().value

        SettingsScreenContainer("Komga Toolkit") {
            if (settings == null) {
                Text("Non disponible sur cette plateforme.")
                return@SettingsScreenContainer
            }

            // Local code gate, re-asked on every entry so only the owner runs
            // automation on this device. Volatile: unlock lives with this screen.
            var unlocked by remember { mutableStateOf(false) }
            if (!unlocked) {
                CodeGate(settings, onUnlocked = { unlocked = true })
                return@SettingsScreenContainer
            }

            var selectedLibraryId by remember(libraries) {
                mutableStateOf(libraries.firstOrNull { it.name.equals("Mangas", true) }?.id?.value
                    ?: libraries.firstOrNull()?.id?.value)
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Automatisation via ton serveur Komga Toolkit (admin uniquement). " +
                        "L'analyse peut durer plusieurs minutes ; tu peux quitter cet écran, " +
                        "le travail continue.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                OutlinedTextField(
                    value = settings.baseUrl,
                    onValueChange = settings::setBaseUrl,
                    label = { Text("URL Toolkit (http://hôte:port)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = settings.token,
                    onValueChange = settings::setToken,
                    label = { Text("Jeton (24 caractères min)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )

                val test by vm.test.collectAsState()
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        enabled = settings.configured && test !is ToolkitViewModel.TestState.Testing,
                        onClick = { vm.testConnection() },
                    ) { Text("Tester") }
                    when (val t = test) {
                        ToolkitViewModel.TestState.Idle -> {}
                        ToolkitViewModel.TestState.Testing -> CircularProgressIndicator(Modifier.size(18.dp))
                        is ToolkitViewModel.TestState.Ok -> Text(
                            if (t.status.ready && t.status.komgaConnected) "Prêt ✓"
                            else "Connecté, mais Komga non lié",
                            color = MaterialTheme.colorScheme.primary,
                        )
                        is ToolkitViewModel.TestState.Error -> Text(t.message, color = MaterialTheme.colorScheme.error)
                    }
                }

                OutlinedButton(onClick = { settings.clearCode(); unlocked = false }) {
                    Text("Changer le code d'accès")
                }

                HorizontalDivider()

                // Library picker (the preview needs a library_id).
                Text("Bibliothèque", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    libraries.forEach { lib ->
                        FilterChip(
                            selected = selectedLibraryId == lib.id.value,
                            onClick = { selectedLibraryId = lib.id.value },
                            label = { Text(lib.name) },
                        )
                    }
                }

                val flow by vm.flowState.collectAsState()
                val running = flow is ToolkitFlowState.Working
                val ready = (test as? ToolkitViewModel.TestState.Ok)?.status?.let { it.ready && it.komgaConnected } == true

                // The 4 function buttons. Disabled while a job runs or before a
                // successful test, and without a chosen library.
                Text("Fonctions", style = MaterialTheme.typography.titleSmall)
                FunctionButton("Prochaines sorties · Manga News", ready, running, selectedLibraryId) {
                    vm.startPreview(ToolkitFunction.NEXT_RELEASES, ToolkitSource.MANGA_NEWS, it)
                }
                FunctionButton("Prochaines sorties · MangaBaka", ready, running, selectedLibraryId) {
                    vm.startPreview(ToolkitFunction.NEXT_RELEASES, ToolkitSource.MANGABAKA, it)
                }
                FunctionButton("Suivi des sorties · Manga News", ready, running, selectedLibraryId) {
                    vm.startPreview(ToolkitFunction.RELEASE_TRACKING, ToolkitSource.MANGA_NEWS, it)
                }
                FunctionButton("Suivi des sorties · MangaBaka", ready, running, selectedLibraryId) {
                    vm.startPreview(ToolkitFunction.RELEASE_TRACKING, ToolkitSource.MANGABAKA, it)
                }

                HorizontalDivider()
                FlowSection(flow, onConfirm = vm::confirm, onCancel = vm::cancel, onReset = vm::reset)
            }
        }
    }
}

/**
 * First entry: set a code. Later entries: enter it. Kora never fills the code —
 * the user types it. Purely local (SHA-256 in the encrypted store); the real
 * protection is that the token lives only on this device.
 */
@Composable
private fun CodeGate(
    settings: snd.komelia.ui.platform.ToolkitSettingsState,
    onUnlocked: () -> Unit,
) {
    val setup = !settings.hasCode
    var code by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            if (setup) "Définis un code d'accès pour verrouiller cette section."
            else "Section verrouillée. Saisis ton code d'accès.",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            value = code,
            onValueChange = { code = it; error = null },
            label = { Text("Code") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth(),
        )
        if (setup) {
            OutlinedTextField(
                value = confirm,
                onValueChange = { confirm = it; error = null },
                label = { Text("Confirmer le code") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            enabled = code.length >= 4 && (!setup || confirm.isNotEmpty()),
            onClick = {
                if (setup) {
                    if (code != confirm) { error = "Les codes ne correspondent pas."; return@Button }
                    settings.setCode(code); onUnlocked()
                } else {
                    if (settings.verifyCode(code)) onUnlocked() else error = "Code incorrect."
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (setup) "Définir et déverrouiller" else "Déverrouiller") }
    }
}

@Composable
private fun FunctionButton(
    label: String,
    ready: Boolean,
    running: Boolean,
    libraryId: String?,
    onClick: (String) -> Unit,
) {
    Button(
        enabled = ready && !running && libraryId != null,
        onClick = { libraryId?.let(onClick) },
        modifier = Modifier.fillMaxWidth(),
    ) { Text(label) }
}

/** Renders the current [ToolkitJobRunner] flow state. */
@Composable
private fun FlowSection(
    flow: ToolkitFlowState,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onReset: () -> Unit,
) {
    when (flow) {
        ToolkitFlowState.Idle -> {}

        is ToolkitFlowState.Working -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${flow.function.label} · ${flow.source.label} — ${if (flow.phase == Phase.PREVIEW) "analyse" else "application"}…")
            if (flow.total > 0) {
                LinearProgressIndicator(progress = { flow.current.toFloat() / flow.total }, modifier = Modifier.fillMaxWidth())
                Text("${flow.current}/${flow.total} ${flow.message}", style = MaterialTheme.typography.bodySmall)
            } else {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            OutlinedButton(onClick = onCancel) { Text("Annuler") }
        }

        is ToolkitFlowState.PreviewReady -> PreviewReview(flow, onConfirm, onCancel)

        is ToolkitFlowState.Applied -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val a = flow.applyJob.applyResult()
            Text("Terminé — ${flow.function.label} · ${flow.source.label}", style = MaterialTheme.typography.titleSmall)
            if (a != null) {
                Text("Appliqué : ${a.applied} · inchangé : ${a.unchanged} · " +
                    "garde-fou : ${a.skippedGuardrail} · échec : ${a.failed}")
            }
            OutlinedButton(onClick = onReset) { Text("Fermer") }
        }

        is ToolkitFlowState.Failed -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Échec : ${flow.message}", color = MaterialTheme.colorScheme.error)
            OutlinedButton(onClick = onReset) { Text("Fermer") }
        }
    }
}

@Composable
private fun PreviewReview(
    flow: ToolkitFlowState.PreviewReady,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Aperçu — ${flow.function.label} · ${flow.source.label}", style = MaterialTheme.typography.titleSmall)

        val applicable: Int = when (flow.function) {
            ToolkitFunction.RELEASE_TRACKING -> {
                val r = flow.previewJob.releaseTrackingResult()
                if (r != null) {
                    Text("Chargées ${r.loaded} · liées ${r.linked} · confiance élevée ${r.highConfidence} · " +
                        "à vérifier ${r.review} · exclues ${r.ignored + r.nonManga} · erreurs ${r.errors}",
                        style = MaterialTheme.typography.bodySmall)
                    r.rows.forEach { row ->
                        Text("• ${row.title} — tomes ${row.currentTotal ?: "?"}→${row.totalDecision.proposed ?: row.currentTotal ?: "?"} · " +
                            "statut ${row.currentStatus ?: "?"}→${row.statusDecision.proposed ?: row.currentStatus ?: "?"} · ${row.source}",
                            style = MaterialTheme.typography.bodyMedium)
                    }
                    r.rows.size
                } else 0
            }
            ToolkitFunction.NEXT_RELEASES -> {
                val r = flow.previewJob.nextReleaseResult()
                if (r != null) {
                    Text("Chargées ${r.loaded} · liées ${r.linked} · changements ${r.changes} · " +
                        "inchangées ${r.unchanged} · sans sortie ${r.noRelease} · erreurs ${r.errors}",
                        style = MaterialTheme.typography.bodySmall)
                    r.rows.forEach { row ->
                        Text("• ${row.title} — tome ${row.volume} le ${row.date}  (${row.currentTag ?: "—"} → ${row.proposedTag ?: "—"})",
                            style = MaterialTheme.typography.bodyMedium)
                    }
                    r.changes
                } else 0
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(enabled = applicable > 0, onClick = onConfirm) { Text("Confirmer et appliquer") }
            OutlinedButton(onClick = onCancel) { Text("Annuler") }
        }
        if (applicable == 0) {
            Text("Rien à appliquer.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
