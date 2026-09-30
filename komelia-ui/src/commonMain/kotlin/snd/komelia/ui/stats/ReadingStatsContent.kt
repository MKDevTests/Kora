package snd.komelia.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import snd.komelia.stats.ReadingStats
import snd.komelia.stats.RecentSeriesEntry
import snd.komelia.ui.LocalStrings
import snd.komelia.ui.common.images.SeriesThumbnail
import snd.komelia.ui.pushUnique
import snd.komelia.ui.series.SeriesScreen
import snd.komelia.ui.settings.components.SettingsChoiceRow
import snd.komelia.ui.strings.UiStrings
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/*
 * Refonte 2: the streak opens the page, the three totals are large, the
 * history is one card with its window choice inside, and what was read
 * last shows its cover and how long ago instead of a raw timestamp.
 */
@Composable
fun ReadingStatsContent(
    stats: ReadingStats,
    onRefresh: () -> Unit,
) {
    val s = LocalStrings.current.ui
    // The hosting SettingsScreenContainer already scrolls: no second
    // vertical scroll here (nested scrollables crash on infinite height).
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (stats.isEmpty) {
            EmptyStatsState(onRefresh = onRefresh)
            return@Column
        }

        StreakCard(stats, onRefresh)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BigNumber(s.thisWeek, stats.booksFinishedLast7Days.toString(), s.booksFinished, Modifier.weight(1f))
            BigNumber(s.thisMonth, stats.booksFinishedLast30Days.toString(), s.booksFinished, Modifier.weight(1f))
            BigNumber(s.lifetime, groupThousands(stats.lifetimeBooksFinished.toLong()), s.booksFinished, Modifier.weight(1f))
        }

        // Only books completed since 1.0.10 carry a page count, hence the
        // note: the total is not "since Kora was installed".
        Column {
            Text(
                "${s.pagesRead.uppercase()} · ${s.sinceV1010}",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BigNumber(s.thisWeek, formatPages(stats.pagesReadLast7Days), s.pages, Modifier.weight(1f), small = true)
                BigNumber(s.thisMonth, formatPages(stats.pagesReadLast30Days), s.pages, Modifier.weight(1f), small = true)
                BigNumber(s.total, formatPages(stats.pagesReadLifetime), s.pages, Modifier.weight(1f), small = true)
            }
        }

        HistoryCard(stats)

        if (stats.recentSeries.isNotEmpty()) {
            RecentlyRead(stats.recentSeries)
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun StreakCard(stats: ReadingStats, onRefresh: () -> Unit) {
    val s = LocalStrings.current.ui
    val primary = MaterialTheme.colorScheme.primary
    Surface(shape = RoundedCornerShape(16.dp), color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        listOf(primary.copy(alpha = 0.22f), MaterialTheme.colorScheme.surfaceContainer)
                    )
                )
                .padding(start = 18.dp, top = 14.dp, bottom = 14.dp, end = 6.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(44.dp).clip(CircleShape).background(Color(0xFFFB923C).copy(alpha = 0.18f)),
            ) {
                Icon(Icons.Rounded.LocalFireDepartment, null, tint = Color(0xFFFB923C))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    if (stats.streakDays > 0) s.streakDays(stats.streakDays) else s.noStreak,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    s.statsMonthLine(stats.booksFinishedLast30Days, stats.lifetimeSeriesFinished),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = s.refresh, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun BigNumber(label: String, value: String, hint: String, modifier: Modifier = Modifier, small: Boolean = false) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(
                value,
                style = (if (small) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium)
                    .copy(fontFamily = MaterialTheme.typography.titleLarge.fontFamily),
                color = if (small) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                maxLines = 1,
                modifier = Modifier.padding(vertical = 2.dp),
            )
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

private enum class HistoryWindow { DAYS_7, DAYS_30, MONTHS_12 }

@Composable
private fun HistoryCard(stats: ReadingStats) {
    val s = LocalStrings.current.ui
    // 12 months by default, as before 1.0.12; not persisted on purpose.
    var window by remember { mutableStateOf(HistoryWindow.MONTHS_12) }
    val bars: List<Pair<String, Int>> = when (window) {
        HistoryWindow.DAYS_7 -> stats.dailyHistory7d.map { dayLabel(it.date, s) to it.count }
        HistoryWindow.DAYS_30 -> stats.dailyHistory30d.map { dayLabel(it.date, s) to it.count }
        // Leading empty months are dropped (three kept at least): a reader
        // who started in July does not need ten months of nothing first.
        HistoryWindow.MONTHS_12 -> stats.monthlyHistory.map { monthLabel(it.yearMonth, s) to it.count }
            .let { all -> all.dropWhile { it.second == 0 }.let { kept -> if (kept.size < 3) all.takeLast(3) else kept } }
    }

    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 6.dp)) {
            Text(
                s.booksFinished.replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp),
            )
            SettingsChoiceRow(
                label = null,
                options = listOf(
                    HistoryWindow.DAYS_7 to s.window7Days,
                    HistoryWindow.DAYS_30 to s.window30Days,
                    HistoryWindow.MONTHS_12 to s.window12Months,
                ),
                selected = window,
                onSelect = { window = it },
            )
            BarChart(bars, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
    }
}

