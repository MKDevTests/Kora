package snd.komelia.ui.library

import snd.komelia.perf.PerfTrace
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.createDirectories
import io.github.vinceglb.filekit.div
import io.github.vinceglb.filekit.filesDir
import io.github.vinceglb.filekit.path
import io.github.vinceglb.filekit.readBytes
import io.github.vinceglb.filekit.write
import snd.komelia.AppNotifications
import snd.komelia.hidden.HIDDEN_TAG
import snd.komelia.komga.api.KomgaReferentialApi
import snd.komelia.komga.api.KomgaSeriesApi
import snd.komelia.settings.CommonSettingsRepository
import snd.komelia.ui.LoadState
import snd.komelia.ui.LoadState.Loading
import snd.komelia.ui.LoadState.Success
import snd.komelia.ui.LoadState.Uninitialized
import snd.komelia.ui.common.cards.defaultCardWidth
import snd.komga.client.common.KomgaPageRequest
import snd.komga.client.common.KomgaSort
import snd.komga.client.library.KomgaLibrary
import snd.komga.client.search.allOfSeries
import snd.komga.client.series.KomgaSeries
import snd.komga.client.series.KomgaSeriesId
import snd.komga.client.series.KomgaSeriesSearch

/**
 * Process-wide cache of the genre catalog per library, so re-opening the Genre
 * tab (or returning to it after a drill-down) shows tiles instantly instead of
 * re-running the discovery + per-genre count fetches. Refreshed silently in the
 * background on every initialize, and forced by pull-to-refresh. Cleared on
 * process restart.
 */
/**
 * Max concurrent per-genre count queries in the Genre tab's phase 2. Komga's DB
 * connection pool is small; firing one query per genre at once saturated it and
 * stalled every other request (grid loads spiked to ~20s). Streaming them a few
 * at a time keeps the server responsive.
 */
private const val MAX_CONCURRENT_COUNTS = 4

private object GenreCatalogCache {
    private val byLibrary = mutableMapOf<String, List<GenreTile>>()
    fun get(libraryKey: String): List<GenreTile>? = byLibrary[libraryKey]
    fun put(libraryKey: String, tiles: List<GenreTile>) {
        byLibrary[libraryKey] = tiles
    }
}

/**
 * Backs the experimental Genre tab. Discovers a library's genres from its
 * `kora:genre:*` series tags (one referential call) and, for each, fetches the
 * count + a representative cover. The catalog is held in memory; a genre's
 * series are listed live by [GenreSeriesScreen]. Refreshed on pull-to-refresh.
 */
