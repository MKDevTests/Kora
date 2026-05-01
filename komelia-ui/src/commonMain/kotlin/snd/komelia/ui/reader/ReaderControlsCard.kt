package snd.komelia.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.HazeMaterials
import snd.komelia.ui.LocalHazeState
import snd.komelia.ui.LocalTheme

@Composable
fun ReaderControlsCard(
    modifier: Modifier = Modifier,
    isFullWidth: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val theme = LocalTheme.current
    val hazeState = LocalHazeState.current
    val hazeStyle = if (hazeState != null) {
        HazeMaterials.thin(theme.colorScheme.surface.copy(alpha = 0.4f))
    } else null

    val shape = if (isFullWidth) RoundedCornerShape(0.dp) else RoundedCornerShape(28.dp)
    val commonModifier = if (isFullWidth) {
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    } else {
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp)
            .navigationBarsPadding()
    }

    Box(modifier = commonModifier) {
        if (hazeState != null && hazeStyle != null) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .shadow(if (isFullWidth) 0.dp else 8.dp, shape)
                    .clip(shape)
                    .hazeEffect(hazeState) { style = hazeStyle }
            )
        } else {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .shadow(if (isFullWidth) 0.dp else 8.dp, shape)
                    .background(MaterialTheme.colorScheme.surface, shape)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (isFullWidth) 0.dp else 16.dp),
        ) {
            content()
        }
    }
}
