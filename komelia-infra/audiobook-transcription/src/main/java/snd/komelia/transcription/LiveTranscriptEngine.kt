package snd.komelia.transcription

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

class LiveTranscriptEngine(
    private val context: Context,
    private val tracks: List<AudioTranscriptTrack>,
    private val getPlaybackMs: () -> Long,
    private val isPlaybackActive: () -> Boolean,
    private val scope: CoroutineScope,
    private val backend: TranscriptionBackend,
    private val store: TranscriptStore,
) {
    private companion object {
        const val UI_TICK_INTERVAL_MS = 1_000L
    }

    private val preReader = AudioPreReader(context, tracks, getPlaybackMs, isPlaybackActive)

    private val _engineState = MutableStateFlow<TranscriptEngineState>(TranscriptEngineState.Idle)
    val state: StateFlow<TranscriptEngineState> = _engineState

    private val _visibleSegments = MutableStateFlow<List<TranscriptSegment>>(emptyList())
    val visibleSegments: StateFlow<List<TranscriptSegment>> = _visibleSegments

    private val chunksDecoded = AtomicInteger(0)
    private var preReaderJob: Job? = null
    private var tickerJob: Job? = null

    fun start() {
        preReaderJob = scope.launch {
            backend.start()
            if (backend.state.value !is TranscriptEngineState.Active) return@launch

            preReader.run { chunk ->
                chunksDecoded.incrementAndGet()
                val resampledBytes = Pcm16MonoResampler.convertTo16kMono(
                    chunk.bytes,
                    Pcm16MonoResampler.InputFormat(chunk.sampleRate, chunk.channelCount)
                )
                val durationMs = (resampledBytes.size / 2 * 1000L) / 16000L
                backend.onPcmChunk(resampledBytes, chunk.bookTimeMs, durationMs)
            }
        }

        tickerJob = scope.launch {
            while (isActive) {
                val baseState = backend.state.value
                if (!isPlaybackActive()) {
                    _engineState.value = baseState
                    delay(UI_TICK_INTERVAL_MS)
                    continue
                }
                val playMs = getPlaybackMs()
                _visibleSegments.value = store.visibleSegmentsForPlayback(playMs)
                _engineState.value = if (baseState is TranscriptEngineState.Active) {
                    val totalSegs = store.segments.value.size
                    val vis = _visibleSegments.value.size
                    TranscriptEngineState.Active(
                        "chunks=${chunksDecoded.get()} play=${playMs / 1000}s segs=$totalSegs vis=$vis"
                    )
                } else {
                    baseState
                }
                delay(UI_TICK_INTERVAL_MS)
            }
        }
    }

    fun stop() {
        preReader.cancel()
        preReaderJob?.cancel()
        tickerJob?.cancel()
        backend.stop()
        _visibleSegments.value = emptyList()
        _engineState.value = TranscriptEngineState.Idle
    }

    fun onPlaybackSeeked(newPositionMs: Long) {
        preReader.reset(newPositionMs)
        backend.onSeek(newPositionMs)
    }
}