/**
 * Bars as boxes, so each carries its own count above and its label below.
 * The largest bar is the accent; the others a quieter shade of it. Daily
 * windows only label every few bars, 30 labels do not fit a phone.
 */
@Composable
private fun BarChart(bars: List<Pair<String, Int>>, modifier: Modifier = Modifier) {
    val max = (bars.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
    val primary = MaterialTheme.colorScheme.primary
    val labelEvery = when {
        bars.size <= 12 -> 1
        else -> (bars.size + 5) / 6
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(if (bars.size > 12) 3.dp else 8.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = modifier.fillMaxWidth().height(190.dp),
    ) {
        bars.forEachIndexed { index, (label, count) ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            ) {
                Text(
                    if (count == 0 || bars.size > 12) "" else count.toString(),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                )
                val fraction = count.toFloat() / max
                Box(
                    Modifier
                        .padding(top = 4.dp)
                        .fillMaxWidth()
                        .height((130f * fraction).coerceAtLeast(if (count == 0) 3f else 6f).dp)
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 2.dp, bottomEnd = 2.dp))
                        .background(
                            when {
                                count == 0 -> MaterialTheme.colorScheme.surfaceContainerHighest
                                count == max -> primary
                                else -> primary.copy(alpha = 0.5f)
                            }
                        )
                )
                Text(
                    if (index % labelEvery == 0 || index == bars.lastIndex) label else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Visible,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun RecentlyRead(entries: List<RecentSeriesEntry>) {
    val s = LocalStrings.current.ui
    val navigator = LocalNavigator.current
    val now = remember { Clock.System.now() }
    Column {
        Text(
            s.recentlyRead.uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp, bottom = 10.dp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            entries.forEach { entry ->
                Column(
                    modifier = Modifier
                        .width(112.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { navigator?.pushUnique(SeriesScreen(entry.seriesId)) },
                ) {
                    SeriesThumbnail(
                        entry.seriesId,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(0.7f).clip(RoundedCornerShape(12.dp)),
                    )
                    Text(
                        entry.seriesTitle,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Text(
                        relativeTime(entry.lastReadAt, now, s),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyStatsState(onRefresh: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = LocalStrings.current.ui.noReadingActivityYet,
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = LocalStrings.current.ui.finishYourFirstBookTo,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        IconButton(onClick = onRefresh) {
            Icon(Icons.Default.Refresh, contentDescription = LocalStrings.current.ui.refresh)
        }
    }
}

// ---------- formatting -----------------------------------------------------

/** "5 min ago", "yesterday", "3 days ago": a timestamp nobody has to read. */
internal fun relativeTime(then: Instant, now: Instant, s: UiStrings): String {
    val d = now - then
    return when {
        d < 1.minutes -> s.justNow
        d < 1.hours -> s.minutesAgo(d.inWholeMinutes.toInt())
        d < 24.hours -> s.hoursAgo(d.inWholeHours.toInt())
        d < 48.hours -> s.yesterday
        d < 30.days -> s.daysAgo(d.inWholeDays.toInt())
        d < 365.days -> s.monthsAgo((d.inWholeDays / 30).toInt().coerceAtLeast(1))
        else -> s.yearsAgo((d.inWholeDays / 365).toInt().coerceAtLeast(1))
    }
}

/** "2026-08" -> "août" / "Aug". */
private fun monthLabel(yearMonth: String, s: UiStrings): String {
    val month = yearMonth.substringAfter('-').toIntOrNull() ?: return yearMonth
    return s.monthShort(month)
}

/** "2026-09-12" -> "12 sept." / "12 Sep". */
private fun dayLabel(date: String, s: UiStrings): String {
    val parts = date.split('-')
    val month = parts.getOrNull(1)?.toIntOrNull() ?: return date
    val day = parts.getOrNull(2)?.toIntOrNull() ?: return date
    return "$day ${s.monthShort(month)}"
}

private fun groupThousands(n: Long): String =
    n.toString().reversed().chunked(3).joinToString(" ").reversed()

/** Short under 10 000 ("1 450"), compact above ("12,3k"), so a card never overflows. */
private fun formatPages(pages: Long): String = when {
    pages < 10_000 -> groupThousands(pages)
    pages < 1_000_000 -> {
        val tenths = pages / 100
        if (tenths % 10 == 0L) "${tenths / 10}k" else "${tenths / 10},${tenths % 10}k"
    }

    else -> {
        val tenths = pages / 100_000
        if (tenths % 10 == 0L) "${tenths / 10}M" else "${tenths / 10},${tenths % 10}M"
    }
}
