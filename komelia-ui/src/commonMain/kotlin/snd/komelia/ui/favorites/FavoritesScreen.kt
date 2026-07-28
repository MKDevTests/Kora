package snd.komelia.ui.favorites

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import snd.komelia.AppNotifications
import snd.komelia.komga.api.KomgaBookApi
import snd.komelia.komga.api.KomgaSeriesApi
import snd.komelia.offline.tasks.OfflineTaskEmitter
import snd.komelia.settings.CommonSettingsRepository
import snd.komelia.ui.LoadState
import snd.komelia.ui.LoadState.Error
import snd.komelia.ui.common.lists.LibraryScopeFilterRow
import snd.komelia.ui.common.lists.PersonalListLoader
import snd.komga.client.library.KomgaLibrary
import snd.komelia.ui.LocalRawStatusBarHeight
import snd.komelia.ui.LocalViewModelFactory
import snd.komelia.ui.common.cards.defaultCardWidth
import snd.komelia.ui.common.components.ErrorContent
import snd.komelia.ui.common.itemlist.SeriesLazyCardGrid
import snd.komelia.ui.common.menus.SeriesMenuActions
import snd.komelia.ui.platform.BackPressHandler
import snd.komelia.ui.platform.ScreenPullToRefreshBox
import snd.komelia.ui.series.seriesScreen
import snd.komga.client.series.KomgaSeries
import snd.komga.client.series.KomgaSeriesId

/**
 * The virtual cross-library "Favorites" library: the series the user marked as
 * favorites (a local, per-server set), resolved by id and shown in the standard
 * series grid. Sorted by title; reacts live to favorite/unfavorite (long-press a
 * card → "Retirer des favoris").
 */
class FavoritesScreen : Screen {
    override val key: String = "favorites"

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModelFactory = LocalViewModelFactory.current
        val vm = rememberScreenModel { viewModelFactory.getFavoritesViewModel() }
        LaunchedEffect(Unit) { vm.initialize() }

        val statusBarHeight = LocalRawStatusBarHeight.current

