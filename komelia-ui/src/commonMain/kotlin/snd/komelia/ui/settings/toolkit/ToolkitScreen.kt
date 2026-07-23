package snd.komelia.ui.settings.toolkit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import snd.komelia.toolkit.ToolkitCategory
import snd.komelia.toolkit.ToolkitFunction
import snd.komelia.toolkit.ToolkitSource
import snd.komelia.ui.LocalKomgaState
import snd.komelia.ui.LocalLibraries
import snd.komelia.ui.LocalViewModelFactory
import snd.komelia.ui.platform.ToolkitSettingsState
import snd.komelia.ui.platform.rememberToolkitSettings
import snd.komelia.ui.settings.SettingsScreenContainer
import snd.komga.client.library.KomgaLibrary

/** One launchable automation. RUN writes directly (confirm first); PREVIEW opens
 *  the review-then-validate flow. */
private data class ToolkitAction(
    val label: String,
    val function: ToolkitFunction,
    val source: ToolkitSource,
    val run: Boolean,
)

/**
 * Admin screen for the Komga Toolkit automation. Code-locked. Configure URL +
 * token, bind each perimeter category (Mangas/BD/Comics) to a Komga library,
 * then launch the functions — each locked to its category's library. The flow
 * runs in the background ([ToolkitJobRunner]); leaving and returning is safe.
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
            var unlocked by remember { mutableStateOf(false) }
            if (!unlocked) {
                CodeGate(settings, onUnlocked = { unlocked = true })
                return@SettingsScreenContainer
            }
            Unlocked(settings, vm, libraries, onRelock = { unlocked = false })
        }
    }
}

@Composable
private fun Unlocked(
    settings: ToolkitSettingsState,
    vm: ToolkitViewModel,
    libraries: List<KomgaLibrary>,
    onRelock: () -> Unit,
) {
    val komgaServerUrl = LocalKomgaState.current.serverUrl.value
    LaunchedEffect(komgaServerUrl) {
        if (settings.baseUrl.isBlank()) suggestToolkitUrl(komgaServerUrl)?.let { settings.setBaseUrl(it) }
    }
    // Auto-match each empty category to a library by name.
    LaunchedEffect(libraries) {
        ToolkitCategory.entries.forEach { cat ->
            if (settings.libraryFor(cat) == null) autoMatch(cat, libraries)?.let { settings.setLibraryFor(cat, it.id.value) }
        }
    }

    var tokenVisible by remember { mutableStateOf(false) }
    val test by vm.test.collectAsState()
    val flow by vm.flowState.collectAsState()
    val ready = (test as? ToolkitViewModel.TestState.Ok)?.status
    val running = flow is ToolkitFlowState.Working
    var pendingRun by remember { mutableStateOf<ToolkitAction?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Automatisation via ton serveur Komga Toolkit (admin). L'analyse peut " +
                "durer plusieurs minutes ; tu peux quitter l'écran, le travail continue.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = settings.baseUrl, onValueChange = settings::setBaseUrl,
            label = { Text("URL Toolkit (http://hôte:port)") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = settings.token, onValueChange = settings::setToken,
            label = { Text("Jeton (24 caractères min)") }, singleLine = true,
            visualTransformation = if (tokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { tokenVisible = !tokenVisible }) {
                    Icon(if (tokenVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                enabled = settings.configured && test !is ToolkitViewModel.TestState.Testing,
                onClick = { vm.testConnection() },
            ) { Text("Tester") }
            when (val t = test) {
                ToolkitViewModel.TestState.Idle -> {}
                ToolkitViewModel.TestState.Testing -> CircularProgressIndicator(Modifier.size(18.dp))
                is ToolkitViewModel.TestState.Ok -> Text(
                    if (t.status.ready && t.status.komgaConnected) "Prêt ✓" else "Connecté, Komga non lié",
                    color = MaterialTheme.colorScheme.primary,
                )
                is ToolkitViewModel.TestState.Error -> Text(t.message, color = MaterialTheme.colorScheme.error)
            }
        }
        val uriHandler = LocalUriHandler.current
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = settings.baseUrl.isNotBlank(), onClick = { runCatching { uriHandler.openUri(settings.baseUrl) } }) {
                Text("Ouvrir le WebUI")
            }
            OutlinedButton(onClick = { settings.clearCode(); onRelock() }) { Text("Changer le code") }
        }

        HorizontalDivider()
        Text("Périmètres (bibliothèque par catégorie)", style = MaterialTheme.typography.titleSmall)
        ToolkitCategory.entries.forEach { cat -> PerimeterRow(cat, settings, libraries) }

        HorizontalDivider()
        Text("Fonctions", style = MaterialTheme.typography.titleSmall)
        ToolkitCategory.entries.forEach { cat ->
            val libId = settings.libraryFor(cat) ?: return@forEach
            val actions = actionsFor(cat)
            if (actions.isEmpty()) return@forEach
            Text(cat.label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
            actions.forEach { a ->
                val sourceOk = ready?.isSourceReady(a.source) ?: false
                Button(
                    enabled = ready != null && ready.ready && ready.komgaConnected && sourceOk && !running,
                    onClick = { if (a.run) pendingRun = a else vm.startPreview(a.function, a.source, libId) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(a.label) }
            }
        }

        HorizontalDivider()
        FlowSection(flow, onConfirm = vm::confirm, onCancel = vm::cancel, onReset = vm::reset)
    }

    // Confirmation before a direct /run (next-releases writes immediately).
    pendingRun?.let { a ->
        val libId = settings.libraryFor(categoryOf(a)) ?: return@let
        AlertDialog(
            onDismissRequest = { pendingRun = null },
            title = { Text("Lancer et appliquer ?") },
            text = { Text("« ${a.label} » va analyser puis écrire directement les changements. Continuer ?") },
            confirmButton = {
                TextButton(onClick = { vm.startRun(a.function, a.source, libId); pendingRun = null }) { Text("Lancer") }
            },
            dismissButton = { TextButton(onClick = { pendingRun = null }) { Text("Annuler") } },
        )
    }
}

/** Actions offered under a category. */
private fun actionsFor(cat: ToolkitCategory): List<ToolkitAction> = when (cat) {
    ToolkitCategory.MANGAS -> listOf(
        ToolkitAction("Prochaines sorties · Manga News", ToolkitFunction.NEXT_RELEASES, ToolkitSource.MANGA_NEWS, run = true),
        ToolkitAction("Prochaines sorties · MangaBaka", ToolkitFunction.NEXT_RELEASES, ToolkitSource.MANGABAKA, run = true),
        ToolkitAction("Suivi des sorties · Manga News", ToolkitFunction.RELEASE_TRACKING, ToolkitSource.MANGA_NEWS, run = false),
        ToolkitAction("Suivi des sorties · MangaBaka", ToolkitFunction.RELEASE_TRACKING, ToolkitSource.MANGABAKA, run = false),
        ToolkitAction("Suivi des tomes · Bedetheque", ToolkitFunction.RELEASE_TRACKING, ToolkitSource.BEDETHEQUE, run = false),
    )
    ToolkitCategory.BD -> listOf(
        ToolkitAction("Suivi des tomes · Bedetheque", ToolkitFunction.RELEASE_TRACKING, ToolkitSource.BEDETHEQUE, run = false),
    )
    ToolkitCategory.COMICS -> listOf(
        ToolkitAction("Suivi tomes/issues · ComicVine", ToolkitFunction.RELEASE_TRACKING, ToolkitSource.COMICVINE, run = false),
        ToolkitAction("Suivi des tomes · Bedetheque", ToolkitFunction.RELEASE_TRACKING, ToolkitSource.BEDETHEQUE, run = false),
    )
}

