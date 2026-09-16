package snd.komelia.ui.common.immersive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import snd.komelia.ui.LocalAccentFollowsCover
import snd.komelia.ui.LocalTheme
import snd.komelia.ui.Theme
import snd.komelia.ui.asCoverAccent

/**
 * The accent a detail page should use: the cover's dominant hue lifted
 * onto the theme ramp when Appearance asks for it and the cover has a
 * hue worth following, else whatever the caller had (the theme's).
 */
@Composable
fun rememberCoverAccent(dominantColor: Color?, fallback: Color?): Color? {
    val follows = LocalAccentFollowsCover.current
    val dark = LocalTheme.current.type == Theme.ThemeType.DARK
    return remember(dominantColor, fallback, follows, dark) {
        if (follows && dominantColor != null) dominantColor.asCoverAccent(dark) ?: fallback else fallback
    }
}