        ScreenPullToRefreshBox(screenState = vm.state, onRefresh = vm::reload) {
            val beforeContent: @Composable () -> Unit = {
                Column(Modifier.fillMaxWidth().padding(top = statusBarHeight)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                        Icon(Icons.Rounded.Star, contentDescription = null, modifier = Modifier.padding(start = 4.dp))
                        Column(Modifier.padding(start = 8.dp)) {
                            Text(
                                "Favoris",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            if (vm.series.isNotEmpty()) {
                                Text(
                                    "${vm.series.size} ${if (vm.series.size > 1) "séries" else "série"}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    LibraryScopeFilterRow(
                        libraries = vm.availableLibraries.collectAsState().value,
                        selectedLibraryId = vm.selectedLibraryId.collectAsState().value,
                        excludedLibraryIds = vm.excludedLibraryIds.collectAsState().value,
                        onSelect = vm::selectLibrary,
                        onToggleExcluded = vm::toggleLibraryExcluded,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                    )
                    if (vm.series.isEmpty() && vm.state.collectAsState().value is LoadState.Success) {
                        Text(
                            if (vm.selectedLibraryId.collectAsState().value != null)
                                "Aucun favori dans cette bibliothèque."
                            else "Aucun favori. Appui long sur une série → « Ajouter aux favoris ».",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 20.dp),
                        )
                    }
                }
            }

            when (val state = vm.state.collectAsState().value) {
                is Error -> ErrorContent(
                    message = state.exception.message ?: "Unknown Error",
                    onReload = vm::reload,
                )

                else -> SeriesLazyCardGrid(
                    series = vm.series,
                    downloadedSeriesIds = vm.downloadedSeriesIds,
                    onSeriesClick = { navigator.push(seriesScreen(it)) },
                    seriesMenuActions = vm.seriesMenuActions(),
                    totalPages = 1,
                    currentPage = 1,
                    onPageChange = {},
                    minSize = vm.cardWidth.collectAsState().value,
                    beforeContent = beforeContent,
                )
            }

            BackPressHandler { navigator.pop() }
        }
    }
}

class FavoritesViewModel(
    private val settingsRepository: CommonSettingsRepository,
    private val seriesApi: KomgaSeriesApi,
    private val bookApi: KomgaBookApi,
    private val notifications: AppNotifications,
    private val taskEmitter: OfflineTaskEmitter,
    private val libraries: StateFlow<List<KomgaLibrary>>,
) : StateScreenModel<LoadState<Unit>>(LoadState.Uninitialized) {

    private val favoriteIds: StateFlow<Set<String>> =
        settingsRepository.getFavoriteSeriesIds().stateIn(screenModelScope, SharingStarted.Eagerly, emptySet())

    private val loader = PersonalListLoader(seriesApi, settingsRepository)

    private val seriesLibraryIds: StateFlow<Map<String, String>> =
        settingsRepository.getSeriesLibraryIds().stateIn(screenModelScope, SharingStarted.Eagerly, emptyMap())

    /** Libraries kept out of "All". Shared with the Planned list. */
    val excludedLibraryIds: StateFlow<Set<String>> =
        settingsRepository.getExcludedLibraryIds().stateIn(screenModelScope, SharingStarted.Eagerly, emptySet())

    /** null = "All libraries". Session-scoped: reopening the screen starts on All. */
    val selectedLibraryId = MutableStateFlow<String?>(null)

    /**
     * Libraries the filter row offers: those actually holding a favorite, in the
     * server's library order. Derived from the cache, so a library only appears
     * once at least one of its favorites has been resolved — which the first
     * load does for all of them.
     */
    val availableLibraries: StateFlow<List<KomgaLibrary>> =
        combine(favoriteIds, seriesLibraryIds, libraries) { ids, cache, all ->
            val present = ids.mapNotNull { cache[it] }.toSet()
            all.filter { it.id.value in present }
        }.stateIn(screenModelScope, SharingStarted.Eagerly, emptyList())

    fun selectLibrary(libraryId: String?) {
        selectedLibraryId.value = libraryId
    }

    /** Toggles whether [libraryId] is part of the "All" view (both lists). */
    fun toggleLibraryExcluded(libraryId: String) {
        screenModelScope.launch {
            val current = excludedLibraryIds.value
            settingsRepository.putExcludedLibraryIds(
                if (libraryId in current) current - libraryId else current + libraryId
            )
        }
    }

    val cardWidth: StateFlow<Dp> = settingsRepository.getCardWidth()
        .map { Dp(it.toFloat()) }
        .stateIn(screenModelScope, SharingStarted.Eagerly, defaultCardWidth.dp)

    var series by mutableStateOf<List<KomgaSeries>>(emptyList())
        private set
    var downloadedSeriesIds by mutableStateOf<Set<KomgaSeriesId>>(emptySet())
        private set

    fun initialize() {
        if (state.value !is LoadState.Uninitialized) return
        // Re-resolve whenever the favorites set, the chosen library or the
        // exclusions change (incl. unfavorite here).
        combine(favoriteIds, selectedLibraryId, excludedLibraryIds) { ids, selected, excluded ->
            Triple(ids, selected, excluded)
        }.onEach { (ids, selected, excluded) -> load(ids, selected, excluded) }
            .launchIn(screenModelScope)
    }

    fun reload() {
        screenModelScope.launch {
            load(favoriteIds.value, selectedLibraryId.value, excludedLibraryIds.value)
        }
    }

    private suspend fun load(ids: Set<String>, selected: String?, excluded: Set<String>) {
        notifications.runCatchingToNotifications {
            if (state.value is LoadState.Uninitialized) mutableState.value = LoadState.Loading
            // The loader applies the library scope before fetching wherever the
            // series' library is already known, so switching libraries doesn't
            // re-resolve the whole favorites set.
            val resolved = loader.resolve(ids, selected, excluded, seriesLibraryIds.value)
            series = resolved
            downloadedSeriesIds = bookApi.getDownloadedSeriesIds(resolved.map { it.id })
            mutableState.value = LoadState.Success(Unit)
        }.onFailure { mutableState.value = LoadState.Error(it) }
    }

    fun seriesMenuActions() = SeriesMenuActions(seriesApi, notifications, taskEmitter, screenModelScope)
}
