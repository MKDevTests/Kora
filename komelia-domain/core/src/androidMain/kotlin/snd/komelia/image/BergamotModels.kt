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
         * Deliberately not every combination: Bergamot has no pivot: en-fr and
         * fr-en exist as separate models, and a pair with no model has to fall
         * back to the other engine rather than silently return the input.
         * Japanese is not on the list at all, which is one reason it stays on
         * ML Kit for now.
         */
        private val SUPPORTED = setOf(
            TranslationLanguage.ENGLISH to TranslationLanguage.FRENCH,
            TranslationLanguage.FRENCH to TranslationLanguage.ENGLISH,
        )

        fun of(source: TranslationLanguage, target: TranslationLanguage): BergamotPair? =
            if ((source to target) in SUPPORTED) BergamotPair(source, target) else null
    }
}
