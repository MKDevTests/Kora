package snd.komelia.ui.common.immersive

import androidx.compose.ui.graphics.Color
import coil3.compose.AsyncImagePainter

expect suspend fun extractDominantColor(painter: AsyncImagePainter): Color?

/**
 * The cover's most vivid colour, for the page accent. The dominant one
 * (above) tints the background and is usually a near-grey: measured on a
 * pink-and-flames cover, Palette's dominant swatch was too desaturated to
 * pass as an accent, so the read button stayed on the theme colour.
 */
expect suspend fun extractVibrantColor(painter: AsyncImagePainter): Color?
