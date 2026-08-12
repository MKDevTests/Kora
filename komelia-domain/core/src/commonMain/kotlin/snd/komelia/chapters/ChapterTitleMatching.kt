package snd.komelia.chapters

/**
 * Scoring a chapter series' title against the volumes it might belong to.
 *
 * Exact equality is not enough in practice: the two entries are often typed at
 * different times and drift by an accent, a colon, a "Vol." or a stray space.
 * So the comparison is normalised first, and what is left over is scored rather
 * than rejected.
 */

/** At or above this, and with no rival, a match is applied without asking. */
const val CHAPTER_MATCH_AUTO_SCORE = 90

/** Below this a candidate is not worth showing at all. */
const val CHAPTER_MATCH_FLOOR_SCORE = 55

private const val ACCENTED = "àáâãäåçèéêëìíîïñòóôõöùúûüýÿœæ"
private const val UNACCENTED = "aaaaaaceeeeiiiinooooouuuuyyoa"

/**
 * The comparable form of a title: lower case, unaccented, stripped of anything
 * that is not a letter, a digit or a single space.
 *
 * Accents are folded through a table rather than a Unicode normaliser — there
 * is none in Kotlin common code, and the Latin range below is what series
 * titles in this library actually use.
 */
fun normalizeForMatch(title: String): String {
    val builder = StringBuilder(title.length)
    var lastWasSpace = false
    for (raw in title.lowercase()) {
        val index = ACCENTED.indexOf(raw)
        val ch = if (index >= 0) UNACCENTED[index] else raw
        when {
            ch.isLetterOrDigit() -> {
                builder.append(ch)
                lastWasSpace = false
            }

            !lastWasSpace && builder.isNotEmpty() -> {
                builder.append(' ')
                lastWasSpace = true
            }
        }
    }
    return builder.toString().trim()
}

/**
 * How alike two titles are, 0 to 100.
 *
 * Equal once normalised scores 100. Otherwise it is the Dice coefficient over
 * character bigrams: it rewards shared runs of letters, so "Vinland Saga" and
 * "Vinland Saga Vol." stay high while "Gantz" and "Gantz G" — genuinely
 * different works — do not reach the automatic threshold.
 */
fun titleMatchScore(a: String, b: String): Int {
    val left = normalizeForMatch(a)
    val right = normalizeForMatch(b)
    if (left.isEmpty() || right.isEmpty()) return 0
    if (left == right) return 100

    val leftGrams = bigrams(left)
    val rightGrams = bigrams(right)
    if (leftGrams.isEmpty() || rightGrams.isEmpty()) return 0

    // Multiset intersection: a bigram repeated twice on both sides counts twice,
    // which keeps repeated words from inflating the score.
    val counts = HashMap<String, Int>()
    leftGrams.forEach { counts[it] = (counts[it] ?: 0) + 1 }
    var shared = 0
    rightGrams.forEach { gram ->
        val remaining = counts[gram] ?: 0
        if (remaining > 0) {
            counts[gram] = remaining - 1
            shared++
        }
    }
    return (2 * shared * 100) / (leftGrams.size + rightGrams.size)
}

private fun bigrams(value: String): List<String> =
    if (value.length < 2) emptyList()
    else (0 until value.length - 1).map { value.substring(it, it + 2) }

/** The title with its "(Chap)" marker removed, which is what we search for. */
fun strippedChapterTitle(title: String): String =
    title.trimEnd().removeSuffix(CHAPTER_TITLE_SUFFIX).trim()
