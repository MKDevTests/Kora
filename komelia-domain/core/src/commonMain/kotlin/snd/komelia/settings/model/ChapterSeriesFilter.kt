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
    HIDE_CHAPTERS,

    /** Keep only chapter series — how you check a release against its volumes. */
    ONLY_CHAPTERS;

    /** Cycles ANY → hide → only → ANY, for the tri-state checkbox. */
    fun next(): ChapterSeriesFilter = when (this) {
        ANY -> HIDE_CHAPTERS
        HIDE_CHAPTERS -> ONLY_CHAPTERS
        ONLY_CHAPTERS -> ANY
    }
}
