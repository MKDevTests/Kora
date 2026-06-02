package snd.komelia.ui.series

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import snd.komelia.AppNotifications
import snd.komelia.anilist.AniListClient
import snd.komelia.anilist.AniListLinkSuggestion
import snd.komelia.anilist.AniListMedia
import snd.komelia.anilist.linkSuggestions
import snd.komelia.komga.api.KomgaSeriesApi
import snd.komelia.links.KoraLinkCodec
import snd.komelia.links.SeriesLinksRepository
import snd.komelia.links.SeriesRelationType
import snd.komelia.settings.CommonSettingsRepository
import snd.komelia.ui.LoadState
import snd.komga.client.common.KomgaPageRequest
import snd.komga.client.common.KomgaWebLink
import snd.komga.client.common.patch
import snd.komga.client.common.patchLists
import snd.komga.client.search.KomgaSearchCondition
import snd.komga.client.search.allOfSeries
import snd.komga.client.series.KomgaSeries
import snd.komga.client.series.KomgaSeriesId
import snd.komga.client.series.KomgaSeriesMetadataUpdateRequest
import snd.komga.client.user.KomgaUser
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Process-wide "a link changed" signal so every open series-Links screen
 * refreshes after a link/unlink anywhere — both sides of a relation update
 * live, even when the other series' screen is already on the back stack.
 */
private object SeriesLinksChanges {
    val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    fun notifyChanged() {
        changes.tryEmit(Unit)
    }
}

/**
 * State for the series "Links" tab: the current series' other versions
 * (language/edition) and its typed related series (sequel/prequel/spin-off/
 * related), resolved to full [KomgaSeries] for display. Backed by the local
 * [SeriesLinksRepository]; links are created/removed here too.
 */
