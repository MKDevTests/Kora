package snd.komelia.bench

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import snd.komelia.image.TranslationService
import snd.komelia.settings.model.TranslationLanguage
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

        CoroutineScope(Dispatchers.Default).launch {
            val service = TranslationService()
            try {
                if (!service.isReady(source, target)) {
                    report(
                        "${source.code}->${target.code} models are not on the device; " +
                            "download them from the reader's translation settings first",
                        error = true,
                    )
                    return@launch
                }
                val started = System.currentTimeMillis()
                val translated = service.translate(sentences, source, target)
                val elapsed = System.currentTimeMillis() - started

                output.writeText(translated.joinToString("\n"))
                val each = if (sentences.isEmpty()) 0 else elapsed / sentences.size
                report("done in ${elapsed}ms (${each}ms each) -> ${output.absolutePath}")
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

    private fun language(code: String?, fallback: TranslationLanguage) =
        TranslationLanguage.entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: fallback

    private companion object {
        const val TAG = "KoraTranslate"
    }
}
