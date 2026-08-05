package snd.komelia.ui.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.intl.Locale
import snd.komelia.ui.LocalStrings
import snd.komelia.ui.strings.EnStrings
import snd.komelia.ui.strings.FrStrings

/**
 * Language of the interface, as chosen by the user.
 *
 * [SYSTEM] is the default and the only behaviour that existed before: the
 * device decides. The explicit values exist because the device often can't —
 * a French reader running an English phone had no way to ask for French.
 */
enum class AppLanguage(val tag: String) {
    SYSTEM(""),
    ENGLISH("en"),
    FRENCH("fr");

    companion object {
        fun of(tag: String): AppLanguage = entries.firstOrNull { it.tag == tag } ?: SYSTEM
    }
}

/**
 * Applies [language] to every string the UI reads.
 *
 * The app translates through its own catalogue ([snd.komelia.ui.strings.AppStrings])
 * rather than through `strings.xml` resources. Compose resources pick their
 * locale from the SYSTEM, and the API that would override it
 * (`LocalComposeEnvironment`) is internal to the Compose plugin — a user-chosen
 * language would mean changing the process locale and recreating the activity.
 * The catalogue is a plain composition local: the switch is instant, works the
 * same on every target, and a missing translation is a compile error instead of
 * an English word left in a French screen.
 */
@Composable
fun ProvideAppLanguage(language: AppLanguage, content: @Composable () -> Unit) {
    val french = when (language) {
        AppLanguage.FRENCH -> true
        AppLanguage.ENGLISH -> false
        AppLanguage.SYSTEM -> Locale.current.language.startsWith("fr")
    }
    CompositionLocalProvider(
        LocalStrings provides if (french) FrStrings else EnStrings,
        content = content,
    )
}
