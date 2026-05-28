package snd.komelia.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import snd.komelia.stats.Achievement
import snd.komelia.stats.MonthBucket
import snd.komelia.stats.ReadingStats
import snd.komelia.stats.RecentSeriesEntry

@Composable
fun ReadingStatsContent(
    stats: ReadingStats,
    onRefresh: () -> Unit,
) {
    // The hosting SettingsScreenContainer already wraps us in a
    // .verticalScroll(...), so we MUST NOT add another vertical scroll
    // here (that would nest two scrollable containers and crash with
    // "infinity maximum height constraints"). We just lay out content
    // vertically; the container handles scrolling.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        if (stats.isEmpty) {
            EmptyStatsState(onRefresh = onRefresh)
            return@Column
        }

        // --- Top stats (4 cards in 2x2 grid) -------------------------------
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(label = "This week", value = stats.booksFinishedLast7Days.toString(),
                hint = "books finished", modifier = Modifier.weight(1f))
            StatCard(label = "This month", value = stats.booksFinishedLast30Days.toString(),
                hint = "books finished", modifier = Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(label = "Streak", value = stats.streakDays.toString(),
                hint = if (stats.streakDays > 1) "days in a row" else "day in a row",
                modifier = Modifier.weight(1f))
            StatCard(label = "Lifetime", value = stats.lifetimeBooksFinished.toString(),
                hint = "books finished", modifier = Modifier.weight(1f))
        }

        // --- Pages read (v1.0.10+) -----------------------------------------
        // Only counts books completed since the v1.0.10 upgrade — events
        // recorded before then have no page_count attached. We show the
        // hint as a small note under the section header so the totals
        // aren't misread as lifetime-since-Kora-install.
        SectionHeader(title = "Pages read", subtitle = "since v1.0.10")
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(label = "This week", value = formatPages(stats.pagesReadLast7Days),
                hint = "pages", modifier = Modifier.weight(1f))
            StatCard(label = "This month", value = formatPages(stats.pagesReadLast30Days),
                hint = "pages", modifier = Modifier.weight(1f))
            StatCard(label = "Total", value = formatPages(stats.pagesReadLifetime),
                hint = "pages", modifier = Modifier.weight(1f))
        }

        // --- History chart with window selector (v1.0.12+) ------------------
        // The user can switch between 7 days / 30 days / 12 months without
        // refetching — all three datasets are computed up-front in
        // ReadingStatsService.compute and stashed on ReadingStats.
        HistorySection(stats = stats, onRefresh = onRefresh)

        // --- Achievements --------------------------------------------------
        if (stats.achievements.any { it.earned }) {
            SectionHeader(title = "Achievements")
            AchievementsRow(achievements = stats.achievements)
        }

        // --- Recent activity ----------------------------------------------
        if (stats.recentSeries.isNotEmpty()) {
            SectionHeader(title = "Recently read")
            stats.recentSeries.forEach { entry ->
                RecentSeriesRow(entry)
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

// ---------- subcomponents ---------------------------------------------------

@Composable
private fun StatCard(
    label: String,
    value: String,
    hint: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String? = null,
    onRefresh: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (onRefresh != null) {
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }
        }
    }
}

/**
 * Compact formatter for pages-read totals: keeps short numbers readable
 * ("1234" → "1,234") and switches to compact unit notation above 10k
 * ("12345" → "12.3k", "1234567" → "1.2M") so the StatCard's headline
 * style doesn't overflow on three-tile rows.
 */
private fun formatPages(pages: Long): String = when {
    pages < 10_000 ->
        // Insert thousands separator: "1234" -> "1,234"
        pages.toString().reversed().chunked(3).joinToString(",").reversed()
    pages < 1_000_000 -> {
        val k = pages / 1000.0
        if (k == k.toLong().toDouble()) "${k.toLong()}k" else "%.1fk".format(k)
    }
    else -> {
        val m = pages / 1_000_000.0
        if (m == m.toLong().toDouble()) "${m.toLong()}M" else "%.1fM".format(m)
    }
}

