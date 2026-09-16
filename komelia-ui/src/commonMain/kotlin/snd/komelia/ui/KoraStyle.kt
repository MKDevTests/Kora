package snd.komelia.ui

import snd.komelia.settings.model.TitleFont
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Inter_Medium
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Inter_Regular
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Inter_SemiBold
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.NotoSerif_Bold
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import org.jetbrains.compose.resources.Font

/**
 * The three corner radii of the app. Every rounded surface picks one of
 * them: [small] for chips, badges and thin bars, [medium] for cards,
 * fields and buttons, [large] for panels, dialogs and the floating bar.
 * Before 1.8.21 there were twelve different values (2 to 28 dp) spread
 * over ninety-eight call sites; the eye reads that as sloppiness without
 * being able to name it. Plain objects, usable outside composition, and
 * fed to [MaterialTheme] through [koraShapes] so the Material defaults
 * agree with them.
 */
object KoraShapes {
    val small = RoundedCornerShape(8.dp)
    val medium = RoundedCornerShape(12.dp)
    val large = RoundedCornerShape(20.dp)
    val extraLarge = RoundedCornerShape(28.dp)
}

fun koraShapes() = Shapes(
    extraSmall = KoraShapes.small,
    small = KoraShapes.small,
    medium = KoraShapes.medium,
    large = KoraShapes.large,
    extraLarge = KoraShapes.extraLarge,
)

/**
 * Two families, each with one job: Noto Serif Bold for the screen and
 * item titles (display, headline, titleLarge), Inter for everything that
 * is read rather than glanced at. Until now the theme carried no
 * typography at all, so Roboto sat next to the serif titles the screens
 * added by hand; the screens keep those, they simply match now.
 */
@Composable
fun koraTypography(titleFont: TitleFont = TitleFont.SERIF): Typography {
    val serif = FontFamily(Font(Res.font.NotoSerif_Bold, FontWeight.Bold))
    val sans = FontFamily(
        Font(Res.font.Inter_Regular, FontWeight.Normal),
        Font(Res.font.Inter_Medium, FontWeight.Medium),
        Font(Res.font.Inter_SemiBold, FontWeight.SemiBold),
    )
    val base = Typography()
    // "serif" is the title role; Appearance decides which face fills it.
    fun TextStyle.serif() = when (titleFont) {
        TitleFont.SERIF -> copy(fontFamily = serif, fontWeight = FontWeight.Bold)
        TitleFont.SANS -> copy(fontFamily = sans, fontWeight = FontWeight.SemiBold)
        TitleFont.SYSTEM -> copy(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold)
    }
    fun TextStyle.sans(weight: FontWeight = FontWeight.Normal) = copy(fontFamily = sans, fontWeight = weight)
    return Typography(
        displayLarge = base.displayLarge.serif(),
        displayMedium = base.displayMedium.serif(),
        displaySmall = base.displaySmall.serif(),
        headlineLarge = base.headlineLarge.serif(),
        headlineMedium = base.headlineMedium.serif(),
        headlineSmall = base.headlineSmall.serif(),
        titleLarge = base.titleLarge.serif(),
        titleMedium = base.titleMedium.sans(FontWeight.SemiBold),
        titleSmall = base.titleSmall.sans(FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.sans(),
        bodyMedium = base.bodyMedium.sans(),
        bodySmall = base.bodySmall.sans(),
        labelLarge = base.labelLarge.sans(FontWeight.Medium),
        labelMedium = base.labelMedium.sans(FontWeight.Medium),
        labelSmall = base.labelSmall.sans(FontWeight.Medium).copy(letterSpacing = 0.3.sp),
    )
}
