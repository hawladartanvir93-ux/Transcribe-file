package com.example.data.asr

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.annotation.RequiresApi
import com.example.data.audio.AudioDecoder
import com.example.data.audio.SpeechWindow
import com.example.domain.model.TranscriptSegment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class OnDeviceSpeechUnavailableException(
    message: String,
    val suggestion: String,
) : Exception(message)

class AndroidOnDeviceAsrEngine(private val context: Context) {

    fun isAvailable(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
    }

    suspend fun transcribePcm(
        pcmFile: File,
        durationMs: Long,
        bcp47LanguageTag: String,
        audioDecoder: AudioDecoder,
        onProgress: (progress: Float, partialText: String, completedCues: Int) -> Unit,
    ): List<TranscriptSegment> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            throw OnDeviceSpeechUnavailableException(
                message = "Android System On-Device SpeechRecognizer requires Android 13 (API 33) or higher.",
                suggestion = "Use an offline Vosk model in the Models tab — Vosk works offline on all Android 7+ devices.",
            )
        }
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            throw OnDeviceSpeechUnavailableException(
                message = "System on-device speech recognition service is not installed on this device.",
                suggestion = "Download or import a Vosk offline model in the Models tab, or install Google Speech Recognition offline packs in Android Settings.",
            )
        }

        val windows = audioDecoder.detectSpeechWindows(pcmFile, durationMs)
        if (windows.isEmpty()) return emptyList()

        val results = mutableListOf<TranscriptSegment>()
        for ((idx, window) in windows.withIndex()) {
            currentCoroutineContext().ensureActive()
            val text = recognizeSingleWindow(pcmFile, window, bcp47LanguageTag)
            if (!text.isNullOrBlank()) {
                results.add(
                    TranscriptSegment(
                        id = UUID.randomUUID().toString(),
                        index = results.size + 1,
                        startMs = window.startMs,
                        endMs = window.endMs,
                        text = text.trim(),
                        confidence = 0.92f,
                    )
                )
            }
            onProgress(
                (idx + 1).toFloat() / windows.size.toFloat(),
                text.orEmpty(),
                results.size,
            )
        }
        return results
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun recognizeSingleWindow(
        pcmFile: File,
        window: SpeechWindow,
        bcp47LanguageTag: String,
    ): String? = withContext(Dispatchers.Main) {
        val pipe = ParcelFileDescriptor.createPipe()
        val readFd = pipe[0]
        val writeFd = pipe[1]

        // Stream window PCM bytes asynchronously into the pipe
        val writerJob = launch(Dispatchers.IO) {
            try {
                ParcelFileDescriptor.AutoCloseOutputStream(writeFd).use { out ->
                    RandomAccessFile(pcmFile, "r").use { raf ->
                        raf.seek(window.startByteOffset)
                        var remaining = (window.endByteOffset - window.startByteOffset).coerceAtLeast(0L)
                        val buf = ByteArray(4096)
                        while (remaining > 0L) {
                            val toRead = minOf(buf.size.toLong(), remaining).toInt()
                            val read = raf.read(buf, 0, toRead)
                            if (read <= 0) break
                            out.write(buf, 0, read)
                            remaining -= read
                        }
                        out.flush()
                    }
                }
            } catch (_: Throwable) {
                runCatching { writeFd.close() }
            }
        }

        val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        try {
            suspendCancellableCoroutine { cont ->
                var completed = false

                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}

                    override fun onResults(results: Bundle?) {
                        if (completed) return
                        completed = true
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        cont.resume(matches?.firstOrNull())
                    }

                    override fun onError(error: Int) {
                        if (completed) return
                        completed = true
                        when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH,
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> cont.resume(null)
                            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
                            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> {
                                cont.resumeWithException(
                                    OnDeviceSpeechUnavailableException(
                                        message = "Offline language pack for '$bcp47LanguageTag' is not downloaded on this device.",
                                        suggestion = "In Android Settings -> System -> Languages -> On-device recognition, download '$bcp47LanguageTag', or use a Vosk offline model.",
                                    )
                                )
                            }
                            else -> cont.resumeWithException(
                                OnDeviceSpeechUnavailableException(
                                    message = "On-device speech engine error (code $error).",
                                    suggestion = "Try downloading or importing a Vosk offline model in the Models tab for deterministic offline transcription.",
                                )
                            )
                        }
                    }
                })

                cont.invokeOnCancellation {
                    runCatching { recognizer.cancel() }
                    runCatching { recognizer.destroy() }
                    runCatching { readFd.close() }
                    writerJob.cancel()
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, bcp47LanguageTag)
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                    putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, readFd)
                    putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, AudioDecoder.TARGET_SAMPLE_RATE)
                    putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
                    putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                }

                recognizer.startListening(intent)
            }
        } finally {
            writerJob.cancel()
            runCatching { readFd.close() }
            runCatching { recognizer.destroy() }
        }
    }
}