class SeriesLinksState(
    val series: StateFlow<KomgaSeries?>,
    private val notifications: AppNotifications,
    private val seriesApi: KomgaSeriesApi,
    private val linksRepository: SeriesLinksRepository,
    private val aniListClient: AniListClient,
    private val settingsRepository: CommonSettingsRepository,
    private val authenticatedUser: StateFlow<KomgaUser?>,
    private val screenModelScope: CoroutineScope,
    val cardWidth: StateFlow<Dp>,
) {
    private val mutableState = MutableStateFlow<LoadState<Unit>>(LoadState.Uninitialized)
    val state = mutableState.asStateFlow()

    var versions by mutableStateOf<List<KomgaSeries>>(emptyList())
        private set

    /** Related series grouped by relation type (from this series' perspective). */
    var relations by mutableStateOf<Map<SeriesRelationType, List<KomgaSeries>>>(emptyMap())
        private set

    /** Ids of related series coming from the shared Komga layer (badge + unlink gating). */
    var sharedRelationIds by mutableStateOf<Set<String>>(emptySet())
        private set

    /** Whether the Komga-shared link layer is active (opt-in). Drives badges in the UI. */
    val shareLinksEnabled: StateFlow<Boolean> =
        settingsRepository.getShareLinksViaKomga().stateIn(screenModelScope, SharingStarted.Eagerly, false)

    private fun isAdmin(): Boolean = authenticatedUser.value?.roleAdmin() == true

    /** Writes go to the shared Komga layer only when sharing is on AND the user is admin. */
    private fun shareViaKomga(): Boolean = shareLinksEnabled.value && isAdmin()

    suspend fun initialize() {
        if (mutableState.value != LoadState.Uninitialized) return
        load()
        // Refresh whenever any link changes (this series or any other).
        SeriesLinksChanges.changes.onEach { load() }.launchIn(screenModelScope)
    }

    fun reload() {
        screenModelScope.launch { load() }
    }

    private suspend fun load() {
        notifications.runCatchingToNotifications {
            mutableState.value = LoadState.Loading
            val current = series.filterNotNull().first()
            versions = linksRepository.versionsOf(current.id).mapNotNull { resolve(it) }

            val localRelations = linksRepository.relationsOf(current.id)
            val sharedRelations =
                if (shareLinksEnabled.value) current.metadata.links.mapNotNull { KoraLinkCodec.parse(it) }
                else emptyList()
            sharedRelationIds = sharedRelations.map { it.target.value }.toSet()

            // Merge local + shared by target id (shared type wins on conflict).
            val byId = LinkedHashMap<String, Pair<KomgaSeriesId, SeriesRelationType>>()
            localRelations.forEach { byId[it.series.value] = it.series to it.type }
            sharedRelations.forEach { byId[it.target.value] = it.target to it.type }

            relations = byId.values
                .mapNotNull { (id, type) -> resolve(id)?.let { type to it } }
                .groupBy({ it.first }, { it.second })
                .filterValues { it.isNotEmpty() }
            mutableState.value = LoadState.Success(Unit)
        }.onFailure { mutableState.value = LoadState.Error(it) }
    }

    /** Resolve a linked id to a series; null if it no longer exists on the server. */
    private suspend fun resolve(id: KomgaSeriesId): KomgaSeries? =
        runCatching { seriesApi.getOneSeries(id) }.getOrNull()

    // Versions stay local-only for now; only typed relations are shared via Komga.
    fun linkVersion(other: KomgaSeriesId) = act { current -> linksRepository.linkVersion(current, other) }
    fun unlinkVersion(other: KomgaSeriesId) = act { linksRepository.unlinkVersion(other) }

    fun linkRelation(other: KomgaSeriesId, type: SeriesRelationType) =
        act { current -> doLinkRelation(current, other, type) }

    fun unlinkRelation(other: KomgaSeriesId) = act { current ->
        when {
            other.value !in sharedRelationIds -> linksRepository.unlinkRelation(current, other)
            isAdmin() -> komgaUnlinkRelation(current, other)
            else -> error("Only an admin can remove a link shared on the server.")
        }
    }

    /** Route a new relation to the shared Komga layer (admin + sharing on) or the local store. */
    private suspend fun doLinkRelation(from: KomgaSeriesId, to: KomgaSeriesId, type: SeriesRelationType) {
        if (shareViaKomga()) komgaLinkRelation(from, to, type)
        else linksRepository.linkRelation(from, to, type)
    }

    private suspend fun komgaLinkRelation(from: KomgaSeriesId, to: KomgaSeriesId, type: SeriesRelationType) {
        writeKoraLink(on = from, target = to, type = type)
        writeKoraLink(on = to, target = from, type = type.inverse())
    }

    private suspend fun komgaUnlinkRelation(from: KomgaSeriesId, to: KomgaSeriesId) {
        removeKoraLink(on = from, target = to)
        removeKoraLink(on = to, target = from)
    }

    private suspend fun writeKoraLink(on: KomgaSeriesId, target: KomgaSeriesId, type: SeriesRelationType) {
        val s = seriesApi.getOneSeries(on)
        val kept = s.metadata.links.filterNot { KoraLinkCodec.parse(it)?.target == target }
        seriesApi.update(on, linksUpdate(s, kept + KoraLinkCodec.relationLink(target, type)))
    }

    private suspend fun removeKoraLink(on: KomgaSeriesId, target: KomgaSeriesId) {
        val s = seriesApi.getOneSeries(on)
        val newLinks = s.metadata.links.filterNot { KoraLinkCodec.parse(it)?.target == target }
        if (newLinks.size != s.metadata.links.size) seriesApi.update(on, linksUpdate(s, newLinks))
    }

    /** Update request that touches ONLY links + linksLock, leaving all else unchanged. */
    private fun linksUpdate(s: KomgaSeries, newLinks: List<KomgaWebLink>) =
        KomgaSeriesMetadataUpdateRequest(
            links = patchLists(s.metadata.links, newLinks),
            linksLock = patch(s.metadata.linksLock, true),
        )

    private fun act(block: suspend (current: KomgaSeriesId) -> Unit) {
        val current = series.value?.id ?: return
        screenModelScope.launch {
            notifications.runCatchingToNotifications { block(current) }
            // Drives a reload on this screen AND every other open Links screen.
            SeriesLinksChanges.notifyChanged()
        }
    }

    /** Search series by text for the "add link" picker (excludes the current series). */
    suspend fun search(query: String): List<KomgaSeries> {
        if (query.isBlank()) return emptyList()
        val currentId = series.value?.id?.value
        return notifications.runCatchingToNotifications {
            seriesApi.getSeriesList(
                conditionBuilder = allOfSeries {},
                fulltextSearch = query,
                pageRequest = KomgaPageRequest(size = 30),
            ).content
        }.getOrDefault(emptyList()).filter { it.id.value != currentId }
    }

    /**
     * Candidate links found from the current series' authors (same writer/artist)
     * and title similarity. The user confirms each — nothing is linked
     * automatically. Already-linked series and the series itself are excluded.
     */
    suspend fun suggestions(): List<KomgaSeries> {
        val current = series.value ?: return emptyList()
        val title = current.metadata.title
        return notifications.runCatchingToNotifications {
            val byName = seriesApi.getSeriesList(
                conditionBuilder = allOfSeries {},
                fulltextSearch = significantQuery(title),
                pageRequest = KomgaPageRequest(size = 20),
            ).content
            val authorNames = current.booksMetadata.authors.map { it.name }.distinct().take(3)
            val byAuthor = authorNames.flatMap { name ->
                runCatching {
                    seriesApi.getSeriesList(
                        conditionBuilder = allOfSeries {
                            author { isEqualTo(KomgaSearchCondition.AuthorMatch(name, null)) }
                        },
                        fulltextSearch = null,
                        pageRequest = KomgaPageRequest(size = 20),
                    ).content
                }.getOrDefault(emptyList())
            }
            val excluded = excludedIds() + current.id.value
            (byName + byAuthor)
                .distinctBy { it.id.value }
                .filter { it.id.value !in excluded }
                .sortedByDescending { nameSimilarity(title, it.metadata.title) }
                .take(15)
        }.getOrDefault(emptyList())
    }

    private fun excludedIds(): Set<String> =
        (versions.map { it.id.value } + relations.values.flatten().map { it.id.value }).toSet()

    private fun significantQuery(title: String): String {
        val base = title.substringBefore(":").trim()
        val words = base.split(" ").filter { it.isNotBlank() }
        return if (words.size <= 2) base else words.take(2).joinToString(" ")
    }

    private fun tokenize(s: String): Set<String> =
        s.lowercase().split(Regex("[^a-z0-9]+")).filter { it.length > 1 }.toSet()

    private fun nameSimilarity(a: String, b: String): Double {
        val ta = tokenize(a)
        val tb = tokenize(b)
        if (ta.isEmpty() || tb.isEmpty()) return 0.0
        val intersection = ta.intersect(tb).size.toDouble()
        val union = (ta + tb).size.toDouble()
        return intersection / union
    }

    // -- AniList online link suggestions (opt-in) ---------------------------

    /** Opt-in flag gating the "Analyze with AniList" action. */
    val aniListEnabled: StateFlow<Boolean> =
        settingsRepository.getAniListLinkSuggestionsEnabled()
            .stateIn(screenModelScope, SharingStarted.Eagerly, false)

    /** Non-null while the AniList analysis dialog is open. */
    var analysis by mutableStateOf<AniListAnalysis?>(null)
        private set

    fun analyze() {
        val current = series.value ?: return
        analysis = AniListAnalysis(loading = true)
        screenModelScope.launch {
            analysis = try {
                val candidates = aniListClient.search(sourceSearchQuery(current.metadata.title))
                if (candidates.isEmpty()) {
                    AniListAnalysis(error = "No AniList match for “${current.metadata.title}”. Search AniList manually below.")
                } else {
                    buildAnalysis(candidates.first(), candidates)
                }
            } catch (e: Exception) {
                AniListAnalysis(error = e.message ?: "AniList request failed")
            }
        }
    }

    /** Strip edition / sub-title suffixes so the source search hits the base work. */
    private fun sourceSearchQuery(title: String): String {
        val base = title.substringBefore(" - ").substringBefore(" (").substringBefore(":").trim()
        return base.ifBlank { title }
    }

    /** Manual AniList source search — recourse when recognition is wrong or empty. */
    fun searchSource(query: String) {
        if (query.isBlank()) return
        screenModelScope.launch {
            val results = runCatching { aniListClient.search(query) }.getOrDefault(emptyList())
            analysis = (analysis ?: AniListAnalysis()).copy(
                sourceCandidates = results,
                error = if (results.isEmpty()) "No AniList match for “$query”." else null,
            )
        }
    }

    /** Re-run with a different source entry (the header "not this one?" picker). */
    fun repickSource(media: AniListMedia) {
        val candidates = analysis?.sourceCandidates ?: listOf(media)
        analysis = analysis?.copy(loading = true, error = null)
        screenModelScope.launch {
            analysis = try {
                buildAnalysis(media, candidates)
            } catch (e: Exception) {
                AniListAnalysis(sourceCandidates = candidates, sourceMedia = media, error = e.message)
            }
        }
    }

    private suspend fun buildAnalysis(media: AniListMedia, candidates: List<AniListMedia>): AniListAnalysis {
        val current = series.value
        val (root, suggestions, edges) = collectFranchise(media)
        val franchise = franchiseTokens(root, current)
        val taken = excludedIds().toMutableSet().apply { current?.id?.value?.let { add(it) } }
        logger.info { "[AniListLinks] source='${root.displayTitle}' id=${root.id} suggestions=${suggestions.size}" }
        val rows = mutableListOf<AniListSuggestionRow>()
        var ignored = 0
        for (suggestion in suggestions) {
            val match = matchInLibrary(suggestion.node, taken, franchise)
            if (match == null) {
                logger.info {
                    "[AniListLinks]  unmatched: '${suggestion.node.displayTitle}' " +
                        "(${suggestion.suggestedType}, format=${suggestion.node.format})"
                }
                ignored++
                continue
            }
            logger.info { "[AniListLinks]  matched: '${suggestion.node.displayTitle}' -> '${match.metadata.title}'" }
            taken += match.id.value
            rows += AniListSuggestionRow(
                anilistId = suggestion.node.id,
                anilistTitle = suggestion.node.displayTitle ?: "?",
                series = match,
                type = suggestion.suggestedType,
            )
        }
        return AniListAnalysis(
            sourceCandidates = candidates,
            sourceMedia = root,
            rows = rows,
            ignoredCount = ignored,
            interEdges = edges,
        )
    }

    /**
     * Resolve the canonical franchise root and collect its related manga to
     * depth 2. AniList sometimes ranks a sub-entry (colored / regional edition)
     * above the main work — that sub-entry's only link is PARENT, so we hop to it
     * to reach the real spin-off list (fixes e.g. Fairy Tail). Depth 2 also
     * catches sequels-of-sequels (Gunnm → Last Order → Mars Chronicle). Bounded
     * by a visited set and a node cap.
     */
    private suspend fun collectFranchise(
        media: AniListMedia,
    ): Triple<AniListMedia, List<AniListLinkSuggestion>, List<FranchiseEdge>> {
        var root = aniListClient.relations(media.id) ?: media
        val parent = root.relations?.edges
            ?.firstOrNull { it.relationType == "PARENT" && it.node?.type == "MANGA" }
            ?.node
        if (parent != null) root = aniListClient.relations(parent.id) ?: parent

        val collected = LinkedHashMap<Int, AniListLinkSuggestion>()
        val edges = mutableListOf<FranchiseEdge>()
        val expanded = mutableSetOf(root.id)
        var frontier = listOf(root)
        repeat(2) {
            val next = mutableListOf<AniListMedia>()
            for (node in frontier) {
                val full = if (node.id == root.id) root else (aniListClient.relations(node.id) ?: node)
                for (suggestion in full.linkSuggestions()) {
                    val id = suggestion.node.id
                    edges += FranchiseEdge(node.id, id, suggestion.suggestedType)
                    if (id == root.id) continue
                    collected.getOrPut(id) { suggestion }
                    if (id !in expanded && expanded.size < MAX_FRANCHISE_NODES) {
                        expanded += id
                        next += suggestion.node
                    }
                }
            }
            frontier = next
        }
        return Triple(root, collected.values.toList(), edges)
    }

    /**
     * Tokens common to the whole franchise (the source series' titles in every
     * language we know). Removed before scoring so matching keys off the
     * distinctive sub-title ("Junior High", "Before the Fall") rather than the
     * shared franchise name — which differs across languages anyway
     * ("Shingeki no Kyojin" / "Attack on Titan" / "L'Attaque des Titans").
     */
    private fun franchiseTokens(media: AniListMedia, current: KomgaSeries?): Set<String> = buildSet {
        addAll(tokenize(media.title.romaji ?: ""))
        addAll(tokenize(media.title.english ?: ""))
        addAll(tokenize(media.title.native ?: ""))
        media.synonyms.forEach { addAll(tokenize(it)) }
        current?.let {
            addAll(tokenize(it.metadata.title))
            it.metadata.alternateTitles.forEach { alt -> addAll(tokenize(alt.title)) }
        }
    }

    /**
     * Best-effort resolve an AniList node to a series already in the library.
     * Searches Komga with every title variant the node carries (romaji / english
     * / native / synonyms) so a library titled in any language can match —
     * AniList synonyms frequently include the localized (e.g. French) title.
     * Queries are stripped of Lucene-special characters (":", "!", "(", …) which
     * would otherwise break Komga's full-text search and silently return nothing.
     */
    private suspend fun matchInLibrary(
        node: AniListMedia,
        excluded: Set<String>,
        franchise: Set<String>,
    ): KomgaSeries? {
        val nodeTitles = (listOfNotNull(node.title.romaji, node.title.english, node.title.native) + node.synonyms)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val queries = nodeTitles.asSequence()
            .map { cleanSearchQuery(it) }
            .filter { it.isNotBlank() }
            .distinct()
            .take(8)
            .toList()

        var best: KomgaSeries? = null
        var bestScore = 0.0
        for (query in queries) {
            for (candidate in search(query)) {
                if (candidate.id.value in excluded) continue
                val score = titleMatchScore(nodeTitles, candidate.metadata.title, franchise)
                if (score > bestScore) {
                    bestScore = score
                    best = candidate
                }
            }
            if (bestScore >= STRONG_MATCH_SCORE) break
        }
        return if (bestScore >= MIN_MATCH_SCORE) best else null
    }

    /** Strip Lucene query syntax so a raw title is safe for Komga full-text search. */
    private fun cleanSearchQuery(title: String): String =
        title.replace(luceneSpecials, " ").replace(whitespace, " ").trim()

    /**
     * Match strength of an AniList node against a library title, keyed on the
     * DISTINCTIVE (non-franchise) tokens so the shared franchise name — which
     * differs across languages — doesn't drive the score. A single shared token
     * counts only when it IS the whole shorter title and is long enough to be
     * distinctive: "Boruto" matches "Boruto: Naruto Next Generations", but
     * "Dead Rock" does not latch onto "Rock Lee" via the common word "rock".
     */
    private fun titleMatchScore(nodeTitles: List<String>, libraryTitle: String, franchise: Set<String>): Double {
        val lib = (tokenize(libraryTitle) - franchise).ifEmpty { tokenize(libraryTitle) }
        if (lib.isEmpty()) return 0.0
        return nodeTitles.maxOfOrNull { title ->
            val node = (tokenize(title) - franchise).ifEmpty { tokenize(title) }
            val shared = lib.intersect(node)
            if (node.isEmpty() || shared.isEmpty()) return@maxOfOrNull 0.0
            val minSize = minOf(lib.size, node.size)
            // Accept a single shared token only when it IS the whole library
            // distinctive title ("Twin", "Boruto", "Mars Chronicle"); reject a
            // lone common word against a 2+ token library name ("Dead Rock").
            val distinctive = shared.size >= 2 || lib.size == 1
            if (distinctive) shared.size.toDouble() / minSize else 0.0
        } ?: 0.0
    }

    fun toggleRow(index: Int) {
        val current = analysis ?: return
        analysis = current.copy(
            rows = current.rows.mapIndexed { i, row -> if (i == index) row.copy(checked = !row.checked) else row }
        )
    }

    fun setRowType(index: Int, type: SeriesRelationType) {
        val current = analysis ?: return
        analysis = current.copy(
            rows = current.rows.mapIndexed { i, row -> if (i == index) row.copy(type = type) else row }
        )
    }

    /** Replace a row's matched series with one the user picked (the "correct" action). */
    fun correctRow(index: Int, newSeries: KomgaSeries) {
        val current = analysis ?: return
        analysis = current.copy(
            rows = current.rows.mapIndexed { i, row ->
                if (i == index) row.copy(series = newSeries, anilistTitle = newSeries.metadata.title, checked = true)
                else row
            }
        )
    }

    fun confirmAnalysis() {
        val currentId = series.value?.id ?: return
        val current = analysis ?: return
        val checked = current.rows.filter { it.checked }
        val edges = current.interEdges
        analysis = null
        if (checked.isEmpty()) return
        screenModelScope.launch {
            notifications.runCatchingToNotifications {
                // Full mesh: interconnect the analyzed series AND every chosen
                // suggestion so the whole franchise is navigable from any member
                // (e.g. 100 Years Quest also links to the spin-offs, not only to
                // Fairy Tail). Pair type = the AniList-known relation if there is
                // one, otherwise RELATED. Never touches a series the user didn't pick.
                val members = (listOf(currentId) + checked.map { it.series.id }).distinctBy { it.value }
                val known = HashMap<Pair<String, String>, SeriesRelationType>()
                checked.forEach { known[currentId.value to it.series.id.value] = it.type }
                val seriesByAniId = checked.associate { it.anilistId to it.series.id }
                edges.forEach { edge ->
                    val from = seriesByAniId[edge.fromId]
                    val to = seriesByAniId[edge.toId]
                    if (from != null && to != null && from.value != to.value) {
                        known[from.value to to.value] = edge.type
                    }
                }
                fun typeBetween(a: KomgaSeriesId, b: KomgaSeriesId): SeriesRelationType =
                    known[a.value to b.value]
                        ?: known[b.value to a.value]?.inverse()
                        ?: SeriesRelationType.RELATED

                if (shareViaKomga()) {
                    // One metadata write per member (batched) with all its franchise links.
                    val memberIds = members.map { it.value }.toSet()
                    members.forEach { m ->
                        val s = seriesApi.getOneSeries(m)
                        val nonKora = s.metadata.links.filterNot { KoraLinkCodec.isKoraLink(it) }
                        val keptKora = s.metadata.links.filter {
                            KoraLinkCodec.isKoraLink(it) && KoraLinkCodec.parse(it)?.target?.value !in memberIds
                        }
                        val mesh = members.filter { it.value != m.value }
                            .map { other -> KoraLinkCodec.relationLink(other, typeBetween(m, other)) }
                        seriesApi.update(m, linksUpdate(s, nonKora + keptKora + mesh))
                    }
                } else {
                    for (i in members.indices) for (j in i + 1 until members.size) {
                        linksRepository.linkRelation(members[i], members[j], typeBetween(members[i], members[j]))
                    }
                }
            }
            SeriesLinksChanges.notifyChanged()
        }
    }

    fun dismissAnalysis() {
        analysis = null
    }
}

