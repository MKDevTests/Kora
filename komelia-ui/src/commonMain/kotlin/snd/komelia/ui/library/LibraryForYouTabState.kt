package snd.komelia.ui.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import snd.komelia.AppNotifications
import snd.komelia.similarity.Feature
import snd.komelia.similarity.TermFamily
import snd.komelia.ui.suggestions.ForYouSuggester
import snd.komga.client.library.KomgaLibrary
import snd.komga.client.series.KomgaSeries

private val logger = KotlinLogging.logger {}

/** One recommendation: the series to show, and why it was picked. */
data class ForYouSuggestion(
    val series: KomgaSeries,
    /** Readable labels, strongest first. The first one is the headline. */
    val reasons: List<String>,
    /** The liked series this pick came from, strongest first. */
    val becauseOf: List<ForYouAttribution> = emptyList(),
)

/** One liked series behind a suggestion: how much it explains, and through what. */
data class ForYouAttribution(
    val seriesId: String,
    val share: Double,
    /** The terms shared with THAT series — what the card shows in its section. */
    val reasons: List<String>,
)

/**
 * What a section is titled after. Data, not a formatted string: the wording is
 * translated, and this class cannot reach a resource — it has no composition.
 */
sealed interface ForYouSectionTitle {
    /** "Because you liked / read / are reading <name>". */
    data class Source(
        val kind: snd.komelia.ui.suggestions.ForYouSourceKind,
        val name: String,
    ) : ForYouSectionTitle

    /** Everything no single series explains. */
    data object More : ForYouSectionTitle
}

/** A titled run of suggestions. A null title means the untitled main grid. */
data class ForYouSection(
    val title: ForYouSectionTitle?,
    val items: List<ForYouSuggestion>,
)

/**
 * State for the library's "For you" tab.
 *
 * The pipeline itself lives in [ForYouSuggester], shared with the Home shelf:
 * two implementations of a taste profile would drift, and the one on Home — the
 * screen people actually look at — would be the one nobody checked.
 */
