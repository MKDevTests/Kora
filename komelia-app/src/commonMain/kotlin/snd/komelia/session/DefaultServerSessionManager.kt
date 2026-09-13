package snd.komelia.session

import kotlinx.coroutines.CoroutineScope
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
        _dependencies.value = null
        currentModule?.close()
        val module = appModuleFactory(profile?.id)
        currentModule = module
        val container = module.initDependencies()
        _dependencies.value = container
        _currentServerProfile.value = profile

        if (profile != null) {
            val updatedProfile = profile.copy(lastActive = kotlinx.datetime.Clock.System.now())
            serverProfileRepository.update(updatedProfile)
            refreshServerProfiles()
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

        scope.launch {
            switchMutex.withLock {
                _dependencies.value = null
                currentModule?.close()

                renameNullProfileFiles(inserted.id)

                val module = appModuleFactory(inserted.id)
                currentModule = module
                val container = module.initDependencies()
                _dependencies.value = container
                _currentServerProfile.value = inserted
            }
        }
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
