package snd.komelia.homefilters

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import snd.komga.client.book.KomgaReadStatus
import snd.komga.client.library.KomgaLibraryId
import snd.komga.client.search.KomgaSearchCondition
import snd.komga.client.search.allOfBooks
import snd.komga.client.search.allOfSeries

class ProgressDependentFiltersTest {

    /**
     * The regression this predicate exists for. "Keep reading" ships as a
     * custom filter, and a `false` here means it is never refreshed after
     * reading a book — the exact bug measured on 2026-08-29, where "On deck"
     * re-queried three times and "Keep reading", right above it, never did.
     */
    @Test
    fun defaultKeepReadingShelfDependsOnProgress() {
        val keepReading = homeScreenDefaultFilters.single { it.label == "Keep reading" }
        assertTrue(keepReading.dependsOnReadProgress(), "the default Keep reading shelf must refresh after reading")
    }

    @Test
    fun defaultShelvesSplitAsExpected() {
        val progress = homeScreenDefaultFilters.filter { it.dependsOnReadProgress() }.map { it.label }
        assertEquals(listOf("Keep reading", "On deck", "Recently read books"), progress)
    }

    @Test
    fun readStatusIsFoundAtAnyDepth() {
        val nested = booksShelf(
            allOfBooks {
                anyOf {
                    readStatus { isEqualTo(KomgaReadStatus.IN_PROGRESS) }
                }
            }.toBookCondition()
        )
        assertTrue(nested.dependsOnReadProgress())
    }

    @Test
    fun conditionsWithoutReadStatusDoNot() {
        val byLibrary = booksShelf(
            allOfBooks { library { isEqualTo(KomgaLibraryId("0P1J3X9HSBC7Y")) } }.toBookCondition()
        )
        assertFalse(byLibrary.dependsOnReadProgress())
        assertFalse(booksShelf(null).dependsOnReadProgress())
    }

    /** Series-side shelves go through their own branch of the walk. */
    @Test
    fun seriesCustomFiltersAreWalkedToo() {
        val inProgress = SeriesHomeScreenFilter.CustomFilter(
            order = 1,
            label = "Séries en cours",
            filter = allOfSeries { readStatus { isEqualTo(KomgaReadStatus.IN_PROGRESS) } }.toSeriesCondition(),
        )
        assertTrue(inProgress.dependsOnReadProgress())

        val byPublisher = SeriesHomeScreenFilter.CustomFilter(
            order = 2,
            label = "Glénat",
            filter = allOfSeries { publisher { isEqualTo("Glénat") } }.toSeriesCondition(),
        )
        assertFalse(byPublisher.dependsOnReadProgress())
        assertFalse(SeriesHomeScreenFilter.RecentlyAdded(order = 3, label = "Recent", pageSize = 20).dependsOnReadProgress())
    }

    private fun booksShelf(condition: KomgaSearchCondition.BookCondition?) =
        BooksHomeScreenFilter.CustomFilter(order = 1, label = "test", filter = condition)
}
