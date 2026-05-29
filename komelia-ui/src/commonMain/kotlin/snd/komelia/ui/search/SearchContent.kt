package snd.komelia.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.ui.LocalTheme
import snd.komelia.ui.LocalTransparentNavBarPadding
import snd.komelia.ui.LocalWindowWidth
import snd.komelia.ui.common.cards.BookDetailedListCard
import snd.komelia.ui.common.cards.SeriesDetailedListCard
import snd.komelia.ui.common.components.Pagination
import snd.komelia.ui.platform.VerticalScrollbar
import snd.komelia.ui.platform.WindowSizeClass
import snd.komelia.ui.search.SearchViewModel.SearchResultsTab
import snd.komga.client.series.KomgaSeries

@Composable
fun SearchContent(
    query: String,
    searchType: SearchResultsTab,
    onSearchTypeChange: (SearchResultsTab) -> Unit,

    bookResults: List<KomeliaBook>,
    bookCurrentPage: Int,
    bookTotalPages: Int,
    onBookPageChange: (Int) -> Unit,
    onBookClick: (KomeliaBook) -> Unit,

    seriesResults: List<KomgaSeries>,
    seriesCurrentPage: Int,
    seriesTotalPages: Int,
    onSeriesPageChange: (Int) -> Unit,
    onSeriesClick: (KomgaSeries) -> Unit,

    authorNames: List<String>,
    selectedAuthor: String?,
    onAuthorSelected: (String) -> Unit,
    onAuthorCleared: () -> Unit,
    authorSeriesResults: List<KomgaSeries>,
    authorSeriesCurrentPage: Int,
    authorSeriesTotalPages: Int,
    onAuthorSeriesPageChange: (Int) -> Unit,
    authorBookResults: List<KomeliaBook>,
    authorBookCurrentPage: Int,
    authorBookTotalPages: Int,
    onAuthorBookPageChange: (Int) -> Unit,
) {
    if (query.isNotBlank() &&
        bookResults.isEmpty() &&
        seriesResults.isEmpty() &&
        authorNames.isEmpty() &&
        selectedAuthor == null
    ) {
        EmptySearchResults()
        return
    }

    Box(
        contentAlignment = Alignment.TopCenter
    ) {
        val widthModifier = when (LocalWindowWidth.current) {
            WindowSizeClass.COMPACT, WindowSizeClass.MEDIUM -> Modifier.fillMaxWidth()
            WindowSizeClass.EXPANDED -> Modifier.fillMaxWidth(.8f)
            WindowSizeClass.FULL -> Modifier.width(1200.dp)
        }
        val scrollState = rememberLazyListState()

        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SearchToolBar(
                searchType = searchType,
                onSearchTypeChange = onSearchTypeChange,
                hasSeries = seriesTotalPages > 0,
                hasBooks = bookTotalPages > 0,
                modifier = widthModifier
            )

            LazyColumn(
                state = scrollState,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                when (searchType) {
                    SearchResultsTab.SERIES -> {
                        items(seriesResults) { series ->
                            SeriesDetailedListCard(
                                series = series,
                                onClick = { onSeriesClick(series) },
                                modifier = widthModifier
                            )
                        }
                        item {
                            Pagination(
                                totalPages = seriesTotalPages,
                                currentPage = seriesCurrentPage,
                                onPageChange = onSeriesPageChange
                            )
                        }
                        item { TransparentBarSpacer() }
                    }

                    SearchResultsTab.BOOKS -> {
                        items(bookResults) { book ->
                            BookDetailedListCard(
                                book = book,
                                onClick = { onBookClick(book) },
                                modifier = widthModifier
                            )
                        }
                        item {
                            Pagination(
                                totalPages = bookTotalPages,
                                currentPage = bookCurrentPage,
                                onPageChange = onBookPageChange
                            )
                        }
                        item { TransparentBarSpacer() }
                    }

                    SearchResultsTab.AUTHORS -> {
                        if (selectedAuthor == null) {
                            items(authorNames, key = { it }) { name ->
                                AuthorListItem(
                                    name = name,
                                    onClick = { onAuthorSelected(name) },
                                    modifier = widthModifier
                                )
                            }
                            item { TransparentBarSpacer() }
                        } else {
                            item {
                                AuthorWorksHeader(
                                    author = selectedAuthor,
                                    onBack = onAuthorCleared,
                                    modifier = widthModifier
                                )
                            }
                            if (authorSeriesResults.isNotEmpty()) {
                                item { AuthorSectionLabel("Series", widthModifier) }
                                items(authorSeriesResults) { series ->
                                    SeriesDetailedListCard(
                                        series = series,
                                        onClick = { onSeriesClick(series) },
                                        modifier = widthModifier
                                    )
                                }
                                item {
                                    Pagination(
                                        totalPages = authorSeriesTotalPages,
                                        currentPage = authorSeriesCurrentPage,
                                        onPageChange = onAuthorSeriesPageChange
                                    )
                                }
                            }
                            if (authorBookResults.isNotEmpty()) {
                                item { AuthorSectionLabel("Books", widthModifier) }
                                items(authorBookResults) { book ->
                                    BookDetailedListCard(
                                        book = book,
                                        onClick = { onBookClick(book) },
                                        modifier = widthModifier
                                    )
                                }
                                item {
                                    Pagination(
                                        totalPages = authorBookTotalPages,
                                        currentPage = authorBookCurrentPage,
                                        onPageChange = onAuthorBookPageChange
                                    )
                                }
                            }
                            if (authorSeriesResults.isEmpty() && authorBookResults.isEmpty()) {
                                item {
                                    Text(
                                        "No works found for this author",
                                        modifier = Modifier.padding(16.dp)
                                    )
                                }
                            }
                            item { TransparentBarSpacer() }
                        }
                    }
                }
            }
        }

        VerticalScrollbar(scrollState, Modifier.align(Alignment.TopEnd))
    }
}

