package snd.komelia.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import snd.komelia.settings.model.AppTheme

/**
 * The app theme is built, not picked from a list. Before 1.8.22 this was an
 * enum of five hand-written colour schemes (Dark, Light, Darker, Light
 * modern, Dark modern) next to a fourteen-entry accent menu that only
 * reached the chips and tabs. Now one seed colour drives the whole
 * Material scheme — primary, containers, secondary, inverse — over a fixed
 * set of neutral surfaces, and every `accentColor ?: colorScheme.primary`
 * in the screens falls back to it. Dark or light comes from [AppTheme]
 * (which may follow the system), pure black is a switch on top.
 */
class Theme(
    val colorScheme: ColorScheme,
    val type: ThemeType,
    val transparentBars: Boolean = true,
) {
    // Semi-transparent surface color used for bars.
    // 80% opacity for the top app bar, 60% for the bottom nav bar.
    val topBarContainerColor: Color
        get() = if (transparentBars) colorScheme.surface.copy(alpha = 0.8f) else colorScheme.surfaceVariant
    val navBarContainerColor: Color
        get() = if (transparentBars) colorScheme.surface.copy(alpha = 0.6f) else colorScheme.surfaceVariant

    enum class ThemeType {
        LIGHT,
        DARK
    }

    companion object {
        /** The blue the modern themes shipped with: "Encre" in the palette row. */
        val DEFAULT_SEED = Color(0xFF60A5FA.toInt())

        /** Seeds offered in Appearance, keyed for UiStrings.paletteName; null in settings means [DEFAULT_SEED]. */
        val PALETTES: List<Pair<String, Color>> = listOf(
            "ink" to DEFAULT_SEED,
            "forest" to Color(0xFF5FB07A.toInt()),
            "ember" to Color(0xFFF0885A.toInt()),
            "ocean" to Color(0xFF4FC3D9.toInt()),
            "lavender" to Color(0xFFA78BFA.toInt()),
            "sand" to Color(0xFFD4B36A.toInt()),
        )

        /** Fallback outside the themed tree (error view, log window, local default). */
        val DARK: Theme by lazy { build(dark = true, seed = null, pureBlack = false) }
        val LIGHT: Theme by lazy { build(dark = false, seed = null, pureBlack = false) }

        fun AppTheme.isDark(systemDark: Boolean): Boolean = when (this) {
            AppTheme.DARK, AppTheme.DARKER, AppTheme.DARK_MODERN -> true
            AppTheme.LIGHT, AppTheme.LIGHT_MODERN -> false
            AppTheme.SYSTEM -> systemDark
        }

        /** The primary a seed becomes; also what the palette swatches show. */
        fun primaryOf(seed: Color, dark: Boolean): Color = seed.withLightness(if (dark) 0.68f else 0.60f)

        fun build(dark: Boolean, seed: Color?, pureBlack: Boolean): Theme {
            val s = seed ?: DEFAULT_SEED
            return if (dark) Theme(darkScheme(s, pureBlack), ThemeType.DARK)
            else Theme(lightScheme(s), ThemeType.LIGHT)
        }

        // The ramp is a set of lightness stops on the seed's hue and
        // saturation. The stops are those of the shipped blue scheme
        // (#60A5FA -> 0.68, #3B82F6 -> 0.60, #93C5FD -> 0.78, #1E3A8A -> 0.33,
        // #0B2A60 -> 0.21, #DBEAFE -> 0.93), so seed = DEFAULT_SEED gives the
        // colours the 1.8.21 screens were tuned on, and another seed gives
        // the same contrasts in another hue.
        private fun darkScheme(seed: Color, pureBlack: Boolean): ColorScheme {
            val primary = primaryOf(seed, dark = true)
            val onPrimary = seed.withLightness(0.21f, saturationScale = 0.8f)
            val container = seed.withLightness(0.60f)
            val onContainer = seed.withLightness(0.93f)
            return darkColorScheme(
                primary = primary,
                onPrimary = onPrimary,
                primaryContainer = container,
                onPrimaryContainer = onContainer,

                secondary = seed.withLightness(0.78f),
                onSecondary = onPrimary,
                secondaryContainer = seed.withLightness(0.33f, saturationScale = 0.7f),
                onSecondaryContainer = onContainer,

                tertiary = primary,
                onTertiary = onPrimary,
                tertiaryContainer = container,
                onTertiaryContainer = onContainer,

                background = if (pureBlack) Color.Black else Color(0xFF0E0E0E.toInt()),
                onBackground = Color(0xFFFFFFFF.toInt()),

                surface = if (pureBlack) Color.Black else Color(0xFF0E0E0E.toInt()),
                onSurface = Color(0xFFFFFFFF.toInt()),

                surfaceVariant = if (pureBlack) Color(0xFF1F1F1F.toInt()) else Color(0xFF262626.toInt()),
                onSurfaceVariant = Color(0xFFADAAAA.toInt()),

                surfaceContainerLowest = Color(0xFF000000.toInt()),
                surfaceContainerLow = if (pureBlack) Color(0xFF0A0A0A.toInt()) else Color(0xFF131313.toInt()),
                surfaceContainer = if (pureBlack) Color(0xFF121212.toInt()) else Color(0xFF1A1A1A.toInt()),
                surfaceContainerHigh = if (pureBlack) Color(0xFF181818.toInt()) else Color(0xFF20201F.toInt()),
                surfaceContainerHighest = if (pureBlack) Color(0xFF1F1F1F.toInt()) else Color(0xFF262626.toInt()),

                surfaceDim = if (pureBlack) Color.Black else Color(0xFF0E0E0E.toInt()),
                surfaceBright = if (pureBlack) Color(0xFF262626.toInt()) else Color(0xFF2C2C2C.toInt()),

                // Quiet outlines: on #0E0E0E every mid-grey stroke reads as
                // noise. Fields and dividers stay findable, chips lose theirs
                // (see KoraChipDefaults).
                outline = if (pureBlack) Color(0xFF2E2E2E.toInt()) else Color(0xFF3A3A39.toInt()),
                outlineVariant = if (pureBlack) Color(0xFF1F1F1F.toInt()) else Color(0xFF262626.toInt()),

                error = Color(0xFFFF6E84.toInt()),
                onError = Color(0xFF490013.toInt()),
                errorContainer = Color(0xFFA70138.toInt()),
                onErrorContainer = Color(0xFFFFB2B9.toInt()),

                inversePrimary = seed.withLightness(0.53f),
                inverseSurface = Color(0xFFFCF9F8.toInt()),
                inverseOnSurface = Color(0xFF565555.toInt()),
            )
        }

        private fun lightScheme(seed: Color): ColorScheme {
            val primary = primaryOf(seed, dark = false)
            val onPrimary = seed.withLightness(0.97f)
            val container = seed.withLightness(0.78f)
            val onContainer = seed.withLightness(0.21f, saturationScale = 0.8f)
            return lightColorScheme(
                primary = primary,
                onPrimary = onPrimary,
                primaryContainer = container,
                onPrimaryContainer = onContainer,

                // Light used to keep a neutral secondary; a selected chip on
                // secondaryContainer then read grey on grey.
                secondary = seed.withLightness(0.40f),
                onSecondary = seed.withLightness(0.97f),
                secondaryContainer = seed.withLightness(0.86f),
                onSecondaryContainer = onContainer,

                tertiary = primary,
                onTertiary = onPrimary,
                tertiaryContainer = container,
                onTertiaryContainer = onContainer,

                background = Color(0xFFF8F6F1.toInt()),
                onBackground = Color(0xFF2E2F2C.toInt()),

                surface = Color(0xFFF8F6F1.toInt()),
                onSurface = Color(0xFF2E2F2C.toInt()),

                surfaceVariant = Color(0xFFDEDDD7.toInt()),
                onSurfaceVariant = Color(0xFF5C5C58.toInt()),

                surfaceContainerLowest = Color(0xFFFFFFFF.toInt()),
                surfaceContainerLow = Color(0xFFF2F1EB.toInt()),
                surfaceContainer = Color(0xFFEAE8E3.toInt()),
                surfaceContainerHigh = Color(0xFFE4E2DD.toInt()),
                surfaceContainerHighest = Color(0xFFDEDDD7.toInt()),

                surfaceDim = Color(0xFFD5D5CE.toInt()),
                surfaceBright = Color(0xFFF8F6F1.toInt()),

                outline = Color(0xFFC9C8C3.toInt()),
                outlineVariant = Color(0xFFDEDDD7.toInt()),

                error = Color(0xFFB41340.toInt()),
                onError = Color(0xFFFFEFEF.toInt()),
                errorContainer = Color(0xFFF74B6D.toInt()),
                onErrorContainer = Color(0xFF510017.toInt()),

                inversePrimary = seed.withLightness(0.68f),
                inverseSurface = Color(0xFF0E0E0C.toInt()),
                inverseOnSurface = Color(0xFF9E9D99.toInt()),
            )
        }
    }
}

