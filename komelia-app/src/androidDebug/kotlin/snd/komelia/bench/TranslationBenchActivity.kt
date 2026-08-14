package snd.komelia.bench

import android.app.Activity
import android.os.Bundle
import android.util.Log
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val directory = getExternalFilesDir(null)
        val input = File(directory, intent.getStringExtra("in") ?: "bench-in.txt")
        val output = File(directory, intent.getStringExtra("out") ?: "bench-out.txt")
        val source = language(intent.getStringExtra("source"), TranslationLanguage.ENGLISH)
        val target = language(intent.getStringExtra("target"), TranslationLanguage.FRENCH)

        if (!input.isFile) {
            Log.e(TAG, "no corpus at ${input.absolutePath}")
            finish()
            return
        }

        val sentences = input.readLines()
        Log.i(TAG, "translating ${sentences.size} lines ${source.code}->${target.code}")

        CoroutineScope(Dispatchers.Default).launch {
            val service = TranslationService()
            try {
                if (!service.isReady(source, target)) {
                    Log.e(TAG, "${source.code}->${target.code} models are not on the device; " +
                        "download them from the reader's translation settings first")
                    return@launch
                }
                val started = System.currentTimeMillis()
                val translated = service.translate(sentences, source, target)
                val elapsed = System.currentTimeMillis() - started

                output.writeText(translated.joinToString("\n"))
                val each = if (sentences.isEmpty()) 0 else elapsed / sentences.size
                Log.i(TAG, "done in ${elapsed}ms (${each}ms each) -> ${output.absolutePath}")
            } catch (e: Throwable) {
                Log.e(TAG, "bench failed", e)
            } finally {
                service.release()
                finish()
            }
        }
    }

    private fun language(code: String?, fallback: TranslationLanguage) =
        TranslationLanguage.entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: fallback

    private companion object {
        const val TAG = "KoraTranslate"
    }
}
