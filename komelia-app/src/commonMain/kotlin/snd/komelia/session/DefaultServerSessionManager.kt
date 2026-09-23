package snd.komelia.session

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import snd.komelia.AppModule
import snd.komelia.db.GlobalDatabase
import snd.komelia.db.settings.ExposedServerProfileRepository
import snd.komelia.settings.model.ServerProfile
import snd.komelia.ui.DependencyContainer
import snd.komelia.ui.session.ServerSessionManager
import java.io.File

/** [snd.komelia.db.AppSettings.serverUrl]'s default: settings that were never given an address. */
private const val DEFAULT_SERVER_URL = "http://localhost:25600"

class DefaultServerSessionManager(
    private val globalDatabaseDir: String,
    private val appDatabaseDir: String,
    private val cacheDir: String,
    private val appModuleFactory: (serverId: Long?) -> AppModule,
) : ServerSessionManager {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val switchMutex = Mutex()
    private val globalDatabase = GlobalDatabase(globalDatabaseDir)
    private val serverProfileRepository = ExposedServerProfileRepository(globalDatabase.database)
    private var currentModule: AppModule? = null
    private var urlFollower: Job? = null

    private val _dependencies = MutableStateFlow<DependencyContainer?>(null)
    override val dependencies: StateFlow<DependencyContainer?> = _dependencies.asStateFlow()

    private val _currentServerProfile = MutableStateFlow<ServerProfile?>(null)
    override val currentServerProfile: StateFlow<ServerProfile?> = _currentServerProfile.asStateFlow()

    private val _serverProfiles = MutableStateFlow<List<ServerProfile>>(emptyList())
    override val serverProfiles: StateFlow<List<ServerProfile>> = _serverProfiles.asStateFlow()

    init {
        scope.launch { refreshServerProfiles() }
    }

    private suspend fun refreshServerProfiles() {
        _serverProfiles.value = serverProfileRepository.getAll()
    }

    fun loadLastActiveServer() {
        scope.launch {
            switchMutex.withLock {
                val profiles = serverProfileRepository.getAll()
                val lastActive = profiles.maxByOrNull { it.lastActive ?: kotlinx.datetime.Instant.DISTANT_PAST }
                doSwitch(lastActive)
            }
        }
    }

    override fun switchServer(profile: ServerProfile?) {
        scope.launch {
            switchMutex.withLock {
                doSwitch(profile)
            }
        }
    }

    private suspend fun doSwitch(profile: ServerProfile?) {
        urlFollower?.cancel()
        _dependencies.value = null
        currentModule?.close()
        val module = appModuleFactory(profile?.id)
        currentModule = module
        val container = module.initDependencies()
        _dependencies.value = container
        // Re-read the profile: the caller's copy can predate an address the
        // follower just wrote, and writing that copy back undid it (measured:
        // after "switch" to 100.92.1.1 the profile was left on shodan).
        val fresh = profile?.let { serverProfileRepository.get(it.id) ?: it }
            ?.copy(lastActive = kotlinx.datetime.Clock.System.now())
        _currentServerProfile.value = fresh
        if (fresh != null) {
            serverProfileRepository.update(fresh)
            refreshServerProfiles()
            followActiveUrl(fresh.id, container)
        }
    }

    override suspend fun addServer(name: String, url: String, username: String) {
        val newProfile = ServerProfile(
            name = name,
            url = url,
            username = username,
            lastActive = kotlinx.datetime.Clock.System.now()
        )
        val inserted = serverProfileRepository.insert(newProfile)
        refreshServerProfiles()

        // The switch runs on this manager's own scope (the caller's screen is
        // torn down halfway through), but the caller waits for it: reporting
        // the login as successful before the swap let the main screen start
        // on the old module and write settings into a pool being closed.
        scope.launch {
            switchMutex.withLock {
                urlFollower?.cancel()
                _dependencies.value = null
                currentModule?.close()

                renameNullProfileFiles(inserted.id)

                val module = appModuleFactory(inserted.id)
                currentModule = module
                val container = module.initDependencies()
                _dependencies.value = container
                _currentServerProfile.value = inserted
                followActiveUrl(inserted.id, container)
            }
        }.join()
    }

    /**
     * Keeps the profile's address (the one the server list and the login
     * menu show) equal to the address the client really uses. The latter
     * lives in the server's own settings and changes on a failover, on
     * "switch" in the server screen and on "change address" at login; the
     * profile used to keep the address it was created with, so the menu
     * showed 192.168.1.30 while the app was talking to .131.
     */
    private fun followActiveUrl(profileId: Long, container: DependencyContainer) {
        urlFollower = scope.launch {
            container.appRepositories.settingsRepository.getServerUrl().collect { active ->
                runCatching { syncProfileUrl(profileId, active) }
            }
        }
    }

    private suspend fun syncProfileUrl(profileId: Long, active: String) {
        val url = active.trim().trimEnd('/')
        // Blank or the built-in default: these settings never received an
        // address (seen on a profile whose login wrote elsewhere), so they
        // say nothing about where the server is. The profile's address is
        // the better guess; do not overwrite it with localhost.
        if (url.isBlank() || url == DEFAULT_SERVER_URL) return
        val stored = serverProfileRepository.get(profileId) ?: return
        val storedUrl = stored.url.trim().trimEnd('/')
        if (storedUrl == url) return
        // A profile added from the login form is named after its address;
        // that name follows the address, a name the user chose stays.
        val name = if (stored.name.trim().trimEnd('/') == storedUrl) url else stored.name
        val updated = stored.copy(url = url, name = name)
        serverProfileRepository.update(updated)
        if (_currentServerProfile.value?.id == profileId) _currentServerProfile.value = updated
        refreshServerProfiles()
    }

    private fun renameNullProfileFiles(serverId: Long) {
        val filesToRename = listOf(
            "komelia.sqlite" to "server_${serverId}_komelia.sqlite",
            "komelia.sqlite-wal" to "server_${serverId}_komelia.sqlite-wal",
            "komelia.sqlite-shm" to "server_${serverId}_komelia.sqlite-shm",
            "offline.sqlite" to "server_${serverId}_offline.sqlite",
            "offline.sqlite-wal" to "server_${serverId}_offline.sqlite-wal",
            "offline.sqlite-shm" to "server_${serverId}_offline.sqlite-shm",
        )
        filesToRename.forEach { (oldName, newName) ->
            val from = File(appDatabaseDir, oldName)
            val to = File(appDatabaseDir, newName)
            if (from.exists()) from.renameTo(to)
        }

        val datastoreDir = File(appDatabaseDir, "datastore")
        if (datastoreDir.exists()) {
            val from = File(datastoreDir, "settings.pb")
            val to = File(datastoreDir, "server_${serverId}_settings.pb")
            if (from.exists()) from.renameTo(to)
        }
    }

    override suspend fun renameServer(profile: ServerProfile, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed == profile.name) return
        val renamed = profile.copy(name = trimmed)
        serverProfileRepository.update(renamed)
        // The name is only a label: no module rebuild, but the current profile
        // is what the screens read, so it must carry the new label too.
        if (_currentServerProfile.value?.id == profile.id) _currentServerProfile.value = renamed
        refreshServerProfiles()
    }

    override suspend fun deleteServer(profile: ServerProfile) {
        serverProfileRepository.delete(profile.id)

        File(appDatabaseDir, "server_${profile.id}_komelia.sqlite").delete()
        File(appDatabaseDir, "server_${profile.id}_komelia.sqlite-wal").delete()
        File(appDatabaseDir, "server_${profile.id}_komelia.sqlite-shm").delete()
        File(appDatabaseDir, "server_${profile.id}_offline.sqlite").delete()
        File(appDatabaseDir, "server_${profile.id}_offline.sqlite-wal").delete()
        File(appDatabaseDir, "server_${profile.id}_offline.sqlite-shm").delete()

        File(cacheDir, "okhttp/server_${profile.id}").deleteRecursively()
        File(cacheDir, "coil3_disk_cache/server_${profile.id}").deleteRecursively()
        File(cacheDir, "komelia_reader_cache/server_${profile.id}").deleteRecursively()

        refreshServerProfiles()
        if (_currentServerProfile.value?.id == profile.id) {
            loadLastActiveServer()
        }
    }
}