/** Bedetheque appears under all three; its category is whichever button launched
 *  it. For non-transverse sources the category is unambiguous. */
private fun categoryOf(a: ToolkitAction): ToolkitCategory = when (a.source) {
    ToolkitSource.COMICVINE -> ToolkitCategory.COMICS
    ToolkitSource.MANGA_NEWS, ToolkitSource.MANGABAKA -> ToolkitCategory.MANGAS
    ToolkitSource.BEDETHEQUE -> ToolkitCategory.MANGAS // overridden by caller's context; run() only used by next-releases (mangas)
}

@Composable
private fun PerimeterRow(category: ToolkitCategory, settings: ToolkitSettingsState, libraries: List<KomgaLibrary>) {
    var expanded by remember { mutableStateOf(false) }
    val current = libraries.firstOrNull { it.id.value == settings.libraryFor(category) }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(category.label, modifier = Modifier.width(80.dp), style = MaterialTheme.typography.bodyMedium)
        Box {
            OutlinedButton(onClick = { expanded = true }) {
                Text(current?.name ?: "— choisir —")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(text = { Text("(aucune)") }, onClick = { settings.setLibraryFor(category, null); expanded = false })
                libraries.forEach { lib ->
                    DropdownMenuItem(text = { Text(lib.name) }, onClick = { settings.setLibraryFor(category, lib.id.value); expanded = false })
                }
            }
        }
        if (current == null) {
            Text(" non lié", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Auto-match a category to a library by name keyword. */
private fun autoMatch(category: ToolkitCategory, libraries: List<KomgaLibrary>): KomgaLibrary? = when (category) {
    ToolkitCategory.MANGAS -> libraries.firstOrNull { it.name.contains("manga", true) }
    ToolkitCategory.BD -> libraries.firstOrNull { l ->
        listOf("bd", "bande", "bédé", "bede").any { l.name.contains(it, true) }
    }
    ToolkitCategory.COMICS -> libraries.firstOrNull { it.name.contains("comic", true) }
}

private fun suggestToolkitUrl(komgaUrl: String): String? {
    val m = Regex("^(https?)://([^/:]+)").find(komgaUrl.trim()) ?: return null
    return "${m.groupValues[1]}://${m.groupValues[2]}:8765"
}

@Composable
private fun CodeGate(settings: ToolkitSettingsState, onUnlocked: () -> Unit) {
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
            value = code, onValueChange = { code = it; error = null }, label = { Text("Code") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth(),
        )
        if (setup) {
            OutlinedTextField(
                value = confirm, onValueChange = { confirm = it; error = null }, label = { Text("Confirmer le code") }, singleLine = true,
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
                } else if (settings.verifyCode(code)) onUnlocked() else error = "Code incorrect."
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (setup) "Définir et déverrouiller" else "Déverrouiller") }
    }
}

@Composable
private fun FlowSection(flow: ToolkitFlowState, onConfirm: () -> Unit, onCancel: () -> Unit, onReset: () -> Unit) {
    when (flow) {
        ToolkitFlowState.Idle -> {}
        is ToolkitFlowState.Working -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${flow.function.label} · ${flow.source.label} — ${if (flow.phase == Phase.PREVIEW) "analyse" else "traitement"}…")
            if (flow.total > 0) {
                LinearProgressIndicator(progress = { flow.current.toFloat() / flow.total }, modifier = Modifier.fillMaxWidth())
                Text("${flow.current}/${flow.total} ${flow.message}", style = MaterialTheme.typography.bodySmall)
            } else LinearProgressIndicator(Modifier.fillMaxWidth())
            OutlinedButton(onClick = onCancel) { Text("Annuler") }
        }
        is ToolkitFlowState.PreviewReady -> PreviewReview(flow, onConfirm, onCancel)
        is ToolkitFlowState.Applied -> AppliedSummary(flow, onReset)
        is ToolkitFlowState.Failed -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Échec : ${flow.message}", color = MaterialTheme.colorScheme.error)
            OutlinedButton(onClick = onReset) { Text("Fermer") }
        }
    }
}