class LibraryForYouTabState(
    private val library: StateFlow<KomgaLibrary?>,
    private val notifications: AppNotifications,
    private val suggester: ForYouSuggester,
    private val feedbackRepository: snd.komelia.similarity.SuggestionFeedbackRepository,
    private val screenModelScope: CoroutineScope,
    val cardWidth: StateFlow<Dp>,
) {
    var suggestions by mutableStateOf<List<ForYouSuggestion>>(emptyList())
        private set

    /** 0f..1f while the library index is being built, null otherwise. */
    var buildProgress by mutableStateOf<Float?>(null)
        private set

    var isLoading by mutableStateOf(false)
        private set

    var failed by mutableStateOf(false)
        private set

    /** How many series fed the taste profile — shown so an empty tab explains itself. */
    var profileSize by mutableStateOf(0)
        private set

    /** How many series the user answered "not interested" to, so it can be undone. */
    var dismissedCount by mutableStateOf(0)
        private set

    /** The profile series that can head a section, by id. */
    private var sources by mutableStateOf<Map<String, snd.komelia.ui.suggestions.ForYouSource>>(emptyMap())

    /**
     * The strongest picks, shown larger and first.
     *
     * A flat grid of forty covers says the last one is as good as the first,
     * though its score is often five times lower.
     */
    val topMatches: List<ForYouSuggestion> get() = suggestions.take(TOP_MATCHES)

    /**
     * The remaining suggestions, grouped under the series they were extrapolated
     * from — "Because you liked X" answers the question a bare cover raises far
     * better than a list of tags does.
     *
     * A group of one is not a section: it would be a heading per card. Those fall
     * back into the general grid at the end.
     */
    val sections: List<ForYouSection>
        get() {
            val rest = suggestions.drop(TOP_MATCHES)
            if (rest.isEmpty()) return emptyList()
            val assigned = HashSet<String>()
            val named = ArrayList<ForYouSection>()
            // A card goes under the series that explains it most, and only
            // that one — appearing under three headings would say nothing.
            fun attributionOf(suggestion: ForYouSuggestion, source: String) =
                suggestion.becauseOf.firstOrNull()?.takeIf { it.seriesId == source }
            sources.entries
                // A series the user explicitly liked heads a section before one
                // they merely finished, even if the latter matches more picks:
                // it is the stronger statement about their taste.
                .sortedWith(
                    compareByDescending<Map.Entry<String, snd.komelia.ui.suggestions.ForYouSource>> {
                        it.value.kind == snd.komelia.ui.suggestions.ForYouSourceKind.LIKED
                    }.thenByDescending { entry ->
                        rest.count { attributionOf(it, entry.key) != null }
                    }
                )
                .forEach { (source, info) ->
                    val items = rest.mapNotNull { suggestion ->
                        if (suggestion.series.id.value in assigned) return@mapNotNull null
                        val attribution = attributionOf(suggestion, source) ?: return@mapNotNull null
                        // The card shows what it shares with THIS series, not
                        // the top term of the whole profile: under a heading
                        // naming one series, the global reason reads as a
                        // mistake even when the pick is right.
                        suggestion.copy(reasons = attribution.reasons)
                    }
                    if (items.size >= MIN_SECTION_SIZE) {
                        items.forEach { assigned += it.series.id.value }
                        named += ForYouSection(ForYouSectionTitle.Source(info.kind, info.name), items)
                    }
                }
            val leftovers = rest.filterNot { it.series.id.value in assigned }
            return when {
                named.isEmpty() -> listOf(ForYouSection(null, leftovers))
                leftovers.isEmpty() -> named
                else -> named + ForYouSection(ForYouSectionTitle.More, leftovers)
            }
        }

    /**
     * When false (the default) the tab only proposes series the user has never
     * finished: it is a discovery surface, unlike the series "Similar" tab where
     * seeing a title you already read is the point.
     */
    var includeRead by mutableStateOf(false)
        private set

    private var started = false

    fun onOpened() {
        if (started) return
        started = true
        screenModelScope.launch { load() }
    }

    fun reload() {
        forceRecompute = true
        screenModelScope.launch { load() }
    }

    /** "Not interested": drops the card now, keeps the series out for good. */
    fun dismiss(seriesId: snd.komga.client.series.KomgaSeriesId) {
        suggestions = suggestions.filterNot { it.series.id == seriesId }
        dismissedCount += 1
        screenModelScope.launch {
            feedbackRepository.dismiss(seriesId)
            // Without this the cached run would bring the card back on the next
            // open, for up to half an hour.
            suggester.invalidateCache()
        }
    }

    /** Puts every dismissed series back in the running, then recomputes. */
    fun resetDismissed() {
        screenModelScope.launch {
            feedbackRepository.clear()
            dismissedCount = 0
            reload()
        }
    }

    fun toggleIncludeRead() {
        includeRead = !includeRead
        reload()
    }

    private var forceRecompute = false

    private suspend fun load() {
        notifications.runCatchingToNotifications {
            isLoading = true
            failed = false
            val currentLibrary = library.filterNotNull().first()
            val computed = suggester.suggest(
                libraryId = currentLibrary.id,
                limit = MAX_SUGGESTIONS,
                includeRead = includeRead,
                // The tab is where the user asks for suggestions, so it always
                // computes; the shelf is what reads the cache.
                force = forceRecompute,
                onIndexProgress = { buildProgress = it },
            ).also { forceRecompute = false }
            dismissedCount = feedbackRepository.dismissed().size
            profileSize = computed.profileSize
            sources = computed.sources
            suggestions = computed.results.map { result ->
                ForYouSuggestion(
                    series = result.series,
                    reasons = result.reasons.map { it.label() },
                    becauseOf = result.becauseOf.map { source ->
                        ForYouAttribution(
                            seriesId = source.seriesId,
                            share = source.share,
                            reasons = source.reasons.map { it.label() },
                        )
                    },
                )
            }
            isLoading = false
        }.onFailure {
            logger.error(it) { "For-you tab failed" }
            isLoading = false
            failed = true
        }
    }
}

/** Same wording everywhere a reason is shown, tab or shelf. */
fun Feature.label(): String = when (family) {
    TermFamily.AUTHOR -> "Author: $value"
    TermFamily.GENRE -> GenreLabels.label(value)
    TermFamily.TAG -> value.removePrefix("kora:tag:").replaceFirstChar { it.uppercaseChar() }
    TermFamily.BOOK_TAG -> value.replaceFirstChar { it.uppercaseChar() }
    TermFamily.PUBLISHER -> "Publisher: ${value.replaceFirstChar { it.uppercaseChar() }}"
}

private const val MAX_SUGGESTIONS = 40
/** Enough to read as "start here", few enough to stay one screen wide. */
private const val TOP_MATCHES = 4
private const val MIN_SECTION_SIZE = 2
