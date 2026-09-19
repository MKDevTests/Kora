package snd.komelia.ui.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.http.HttpStatusCode.Companion.Unauthorized
import io.ktor.utils.io.*
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import snd.komelia.AppNotification
import snd.komelia.AppNotifications
import snd.komelia.KomgaAuthenticationState
import snd.komelia.komga.api.KomgaLibraryApi
import snd.komelia.komga.api.KomgaUserApi
import snd.komelia.offline.api.OfflineLibraryApi
import snd.komelia.offline.server.repository.OfflineMediaServerRepository
import snd.komelia.offline.settings.OfflineSettingsRepository
import snd.komelia.offline.user.model.OfflineUser
import snd.komelia.offline.user.repository.OfflineUserRepository
import snd.komelia.settings.CommonSettingsRepository
import snd.komelia.settings.SecretsRepository
import snd.komelia.settings.model.ServerProfile
import snd.komelia.ui.session.ServerSessionManager
import snd.komelia.ui.LoadState
import snd.komelia.ui.LoadState.Uninitialized
import snd.komelia.ui.error.formatExceptionMessage
import snd.komelia.ui.platform.PlatformType
import snd.komelia.ui.platform.PlatformType.DESKTOP
import snd.komelia.ui.platform.PlatformType.MOBILE
import snd.komelia.ui.platform.PlatformType.WEB_KOMF

