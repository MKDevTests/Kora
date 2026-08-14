package snd.komelia.image

import kotlinx.coroutines.flow.Flow
import snd.komelia.settings.model.TranslationLanguage
import snd.komelia.updates.UpdateProgress

/**
 * Fetches the models an engine needs but cannot fetch itself.
 *
 * Only Bergamot has one: ML Kit downloads its own models through Google Play
 * services, and asking it to would be a different mechanism with different
 * failure modes. Null on the platforms with no engine at all.
 *
 * Separate from [TranslationEngine] because the two are used at opposite times.
 * The engine is called on a page turn and must never block on a network; this
 * moves 36MB and belongs behind a button with a progress bar.
 */
interface TranslationModelDownloader {

    /** Which pairs this downloader has a model for at all. */
    fun supports(source: TranslationLanguage, target: TranslationLanguage): Boolean

    fun isDownloaded(source: TranslationLanguage, target: TranslationLanguage): Boolean

    /** Bytes as they arrive. Cancelling the collection cancels the transfer. */
    fun download(source: TranslationLanguage, target: TranslationLanguage): Flow<UpdateProgress>

    /** Frees the disk space again. */
    fun delete(source: TranslationLanguage, target: TranslationLanguage)
}
