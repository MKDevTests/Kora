package snd.komelia.ui.reader.epub

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.Url
import snd.komelia.bookmarks.EpubBookmark
import snd.komelia.ui.LocalAccentColor
import kotlin.math.roundToInt
import snd.komelia.ui.LocalStrings

private fun containsHref(link: Link, targetHref: Url): Boolean {
    if (link.url()?.removeFragment() == targetHref) return true
    return link.children.any { containsHref(it, targetHref) }
}

private fun expandAncestors(links: List<Link>, targetHref: Url, expandedState: MutableMap<String, Boolean>) {
    for (link in links) {
        if (link.children.any { containsHref(it, targetHref) }) {
            expandedState[link.href.toString()] = true
            expandAncestors(link.children, targetHref, expandedState)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Epub3ContentDialog(
    toc: List<Link>,
    bookmarks: List<EpubBookmark>,
    positions: List<Locator>,
    searchQuery: String,
    searchResults: List<Locator>,
    isSearching: Boolean,
    currentHref: Url?,
    currentLocator: Locator?,
    onNavigateLink: (Link) -> Unit,
    onNavigateLocator: (Locator) -> Unit,
    onDeleteBookmark: (EpubBookmark) -> Unit,
    annotations: List<snd.komelia.annotations.BookAnnotation>,
    onAnnotationTap: (snd.komelia.annotations.BookAnnotation) -> Unit,
    onDeleteAnnotation: (snd.komelia.annotations.BookAnnotation) -> Unit,
    onSearch: (String) -> Unit,
    onDismiss: () -> Unit,
    initialTab: Int = 0,
    modifier: Modifier = Modifier,
) {
    var dragOffsetY by remember { mutableStateOf(0f) }
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 2f / 3f).dp
    val pagerState = rememberPagerState(initialPage = initialTab, pageCount = { 4 })
    val coroutineScope = rememberCoroutineScope()
    val theme = snd.komelia.ui.LocalTheme.current
    val surfaceColor = if (theme.type == snd.komelia.ui.Theme.ThemeType.DARK) Color(43, 43, 43)
    else MaterialTheme.colorScheme.background

    Surface(
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = surfaceColor,
        tonalElevation = 0.dp,
        modifier = modifier
            .heightIn(max = maxHeight)
            .offset { IntOffset(0, dragOffsetY.roundToInt().coerceAtLeast(0)) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .navigationBarsPadding(),
        ) {
            // Drag handle
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                if (dragOffsetY > 120f) onDismiss()
                                else dragOffsetY = 0f
                            },
                            onDragCancel = { dragOffsetY = 0f },
                            onVerticalDrag = { _, delta ->
                                dragOffsetY = (dragOffsetY + delta).coerceAtLeast(0f)
                            }
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                BottomSheetDefaults.DragHandle()
            }

            SecondaryTabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = Color.Transparent,
            ) {
                // Use content slot (not text=) so we can reduce horizontal padding from
                // the default 16 dp to 6 dp — enough room for "Bookmarks" to stay on one line.
                listOf("Contents", "Bookmarks", "Notes", "Search").forEachIndexed { index, label ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } },
                    ) {
                        Text(
                            text = label,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                        )
                    }
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.Top,
            ) { page ->
                when (page) {
                    0 -> ContentsTab(toc, currentHref, onNavigateLink)
                    1 -> BookmarksTab(bookmarks, positions, onNavigateLocator, onDeleteBookmark)
                    2 -> AnnotationsTab(annotations, positions, onAnnotationTap, onDeleteAnnotation)
                    3 -> SearchTab(searchQuery, searchResults, positions, isSearching, onSearch, onNavigateLocator)
                }
            }
        }
    }
}

