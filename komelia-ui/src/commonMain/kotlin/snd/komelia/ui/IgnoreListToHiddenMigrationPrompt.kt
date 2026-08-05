package snd.komelia.ui

import androidx.compose.material3.AlertDialog
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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import snd.komelia.hidden.HiddenSeriesController
import snd.komelia.settings.CommonSettingsRepository
import snd.komga.client.user.KomgaUser

/**
 * One-shot launch prompt (admin only) offering to push the local Ignore List to
 * the server as kora:hidden tags — so the user's already-ignored series become
 * hidden for *everyone*, without re-doing the work. Runs once: a persisted flag
 * ([CommonSettingsRepository.getIgnoreListMigratedToServerHidden]) gates it, and
 * is only set on full success (partial failures re-prompt next launch, with the
 * already-migrated ids removed from the local list). Renders nothing until the
 * conditions are met.
 */
@Composable
fun IgnoreListToHiddenMigrationPrompt(
    settingsRepository: CommonSettingsRepository,
    hiddenController: HiddenSeriesController?,
    authenticatedUser: StateFlow<KomgaUser?>,
    isOffline: StateFlow<Boolean>,
) {
    if (hiddenController == null) return
    val user by authenticatedUser.collectAsState()
    val offline by isOffline.collectAsState()

    var candidate by remember { mutableStateOf<Set<String>?>(null) }
    var working by remember { mutableStateOf(false) }

    LaunchedEffect(user, offline) {
        if (candidate != null || working) return@LaunchedEffect
        if (user?.roleAdmin() != true || offline) return@LaunchedEffect
        if (settingsRepository.getIgnoreListMigratedToServerHidden().first()) return@LaunchedEffect
        val ignored = settingsRepository.getIgnoredSeriesIds().first()
        if (ignored.isEmpty()) {
            // Nothing to migrate — mark done so we never check again.
            settingsRepository.putIgnoreListMigratedToServerHidden(true)
        } else {
            candidate = ignored
        }
    }

    val ids = candidate ?: return
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = { if (!working) candidate = null },
        title = { Text(LocalStrings.current.ui.masquerPourTous) },
        text = {
            Text(
                "Tu as ${ids.size} série(s) ignorée(s) localement. Les masquer pour TOUS les " +
                    "utilisateurs Kora (tag kora:hidden sur le serveur) ? Elles seront retirées " +
                    "de ta liste d'ignorés locale."
            )
        },
        confirmButton = {
            TextButton(
                enabled = !working,
                onClick = {
                    working = true
                    scope.launch {
                        runCatching {
                            hiddenController.hide(ids)
                            // Verify which ids actually got the tag (refresh ran inside hide).
                            val nowHidden = hiddenController.hiddenIds.value
                            val migrated = ids.intersect(nowHidden)
                            val current = settingsRepository.getIgnoredSeriesIds().first()
                            settingsRepository.putIgnoredSeriesIds(current - migrated)
                            // Only mark done when everything migrated; else re-prompt next launch.
                            if (migrated.containsAll(ids)) {
                                settingsRepository.putIgnoreListMigratedToServerHidden(true)
                            }
                        }
                        working = false
                        candidate = null
                    }
                },
            ) { Text(if (working) "…" else "Masquer pour tous") }
        },
        dismissButton = {
            TextButton(enabled = !working, onClick = { candidate = null }) { Text(LocalStrings.current.ui.plusTard) }
        },
    )
}
