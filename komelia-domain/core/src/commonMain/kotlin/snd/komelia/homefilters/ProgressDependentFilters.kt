package snd.komelia.homefilters

import snd.komga.client.search.KomgaSearchCondition
import snd.komga.client.search.KomgaSearchCondition.AllOfBook
import snd.komga.client.search.KomgaSearchCondition.AllOfSeries
import snd.komga.client.search.KomgaSearchCondition.AnyOfBook
import snd.komga.client.search.KomgaSearchCondition.AnyOfSeries
import snd.komga.client.search.KomgaSearchCondition.ReadStatus

/**
 * Whether this shelf shows something different once read progress moves.
 *
 * Home has two ways to refresh: re-query everything, or re-query only the
 * shelves that read progress can change. Reading a book takes the second one —
 * a page turn must not cost eleven server round-trips — so this predicate is
 * what decides whether a shelf is updated at all after reading.
 *
 * It used to answer for the three built-in progress shelves only, and `false`
 * for every [BooksHomeScreenFilter.CustomFilter]. That silently excluded the
 * default "Keep reading" shelf, which IS a custom filter (see
 * [homeScreenDefaultFilters]) — so on the user's own tablet, finishing a book
 * refreshed "On deck" three times over and never touched "Keep reading",
 * sitting right above it. Only a full reload — cold start, pull-to-refresh, or
 * a library scan — ever brought it up to date.
 *
 * The reason given for the exclusion was that finding a read-status condition
 * inside an arbitrary search tree is fragile. It is not: the tree is three
 * shapes deep ([AllOfBook] / [AnyOfBook] and their series twins hold children,
 * everything else is a leaf), and an unknown node answers `false` rather than
 * guessing.
 *
 * Deliberately NOT here: a shelf whose condition mentions read status only
 * through something the server evaluates independently of the current user.
 * There is no such condition today — [ReadStatus] is the only per-user one.
 */
fun HomeScreenFilter.dependsOnReadProgress(): Boolean = when (this) {
    is BooksHomeScreenFilter.OnDeck,
    is BooksHomeScreenFilter.ForgottenBooks,
    is SeriesHomeScreenFilter.AlmostFinished -> true

    is BooksHomeScreenFilter.CustomFilter -> filter?.readsProgress() == true
    is SeriesHomeScreenFilter.CustomFilter -> filter?.readsProgress() == true

    is SeriesHomeScreenFilter.RecentlyAdded,
    is SeriesHomeScreenFilter.RecentlyUpdated,
    is SeriesHomeScreenFilter.Favorites,
    is SeriesHomeScreenFilter.ForYou -> false
}

/**
 * True when [ReadStatus] appears anywhere in the condition tree.
 *
 * `else -> false` covers both the leaves that have nothing to do with progress
 * and any node a future komga-client adds: a shelf that refreshes one reload
 * later is a far smaller failure than one that crashes on an unknown node.
 */
private fun KomgaSearchCondition.BookCondition.readsProgress(): Boolean = when (this) {
    is ReadStatus -> true
    is AllOfBook -> conditions.any { it.readsProgress() }
    is AnyOfBook -> conditions.any { it.readsProgress() }
    else -> false
}

private fun KomgaSearchCondition.SeriesCondition.readsProgress(): Boolean = when (this) {
    is ReadStatus -> true
    is AllOfSeries -> conditions.any { it.readsProgress() }
    is AnyOfSeries -> conditions.any { it.readsProgress() }
    else -> false
}
