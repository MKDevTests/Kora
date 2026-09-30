package snd.komelia.ui.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import snd.komelia.ui.LoadState
import snd.komelia.ui.LocalFloatingToolbarPadding
import snd.komelia.ui.LocalPlatform
import snd.komelia.ui.LocalRawStatusBarHeight
import snd.komelia.ui.LocalTheme
import snd.komelia.ui.LocalUseNewLibraryUI2
import snd.komelia.ui.LocalViewModelFactory
import snd.komelia.ui.ReloadableScreen
import snd.komelia.ui.topbar.NewTopAppBar
import snd.komelia.ui.book.bookScreen
import snd.komelia.ui.common.components.ErrorContent
import snd.komelia.ui.common.components.LoadingMaxSizeIndicator
import snd.komelia.ui.platform.BackPressHandler
import snd.komelia.ui.platform.PlatformType
import snd.komelia.ui.platform.ScreenPullToRefreshBox
import snd.komelia.ui.series.seriesScreen
import snd.komelia.ui.pushUnique

class SearchScreen(
    private val initialQuery: String?,
) : ReloadableScreen {

    @Composable
    override fun Content() {
        val viewModelFactory = LocalViewModelFactory.current
        val vm = rememberScreenModel(initialQuery) {
            viewModelFactory.getSearchViewModel()
        }
        LaunchedEffect(initialQuery) { vm.initialize(initialQuery) }
        // Every return to the tab: a book read in between belongs on top.
        LaunchedEffect(Unit) { vm.refreshRecentBooks() }

        val navigator = LocalNavigator.currentOrThrow

        ScreenPullToRefreshBox(screenState = vm.state, onRefresh = vm::reload) {
            if (LocalPlatform.current == PlatformType.MOBILE) {
                val state by vm.state.collectAsState()
                SearchHome(
                    vm = vm,
                    state = state,
                    canGoBack = navigator.canPop,
                    onBack = { navigator.pop() },
                    onSeriesClick = { navigator.pushUnique(seriesScreen(it)) },
                    onBookClick = { navigator.pushUnique(bookScreen(it)) },
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val state = vm.state.collectAsState().value
                    when {
                        state is LoadState.Error -> ErrorContent(
                            state.exception.message ?: "Error",
                            onReload = vm::reload
                        )

                        state is LoadState.Success || vm.hasAnyResults -> {
                            SearchContent(
                                query = vm.query,
                                searchType = vm.currentTab,
                                onSearchTypeChange = vm::onSearchTypeChange,

                                seriesResults = vm.seriesResults,
                                seriesCurrentPage = vm.seriesCurrentPage,
                                seriesTotalPages = vm.seriesTotalPages,
                                onSeriesPageChange = vm::onSeriesPageChange,
                                onSeriesClick = { navigator.pushUnique(seriesScreen(it)) },

                                bookResults = vm.bookResults,
                                bookCurrentPage = vm.bookCurrentPage,
                                bookTotalPages = vm.bookTotalPages,
                                onBookPageChange = vm::onBookPageChange,
                                onBookClick = { navigator.pushUnique(bookScreen(it)) },

                                authorNames = vm.authorNames,
                                selectedAuthor = vm.selectedAuthor,
                                onAuthorSelected = vm::onAuthorSelected,
                                onAuthorCleared = vm::clearSelectedAuthor,
                                authorSeriesResults = vm.authorSeriesResults,
                                authorSeriesCurrentPage = vm.authorSeriesCurrentPage,
                                authorSeriesTotalPages = vm.authorSeriesTotalPages,
                                onAuthorSeriesPageChange = vm::onAuthorSeriesPageChange,
                                authorBookResults = vm.authorBookResults,
                                authorBookCurrentPage = vm.authorBookCurrentPage,
                                authorBookTotalPages = vm.authorBookTotalPages,
                                onAuthorBookPageChange = vm::onAuthorBookPageChange,
                                stale = vm.resultsAreStale,
                            )
                        }

                        else -> LoadingMaxSizeIndicator()
                    }
                }
            }
            BackPressHandler {
                if (vm.selectedAuthor != null) vm.clearSelectedAuthor()
                else navigator.pop()
            }
        }
    }
}
