package snd.komelia.image

import snd.komelia.settings.model.TranslationLanguage

/**
 * On-device text translation, used to translate the blocks OCR found on the
 * page being read.
 *
 * Implementations keep at most one loaded language pair: a loaded translator
 * costs tens of megabytes of heap, and the reader only ever needs the pair the
 * user selected.
 */
expect class TranslationService() {

    /** True when both models are already on the device — no download needed. */
    suspend fun isReady(source: TranslationLanguage, target: TranslationLanguage): Boolean

    /**
     * Downloads whatever is missing for the pair. [requireWifi] is the default
     * because the models are around 30MB each.
     */
    suspend fun downloadModels(
        source: TranslationLanguage,
        target: TranslationLanguage,
        requireWifi: Boolean = true,
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
