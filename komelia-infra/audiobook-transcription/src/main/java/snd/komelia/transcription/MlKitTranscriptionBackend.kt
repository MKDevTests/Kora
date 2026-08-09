package snd.komelia.transcription

import android.os.Build
import android.util.Log
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.audio.AudioSource
import com.google.mlkit.genai.speechrecognition.SpeechRecognition
import com.google.mlkit.genai.speechrecognition.SpeechRecognizer
import com.google.mlkit.genai.speechrecognition.SpeechRecognizerOptions
import com.google.mlkit.genai.speechrecognition.SpeechRecognizerResponse
import com.google.mlkit.genai.speechrecognition.speechRecognizerRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

private const val TAG = "MlKitBackend"

class MlKitTranscriptionBackend(
    private val store: TranscriptStore,
    scope: CoroutineScope,
    private val locale: Locale = Locale.US,
) : TranscriptionBackend {

    private val _state = MutableStateFlow<TranscriptEngineState>(TranscriptEngineState.Idle)
    override val state: StateFlow<TranscriptEngineState> = _state

    val responsesReceived = AtomicInteger(0)
    val errorsReceived = AtomicInteger(0)
    val lastResponseType = AtomicReference<String>("none")
    val lastErrorMsg = AtomicReference<String>("-")

    private val innerScope = CoroutineScope(
        scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job])
    )
    private val pipeWriter = MlKitAudioPipeWriter(innerScope)
    private var recognizer: SpeechRecognizer? = null

    override suspend fun start() {
        Log.d(TAG, "start() called, SDK=${Build.VERSION.SDK_INT}, locale=$locale")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            _state.value = TranscriptEngineState.UnsupportedDevice
            Log.w(TAG, "Unsupported device (SDK < 31)")
            return
        }

        try {
            val builder = SpeechRecognizerOptions.Builder()
            builder.locale = locale
            builder.preferredMode = 0 // MODE_BASIC
            val options = builder.build()
            val client = SpeechRecognition.getClient(options)
            recognizer = client

            val status = client.checkStatus()
            Log.d(TAG, "checkStatus() = $status")
            when (status) {
                FeatureStatus.AVAILABLE -> startRecognition(client)
                FeatureStatus.DOWNLOADABLE, FeatureStatus.DOWNLOADING -> {
                    _state.value = TranscriptEngineState.Downloading(null)
                    var totalBytes = 0L
                    client.download().collect { dlStatus ->
                        when (dlStatus) {
                            is DownloadStatus.DownloadCompleted -> {
                                Log.d(TAG, "Model download completed")
                                startRecognition(client)
                                return@collect
                            }
                            is DownloadStatus.DownloadStarted -> {
                                totalBytes = dlStatus.bytesToDownload
                                Log.d(TAG, "Download started, total=${totalBytes}B")
                                _state.value = TranscriptEngineState.Downloading(0f)
                            }
                            is DownloadStatus.DownloadProgress -> {
                                val progress = if (totalBytes > 0)
                                    (dlStatus.totalBytesDownloaded.toFloat() / totalBytes).coerceIn(0f, 1f)
                                else null
                                _state.value = TranscriptEngineState.Downloading(progress)
                            }
                            is DownloadStatus.DownloadFailed ->
                                _state.value = TranscriptEngineState.Error("Model download failed: ${dlStatus.e.message}")
                            else -> {}
                        }
                    }
                }
                FeatureStatus.UNAVAILABLE -> {
                    Log.w(TAG, "Feature unavailable")
                    _state.value = TranscriptEngineState.UnsupportedDevice
                }
                else -> {
                    Log.e(TAG, "Unexpected status: $status")
                    _state.value = TranscriptEngineState.Error("Unexpected status: $status")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "start() threw exception", e)
            _state.value = TranscriptEngineState.Error(e.message ?: e.javaClass.simpleName)
        }
    }

    private fun startRecognition(client: SpeechRecognizer) {
        Log.d(TAG, "startRecognition() — live pipe streaming mode")
        _state.value = TranscriptEngineState.Active()

        innerScope.launch {
            try {
                val request = speechRecognizerRequest {
                    audioSource = AudioSource.fromPfd(pipeWriter.readSide)
                }
                client.startRecognition(request).collect { response ->
                    responsesReceived.incrementAndGet()
                    lastResponseType.set(response::class.java.simpleName)
                    when (response) {
                        is SpeechRecognizerResponse.FinalTextResponse -> {
                            val text = response.text.takeIf { it.isNotBlank() } ?: return@collect
                            Log.d(TAG, "FinalText: '$text'")
                            store.upsertLiveSegment(
                                approxEndMs = pipeWriter.audioSentUntilMs,
                                text = text,
                                isFinal = true,
                                approxDurationMs = 2_000L,
                            )
                        }
                        is SpeechRecognizerResponse.PartialTextResponse -> {
                            val text = response.text.takeIf { it.isNotBlank() } ?: return@collect
                            Log.d(TAG, "PartialText: '$text'")
                            store.upsertLiveSegment(
                                approxEndMs = pipeWriter.audioSentUntilMs,
                                text = text,
                                isFinal = false,
                                approxDurationMs = 2_000L,
                            )
                        }
                        is SpeechRecognizerResponse.ErrorResponse -> {
                            errorsReceived.incrementAndGet()
                            val msg = response.e.toString()
                            lastResponseType.set("ERR")
                            lastErrorMsg.set(msg)
                            Log.e(TAG, "Recognition ErrorResponse: $msg")
                        }
                        is SpeechRecognizerResponse.CompletedResponse -> {
                            Log.d(TAG, "Recognition stream completed")
                        }
                        else -> {
                            Log.d(TAG, "Unknown response type: ${response::class.java.simpleName}")
                        }
                    }
                }
                Log.d(TAG, "Recognition flow ended, total responses=${responsesReceived.get()}")
            } catch (e: Exception) {
                Log.e(TAG, "Recognition coroutine threw exception", e)
                _state.value = TranscriptEngineState.Error("Recognition error: ${e.message}")
            }
        }
    }

    override suspend fun onPcmChunk(bytes: ByteArray, bookTimeMs: Long, durationMs: Long) {
        // bytes are already 16kHz mono PCM16; pass 16000/1 so resampler in writePcm is a no-op
        pipeWriter.writePcm(bytes, 16000, 1, bookTimeMs)
    }

    override fun onSeek(newPositionMs: Long) {
        // no-op for ML Kit backend
    }

    override fun stop() {
        Log.d(TAG, "stop() called")
        innerScope.cancel()
        runCatching { recognizer?.close() }
        recognizer = null
        pipeWriter.close()
        _state.value = TranscriptEngineState.Idle
    }
}
