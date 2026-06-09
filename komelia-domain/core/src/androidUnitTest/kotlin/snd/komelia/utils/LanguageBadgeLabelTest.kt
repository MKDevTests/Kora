package snd.komelia.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LanguageBadgeLabelTest {

    @Test
    fun french() {
        for (v in listOf("fr", "fra", "fre", "fr-FR", "French", "Français", "francais")) {
            assertEquals("FR", languageBadgeLabel(v), "expected FR for '$v'")
        }
    }

    @Test
    fun english() {
        for (v in listOf("en", "eng", "en-US", "English", "anglais")) {
            assertEquals("EN", languageBadgeLabel(v), "expected EN for '$v'")
        }
    }

    @Test
    fun unknownOrBlank() {
        assertNull(languageBadgeLabel(null))
        assertNull(languageBadgeLabel(""))
        assertNull(languageBadgeLabel("   "))
        assertNull(languageBadgeLabel("ja"))
        assertNull(languageBadgeLabel("japanese"))
        assertNull(languageBadgeLabel("de"))
    }
}
