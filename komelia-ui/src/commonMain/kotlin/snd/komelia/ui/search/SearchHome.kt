package snd.komelia.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.ui.LoadState
import snd.komelia.ui.LocalRawStatusBarHeight
import snd.komelia.ui.LocalStrings
import snd.komelia.ui.LocalTheme
import snd.komelia.ui.LocalTransparentNavBarPadding
import snd.komelia.ui.common.cards.BookImageCard
import snd.komelia.ui.common.cards.SeriesImageCard
import snd.komelia.ui.common.components.ErrorContent
import snd.komelia.ui.common.components.KoraChipDefaults
import snd.komelia.ui.common.components.Pagination
import snd.komelia.ui.platform.rememberVoiceSearchLauncher
import snd.komelia.ui.search.SearchViewModel.SearchResultsTab
import snd.komga.client.series.KomgaSeries

/**
 * The search tab, Refonte 2: a page title instead of the app bar, one large
 * field, and before anything is typed the recent searches and the books
 * read last. Results are covers in a grid, the way the libraries show them,
 * with the count of each kind on its tab.
 */
@Composable
fun SearchHome(
    vm: SearchViewModel,
    state: LoadState<Unit>,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onSeriesClick: (KomgaSeries) -> Unit,
    onBookClick: (KomeliaBook) -> Unit,
) {
    val s = LocalStrings.current.ui
    val theme = LocalTheme.current
    val statusBarHeight = if (theme.transparentBars) LocalRawStatusBarHeight.current else 0.dp
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val forceShowKeyboard = snd.komelia.ui.platform.rememberForceShowKeyboard()
    val voiceSearch = rememberVoiceSearchLauncher(onResult = { vm.query = it })

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        focusRequester.requestFocus()
        kotlinx.coroutines.delay(100)
        keyboardController?.show()
        forceShowKeyboard()
    }

    // A result opened is the query worth keeping; every debounced keystroke
    // on the way ("cel", "cell") is not.
    val openSeries: (KomgaSeries) -> Unit = { vm.rememberQuery(); onSeriesClick(it) }
    val openBook: (KomeliaBook) -> Unit = { vm.rememberQuery(); onBookClick(it) }

    Column(Modifier.fillMaxSize().padding(top = statusBarHeight)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = if (canGoBack) 4.dp else 16.dp, end = 16.dp, top = 12.dp),
        ) {
            if (canGoBack) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = s.back)
                }
            }
            Text(s.searchTitle, style = MaterialTheme.typography.headlineSmall.copy(fontFamily = MaterialTheme.typography.titleLarge.fontFamily))
        }

        BigSearchField(
            query = vm.query,
            onQueryChange = { vm.query = it },
            onSubmit = { vm.rememberQuery() },
            onVoiceSearch = voiceSearch,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp).focusRequester(focusRequester),
        )

        if (state == LoadState.Loading && vm.query.isNotBlank()) {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(2.dp))
        }

        FilterChipsRow(vm)

        Box(Modifier.fillMaxWidth().weight(1f)) {
            when {
                vm.query.isBlank() -> StartPanel(vm, openBook)
                state is LoadState.Error -> ErrorContent(state.exception.message ?: "Error", onReload = vm::reload)
                state is LoadState.Success || vm.hasAnyResults -> Results(vm, openSeries, openBook)
                else -> Unit
            }
        }
    }
}

@Composable
private fun BigSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onVoiceSearch: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(LocalStrings.current.ui.searchPlaceholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            when {
                query.isNotBlank() -> IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = LocalStrings.current.ui.clear)
                }

                onVoiceSearch != null -> IconButton(onClick = onVoiceSearch) {
                    Icon(Icons.Filled.Mic, contentDescription = LocalStrings.current.ui.voiceSearch)
                }
            }
        },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp),
        shape = RoundedCornerShape(28.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = {
            onSubmit()
            focusManager.clearFocus()
        }),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            unfocusedBorderColor = Color.Transparent,
        ),
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
    )
}

