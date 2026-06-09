package snd.komelia.utils

/**
 * Remove parenthetical groups "(…)" from a name while KEEPING the rest of the
 * title. Previously this cut everything from the first "(" to the end, which
 * truncated titles like "Attaque Des Titans (L') - Birth Of Livaï" down to
 * "Attaque Des Titans". Now only the "(…)" groups are dropped:
 *   "Attaque Des Titans (L') - Birth Of Livaï" -> "Attaque Des Titans - Birth Of Livaï"
 *   "Naruto (2002)"                            -> "Naruto"
 * Falls back to the original (trimmed) when removal would leave it blank.
 */
fun String.removeParentheses(): String {
    val cleaned = replace(Regex("""\([^)]*\)"""), "")
        .replace(Regex("""\s{2,}"""), " ")
        .trim()
    return cleaned.ifBlank { trim() }
}
