package snd.komelia.ui.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import snd.komelia.AppNotifications
import snd.komelia.komga.api.KomgaSeriesApi
import snd.komelia.settings.CommonSettingsRepository
import snd.komelia.ui.LoadState
import snd.komelia.ui.LoadState.Error
import snd.komelia.ui.LoadState.Loading
import snd.komelia.ui.LoadState.Uninitialized
import snd.komelia.ui.LocalViewModelFactory
import snd.komelia.ui.common.cards.defaultCardWidth
import snd.komelia.ui.common.components.ErrorContent
import snd.komelia.ui.common.components.LoadingMaxSizeIndicator
import snd.komelia.ui.common.itemlist.SeriesLazyCardGrid
import snd.komelia.ui.platform.BackPressHandler
import snd.komelia.ui.platform.ScreenPullToRefreshBox
import snd.komelia.ui.series.seriesScreen
import snd.komga.client.common.KomgaPageRequest
import snd.komga.client.common.KomgaSort
import snd.komga.client.library.KomgaLibraryId
import snd.komga.client.search.allOfSeries
import snd.komga.client.series.KomgaSeries
import snd.komga.client.series.KomgaSeriesSearch

/**
 * Dedicated, read-only drill-down: the series of a single genre, listed live by
 * the `kora:genre:*` tag. Isolated from the library Series tab so it never
 * touches that tab's persisted per-library filter.
 */
class GenreSeriesScreen(
    private val libraryId: KomgaLibraryId?,
    private val genreTag: String,
    private val genreLabel: String,
) : Screen {

    override val key: String = "genre_${libraryId?.value}_$genreTag"

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModelFactory = LocalViewModelFactory.current
        val vm = rememberScreenModel(genreTag) {
            viewModelFactory.getGenreSeriesViewModel(libraryId, genreTag)
        }
        LaunchedEffect(genreTag) { vm.initialize() }

        ScreenPullToRefreshBox(screenState = vm.state, onRefresh = vm::reload) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { navigator.pop() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Column(Modifier.padding(start = 4.dp)) {
                        Text(
                            genreLabel,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        if (vm.totalCount > 0) {
                            Text(
                                "${vm.totalCount} ${if (vm.totalCount > 1) "séries" else "série"}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                when (val state = vm.state.collectAsState().value) {
                    is Error -> ErrorContent(
                        message = state.exception.message ?: "Unknown Error",
                        onReload = vm::reload,
                    )

                    Uninitialized, Loading -> if (vm.series.isEmpty()) LoadingMaxSizeIndicator()
                    else -> Unit
                }

                if (vm.series.isNotEmpty()) {
                    SeriesLazyCardGrid(
                        series = vm.series,
                        onSeriesClick = { navigator.push(seriesScreen(it)) },
                        seriesMenuActions = null,
                        totalPages = vm.totalPages,
                        currentPage = vm.currentPage,
                        onPageChange = vm::onPageChange,
                        minSize = vm.cardWidth.collectAsState().value,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            BackPressHandler { navigator.pop() }
        }
    }
}

class GenreSeriesViewModel(
    private val seriesApi: KomgaSeriesApi,
    private val appNotifications: AppNotifications,
    private val libraryId: KomgaLibraryId?,
    private val genreTag: String,
    settingsRepository: CommonSettingsRepository,
) : StateScreenModel<LoadState<Unit>>(Uninitialized) {

    val cardWidth: StateFlow<Dp> = settingsRepository.getCardWidth()
        .map { Dp(it.toFloat()) }
        .stateIn(screenModelScope, SharingStarted.Eagerly, defaultCardWidth.dp)

    var series: List<KomgaSeries> by mutableStateOf(emptyList())
        private set
    var currentPage by mutableStateOf(1)
        private set
    var totalPages by mutableStateOf(1)
        private set
    var totalCount by mutableStateOf(0)
        private set

    suspend fun initialize() {
        if (state.value !is Uninitialized) return
        loadPage(1)
    }

    fun onPageChange(page: Int) {
        screenModelScope.launch { loadPage(page) }
    }

    fun reload() {
        screenModelScope.launch { loadPage(currentPage) }
    }

    private suspend fun loadPage(page: Int) {
        appNotifications.runCatchingToNotifications {
            mutableState.value = Loading
            val result = seriesApi.getSeriesList(
                KomgaSeriesSearch(
                    condition = allOfSeries {
                        libraryId?.let { library { isEqualTo(it) } }
                        tag { isEqualTo(genreTag) }
                    }.toSeriesCondition()
                ),
                KomgaPageRequest(
                    pageIndex = page - 1,
                    size = 50,
                    sort = KomgaSort.KomgaSeriesSort.byTitleAsc(),
                )
            )
            currentPage = result.number + 1
            totalPages = result.totalPages
            totalCount = result.totalElements
            series = result.content
            mutableState.value = LoadState.Success(Unit)
        }.onFailure { mutableState.value = LoadState.Error(it) }
    }
}
