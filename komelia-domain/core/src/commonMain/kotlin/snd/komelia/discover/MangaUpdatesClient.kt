package snd.komelia.discover

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val logger = KotlinLogging.logger {}

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
        return try {
            ktor.get("$BASE/series/$seriesId").body<MangaUpdatesSeries>()
        } catch (t: Throwable) {
            currentCoroutineContext().ensureActive()
            // Logged, never swallowed. The first pass on the tablet resolved 1
            // series out of 43 and could not say why, because this returned a
            // bare null and so did its caller.
            logger.warn { "MangaUpdates series $seriesId failed: ${t::class.simpleName}: ${t.message}" }
            null
        }
    }

    /**
     * Best match for [title], or null when nothing on the other side is
     * convincingly the same series.
     *
     * The fallback for series whose Komga metadata carries no MangaUpdates
     * link. Every result is put through [titleMatches] rather than trusting
     * the ranking: this search never returns nothing, so an unchecked first
     * result is an answer even when the series does not exist there at all.
     * Returning null is the honest outcome for most of a franco-belgian
     * shelf.
     */
    suspend fun searchOne(title: String): MangaUpdatesSeries? {
        if (title.isBlank()) return null
        return try {
            ktor.post("$BASE/series/search") {
                contentType(ContentType.Application.Json)
                setBody(SearchRequest(search = title, perpage = SEARCH_PAGE_SIZE))
            }.body<SearchResponse>().results
                .firstNotNullOfOrNull { result ->
                    val record = result.record
                    // Scans the page rather than stopping at the first result:
                    // when the top hit is noise, a lower one is sometimes the
                    // real series.
                    if (record != null && titleMatches(title, record.title, result.hitTitle)) record
                    else null
                }
        } catch (t: Throwable) {
            currentCoroutineContext().ensureActive()
            logger.warn { "MangaUpdates search [$title] failed: ${t::class.simpleName}: ${t.message}" }
            null
        }
    }

    companion object {
        private const val BASE = "https://api.mangaupdates.com/v1"

        /** The API ignores perpage=1 and answers 25 anyway; 5 it honours. */
        private const val SEARCH_PAGE_SIZE = 5

        /**
         * The Json this API has to be read with. Owned here rather than left
         * to whoever wires the client, so the app and the tests cannot drift
         * apart on it.
         *
         * coerceInputValues is the load-bearing setting: MangaUpdates sends a
         * literal null for any field it has no value for — measured on one
         * live search response, both image.url.original and bayesian_rating —
         * and kotlinx fails the WHOLE payload on the first one, not the entry
         * that carried it. One coverless series among 25 results returned
         * nothing at all, which is what made the first tablet pass resolve 1
         * series out of 43. Patching the fields one at a time is whack-a-mole;
         * this covers every field that has a default.
         */
        val json: Json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

        /**
         * The id inside a mangaupdates.com link, or null.
         *
         * Rare in this library — its trackers are manga-news, Nautiljon and
         * Bedetheque — but free when present: no request, no guessing. Both
         * shapes appear in the wild: the modern
         * `/series/<base36>/<slug>` and the older `?id=<decimal>`.
         *
         * The modern slug is the numeric id written in base 36, and the API
         * takes only the decimal form — measured 2026-09-10: GET
         * /v1/series/0me0jrx answers 405, GET /v1/series/1353796125 answers 200
         * for the same series. Passing the slug through untouched is what
         * wasted the only link the first tablet pass managed to find.
         */
        fun idFromUrl(url: String): String? {
            if (!url.contains("mangaupdates.com", ignoreCase = true)) return null
            Regex("""[?&]id=(\d+)""").find(url)?.let { return it.groupValues[1] }
            Regex("""/series/([A-Za-z0-9]+)""").find(url)?.let { match ->
                return match.groupValues[1].lowercase().toLongOrNull(radix = 36)?.toString()
            }
            return null
        }
    }
}

@Serializable
internal data class SearchRequest(val search: String, val perpage: Int)

@Serializable
internal data class SearchResponse(val results: List<SearchResult> = emptyList())

@Serializable
internal data class SearchResult(
    val record: MangaUpdatesSeries? = null,
    /** The alternative title the API matched on — the whole basis of the guard. */
    @SerialName("hit_title") val hitTitle: String? = null,
)

@Serializable
data class MangaUpdatesSeries(
    @SerialName("series_id") val seriesId: Long = 0,
    val title: String = "",
    val url: String = "",
    val year: String = "",
    @SerialName("bayesian_rating") val rating: Double = 0.0,
    @SerialName("rating_votes") val ratingVotes: Int = 0,
    val type: String = "",
    /** Free text, several paragraphs, sometimes with the publisher's credit line. */
    val description: String = "",
    /** "27 Volumes (Complete)" — publication state as one human string. */
    val status: String = "",
    val completed: Boolean = false,
    val image: MangaUpdatesImage? = null,
    val genres: List<MangaUpdatesGenre> = emptyList(),
    val authors: List<MangaUpdatesAuthor> = emptyList(),
    val publishers: List<MangaUpdatesPublisher> = emptyList(),
    /**
     * Every other name the series is known by. The reason the "do I own this"
     * check can work at all: the local shelf says "Parasite" where the source
     * says "Kiseijuu", and only this list bridges the two.
     */
    val associated: List<MangaUpdatesTitle> = emptyList(),
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

    /**
     * Prose rather than comics — "Novel" and "Light Novel" both appear here.
     *
     * Deliberately out of scope: this reader is for manga and comics, and a
     * novel suggestion is one the user cannot act on. Measured at 9 of 54
     * candidates on the real taste profile, so it is not a rounding error.
     */
    val isNovel: Boolean
        get() = type.contains("novel", ignoreCase = true)
}

@Serializable
data class MangaUpdatesTitle(val title: String = "")

@Serializable
data class MangaUpdatesGenre(val genre: String = "")

@Serializable
data class MangaUpdatesAuthor(
    val name: String = "",
    /** "Author", "Artist" — worth keeping apart on a card. */
    val type: String = "",
)

@Serializable
data class MangaUpdatesPublisher(
    @SerialName("publisher_name") val publisherName: String = "",
    val type: String = "",
)

@Serializable
data class MangaUpdatesImage(val url: MangaUpdatesImageUrl? = null)

/**
 * Both members are nullable because the API really sends
 * `{"original":null,"thumb":null}` for a series with no cover, and a
 * non-nullable String there failed the ENTIRE response rather than that one
 * entry. Measured on a live search payload: one such result out of 25 killed
 * all 25, which is why the first tablet pass resolved almost nothing.
 */
@Serializable
data class MangaUpdatesImageUrl(val original: String? = null, val thumb: String? = null)

@Serializable
data class MangaUpdatesRecommendation(
    @SerialName("series_name") val seriesName: String = "",
    @SerialName("series_id") val seriesId: Long = 0,
    @SerialName("series_url") val seriesUrl: String = "",
    @SerialName("series_image") val seriesImage: MangaUpdatesImage? = null,
    /** How many readers made this link. Used directly as the base score. */
    val weight: Int = 0,
)
