package snd.komelia.ui.reader.image.common

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix

/**
 * Strength of the reader's warm tint for the page currently being drawn, or
 * null when night mode is off.
 *
 * A composition local rather than a parameter: [ReaderImageContent] is called
 * from seven places across the paged, continuous and panels readers, and every
 * one of them would otherwise have to carry a value it does nothing with.
 */
val LocalReaderNightModeIntensity = staticCompositionLocalOf<Float?> { null }

/**
 * Multipliers for a blue-light filter, from untouched at 0f to roughly candle
 * light at 1f.
 *
 * Red is left alone and the other two channels are scaled down. Scaling is the
 * point: an amber layer painted *over* the page would add light to the blacks
 * and flatten the contrast of the artwork, while multiplying leaves black at
 * black and only warms what was bright.
 */
fun nightModeColorMatrix(intensity: Float): ColorMatrix {
    val t = intensity.coerceIn(0f, 1f)
    val green = 1f - 0.28f * t
    val blue = 1f - 0.55f * t
    return ColorMatrix(
        floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, green, 0f, 0f, 0f,
            0f, 0f, blue, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
    )
}

/** The same tint as a [ColorFilter], for drawing a page. */
fun nightModeColorFilter(intensity: Float): ColorFilter =
    ColorFilter.colorMatrix(nightModeColorMatrix(intensity))

/**
 * The same tint applied to a single colour.
 *
 * [AdaptiveBackground] derives the letterbox colour from the image's edge
 * pixels rather than from what ends up on screen, so a filter on the page
 * would leave a warm page ringed by a cold background. This keeps the two
 * together.
 */
fun Color.tintedForNightMode(intensity: Float): Color {
    val t = intensity.coerceIn(0f, 1f)
    return copy(green = green * (1f - 0.28f * t), blue = blue * (1f - 0.55f * t))
}
