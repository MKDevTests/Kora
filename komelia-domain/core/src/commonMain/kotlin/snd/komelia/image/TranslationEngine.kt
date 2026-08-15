package snd.komelia.image

import snd.komelia.settings.model.TranslationLanguage

/**
 * On-device text translation, used on the blocks OCR found on the page being
 * read.
 *
 * An interface rather than the `expect class` this replaces, because there is
 * more than one engine worth having on the same platform: ML Kit is what ships,
 * and Bergamot is being measured against it. An `expect class` can only ever
 * have one implementation per target, so choosing between two of them at
 * runtime is not something it can express.
 *
 * Implementations keep at most one loaded language pair: a loaded translator
 * costs tens of megabytes of heap, and the reader only ever needs the pair the
 * user selected.
 *
 * Every call blocks. None of them belong on the main thread.
 */
interface TranslationEngine {

    /** True when the models are already on the device — no download needed. */
    suspend fun isReady(source: TranslationLanguage, target: TranslationLanguage): Boolean

    /**
     * Downloads whatever is missing for the pair. [requireWifi] because the
     * models are around 30MB each.
     *
     * No default argument, deliberately: the callers all pass it, and a default
     * on an interface method is the kind of thing that quietly disagrees with
     * itself once there are two implementations.
     */
    suspend fun downloadModels(
        source: TranslationLanguage,
        target: TranslationLanguage,
        requireWifi: Boolean,
    )

    /**
     * Translates [texts] in order, one entry out per entry in. An entry that
     * fails comes back unchanged rather than failing the whole page.
     */
    suspend fun translate(
        texts: List<String>,
        source: TranslationLanguage,
        target: TranslationLanguage,
    ): List<String>

    /** Releases the loaded pair. Called when the reader closes. */
    fun release()
}

/**
 * Stands in on the platforms with no engine — desktop and web.
 *
 * Returns the text untouched rather than throwing, so page translation is a
 * feature that does nothing there instead of a crash. [isReady] is false, which
 * is what the reader gates the feature on.
 */
object NoopTranslationEngine : TranslationEngine {
    override suspend fun isReady(source: TranslationLanguage, target: TranslationLanguage) = false

    override suspend fun downloadModels(
        source: TranslationLanguage,
        target: TranslationLanguage,
        requireWifi: Boolean,
    ) = Unit

    override suspend fun translate(
        texts: List<String>,
        source: TranslationLanguage,
        target: TranslationLanguage,
    ): List<String> = texts

    override fun release() = Unit
}

/** The engine this platform ships with. */
expect fun defaultTranslationEngine(): TranslationEngine
