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
import snd.komelia.links.SeriesLinksRepository
import snd.komelia.links.SeriesRelationType
import snd.komelia.settings.CommonSettingsRepository
import snd.komelia.ui.LoadState
import snd.komga.client.common.KomgaPageRequest
import snd.komga.client.search.KomgaSearchCondition
import snd.komga.client.search.allOfSeries
import snd.komga.client.series.KomgaSeries
import snd.komga.client.series.KomgaSeriesId

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
            relations = linksRepository.relationsOf(current.id)
                .groupBy { it.type }
                .mapValues { (_, rels) -> rels.mapNotNull { resolve(it.series) } }
                .filterValues { it.isNotEmpty() }
            mutableState.value = LoadState.Success(Unit)
        }.onFailure { mutableState.value = LoadState.Error(it) }
    }

    /** Resolve a linked id to a series; null if it no longer exists on the server. */
    private suspend fun resolve(id: KomgaSeriesId): KomgaSeries? =
        runCatching { seriesApi.getOneSeries(id) }.getOrNull()

    fun linkVersion(other: KomgaSeriesId) = act { current -> linksRepository.linkVersion(current, other) }
    fun unlinkVersion(other: KomgaSeriesId) = act { linksRepository.unlinkVersion(other) }
    fun linkRelation(other: KomgaSeriesId, type: SeriesRelationType) =
        act { current -> linksRepository.linkRelation(current, other, type) }
    fun unlinkRelation(other: KomgaSeriesId) = act { current -> linksRepository.unlinkRelation(current, other) }

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
        val (root, suggestions) = collectFranchise(media)
        val franchise = franchiseTokens(root, current)
        val taken = excludedIds().toMutableSet().apply { current?.id?.value?.let { add(it) } }
        val rows = mutableListOf<AniListSuggestionRow>()
        var ignored = 0
        for (suggestion in suggestions) {
            val match = matchInLibrary(suggestion.node, taken, franchise)
            if (match == null) {
                ignored++
                continue
            }
            taken += match.id.value
            rows += AniListSuggestionRow(
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
    private suspend fun collectFranchise(media: AniListMedia): Pair<AniListMedia, List<AniListLinkSuggestion>> {
        var root = aniListClient.relations(media.id) ?: media
        val parent = root.relations?.edges
            ?.firstOrNull { it.relationType == "PARENT" && it.node?.type == "MANGA" }
            ?.node
        if (parent != null) root = aniListClient.relations(parent.id) ?: parent

        val collected = LinkedHashMap<Int, AniListLinkSuggestion>()
        val expanded = mutableSetOf(root.id)
        var frontier = listOf(root)
        repeat(2) {
            val next = mutableListOf<AniListMedia>()
            for (node in frontier) {
                val full = if (node.id == root.id) root else (aniListClient.relations(node.id) ?: node)
                for (suggestion in full.linkSuggestions()) {
                    val id = suggestion.node.id
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
        return root to collected.values.toList()
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
        val checked = analysis?.rows?.filter { it.checked }.orEmpty()
        analysis = null
        if (checked.isEmpty()) return
        screenModelScope.launch {
            notifications.runCatchingToNotifications {
                checked.forEach { linksRepository.linkRelation(currentId, it.series.id, it.type) }
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
)

/** One proposed link: a library series + the auto-detected (editable) type. */
data class AniListSuggestionRow(
    val anilistTitle: String,
    val series: KomgaSeries,
    val type: SeriesRelationType,
    val checked: Boolean = true,
)

/** Accept a library match at/above this coverage score; below it, treat as no match. */
private const val MIN_MATCH_SCORE = 0.5

/** Stop searching further title variants once a match this strong is found. */
private const val STRONG_MATCH_SCORE = 0.85

/** Cap on franchise nodes expanded during the depth-2 relation crawl. */
private const val MAX_FRANCHISE_NODES = 24

/** Lucene query-syntax characters that must not reach Komga's full-text search. */
private val luceneSpecials = Regex("""[":!+\-*?~^()\[\]{}\\/&|]""")
private val whitespace = Regex("""\s+""")
