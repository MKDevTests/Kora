package snd.komelia.ui.stats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import snd.komelia.stats.ReadingStats
import snd.komelia.ui.LocalMainScreenViewModel
import snd.komelia.ui.LocalViewModelFactory
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import snd.komelia.ui.LocalStrings
import snd.komelia.ui.strings.UiStrings
import snd.komelia.ui.pushUnique

/**
 * Compact reading stats summary, embedded near the top of the Home screen.
 * Renders nothing when the user has disabled the stats feature, and
 * silently degrades to nothing when there is no completion data yet (so
 * fresh users don't see an empty box).
 *
 * Tapping the card pushes the full [ReadingStatsScreen].
 */
@Composable
fun HomeStatsCard(homeReady: Boolean = true) {
    val mainVm = LocalMainScreenViewModel.current
    // The Home card only checks the master switch — even when the user
    // hasn't opted into the bottom-nav shortcut, the Home card is a
    // discoverable surface so the feature isn't orphaned.
    val masterEnabled by mainVm.statsMasterEnabled.collectAsState()
    if (!masterEnabled) return

    val factory = LocalViewModelFactory.current
    // The card runs the stats compute directly rather than going through
    // a Voyager ScreenModel — `rememberScreenModel` is only legal inside
    // Screen.Content(), and this composable is nested under HomeContent.
    // `remember` keeps the service instance stable across recompositions.
    val service = remember { factory.createReadingStatsService() }
    // Paint whatever was computed earlier in the session, immediately.
    var stats by remember { mutableStateOf(ReadingStatsCache.peek()) }
    // Then, and only then, consider recomputing. Two gates, both measured:
    //
    //  - homeReady: the card waits for the shelves. Its three API calls used to
    //    race them for the same server, and its ten SQL steps ran across the
    //    2.66 s in which the home screen paints. Nothing here is urgent enough
    //    to be in that window.
    //  - the cache: at most one compute per session (see ReadingStatsCache).
    //
    // Recomposition-safe: LaunchedEffect keys on homeReady, so this runs once
    // when the shelves land, not on every frame.
    LaunchedEffect(homeReady) {
        if (!homeReady) return@LaunchedEffect
        if (ReadingStatsCache.isFresh()) return@LaunchedEffect
        runCatching { service.compute() }.getOrNull()?.let {
            ReadingStatsCache.put(it)
            stats = it
        }
    }

    val navigator = LocalNavigator.currentOrThrow

    // Hide the card until the first compute returns. Once we have data,
    // hide it again only when the dataset is genuinely empty (fresh
    // install, no completions yet) so we don't pester users with an
    // empty box.
    val current = stats ?: return
    if (current.isEmpty) return

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable { navigator.pushUnique(ReadingStatsScreen()) },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.BarChart,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = LocalStrings.current.ui.yourReading,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = buildSummaryLine(
                        strings = LocalStrings.current.ui,
                        booksThisMonth = current.booksFinishedLast30Days,
                        streak = current.streakDays,
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = LocalStrings.current.ui.openStats,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun buildSummaryLine(strings: UiStrings, booksThisMonth: Int, streak: Int): String {
    val books = when (booksThisMonth) {
        0 -> strings.noBookFinishedThisMonth
        1 -> strings.oneBookThisMonth
        else -> strings.booksThisMonth(booksThisMonth)
    }
    val streakSuffix = when {
        streak <= 0 -> ""
        streak == 1 -> " · " + strings.oneDayStreak
        else -> " · " + strings.dayStreak(streak)
    }
    return books + streakSuffix
}
