package snd.komelia.bench

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import snd.komelia.image.BergamotModelDownloader
import snd.komelia.image.BergamotTranslationEngine
import snd.komelia.image.MlKitTranslationEngine
import snd.komelia.image.TranslationEngine
import snd.komelia.settings.model.TranslationLanguage
import snd.komelia.updates.UpdateClient
import java.io.File

/**
 * Runs the shipping translator over a list of sentences and writes the result
 * next to it, so the engine on the device can be compared against one on a PC
 * over exactly the same input.
 *
 * Debug source set only: it is not compiled into a release build, and it adds
 * nothing to the app's own screens.
 *
 *     adb push corpus.txt /sdcard/Android/data/<id>/files/bench-in.txt
 *     adb shell am start -n <id>/snd.komelia.bench.TranslationBenchActivity
 *     adb pull /sdcard/Android/data/<id>/files/bench-out.txt
 *
 * One sentence per line in, one translation per line out, same order, blank
 * lines preserved. A line that fails comes back unchanged, which is what the
 * reader does, so the output always lines up with the input.
 *
 * The engine is chosen here rather than left to the app's own rule, because
 * comparing the two is the whole point:
 *
 *     -e engine mlkit       ML Kit, whatever else is installed
 *     -e engine bergamot    Bergamot, fails loudly if its pair is missing
 *     -e download true      fetch the Bergamot pair first (36MB), then run
 *
 * Timing is per line and printed with the result. It is the only number that
 * decides whether Bergamot replaces ML Kit: 8ms a bubble was measured on x86
 * with ruy, and says nothing about this device.
 */
class TranslationBenchActivity : Activity() {

    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Shown rather than hidden: an invisible activity is killed halfway
        // through, and there is no other signal that the run is still going.
        status = TextView(this).apply {
            gravity = Gravity.CENTER
            textSize = 18f
            text = "starting…"
        }
        setContentView(status)

        val directory = getExternalFilesDir(null)
        val input = File(directory, intent.getStringExtra("in") ?: "bench-in.txt")
        val output = File(directory, intent.getStringExtra("out") ?: "bench-out.txt")
        val source = language(intent.getStringExtra("source"), TranslationLanguage.ENGLISH)
        val target = language(intent.getStringExtra("target"), TranslationLanguage.FRENCH)

        if (!input.isFile) {
            report("no corpus at ${input.absolutePath}", error = true)
            return
        }

        val sentences = input.readLines()
        Log.i(TAG, "translating ${sentences.size} lines ${source.code}->${target.code}")
        status.text = "translating ${sentences.size} lines ${source.code}->${target.code}…"

        val engineName = intent.getStringExtra("engine") ?: "mlkit"
        val download = intent.getStringExtra("download").toBoolean()

        CoroutineScope(Dispatchers.Default).launch {
            if (download) {
                val fetched = downloadBergamot(source, target)
                if (!fetched) return@launch
            }
            val service = engine(engineName)
            try {
                if (!service.isReady(source, target)) {
                    report(
                        "$engineName has no ${source.code}->${target.code} model on this device" +
                            if (engineName == "bergamot") "; re-run with -e download true"
                            else "; download it from the reader's translation settings first",
                        error = true,
                    )
                    return@launch
                }
                val started = System.currentTimeMillis()
                val translated = service.translate(sentences, source, target)
                val elapsed = System.currentTimeMillis() - started

                output.writeText(translated.joinToString("\n"))
                val each = if (sentences.isEmpty()) 0 else elapsed / sentences.size
                report("$engineName: ${elapsed}ms (${each}ms each) -> ${output.absolutePath}")
            } catch (e: Throwable) {
                Log.e(TAG, "bench failed", e)
                report("bench failed: $e", error = true)
            } finally {
                service.release()
            }
        }
    }

    /**
     * Left on screen rather than finishing: the tablet then says what happened
     * without anyone having to catch it in logcat, and a run that ended badly
     * cannot be mistaken for one that never started.
     */
    private fun report(message: String, error: Boolean = false) {
        if (error) Log.e(TAG, message) else Log.i(TAG, message)
        runOnUiThread { status.text = message }
    }

    /**
     * Built here rather than taken from the app module, which would hand back
     * whichever engine the app prefers -- exactly the choice this bench exists
     * to make by hand.
     */
    private fun engine(name: String): TranslationEngine = when (name) {
        "bergamot" -> BergamotTranslationEngine(this, bergamotModelRoot())
        else -> MlKitTranslationEngine()
    }

    /** Same directory AndroidAppModule uses, so a fetch here serves the reader too. */
    private fun bergamotModelRoot() = filesDir.resolve("bergamot_models")

    /** 36MB over the network, with the byte count reported as it goes. */
    private suspend fun downloadBergamot(
        source: TranslationLanguage,
        target: TranslationLanguage,
    ): Boolean {
        val ktor = io.ktor.client.HttpClient()
        return try {
            BergamotModelDownloader(
                ktor = ktor,
                updateClient = UpdateClient(ktor, ktor),
                modelRoot = bergamotModelRoot(),
            ).download(source, target).collect { progress ->
                val percent = if (progress.total > 0) progress.completed * 100 / progress.total else 0
                report("downloading ${progress.description ?: ""} $percent%")
            }
            true
        } catch (e: Throwable) {
            Log.e(TAG, "model download failed", e)
            report("model download failed: $e", error = true)
            false
        } finally {
            ktor.close()
        }
    }

    private fun language(code: String?, fallback: TranslationLanguage) =
        TranslationLanguage.entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: fallback

    private companion object {
        const val TAG = "KoraTranslate"
    }
}
