package snd.komelia.ui.session

import kotlinx.coroutines.flow.StateFlow
import snd.komelia.settings.model.ServerProfile
import snd.komelia.ui.DependencyContainer

interface ServerSessionManager {
    val dependencies: StateFlow<DependencyContainer?>
    val currentServerProfile: StateFlow<ServerProfile?>
    val serverProfiles: StateFlow<List<ServerProfile>>
    fun switchServer(profile: ServerProfile?)
    suspend fun addServer(name: String, url: String, username: String)
    suspend fun deleteServer(profile: ServerProfile)

    /** Changes the label shown for [profile]; nothing else about it. */
    suspend fun renameServer(profile: ServerProfile, name: String)
}
