package snd.komelia.ui.common.lists

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import snd.komelia.komga.api.KomgaSeriesApi
import snd.komelia.settings.CommonSettingsRepository
import snd.komga.client.series.KomgaSeries
import snd.komga.client.series.KomgaSeriesId

/**
 * Resolves a personal list (Favorites / Planned) into series, applying the
 * library scope BEFORE hitting the network wherever possible.
 *
 * Those lists store bare series ids, so a naive per-library filter would have to
 * resolve every entry first — one `getOneSeries` round-trip each — just to throw
 * most of them away. Instead a persisted `seriesId -> libraryId` cache
 * ([CommonSettingsRepository.getSeriesLibraryIds]) lets the filter run locally:
 *
 *  - ids whose library is known and excluded by the scope are dropped for free;
 *  - ids whose library is unknown must be resolved to find out, then filtered
 *    on the result — and their library is recorded so it is free next time.
 *
 * So the first load after an update still resolves everything once (exactly what
 * the screens did before), and every later load only fetches what it displays.
 */
class PersonalListLoader(
    private val seriesApi: KomgaSeriesApi,
    private val settingsRepository: CommonSettingsRepository,
) {

    /**
     * @param ids the whole list (favorites or planned)
     * @param selectedLibraryId a single library to show, or null for "All"
     * @param excludedLibraryIds libraries kept out of "All" (ignored when a
     *        specific library is selected — asking for it means wanting it)
     * @param cache the persisted seriesId -> libraryId mapping
     */
    suspend fun resolve(
        ids: Set<String>,
        selectedLibraryId: String?,
        excludedLibraryIds: Set<String>,
        cache: Map<String, String>,
    ): List<KomgaSeries> {
        val (known, unknown) = ids.partition { cache.containsKey(it) }

        val wantedKnown = known.filter { inScope(cache.getValue(it), selectedLibraryId, excludedLibraryIds) }
        // Unknown ids can't be filtered yet — resolve them, then filter below.
        val toFetch = wantedKnown + unknown

        val limit = Semaphore(MAX_CONCURRENT)
        val fetched = coroutineScope {
            toFetch.map { id ->
                async {
                    limit.withPermit {
                        runCatching { seriesApi.getOneSeries(KomgaSeriesId(id)) }.getOrNull()
                    }
                }
            }.awaitAll().filterNotNull()
        }

        // Record what we just learned so the next load can filter these locally.
        val learned = fetched
            .filter { it.id.value in unknown }
            .associate { it.id.value to it.libraryId.value }
        if (learned.isNotEmpty()) settingsRepository.putSeriesLibraryIds(learned)

        return fetched
            .filter { inScope(it.libraryId.value, selectedLibraryId, excludedLibraryIds) }
            .sortedBy { it.metadata.title.lowercase() }
    }

    private fun inScope(libraryId: String, selected: String?, excluded: Set<String>): Boolean =
        if (selected != null) libraryId == selected else libraryId !in excluded

    private companion object {
        /** Same bound as every other Komga fan-out: more saturates its pool. */
        const val MAX_CONCURRENT = 4
    }
}