private enum class HistoryWindow(val labelText: String) {
    DAYS_7("7 days"),
    DAYS_30("30 days"),
    MONTHS_12("12 months"),
}

@Composable
private fun HistorySection(stats: ReadingStats, onRefresh: () -> Unit) {
    // Default to 12 months — preserves the pre-v1.0.12 behavior on first
    // open. State is local-only (no persistence) so a screen leave + return
    // returns to the default; a deliberate choice to keep the surface
    // simple and avoid yet another preference.
    var window by remember { mutableStateOf(HistoryWindow.MONTHS_12) }

    val (title, bars) = when (window) {
        HistoryWindow.DAYS_7 -> "Last 7 days" to stats.dailyHistory7d.map { it.date to it.count }
        HistoryWindow.DAYS_30 -> "Last 30 days" to stats.dailyHistory30d.map { it.date to it.count }
        HistoryWindow.MONTHS_12 -> "Last 12 months" to stats.monthlyHistory.map { it.yearMonth to it.count }
    }

    SectionHeader(title = title, onRefresh = onRefresh)
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HistoryWindow.entries.forEach { option ->
            FilterChip(
                selected = window == option,
                onClick = { window = option },
                label = { Text(option.labelText) },
            )
        }
    }
    HistoryBarChart(bars = bars)
}

@Composable
private fun HistoryBarChart(bars: List<Pair<String, Int>>) {
    val maxCount = (bars.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
    val barColor = MaterialTheme.colorScheme.primary
    val axisColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                val n = bars.size
                if (n == 0) return@Canvas
                val gap = 6.dp.toPx()
                val barWidth = ((size.width - gap * (n - 1)) / n).coerceAtLeast(2f)
                val baselineY = size.height
                bars.forEachIndexed { index, (_, count) ->
                    val rel = count.toFloat() / maxCount.toFloat()
                    val h = (rel * size.height).coerceAtLeast(if (count == 0) 0f else 2f)
                    val x = index * (barWidth + gap)
                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(x, baselineY - h),
                        size = Size(barWidth, h),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                    )
                }
                drawLine(
                    color = axisColor,
                    start = Offset(0f, baselineY),
                    end = Offset(size.width, baselineY),
                    strokeWidth = 1f,
                )
            }
        }
        // Sparse x-axis labels: first / middle / last only, so daily charts
        // (30 bars) don't get a crammed strip of 30 labels.
        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            val labels = when (bars.size) {
                0 -> emptyList()
                in 1..2 -> bars.map { it.first }
                else -> listOf(
                    bars.first().first,
                    bars[bars.size / 2].first,
                    bars.last().first,
                )
            }
            labels.forEachIndexed { i, label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = when (i) {
                        0 -> TextAlign.Start
                        labels.lastIndex -> TextAlign.End
                        else -> TextAlign.Center
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun AchievementsRow(achievements: List<Achievement>) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp),
    ) {
        items(achievements.filter { it.earned }) { ach ->
            AchievementChip(ach)
        }
    }
}

@Composable
private fun AchievementChip(achievement: Achievement) {
    val container = MaterialTheme.colorScheme.tertiaryContainer
    val onContainer = MaterialTheme.colorScheme.onTertiaryContainer
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(container)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = achievement.title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = onContainer,
        )
        Text(
            text = achievement.description,
            style = MaterialTheme.typography.bodySmall,
            color = onContainer.copy(alpha = 0.8f),
        )
    }
}

@Composable
private fun RecentSeriesRow(entry: RecentSeriesEntry) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
        ),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(
                text = entry.seriesTitle,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            Text(
                text = "Last read · ${entry.lastReadAt}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
            text = "No reading activity yet",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = "Finish your first book to see stats appear here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        IconButton(onClick = onRefresh) {
            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
        }
    }
}