@Composable
private fun TransparentBarSpacer() {
    val theme = LocalTheme.current
    if (theme.transparentBars) {
        Spacer(Modifier.height(LocalTransparentNavBarPadding.current))
    }
}

@Composable
private fun AuthorListItem(
    name: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(name, style = MaterialTheme.typography.bodyLarge)
        }
        HorizontalDivider()
    }
}

@Composable
private fun AuthorWorksHeader(
    author: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to authors")
        }
        Text(author, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun AuthorSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    )
}

@Composable
private fun EmptySearchResults() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(100.dp))
        Text("The search returned no results", style = MaterialTheme.typography.titleLarge)
        Text("Try searching for something else")
    }
}

@Composable
fun SearchToolBar(
    searchType: SearchResultsTab,
    onSearchTypeChange: (SearchResultsTab) -> Unit,
    hasSeries: Boolean,
    hasBooks: Boolean,
    modifier: Modifier,
) {
    val tabs = listOfNotNull(
        if (hasSeries) SearchResultsTab.SERIES else null,
        if (hasBooks) SearchResultsTab.BOOKS else null,
        SearchResultsTab.AUTHORS,
    )

    SecondaryTabRow(
        selectedTabIndex = tabs.indexOf(searchType).coerceAtLeast(0),
        modifier = modifier,
    ) {
        if (hasSeries) Tab(
            selected = searchType == SearchResultsTab.SERIES,
            onClick = { onSearchTypeChange(SearchResultsTab.SERIES) },
            modifier = Modifier.heightIn(min = 40.dp),
            text = { Text("Series") },
        )
        if (hasBooks) Tab(
            selected = searchType == SearchResultsTab.BOOKS,
            onClick = { onSearchTypeChange(SearchResultsTab.BOOKS) },
            modifier = Modifier.heightIn(min = 40.dp),
            text = { Text("Books") },
        )
        Tab(
            selected = searchType == SearchResultsTab.AUTHORS,
            onClick = { onSearchTypeChange(SearchResultsTab.AUTHORS) },
            modifier = Modifier.heightIn(min = 40.dp),
            text = { Text("Authors") },
        )
    }
}
