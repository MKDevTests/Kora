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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
                    if (vm.series.isEmpty() && vm.state.collectAsState().value is LoadState.Success) {
                        Text(
                            "Aucun favori. Appui long sur une série → « Ajouter aux favoris ».",
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
    settingsRepository: CommonSettingsRepository,
    private val seriesApi: KomgaSeriesApi,
    private val bookApi: KomgaBookApi,
    private val notifications: AppNotifications,
    private val taskEmitter: OfflineTaskEmitter,
) : StateScreenModel<LoadState<Unit>>(LoadState.Uninitialized) {

    private val favoriteIds: StateFlow<Set<String>> =
        settingsRepository.getFavoriteSeriesIds().stateIn(screenModelScope, SharingStarted.Eagerly, emptySet())

    val cardWidth: StateFlow<Dp> = settingsRepository.getCardWidth()
        .map { Dp(it.toFloat()) }
        .stateIn(screenModelScope, SharingStarted.Eagerly, defaultCardWidth.dp)

    var series by mutableStateOf<List<KomgaSeries>>(emptyList())
        private set
    var downloadedSeriesIds by mutableStateOf<Set<KomgaSeriesId>>(emptySet())
        private set

    fun initialize() {
        if (state.value !is LoadState.Uninitialized) return
        // Re-resolve whenever the favorites set changes (incl. unfavorite here).
        favoriteIds.onEach { load(it) }.launchIn(screenModelScope)
    }

    fun reload() {
        screenModelScope.launch { load(favoriteIds.value) }
    }

    private suspend fun load(ids: Set<String>) {
        notifications.runCatchingToNotifications {
            if (state.value is LoadState.Uninitialized) mutableState.value = LoadState.Loading
            // getOneSeries is not filtered; resolve favorites in parallel, sort by title.
            val resolved = coroutineScope {
                ids.map { id ->
                    async { runCatching { seriesApi.getOneSeries(KomgaSeriesId(id)) }.getOrNull() }
                }.awaitAll().filterNotNull()
            }.sortedBy { it.metadata.title.lowercase() }
            series = resolved
            downloadedSeriesIds = bookApi.getDownloadedSeriesIds(resolved.map { it.id })
            mutableState.value = LoadState.Success(Unit)
        }.onFailure { mutableState.value = LoadState.Error(it) }
    }

    fun seriesMenuActions() = SeriesMenuActions(seriesApi, notifications, taskEmitter, screenModelScope)
}
