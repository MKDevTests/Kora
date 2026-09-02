package snd.komelia.duplicates

import snd.komelia.chapters.normalizeForMatch
import snd.komelia.similarity.SimilarityIndexTitle

/**
 * Finding the same series stored twice in one library.
 *
 * Everything here runs off the locally persisted similarity index (V85), so the
 * whole sweep costs about a tenth of a second on a full catalogue and issues
 * not one server request. That is the point: the server already struggles with
 * the genre and tag endpoints, and a duplicate hunt that re-listed every series
 * would be the most expensive screen in the app.
 *
 * Two hash passes, no fuzzy matching. A trigram-blocked Dice pass was measured
 * on the real catalogue (12 688 series): it took 86 seconds on a desktop to
 * find 53 pairs above 95, most of them sequels rather than duplicates, and it
 * missed the reordering cases entirely. Pass B below catches those exactly, for
 * free, and the rest of what the fuzzy pass returned was noise.
 */

/**
 * Words dropped before pass B compares titles.
 *
 * Only articles and conjunctions: they are what Komga's "Title (The)" sort form
 * moves around, and they carry no identity. Japanese particles (no/wa/ga) were
 * tried here too and changed nothing on the real catalogue, so they are left
 * alone — they are also real words in romanised titles.
 */
private val TITLE_STOP_WORDS = setOf(
    "the", "a", "an", "le", "la", "les", "l", "de", "des", "du", "d", "et", "and", "of",
)

/**
 * Above this many members, a group is almost certainly a shared generic title
 * rather than a duplicate.
 *
 * Measured, not guessed: on the real catalogue exactly three groups exceed it —
 * "Star Wars" x11, "Side Stories" x10 and "original" x4, all of them different
 * works filed under a collection name. Every group of three or fewer was a real
 * duplicate, including ones with no author or publisher in common. So group
 * size decides, and a title-length guard that also looked promising is not
 * used: it threw away "One Piece" and "Kagurabachi", whose second copy simply
 * has thin metadata.
 */
const val DUPLICATE_LIKELY_MAX_SIZE = 3

/** One series inside a group. Enough to draw a row without another read. */
data class DuplicateMember(
    val seriesId: String,
    val title: String,
)

/**
 * Series of one library that normalise to the same title.
 *
 * [likely] separates the two lists the screen shows. It is not a confidence
 * score: a group is either small enough to be trustworthy or large enough to be
 * a collection name, and there is nothing in between worth ranking.
 */
data class DuplicateGroup(
    val libraryId: String,
    val members: List<DuplicateMember>,
) {
    val likely: Boolean get() = members.size <= DUPLICATE_LIKELY_MAX_SIZE

    /** What the screen shows as the group's name. */
    val title: String get() = members.first().title
}

/**
 * Marks a series released chapter by chapter rather than in collected volumes.
 *
 * Such a series is never a duplicate of anything, even of another series with
 * the same marker: the pair is a chapter release and its volumes, or two
 * chapter feeds of the same work, and both belong to the chapter management
 * screen rather than here. Measured on the real catalogue, the rule removes
 * three groups and every one of them was a false positive.
 */
private const val CHAPTER_MARKER = "(chap)"

fun isChapterTitle(title: String): Boolean = title.contains(CHAPTER_MARKER, ignoreCase = true)

/**
 * Stable identity of an unordered pair, so the ignore list does not depend on
 * which of the two the sweep happened to visit first.
 */
fun duplicatePairKey(a: String, b: String): String =
    if (a <= b) "$a|$b" else "$b|$a"

/**
 * Groups of same-library series sharing a title.
 *
 * [ignoredPairs] holds [duplicatePairKey] values the user has dismissed. They
 * are removed before the groups are assembled rather than after: dismissing one
 * link inside a group of three has to be able to split it into a pair and a
 * loner, which filtering finished groups could not express.
 *
 * Results are sorted likely-first then by title, which is the order the screen
 * wants and costs nothing here.
 */
