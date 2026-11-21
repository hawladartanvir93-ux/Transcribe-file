package com.example.data.asr

import com.example.data.audio.AudioDecoder
import com.example.data.audio.TranscriptFormatter
import com.example.domain.model.TranscriptSegment
import com.example.domain.model.WordTiming
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.FileInputStream
import java.util.UUID

class VoskTranscriptionEngine {

    suspend fun transcribePcm(
        pcmFile: File,
        modelDir: File,
        maxCharsPerCue: Int,
        onProgress: (progress: Float, partialText: String, completedCues: Int) -> Unit,
    ): List<TranscriptSegment> = withContext(Dispatchers.IO) {
        LibVosk.setLogLevel(LogLevel.WARNINGS)
        val totalBytes = pcmFile.length().coerceAtLeast(1L)
        val collectedWords = mutableListOf<WordTiming>()
        val fallbackSegments = mutableListOf<TranscriptSegment>()

        var model: Model? = null
        var recognizer: Recognizer? = null

        try {
            model = Model(modelDir.absolutePath)
            recognizer = Recognizer(model, AudioDecoder.TARGET_SAMPLE_RATE.toFloat()).apply {
                setWords(true)
            }

            val buffer = ByteArray(4096)
            var bytesReadTotal = 0L
            var latestPartial = ""

            FileInputStream(pcmFile).use { fis ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = fis.read(buffer)
                    if (read <= 0) break
                    bytesReadTotal += read

                    val isSegmentEnd = recognizer.acceptWaveForm(buffer, read)
                    if (isSegmentEnd) {
                        val resultJson = recognizer.result
                        parseVoskResultJson(
                            jsonRaw = resultJson,
                            wordsOut = collectedWords,
                            fallbackSegmentsOut = fallbackSegments,
                        )
                        latestPartial = ""
                    } else {
                        val partialRaw = recognizer.partialResult
                        val partial = runCatching { JSONObject(partialRaw).optString("partial", "") }
                            .getOrDefault("")
                        if (partial.isNotBlank()) {
                            latestPartial = partial
                        }
                    }

                    val progress = (bytesReadTotal.toFloat() / totalBytes.toFloat()).coerceIn(0f, 0.99f)
                    val approxCues = maxOf(fallbackSegments.size, collectedWords.size / 7)
                    onProgress(progress, latestPartial, approxCues)
                }
            }

            val finalJson = recognizer.finalResult
            parseVoskResultJson(
                jsonRaw = finalJson,
                wordsOut = collectedWords,
                fallbackSegmentsOut = fallbackSegments,
            )
            onProgress(1f, "", maxOf(fallbackSegments.size, collectedWords.size / 7))

            if (collectedWords.isNotEmpty()) {
                TranscriptFormatter.buildCuesFromWords(
                    words = collectedWords,
                    maxCharsPerCue = maxCharsPerCue,
                )
            } else {
                fallbackSegments
            }
        } finally {
            runCatching { recognizer?.close() }
            runCatching { model?.close() }
        }
    }

    private fun parseVoskResultJson(
        jsonRaw: String,
        wordsOut: MutableList<WordTiming>,
        fallbackSegmentsOut: MutableList<TranscriptSegment>,
    ) {
        if (jsonRaw.isBlank()) return
        runCatching {
            val obj = JSONObject(jsonRaw)
            val resultArr = obj.optJSONArray("result")
            if (resultArr != null && resultArr.length() > 0) {
                for (i in 0 until resultArr.length()) {
                    val item = resultArr.getJSONObject(i)
                    val word = item.optString("word", "").trim()
                    if (word.isNotEmpty()) {
                        val startMs = (item.optDouble("start", 0.0) * 1000.0).toLong()
                        val endMs = (item.optDouble("end", 0.0) * 1000.0).toLong().coerceAtLeast(startMs + 80L)
                        val conf = item.optDouble("conf", 1.0).toFloat().coerceIn(0f, 1f)
                        wordsOut.add(WordTiming(word = word, startMs = startMs, endMs = endMs, confidence = conf))
                    }
                }
            } else {
                val text = obj.optString("text", "").trim()
                if (text.isNotEmpty()) {
                    val lastEnd = fallbackSegmentsOut.lastOrNull()?.endMs ?: 0L
                    fallbackSegmentsOut.add(
                        TranscriptSegment(
                            id = UUID.randomUUID().toString(),
                            index = fallbackSegmentsOut.size + 1,
                            startMs = lastEnd,
                            endMs = lastEnd + 2500L,
                            text = text,
                            confidence = 1f,
                        )
                    )
                }
            }
        }
    }
}
