package snd.komelia.transcription

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private val logger = KotlinLogging.logger {}
private const val CHUNK_THRESHOLD_SECONDS = 10
private const val CHUNK_THRESHOLD_MS = CHUNK_THRESHOLD_SECONDS * 1_000L
private const val WHISPER_SAMPLE_RATE = 16_000
private const val INITIAL_PCM_BUFFER_SAMPLES = WHISPER_SAMPLE_RATE * CHUNK_THRESHOLD_SECONDS

class WhisperTranscriptionBackend(
    private val store: TranscriptStore,
    private val modelPath: String,
    private val language: String?,
    scope: CoroutineScope,
) : TranscriptionBackend {

    private val _state = MutableStateFlow<TranscriptEngineState>(TranscriptEngineState.Idle)
    override val state: StateFlow<TranscriptEngineState> = _state

    private val innerScope = CoroutineScope(
        scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job])
    )
    private val cleanupScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var nativeCtx = 0L

    private var pcmBuffer = ShortArray(INITIAL_PCM_BUFFER_SAMPLES)
    private var pcmBufferSize = 0
    private var bufferStartMs = 0L
    private var bufferDurationMs = 0L
    private val mutex = Mutex()
    private val inferenceMutex = Mutex()

    override suspend fun start() {
        logger.info { "WhisperBackend: loading model from $modelPath" }
        val loadStart = System.currentTimeMillis()
        val loadedCtx = withContext(Dispatchers.IO + NonCancellable) {
            WhisperJni.loadModel(modelPath)
        }
        if (loadedCtx == 0L) {
            logger.error { "WhisperBackend: model load failed (path=$modelPath)" }
            _state.value = TranscriptEngineState.Error("Failed to load Whisper model: $modelPath")
            return
        }
        if (!currentCoroutineContext().isActive) {
            withContext(Dispatchers.IO + NonCancellable) {
                WhisperJni.freeContext(loadedCtx)
            }
            logger.info { "WhisperBackend: released model loaded by a cancelled session" }
            return
        }
        nativeCtx = loadedCtx
        logger.info { "WhisperBackend: model loaded ctx=$nativeCtx in ${System.currentTimeMillis() - loadStart}ms" }
        _state.value = TranscriptEngineState.Active()
    }

    override suspend fun onPcmChunk(bytes: ByteArray, bookTimeMs: Long, durationMs: Long) {
        if (nativeCtx == 0L) return
        val shouldRunInference = mutex.withLock {
            if (pcmBufferSize == 0) bufferStartMs = bookTimeMs
            appendPcm16Le(bytes)
            bufferDurationMs += durationMs
            logger.debug { "WhisperBackend: chunk bytes=${bytes.size} bookMs=$bookTimeMs durationMs=$durationMs bufferMs=$bufferDurationMs" }
            bufferDurationMs >= CHUNK_THRESHOLD_MS
        }
        if (shouldRunInference) {
            runInference()
        }
    }

    private suspend fun runInference() {
        val (floats, offsetMs) = mutex.withLock {
            if (pcmBufferSize == 0) return
            val f = FloatArray(pcmBufferSize) { i -> pcmBuffer[i] / 32768f }
            val o = bufferStartMs
            pcmBufferSize = 0
            bufferStartMs += bufferDurationMs
            bufferDurationMs = 0L
            f to o
        }

        val inferStart = System.currentTimeMillis()
        logger.info { "WhisperBackend: running inference floats=${floats.size} offsetMs=$offsetMs" }
        // inferenceMutex ensures freeContext in stop() waits for this call to finish
        val results = inferenceMutex.withLock {
            if (nativeCtx == 0L) return
            runCatching {
                withContext(Dispatchers.IO) {
                    WhisperJni.transcribeChunk(nativeCtx, floats, offsetMs, language)
                }
            }.getOrElse { e ->
                logger.error(e) { "WhisperBackend: transcribeChunk threw" }
                _state.value = TranscriptEngineState.Error("Whisper inference failed: ${e.message}")
                return
            }
        }
        logger.info { "WhisperBackend: inference done in ${System.currentTimeMillis() - inferStart}ms segments=${results.size}" }

        val segments = results.map { r ->
            TranscriptSegment(
                id = store.nextId(),
                startMs = r.startMs,
                endMs = r.endMs,
                text = r.text.trim(),
                isFinal = true,
                chunkId = offsetMs,
            )
        }.filter { it.text.isNotBlank() }

        segments.forEach { logger.debug { "WhisperBackend: segment [${it.startMs}-${it.endMs}] ${it.text}" } }
        store.addSegments(segments)
    }

    override fun onSeek(newPositionMs: Long) {
        innerScope.launch {
            mutex.withLock {
                pcmBufferSize = 0
                bufferStartMs = newPositionMs
                bufferDurationMs = 0L
            }
        }
    }

    override fun stop() {
        logger.info { "WhisperBackend: stop called" }
        innerScope.cancel()
        val savedCtx = nativeCtx
        nativeCtx = 0L
        if (savedCtx != 0L) {
            // Wait for any in-flight transcribeChunk to finish before freeing the context
            cleanupScope.launch {
                try {
                    inferenceMutex.withLock {
                        WhisperJni.freeContext(savedCtx)
                        logger.info { "WhisperBackend: freeContext done" }
                    }
                } finally {
                    cleanupScope.cancel()
                }
            }
        } else {
            cleanupScope.cancel()
        }
        _state.value = TranscriptEngineState.Idle
    }

    private fun appendPcm16Le(bytes: ByteArray) {
        val sampleCount = bytes.size / 2
        ensurePcmCapacity(pcmBufferSize + sampleCount)

        var sourceIndex = 0
        var destinationIndex = pcmBufferSize
        repeat(sampleCount) {
            val low = bytes[sourceIndex].toInt() and 0xFF
            val high = bytes[sourceIndex + 1].toInt() shl 8
            pcmBuffer[destinationIndex] = (low or high).toShort()
            sourceIndex += 2
            destinationIndex += 1
        }
        pcmBufferSize = destinationIndex
    }

    private fun ensurePcmCapacity(requiredCapacity: Int) {
        if (requiredCapacity <= pcmBuffer.size) return
        val expandedCapacity = maxOf(requiredCapacity, pcmBuffer.size * 2)
        pcmBuffer = pcmBuffer.copyOf(expandedCapacity)
    }
}
