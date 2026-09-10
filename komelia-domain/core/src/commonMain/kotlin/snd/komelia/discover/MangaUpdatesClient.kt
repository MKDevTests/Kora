package snd.komelia.discover

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Minimal read-only client for the public MangaUpdates API
 * (https://api.mangaupdates.com/v1) — no key, no account.
 *
 * Chosen over AniList and MyAnimeList after measuring all three on 2026-09-10:
 * AniList answered 403 with "The AniList API has been temporarily disabled due
 * to severe stability issues", Jikan timed out twice at 504, and MangaDex and
 * Kitsu answer but publish no recommendations at all. MangaUpdates answered,
 * and answers with what this feature actually needs.
 *
 * What makes it the right source here is that ONE request carries everything:
 * a series' recommendations come back with the recommended title, its image,
 * its id and a `weight` (the number of readers who made the link), alongside
 * the series' own genres, year and rating. Showing a suggestion therefore
 * costs no further request — which is what makes a tablet-friendly budget
 * possible at all.
 *
 * Measured: five sequential requests in 1.4s with no refusal. The caller still
 * limits itself to two in flight; this is somebody's free service.
 *
 * Their recommendations are voted by readers rather than computed, which is
 * the point: a third of this library carries no genre, tag, publisher or
 * author, and nothing can be scored as similar to nothing.
 */
class MangaUpdatesClient(
    private val ktor: HttpClient,
) {
    /** The series [seriesId], with its recommendations, or null if it is gone. */
    suspend fun series(seriesId: String): MangaUpdatesSeries? {
        if (seriesId.isBlank()) return null
        return runCatching {
            ktor.get("$BASE/series/$seriesId").body<MangaUpdatesSeries>()
        }.getOrNull()
    }

    /**
     * Best match for [title], or null.
     *
     * The fallback for series whose Komga metadata carries no MangaUpdates
     * link. Measured on three real titles, three hits — including "Negima",
     * which resolves to "Mahou Sensei Negima!" rather than failing on the
     * shortened shelf name.
     */
    suspend fun searchOne(title: String): MangaUpdatesSeries? {
        if (title.isBlank()) return null
        return runCatching {
            ktor.post("$BASE/series/search") {
                contentType(ContentType.Application.Json)
                setBody(SearchRequest(search = title, perpage = 1))
            }.body<SearchResponse>().results.firstOrNull()?.record
        }.getOrNull()
    }

    companion object {
        private const val BASE = "https://api.mangaupdates.com/v1"

        /**
         * The id inside a mangaupdates.com link, or null.
         *
         * Komga metadata already carries these links on much of the library —
         * 22 of 45 sampled series — so most of the mapping is a string parse
         * rather than a request. Both shapes appear in the wild: the modern
         * `/series/<base36>/<slug>` and the older `?id=<decimal>`.
         */
        fun idFromUrl(url: String): String? {
            if (!url.contains("mangaupdates.com", ignoreCase = true)) return null
            Regex("""[?&]id=(\d+)""").find(url)?.let { return it.groupValues[1] }
            Regex("""/series/([A-Za-z0-9]+)""").find(url)?.let { return it.groupValues[1] }
            return null
        }
    }
}

@Serializable
private data class SearchRequest(val search: String, val perpage: Int)

@Serializable
private data class SearchResponse(val results: List<SearchResult> = emptyList())

@Serializable
private data class SearchResult(val record: MangaUpdatesSeries? = null)

@Serializable
data class MangaUpdatesSeries(
    @SerialName("series_id") val seriesId: Long = 0,
    val title: String = "",
    val url: String = "",
    val year: String = "",
    @SerialName("bayesian_rating") val rating: Double = 0.0,
    @SerialName("rating_votes") val ratingVotes: Int = 0,
    val type: String = "",
    val image: MangaUpdatesImage? = null,
    val recommendations: List<MangaUpdatesRecommendation> = emptyList(),
    @SerialName("category_recommendations") val categoryRecommendations: List<MangaUpdatesRecommendation> = emptyList(),
) {
    /**
     * Both lists, best first.
     *
     * `recommendations` are reader-submitted links; `category_recommendations`
     * come from category votes and are weaker, so they follow rather than
     * interleave.
     */
    val allRecommendations: List<MangaUpdatesRecommendation>
        get() = recommendations + categoryRecommendations
}

@Serializable
data class MangaUpdatesImage(val url: MangaUpdatesImageUrl? = null)

@Serializable
data class MangaUpdatesImageUrl(val original: String = "", val thumb: String = "")

@Serializable
data class MangaUpdatesRecommendation(
    @SerialName("series_name") val seriesName: String = "",
    @SerialName("series_id") val seriesId: Long = 0,
    @SerialName("series_url") val seriesUrl: String = "",
    @SerialName("series_image") val seriesImage: MangaUpdatesImage? = null,
    /** How many readers made this link. Used directly as the base score. */
    val weight: Int = 0,
)
