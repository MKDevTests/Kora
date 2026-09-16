package snd.komelia.ui.common.immersive

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import snd.komelia.ui.KoraShapes

/** A secondary action next to the read button: one icon, one job. */
class DetailAction(
    val icon: ImageVector,
    val contentDescription: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
)

/**
 * The read action as a wide button under the title, with the secondary
 * actions as square tonal buttons beside it. Before 1.8.21 the series
 * page offered six identical round FABs at the bottom (previous, random,
 * next, download, restart, continue) and nothing told which one was the
 * action; the floating bar now keeps the navigation trio and the continue
 * button, the rest lives here where the eye lands after the title.
 */
@Composable
fun DetailActionRow(
    label: String,
    onClick: () -> Unit,
    accentColor: Color?,
    modifier: Modifier = Modifier,
    detail: String? = null,
    enabled: Boolean = true,
    icon: ImageVector = Icons.AutoMirrored.Rounded.MenuBook,
    secondaryActions: List<DetailAction> = emptyList(),
) {
    val container = accentColor ?: MaterialTheme.colorScheme.primary
    val onContainer = if (container.luminance() > 0.5f) Color.Black else Color.White
    val tonal = MaterialTheme.colorScheme.surfaceContainer
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(52.dp)
                .clip(KoraShapes.medium)
                .background(if (enabled) container else tonal)
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val content = if (enabled) onContainer else MaterialTheme.colorScheme.onSurfaceVariant
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
            Text(
                label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (detail != null) {
                Text(
                    detail,
                    style = MaterialTheme.typography.labelMedium,
                    color = content.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        secondaryActions.forEach { action ->
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(KoraShapes.medium)
                    .background(tonal)
                    .clickable(enabled = action.enabled, onClick = action.onClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    action.icon,
                    contentDescription = action.contentDescription,
                    tint = if (action.enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
            }
        }
    }
}
