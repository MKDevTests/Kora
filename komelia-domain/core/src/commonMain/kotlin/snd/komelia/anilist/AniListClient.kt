package snd.komelia.anilist

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import snd.komelia.links.SeriesRelationType

/**
 * Minimal read-only client for the public AniList GraphQL API
 * (https://graphql.anilist.co) — no API key, no account, no OAuth needed for
 * public media/relation data.
 *
 * Used as an OPT-IN suggestion source for series links: given a Komga series
 * title we resolve an AniList manga entry, then read its direct `relations`
 * (depth 1) to propose sequels/prequels/spin-offs the user can confirm. The
 * injected [ktor] must have ContentNegotiation(json) installed (wired in
 * AppModule, same as UpdateClient).
 *
 * AniList ToS: fine for personal/in-app use; no bulk hoarding / no storing a
 * copy of the database — so we only fetch on demand, depth 1, never crawl.
 */
class AniListClient(
    private val ktor: HttpClient,
) {
    /** Best-match manga candidates for [title] (SEARCH_MATCH order). */
    suspend fun search(title: String): List<AniListMedia> {
        if (title.isBlank()) return emptyList()
        val response = postGraphQl(SearchRequest(SEARCH_QUERY, SearchVars(title)))
        return response.data?.page?.media ?: emptyList()
    }

    /** The media [mediaId] with its direct relation edges, or null if not found. */
    suspend fun relations(mediaId: Int): AniListMedia? {
        val response = postGraphQl(RelationsRequest(RELATIONS_QUERY, IdVars(mediaId)))
        return response.data?.media
    }

    private suspend inline fun <reified B> postGraphQl(body: B): AniListResponse {
        var attempt = 0
        while (true) {
            try {
                val response: HttpResponse = ktor.post(ANILIST_ENDPOINT) {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
                // Honoured only when the client isn't configured with
                // expectSuccess (otherwise a 429 surfaces as the exception below).
                if (response.status.value == 429 && attempt < MAX_RETRIES) {
                    delay(retryAfterMillis(response.headers["Retry-After"]))
                    attempt++
                    continue
                }
                return response.body()
            } catch (e: ClientRequestException) {
                if (e.response.status.value == 429 && attempt < MAX_RETRIES) {
                    delay(retryAfterMillis(e.response.headers["Retry-After"]))
                    attempt++
                    continue
                }
                throw e
            }
        }
    }

    private companion object {
        const val ANILIST_ENDPOINT = "https://graphql.anilist.co"
        const val MAX_RETRIES = 2

        // No EXTRA-language equivalent: AniList stores one entry per work with
        // romaji/english/native + synonyms; we let SEARCH_MATCH do the fuzzy work.
        val SEARCH_QUERY = """
            query (${'$'}search: String!) {
              Page(page: 1, perPage: 8) {
                media(search: ${'$'}search, type: MANGA, sort: [SEARCH_MATCH]) {
                  id
                  idMal
                  type
                  format
                  countryOfOrigin
                  title { romaji english native }
                  synonyms
                }
              }
            }
        """.trimIndent()

        // version: 2 is required for SOURCE/COMPILATION/CONTAINS; we still only
        // keep a small subset (see toSeriesRelationType). Depth 1 only.
        val RELATIONS_QUERY = """
            query (${'$'}id: Int!) {
              Media(id: ${'$'}id, type: MANGA) {
                id
                title { romaji english native }
                relations {
                  edges {
                    relationType(version: 2)
                    node {
                      id
                      idMal
                      type
                      format
                      countryOfOrigin
                      title { romaji english native }
                      synonyms
                    }
                  }
                }
              }
            }
        """.trimIndent()
    }
}

/** Parse a `Retry-After` (seconds) header into a clamped delay in millis. */
private fun retryAfterMillis(header: String?): Long =
    (header?.toLongOrNull() ?: 2L).coerceIn(1L, 30L) * 1000L

// -- GraphQL request bodies --

@Serializable
private data class SearchVars(val search: String)

@Serializable
private data class IdVars(val id: Int)

@Serializable
private data class SearchRequest(val query: String, val variables: SearchVars)

@Serializable
private data class RelationsRequest(val query: String, val variables: IdVars)

// -- GraphQL response models --

@Serializable
data class AniListResponse(val data: AniListData? = null)

@Serializable
data class AniListData(
    @SerialName("Page") val page: AniListPage? = null,
    @SerialName("Media") val media: AniListMedia? = null,
)

@Serializable
data class AniListPage(val media: List<AniListMedia> = emptyList())

@Serializable
data class AniListMedia(
    val id: Int,
    val idMal: Int? = null,
    val type: String? = null,
    val format: String? = null,
    val countryOfOrigin: String? = null,
    val title: AniListTitle = AniListTitle(),
    val synonyms: List<String> = emptyList(),
    val relations: AniListRelations? = null,
) {
    /** First non-blank of romaji / english / native, for display + Komga search. */
    val displayTitle: String?
        get() = title.romaji?.takeIf { it.isNotBlank() }
            ?: title.english?.takeIf { it.isNotBlank() }
            ?: title.native?.takeIf { it.isNotBlank() }
}

@Serializable
data class AniListTitle(
    val romaji: String? = null,
    val english: String? = null,
    val native: String? = null,
)

@Serializable
data class AniListRelations(val edges: List<AniListRelationEdge> = emptyList())

@Serializable
data class AniListRelationEdge(
    val relationType: String? = null,
    val node: AniListMedia? = null,
)

/**
 * A linkable relation extracted from an AniList entry: the related manga node
 * and the auto-detected Kora relation type (user-overridable in the popup).
 */
data class AniListLinkSuggestion(
    val node: AniListMedia,
    val suggestedType: SeriesRelationType,
)

/**
 * Map an AniList `relationType` to Kora's [SeriesRelationType] from the viewing
 * series' perspective, or null to skip it.
 *
 * Direction note: an AniList edge `relationType` describes how the *node*
 * relates to the queried series (e.g. Naruto -> SEQUEL -> Boruto means "Boruto
 * is the sequel of Naruto"), which matches `linkRelation(current, node, type)`.
 *
 * Kept: SEQUEL/PREQUEL/SPIN_OFF (direct), PARENT (the node is the main story →
 * MAIN_STORY), SIDE_STORY (no exact match → SPIN_OFF by default). Everything
 * else (ADAPTATION, SOURCE, ALTERNATIVE, SUMMARY, COMPILATION, CONTAINS,
 * CHARACTER, OTHER) is dropped — too noisy / cross-media for series links.
 */
fun toSeriesRelationType(aniListRelationType: String?): SeriesRelationType? =
    when (aniListRelationType) {
        "SEQUEL" -> SeriesRelationType.SEQUEL
        "PREQUEL" -> SeriesRelationType.PREQUEL
        "SPIN_OFF" -> SeriesRelationType.SPIN_OFF
        "PARENT" -> SeriesRelationType.MAIN_STORY
        "SIDE_STORY" -> SeriesRelationType.SPIN_OFF
        else -> null
    }

/**
 * Keep only manga the user could plausibly own: type MANGA and a print format
 * (MANGA / ONE_SHOT). Drops anime adaptations and light NOVEL entries, which
 * AniList also returns under type=MANGA (e.g. "NARUTO Hiden Series").
 */
fun isLinkableMangaNode(node: AniListMedia): Boolean =
    node.type == "MANGA" && node.format in LINKABLE_FORMATS

private val LINKABLE_FORMATS = setOf("MANGA", "ONE_SHOT")

/**
 * Extract the linkable relations from a resolved AniList media: filter to
 * owned-format manga, map the relation type, drop the rest. Pure (no network)
 * so it can be unit-tested.
 */
fun AniListMedia.linkSuggestions(): List<AniListLinkSuggestion> =
    relations?.edges.orEmpty().mapNotNull { edge ->
        val node = edge.node ?: return@mapNotNull null
        if (!isLinkableMangaNode(node)) return@mapNotNull null
        val type = toSeriesRelationType(edge.relationType) ?: return@mapNotNull null
        AniListLinkSuggestion(node, type)
    }
