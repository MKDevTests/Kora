package snd.komelia.settings.model

/**
 * What to do with series whose title ends with "(Chap)" — a work released
 * chapter by chapter rather than in collected volumes.
 *
 * One setting for the whole app rather than one per library: the home shelves
 * and search span every library at once and would have had none to read.
 */
enum class ChapterSeriesFilter {
    /** Leave every series alone. Default. */
    ANY,

    /** Drop chapter series from every list. */
    HIDE_CHAPTERS;

    fun toggled(): ChapterSeriesFilter = if (this == ANY) HIDE_CHAPTERS else ANY

    companion object {
        /**
         * Tolerant of values this build no longer knows. V95 briefly shipped an
         * ONLY_CHAPTERS mode to a debug install; it filtered page by page, so it
         * showed a handful of series per page and mostly empty ones, and it was
         * dropped. Reading it back must not take the app down at startup.
         */
        fun parse(stored: String): ChapterSeriesFilter =
            entries.firstOrNull { it.name == stored } ?: ANY
    }
}