/** State of the AniList analysis dialog. */
data class AniListAnalysis(
    val loading: Boolean = false,
    val sourceCandidates: List<AniListMedia> = emptyList(),
    val sourceMedia: AniListMedia? = null,
    val rows: List<AniListSuggestionRow> = emptyList(),
    val ignoredCount: Int = 0,
    val error: String? = null,
    /** AniList-known relations among the franchise nodes, for the chain links. */
    val interEdges: List<FranchiseEdge> = emptyList(),
)

/** One proposed link: a library series + the auto-detected (editable) type. */
data class AniListSuggestionRow(
    val anilistId: Int,
    val anilistTitle: String,
    val series: KomgaSeries,
    val type: SeriesRelationType,
    val checked: Boolean = true,
)

/** An AniList-known relation between two franchise members (by AniList id). */
data class FranchiseEdge(val fromId: Int, val toId: Int, val type: SeriesRelationType)

/** Accept a library match at/above this coverage score; below it, treat as no match. */
private const val MIN_MATCH_SCORE = 0.5

/** Stop searching further title variants once a match this strong is found. */
private const val STRONG_MATCH_SCORE = 0.85

/** Cap on franchise nodes expanded during the depth-2 relation crawl. */
private const val MAX_FRANCHISE_NODES = 24

/** Lucene query-syntax characters that must not reach Komga's full-text search. */
private val luceneSpecials = Regex("""[":!+\-*?~^()\[\]{}\\/&|]""")
private val whitespace = Regex("""\s+""")
