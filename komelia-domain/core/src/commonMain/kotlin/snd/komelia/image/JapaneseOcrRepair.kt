package snd.komelia.image

/**
 * Repairs what the recogniser got wrong about Japanese characters, before any
 * table or model reads the sentence: a kanji it replaced with a katakana drawn
 * the same way, and a katakana whose dakuten or handakuten it lost.
 *
 * The Japanese counterpart of [OcrSpellRepair], and it exists for the same
 * reason: a misread word cannot be translated into the right one. 二丁 is the
 * name of a character in the volume this was measured on, and the recogniser
 * returned ニ丁 — katakana ni, which is two strokes in the same place as the
 * kanji for two. The translator then had a word it had never seen and answered
 * "Nicho", "deux cho", or nothing at all, on eight different pages.
 *
 * ## Why the table is two entries and not nine
 *
 * The obvious pairs are ロ/口, カ/力, タ/夕, ハ/八, エ/工, ト/卜 and ス/又. All
 * seven were tried over 722 recognised lines from two volumes. Between them
 * they produced no correct repair and one wrong one: 3カ月 ("three months",
 * where カ is a counter and is meant to be katakana) came out 3力月. So they are
 * not here. ニ→二 fired eight times, all eight correct, and ー→一 once.
 *
 * ## The two rules that survived
 *
 * A katakana next to another katakana belongs to a katakana word and is left
 * alone — that is what keeps ミニ, アニキ and ニュース intact. What is left is a
 * lone katakana with a kanji beside it, which is a kanji context.
 *
 * ー needs one more condition. It is the long-vowel mark far more often than it
 * is the numeral, and it lengthens a hiragana kana as happily as a katakana one:
 * じゃー became じゃ一. What separates the numeral is what follows, because 一
 * before a counter is a number and counters are a closed class.
 *
 * ## The marks
 *
 * The dakuten is two small strokes and the handakuten a small ring, and both sit
 * where a screentone or a small font can swallow them. On the 7-volume corpus
 * the recogniser gets パーティー — the adventuring party, the most common noun in
 * the genre — wrong more often than right: ハーティー 14 times, パーティー 11.
 * The damage lands two stages later and does not look like an OCR problem at
 * all; アレンのハーティー came back "Allen Est Copieux" and
 * ユイのハーティーで支援職をしている as "J'ai les trente heures du moi".
 *
 * Measured on the 16 bubbles the word list changes: 15 clear gains, no
 * regression. The remaining one is a title already too garbled to judge.
 *
 * What this does NOT fix, and it is the larger half: パーティー spelled correctly
 * still translates as "fête". That is a glossary question, not a recognition
 * one, and it is measured separately.
 */
object JapaneseOcrRepair {

    private val homoglyphs = mapOf('ニ' to '二', 'ー' to '一')

    /**
     * Katakana whose only difference from another is the dakuten or handakuten
     * -- the two strokes and the small ring the recogniser is most likely to
     * miss on a screentone or a small font.
     */
    private val UNMARKED = mapOf(
        'ガ' to 'カ', 'ギ' to 'キ', 'グ' to 'ク', 'ゲ' to 'ケ', 'ゴ' to 'コ',
        'ザ' to 'サ', 'ジ' to 'シ', 'ズ' to 'ス', 'ゼ' to 'セ', 'ゾ' to 'ソ',
        'ダ' to 'タ', 'ヂ' to 'チ', 'ヅ' to 'ツ', 'デ' to 'テ', 'ド' to 'ト',
        'バ' to 'ハ', 'ビ' to 'ヒ', 'ブ' to 'フ', 'ベ' to 'ヘ', 'ボ' to 'ホ',
        'パ' to 'ハ', 'ピ' to 'ヒ', 'プ' to 'フ', 'ペ' to 'ヘ', 'ポ' to 'ホ',
        'ヴ' to 'ウ',
    )

