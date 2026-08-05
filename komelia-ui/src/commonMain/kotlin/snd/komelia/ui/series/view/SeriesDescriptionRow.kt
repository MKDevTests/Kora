package snd.komelia.ui.series.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.LocalDate
import snd.komelia.ui.LocalStrings
import snd.komelia.ui.common.components.ExpandableText
import snd.komelia.ui.library.NextReleaseLabels
import snd.komelia.ui.library.SeriesScreenFilter
import snd.komga.client.common.KomgaReadingDirection
import snd.komga.client.library.KomgaLibrary
import snd.komga.client.series.KomgaAlternativeTitle
import snd.komga.client.series.KomgaSeriesStatus
import snd.komga.client.series.KomgaSeriesStatus.ABANDONED
import snd.komga.client.series.KomgaSeriesStatus.ENDED
import snd.komga.client.series.KomgaSeriesStatus.HIATUS
import snd.komga.client.series.KomgaSeriesStatus.ONGOING

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SeriesDescriptionRow(
    library: KomgaLibrary,
    onLibraryClick: (KomgaLibrary) -> Unit,
    releaseDate: LocalDate?,
    status: KomgaSeriesStatus?,
    ageRating: Int?,
    language: String,
    readingDirection: KomgaReadingDirection?,
    deleted: Boolean,
    alternateTitles: List<KomgaAlternativeTitle>,
    onFilterClick: (SeriesScreenFilter) -> Unit,
    totalBooksCount: Int? = null,
    totalBookCount: Int? = null,
    totalPagesCount: Int? = null,
    pagesLeftCount: Int? = null,
    accentColor: Color? = null,
    showReleaseYear: Boolean = true,
    genres: List<String> = emptyList(),
    nextRelease: NextReleaseLabels.NextRelease? = null,
    modifier: Modifier
) {
    val strings = LocalStrings.current.seriesView
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(5.dp),
        horizontalAlignment = Alignment.Start
    ) {

        // Upcoming volume (from the user's `nextrelease:<vol>-<dd.mm.yyyy>` tag).
        // Caller passes null when absent or already past, so a value here always
        // means "show it". Highlighted so it stands out as actionable info.
        nextRelease?.let { nr ->
            Text(
                text = "Prochain tome ${nr.volume} — ${NextReleaseLabels.formatDate(nr.date)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = accentColor ?: MaterialTheme.colorScheme.primary,
            )
        }

        if (showReleaseYear && releaseDate != null)
            Text(LocalStrings.current.counts.releaseYear(releaseDate.year), style = MaterialTheme.typography.labelSmall)

        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SuggestionChip(
                onClick = { onLibraryClick(library) },
                label = { Text(library.name) },
                icon = { Icon(Icons.AutoMirrored.Outlined.MenuBook, null) },
                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
            )

            if (status != null) {
                SuggestionChip(
                    onClick = { onFilterClick(SeriesScreenFilter(publicationStatus = listOf(status))) },
                    label = { Text(strings.forSeriesStatus(status)) },
                    colors =
                        when (status) {
                            ENDED -> SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f),
                                labelColor = MaterialTheme.colorScheme.secondary
                            )

                            ONGOING -> SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            ABANDONED -> SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                                labelColor = MaterialTheme.colorScheme.onErrorContainer
                            )

                            HIATUS -> SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.25f),
                                labelColor = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        },
                )
            }

            ageRating?.let { age ->
                SuggestionChip(
                    onClick = { onFilterClick(SeriesScreenFilter(ageRating = listOf(age))) },
                    label = { Text("$age+") }
                )
            }

            if (language.isNotBlank())
                SuggestionChip(
                    onClick = { onFilterClick(SeriesScreenFilter(language = listOf(language))) },
                    label = { Text(language) }
                )

            if (readingDirection != null) {
                SuggestionChip(
                    onClick = {},
                    label = { Text(strings.forReadingDirection(readingDirection)) }
                )
            }

            if (deleted) {
                SuggestionChip(
                    onClick = {},
                    label = { Text(LocalStrings.current.ui.unavailable) },
                    border = null,
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                )
            }

            totalBooksCount?.let { booksCount ->
                val booksLabel = buildString {
                    append(booksCount)
                    if (totalBookCount != null) append(" / $totalBookCount")
                    if (booksCount > 1 || totalBookCount?.let { it > 1 } == true) append(" books")
                    else append(" book")
                }
                SuggestionChip(
                    onClick = {},
                    label = { Text(booksLabel) },
                )
            }

            totalPagesCount?.let { pagesCount ->
                SuggestionChip(
                    onClick = {},
                    label = { Text(LocalStrings.current.counts.pages(pagesCount)) },
                )
            }

            pagesLeftCount?.let { pagesLeft ->
                SuggestionChip(
                    onClick = {},
                    label = { Text(LocalStrings.current.counts.pagesLeft(pagesLeft)) },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        labelColor = accentColor ?: MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    border = SuggestionChipDefaults.suggestionChipBorder(
                        enabled = true,
                        borderColor = accentColor ?: MaterialTheme.colorScheme.outline
                    )
                )
            }
        }

        // Kora genre tags (kora:genre:*) shown as a plain readable line below the
        // chips. Pre-cleaned by the caller via GenreLabels; empty on screens that
        // don't pass them (oneshots / books).
        if (genres.isNotEmpty()) {
            Text(
                text = LocalStrings.current.ui.genres2 + genres.joinToString(", "),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        if (alternateTitles.isNotEmpty()) {
            SelectionContainer {
                Column {
                    Text(LocalStrings.current.ui.alternativeTitles, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    alternateTitles.forEach {
                        Row {
                            Text(
                                it.label,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.widthIn(min = 100.dp, max = 200.dp)
                            )
                            Text(
                                it.title,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SeriesSummary(
    seriesSummary: String,
    bookSummary: String,
    bookSummaryNumber: String,
) {
    val summaryText = remember(seriesSummary) {
        if (seriesSummary.isNotBlank()) {
            seriesSummary
        } else if (bookSummary.isNotBlank()) {
            "Summary from book ${bookSummaryNumber}:\n" + bookSummary
        } else null
    }
    if (summaryText != null) {
        ExpandableText(
            text = summaryText,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}