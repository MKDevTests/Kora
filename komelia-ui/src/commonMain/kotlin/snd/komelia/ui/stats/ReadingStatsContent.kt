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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

        // --- Monthly chart -------------------------------------------------
        SectionHeader(title = "Last 12 months", onRefresh = onRefresh)
        MonthlyHistoryChart(buckets = stats.monthlyHistory)

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
private fun SectionHeader(title: String, onRefresh: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        if (onRefresh != null) {
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }
        }
    }
}

@Composable
private fun MonthlyHistoryChart(buckets: List<MonthBucket>) {
    val maxCount = (buckets.maxOfOrNull { it.count } ?: 0).coerceAtLeast(1)
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
                val n = buckets.size
                if (n == 0) return@Canvas
                val gap = 6.dp.toPx()
                val barWidth = ((size.width - gap * (n - 1)) / n).coerceAtLeast(2f)
                val baselineY = size.height
                buckets.forEachIndexed { index, bucket ->
                    val rel = bucket.count.toFloat() / maxCount.toFloat()
                    val h = (rel * size.height).coerceAtLeast(if (bucket.count == 0) 0f else 2f)
                    val x = index * (barWidth + gap)
                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(x, baselineY - h),
                        size = Size(barWidth, h),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                    )
                }
                // baseline
                drawLine(
                    color = axisColor,
                    start = Offset(0f, baselineY),
                    end = Offset(size.width, baselineY),
                    strokeWidth = 1f,
                )
            }
        }
        // Sparse x-axis labels: oldest, middle, latest
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        ) {
            val labels = when (buckets.size) {
                0 -> emptyList()
                in 1..2 -> buckets.map { it.yearMonth }
                else -> listOf(
                    buckets.first().yearMonth,
                    buckets[buckets.size / 2].yearMonth,
                    buckets.last().yearMonth,
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

