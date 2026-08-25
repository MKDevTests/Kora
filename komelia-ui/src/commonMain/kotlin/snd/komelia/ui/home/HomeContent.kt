package snd.komelia.ui.home

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Inter_SemiBold
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.NotoSerif_Bold
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.Font
import snd.komelia.homefilters.HomeScreenFilter
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.ui.LocalFloatingToolbarPadding
import snd.komelia.ui.LocalPlatform
import snd.komelia.ui.LocalTransparentNavBarPadding
import snd.komelia.ui.LocalUseNewLibraryUI
import snd.komelia.ui.LocalUseNewLibraryUI2
import snd.komelia.ui.common.traceLayout
import snd.komelia.ui.common.cards.BookImageCard
import snd.komelia.ui.common.components.AppFilterChipDefaults
import snd.komelia.ui.common.cards.SeriesImageCard
import snd.komelia.ui.common.menus.BookMenuActions
import snd.komelia.ui.common.menus.SeriesMenuActions
import snd.komelia.ui.platform.PlatformType
import snd.komga.client.series.KomgaSeries
import snd.komelia.ui.LocalStrings

@Composable
fun HomeContent(
    filters: List<HomeFilterData>,
    activeFilterNumber: Int,
    onFilterChange: (Int) -> Unit,

    cardWidth: Dp,
    onSeriesClick: (KomgaSeries) -> Unit,
    seriesMenuActions: SeriesMenuActions,
    bookMenuActions: BookMenuActions,
    onBookClick: (KomeliaBook) -> Unit,
    onBookReadClick: (KomeliaBook, Boolean) -> Unit,
    selectedSeries: List<KomgaSeries> = emptyList(),
    onSeriesSelect: ((KomgaSeries) -> Unit)? = null,
    onShelfClick: (HomeScreenFilter) -> Unit = {},
    /** True once the shelves have loaded. Gates the accessory statistics card. */
    homeReady: Boolean = true,
) {
    val gridState = rememberLazyGridState()
    val columnState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val useNewLibraryUI = LocalUseNewLibraryUI.current
    val useNewUI2 = LocalUseNewLibraryUI2.current

    if (useNewUI2) {
        DisplayContent(
            filters = filters,
            activeFilterNumber = activeFilterNumber,
            gridState = gridState,
            columnState = columnState,
            cardWidth = cardWidth,
            onSeriesClick = onSeriesClick,
            seriesMenuActions = seriesMenuActions,
            bookMenuActions = bookMenuActions,
            onBookClick = onBookClick,
            onBookReadClick = onBookReadClick,
            selectedSeries = selectedSeries,
            onSeriesSelect = onSeriesSelect,
            onShelfClick = onShelfClick,
            topContent = {
                Column {
                    HomeHeaderSection()
                    snd.komelia.ui.stats.HomeStatsCard(homeReady = homeReady)
                    snd.komelia.ui.nextreleases.NextReleasesHomeCard()
                    Toolbar(
                        filters = filters,
                        currentFilterNumber = activeFilterNumber,
                        onFilterChange = { newFilter ->
                            onFilterChange(newFilter)
                            coroutineScope.launch {
                                if (useNewLibraryUI && newFilter == 0) columnState.animateScrollToItem(0)
                                else gridState.animateScrollToItem(0)
                            }
                        },
                    )
                }
            },
        )
    } else {
        Column {
            Toolbar(
                filters = filters,
                currentFilterNumber = activeFilterNumber,
                onFilterChange = { newFilter ->
                    onFilterChange(newFilter)
                    coroutineScope.launch {
                        if (useNewLibraryUI && newFilter == 0) columnState.animateScrollToItem(0)
                        else gridState.animateScrollToItem(0)
                    }
                },
            )
            DisplayContent(
                filters = filters,
                activeFilterNumber = activeFilterNumber,
                gridState = gridState,
                columnState = columnState,
                cardWidth = cardWidth,
                onSeriesClick = onSeriesClick,
                seriesMenuActions = seriesMenuActions,
                bookMenuActions = bookMenuActions,
                onBookClick = onBookClick,
                onBookReadClick = onBookReadClick,
                selectedSeries = selectedSeries,
                onSeriesSelect = onSeriesSelect,
                onShelfClick = onShelfClick,
            )
        }
    }
}