    /**
     * Words whose katakana spelling is not in doubt, indexed by their skeleton.
     *
     * Deliberately short, and every entry earns its place twice over: it repairs
     * a run that lost its marks, AND it protects a run that never had any. Both
     * directions are needed, because the recogniser does not only drop marks --
     * it invents wrong ones. Over the 7-volume corpus ハーレム (harem, correctly
     * unmarked) appears 4 times against 1 for バーレム, while ハーティー (wrong)
     * appears 14 times against 11 for パーティー. Skeleton alone cannot tell
     * those two situations apart; the word list is what arbitrates.
     *
     * Words of two characters are excluded on purpose, and so is any word whose
     * skeleton is itself an ordinary word: ボール would rewrite ホール (a hall),
     * ビール would rewrite ヒール (heal, which every one of these volumes uses),
     * カード would rewrite カート, and プレイヤー would rename フレイヤー, who is
     * a person. A repair that renames a character is worse than the misreading.
     */
    private val KATAKANA_WORDS = listOf(
        "パーティー", "サポート", "ダンジョン", "モンスター", "アイテム", "ステータス",
        "スキル", "レベル", "ダメージ", "ギルド", "クエスト", "ドラゴン", "ゴブリン",
        "エルフ", "ポーション", "マジック", "パワー", "スピード", "ラスボス", "チート",
        "キャラクター", "エネルギー", "ページ", "マスター", "ランク", "ハーレム",
        "ゲーム", "システム", "ベッド", "テーブル", "コーヒー", "ズボン", "ボタン",
        "アドバイス", "プレゼント", "スカート", "ポケット", "テスト", "チーム",
        "ランキング", "バランス", "チャンス", "ペース", "グループ",
    )

    private val bySkeleton: Map<String, String> =
        KATAKANA_WORDS.groupBy { skeleton(it) }
            // Two words sharing a skeleton would make the choice arbitrary.
            // Refuse both rather than pick one.
            .filterValues { it.size == 1 }
            .mapValues { (_, words) -> words.first() }

    private fun skeleton(text: String): String {
        val out = StringBuilder(text.length)
        for (c in text) out.append(UNMARKED[c] ?: c)
        return out.toString()
    }

    private const val COUNTERS = "丁人本回個度発杯枚匹台冊軒階年月日時分秒歳番件"

    fun apply(text: String): String = repairMarks(repairHomoglyphs(text))

    /**
     * Rewrites a katakana run to the spelling the word actually has, when the
     * run differs from a known word only by its dakuten and handakuten.
     *
     * The whole run has to be the word. A run is one word here -- Japanese does
     * not space them and a partial match would rewrite the inside of a name.
     * ランクハーティー is therefore left alone even though it visibly holds
     * パーティー: it is one run, its skeleton is not in the list, and splitting
     * runs on a guess is how a repair starts renaming people.
     */
    private fun repairMarks(text: String): String {
        if (text.isEmpty() || bySkeleton.isEmpty()) return text
        var out: StringBuilder? = null
        var i = 0
        while (i < text.length) {
            if (!text[i].isKatakana()) {
                i++
                continue
            }
            var end = i
            while (end < text.length && text[end].isKatakana()) end++
            if (end - i >= 2) {
                val run = text.substring(i, end)
                val word = bySkeleton[skeleton(run)]
                if (word != null && word != run && word.length == run.length) {
                    val builder = out ?: StringBuilder(text).also { out = it }
                    for (k in word.indices) builder[i + k] = word[k]
                }
            }
            i = end
        }
        return out?.toString() ?: text
    }

    private fun repairHomoglyphs(text: String): String {
        if (text.isEmpty()) return text

        var out: StringBuilder? = null
        for (i in text.indices) {
            val kanji = homoglyphs[text[i]] ?: continue
            val before = text.getOrNull(i - 1)
            val after = text.getOrNull(i + 1)
            // Part of a katakana word: not a misread kanji.
            if (before?.isKatakana() == true || after?.isKatakana() == true) continue
            // Needs a kanji beside it — that is what makes this a kanji context.
            if (before?.isKanji() != true && after?.isKanji() != true) continue
            if (text[i] == 'ー' && (after == null || after !in COUNTERS)) continue

            val builder = out ?: StringBuilder(text).also { out = it }
            builder[i] = kanji
        }
        return out?.toString() ?: text
    }

    private fun Char.isKatakana(): Boolean = this in 'ァ'..'ヺ' || this == 'ー'

    private fun Char.isKanji(): Boolean = this in '一'..'鿿'
}