@Composable
private fun ContentsTab(
    toc: List<Link>,
    currentHref: Url?,
    onNavigate: (Link) -> Unit,
) {
    val expandedState = remember { mutableStateMapOf<String, Boolean>() }
    val lazyListState = rememberLazyListState()

    val currentTopLevelIndex = remember(currentHref, toc) {
        if (currentHref == null) -1
        else {
            val target = currentHref.removeFragment()
            toc.indexOfFirst { containsHref(it, target) }
        }
    }

    LaunchedEffect(currentHref) {
        if (currentHref != null) {
            val target = currentHref.removeFragment()
            expandAncestors(toc, target, expandedState)
            if (currentTopLevelIndex >= 0) {
                lazyListState.scrollToItem(currentTopLevelIndex)
            }
        }
    }

    if (toc.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            Text(
                text = LocalStrings.current.ui.noChaptersAvailable,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        LazyColumn(
            state = lazyListState,
        ) {
            itemsIndexed(toc) { index, link ->
                if (index > 0) HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = dividerColor
                )
                TocRow(
                    link = link,
                    depth = 0,
                    currentHref = currentHref,
                    expandedState = expandedState,
                    onNavigate = onNavigate,
                )
            }
        }
    }
}

@Composable
private fun TocRow(
    link: Link,
    depth: Int,
    currentHref: Url?,
    expandedState: MutableMap<String, Boolean>,
    onNavigate: (Link) -> Unit,
) {
    val key = link.href.toString()
    val hasChildren = link.children.isNotEmpty()
    val isExpanded = expandedState[key] ?: false
    val title = link.title
        ?: link.href.toString().substringAfterLast('/').substringBeforeLast('.')

    val isCurrentChapter = currentHref != null &&
        link.url()?.removeFragment() == currentHref.removeFragment()

    val accentColor = LocalAccentColor.current ?: MaterialTheme.colorScheme.secondary
    val buttonColors = if (isCurrentChapter) {
        ButtonDefaults.textButtonColors(
            containerColor = accentColor.copy(alpha = 0.15f),
            contentColor = MaterialTheme.colorScheme.onSurface,
        )
    } else {
        ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
    }

    if (hasChildren) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = (depth * 16).dp),
        ) {
            TextButton(
                onClick = { onNavigate(link) },
                colors = buttonColors,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            IconButton(onClick = { expandedState[key] = !isExpanded }) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (isExpanded) {
            link.children.forEach { child ->
                TocRow(
                    link = child,
                    depth = depth + 1,
                    currentHref = currentHref,
                    expandedState = expandedState,
                    onNavigate = onNavigate,
                )
            }
        }
    } else {
        TextButton(
            onClick = { onNavigate(link) },
            colors = buttonColors,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .padding(start = (depth * 16).dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun BookmarksTab(
    bookmarks: List<EpubBookmark>,
    positions: List<Locator>,
    onNavigate: (Locator) -> Unit,
    onDeleteBookmark: (EpubBookmark) -> Unit,
) {
    if (bookmarks.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            Text(
                text = LocalStrings.current.ui.noBookmarksYet,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        LazyColumn {
            itemsIndexed(bookmarks) { index, bookmark ->
                if (index > 0) HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = dividerColor
                )
                BookmarkRow(
                    bookmark = bookmark,
                    positions = positions,
                    onNavigate = onNavigate,
                    onDelete = { onDeleteBookmark(bookmark) }
                )
            }
        }
    }
}


@Composable
private fun BookmarkRow(
    bookmark: EpubBookmark,
    positions: List<Locator>,
    onNavigate: (Locator) -> Unit,
    onDelete: () -> Unit,
) {
    val locator = remember(bookmark.locatorJson) {
        runCatching { Locator.fromJSON(JSONObject(bookmark.locatorJson)) }.getOrNull()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { locator?.let { onNavigate(it) } }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            val chapterTitle = locator?.title ?: "Chapter Unknown"
            Text(
                text = chapterTitle,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            val positionIndex = if (locator != null) locatorToPositionIndex(positions, locator) else -1
            val locationText = if (positions.isNotEmpty() && positionIndex != -1) "Location: ${positionIndex + 1} of ${positions.size}"
            else "Location: Unknown"

            Text(
                text = locationText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = LocalStrings.current.ui.deleteBookmark)
        }
    }
}

@Composable
private fun SearchTab(
    searchQuery: String,
    searchResults: List<Locator>,
    positions: List<Locator>,
    isSearching: Boolean,
    onSearch: (String) -> Unit,
    onNavigate: (Locator) -> Unit,
) {
    var query by remember { mutableStateOf(searchQuery) }

    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(LocalStrings.current.ui.search) },
            shape = CircleShape,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                onSearch(query)
                keyboardController?.hide()
            }),
            trailingIcon = {
                IconButton(onClick = {
                    onSearch(query)
                    keyboardController?.hide()
                }) {
                    Icon(Icons.Default.Search, contentDescription = LocalStrings.current.ui.search)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${searchResults.size} Results",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (isSearching) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(start = 16.dp)
                        .size(20.dp),
                    strokeWidth = 2.dp
                )
            }
        }

        if (searchResults.isNotEmpty()) {
            val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                itemsIndexed(searchResults) { index, locator ->
                    if (index > 0) HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = dividerColor
                    )
                    SearchResultRow(index, locator, positions, onNavigate)
                }
            }
        }
    }
}

