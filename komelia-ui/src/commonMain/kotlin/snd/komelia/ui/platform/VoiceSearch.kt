package snd.komelia.ui.platform

import androidx.compose.runtime.Composable

/**
 * Returns a launcher that triggers the platform's speech-to-text and feeds the
 * recognized text back through [onResult], or `null` when speech recognition is
 * unavailable on the current platform/device. Callers should hide the mic
 * affordance when this is `null`.
 *
 * On Android this drives `RecognizerIntent.ACTION_RECOGNIZE_SPEECH` (the system
 * "Speak now" dialog) — no in-app RECORD_AUDIO handling needed, and the spoken
 * language follows the device's speech settings. No-op (`null`) on desktop/web.
 */
@Composable
expect fun rememberVoiceSearchLauncher(onResult: (String) -> Unit): (() -> Unit)?
