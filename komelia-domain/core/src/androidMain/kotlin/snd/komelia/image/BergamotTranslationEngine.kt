package snd.komelia.image

import android.content.Context
import io.github.marcosholgado.translatekit.ModelSpec
import io.github.marcosholgado.translatekit.TranslateKit
import io.github.marcosholgado.translatekit.TranslationModel
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import snd.komelia.settings.model.TranslationLanguage
import java.io.File

private val logger = KotlinLogging.logger { }

/**
 * Bergamot/Marian on device, through translate-kit's JNI wrapper.
 *
 * Why alongside ML Kit rather than instead of it, for now: measured over 400
 * real bubbles, Bergamot leaves fewer English words untranslated (21 against 27
 * out of 142), and keeps interjections where ML Kit invents — "Huh." stays
 * "Hein." rather than becoming "C'est quoi ?". Speed is the open question: 8ms
 * a bubble against ML Kit's 43ms, but that was x86 with ruy on a PC and says
 * nothing about ARM. Measuring it on the tablet is what this class is for.
 *
 * Falls back by returning the input untouched whenever it cannot work — no
 * model on disk, or no native library for the device's ABI (translate-kit ships
 * arm64-v8a only, and reports that through [TranslateKit.isInitialized] rather
 * than by throwing, since an UnsatisfiedLinkError is an Error and would sail
 * past a caller's Exception handler).
 */
class BergamotTranslationEngine(
    private val context: Context,
    private val modelRoot: File,
) : TranslationEngine {

    private val mutex = Mutex()
    private var loaded: LoadedPair? = null

    private class LoadedPair(val pair: BergamotPair, val model: TranslationModel)

    override suspend fun isReady(source: TranslationLanguage, target: TranslationLanguage): Boolean {
        val pair = BergamotPair.of(source, target) ?: return false
        return pair.isComplete(modelRoot)
    }

    /**
     * Nothing to do here. The files are fetched by [BergamotModelDownloader],
     * which reports progress and can be cancelled; pulling 36MB from inside a
     * call the reader makes on a page turn would be invisible until it wasn't.
     */
    override suspend fun downloadModels(
        source: TranslationLanguage,
        target: TranslationLanguage,
        requireWifi: Boolean,
    ) = Unit

    override suspend fun translate(
        texts: List<String>,
        source: TranslationLanguage,
        target: TranslationLanguage,
    ): List<String> {
        if (texts.isEmpty()) return texts
        val model = mutex.withLock { modelFor(source, target) } ?: return texts
        return texts.map { text ->
            if (text.isBlank()) text
            // isHtml = false: the reader hands over plain sentences. The HTML
            // path exists to re-align inline tags, which is one more thing to
            // get wrong for nothing gained here.
            else runCatching { model.translate(text, false).text }
                .onFailure { logger.warn(it) { "translation failed for a block, keeping the original" } }
                .getOrDefault(text)
        }
    }

    override fun release() {
        loaded?.model?.close()
        loaded = null
    }

    /**
     * One live model at a time, for the same reason ML Kit keeps one
     * translator: the weights are mmap'd, but the engine's workspace is not.
     */
    private fun modelFor(
        source: TranslationLanguage,
        target: TranslationLanguage,
    ): TranslationModel? {
        val pair = BergamotPair.of(source, target) ?: return null
        loaded?.let { if (it.pair == pair) return it.model }
        if (!pair.isComplete(modelRoot)) return null

        TranslateKit.init(context)
        if (!TranslateKit.isInitialized()) {
            logger.warn { "translate-kit has no native library for this device's ABI" }
            return null
        }

        val (model, vocab, shortlist) = pair.files(modelRoot)
        loaded?.model?.close()
        loaded = null
        return runCatching {
            TranslateKit.loadModel(
                ModelSpec(
                    sourceLang = source.code,
                    targetLang = target.code,
                    modelPath = model.absolutePath,
                    // One entry, not two: this pair shares a single
                    // SentencePiece vocabulary between source and target.
                    vocabPaths = listOf(vocab.absolutePath),
                    shortlistPath = shortlist.absolutePath,
                    // Engine defaults. The bench drives the CLI with an explicit
                    // YAML (beam-size 1, int8shiftAlphaAll, and the rest), but
                    // that file also carries the model paths, which this API
                    // passes separately — so handing it over here would say the
                    // same thing twice, in two places that can disagree. Worth
                    // revisiting once the tablet numbers exist.
                    configYaml = null,
                    numWorkers = 1,
                )
            )
        }
            .onFailure { logger.warn(it) { "could not load the Bergamot model for $pair" } }
            .onSuccess { loaded = LoadedPair(pair, it) }
            .getOrNull()
    }
}