@Composable
private fun AnnotationsTab(
    annotations: List<snd.komelia.annotations.BookAnnotation>,
    positions: List<Locator>,
    onTap: (snd.komelia.annotations.BookAnnotation) -> Unit,
    onDelete: (snd.komelia.annotations.BookAnnotation) -> Unit,
) {
    if (annotations.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            Text(
                text = LocalStrings.current.ui.noAnnotationsYet,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        val sorted = remember(annotations) {
            annotations.sortedBy { annotation ->
                (annotation.location as? snd.komelia.annotations.AnnotationLocation.EpubLocation)
                    ?.let { loc ->
                        runCatching {
                            Locator.fromJSON(JSONObject(loc.locatorJson))
                        }.getOrNull()?.locations?.totalProgression
                    } ?: 0.0
            }
        }
        LazyColumn {
            itemsIndexed(sorted) { index, annotation ->
                if (index > 0) HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = dividerColor
                )
                val location = annotation.location as? snd.komelia.annotations.AnnotationLocation.EpubLocation
                val locator = remember(location?.locatorJson) {
                    location?.let { runCatching { Locator.fromJSON(JSONObject(it.locatorJson)) }.getOrNull() }
                }
                val positionIndex = if (locator != null) locatorToPositionIndex(positions, locator) else -1
                val locationLabel = buildString {
                    append(locator?.title ?: "Unknown chapter")
                    if (positions.isNotEmpty() && positionIndex >= 0) {
                        append(" · Location ${positionIndex + 1} of ${positions.size}")
                    }
                }
                snd.komelia.ui.reader.common.AnnotationRow(
                    annotation = annotation,
                    locationLabel = locationLabel,
                    onTap = { onTap(annotation) },
                    onDelete = { onDelete(annotation) },
                )
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    index: Int,
    locator: Locator,
    positions: List<Locator>,
    onNavigate: (Locator) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onNavigate(locator) }
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        val chapterTitle = locator.title ?: "Chapter Unknown"
        Text(
            text = "${index + 1}. $chapterTitle",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        val positionIndex = locatorToPositionIndex(positions, locator)
        val locationText = if (positions.isNotEmpty()) "Location: ${positionIndex + 1} of ${positions.size}"
        else "Location: Unknown"

        Text(
            text = locationText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, bottom = 6.dp)
        )

        val before = (locator.text.before ?: "").replace(Regex("\\s+"), " ")
        val highlight = (locator.text.highlight ?: "").replace(Regex("\\s+"), " ")
        val after = (locator.text.after ?: "").replace(Regex("\\s+"), " ")

        val truncatedBefore = if (before.length > 50) "..." + before.takeLast(50) else before
        val truncatedAfter = if (after.length > 50) after.take(50) + "..." else after

        val accentColor = LocalAccentColor.current ?: MaterialTheme.colorScheme.primary
        val annotatedText = buildAnnotatedString {
            append(truncatedBefore)
            val startIndex = length
            append(highlight)
            addStyle(
                style = SpanStyle(color = accentColor, fontWeight = FontWeight.Bold),
                start = startIndex,
                end = length
            )
            append(truncatedAfter)
        }

        Text(
            text = annotatedText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
