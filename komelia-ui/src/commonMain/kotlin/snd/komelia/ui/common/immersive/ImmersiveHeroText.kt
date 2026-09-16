package snd.komelia.ui.common.immersive

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp as lerpDp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.lerp as lerpTextUnit

@Composable
fun ImmersiveHeroText(
    seriesTitle: String,
    authorYear: String,
    chapterTitle: String? = null,
    expandFraction: Float,
    accentColor: Color? = null,
    onSeriesClick: (() -> Unit)? = null,
    horizontalPadding: Dp = 16.dp,
    modifier: Modifier = Modifier,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val resolvedAccentColor = accentColor ?: MaterialTheme.colorScheme.primary

    val isCollapsed = expandFraction < 0.5f
    val isLightText = onSurface.luminance() > 0.5f
    val shadowColor = if (isLightText) Color.Black.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.7f)
    val shadow = if (isCollapsed) Shadow(
        color = shadowColor,
        blurRadius = 8f
    ) else Shadow.None

    val titleColor = lerpColor(onSurface, onSurface, expandFraction)
    val authorColor = lerpColor(resolvedAccentColor, onSurfaceVariant, expandFraction)
    val chapterColor = lerpColor(onSurface, onSurfaceVariant, expandFraction)

    var titleMultiplier by remember(seriesTitle) { mutableFloatStateOf(1f) }
    var chapterMultiplier by remember(chapterTitle) { mutableFloatStateOf(1f) }

    Column(
        modifier = modifier.padding(horizontal = horizontalPadding),
        verticalArrangement = Arrangement.Bottom
    ) {
        // Author & Year
        Text(
            text = authorYear.uppercase(),
            color = authorColor,
            style = MaterialTheme.typography.labelMedium.copy(
                letterSpacing = 2.sp,
                shadow = shadow,
                fontSize = lerpTextUnit(11.sp, 12.sp, expandFraction)
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // Series Title
        Text(
            text = seriesTitle,
            color = titleColor,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                shadow = shadow,
                fontSize = lerpTextUnit(32.sp * titleMultiplier, 20.sp * titleMultiplier, expandFraction),
                lineHeight = lerpTextUnit(36.sp * titleMultiplier, 24.sp * titleMultiplier, expandFraction)
            ),
            maxLines = if (isCollapsed) 2 else 3,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { textLayoutResult ->
                if (textLayoutResult.hasVisualOverflow) {
                    titleMultiplier *= 0.9f
                }
            },
            modifier = Modifier
                .padding(vertical = lerpDp(4.dp, 0.dp, expandFraction))
                .then(if (onSeriesClick != null) Modifier.clickable(onClick = onSeriesClick) else Modifier)
        )

        // Chapter Name (Optional)
        if (chapterTitle != null) {
            Text(
                text = chapterTitle,
                color = chapterColor,
                style = MaterialTheme.typography.titleSmall.copy(
                    shadow = shadow,
                    fontSize = lerpTextUnit(15.sp * chapterMultiplier, 13.sp * chapterMultiplier, expandFraction)
                ),
                maxLines = if (isCollapsed) 1 else 2,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { textLayoutResult ->
                    if (textLayoutResult.hasVisualOverflow) {
                        chapterMultiplier *= 0.9f
                    }
                }
            )
        }
    }
}
