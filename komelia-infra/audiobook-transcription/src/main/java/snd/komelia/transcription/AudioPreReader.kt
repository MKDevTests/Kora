package snd.komelia.transcription

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

private val logger = KotlinLogging.logger {}

data class PcmChunk(
    val bytes: ByteArray,
    val sampleRate: Int,
    val channelCount: Int,
    val bookTimeMs: Long,
    val durationMs: Long,
)

class AudioPreReader(
    private val context: Context,
    private val tracks: List<AudioTranscriptTrack>,
    private val getPlaybackMs: () -> Long,
    private val isPlaybackActive: () -> Boolean,
) {
    companion object {
        const val PRE_READ_AHEAD_MS = 11_000L
        const val MAX_AHEAD_MS = 12_000L
        const val CHUNK_TARGET_MS = 2_000L
    }

    @Volatile private var readHeadMs: Long = 0L
    @Volatile private var cancelled = false

    suspend fun run(onChunk: suspend (PcmChunk) -> Unit) {
        cancelled = false
        readHeadMs = getPlaybackMs() + PRE_READ_AHEAD_MS

        while (!cancelled) {
            if (!isPlaybackActive()) {
                delay(1_000)
                continue
            }
            val playbackMs = getPlaybackMs()

            when {
                readHeadMs > playbackMs + MAX_AHEAD_MS -> {
                    delay(500)
                }
                readHeadMs < playbackMs + PRE_READ_AHEAD_MS -> {
                    readHeadMs = playbackMs + PRE_READ_AHEAD_MS
                }
                else -> {
                    val chunk = try {
                        withContext(Dispatchers.IO) {
                            decodeChunkAt(readHeadMs, CHUNK_TARGET_MS)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        logger.warn(e) { "AudioPreReader: decode failed at readHead=${readHeadMs}ms — skipping ${CHUNK_TARGET_MS}ms ahead" }
                        readHeadMs += CHUNK_TARGET_MS
                        null
                    }
                    if (chunk == null) {
                        delay(1000)
                    } else {
                        onChunk(chunk)
                        readHeadMs += chunk.durationMs
                    }
                }
            }
        }
    }

    fun reset(newBookMs: Long) {
        readHeadMs = newBookMs + PRE_READ_AHEAD_MS
    }

    fun cancel() {
        cancelled = true
    }

    private fun decodeChunkAt(bookMs: Long, maxMs: Long): PcmChunk? {
        val track = tracks.lastOrNull { it.bookOffsetMs <= bookMs } ?: return null
        val inFileMs = bookMs - track.bookOffsetMs
        if (inFileMs >= track.durationMs) return null

        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, track.uri, null)

            val audioTrackIndex = (0 until extractor.trackCount).firstOrNull { i ->
                extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return null

            extractor.selectTrack(audioTrackIndex)
            val format = extractor.getTrackFormat(audioTrackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            extractor.seekTo(inFileMs * 1000L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val pcmBuffer = ByteArrayOutputStream()
            val info = MediaCodec.BufferInfo()
            var decodedMs = 0L
            var eos = false

            try {
                while (decodedMs < maxMs && !eos) {
                    val inIdx = codec.dequeueInputBuffer(10_000L)
                    if (inIdx >= 0) {
                        val inBuf = codec.getInputBuffer(inIdx)!!
                        val size = extractor.readSampleData(inBuf, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            eos = true
                        } else {
                            val pts = extractor.sampleTime
                            codec.queueInputBuffer(inIdx, 0, size, pts, 0)
                            extractor.advance()
                        }
                    }

                    val outIdx = codec.dequeueOutputBuffer(info, 10_000L)
                    if (outIdx >= 0) {
                        val outBuf = codec.getOutputBuffer(outIdx)!!
                        val chunk = ByteArray(info.size)
                        outBuf.get(chunk)
                        pcmBuffer.write(chunk)
                        codec.releaseOutputBuffer(outIdx, false)

                        val bytesPerMs = (sampleRate * channelCount * 2) / 1000
                        decodedMs += if (bytesPerMs > 0) (info.size / bytesPerMs).toLong() else 0L

                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) eos = true
                    }
                }
            } finally {
                codec.stop()
                codec.release()
            }

            val pcm = pcmBuffer.toByteArray()
            if (pcm.isEmpty()) return null

            return PcmChunk(
                bytes = pcm,
                sampleRate = sampleRate,
                channelCount = channelCount,
                bookTimeMs = bookMs,
                durationMs = decodedMs,
            )
        } finally {
            extractor.release()
        }
    }
}
