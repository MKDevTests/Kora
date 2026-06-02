package snd.komelia.ui.platform

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberVoiceSearchLauncher(onResult: (String) -> Unit): (() -> Unit)? {
    val context = LocalContext.current
    // Gate on availability so the mic never shows on devices without a speech
    // recognizer (also covers API 30+ where the <queries> RecognitionService
    // entry is required for this check to resolve at all).
    if (!SpeechRecognizer.isRecognitionAvailable(context)) return null

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()
            if (!spoken.isNullOrEmpty()) onResult(spoken)
        }
    }

    return {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            // No EXTRA_LANGUAGE on purpose: the recognizer uses the device's
            // configured speech language (fr / en / ja, per system settings).
        }
        // Defensive: some OEMs report availability but still lack an Activity
        // to handle the intent.
        runCatching { launcher.launch(intent) }
    }
}
