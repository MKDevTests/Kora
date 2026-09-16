package snd.komelia.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import snd.komelia.settings.model.AppTheme

enum class Theme(
    val colorScheme: ColorScheme,
    val type: ThemeType,
    val transparentBars: Boolean = false,
) {
    DARK(
        darkColorScheme(
            primary = Color.White,
            onPrimary = Color.Black,
            primaryContainer = Color(red = 212, green = 212, blue = 212),
            onPrimaryContainer = Color(red = 62, green = 64, blue = 64),

            secondary = Color(red = 87, green = 131, blue = 212),
            onSecondary = Color.White,
            secondaryContainer = Color(red = 50, green = 70, blue = 120),
            onSecondaryContainer = Color(red = 230, green = 230, blue = 230),

            tertiary = Color(red = 249, green = 168, blue = 37),
            onTertiary = Color.White,
            tertiaryContainer = Color(red = 181, green = 130, blue = 49),
            onTertiaryContainer = Color.White,

            background = Color(red = 113, green = 116, blue = 118),
            onBackground = Color(red = 202, green = 196, blue = 208),

            surface = Color(red = 15, green = 15, blue = 15),
            onSurface = Color(red = 237, green = 235, blue = 235),

            surfaceVariant = Color(red = 43, green = 43, blue = 43),
            surfaceContainerLow = Color(red = 43, green = 43, blue = 43),
            surfaceContainerHighest = Color(red = 43, green = 43, blue = 43),
            onSurfaceVariant = Color(red = 202, green = 196, blue = 208),

            surfaceDim = Color(red = 32, green = 31, blue = 35),
            surfaceBright = Color(red = 113, green = 116, blue = 118),

            error = Color(red = 240, green = 70, blue = 60),
            onError = Color.White,
            errorContainer = Color(red = 140, green = 29, blue = 24),
            onErrorContainer = Color.White
        ),
        ThemeType.DARK
    ),
    LIGHT(
        lightColorScheme(
            primary = Color.Black,
            onPrimary = Color.White,
            primaryContainer = Color(red = 212, green = 212, blue = 212),
            onPrimaryContainer = Color(red = 62, green = 64, blue = 64),

            secondary = Color(red = 87, green = 131, blue = 212),
            onSecondary = Color.White,
            secondaryContainer = Color(red = 70, green = 100, blue = 160),
            onSecondaryContainer = Color.White,

            tertiary = Color(red = 232, green = 156, blue = 35),
            onTertiary = Color.White,
            tertiaryContainer = Color(red = 181, green = 130, blue = 49),
            onTertiaryContainer = Color.White,

            background = Color.White, // Original: Color(red = 254, green = 247, blue = 255)
            onBackground = Color(red = 29, green = 27, blue = 32),

            surface = Color.White, // Original: Color(red = 254, green = 247, blue = 255)
            onSurface = Color(red = 29, green = 27, blue = 32),

            surfaceVariant = Color(red = 240, green = 240, blue = 240), // Original: Color(red = 231, green = 224, blue = 236)
            surfaceContainerHighest = Color(red = 235, green = 235, blue = 235), // Original: Color(red = 230, green = 224, blue = 233)
            onSurfaceVariant = Color(red = 73, green = 69, blue = 79),

            surfaceDim = Color(red = 225, green = 225, blue = 225), // Original: Color(red = 222, green = 216, blue = 225)
            surfaceBright = Color(red = 180, green = 180, blue = 180),

            error = Color(red = 240, green = 70, blue = 60),
            onError = Color.White,
            errorContainer = Color(red = 195, green = 65, blue = 60),
            onErrorContainer = Color.White
        ),
        ThemeType.LIGHT
    ),

    LIGHT_MODERN(
        lightColorScheme(
            primary = Color(0xFF3B82F6.toInt()),
            onPrimary = Color(0xFFEFF6FF.toInt()),
            primaryContainer = Color(0xFF93C5FD.toInt()),
            onPrimaryContainer = Color(0xFF0B2A60.toInt()),

            secondary = Color(0xFF5C5B5B.toInt()),
            onSecondary = Color(0xFFF5F2F1.toInt()),
            secondaryContainer = Color(0xFFE5E2E1.toInt()),
            onSecondaryContainer = Color(0xFF525151.toInt()),

            tertiary = Color(0xFF3B82F6.toInt()),
            onTertiary = Color(0xFFEFF6FF.toInt()),
            tertiaryContainer = Color(0xFF93C5FD.toInt()),
            onTertiaryContainer = Color(0xFF0B2A60.toInt()),

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

            inversePrimary = Color(0xFF60A5FA.toInt()),
            inverseSurface = Color(0xFF0E0E0C.toInt()),
            inverseOnSurface = Color(0xFF9E9D99.toInt()),
        ),
        ThemeType.LIGHT,
        transparentBars = true
    ),

    DARK_MODERN(
        darkColorScheme(
            primary = Color(0xFF60A5FA.toInt()),
            onPrimary = Color(0xFF0B2A60.toInt()),
            primaryContainer = Color(0xFF3B82F6.toInt()),
            onPrimaryContainer = Color(0xFFDBEAFE.toInt()),

            // One accent. Secondary and tertiary used to be two cyans
            // (#7DD3FC, #22D3EE) next to the blue primary: sliders, toggles
            // and badges each had their own shade of the same idea.
            secondary = Color(0xFF93C5FD.toInt()),
            onSecondary = Color(0xFF0B2A60.toInt()),
            secondaryContainer = Color(0xFF1E3A8A.toInt()),
            onSecondaryContainer = Color(0xFFDBEAFE.toInt()),

            tertiary = Color(0xFF60A5FA.toInt()),
            onTertiary = Color(0xFF0B2A60.toInt()),
            tertiaryContainer = Color(0xFF3B82F6.toInt()),
            onTertiaryContainer = Color(0xFFDBEAFE.toInt()),

            background = Color(0xFF0E0E0E.toInt()),
            onBackground = Color(0xFFFFFFFF.toInt()),

            surface = Color(0xFF0E0E0E.toInt()),
            onSurface = Color(0xFFFFFFFF.toInt()),

            surfaceVariant = Color(0xFF262626.toInt()),
            onSurfaceVariant = Color(0xFFADAAAA.toInt()),

            surfaceContainerLowest = Color(0xFF000000.toInt()),
            surfaceContainerLow = Color(0xFF131313.toInt()),
            surfaceContainer = Color(0xFF1A1A1A.toInt()),
            surfaceContainerHigh = Color(0xFF20201F.toInt()),
            surfaceContainerHighest = Color(0xFF262626.toInt()),

            surfaceDim = Color(0xFF0E0E0E.toInt()),
            surfaceBright = Color(0xFF2C2C2C.toInt()),

            // Quiet outlines: on #0E0E0E every mid-grey stroke reads as
            // noise. Fields and dividers stay findable, chips lose theirs
            // (see KoraChipDefaults).
            outline = Color(0xFF3A3A39.toInt()),
            outlineVariant = Color(0xFF262626.toInt()),

            error = Color(0xFFFF6E84.toInt()),
            onError = Color(0xFF490013.toInt()),
            errorContainer = Color(0xFFA70138.toInt()),
            onErrorContainer = Color(0xFFFFB2B9.toInt()),

            inversePrimary = Color(0xFF2563EB.toInt()),
            inverseSurface = Color(0xFFFCF9F8.toInt()),
            inverseOnSurface = Color(0xFF565555.toInt()),
        ),
        ThemeType.DARK,
        transparentBars = true
    ),

    DARKER(
        darkColorScheme(
            primary = Color.White,
            onPrimary = Color.Black,
            primaryContainer = Color.Black,
            onPrimaryContainer = Color.White,

            secondary = Color(red = 75, green = 125, blue = 205),
            onSecondary = Color.White,
            secondaryContainer = Color(red = 50, green = 70, blue = 120),
            onSecondaryContainer = Color(red = 230, green = 230, blue = 230),

            tertiary = Color(red = 193, green = 127, blue = 31),
            onTertiary = Color.White,
            tertiaryContainer = Color(red = 115, green = 84, blue = 10),
            onTertiaryContainer = Color.White,

            background = Color.Black,
            onBackground = Color.White,

            surface = Color.Black,
            onSurface = Color.White,

            surfaceVariant = Color(red = 30, green = 30, blue = 30),
            surfaceContainerHighest = Color(red = 30, green = 30, blue = 30),
            onSurfaceVariant = Color.White,

            surfaceDim = Color(red = 25, green = 25, blue = 25),
            surfaceBright = Color(red = 65, green = 65, blue = 65),

            error = Color(red = 240, green = 70, blue = 60),
            onError = Color.White,
            errorContainer = Color(red = 140, green = 29, blue = 24),
            onErrorContainer = Color.White
        ),
        ThemeType.DARK
    );

    // Semi-transparent surface color used for bars in Modern themes.
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
        fun AppTheme.toTheme() = valueOf(this.name)
        fun Theme.toAppTheme() = AppTheme.valueOf(this.name)
    }
}
