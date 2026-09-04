package snd.komelia.ui.common.immersive

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Publisher names below are real, taken from a 12694-series Komga library, with
 * the series count that made each case worth a rule.
 */
class PublisherLogoMatchTest {

    private val index = setOf(
        "glenat", "gl_nat", "image", "kodansha", "ablaze", "pika_dition",
        "manga_up", "seven_seas_entertainment", "delcourt", "casterman",
        "ubisoft", "viz_media", "boom", "futuropolis", "akileos_jpg",
        "yen_press", "panini_comics", "panini_espa_a", "asuka_comics",
    )

    private fun key(publisher: String) = resolvePublisherLogoKey(publisher, index)

    @Test
    fun exactNameStillWins() {
        assertEquals("glenat", key("Glenat"))
        assertEquals("image", key("Image"))
    }

    @Test
    fun accentsAreTransliterated() {
        // "Glénat" normalises to gl_nat; both spellings ship, but the deaccented
        // form is what saves publishers whose accent-eaten key was never bundled.
        assertEquals("gl_nat", key("Glénat"))
    }

    @Test
    fun splitsOnImprintSeparators() {
        assertEquals("image", key("Image - Top Cow"))          // 8 series
        assertEquals("image", key("Image; 1st edition"))       // 3 editions merged
        assertEquals("glenat", key("Glenat / Fayard"))
        assertEquals("delcourt", key("Delcourt/Tonkam"))       // 7 series
        assertEquals("viz_media", key("VIZ Media: SHONEN JUMP"))
    }

    @Test
    fun dropsLegalSuffix() {
        assertEquals("ablaze", key("Ablaze, LLC."))
        assertEquals("ubisoft", key("Ubisoft Entertainment S.A."))
    }

    @Test
    fun dropsTrailingDescriptor() {
        assertEquals("kodansha", key("Kodansha Comics"))       // 9 series
        assertEquals("kodansha", key("Kodansha USA"))
    }

    @Test
    fun fallsBackToAUniqueExtension() {
        assertEquals("pika_dition", key("Pika"))               // 30 series
        assertEquals("manga_up", key("Manga UP! Global"))      // 135 series
        assertEquals("seven_seas_entertainment", key("Seven Seas"))
        assertEquals("akileos_jpg", key("Akileos"))
    }

    @Test
    fun readsAJsonArrayOfImprints() {
        assertEquals("viz_media", key("""["MANGA Plus", "VIZ Media"]"""))
    }

    @Test
    fun anAmbiguousPrefixIsNotGuessed() {
        // Two files extend "panini"; picking one at random would show the wrong
        // country's logo, so nothing is shown.
        assertNull(key("Panini"))
    }

    @Test
    fun unknownPublisherStaysUnmatched() {
        assertNull(key("Ki-oon"))
        assertNull(key("Urban Comics"))
        assertNull(key(""))
    }
}
