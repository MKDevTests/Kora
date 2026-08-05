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
            profileSize = computed.profileSize
            suggestions = computed.results.map { result ->
                ForYouSuggestion(result.series, result.reasons.map { it.label() })
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
