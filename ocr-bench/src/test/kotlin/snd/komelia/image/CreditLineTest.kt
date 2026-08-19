package snd.komelia.image

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Every case here is a real line from the bench corpus, with its real geometry,
 * including all seven the keyword alone got wrong. The point of the test is that
 * list: the rule was built by reading those seven, so they are the ones that
 * silently come back if a condition is ever relaxed.
 */
class CreditLineTest {

    private fun line(index: Int, text: String, ratio: Float, top: Float = 100f) =
        CreditLine.Line(
            index = index,
            text = text,
            left = 0f,
            top = top,
            right = 20f * ratio,
            bottom = top + 20f,
        )

    @Test
    fun `the credits of a comic are recognised`() {
        val credits = listOf(
            "TRANSLATION: STEPHEN PAUL" to 9.8f,
            "LETTERING: SNIR AHARON" to 7.7f,
            "STORY BY YUKI SUENAGA" to 9.1f,
            "SCRIPT BY SCOTT BRYAN WILSON AND RICHARD K. MORGAN" to 20.4f,
            "COLORS BY PIPPA BOWLAND" to 8.9f,
            "EDITED BY JOSEPH RYBANDT" to 9.6f,
            "Artwork Copyright 2021 Dynamite Entertainment. All rights reserved." to 28.4f,
            "Translation: Christine Dashiell" to 18.8f,
        )
        credits.forEach { (text, ratio) ->
            assertTrue(CreditLine.isCreditLine(text, ratio), text)
        }
    }

    @Test
    fun `dialogue that happens to say story is not a credit`() {
        // Shape, not vocabulary: a balloon is lettered several lines deep.
        assertFalse(CreditLine.isCreditLine("STORY!", 2.5f))
        assertFalse(CreditLine.isCreditLine("STORY OR...", 5.2f))
        assertFalse(CreditLine.isCreditLine("THE STORY", 4.2f))
        assertFalse(CreditLine.isCreditLine("STORY", 2.6f))
    }

    @Test
    fun `a line addressed to the reader is not a credit`() {
        // Both are wide enough and both carry a keyword. What separates them is
        // that they are said to someone.
        assertFalse(CreditLine.isCreditLine("TRANSLATION NOTE!", 8.5f))
        assertFalse(CreditLine.isCreditLine("Support the Author!", 7.8f))
    }

    @Test
    fun `a numbered chapter is not a byline`() {
        assertFalse(CreditLine.isCreditLine("STORY 180: I WANTED TO MEASURE", 11.2f))
        // The same words without the number are one.
        assertTrue(CreditLine.isCreditLine("STORY BY YUKI SUENAGA", 11.2f))
    }

    @Test
    fun `credits are only looked for at the ends of the book`() {
        val credits = listOf(line(0, "TRANSLATION: STEPHEN PAUL", 9.8f))
        assertEquals(setOf(0), CreditLine.detect(credits, pageNumber = 1, pageCount = 200))
        assertEquals(setOf(0), CreditLine.detect(credits, pageNumber = 199, pageCount = 200))
        // Mid-book, the same words are a character reading something aloud.
        assertEquals(emptySet(), CreditLine.detect(credits, pageNumber = 100, pageCount = 200))
    }

    @Test
    fun `an unknown book length still protects the opening pages`() {
        val credits = listOf(line(0, "LETTERING: SNIR AHARON", 7.7f))
        assertEquals(setOf(0), CreditLine.detect(credits, pageNumber = 2, pageCount = 0))
        // Without a length there is no back matter to be in -- an unknown length
        // must not turn every page into the end of the book.
        assertEquals(emptySet(), CreditLine.detect(credits, pageNumber = 90, pageCount = 0))
    }

    @Test
    fun `the fragment of a cut credit band goes with it`() {
        // The recogniser read Blue Box's translator credit twice, and only the
        // piece without the keyword was mangled ("de laine Dashiell").
        val lines = listOf(
            line(0, "Translation: Christine Dashiell", 18.8f, top = 489f),
            line(1, "tine Dashiell", 7.8f, top = 492f),
        )
        assertEquals(setOf(0, 1), CreditLine.detect(lines, pageNumber = 5, pageCount = 190))
    }

    @Test
    fun `a balloon below the credits is not dragged in with them`() {
        val lines = listOf(
            line(0, "TRANSLATION: STEPHEN PAUL", 9.8f, top = 100f),
            line(1, "I never asked for this", 3.0f, top = 400f),
        )
        assertEquals(setOf(0), CreditLine.detect(lines, pageNumber = 1, pageCount = 190))
    }

    @Test
    fun `a page with no credit word is left alone entirely`() {
        val lines = listOf(
            line(0, "Neither of them", 3.0f),
            line(1, "Are letting up!", 3.2f),
        )
        assertEquals(emptySet(), CreditLine.detect(lines, pageNumber = 1, pageCount = 190))
    }
}
