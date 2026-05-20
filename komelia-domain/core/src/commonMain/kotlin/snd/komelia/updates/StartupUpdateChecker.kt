package snd.komelia.updates

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import snd.komelia.settings.CommonSettingsRepository
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

private val logger = KotlinLogging.logger {}

class StartupUpdateChecker(
    private val updater: AppUpdater,
    private val settings: CommonSettingsRepository,
    private val releaseFlow: MutableStateFlow<List<AppRelease>>,
) {
    private val updateScope = CoroutineScope(Dispatchers.Default)
    val downloadProgress = MutableStateFlow<UpdateProgress?>(null)

    suspend fun checkForUpdates(): AppRelease? {
        try {
            val checkForUpdates = settings.getCheckForUpdatesOnStartup().first()
            if (!checkForUpdates) return null

            val lastChecked = settings.getLastUpdateCheckTimestamp().first()
            if (lastChecked != null && lastChecked > Clock.System.now().minus(24.hours)) return null

            val releases = updater.getReleases()
            val latest = releases.first()
            releaseFlow.value = releases
            settings.putLastUpdateCheckTimestamp(Clock.System.now())
            settings.putLastCheckedReleaseVersion(latest.version)

            if (AppVersion.current >= latest.version) return null
            // Skip when the user has explicitly dismissed THIS LATEST
            // version. Previous code compared against `AppVersion.current`
            // which silently swallowed every "next" update after an
            // accept (onUpdate used to write dismissedVersion to the
            // accepted release, so after an in-app update the user was
            // permanently dismissed against the version they were now
            // running — and the next release never surfaced). The fix
            // is to compare to `latest.version`: only the version the
            // user genuinely told us "no thanks" about gets skipped.
            if (settings.getDismissedVersion().first() == latest.version) return null

            return latest
        } catch (e: Exception) {
            logger.catching(e)
            return null
        }
    }

    suspend fun onUpdate(release: AppRelease) {
        // No-op on dismissedVersion. Accepting an update is the
        // opposite of dismissing it — the previous behaviour of writing
        // dismissedVersion = release.version here was the root cause
        // of "I accepted v1.0.3, now v1.0.4 never notifies me". Only
        // onUpdateDismiss touches dismissedVersion now.
        updater.updateTo(release)
            ?.conflate()
            ?.onCompletion { downloadProgress.value = null }
            ?.onEach { downloadProgress.value = it }
            ?.launchIn(updateScope)
    }

    fun onUpdateCancel() {
        updateScope.coroutineContext.cancelChildren()
    }

    suspend fun onUpdateDismiss(release: AppRelease) {
        settings.putDismissedVersion(release.version)
    }

}