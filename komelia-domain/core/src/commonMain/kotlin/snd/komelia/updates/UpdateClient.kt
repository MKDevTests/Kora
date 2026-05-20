package snd.komelia.updates

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

// Point at MKDevTests/Kora (this fork) rather than the upstream
// eserero/Sipurra. Upstream releases ship the original Komelia/Sipurra
// APK, which would clobber every Kora-specific feature on update. The
// scripts/release-kora.sh helper publishes tagged releases here that
// the in-app auto-updater can safely consume.
private const val komeliaBaseUrl = "https://api.github.com/repos/MKDevTests/Kora"
private const val onnxRuntimeBaseUrl = "https://api.github.com/repos/microsoft/onnxruntime"

class UpdateClient(
    private val ktor: HttpClient,
    private val ktorWithoutCache: HttpClient
) {

    suspend fun getKomeliaReleases(): List<GithubRelease> {
        return ktor.get("$komeliaBaseUrl/releases") {
            parameter("per_page", 5)
        }.body()
    }

    suspend fun getKomeliaLatestRelease(): GithubRelease {
        return ktor.get("$komeliaBaseUrl/releases/latest").body()
    }

    /**
     * Fetch a single release by its tag name (e.g. "v1.0.3"). Used by
     * the "What's new" modal to look up the notes for the *currently
     * running* version so we can show them on first launch after an
     * upgrade.
     */
    suspend fun getKomeliaReleaseByTag(tagName: String): GithubRelease {
        return ktor.get("$komeliaBaseUrl/releases/tags/$tagName").body()
    }

    suspend fun getOnnxRuntimeRelease(tagName: String): GithubRelease {
        return ktor.get("$onnxRuntimeBaseUrl/releases/tags/$tagName").body()
    }

    suspend fun streamFile(url: String, block: suspend (response: HttpResponse) -> Unit) {
        ktorWithoutCache.prepareGet(url).execute(block)
    }
}

@Serializable
data class GithubRelease(
    val id: Int,
    @SerialName("published_at")
    val publishedAt: Instant,
    @SerialName("tag_name")
    val tagName: String,
    @SerialName("html_url")
    val htmlUrl: String,
    val body: String,
    val assets: List<GithubReleaseAsset>
)

@Serializable
data class GithubReleaseAsset(
    val id: Int,
    val name: String,
    @SerialName("content_type")
    val contentType: String,
    @SerialName("browser_download_url")
    val browserDownloadUrl: String
)