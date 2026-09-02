package snd.komelia.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class SyncBlob(
    @SerialName("v") val version: Int = 1,
    @SerialName("b") val bookmarks: List<CompactBookmark> = emptyList(),
    @SerialName("a") val annotations: List<CompactAnnotation> = emptyList(),
    @SerialName("m") val lastModified: Long = 0
)

@Serializable
data class CompactBookmark(
    @SerialName("i") val id: String,
    @SerialName("l") val locatorJson: String,
    @SerialName("c") val createdAt: Long
)

@Serializable
data class CompactAnnotation(
    @SerialName("i") val id: String,
    @SerialName("t") val type: Int, // 0 for EPUB, 1 for Comic
    @SerialName("l") val loc: String, // locatorJson for EPUB, or "page,x,y" for Comic
    @SerialName("h") val color: Int? = null,
    @SerialName("n") val note: String? = null,
    @SerialName("c") val createdAt: Long,
    @SerialName("u") val updatedAt: Long = createdAt, // defaults to createdAt for old blobs
    @SerialName("s") val selectedText: String? = null, // EPUB only, null for comics
)

class ReaderSyncService {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun decode(blob: String?): SyncBlob? {
        if (blob == null || blob.isBlank()) return null
        return try {
            json.decodeFromString<SyncBlob>(blob)
        } catch (e: Exception) {
            null
        }
    }

    fun encode(blob: SyncBlob): String {
        return json.encodeToString(blob)
    }

    /**
     * Merges local and remote blobs while propagating deletions.
     * @param local The state currently on this device.
     * @param remote The state currently on the server.
     * @param localLastSyncTime The timestamp of the remote blob when this device last synced.
     */
    fun merge(local: SyncBlob, remote: SyncBlob, localLastSyncTime: Long): SyncBlob {
        return SyncBlob(
            bookmarks = mergeItems(local.bookmarks, remote.bookmarks, localLastSyncTime, remote.lastModified) { it.id to it.createdAt },
            annotations = mergeItems(local.annotations, remote.annotations, localLastSyncTime, remote.lastModified) { it.id to it.updatedAt },
            lastModified = maxOf(local.lastModified, remote.lastModified)
        )
    }

    private fun <T> mergeItems(
        local: List<T>,
        remote: List<T>,
        localLastSyncTime: Long,
        remoteLastModified: Long,
        selector: (T) -> Pair<String, Long>
    ): List<T> {
        val localMap = local.associateBy { selector(it).first }
        val remoteMap = remote.associateBy { selector(it).first }
        val allIds = (localMap.keys + remoteMap.keys).distinct()

        return allIds.mapNotNull { id ->
            val l = localMap[id]
            val r = remoteMap[id]

            when {
                l != null && r != null -> {
                    // Item exists in both, take newest (though IDs should be unique and immutable here)
                    if (selector(l).second >= selector(r).second) l else r
                }
                l != null && r == null -> {
                    // Exists locally but not remotely.
                    // If it was created before the remote blob's last modification, it means it was deleted remotely.
                    // If it was created after, it's a new local item.
                    if (selector(l).second > remoteLastModified) l else null
                }
                l == null && r != null -> {
                    // Exists remotely but not locally.
                    // If it was created before our last sync, it means we deleted it locally.
                    // If it was created after, it's a new remote item.
                    if (selector(r).second > localLastSyncTime) r else null
                }
                else -> null
            }
        }
    }
}
