package snd.komelia.db

import io.github.vinceglb.filekit.PlatformFile
import kotlinx.serialization.Serializable
import snd.komelia.offline.server.model.OfflineMediaServerId
import snd.komelia.offline.user.model.OfflineUser
import snd.komga.client.user.KomgaUserId
import kotlin.time.Instant

@Serializable
data class OfflineSettings(
    val isOfflineModeEnabled: Boolean = false,
    val downloadDirectory: PlatformFile,
    val userId: KomgaUserId = OfflineUser.ROOT,
    val serverId: OfflineMediaServerId? = null,
    val readProgressSyncDate: Instant? = null,
    val dataSyncDate: Instant? = null,

    /**
     * Optional, never enforced against the user's wishes: they asked for the
     * app to warn rather than refuse when a download runs outside these.
     */
    val downloadWifiOnly: Boolean = false,
    val downloadWhileChargingOnly: Boolean = false,

    /**
     * How much the offline library may occupy, in megabytes.
     *
     * The default is deliberate rather than round: 78 series in progress at two
     * books ahead would be over 15 GB on this install, and 39 GB of the tablet
     * is free. Four gigabytes is what an automatic download can spend without
     * anyone noticing.
     */
    val downloadStorageLimitMb: Int = 4096,

    /** Days after a book is read before the cleaner may drop it. 0 = never. */
    val cleanupReadAfterDays: Int = 0,

    /** Whether the cleaner may also drop books the user downloaded by hand. */
    val cleanupIncludeManual: Boolean = false,
)