@Composable
private fun HomeHeaderSection() {
    val notoSerif = FontFamily(Font(Res.font.NotoSerif_Bold, FontWeight.Bold))
    val mainScreenVm = snd.komelia.ui.LocalMainScreenViewModel.current
    val libraries = mainScreenVm.libraries.collectAsState().value
    val showDropdown = mainScreenVm.libraryDropdownInTitle.collectAsState().value
    val titleStyle = MaterialTheme.typography.headlineLarge.copy(
        fontFamily = notoSerif,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.5).sp,
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 12.dp),
    ) {
        if (showDropdown) {
            snd.komelia.ui.common.components.LibraryTitleSelector(
                label = LocalStrings.current.ui.home,
                titleStyle = titleStyle,
                libraries = libraries,
                currentLibraryId = null,
                onPickHome = { /* already on Home — no-op */ },
                onPickLibrary = { libId -> mainScreenVm.navigateToLibrary(libId) },
            )
        } else {
            Text(LocalStrings.current.ui.home, style = titleStyle)
        }
    }
}

@Composable
private fun Toolbar(
    filters: List<HomeFilterData>,
    currentFilterNumber: Int,
    onFilterChange: (Int) -> Unit,
) {
    val chipColors = AppFilterChipDefaults.filterChipColors()
    // Read here, not inside the lazy blocks below: those are not compositions.
    val shelfStrings = LocalStrings.current.shelves
    val nonEmptyFilters = remember(filters) {
        filters.filter {
            when (it) {
                is BookFilterData -> it.books.isNotEmpty()
                is SeriesFilterData -> it.series.isNotEmpty()
            }
        }
    }
    Box {
        val lazyRowState = rememberLazyListState()
        val coroutineScope = rememberCoroutineScope()

        LazyRow(
            state = lazyRowState,
            modifier = Modifier.animateContentSize(),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            item {
                Spacer(Modifier.width(5.dp))
            }

            if (filters.size > 1) {
                item {
                    val selected = currentFilterNumber == 0
                    FilterChip(
                        onClick = { onFilterChange(0) },
                        selected = selected,
                        label = { Text(LocalStrings.current.ui.all) },
                        colors = chipColors,
                        shape = AppFilterChipDefaults.shape(),
                        border = AppFilterChipDefaults.filterChipBorder(selected),
                    )
                }
            }
            items(nonEmptyFilters) { data ->
                val display = remember(data.filter) {
                    when (data) {
                        is BookFilterData -> data.books.isNotEmpty()
                        is SeriesFilterData -> data.series.isNotEmpty()
                    }
                }
                if (display) {
                    val selected = currentFilterNumber == data.filter.order || filters.size == 1
                    FilterChip(
                        onClick = { onFilterChange(data.filter.order) },
                        selected = selected,
                        label = { Text(shelfLabel(data.filter.label, shelfStrings)) },
                        colors = chipColors,
                        shape = AppFilterChipDefaults.shape(),
                        border = AppFilterChipDefaults.filterChipBorder(selected),
                    )
                }
            }
        }

        if (LocalPlatform.current != PlatformType.MOBILE) {
            Row {
                if (lazyRowState.canScrollBackward) {
                    IconButton(
                        colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.surface),
                        onClick = { coroutineScope.launch { lazyRowState.animateScrollBy(-200.0f) } },
                    ) {
                        Icon(Icons.Default.ChevronLeft, null)
                    }
                }
                Spacer(Modifier.weight(1f))
                if (lazyRowState.canScrollForward) {
                    IconButton(
                        colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.surface),
                        onClick = { coroutineScope.launch { lazyRowState.animateScrollBy(200.0f) } },
                    ) {
                        Icon(Icons.Default.ChevronRight, null)
                    }
                }
            }
        }
    }
}

