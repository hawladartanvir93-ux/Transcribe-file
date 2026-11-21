package com.example.data.audio

import com.example.domain.model.TranscriptSegment
import com.example.domain.model.TranscriptionJob
import com.example.domain.model.WordTiming
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

object TranscriptFormatter {

    fun formatSrtTimestamp(ms: Long): String {
        val clamped = ms.coerceAtLeast(0L)
        val hours = clamped / 3_600_000L
        val minutes = (clamped % 3_600_000L) / 60_000L
        val seconds = (clamped % 60_000L) / 1_000L
        val millis = clamped % 1_000L
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", hours, minutes, seconds, millis)
    }

    fun formatCompactTimestamp(ms: Long): String {
        val clamped = ms.coerceAtLeast(0L)
        val hours = clamped / 3_600_000L
        val minutes = (clamped % 3_600_000L) / 60_000L
        val seconds = (clamped % 60_000L) / 1_000L
        return if (hours > 0L) {
            String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    fun formatSrt(segments: List<TranscriptSegment>): String {
        if (segments.isEmpty()) return ""
        return buildString {
            segments.forEachIndexed { idx, seg ->
                val cueNumber = idx + 1
                append(cueNumber).append('\n')
                append(formatSrtTimestamp(seg.startMs))
                append(" --> ")
                append(formatSrtTimestamp(seg.endMs.coerceAtLeast(seg.startMs + 100L)))
                append('\n')
                append(seg.text.trim())
                if (idx < segments.lastIndex) {
                    append("\n\n")
                } else {
                    append('\n')
                }
            }
        }
    }

    fun formatTxt(segments: List<TranscriptSegment>, includeTimestamps: Boolean): String {
        if (segments.isEmpty()) return ""
        return if (includeTimestamps) {
            segments.joinToString("\n") { seg ->
                "[${formatCompactTimestamp(seg.startMs)} - ${formatCompactTimestamp(seg.endMs)}] ${seg.text.trim()}"
            }
        } else {
            segments.joinToString(" ") { it.text.trim() }
        }
    }

    fun formatJson(job: TranscriptionJob): String {
        val root = JSONObject().apply {
            put("schema_version", "1.0")
            put("generator", "VaultScribe On-Device Studio")
            put("privacy_mode", "strictly_on_device")
            put("source_file", job.fileName)
            put("language_id", job.languageId)
            put("language", job.languageLabel)
            put("engine", job.engineUsed)
            put("duration_ms", job.durationMs)
            put("created_at_ms", job.createdAtMs)
            put("segment_count", job.segments.size)
            put("full_text", job.segments.joinToString(" ") { it.text.trim() }.trim())

            val segArray = JSONArray()
            job.segments.forEachIndexed { idx, seg ->
                val segObj = JSONObject().apply {
                    put("index", idx + 1)
                    put("start_ms", seg.startMs)
                    put("end_ms", seg.endMs)
                    put("start_srt", formatSrtTimestamp(seg.startMs))
                    put("end_srt", formatSrtTimestamp(seg.endMs))
                    put("confidence", seg.confidence.toDouble())
                    put("text", seg.text.trim())
                    if (seg.words.isNotEmpty()) {
                        val wordsArray = JSONArray()
                        seg.words.forEach { w ->
                            wordsArray.put(
                                JSONObject().apply {
                                    put("word", w.word)
                                    put("start_ms", w.startMs)
                                    put("end_ms", w.endMs)
                                    put("confidence", w.confidence.toDouble())
                                }
                            )
                        }
                        put("words", wordsArray)
                    }
                }
                segArray.put(segObj)
            }
            put("segments", segArray)
        }
        return root.toString(2)
    }

    /**
     * Groups word-level timestamps into balanced subtitle cues respecting
     * pause gaps, line character budgets, and max cue duration.
     */
    fun buildCuesFromWords(
        words: List<WordTiming>,
        maxCharsPerCue: Int = 42,
        pauseThresholdMs: Long = 650L,
        maxCueDurationMs: Long = 5000L,
    ): List<TranscriptSegment> {
        if (words.isEmpty()) return emptyList()
        val cues = mutableListOf<TranscriptSegment>()
        val currentWords = mutableListOf<WordTiming>()
        var currentCharLen = 0

        fun flush() {
            if (currentWords.isEmpty()) return
            val first = currentWords.first()
            val last = currentWords.last()
            val avgConf = currentWords.map { it.confidence }.average().toFloat()
            cues.add(
                TranscriptSegment(
                    id = UUID.randomUUID().toString(),
                    index = cues.size + 1,
                    startMs = first.startMs,
                    endMs = last.endMs.coerceAtLeast(first.startMs + 250L),
                    text = currentWords.joinToString(" ") { it.word }.trim(),
                    confidence = avgConf,
                    words = currentWords.toList(),
                )
            )
            currentWords.clear()
            currentCharLen = 0
        }

        for (w in words) {
            val prev = currentWords.lastOrNull()
            val gap = if (prev != null) w.startMs - prev.endMs else 0L
            val projectedDuration = if (currentWords.isNotEmpty()) w.endMs - currentWords.first().startMs else 0L
            val projectedChars = currentCharLen + (if (currentWords.isEmpty()) 0 else 1) + w.word.length

            if (currentWords.isNotEmpty() &&
                (gap > pauseThresholdMs || projectedChars > maxCharsPerCue || projectedDuration > maxCueDurationMs)
            ) {
                flush()
            }
            currentWords.add(w)
            currentCharLen += (if (currentWords.size == 1) 0 else 1) + w.word.length
        }
        flush()
        return cues
    }

    fun serializeSegmentsToJson(segments: List<TranscriptSegment>): String {
        val arr = JSONArray()
        segments.forEach { seg ->
            val obj = JSONObject().apply {
                put("id", seg.id)
                put("index", seg.index)
                put("startMs", seg.startMs)
                put("endMs", seg.endMs)
                put("text", seg.text)
                put("confidence", seg.confidence.toDouble())
                val wordsArr = JSONArray()
                seg.words.forEach { w ->
                    wordsArr.put(
                        JSONObject().apply {
                            put("word", w.word)
                            put("startMs", w.startMs)
                            put("endMs", w.endMs)
                            put("confidence", w.confidence.toDouble())
                        }
                    )
                }
                put("words", wordsArr)
            }
            arr.put(obj)
        }
        return arr.toString()
    }

    fun deserializeSegmentsFromJson(raw: String): List<TranscriptSegment> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            List(arr.length()) { i ->
                val obj = arr.getJSONObject(i)
                val wordsArr = obj.optJSONArray("words")
                val words = if (wordsArr != null) {
                    List(wordsArr.length()) { j ->
                        val wo = wordsArr.getJSONObject(j)
                        WordTiming(
                            word = wo.optString("word", ""),
                            startMs = wo.optLong("startMs", 0L),
                            endMs = wo.optLong("endMs", 0L),
                            confidence = wo.optDouble("confidence", 1.0).toFloat(),
                        )
                    }
                } else emptyList()
                TranscriptSegment(
                    id = obj.optString("id", UUID.randomUUID().toString()),
                    index = obj.optInt("index", i + 1),
                    startMs = obj.optLong("startMs", 0L),
                    endMs = obj.optLong("endMs", 0L),
                    text = obj.optString("text", ""),
                    confidence = obj.optDouble("confidence", 1.0).toFloat(),
                    words = words,
                )
            }
        }.getOrDefault(emptyList())
    }

    fun serializeWaveform(bars: List<Float>): String {
        val arr = JSONArray()
        bars.forEach { arr.put(it.toDouble()) }
        return arr.toString()
    }

    fun deserializeWaveform(raw: String): List<Float> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            List(arr.length()) { i -> arr.optDouble(i, 0.1).toFloat() }
        }.getOrDefault(emptyList())
    }
}