@Composable
private fun FilterChipsRow(vm: SearchViewModel) {
    val libraries by vm.availableLibraries.collectAsState()
    val s = LocalStrings.current.ui
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (libraries.size > 1) {
            item {
                FilterChip(
                    selected = vm.selectedLibraryId == null,
                    onClick = { vm.onSelectedLibraryChange(null) },
                    label = { Text(s.all) },
                    colors = KoraChipDefaults.filterChipColors(),
                    border = KoraChipDefaults.border,
                )
            }
            items(libraries, key = { it.id.value }) { lib ->
                FilterChip(
                    selected = vm.selectedLibraryId == lib.id,
                    onClick = { vm.onSelectedLibraryChange(lib.id) },
                    label = { Text(lib.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    colors = KoraChipDefaults.filterChipColors(),
                    border = KoraChipDefaults.border,
                )
            }
        }
        // Fuzzy search is a modifier of the query like the library filter,
        // so it keeps its place at the end of the same row.
        item {
            FilterChip(
                selected = vm.fuzzyEnabled,
                onClick = { vm.onFuzzyEnabledChange(!vm.fuzzyEnabled) },
                label = { Text(s.fuzzy) },
                colors = KoraChipDefaults.filterChipColors(),
                border = KoraChipDefaults.border,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StartPanel(vm: SearchViewModel, onBookClick: (KomeliaBook) -> Unit) {
    val s = LocalStrings.current.ui
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp + LocalTransparentNavBarPadding.current),
    ) {
        if (vm.recentSearches.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 18.dp)) {
                SectionLabel(s.recentSearches, Modifier.weight(1f))
                TextButton(onClick = vm::clearRecentSearches) { Text(s.clearHistory) }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                vm.recentSearches.forEach { recent ->
                    AssistChip(
                        onClick = { vm.query = recent },
                        label = { Text(recent) },
                        leadingIcon = { Icon(Icons.Rounded.History, null, Modifier.size(16.dp)) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            leadingIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                        border = null,
                    )
                }
            }
        }

        if (vm.recentBooks.isNotEmpty()) {
            SectionLabel(s.recentlyOpened, Modifier.padding(start = 16.dp, top = 22.dp, bottom = 10.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                items(vm.recentBooks, key = { it.id.value }) { book ->
                    BookImageCard(
                        book = book,
                        onBookClick = { onBookClick(book) },
                        showSeriesTitle = true,
                        modifier = Modifier.width(118.dp),
                    )
                }
            }
        }

        if (vm.recentSearches.isEmpty() && vm.recentBooks.isEmpty()) {
            Text(
                s.searchStartHint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
        letterSpacing = 0.8.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
private fun Results(
    vm: SearchViewModel,
    onSeriesClick: (KomgaSeries) -> Unit,
    onBookClick: (KomeliaBook) -> Unit,
) {
    val s = LocalStrings.current.ui
    val nothing = vm.seriesResults.isEmpty() && vm.bookResults.isEmpty() &&
            vm.authorNames.isEmpty() && vm.selectedAuthor == null
    if (nothing && !vm.resultsAreStale) {
        EmptySearchResults()
        return
    }
    val stale = vm.resultsAreStale

    Column(Modifier.fillMaxSize().then(if (stale) Modifier.alpha(.38f) else Modifier)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            ResultTab(s.series, vm.seriesTotalCount, vm.currentTab == SearchResultsTab.SERIES) {
                vm.onSearchTypeChange(SearchResultsTab.SERIES)
            }
            ResultTab(s.books, vm.bookTotalCount, vm.currentTab == SearchResultsTab.BOOKS) {
                vm.onSearchTypeChange(SearchResultsTab.BOOKS)
            }
            ResultTab(s.authors, vm.authorNames.size, vm.currentTab == SearchResultsTab.AUTHORS) {
                vm.onSearchTypeChange(SearchResultsTab.AUTHORS)
            }
        }

        Box(Modifier.fillMaxWidth().weight(1f)) {
            when (vm.currentTab) {
                SearchResultsTab.SERIES -> ResultGrid(
                    cardWidth = vm.cardWidth,
                    entries = vm.seriesResults,
                    key = { it.id.value },
                    currentPage = vm.seriesCurrentPage,
                    totalPages = vm.seriesTotalPages,
                    onPageChange = vm::onSeriesPageChange,
                ) { series ->
                    SeriesImageCard(series = series, onSeriesClick = { onSeriesClick(series) })
                }

                SearchResultsTab.BOOKS -> ResultGrid(
                    cardWidth = vm.cardWidth,
                    entries = vm.bookResults,
                    key = { it.id.value },
                    currentPage = vm.bookCurrentPage,
                    totalPages = vm.bookTotalPages,
                    onPageChange = vm::onBookPageChange,
                ) { book ->
                    BookImageCard(book = book, onBookClick = { onBookClick(book) }, showSeriesTitle = true)
                }

                // Names are a list, and picking one opens that author's works:
                // the existing author view does both.
                SearchResultsTab.AUTHORS -> SearchContent(
                    query = vm.query,
                    searchType = SearchResultsTab.AUTHORS,
                    onSearchTypeChange = vm::onSearchTypeChange,
                    seriesResults = vm.seriesResults,
                    seriesCurrentPage = vm.seriesCurrentPage,
                    seriesTotalPages = vm.seriesTotalPages,
                    onSeriesPageChange = vm::onSeriesPageChange,
                    onSeriesClick = onSeriesClick,
                    bookResults = vm.bookResults,
                    bookCurrentPage = vm.bookCurrentPage,
                    bookTotalPages = vm.bookTotalPages,
                    onBookPageChange = vm::onBookPageChange,
                    onBookClick = onBookClick,
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
                    showToolbar = false,
                )
            }
        }
    }
    // Same guard as SearchContent: rows of the previous query must not open
    // while the new one is in flight.
    if (stale) {
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                        }
                    }
                }
        )
    }
}

@Composable
private fun ResultTab(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text("$label · $count") },
        colors = KoraChipDefaults.filterChipColors(),
        border = KoraChipDefaults.border,
    )
}

@Composable
private fun <T> ResultGrid(
    cardWidth: androidx.compose.ui.unit.Dp,
    entries: List<T>,
    key: (T) -> Any,
    currentPage: Int,
    totalPages: Int,
    onPageChange: (Int) -> Unit,
    card: @Composable (T) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(cardWidth),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp + LocalTransparentNavBarPadding.current),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(entries, key = key) { card(it) }
        if (totalPages > 1) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Pagination(totalPages = totalPages, currentPage = currentPage, onPageChange = onPageChange)
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(1.dp)) }
    }
}
