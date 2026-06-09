package snd.komelia.ui.settings.servers

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import snd.komelia.settings.CommonSettingsRepository
import snd.komelia.settings.model.ServerProfile
import snd.komelia.ui.session.ServerSessionManager

class AppServerManagementViewModel(
    private val sessionManager: ServerSessionManager,
    private val settingsRepository: CommonSettingsRepository,
) : ScreenModel {
    val serverProfiles = sessionManager.serverProfiles
    val currentServer = sessionManager.currentServerProfile

    /** Active URL of the currently connected server (the one the client uses). */
    val activeServerUrl: StateFlow<String> =
        settingsRepository.getServerUrl()
            .stateIn(screenModelScope, SharingStarted.Eagerly, "")

    /** Spare URLs for the current server that the user can switch to. */
    val alternateServerUrls: StateFlow<List<String>> =
        settingsRepository.getAlternateServerUrls()
            .stateIn(screenModelScope, SharingStarted.Eagerly, emptyList())

    fun deleteServer(profile: ServerProfile) {
        screenModelScope.launch {
            sessionManager.deleteServer(profile)
        }
    }

    fun switchServer(profile: ServerProfile) {
        sessionManager.switchServer(profile)
    }

    fun addNewServer() {
        sessionManager.switchServer(null)
    }

    /**
     * Register another URL that reaches the *same* server (e.g. a Tailscale
     * address alongside the LAN IP). Stored as a spare; does not change the
     * active connection. No-op when blank, equal to the active URL, or already
     * present.
     */
    fun addAlternateUrl(rawUrl: String) {
        val url = normalize(rawUrl)
        if (url.isBlank()) return
        screenModelScope.launch {
            val active = normalize(activeServerUrl.value)
            if (url == active) return@launch
            val current = alternateServerUrls.value
            if (current.any { normalize(it) == url }) return@launch
            settingsRepository.putAlternateServerUrls(current + url)
        }
    }

    fun removeAlternateUrl(url: String) {
        screenModelScope.launch {
            settingsRepository.putAlternateServerUrls(
                alternateServerUrls.value.filterNot { normalize(it) == normalize(url) }
            )
        }
    }

    /**
     * Make [target] (one of the alternates) the active URL. The previously
     * active URL is demoted into the alternates so the swap is reversible. The
     * current server module is then rebuilt so the Komga client, cookie store
     * and event listener rebind cleanly to the new host — same server profile,
     * same per-server DB, so stats / ratings / links stay unified.
     */
    fun switchToUrl(target: String) {
        val normalizedTarget = normalize(target)
        if (normalizedTarget.isBlank()) return
        val profile = currentServer.value ?: return
        screenModelScope.launch {
            val oldActive = normalize(activeServerUrl.value)
            if (normalizedTarget == oldActive) return@launch
            val newAlternates =
                (alternateServerUrls.value.map { normalize(it) } - normalizedTarget + oldActive)
                    .filter { it.isNotBlank() }
                    .distinct()
            settingsRepository.putServerUrl(normalizedTarget)
            settingsRepository.putAlternateServerUrls(newAlternates)
            // Rebuild the current server module so everything rebinds to the new
            // host. Same profile id → same per-server DB → unified stats.
            sessionManager.switchServer(profile)
        }
    }

    /** Trim whitespace and a single trailing slash so URLs compare cleanly. */
    private fun normalize(url: String): String = url.trim().trimEnd('/')
}
