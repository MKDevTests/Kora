package snd.komelia.ui.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import snd.komelia.ui.LoadState.Error
import snd.komelia.ui.LocalRawStatusBarHeight
import snd.komelia.ui.LocalViewModelFactory
import snd.komelia.ui.common.components.ErrorContent
import snd.komelia.ui.platform.BackPressHandler
import snd.komelia.ui.platform.ScreenPullToRefreshBox
import snd.komelia.ui.series.activeFilters
import snd.komelia.ui.series.list.SeriesListContent
import snd.komelia.ui.series.seriesScreen
import snd.komga.client.library.KomgaLibraryId
import snd.komelia.ui.LocalStrings
import snd.komelia.ui.pushUnique

/**
 * A genre's series, with the full library list UI (letters, read/unread and all
 * filters, sort, random, multi-select). Reuses [LibrarySeriesTabState] locked to
 * the genre tag — the genre constraint is always applied and never persisted to
 * the library's own filter.
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
            viewModelFactory.getGenreSeriesTabState(libraryId, genreTag)
        }
        LaunchedEffect(genreTag) { vm.initialize() }
        DisposableEffect(Unit) {
            vm.startKomgaEventHandler()
            onDispose { vm.stopKomgaEventHandler() }
        }

        val statusBarHeight = LocalRawStatusBarHeight.current

        ScreenPullToRefreshBox(screenState = vm.state, onRefresh = vm::reload) {
            val currentFilter = vm.filterState.state.collectAsState().value
            val currentLetter = currentFilter.letterFilter
            val activeFilters = currentFilter.activeFilters()
            val beforeContent: @Composable () -> Unit = {
                Column(Modifier.fillMaxWidth().padding(top = statusBarHeight)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = LocalStrings.current.ui.back)
                        }
                        Column(Modifier.padding(start = 4.dp)) {
                            Text(
                                genreLabel,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            if (vm.totalSeriesCount > 0) {
                                Text(
                                    "${vm.totalSeriesCount} ${if (vm.totalSeriesCount > 1) "séries" else "série"}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    // The same two rows the library's series tab has. Without
                    // them this screen showed no sign that a filter existed at
                    // all: the only affordance was a floating icon, and a query
                    // that takes ten seconds looked like a tap that did nothing.
                    ActiveFilterChipsRow(
                        filters = activeFilters,
                        onRemove = vm.filterState::remove,
                        onClearAll = vm.filterState::reset,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    )
                    LetterFilterBar(
                        selected = currentLetter,
                        onLetterClick = vm.filterState::onLetterFilterChange,
                    )
                    if (vm.isRefreshing) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp))
                    } else {
                        Spacer(Modifier.height(2.dp))
                    }
                }
            }

            when (val state = vm.state.collectAsState().value) {
                is Error -> ErrorContent(
                    message = state.exception.message ?: "Unknown Error",
                    onReload = vm::reload,
                )

                else -> SeriesListContent(
                    series = vm.series,
                    downloadedSeriesIds = vm.downloadedSeriesIds,
                    seriesActions = vm.seriesMenuActions(),
                    seriesTotalCount = vm.totalSeriesCount,
                    onSeriesClick = {
                        vm.registerSeriesListContext(it)
                        navigator.pushUnique(seriesScreen(it))
                    },
                    editMode = vm.isInEditMode.collectAsState().value,
                    onEditModeChange = vm::onEditModeChange,
                    selectedSeries = vm.selectedSeries,
                    onSeriesSelect = vm::onSeriesSelect,
                    filterState = vm.filterState,
                    // Never passed here, so the panel silently lost its
                    // chapter-series checkbox on this screen alone.
                    chapterSeriesFilter = vm.chapterSeriesFilter,
                    onChapterSeriesFilterCycle = vm::onChapterSeriesFilterCycle,
                    currentPage = vm.currentSeriesPage,
                    totalPages = vm.totalSeriesPages,
                    pageSize = vm.pageLoadSize.collectAsState().value,
                    onPageSizeChange = vm::onPageSizeChange,
                    onPageChange = vm::onPageChange,
                    minSize = vm.cardWidth.collectAsState().value,
                    beforeContent = beforeContent,
                )
            }

            BackPressHandler { navigator.pop() }
        }
    }
}
