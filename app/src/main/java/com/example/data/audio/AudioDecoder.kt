package com.example.data.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.example.domain.model.AudioFileMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class DecodedAudioResult(
    val pcmFile: File,
    val totalPcmBytes: Long,
    val durationMs: Long,
    val sourceSampleRateHz: Int,
    val sourceChannelCount: Int,
    val waveformBars: List<Float>,
)

data class SpeechWindow(
    val startMs: Long,
    val endMs: Long,
    val startByteOffset: Long,
    val endByteOffset: Long,
)

class AudioDecoder(private val context: Context) {

    suspend fun inspectAudioUri(uri: Uri): AudioFileMetadata = withContext(Dispatchers.IO) {
        var displayName = "audio_track.mp3"
        var fileSizeBytes = 0L

        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIdx >= 0) {
                    cursor.getString(nameIdx)?.takeIf { it.isNotBlank() }?.let { displayName = it }
                }
                if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                    fileSizeBytes = cursor.getLong(sizeIdx)
                }
            }
        }

        val mimeType = context.contentResolver.getType(uri) ?: "audio/mpeg"
        var durationMs = 0L
        var sampleRateHz = 44100
        var channelCount = 2

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
        } finally {
            runCatching { retriever.release() }
        }

        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            val trackIndex = findAudioTrack(extractor)
            if (trackIndex >= 0) {
                val format = extractor.getTrackFormat(trackIndex)
                if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                    sampleRateHz = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                }
                if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                    channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                }
                if (durationMs <= 0L && format.containsKey(MediaFormat.KEY_DURATION)) {
                    durationMs = format.getLong(MediaFormat.KEY_DURATION) / 1000L
                }
            }
        } catch (_: Exception) {
        } finally {
            runCatching { extractor.release() }
        }

        AudioFileMetadata(
            uriString = uri.toString(),
            displayName = displayName,
            fileSizeBytes = fileSizeBytes,
            durationMs = durationMs,
            sampleRateHz = sampleRateHz,
            channelCount = channelCount,
            mimeType = mimeType,
            waveformBars = emptyList(),
        )
    }

    suspend fun decodeTo16kMonoPcm(
        uri: Uri,
        onProgress: (Float) -> Unit,
    ): DecodedAudioResult = withContext(Dispatchers.IO) {
        val outFile = File(context.cacheDir, "decoded_16k_mono_${System.currentTimeMillis()}.pcm")
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null

        var sourceSampleRate = 44100
        var sourceChannels = 1
        var estimatedDurationUs = 0L
        var currentPcmEncoding = AudioFormat.ENCODING_PCM_16BIT

        val amplitudeBuckets = FloatArray(WAVEFORM_BUCKETS)
        val bucketCounts = IntArray(WAVEFORM_BUCKETS)
        var totalOutputSamples = 0L

        try {
            extractor.setDataSource(context, uri, null)
            val audioTrackIndex = findAudioTrack(extractor)
            if (audioTrackIndex < 0) {
                throw IllegalArgumentException("No audio track found in the selected file.")
            }

            extractor.selectTrack(audioTrackIndex)
            val inputFormat = extractor.getTrackFormat(audioTrackIndex)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME)
                ?: throw IllegalStateException("Missing audio MIME type.")

            sourceSampleRate = if (inputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else 44100
            sourceChannels = if (inputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else 1
            estimatedDurationUs = if (inputFormat.containsKey(MediaFormat.KEY_DURATION)) {
                inputFormat.getLong(MediaFormat.KEY_DURATION)
            } else 0L

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(inputFormat, null, null, 0)
            codec.start()

            val bufferInfo = MediaCodec.BufferInfo()
            var sawInputEos = false
            var sawOutputEos = false

            // Linear resampler state
            var resamplePos = 0.0
            var prevMonoSample = 0f
            var hasPrevSample = false

            BufferedOutputStream(FileOutputStream(outFile), 64 * 1024).use { bos ->
                val outByteBuf = ByteBuffer.allocate(8192).order(ByteOrder.LITTLE_ENDIAN)

                while (!sawOutputEos) {
                    currentCoroutineContext().ensureActive()

                    if (!sawInputEos) {
                        val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                        if (inIndex >= 0) {
                            val inputBuffer = codec.getInputBuffer(inIndex)
                            if (inputBuffer != null) {
                                val sampleSize = extractor.readSampleData(inputBuffer, 0)
                                if (sampleSize < 0) {
                                    codec.queueInputBuffer(
                                        inIndex,
                                        0,
                                        0,
                                        0L,
                                        MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                                    )
                                    sawInputEos = true
                                } else {
                                    val pts = extractor.sampleTime
                                    codec.queueInputBuffer(inIndex, 0, sampleSize, pts, 0)
                                    if (estimatedDurationUs > 0L && pts >= 0L) {
                                        onProgress((pts.toFloat() / estimatedDurationUs.toFloat()).coerceIn(0f, 0.96f))
                                    }
                                    extractor.advance()
                                }
                            }
                        }
                    }

                    val outIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                    when {
                        outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val newFormat = codec.outputFormat
                            if (newFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                                sourceSampleRate = newFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            }
                            if (newFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                                sourceChannels = newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            }
                            if (newFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                                currentPcmEncoding = newFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                            }
                        }
                        outIndex >= 0 -> {
                            val outBuffer = codec.getOutputBuffer(outIndex)
                            if (outBuffer != null && bufferInfo.size > 0) {
                                outBuffer.position(bufferInfo.offset)
                                outBuffer.limit(bufferInfo.offset + bufferInfo.size)
                                outBuffer.order(ByteOrder.LITTLE_ENDIAN)

                                val monoChunk = extractMonoNormalizedSamples(
                                    buffer = outBuffer,
                                    channels = sourceChannels.coerceAtLeast(1),
                                    pcmEncoding = currentPcmEncoding,
                                )

                                if (monoChunk.isNotEmpty()) {
                                    val ratio = sourceSampleRate.toDouble() / TARGET_SAMPLE_RATE.toDouble()
                                    val expectedTotalOut = if (estimatedDurationUs > 0L) {
                                        (estimatedDurationUs * TARGET_SAMPLE_RATE / 1_000_000L).coerceAtLeast(1L)
                                    } else 1L

                                    if (!hasPrevSample) {
                                        prevMonoSample = monoChunk[0]
                                        hasPrevSample = true
                                    }

                                    var pos = resamplePos
                                    while (pos < monoChunk.size) {
                                        val idx0 = pos.toInt()
                                        val frac = (pos - idx0).toFloat()
                                        val s0 = if (idx0 == 0 && resamplePos < 0.0) prevMonoSample else monoChunk[idx0]
                                        val s1 = if (idx0 + 1 < monoChunk.size) monoChunk[idx0 + 1] else monoChunk[idx0]
                                        val interpolated = s0 + (s1 - s0) * frac
                                        val clamped = interpolated.coerceIn(-1f, 1f)
                                        val pcm16 = (clamped * 32767f).toInt().toShort()

                                        if (outByteBuf.remaining() < 2) {
                                            bos.write(outByteBuf.array(), 0, outByteBuf.position())
                                            outByteBuf.clear()
                                        }
                                        outByteBuf.putShort(pcm16)

                                        val bucket = if (estimatedDurationUs > 0L) {
                                            ((totalOutputSamples * WAVEFORM_BUCKETS) / expectedTotalOut)
                                                .toInt()
                                                .coerceIn(0, WAVEFORM_BUCKETS - 1)
                                        } else {
                                            (totalOutputSamples % WAVEFORM_BUCKETS).toInt()
                                        }
                                        val absAmp = abs(clamped)
                                        if (absAmp > amplitudeBuckets[bucket]) {
                                            amplitudeBuckets[bucket] = absAmp
                                        }
                                        bucketCounts[bucket]++
                                        totalOutputSamples++
                                        pos += ratio
                                    }
                                    prevMonoSample = monoChunk.last()
                                    resamplePos = pos - monoChunk.size
                                }
                            }

                            codec.releaseOutputBuffer(outIndex, false)
                            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                sawOutputEos = true
                            }
                        }
                    }
                }

                if (outByteBuf.position() > 0) {
                    bos.write(outByteBuf.array(), 0, outByteBuf.position())
                }
                bos.flush()
            }

            onProgress(1f)
            val totalBytes = outFile.length()
            val actualDurationMs = (totalBytes / 2L) * 1000L / TARGET_SAMPLE_RATE

            val peak = amplitudeBuckets.maxOrNull()?.takeIf { it > 0.01f } ?: 1f
            val normalizedBars = amplitudeBuckets.mapIndexed { idx, value ->
                if (bucketCounts[idx] == 0) 0.10f else (value / peak).coerceIn(0.10f, 1.0f)
            }

            DecodedAudioResult(
                pcmFile = outFile,
                totalPcmBytes = totalBytes,
                durationMs = max(actualDurationMs, estimatedDurationUs / 1000L),
                sourceSampleRateHz = sourceSampleRate,
                sourceChannelCount = sourceChannels,
                waveformBars = normalizedBars,
            )
        } catch (t: Throwable) {
            outFile.delete()
            throw t
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    /**
     * Energy-based Voice Activity Detection (VAD) for slicing 16kHz 16-bit mono PCM
     * into natural speech windows (max 14s each) for chunked Android On-Device ASR.
     */
    suspend fun detectSpeechWindows(
        pcmFile: File,
        durationMs: Long,
        maxWindowMs: Long = 13_500L,
    ): List<SpeechWindow> = withContext(Dispatchers.IO) {
        val totalBytes = pcmFile.length()
        if (totalBytes <= 0L) return@withContext emptyList()

        val bytesPerSecond = TARGET_SAMPLE_RATE * 2L // 32,000 bytes/sec
        val frameMs = 100L
        val frameBytes = ((bytesPerSecond * frameMs) / 1000L).toInt() // 3200 bytes
        val frameRms = mutableListOf<Float>()

        FileInputStream(pcmFile).use { fis ->
            val buf = ByteArray(frameBytes)
            while (true) {
                val read = fis.read(buf)
                if (read <= 0) break
                val sampleCount = read / 2
                if (sampleCount == 0) break
                var sumSq = 0.0
                val bb = ByteBuffer.wrap(buf, 0, sampleCount * 2).order(ByteOrder.LITTLE_ENDIAN)
                for (i in 0 until sampleCount) {
                    val s = bb.short / 32768.0
                    sumSq += s * s
                }
                frameRms.add(sqrt(sumSq / sampleCount).toFloat())
            }
        }

        if (frameRms.isEmpty()) return@withContext emptyList()

        val sortedRms = frameRms.sorted()
        val noiseFloor = sortedRms[(sortedRms.size * 0.15f).toInt().coerceIn(0, sortedRms.lastIndex)]
        val threshold = max(0.012f, noiseFloor * 2.2f)

        val windows = mutableListOf<SpeechWindow>()
        var activeStartFrame: Int? = null
        var silenceRun = 0
        val maxFramesPerWindow = (maxWindowMs / frameMs).toInt()

        for (i in frameRms.indices) {
            val isSpeech = frameRms[i] >= threshold
            if (activeStartFrame == null) {
                if (isSpeech) {
                    activeStartFrame = max(0, i - 2) // 200ms lead-in
                    silenceRun = 0
                }
            } else {
                if (!isSpeech) silenceRun++ else silenceRun = 0
                val currentLen = i - activeStartFrame
                val shouldClose = silenceRun >= 7 || currentLen >= maxFramesPerWindow
                if (shouldClose) {
                    val endFrame = if (silenceRun >= 7) max(activeStartFrame + 3, i - 4) else i
                    windows.add(buildSpeechWindow(activeStartFrame, endFrame, frameMs, totalBytes, durationMs))
                    activeStartFrame = null
                    silenceRun = 0
                }
            }
        }

        if (activeStartFrame != null) {
            windows.add(buildSpeechWindow(activeStartFrame, frameRms.size, frameMs, totalBytes, durationMs))
        }

        // If audio is very quiet or continuous, fall back to uniform 12s chunks so nothing is skipped
        if (windows.isEmpty()) {
            var startMs = 0L
            while (startMs < durationMs) {
                val endMs = min(durationMs, startMs + 12_000L)
                val startByte = ((startMs * bytesPerSecond) / 1000L) and -2L
                val endByte = min(totalBytes, ((endMs * bytesPerSecond) / 1000L) and -2L)
                if (endByte > startByte) {
                    windows.add(SpeechWindow(startMs, endMs, startByte, endByte))
                }
                startMs = endMs
            }
        }
        windows
    }

    private fun buildSpeechWindow(
        startFrame: Int,
        endFrame: Int,
        frameMs: Long,
        totalBytes: Long,
        durationMs: Long,
    ): SpeechWindow {
        val bytesPerSecond = TARGET_SAMPLE_RATE * 2L
        val startMs = (startFrame * frameMs).coerceIn(0L, durationMs)
        val endMs = max(startMs + 400L, (endFrame * frameMs).coerceAtMost(durationMs))
        val startByte = ((startMs * bytesPerSecond) / 1000L).coerceIn(0L, totalBytes) and -2L
        val endByte = ((endMs * bytesPerSecond) / 1000L).coerceIn(startByte, totalBytes) and -2L
        return SpeechWindow(startMs, endMs, startByte, endByte)
    }

    private fun extractMonoNormalizedSamples(
        buffer: ByteBuffer,
        channels: Int,
        pcmEncoding: Int,
    ): FloatArray {
        return if (pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT) {
            val totalFloats = buffer.remaining() / 4
            val frames = totalFloats / channels
            FloatArray(frames) {
                var sum = 0f
                for (c in 0 until channels) {
                    sum += buffer.float
                }
                sum / channels
            }
        } else {
            val totalShorts = buffer.remaining() / 2
            val frames = totalShorts / channels
            FloatArray(frames) {
                var sum = 0f
                for (c in 0 until channels) {
                    sum += buffer.short / 32768f
                }
                sum / channels
            }
        }
    }

    private fun findAudioTrack(extractor: MediaExtractor): Int {
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)
            if (mime != null && mime.startsWith("audio/")) {
                return i
            }
        }
        return -1
    }

    companion object {
        const val TARGET_SAMPLE_RATE = 16000
        private const val TIMEOUT_US = 10_000L
        private const val WAVEFORM_BUCKETS = 64
    }
}
