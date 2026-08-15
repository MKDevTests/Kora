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

    /**
     * The models currently in memory, keyed by pair.
     *
     * Was a single slot, and had to stop being one when Japanese arrived:
     * ja-fr is two hops and evicting one to load the other would pay a model
     * load twice per page rather than once per book. Capped at [MAX_LOADED],
     * which is exactly the length of the longest route.
     */
    private val loaded = LinkedHashMap<BergamotPair, TranslationModel>()

    override suspend fun isReady(source: TranslationLanguage, target: TranslationLanguage): Boolean {
        val route = BergamotPair.route(source, target)
        return route.isNotEmpty() && route.all { it.isComplete(modelRoot) }
    }

    /**
     * Whether this engine can actually translate the pair right now — files on
     * disk, a native library for this ABI, and a model that loads.
     *
     * [isReady] only answers the first of those, because it is what the reader
     * gates the feature on and it must not load 30MB of weights to answer.
     * This one does load them, so it belongs to whoever is choosing between
     * engines, and the cost is paid once per pair.
     */
    suspend fun canTranslate(
        source: TranslationLanguage,
        target: TranslationLanguage,
    ): Boolean = mutex.withLock {
        val route = BergamotPair.route(source, target)
        route.isNotEmpty() && route.all { modelFor(it) != null }
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
        // Held across the translation, not just the load. translate-kit
        // documents its calls as blocking and its objects as not thread-safe,
        // and the lock used to be released the moment the model was in hand --
        // which left two page turns free to enter the same native model at once.
        return mutex.withLock {
            val route = BergamotPair.route(source, target)
            if (route.isEmpty()) return@withLock texts
            val wanted = texts.indices.filter { texts[it].isNotBlank() }
            if (wanted.isEmpty()) return@withLock texts

            // Every hop translates the whole batch before the next one starts.
            // Going bubble by bubble through both hops would load nothing extra
            // but would cross JNI twice per balloon instead of twice per page.
            var carried: List<String> = wanted.map { texts[it] }
            for (pair in route) {
                val model = modelFor(pair) ?: return@withLock texts
                val batch = runCatching { model.translate(carried, false) }
                    .onFailure { logger.warn(it) { "batch translation failed on $pair, keeping the originals" } }
                    .getOrNull()
                // A short batch would silently shift every result onto the wrong
                // bubble, which reads as the translator scrambling the page rather
                // than as a failure. Keep the originals instead.
                if (batch == null || batch.size != carried.size) {
                    if (batch != null) {
                        logger.warn { "batch returned ${batch.size} of ${carried.size} on $pair — keeping the originals" }
                    }
                    return@withLock texts
                }
                carried = batch.map { it.text }
            }
            val out = texts.toMutableList()
            wanted.forEachIndexed { position, index -> out[index] = carried[position] }
            out
        }
    }

    override fun release() {
        loaded.values.forEach { it.close() }
        loaded.clear()
    }

    /**
     * The model for one hop, loading it if it is not already in memory.
     *
     * The weights are mmap'd but the engine's workspace is not, so this keeps
     * at most [MAX_LOADED] and evicts the least recently used — which on a
     * two-hop route is nothing, and on a change of language pair is the pair
     * that was left behind.
     */
    private fun modelFor(pair: BergamotPair): TranslationModel? {
        loaded[pair]?.let {
            // Re-inserting moves it to the end, which is what makes the first
            // entry the least recently used.
            loaded.remove(pair)
            loaded[pair] = it
            return it
        }
        if (!pair.isComplete(modelRoot)) return null

        TranslateKit.init(context)
        if (!TranslateKit.isInitialized()) {
            logger.warn { "translate-kit has no native library for this device's ABI" }
            return null
        }

        val (model, vocab, shortlist) = pair.files(modelRoot)
        while (loaded.size >= MAX_LOADED) {
            val oldest = loaded.keys.first()
            loaded.remove(oldest)?.close()
        }
        return runCatching {
            TranslateKit.loadModel(
                ModelSpec(
                    sourceLang = pair.source.code,
                    targetLang = pair.target.code,
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
            .onSuccess { loaded[pair] = it }
            .getOrNull()
    }

    private companion object {
        /** The length of the longest route: Japanese to French, through English. */
        const val MAX_LOADED = 2
    }
}
