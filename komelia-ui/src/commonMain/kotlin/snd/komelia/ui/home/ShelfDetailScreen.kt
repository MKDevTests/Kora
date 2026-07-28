package snd.komelia.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import snd.komelia.homefilters.HomeScreenFilter
import snd.komelia.komga.api.KomgaBookApi
import snd.komelia.komga.api.KomgaSeriesApi
import snd.komelia.offline.tasks.OfflineTaskEmitter
import snd.komelia.settings.CommonSettingsRepository
import snd.komelia.ui.LoadState
import snd.komelia.ui.LocalRawStatusBarHeight
import snd.komelia.ui.LocalViewModelFactory
import snd.komelia.ui.book.bookScreen
import snd.komelia.ui.common.cards.defaultCardWidth
import snd.komelia.ui.common.components.ErrorContent
import snd.komelia.ui.common.itemlist.BookLazyCardGrid
import snd.komelia.ui.common.itemlist.SeriesLazyCardGrid
import snd.komelia.ui.common.menus.BookMenuActions
import snd.komelia.ui.common.menus.SeriesMenuActions
import snd.komelia.ui.platform.BackPressHandler
import snd.komelia.ui.platform.ScreenPullToRefreshBox
import snd.komelia.ui.reader.readerScreen
import snd.komelia.ui.series.seriesScreen

/** How many items a shelf shows once opened full-screen, versus the ~20 that fit
 *  in the Home carousel. Deliberately a single fixed page: the shelves are
 *  "top N" views, not browsable catalogs (the library screen is for that). */
private const val SHELF_DETAIL_PAGE_SIZE = 50

/**
 * Full-screen view of one Home shelf. Tapping a shelf title on Home lands here.
 *
 * All 7 shelf types are supported because the screen doesn't reimplement any
 * query — it hands the shelf's own filter, widened to [SHELF_DETAIL_PAGE_SIZE],
 * to the shared [HomeShelfResolver]. That matters for the four shelves that have
 * no server-side equivalent (AlmostFinished, Favorites, and the book shelves),
 * which a pre-filtered library screen could not have shown.
 *
 * No sorting, filtering or multi-selection here by design.
 */
class ShelfDetailScreen(private val filter: HomeScreenFilter) : Screen {
    override val key: String = "shelfDetail_${filter.order}_${filter.label}"

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModelFactory = LocalViewModelFactory.current
        val vm = rememberScreenModel(key) { viewModelFactory.getShelfDetailViewModel(filter) }
        LaunchedEffect(Unit) { vm.initialize() }

        val statusBarHeight = LocalRawStatusBarHeight.current

        ScreenPullToRefreshBox(screenState = vm.state, onRefresh = vm::reload) {
            val header: @Composable () -> Unit = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = statusBarHeight)
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { navigator.pop() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                    Column(Modifier.padding(start = 8.dp)) {
                        Text(
                            filter.label,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        val count = vm.itemCount
                        if (count > 0) {
                            Text(
                                "$count ${if (count > 1) "éléments" else "élément"}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            when (val state = vm.state.collectAsState().value) {
                is LoadState.Error -> ErrorContent(
                    message = state.exception.message ?: "Unknown Error",
                    onReload = vm::reload,
                )

                else -> {
                    val cardWidth = vm.cardWidth.collectAsState().value
                    when (val data = vm.data) {
                        is SeriesFilterData -> SeriesLazyCardGrid(
                            series = data.series,
                            onSeriesClick = { navigator.push(seriesScreen(it)) },
                            seriesMenuActions = vm.seriesMenuActions(),
                            totalPages = 1,
                            currentPage = 1,
                            onPageChange = {},
                            minSize = cardWidth,
                            beforeContent = header,
                        )

                        is BookFilterData -> Column {
                            // BookLazyCardGrid has no beforeContent slot, so the
                            // header sits above it rather than scrolling with it.
                            header()
                            BookLazyCardGrid(
                                books = data.books,
                                onBookClick = { navigator.push(bookScreen(it)) },
                                onBookReadClick = { book, markProgress ->
                                    navigator.parent?.push(
                                        readerScreen(
                                            book = book,
                                            markReadProgress = markProgress,
                                            // Mirrors HomeScreen: read progress changes even when
                                            // you leave on the same book, so the shelf has to
                                            // re-resolve on the way back or it shows stale
                                            // progress until a manual pull-to-refresh.
                                            onExit = { vm.reload() },
                                        )
                                    )
                                },
                                bookMenuActions = vm.bookMenuActions(),
                                totalPages = 1,
                                currentPage = 1,
                                onPageChange = {},
                                minSize = cardWidth,
                            )
                        }

                        null -> header()
                    }
                }
            }

            BackPressHandler { navigator.pop() }
        }
    }
}

class ShelfDetailViewModel(
    private val filter: HomeScreenFilter,
    settingsRepository: CommonSettingsRepository,
    private val seriesApi: KomgaSeriesApi,
    private val bookApi: KomgaBookApi,
    private val notifications: AppNotifications,
    private val taskEmitter: OfflineTaskEmitter,
    favoriteIdsFlow: kotlinx.coroutines.flow.Flow<Set<String>>,
    excludedLibraryIdsFlow: kotlinx.coroutines.flow.Flow<Set<String>> =
        kotlinx.coroutines.flow.flowOf(emptySet()),
) : StateScreenModel<LoadState<Unit>>(LoadState.Uninitialized) {

    private val favoriteIds: StateFlow<Set<String>> =
        favoriteIdsFlow.stateIn(screenModelScope, SharingStarted.Eagerly, emptySet())

    private val excludedLibraryIds: StateFlow<Set<String>> =
        excludedLibraryIdsFlow.stateIn(screenModelScope, SharingStarted.Eagerly, emptySet())

    // Named arguments on purpose: the resolver takes two lambdas now, and a
    // trailing-lambda call would silently bind to the last one.
    private val resolver = HomeShelfResolver(
        seriesApi = seriesApi,
        bookApi = bookApi,
        favoriteIds = { favoriteIds.value },
        excludedLibraryIds = { excludedLibraryIds.value },
    )

    val cardWidth: StateFlow<Dp> = settingsRepository.getCardWidth()
        .map { Dp(it.toFloat()) }
        .stateIn(screenModelScope, SharingStarted.Eagerly, defaultCardWidth.dp)

    var data by mutableStateOf<HomeFilterData?>(null)
        private set

    val itemCount: Int
        get() = when (val d = data) {
            is SeriesFilterData -> d.series.size
            is BookFilterData -> d.books.size
            null -> 0
        }

    fun initialize() {
        if (state.value !is LoadState.Uninitialized) return
        reload()
    }

    fun reload() {
        screenModelScope.launch {
            notifications.runCatchingToNotifications {
                if (data == null) mutableState.value = LoadState.Loading
                data = resolver.resolve(filter.withPageSize(SHELF_DETAIL_PAGE_SIZE))
                mutableState.value = LoadState.Success(Unit)
            }.onFailure { mutableState.value = LoadState.Error(it) }
        }
    }

    fun seriesMenuActions() = SeriesMenuActions(seriesApi, notifications, taskEmitter, screenModelScope)
    fun bookMenuActions() = BookMenuActions(bookApi, notifications, screenModelScope, taskEmitter)
}
