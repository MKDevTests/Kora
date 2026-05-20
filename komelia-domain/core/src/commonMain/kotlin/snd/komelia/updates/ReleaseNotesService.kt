package snd.komelia.updates

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.first
import snd.komelia.settings.CommonSettingsRepository

private val logger = KotlinLogging.logger {}

/**
 * Decides whether to display the "What's new in this release" modal and
 * looks up the actual notes from GitHub.
 *
 * The decision is dirt-simple: compare [AppVersion.current] (compiled into
 * the APK) with the version the user last acknowledged. If they match,
 * the modal stays hidden. If they differ — first launch ever, or the
 * user has just upgraded — we ask GitHub for the release notes that ship
 * with the current version and return them.
 *
 * Failure modes are silent on purpose: if we're offline, if GitHub
 * returns 404 (release not yet published, or running a dev build that
 * doesn't have a release on disk), if anything in the chain throws — we
 * just don't show the modal. The user will see it next time, or never,
 * but their session is never blocked.
 *
 * [markSeen] is called by the modal's dismiss button so the same version
 * never re-prompts on subsequent launches.
 */
class ReleaseNotesService(
    private val updateClient: UpdateClient,
    private val settingsRepository: CommonSettingsRepository,
) {

    /**
     * Returns the [AppRelease] to show, or null when:
     *  - the user has already seen the current version's notes, or
     *  - the GitHub lookup failed (offline, 404, malformed body, …).
     */
    suspend fun fetchIfUnseen(currentVersion: AppVersion): AppRelease? {
        val lastSeen = settingsRepository.getLastSeenReleaseNotesVersion().first()
        if (lastSeen == currentVersion.toString()) return null

        val tag = "v$currentVersion"
        return try {
            val release = updateClient.getKomeliaReleaseByTag(tag)
            AppRelease(
                version = AppVersion.fromString(release.tagName),
                publishDate = release.publishedAt,
                // GitHub returns body with \r\n on some platforms; the
                // markdown renderer trips on those. Match
                // AndroidAppUpdater.toAppRelease's normalisation.
                releaseNotesBody = release.body.replace("\r", ""),
                htmlUrl = release.htmlUrl,
                assetName = null,
                assetUrl = null,
            )
        } catch (e: Throwable) {
            logger.debug(e) { "Release notes lookup failed for $tag — modal will not show this session" }
            null
        }
    }

    /** Mark [version] as acknowledged so the modal doesn't show again for it. */
    suspend fun markSeen(version: AppVersion) {
        settingsRepository.putLastSeenReleaseNotesVersion(version.toString())
    }
}
