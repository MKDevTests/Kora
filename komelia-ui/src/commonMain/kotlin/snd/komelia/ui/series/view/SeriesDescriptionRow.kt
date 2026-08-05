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
import androidx.compose.material3.AssistChip
import androidx.compose.ui.text.style.TextOverflow
import snd.komelia.ui.library.GenreLabels
import androidx.compose.material3.AssistChipDefaults

/**
 * Another edition of the same work: what it is, and where it leads.
 *
 * The KIND, not a ready-made string: "Berserk" in a chip next to a series
 * already called Berserk says nothing — what the reader needs to know is that
 * this one is the colour edition. The name only earns its place when it
 * differs, and the wording belongs to the translation, not to the state.
 */
data class OtherVersion(
    val seriesId: snd.komga.client.series.KomgaSeriesId,
    val kind: Kind,
    /** Language code or differing title; null when the name adds nothing. */
    val detail: String? = null,
) {
    enum class Kind { VERSION, LANGUAGE, COLOURED }
}

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
    onGenreClick: ((String) -> Unit)? = null,
    otherVersions: List<OtherVersion> = emptyList(),
    onVersionClick: ((snd.komga.client.series.KomgaSeriesId) -> Unit)? = null,
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

        // Kora genre tags (kora:genre:*), as chips rather than a comma line:
        // they are the one piece of metadata people navigate BY, and a chip
        // says "you can tap this" where a sentence does not.
        if (genres.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                genres.forEach { genre ->
                    AssistChip(
                        onClick = { onGenreClick?.invoke(genre) },
                        enabled = onGenreClick != null,
                        colors = tappableChipColors(),
                        label = {
                            Text(
                                text = GenreLabels.label(genre),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Medium,
                            )
                        },
                    )
                }
            }
        }

        // The other editions of this same work — languages, colour editions,
        // reprints. They live in the Links tab, which is one tap away and one
        // the user has to think of; here they sit where the eye already is.
        if (otherVersions.isNotEmpty()) {
            val editions = LocalStrings.current.editions
            Text(
                text = editions.heading,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                otherVersions.forEach { version ->
                    val kind = when (version.kind) {
                        OtherVersion.Kind.VERSION -> editions.otherVersion
                        OtherVersion.Kind.LANGUAGE -> editions.otherLanguage
                        OtherVersion.Kind.COLOURED -> editions.colourEdition
                    }
                    AssistChip(
                        onClick = { onVersionClick?.invoke(version.seriesId) },
                        enabled = onVersionClick != null,
                        colors = tappableChipColors(),
                        label = {
                            Text(
                                text = version.detail?.let { "$kind · $it" } ?: kind,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
            }
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
/**
 * Chip colours that read as "you can tap this".
 *
 * The default assist chip is a transparent outline, which sits at the same
 * visual weight as the sentences around it — these are navigation, and they
 * have to look like it without turning into buttons.
 */
@Composable
private fun tappableChipColors() = AssistChipDefaults.assistChipColors(
    containerColor = MaterialTheme.colorScheme.surfaceVariant,
    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
)
