package snd.komelia.ui.settings.navigation

import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import snd.komelia.ui.KoraShapes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import snd.komelia.ui.LocalPlatform
import snd.komelia.ui.LocalCompactUi
import snd.komelia.ui.LocalTheme
import snd.komelia.ui.Theme
import snd.komelia.ui.platform.PlatformType.DESKTOP
import snd.komelia.ui.platform.PlatformType.MOBILE
import snd.komelia.ui.platform.PlatformType.WEB_KOMF
import snd.komelia.ui.platform.cursorForHand

@Composable
fun SettingsGroup(
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    // One step above the page, not a hard-coded #2B2B2B: the group reads as
    // a panel, the rows inside no longer need dividers to be told apart.
    val containerColor = MaterialTheme.colorScheme.surfaceContainer

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
            )
        }
        Surface(
            shape = KoraShapes.medium,
            color = containerColor,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(content = content)
        }
    }
}

@Composable
fun SettingsListItem(
    label: String,
    onClick: () -> Unit,
    isSelected: Boolean,
    icon: ImageVector? = null,
    trailingContent: @Composable (() -> Unit)? = null,
) {
    val height = when (LocalPlatform.current) {
        MOBILE -> if (LocalCompactUi.current) 44.dp else 56.dp
        DESKTOP, WEB_KOMF -> 48.dp
    }

    ListItem(
        headlineContent = {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingContent = icon?.let {
            {
                Icon(
                    it,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        trailingContent = trailingContent,
        colors = ListItemDefaults.colors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
            headlineColor = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .cursorForHand()
            .clickable(enabled = !isSelected, onClick = onClick)
    )
}

@Composable
fun ErrorIndicator() {
    val color = MaterialTheme.colorScheme.error
    Canvas(modifier = Modifier.size(10.dp)) {
        drawCircle(color = color)
    }
}