class LoginViewModel(
    private val settingsRepository: CommonSettingsRepository,
    private val secretsRepository: SecretsRepository,
    private val komgaUserApi: Flow<KomgaUserApi>,
    private val komgaLibraryApi: Flow<KomgaLibraryApi>,
    private val komgaAuthState: KomgaAuthenticationState,
    private val notifications: AppNotifications,
    private val platform: PlatformType,
    private val sessionManager: ServerSessionManager,

    private val offlineUserRepository: OfflineUserRepository,
    private val offlineServerRepository: OfflineMediaServerRepository,
    private val offlineSettingsRepository: OfflineSettingsRepository,
    private val offlineLibraryApi: OfflineLibraryApi,
) : StateScreenModel<LoadState<Unit>>(Uninitialized) {

    var url by mutableStateOf("")
    var user by mutableStateOf("")
    var password by mutableStateOf("")
    var userLoginError by mutableStateOf<String?>(null)
    var autoLoginError by mutableStateOf<String?>(null)
    val offlineIsAvailable = MutableStateFlow(false)
    private val offlineUser = MutableStateFlow<OfflineUser?>(null)
    val canGoOfflineAsCurrentUser = offlineUser.map { it != null }

    val serverProfiles = sessionManager.serverProfiles
    var selectedServerProfile by mutableStateOf<ServerProfile?>(null)
    var showNewServerFields by mutableStateOf(false)

    fun initialize() {
        if (state.value !is Uninitialized) return

        screenModelScope.launch {
            val profiles = serverProfiles.first()
            val currentProfile = sessionManager.currentServerProfile.value
            selectedServerProfile = currentProfile
            showNewServerFields = currentProfile == null

            val initialUrl = selectedServerProfile?.url ?: settingsRepository.getServerUrl().first()
            val initialUser = selectedServerProfile?.username ?: settingsRepository.getCurrentUser().first()
            url = initialUrl
            user = initialUser
            val offlineUsers = offlineUserRepository.findAll()
            val offlineServer = offlineServerRepository.findByUrl(url)

            offlineIsAvailable.value = offlineUsers.any { it.id != OfflineUser.ROOT }
            offlineUser.value = offlineServer?.let { server -> offlineUsers.firstOrNull { it.serverId == server.id } }
            val isOffline = offlineSettingsRepository.getOfflineMode().first()

            when (platform) {
                MOBILE, DESKTOP -> {
                    if (isOffline || secretsRepository.getCookie(url) != null) {
                        tryAutologin()
                    } else {
                        mutableState.value = LoadState.Error(RuntimeException("Not logged in"))
                    }
                }

                WEB_KOMF -> tryAutologin()
            }
        }
    }

    fun retryAutoLogin() {
        screenModelScope.launch {
            mutableState.value = LoadState.Loading
            tryAutologin()
        }
    }

    fun cancel() {
        screenModelScope.coroutineContext.cancelChildren()
        mutableState.value = LoadState.Error(RuntimeException("Cancelled login attempt"))
        userLoginError = "Cancelled login attempt"
    }

    fun onServerProfileSelect(profile: ServerProfile?) {
        selectedServerProfile = profile
        if (profile != null) {
            showNewServerFields = false
            url = profile.url
            user = profile.username
            sessionManager.switchServer(profile)
        } else {
            showNewServerFields = true
            sessionManager.switchServer(null)
        }

        // switchServer tears down the module that owns these repositories on
        // another thread; a lookup racing that shutdown used to surface as a
        // "DB offline pool has been closed" crash. A server that is not added
        // yet has no offline user, and a failed lookup only hides the
        // "go offline as this user" button.
        if (profile == null) {
            offlineUser.value = null
            return
        }
        screenModelScope.launch {
            offlineUser.value = runCatching {
                val offlineUsers = offlineUserRepository.findAll()
                val offlineServer = offlineServerRepository.findByUrl(url)
                offlineServer?.let { server -> offlineUsers.firstOrNull { it.serverId == server.id } }
            }.getOrNull()
        }
    }

    fun loginWithCredentials() {
        screenModelScope.launch {
            userLoginError = null
            if (showNewServerFields) {
                settingsRepository.putServerUrl(url)
                settingsRepository.putCurrentUser(user)
                tryUserLogin(user, password) {
                    sessionManager.addServer(name = url, url = url, username = user)
                }
            } else {
                tryUserLogin(user, password)
            }
        }
    }

    fun offlineLogin() {
        notifications.runCatchingToNotifications(screenModelScope) {
            val user = offlineUser.value ?: return@runCatchingToNotifications
            offlineSettingsRepository.putOfflineMode(true)
            offlineSettingsRepository.putUserId(user.id)
            komgaAuthState.setStateValues(user.toKomgaUser(), offlineLibraryApi.getLibraries())
            mutableState.value = LoadState.Success(Unit)
        }
    }

    private suspend fun tryAutologin() {
        try {
            tryLogin()
            mutableState.value = LoadState.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: NoTransformationFoundException) {
            val message = "Unexpected response for url $url"
            autoLoginError = message
            notifications.add(AppNotification.Error(message))
            mutableState.value = LoadState.Error(e)
        } catch (e: ClientRequestException) {
            if (e.response.status == Unauthorized) {
                autoLoginError = null
            } else {
                autoLoginError = "Login error: ${e::class.simpleName} ${e.message}"
                notifications.add(AppNotification.Error(e.message))
            }
            mutableState.value = LoadState.Error(e)
        } catch (e: Throwable) {
            val errorMessage = "Login error: ${e::class.simpleName} ${e.message}"
            autoLoginError = errorMessage
            mutableState.value = LoadState.Error(e)
            notifications.add(AppNotification.Error(errorMessage))
        }
    }

    private suspend fun tryUserLogin(
        username: String,
        password: String,
        onSuccess: suspend () -> Unit = {}
    ) {
        try {
            tryLogin(username, password)
            onSuccess()
            mutableState.value = LoadState.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: NoTransformationFoundException) {
            val message = "Unexpected response for url $url"
            userLoginError = message
            mutableState.value = LoadState.Error(e)
        } catch (e: ClientRequestException) {
            userLoginError = if (e.response.status == Unauthorized) "Invalid credentials"
            else "Login error ${e::class.simpleName}: ${e.message}"
            mutableState.value = LoadState.Error(e)
        } catch (e: Throwable) {
            userLoginError = formatExceptionMessage(e)
            mutableState.value = LoadState.Error(e)
        }
    }

    private suspend fun tryLogin(
        username: String? = null,
        password: String? = null
    ) {
        val userApi = this.komgaUserApi.first()
        val libraryApi = this.komgaLibraryApi.first()
        val user =
            if (username != null && password != null) userApi.getMe(username, password, true)
            else userApi.getMe()

        val libraries = libraryApi.getLibraries()
        komgaAuthState.setStateValues(user, libraries)
    }
}

sealed class LoginResult {
    data object Loading : LoginResult()
    data object Error : LoginResult()
    data object Success : LoginResult()
}
