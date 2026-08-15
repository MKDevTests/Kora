package snd.komelia.image

import io.github.oshai.kotlinlogging.KotlinLogging
import snd.komelia.settings.model.TranslationLanguage

/** Same tag as the reader's block dump, so one grep shows both. */
private val translationLogger = KotlinLogging.logger("KoraTranslate")

/**
 * Uses Bergamot when its model for the pair is on disk, ML Kit otherwise.
 *
 * No setting behind this, deliberately. The user-visible choice is whether to
 * download the Bergamot pair, which is a 36MB decision they take once; a second
 * control asking which engine to use would be the same decision asked twice.
 * Comparing the two is a bench job — TranslationBenchActivity takes the engine
 * as an intent extra — not something the reader should be configured for.
 *
 * Both are held, not created on demand: each keeps at most one loaded pair, and
 * the fallback has to be ready on the page where the download has not finished.
 */
class PreferredTranslationEngine(
    private val bergamot: BergamotTranslationEngine,
    private val mlKit: TranslationEngine,
) : TranslationEngine {

    /**
     * True when either engine can do the pair. Bergamot's models are on disk or
     * they are not; ML Kit's it downloads itself, which is why this is not
     * simply the preferred engine's answer.
     */
    override suspend fun isReady(source: TranslationLanguage, target: TranslationLanguage): Boolean =
        bergamot.isReady(source, target) || mlKit.isReady(source, target)

    /**
     * Always ML Kit's job. Bergamot's models come from the downloader, and a
     * pair Bergamot cannot do still needs ML Kit's to be present.
     */
    override suspend fun downloadModels(
        source: TranslationLanguage,
        target: TranslationLanguage,
        requireWifi: Boolean,
    ) = mlKit.downloadModels(source, target, requireWifi)

    override suspend fun translate(
        texts: List<String>,
        source: TranslationLanguage,
        target: TranslationLanguage,
    ): List<String> {
        // Asked before translating rather than inferred from the result: an
        // engine that cannot run returns the input untouched, and so does one
        // handed a page of sound effects. Telling those apart by comparing the
        // output would silently keep the wrong engine.
        val pair = "${source.code}-${target.code}"
        if (bergamot.canTranslate(source, target)) {
            announce(pair, "Bergamot")
            return bergamot.translate(texts, source, target)
        }
        announce(pair, "ML Kit")
        return mlKit.translate(texts, source, target)
    }

    /**
     * Says which engine is running, once per pair per session.
     *
     * This was a debug line, and that cost a full round of testing. The debug
     * build carries applicationIdSuffix ".debug", so it has its own filesDir and
     * none of the Bergamot models the release build downloaded — it fell back to
     * ML Kit without a word, and a page of ML Kit output was read as a Bergamot
     * regression. The two engines fail differently enough that knowing which one
     * answered is the first thing to establish: ML Kit leaves a word it does not
     * know in English ("j'ai eu enoigh"), Bergamot never does.
     *
     * On the reader-translation logger so it lands in the same grep as the
     * blocks it explains.
     */
    private fun announce(pair: String, engine: String) {
        if (announced.add(pair to engine)) {
            translationLogger.info { "translating $pair with $engine" }
        }
    }

    private val announced = mutableSetOf<Pair<String, String>>()

    override fun release() {
        bergamot.release()
        mlKit.release()
    }
}