fun findDuplicateGroups(
    entries: List<SimilarityIndexTitle>,
    ignoredPairs: Set<String> = emptySet(),
    linkedPairs: Set<String> = emptySet(),
): List<DuplicateGroup> {
    if (entries.size < 2) return emptyList()

    val normalized = arrayOfNulls<String>(entries.size)
    // Pass A buckets on the normalised title, pass B on its words re-sorted
    // alphabetically. B is what catches "Attaque Des Titans (l')" against
    // "L'attaque des Titans": same words, moved article, nothing else in common
    // that a bucket could key on.
    val byTitle = HashMap<String, MutableList<Int>>()
    val bySortedWords = HashMap<String, MutableList<Int>>()

    entries.forEachIndexed { index, entry ->
        val norm = normalizeForMatch(entry.titleSort)
        if (norm.isEmpty() || isChapterTitle(entry.titleSort)) return@forEachIndexed
        normalized[index] = norm
        byTitle.getOrPut("${entry.libraryId}\u0000$norm") { mutableListOf() }.add(index)

        val words = norm.split(' ').filter { it.isNotEmpty() && it !in TITLE_STOP_WORDS }
        if (words.isEmpty()) return@forEachIndexed
        val key = words.sorted().joinToString(" ")
        bySortedWords.getOrPut("${entry.libraryId}\u0000$key") { mutableListOf() }.add(index)
    }

    val parent = IntArray(entries.size) { it }
    fun find(start: Int): Int {
        var node = start
        while (parent[node] != node) {
            parent[node] = parent[parent[node]]
            node = parent[node]
        }
        return node
    }

    var linked = false
    for (buckets in listOf(byTitle, bySortedWords)) {
        for (bucket in buckets.values) {
            if (bucket.size < 2) continue
            for (i in bucket.indices) {
                for (j in i + 1 until bucket.size) {
                    val left = entries[bucket[i]]
                    val right = entries[bucket[j]]
                    if (!canBeDuplicates(left, right, ignoredPairs, linkedPairs)) continue
                    val a = find(bucket[i])
                    val b = find(bucket[j])
                    if (a != b) {
                        parent[a] = b
                        linked = true
                    }
                }
            }
        }
    }
    if (!linked) return emptyList()

    // A series linked to nothing is its own root and lands in a component of
    // one, which the size filter below drops. No special case needed.
    val components = HashMap<Int, MutableList<Int>>()
    entries.indices.forEach { index ->
        if (normalized[index] == null) return@forEach
        components.getOrPut(find(index)) { mutableListOf() }.add(index)
    }

    return components.values
        .filter { it.size > 1 }
        .map { indices ->
            DuplicateGroup(
                libraryId = entries[indices.first()].libraryId,
                members = indices
                    .map { DuplicateMember(entries[it].seriesId, entries[it].titleSort) }
                    .sortedBy { it.title },
            )
        }
        .sortedWith(compareByDescending<DuplicateGroup> { it.likely }.thenBy { it.title })
}

/**
 * Whether two same-title series can be the same file stored twice.
 *
 * Three ways to say no, all of them things the app already knows and the title
 * alone cannot:
 *
 *  - the admin dismissed the pair;
 *  - the two are already linked — a Chapters/Volumes edge is the case the title
 *    cannot see, two series named "Chainsaw Man" where one is the chapter
 *    release, and a Language edge is the language rule written by hand;
 *  - their languages differ. A French edition and an English one are the same
 *    work, not the same file, and this catalogue holds several ("7th Garden ·
 *    FR · Delcourt" against "7th Garden · EN · VIZ Media").
 *
 * An unknown language never decides anything: rows written before V104 have
 * none, and reading that as a mismatch would split every real duplicate until
 * the index is rebuilt.
 */
private fun canBeDuplicates(
    left: SimilarityIndexTitle,
    right: SimilarityIndexTitle,
    ignoredPairs: Set<String>,
    linkedPairs: Set<String>,
): Boolean {
    val key = duplicatePairKey(left.seriesId, right.seriesId)
    if (key in ignoredPairs || key in linkedPairs) return false
    val a = left.language
    val b = right.language
    return a == null || b == null || a == b
}