/** Hue (0..360), saturation and lightness (0..1) of an opaque colour. */
fun Color.toHsl(): FloatArray {
    val r = red
    val g = green
    val b = blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val l = (max + min) / 2f
    if (max == min) return floatArrayOf(0f, 0f, l)
    val d = max - min
    val s = if (l > 0.5f) d / (2f - max - min) else d / (max + min)
    val h = when (max) {
        r -> ((g - b) / d + (if (g < b) 6f else 0f))
        g -> ((b - r) / d + 2f)
        else -> ((r - g) / d + 4f)
    } * 60f
    return floatArrayOf(h, s, l)
}

fun hslColor(h: Float, s: Float, l: Float): Color {
    val c = (1f - kotlin.math.abs(2f * l - 1f)) * s
    val hh = ((h % 360f) + 360f) % 360f / 60f
    val x = c * (1f - kotlin.math.abs(hh % 2f - 1f))
    val (r1, g1, b1) = when {
        hh < 1f -> Triple(c, x, 0f)
        hh < 2f -> Triple(x, c, 0f)
        hh < 3f -> Triple(0f, c, x)
        hh < 4f -> Triple(0f, x, c)
        hh < 5f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    val m = l - c / 2f
    return Color(
        (r1 + m).coerceIn(0f, 1f),
        (g1 + m).coerceIn(0f, 1f),
        (b1 + m).coerceIn(0f, 1f),
    )
}

/** Same hue, same (optionally scaled) saturation, given lightness. */
fun Color.withLightness(lightness: Float, saturationScale: Float = 1f): Color {
    val (h, s, _) = toHsl()
    return hslColor(h, (s * saturationScale).coerceIn(0f, 1f), lightness)
}

/**
 * A cover's dominant colour is rarely usable as an accent as-is: dark
 * covers give a near-black, washed scans a grey. Lift it onto the seed
 * ramp (same lightness as a dark-scheme primary, saturation floored) so
 * the read button stays a button.
 */
fun Color.asCoverAccent(dark: Boolean): Color? {
    val (h, s, _) = toHsl()
    // A black-and-white scan has a hue made of noise: keep the theme's accent.
    if (s < 0.15f) return null
    val sat = s.coerceIn(0.45f, 0.9f)
    return hslColor(h, sat, if (dark) 0.68f else 0.55f)
}
