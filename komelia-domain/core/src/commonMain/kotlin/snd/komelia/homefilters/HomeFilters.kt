package snd.komelia.homefilters

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import snd.komga.client.book.KomgaReadStatus
import snd.komga.client.common.KomgaPageRequest
import snd.komga.client.common.KomgaSort
import snd.komga.client.search.KomgaSearchCondition
import snd.komga.client.search.allOfBooks
import kotlin.time.Duration.Companion.days

val homeScreenDefaultFilters = listOf(
    BooksHomeScreenFilter.CustomFilter(
        order = 1,
        label = "Keep reading",
        filter = allOfBooks { readStatus { isEqualTo(KomgaReadStatus.IN_PROGRESS) } }.toBookCondition(),
        pageRequest = KomgaPageRequest(sort = KomgaSort.KomgaBooksSort.byReadDateDesc())
    ),
    BooksHomeScreenFilter.OnDeck(
        order = 2,
        label = "On deck",
        pageSize = 20,
    ),
    BooksHomeScreenFilter.CustomFilter(
        order = 3,
        label = "Recently released books",
        filter = allOfBooks { releaseDate { isInLast(30.days) } }.toBookCondition(),
        pageRequest = KomgaPageRequest(
            sort = KomgaSort.KomgaBooksSort.byReleaseDateDesc(),
        )
    ),
    BooksHomeScreenFilter.CustomFilter(
        order = 4,
        label = "Recently added books",
        filter = allOfBooks {}.toBookCondition(),
        pageRequest = KomgaPageRequest(
            sort = KomgaSort.KomgaBooksSort.byCreatedDateDesc(),
            size = 20
        )
    ),
    SeriesHomeScreenFilter.RecentlyAdded(
        order = 5,
        label = "Recently added series",
        pageSize = 20,
    ),
    SeriesHomeScreenFilter.RecentlyUpdated(
        order = 6,
        label = "Recently updated series",
        pageSize = 20,
    ),
    BooksHomeScreenFilter.CustomFilter(
        order = 7,
        label = "Recently read books",
        filter = allOfBooks {
            readStatus { isEqualTo(KomgaReadStatus.READ) }
        }.toBookCondition(),
        pageRequest = KomgaPageRequest(sort = KomgaSort.KomgaBooksSort.byReadDateDesc())
    ),
).sortedBy { it.order }

@Serializable
sealed interface HomeScreenFilter {
    val order: Int
    val label: String

    fun withOrder(newOrder: Int): HomeScreenFilter
}

@Serializable
sealed interface SeriesHomeScreenFilter : HomeScreenFilter {
    override fun withOrder(newOrder: Int): SeriesHomeScreenFilter

    @Serializable
    @SerialName("io.github.snd_r.komelia.ui.home.SeriesHomeScreenFilter.RecentlyAdded")
    data class RecentlyAdded(
        override val order: Int,
        override val label: String,
        val pageSize: Int,
    ) : SeriesHomeScreenFilter {
        override fun withOrder(newOrder: Int) = this.copy(order = newOrder)
    }

    @Serializable
    @SerialName("io.github.snd_r.komelia.ui.home.SeriesHomeScreenFilter.RecentlyUpdated")
    data class RecentlyUpdated(
        override val order: Int,
        override val label: String,
        val pageSize: Int,
    ) : SeriesHomeScreenFilter {
        override fun withOrder(newOrder: Int) = this.copy(order = newOrder)
    }

    @Serializable
    @SerialName("io.github.snd_r.komelia.ui.home.SeriesHomeScreenFilter.CustomFilter")
    data class CustomFilter(
        override val order: Int,
        override val label: String,
        val filter: KomgaSearchCondition.SeriesCondition? = null,
        val textSearch: String? = null,
        val pageRequest: KomgaPageRequest? = null,
    ) : SeriesHomeScreenFilter {
        override fun withOrder(newOrder: Int) = this.copy(order = newOrder)
    }

    /**
     * Series the user is close to finishing — at least
     * [progressThresholdPercent]% of the books are READ.
     *
     * Komga's API does not expose a server-side filter on the
     * "books-read / total" ratio. The resolver in HomeViewModel
     * fetches a wider pool of IN_PROGRESS series (page size scaled
     * up) and filters client-side. Volume stays manageable because
     * IN_PROGRESS << lifetime catalog.
     */
    @Serializable
    @SerialName("io.github.snd_r.komelia.ui.home.SeriesHomeScreenFilter.AlmostFinished")
    data class AlmostFinished(
        override val order: Int,
        override val label: String,
        val pageSize: Int,
        /** Percentage of books read above which a series qualifies. 80 = 80%. */
        val progressThresholdPercent: Int = 80,
        /**
         * Library IDs (as raw strings — KomgaLibraryId isn't @Serializable
         * itself, we wrap at resolve time) whose series should be hidden
         * from this shelf. Empty = include everything. Use to exclude
         * "Divers" / dumping-ground libraries that would noise the shelf.
         */
        val excludedLibraryIds: List<String> = emptyList(),
    ) : SeriesHomeScreenFilter {
        override fun withOrder(newOrder: Int) = this.copy(order = newOrder)
    }
}

@Serializable
sealed interface BooksHomeScreenFilter : HomeScreenFilter {
    override fun withOrder(newOrder: Int): BooksHomeScreenFilter

    @Serializable
    @SerialName("io.github.snd_r.komelia.ui.home.BooksHomeScreenFilter.OnDeck")
    data class OnDeck(
        override val order: Int,
        override val label: String,
        val pageSize: Int,
    ) : BooksHomeScreenFilter {
        override fun withOrder(newOrder: Int) = this.copy(order = newOrder)
    }

    @Serializable
    @SerialName("io.github.snd_r.komelia.ui.home.BooksHomeScreenFilter.CustomFilter")
    data class CustomFilter(
        override val order: Int,
        override val label: String,
        val filter: KomgaSearchCondition.BookCondition? = null,
        val textSearch: String? = null,
        val pageRequest: KomgaPageRequest? = null,
    ) : BooksHomeScreenFilter {
        override fun withOrder(newOrder: Int) = this.copy(order = newOrder)
    }

    /**
     * Books the user started but hasn't touched in a while — the
     * mirror image of "Keep reading". Same query (IN_PROGRESS) but
     * sorted by read date ASCENDING so the most stale items come
     * first. Surfaces book-shaped reminders without needing a
     * notification.
     *
     * Built-in (rather than asking users to clone Keep-reading and
     * flip the sort) because it's a recurring ask and a one-tap
     * "Add" experience is much friendlier than walking through the
     * generic CustomFilter editor.
     */
    @Serializable
    @SerialName("io.github.snd_r.komelia.ui.home.BooksHomeScreenFilter.ForgottenBooks")
    data class ForgottenBooks(
        override val order: Int,
        override val label: String,
        val pageSize: Int,
        /**
         * Library IDs (as raw strings) whose books should be hidden
         * from this shelf. Empty = include everything. Use to exclude
         * "Divers" / dumping-ground libraries.
         */
        val excludedLibraryIds: List<String> = emptyList(),
    ) : BooksHomeScreenFilter {
        override fun withOrder(newOrder: Int) = this.copy(order = newOrder)
    }
}