class LibraryGenreTabState(
    private val seriesApi: KomgaSeriesApi,
    private val referentialApi: KomgaReferentialApi,
    private val appNotifications: AppNotifications,
    private val settingsRepository: CommonSettingsRepository,
    private val library: StateFlow<KomgaLibrary?>,
    val cardWidth: StateFlow<Dp>,
) : StateScreenModel<LoadState<Unit>>(Uninitialized) {

    var genres: List<GenreTile> by mutableStateOf(emptyList())
        private set
    var overriddenSlugs: Set<String> by mutableStateOf(emptySet())
        private set

    /**
     * Tile appearance: when the user enables custom genre-tile appearance, the
     * tiles use their own width + text settings; otherwise they inherit the
     * global card width and layout (textBelow == null = inherit).
     */
    val tileAppearance: StateFlow<GenreTileAppearance> = combine(
        settingsRepository.getGenreTilesCustomAppearance(),
        settingsRepository.getGenreTileWidth(),
        settingsRepository.getGenreTileTextBelow(),
        settingsRepository.getGenreTileShowCount(),
        settingsRepository.getCardWidth(),
    ) { custom, width, textBelow, showCount, globalWidth ->
        if (custom) GenreTileAppearance(width.dp, textBelow, showCount)
        else GenreTileAppearance(globalWidth.dp, null, true)
    }.stateIn(
        screenModelScope,
        SharingStarted.Eagerly,
        GenreTileAppearance(defaultCardWidth.dp, null, true),
    )

    fun initialize() {
        if (state.value !is Uninitialized) return
        // Show the in-memory cached catalog instantly (if any), then refresh in
        // the background. loadGenres only flips to Loading when genres is empty,
        // so a cache hit refreshes silently without a spinner.
        library.value?.id?.value?.let { key ->
            GenreCatalogCache.get(key)?.let { cached ->
                genres = cached
                mutableState.value = Success(Unit)
            }
        }
        screenModelScope.launch {
            // Nothing in memory (e.g. the first open after an app restart)? Fall
            // back to the persisted snapshot so tiles + covers show immediately
            // (Coil serves the images from its disk cache) while we re-verify
            // with the server below. Unchanged tiles don't flicker.
            if (genres.isEmpty()) {
                library.value?.id?.value?.let { key ->
                    loadPersistedCatalog(key)?.takeIf { it.isNotEmpty() }?.let { persisted ->
                        if (genres.isEmpty()) {
                            genres = persisted
                            GenreCatalogCache.put(key, persisted)
                            mutableState.value = Success(Unit)
                        }
                    }
                }
            }
            loadGenres()
        }
    }

    fun reload() {
        screenModelScope.launch { loadGenres() }
    }

    private suspend fun loadGenres() {
        val lib = library.value ?: return
        appNotifications.runCatchingToNotifications {
            if (genres.isEmpty()) mutableState.value = Loading

            val coverOverrides = settingsRepository.getGenreCoverOverrides().first()
            val labelOverrides = settingsRepository.getGenreLabelOverrides().first()

            val genreTags = PerfTrace.measure("genre.getSeriesTags", { it.size }) {
                referentialApi.getSeriesTags(libraryId = lib.id)
            }.filter { GenreLabels.isGenreTag(it) }

            // Phase 1: paint tiles from the tag list after a single call (label
            // + any override cover; count unknown), so the grid shows almost
            // immediately instead of waiting on a count + cover fetch per genre.
            // Only on a cold open (nothing already on screen from the cache).
            if (genres.isEmpty()) {
                genres = genreTags.map { genreTag ->
                    val slug = GenreLabels.slugOf(genreTag)
                    val key = overrideKey(slug)
                    val ov = coverOverrides[key]
                    val localPath = ov?.takeIf { it.startsWith(FILE_PREFIX) }?.removePrefix(FILE_PREFIX)
                    GenreTile(
                        tag = genreTag,
                        slug = slug,
                        label = labelOverrides[key] ?: GenreLabels.label(slug),
                        count = -1,
                        coverSeriesId = if (localPath == null && ov != null) KomgaSeriesId(ov) else null,
                        coverLocalPath = localPath,
                    )
                }.sortedBy { it.label.lowercase() }
                mutableState.value = Success(Unit)
            }

            // Phase 2: fetch the count + representative cover per genre and patch
            // each tile in place as its call returns, so covers/counts stream in
            // (the server is ~2s per query) instead of all appearing at the end.
            // All updates run on the screenModel's Main dispatcher, so the shared
            // map and `genres` writes don't race.
            val resolvedByTag = mutableMapOf<String, GenreTile>()
            // Bound the fan-out: this used to launch one query PER genre at once
            // (~22 concurrent), which saturated Komga's DB connection pool and
            // made any grid load happening at the same time queue for many
            // seconds behind it (measured: a 50-item page spiking to ~20s during
            // a genre open, vs ~1s idle). A small permit count keeps counts
            // streaming in without stampeding the server.
            val limit = Semaphore(MAX_CONCURRENT_COUNTS)
            coroutineScope {
                genreTags.forEach { genreTag ->
                    launch {
                        val slug = GenreLabels.slugOf(genreTag)
                        val key = overrideKey(slug)
                        val page = limit.withPermit {
                            seriesApi.getSeriesList(
                                KomgaSeriesSearch(
                                    condition = allOfSeries {
                                        library { isEqualTo(lib.id) }
                                        tag { isEqualTo(genreTag) }
                                        tag { isNotEqualTo(HIDDEN_TAG) }
                                    }.toSeriesCondition()
                                ),
                                KomgaPageRequest(
                                    pageIndex = 0,
                                    size = 1,
                                    sort = KomgaSort.KomgaSeriesSort.byTitleAsc(),
                                )
                            )
                        }
                        val ov = coverOverrides[key]
                        val localPath = ov?.takeIf { it.startsWith(FILE_PREFIX) }?.removePrefix(FILE_PREFIX)
                        val resolved = GenreTile(
                            tag = genreTag,
                            slug = slug,
                            label = labelOverrides[key] ?: GenreLabels.label(slug),
                            count = page.totalElements,
                            coverSeriesId = when {
                                localPath != null -> null
                                ov != null -> KomgaSeriesId(ov)
                                else -> page.content.firstOrNull()?.id
                            },
                            coverLocalPath = localPath,
                        )
                        resolvedByTag[genreTag] = resolved
                        genres = genres.map { if (it.tag == genreTag) resolved else it }
                    }
                }
            }

            // All counts in: rebuild from the fresh results (handles added /
            // removed genres), drop empties, settle the order and cache.
            genres = genreTags.mapNotNull { resolvedByTag[it] }
                .filter { it.count > 0 }
                .sortedBy { it.label.lowercase() }
            overriddenSlugs = genres.map { it.slug }
                .filter { coverOverrides.containsKey(overrideKey(it)) || labelOverrides.containsKey(overrideKey(it)) }
                .toSet()
            GenreCatalogCache.put(lib.id.value, genres)
            persistCatalog(lib.id.value, genres)
            mutableState.value = Success(Unit)
        }.onFailure { mutableState.value = LoadState.Error(it) }
    }

    /** Series in a genre, shown by default in the cover picker. */
    suspend fun seriesForGenre(genreTag: String): List<KomgaSeries> {
        val lib = library.value ?: return emptyList()
        return runCatching {
            seriesApi.getSeriesList(
                KomgaSeriesSearch(
                    condition = allOfSeries {
                        library { isEqualTo(lib.id) }
                        tag { isEqualTo(genreTag) }
                    }.toSeriesCondition()
                ),
                KomgaPageRequest(pageIndex = 0, size = 60, sort = KomgaSort.KomgaSeriesSort.byTitleAsc())
            ).content
        }.getOrDefault(emptyList())
    }

    /** Full-text search of series in this library, for the cover picker. */
    suspend fun searchSeriesInLibrary(query: String): List<KomgaSeries> {
        val lib = library.value ?: return emptyList()
        return runCatching {
            seriesApi.getSeriesList(
                KomgaSeriesSearch(
                    condition = allOfSeries { library { isEqualTo(lib.id) } }.toSeriesCondition(),
                    fullTextSearch = query,
                ),
                KomgaPageRequest(pageIndex = 0, size = 60, sort = KomgaSort.Unsorted)
            ).content
        }.getOrDefault(emptyList())
    }

    fun setCover(slug: String, seriesId: KomgaSeriesId) {
        screenModelScope.launch {
            val map = settingsRepository.getGenreCoverOverrides().first().toMutableMap()
            map[overrideKey(slug)] = seriesId.value
            settingsRepository.putGenreCoverOverrides(map)
            genres = genres.map { if (it.slug == slug) it.copy(coverSeriesId = seriesId, coverLocalPath = null) else it }
            refreshOverriddenSlugs()
            cacheCurrent()
        }
    }

    /** Set a genre cover from a picked local image, copied into app storage. */
    fun setLocalCover(slug: String, bytes: ByteArray) {
        screenModelScope.launch {
            val path = saveCoverBytes(overrideKey(slug), bytes) ?: return@launch
            val map = settingsRepository.getGenreCoverOverrides().first().toMutableMap()
            map[overrideKey(slug)] = "$FILE_PREFIX$path"
            settingsRepository.putGenreCoverOverrides(map)
            genres = genres.map { if (it.slug == slug) it.copy(coverSeriesId = null, coverLocalPath = path) else it }
            refreshOverriddenSlugs()
            cacheCurrent()
        }
    }

    /**
     * Bulk-applies genre covers from picked image files, matching each file NAME
     * to a genre (see [GenreLabels.slugForFileName]). Exists because re-doing 50+
     * covers one dialog at a time is unusable — and because losing the app's data
     * means losing the images, which live in private storage and are not in the
     * JSON backup. Keep the source files somewhere safe; this re-applies them.
     *
     * Only genres present in the CURRENT library are touched. Returns a report so
     * the UI can tell the user exactly what matched and what didn't.
     */
    fun importCovers(files: List<Pair<String, ByteArray>>, onDone: (CoverImportReport) -> Unit) {
        screenModelScope.launch {
            val knownSlugs = genres.map { it.slug }.toSet()
            val libraryName = library.value?.name
            val map = settingsRepository.getGenreCoverOverrides().first().toMutableMap()
            val applied = mutableListOf<String>()
            val unmatched = mutableListOf<String>()
            val notInLibrary = mutableListOf<String>()

            for ((fileName, bytes) in files) {
                val slug = GenreLabels.slugForFileName(fileName)
                // A file named for ANOTHER library is skipped, not applied: the
                // same genre exists in several libraries (Bd_Action /
                // Comics_Action / Manga_Action all mean `action`) and without
                // this the last file processed would win. A file with no library
                // prefix ("Espionnage") is accepted for whichever library is open.
                val token = GenreLabels.libraryTokenOfFileName(fileName)
                val wrongLibrary = token != null && libraryName != null &&
                    !GenreLabels.libraryTokenMatches(token, libraryName)
                when {
                    slug == null -> unmatched += fileName
                    wrongLibrary || slug !in knownSlugs -> notInLibrary += fileName
                    else -> {
                        val key = overrideKey(slug)
                        val path = saveCoverBytes(key, bytes)
                        if (path == null) unmatched += fileName
                        else {
                            map[key] = "$FILE_PREFIX$path"
                            applied += GenreLabels.label(slug)
                        }
                    }
                }
            }

            settingsRepository.putGenreCoverOverrides(map)
            loadGenres()
            onDone(CoverImportReport(applied.sorted(), unmatched.sorted(), notInLibrary.sorted()))
        }
    }

    private suspend fun saveCoverBytes(key: String, bytes: ByteArray): String? = runCatching {
        val safe = key.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val dir = FileKit.filesDir / "genre_covers"
        dir.createDirectories()
        // Hash in the filename so a new image gets a new path (fresh Coil cache key).
        val dest = dir / "${safe}_${bytes.contentHashCode()}.img"
        dest.write(bytes)
        dest.path
    }.getOrNull()

    fun setLabel(slug: String, label: String) {
        screenModelScope.launch {
            val key = overrideKey(slug)
            val map = settingsRepository.getGenreLabelOverrides().first().toMutableMap()
            val trimmed = label.trim()
            if (trimmed.isEmpty()) map.remove(key) else map[key] = trimmed
            settingsRepository.putGenreLabelOverrides(map)
            val newLabel = if (trimmed.isEmpty()) GenreLabels.label(slug) else trimmed
            genres = genres.map { if (it.slug == slug) it.copy(label = newLabel) else it }
                .sortedBy { it.label.lowercase() }
            refreshOverriddenSlugs()
            cacheCurrent()
        }
    }

    fun resetOverride(slug: String) {
        screenModelScope.launch {
            val key = overrideKey(slug)
            val coverMap = settingsRepository.getGenreCoverOverrides().first().toMutableMap()
            val labelMap = settingsRepository.getGenreLabelOverrides().first().toMutableMap()
            coverMap.remove(key)
            labelMap.remove(key)
            settingsRepository.putGenreCoverOverrides(coverMap)
            settingsRepository.putGenreLabelOverrides(labelMap)
            loadGenres()
        }
    }

    private suspend fun refreshOverriddenSlugs() {
        val coverMap = settingsRepository.getGenreCoverOverrides().first()
        val labelMap = settingsRepository.getGenreLabelOverrides().first()
        overriddenSlugs = genres.map { it.slug }
            .filter { coverMap.containsKey(overrideKey(it)) || labelMap.containsKey(overrideKey(it)) }
            .toSet()
    }

    /**
     * Read the persisted genre catalog for a library (written by [persistCatalog]).
     * Lets a cold open show tiles + covers instantly before the server re-verify.
     */
    private suspend fun loadPersistedCatalog(libraryKey: String): List<GenreTile>? = runCatching {
        val json = catalogFile(libraryKey).readBytes().decodeToString()
        Json.decodeFromString(ListSerializer(GenreTileSnapshot.serializer()), json).map {
            GenreTile(
                tag = it.tag,
                slug = it.slug,
                label = it.label,
                count = it.count,
                coverSeriesId = it.coverSeriesId?.let { id -> KomgaSeriesId(id) },
                coverLocalPath = it.coverLocalPath,
            )
        }
    }.getOrNull()

    /** Persist the resolved catalog so it survives a process restart. */
    private suspend fun persistCatalog(libraryKey: String, tiles: List<GenreTile>) {
        runCatching {
            (FileKit.filesDir / "genre_catalog").createDirectories()
            val snapshots = tiles.map {
                GenreTileSnapshot(it.tag, it.slug, it.label, it.count, it.coverSeriesId?.value, it.coverLocalPath)
            }
            catalogFile(libraryKey).write(
                Json.encodeToString(ListSerializer(GenreTileSnapshot.serializer()), snapshots).encodeToByteArray()
            )
        }
    }

    private fun catalogFile(libraryKey: String) =
        FileKit.filesDir / "genre_catalog" / "${libraryKey.replace(Regex("[^A-Za-z0-9_-]"), "_")}.json"

    private fun cacheCurrent() {
        library.value?.id?.value?.let { GenreCatalogCache.put(it, genres) }
    }

    private fun overrideKey(slug: String): String =
        "${library.value?.id?.value ?: "all"}:$slug"
}

private const val FILE_PREFIX = "file:"

/** Outcome of a bulk cover import, so the UI can report it precisely. */
data class CoverImportReport(
    /** Display labels of the genres whose cover was set. */
    val applied: List<String>,
    /** File names whose genre could not be recognised. */
    val unmatched: List<String>,
    /** Recognised genres that this library doesn't use — skipped, not an error. */
    val notInLibrary: List<String>,
) {
    val total: Int get() = applied.size + unmatched.size + notInLibrary.size
}

/** Resolved appearance for the genre tiles. [textBelow] null = inherit global. */
data class GenreTileAppearance(
    val minSize: Dp,
    val textBelow: Boolean?,
    val showCount: Boolean,
)

@Serializable
private data class GenreTileSnapshot(
    val tag: String,
    val slug: String,
    val label: String,
    val count: Int,
    val coverSeriesId: String? = null,
    val coverLocalPath: String? = null,
)

data class GenreTile(
    val tag: String,
    val slug: String,
    val label: String,
    val count: Int,
    val coverSeriesId: KomgaSeriesId?,
    val coverLocalPath: String? = null,
)
