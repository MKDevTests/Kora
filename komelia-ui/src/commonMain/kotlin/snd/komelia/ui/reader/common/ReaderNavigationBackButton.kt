package snd.komelia.ui.reader.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.HazeMaterials
import snd.komelia.ui.LocalAccentColor
import snd.komelia.ui.LocalHazeState
import snd.komelia.ui.LocalTheme

@Composable
fun ReaderNavigationBackButton(
    isVisible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalTheme.current
    val hazeState = LocalHazeState.current
    val accentColor = LocalAccentColor.current ?: MaterialTheme.colorScheme.primary
    val hazeStyle = if (hazeState != null) {
        HazeMaterials.thin(theme.colorScheme.surface.copy(alpha = 0.4f))
    } else null

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier.size(56.dp),
            contentAlignment = Alignment.Center
        ) {
            // Background Layer: shadow modifier applied to this internal Box
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .shadow(8.dp, CircleShape)
                    .clip(CircleShape)
                    .then(
                        if (hazeState != null && hazeStyle != null)
                            Modifier.hazeEffect(hazeState) { style = hazeStyle }
                        else
                            Modifier.background(theme.colorScheme.surface)
                    )
            )

            // Content Layer: Icon and Ripple
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(),
                        onClick = onClick
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.History,
                    contentDescription = "Go back",
                    tint = accentColor,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}
