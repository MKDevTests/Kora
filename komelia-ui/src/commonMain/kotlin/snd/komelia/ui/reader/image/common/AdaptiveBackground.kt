package snd.komelia.ui.reader.image.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.IntSize
import snd.komelia.image.EdgeSampling

@Composable
fun AdaptiveBackground(
    edgeSampling: EdgeSampling?,
    modifier: Modifier = Modifier,
    imageBounds: Rect? = null,
    content: @Composable () -> Unit
) {
    val backgroundColor = MaterialTheme.colorScheme.background
    // The letterbox colour comes from the image's edge pixels, not from what
    // is drawn, so the page's night tint has to be applied here too — without
    // it a warm page sits inside a cold frame.
    val nightTint = LocalReaderNightModeIntensity.current
    fun sample(edge: snd.komelia.image.EdgeSample?): Color {
        val color = edge?.averageColor?.let { Color(it) } ?: return Color.Transparent
        return nightTint?.let { color.tintedForNightMode(it) } ?: color
    }
    val topColor = remember(edgeSampling, nightTint) { sample(edgeSampling?.top) }
    val bottomColor = remember(edgeSampling, nightTint) { sample(edgeSampling?.bottom) }
    val leftColor = remember(edgeSampling, nightTint) { sample(edgeSampling?.left) }
    val rightColor = remember(edgeSampling, nightTint) { sample(edgeSampling?.right) }

    val animatedTop by animateColorAsState(targetValue = topColor, animationSpec = tween(durationMillis = 500))
    val animatedBottom by animateColorAsState(targetValue = bottomColor, animationSpec = tween(durationMillis = 500))
    val animatedLeft by animateColorAsState(targetValue = leftColor, animationSpec = tween(durationMillis = 500))
    val animatedRight by animateColorAsState(targetValue = rightColor, animationSpec = tween(durationMillis = 500))

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                if (edgeSampling != null) {
                    val containerWidth = size.width
                    val containerHeight = size.height

                    val imageLeft = imageBounds?.left ?: 0f
                    val imageTop = imageBounds?.top ?: 0f
                    val imageRight = imageBounds?.right ?: containerWidth
                    val imageBottom = imageBounds?.bottom ?: containerHeight

                    // Top Zone
                    if (imageTop > 0) {
                        val path = Path().apply {
                            moveTo(0f, 0f)
                            lineTo(containerWidth, 0f)
                            lineTo(imageRight, imageTop)
                            lineTo(imageLeft, imageTop)
                            close()
                        }
                        drawPath(
                            path = path,
                            brush = Brush.verticalGradient(
                                0f to backgroundColor,
                                1f to animatedTop,
                                startY = 0f,
                                endY = imageTop
                            )
                        )
                    }

                    // Bottom Zone
                    if (imageBottom < containerHeight) {
                        val path = Path().apply {
                            moveTo(0f, containerHeight)
                            lineTo(containerWidth, containerHeight)
                            lineTo(imageRight, imageBottom)
                            lineTo(imageLeft, imageBottom)
                            close()
                        }
                        drawPath(
                            path = path,
                            brush = Brush.verticalGradient(
                                0f to animatedBottom,
                                1f to backgroundColor,
                                startY = imageBottom,
                                endY = containerHeight
                            )
                        )
                    }

                    // Left Zone
                    if (imageLeft > 0) {
                        val path = Path().apply {
                            moveTo(0f, 0f)
                            lineTo(imageLeft, imageTop)
                            lineTo(imageLeft, imageBottom)
                            lineTo(0f, containerHeight)
                            close()
                        }
                        drawPath(
                            path = path,
                            brush = Brush.horizontalGradient(
                                0f to backgroundColor,
                                1f to animatedLeft,
                                startX = 0f,
                                endX = imageLeft
                            )
                        )
                    }

                    // Right Zone
                    if (imageRight < containerWidth) {
                        val path = Path().apply {
                            moveTo(containerWidth, 0f)
                            lineTo(imageRight, imageTop)
                            lineTo(imageRight, imageBottom)
                            lineTo(containerWidth, containerHeight)
                            close()
                        }
                        drawPath(
                            path = path,
                            brush = Brush.horizontalGradient(
                                0f to animatedRight,
                                1f to backgroundColor,
                                startX = imageRight,
                                endX = containerWidth
                            )
                        )
                    }
                }
            }
    ) {
        content()
    }
}
