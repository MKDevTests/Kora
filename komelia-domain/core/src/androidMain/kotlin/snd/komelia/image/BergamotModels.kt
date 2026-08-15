package snd.komelia.image

import snd.komelia.settings.model.TranslationLanguage
import java.io.File

/**
 * Where a Bergamot language pair lives on disk, and what its files are called.
 *
 * Downloaded rather than bundled: the pair is around 36MB, which is not worth
 * carrying in every APK, and the engine takes file paths precisely so it can
 * mmap them.
 *
 * Files are stored under fixed names rather than the ones Mozilla ships
 * (model.enfr.intgemm.alphas.bin, lex.50.50.enfr.s2t.bin) because those names
 * carry the pair and the shortlist pruning in them, and both change from one
 * pair to the next. The directory already says which pair this is.
 */
data class BergamotPair(
    val source: TranslationLanguage,
    val target: TranslationLanguage,
) {
    /** Mozilla's own naming for a pair, and the directory we mirror it in. */
    val directory: String get() = "${source.code}-${target.code}"

    fun dirIn(root: File): File = File(root, directory)

    fun files(root: File): List<File> = dirIn(root).let { dir ->
        listOf(File(dir, MODEL), File(dir, VOCAB), File(dir, SHORTLIST))
    }

    fun isComplete(root: File): Boolean = files(root).all { it.isFile && it.length() > 0 }

    companion object {
        const val MODEL = "model.bin"
        const val VOCAB = "vocab.spm"
        const val SHORTLIST = "lex.bin"

        /**
         * The pairs Mozilla publishes a direct model for.
         *
         * Deliberately not every combination: en-fr and fr-en are separate
         * models, and a pair with no model has to fall back to the other engine
         * rather than silently return the input.
         *
         * ja-en is on the list. An earlier version of this comment said
         * Japanese was not published at all — that was wrong, and it is why
         * nobody looked: the pack is at the same endpoint and in the same
         * format as en-fr (v2.1, 52MB, one shared vocabulary), so it costs a
         * download and no new code.
         */
        private val SUPPORTED = setOf(
            TranslationLanguage.ENGLISH to TranslationLanguage.FRENCH,
            TranslationLanguage.FRENCH to TranslationLanguage.ENGLISH,
            TranslationLanguage.JAPANESE to TranslationLanguage.ENGLISH,
        )

        fun of(source: TranslationLanguage, target: TranslationLanguage): BergamotPair? =
            if ((source to target) in SUPPORTED) BergamotPair(source, target) else null

        /**
         * The hops needed to get from [source] to [target], or an empty list
         * when there is no way.
         *
         * Usually one. Japanese to French is two, through English, because
         * Mozilla publishes no ja-fr model — and the direct alternative was
         * measured and rejected: Helsinki's opus-mt-ja-fr invents proper names
         * ("Tu n'as pas le choix, Nimah" for 仕方ないだろ), at beam 1 and beam
         * 4 alike, and it is an fp32 teacher of 76M parameters where the pivot
         * runs on the 31M student. A flat translation beats a fabricated one.
         *
         * The pivot's own weakness is known and accepted: an ambiguity resolved
         * wrongly in English cannot be recovered in French (しょうがねぇな goes
         * through "i can't help it" and lands on "Je ne peux pas l'aider").
         */
        fun route(
            source: TranslationLanguage,
            target: TranslationLanguage,
        ): List<BergamotPair> {
            if (source == target) return emptyList()
            of(source, target)?.let { return listOf(it) }
            // Only English is used as a bridge. Trying every language as a
            // pivot would find routes nobody wants to read the output of.
            val first = of(source, TranslationLanguage.ENGLISH) ?: return emptyList()
            val second = of(TranslationLanguage.ENGLISH, target) ?: return emptyList()
            return listOf(first, second)
        }
    }
}
