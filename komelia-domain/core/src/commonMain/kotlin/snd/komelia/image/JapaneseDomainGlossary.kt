package snd.komelia.image

/**
 * Rewrites the handful of genre words the ja-en model reads wrong, into
 * ordinary Japanese it reads right, before the sentence reaches the engine.
 *
 * Distinct from [JapaneseKatakanaGlossary] on purpose, and not merged into it.
 * That table normalises a letterer's EMPHASIS -- サカナ back to 魚 -- and it
 * matches on katakana runs, so 勇者 and 流石 are invisible to it. This one is
 * about vocabulary the model has simply not learned, in whichever script the
 * word is written. Two different phenomena, two tables.
 *
 * ## Every entry is measured in context, not in isolation
 *
 * A term translated on its own is not the same term inside a sentence:
 * パーティー alone comes back "partie", and in a sentence "fête". So each
 * candidate was applied to up to 8 real bubbles from the corpus and both
 * versions were translated through the shipped pivot.
 *
 *     勇者    → 英雄     27 occurrences   4 gains, 0 losses
 *     パーティー → チーム    24               7 gains, 0 losses
 *     流石    → さすが     4               3 gains, 0 losses
 *
 * 勇者 is a title, the Hero, and "brave" is an adjective: その昔勇者が魔王を
 * 倒して came back "les courageux ont vaincu le roi démon" and becomes "Il
 * était une fois, un héros a vaincu le roi démon".
 *
 * パーティー is the adventuring group. チーム beats 仲間 (7/3, which loses the
 * group entirely in half its sentences) and 一行, which is rejected outright --
 * it also means a line of text, and produced "d'autres lignes dans cette ville"
 * and "que tu me mets en une seule ligne".
 *
 * 流石 is the least obvious and the cleanest: the kanji are ateji, the word IS
 * さすが. Written in kanji the model reads the characters and answers "Ryuseki",
 * "une pierre de dérive", "C'est un rocher".
 *
 * ## What was measured and refused
 *
 * ハーレム → 後宮 lost outright: 成り上がるハーレム戦記 went from "La guerre de
 * Harlem" to "Gogusenki1". 亜人 → 獣人 is a demi-human rewritten as a
 * beast-person, which is a different creature, and it did not even win.
 *
 * ラスボス → 魔王 measured 3 gains and 0 losses and is still not here. The
 * output it replaces is genuinely broken ("le patron rass", "le deuxième patron
 * de la Russie"), but a last boss is not always the demon king -- one of those
 * three lines is about the second game in a series. A rewrite that fixes the
 * French by changing what the sentence says is not a fix, and a table of three
 * entries is not worth a wrong one.
 */
object JapaneseDomainGlossary {

    /**
     * Longest first, so a term that contains another cannot be half-rewritten
     * by the shorter one. Nothing here overlaps today; the ordering is what
     * keeps that true when the table grows.
     */
    private val rewrites: List<Pair<String, String>> = listOf(
        "パーティー" to "チーム",
        "勇者" to "英雄",
        "流石" to "さすが",
    ).sortedByDescending { it.first.length }

    fun apply(text: String): String {
        if (text.isEmpty()) return text
        var out = text
        for ((from, to) in rewrites) {
            if (out.contains(from)) out = out.replace(from, to)
        }
        return out
    }
}