@Composable
private fun AppliedSummary(flow: ToolkitFlowState.Applied, onReset: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Terminé — ${flow.function.label} · ${flow.source.label}", style = MaterialTheme.typography.titleSmall)
        val nr = flow.applyJob.nextReleaseAutoResult()
        val rt = flow.applyJob.releaseTrackingAutoResult()
        val ap = flow.applyJob.applyResult()
        when {
            nr != null && nr.mode.isNotBlank() -> {
                Text("Analysées ${nr.scanned} · changements ${nr.validChanges} · appliquées ${nr.applied} · " +
                    "garde-fou ${nr.skippedGuardrail} · échec ${nr.failed}")
                nr.rows.forEach { Text("• ${it.title} — tome ${it.volume} le ${it.date}  (${it.oldTag.ifBlank { "—" }} → ${it.newTag})", style = MaterialTheme.typography.bodyMedium) }
            }
            rt != null && rt.mode.isNotBlank() -> {
                Text("Analysées ${rt.scanned} · confiance élevée ${rt.highConfidence} · appliquées ${rt.applied} · " +
                    "garde-fou ${rt.skippedGuardrail} · échec ${rt.failed}")
                rt.rows.forEach { Text("• ${it.title} — tomes ${it.currentTotal ?: "?"}→${it.newTotal ?: "?"} · statut ${it.currentStatus.ifBlank { "?" }}→${it.newStatus.ifBlank { "=" }}", style = MaterialTheme.typography.bodyMedium) }
            }
            ap != null -> Text("Appliqué ${ap.applied} · inchangé ${ap.unchanged} · garde-fou ${ap.skippedGuardrail} · échec ${ap.failed}")
        }
        OutlinedButton(onClick = onReset) { Text("Fermer") }
    }
}

@Composable
private fun PreviewReview(flow: ToolkitFlowState.PreviewReady, onConfirm: () -> Unit, onCancel: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Aperçu — ${flow.function.label} · ${flow.source.label}", style = MaterialTheme.typography.titleSmall)
        val r = flow.previewJob.releaseTrackingResult()
        val applicable = if (r != null) {
            Text("Chargées ${r.loaded} · liées ${r.linked} · confiance élevée ${r.highConfidence} · " +
                "à vérifier ${r.review} · exclues ${r.ignored + r.nonManga} · erreurs ${r.errors}",
                style = MaterialTheme.typography.bodySmall)
            r.rows.forEach { row ->
                Text("• ${row.title} — tomes ${row.currentTotal ?: "?"}→${row.totalDecision.proposed ?: row.currentTotal ?: "?"} · " +
                    "statut ${row.currentStatus ?: "?"}→${row.statusDecision.proposed ?: row.currentStatus ?: "="} · ${row.source}",
                    style = MaterialTheme.typography.bodyMedium)
            }
            r.rows.size
        } else 0
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(enabled = applicable > 0, onClick = onConfirm) { Text("Confirmer et appliquer") }
            OutlinedButton(onClick = onCancel) { Text("Annuler") }
        }
        if (applicable == 0) Text("Rien à appliquer.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