@Composable
private fun DisplayContent(
    filters: List<HomeFilterData>,
    activeFilterNumber: Int,
    gridState: LazyGridState,
    columnState: LazyListState,
    cardWidth: Dp,
    onSeriesClick: (KomgaSeries) -> Unit,
    seriesMenuActions: SeriesMenuActions,
    bookMenuActions: BookMenuActions,
    onBookClick: (KomeliaBook) -> Unit,
    onBookReadClick: (KomeliaBook, Boolean) -> Unit,
    selectedSeries: List<KomgaSeries> = emptyList(),
    onSeriesSelect: ((KomgaSeries) -> Unit)? = null,
    onShelfClick: (HomeScreenFilter) -> Unit = {},
    topContent: (@Composable () -> Unit)? = null,
) {
    val useNewLibraryUI = LocalUseNewLibraryUI.current
    val extraBottomPadding = LocalTransparentNavBarPadding.current
    val toolbarPadding = LocalFloatingToolbarPadding.current
    val shelfStrings = LocalStrings.current.shelves
    if (useNewLibraryUI && activeFilterNumber == 0) {
        LazyColumn(
            state = columnState,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(top = toolbarPadding, bottom = 15.dp + extraBottomPadding),
        ) {
            if (topContent != null) {
                item { topContent() }
            }
            for (data in filters) {
                val isEmpty = when (data) {
                    is BookFilterData -> data.books.isEmpty()
                    is SeriesFilterData -> data.series.isEmpty()
                }
                if (!isEmpty) {
                    item {
                        // Traced so a system trace can split the first frame's
                        // one opaque draw slice into per-shelf work. Free, and
                        // absent from the tree, when nothing is capturing.
                        Column(
                            modifier = Modifier.traceLayout("home.shelf"),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            SectionHeader(shelfLabel(data.filter.label, shelfStrings), onClick = { onShelfClick(data.filter) })
                            SectionRow(
                                modifier = Modifier.traceLayout("home.shelfRow"),
                                data = data,
                                cardWidth = cardWidth,
                                onSeriesClick = onSeriesClick,
                                seriesMenuActions = seriesMenuActions,
                                bookMenuActions = bookMenuActions,
                                onBookClick = onBookClick,
                                onBookReadClick = onBookReadClick,
                                selectedSeries = selectedSeries,
                                onSeriesSelect = onSeriesSelect,
                            )
                        }
                    }
                }
            }
        }
    } else {
        LazyVerticalGrid(
            modifier = Modifier.padding(horizontal = 20.dp),
            state = gridState,
            columns = GridCells.Adaptive(cardWidth),
            horizontalArrangement = Arrangement.spacedBy(15.dp),
            verticalArrangement = Arrangement.spacedBy(15.dp),
            contentPadding = PaddingValues(top = toolbarPadding, bottom = 15.dp + extraBottomPadding)
        ) {
            if (topContent != null) {
                item(span = { GridItemSpan(maxLineSpan) }) { topContent() }
            }
            for (data in filters) {
                if (activeFilterNumber == 0 || data.filter.order == activeFilterNumber) {
                    when (data) {
                        is BookFilterData -> BookFilterEntry(
                            label = shelfLabel(data.filter.label, shelfStrings),
                            onLabelClick = { onShelfClick(data.filter) },
                            books = data.books,
                            bookMenuActions = bookMenuActions,
                            onBookClick = onBookClick,
                            onBookReadClick = onBookReadClick,
                        )

                        is SeriesFilterData -> SeriesFilterEntries(
                            label = shelfLabel(data.filter.label, shelfStrings),
                            onLabelClick = { onShelfClick(data.filter) },
                            series = data.series,
                            onSeriesClick = onSeriesClick,
                            seriesMenuActions = seriesMenuActions,
                            selectedSeries = selectedSeries,
                            onSeriesSelect = onSeriesSelect,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Shelf title. Tapping it opens the shelf full-screen ([ShelfDetailScreen]);
 * the chevron is what tells the user the row is more than a label.
 */
@Composable
private fun SectionHeader(label: String, onClick: () -> Unit) {
    val inter = FontFamily(Font(Res.font.Inter_SemiBold, FontWeight.SemiBold))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleLarge.copy(
                fontFamily = inter,
                fontWeight = FontWeight.SemiBold
            ),
        )
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            modifier = Modifier.padding(start = 2.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** [SectionHeader]'s twin for the paginated grid layout (no horizontal inset). */
@Composable
private fun GridSectionHeader(label: String, onClick: () -> Unit) {
    val inter = FontFamily(Font(Res.font.Inter_SemiBold, FontWeight.SemiBold))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable(onClick = onClick).padding(vertical = 4.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleLarge.copy(
                fontFamily = inter,
                fontWeight = FontWeight.SemiBold
            ),
        )
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            modifier = Modifier.padding(start = 2.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionRow(
    modifier: Modifier = Modifier,
    data: HomeFilterData,
    cardWidth: Dp,
    onSeriesClick: (KomgaSeries) -> Unit,
    seriesMenuActions: SeriesMenuActions,
    bookMenuActions: BookMenuActions,
    onBookClick: (KomeliaBook) -> Unit,
    onBookReadClick: (KomeliaBook, Boolean) -> Unit,
    selectedSeries: List<KomgaSeries> = emptyList(),
    onSeriesSelect: ((KomgaSeries) -> Unit)? = null,
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        when (data) {
            is BookFilterData -> items(data.books) { book ->
                BookImageCard(
                    book = book,
                    onBookClick = { onBookClick(book) },
                    onBookReadClick = { onBookReadClick(book, it) },
                    bookMenuActions = bookMenuActions,
                    showSeriesTitle = true,
                    modifier = Modifier.width(cardWidth).traceLayout("home.card"),
                )
            }

            is SeriesFilterData -> items(data.series) { series ->
                val selectionMode = selectedSeries.isNotEmpty()
                SeriesImageCard(
                    series = series,
                    onSeriesClick = {
                        if (selectionMode && onSeriesSelect != null) onSeriesSelect(series)
                        else onSeriesClick(series)
                    },
                    isSelected = selectedSeries.any { it.id == series.id },
                    onSeriesSelect = onSeriesSelect?.let { { it(series) } },
                    seriesMenuActions = seriesMenuActions,
                    modifier = Modifier.width(cardWidth).traceLayout("home.card"),
                )
            }
        }
    }
}

private fun LazyGridScope.BookFilterEntry(
    label: String,
    onLabelClick: () -> Unit,
    books: List<KomeliaBook>,
    bookMenuActions: BookMenuActions,
    onBookClick: (KomeliaBook) -> Unit,
    onBookReadClick: (KomeliaBook, Boolean) -> Unit,
) {
    if (books.isEmpty()) return

    item(span = { GridItemSpan(maxLineSpan) }) {
        GridSectionHeader(label, onLabelClick)
    }
    items(books) { book ->
        BookImageCard(
            book = book,
            onBookClick = { onBookClick(book) },
            onBookReadClick = { onBookReadClick(book, it) },
            bookMenuActions = bookMenuActions,
            showSeriesTitle = true,
            modifier = Modifier.fillMaxSize()
        )
    }
}

private fun LazyGridScope.SeriesFilterEntries(
    label: String,
    onLabelClick: () -> Unit,
    series: List<KomgaSeries>,
    onSeriesClick: (KomgaSeries) -> Unit,
    seriesMenuActions: SeriesMenuActions,
    selectedSeries: List<KomgaSeries> = emptyList(),
    onSeriesSelect: ((KomgaSeries) -> Unit)? = null,
) {
    if (series.isEmpty()) return
    item(span = { GridItemSpan(maxLineSpan) }) {
        GridSectionHeader(label, onLabelClick)
    }

    items(series) { s ->
        val selectionMode = selectedSeries.isNotEmpty()
        SeriesImageCard(
            series = s,
            onSeriesClick = {
                if (selectionMode && onSeriesSelect != null) onSeriesSelect(s)
                else onSeriesClick(s)
            },
            isSelected = selectedSeries.any { it.id == s.id },
            onSeriesSelect = onSeriesSelect?.let { { it(s) } },
            seriesMenuActions = seriesMenuActions,
            modifier = Modifier.fillMaxSize()
        )
    }
}
