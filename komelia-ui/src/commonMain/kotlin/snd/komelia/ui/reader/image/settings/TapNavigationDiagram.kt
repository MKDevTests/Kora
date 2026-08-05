package snd.komelia.ui.reader.image.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import snd.komelia.settings.model.ReaderTapNavigationMode
import snd.komelia.settings.model.ReaderTapNavigationMode.*
import snd.komelia.ui.LocalStrings

@Composable
fun TapNavigationDiagram(
    mode: ReaderTapNavigationMode,
    modifier: Modifier = Modifier
) {
    val outlineColor = MaterialTheme.colorScheme.outline
    val textColor = MaterialTheme.colorScheme.onSurface
    val textMeasurer = rememberTextMeasurer()
    val textStyle = TextStyle(fontSize = 9.sp, color = textColor)
    // Read before the Canvas: a draw scope is not a composition.
    val menuLabel = LocalStrings.current.ui.menu

    Box(modifier = modifier.size(100.dp).padding(8.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize().aspectRatio(0.7f)) {
            val strokeWidth = 2.dp.toPx()
            val cornerRadius = 4.dp.toPx()
            
            // Screen border
            drawRoundRect(
                color = outlineColor,
                size = size,
                cornerRadius = CornerRadius(cornerRadius, cornerRadius),
                style = Stroke(width = strokeWidth)
            )

            val thirdWidth = size.width / 3
            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)

            // Vertical dividers
            drawLine(
                color = outlineColor,
                start = Offset(thirdWidth, 0f),
                end = Offset(thirdWidth, size.height),
                strokeWidth = strokeWidth / 2,
                pathEffect = dashEffect
            )
            drawLine(
                color = outlineColor,
                start = Offset(thirdWidth * 2, 0f),
                end = Offset(thirdWidth * 2, size.height),
                strokeWidth = strokeWidth / 2,
                pathEffect = dashEffect
            )

            // Middle zone (Menu)
            drawTextInZone(
                text = menuLabel,
                center = Offset(size.width / 2, size.height / 2),
                textMeasurer = textMeasurer,
                textStyle = textStyle
            )

            when (mode) {
                LEFT_RIGHT -> {
                    drawTextInZone("Prev", Offset(thirdWidth / 2, size.height / 2), textMeasurer, textStyle)
                    drawTextInZone("Next", Offset(thirdWidth * 2.5f, size.height / 2), textMeasurer, textStyle)
                }
                RIGHT_LEFT -> {
                    drawTextInZone("Next", Offset(thirdWidth / 2, size.height / 2), textMeasurer, textStyle)
                    drawTextInZone("Prev", Offset(thirdWidth * 2.5f, size.height / 2), textMeasurer, textStyle)
                }
                HORIZONTAL_SPLIT -> {
                    drawSplitZones(outlineColor, thirdWidth, size, dashEffect, strokeWidth, "Prev", "Next", textMeasurer, textStyle)
                }
                REVERSED_HORIZONTAL_SPLIT -> {
                    drawSplitZones(outlineColor, thirdWidth, size, dashEffect, strokeWidth, "Next", "Prev", textMeasurer, textStyle)
                }
            }
        }
    }
}

private fun DrawScope.drawSplitZones(
    color: Color,
    thirdWidth: Float,
    size: Size,
    pathEffect: PathEffect,
    strokeWidth: Float,
    topText: String,
    bottomText: String,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    textStyle: TextStyle
) {
    // Horizontal dividers in side columns
    drawLine(
        color = color,
        start = Offset(0f, size.height / 2),
        end = Offset(thirdWidth, size.height / 2),
        strokeWidth = strokeWidth / 2,
        pathEffect = pathEffect
    )
    drawLine(
        color = color,
        start = Offset(thirdWidth * 2, size.height / 2),
        end = Offset(size.width, size.height / 2),
        strokeWidth = strokeWidth / 2,
        pathEffect = pathEffect
    )

    // Indicators
    drawTextInZone(topText, Offset(thirdWidth / 2, size.height * 0.25f), textMeasurer, textStyle)
    drawTextInZone(bottomText, Offset(thirdWidth / 2, size.height * 0.75f), textMeasurer, textStyle)
    drawTextInZone(topText, Offset(thirdWidth * 2.5f, size.height * 0.25f), textMeasurer, textStyle)
    drawTextInZone(bottomText, Offset(thirdWidth * 2.5f, size.height * 0.75f), textMeasurer, textStyle)
}

private fun DrawScope.drawTextInZone(
    text: String,
    center: Offset,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    textStyle: TextStyle
) {
    val result = textMeasurer.measure(AnnotatedString(text), textStyle)
    drawText(
        textLayoutResult = result,
        topLeft = Offset(center.x - result.size.width / 2, center.y - result.size.height / 2)
    )
}
