package com.example

import com.example.data.audio.TranscriptFormatter
import com.example.domain.model.TranscriptSegment
import com.example.domain.model.TranscriptionJob
import com.example.domain.model.WordTiming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TranscriptFormatterTest {

    @Test
    fun formatSrtTimestamp_formatsMillisecondsAccurately() {
        assertEquals("00:00:00,000", TranscriptFormatter.formatSrtTimestamp(0L))
        assertEquals("00:00:04,350", TranscriptFormatter.formatSrtTimestamp(4350L))
        assertEquals("01:02:03,009", TranscriptFormatter.formatSrtTimestamp(3_723_009L))
    }

    @Test
    fun buildCuesFromWords_splitsOnPauseAndCharBudget() {
        val words = listOf(
            WordTiming("Hello", 100L, 400L, 0.98f),
            WordTiming("world", 420L, 800L, 0.96f),
            WordTiming("স্বাগতম", 2000L, 2500L, 0.95f),
            WordTiming("नमस्ते", 2600L, 3100L, 0.94f),
        )
        val cues = TranscriptFormatter.buildCuesFromWords(words, maxCharsPerCue = 42, pauseThresholdMs = 650L)
        assertEquals(2, cues.size)
        assertEquals("Hello world", cues[0].text)
        assertEquals(100L, cues[0].startMs)
        assertEquals(800L, cues[0].endMs)
        assertEquals("স্বাগতম नमस्ते", cues[1].text)
    }

    @Test
    fun formatSrtJsonAndTxt_producesValidMultilingualOutputs() {
        val segments = listOf(
            TranscriptSegment("1", 1, 500L, 2400L, "আমার সোনার বাংলা", 0.95f),
            TranscriptSegment("2", 2, 2600L, 5100L, "Hello and नमस्ते", 0.93f),
        )

        val srt = TranscriptFormatter.formatSrt(segments)
        assertTrue(srt.contains("1\n00:00:00,500 --> 00:00:02,400\nআমার সোনার বাংলা"))
        assertTrue(srt.contains("2\n00:00:02,600 --> 00:00:05,100\nHello and नमस्ते"))

        val txtWithTs = TranscriptFormatter.formatTxt(segments, includeTimestamps = true)
        assertTrue(txtWithTs.contains("[00:00 - 00:02] আমার সোনার বাংলা"))

        val txtPlain = TranscriptFormatter.formatTxt(segments, includeTimestamps = false)
        assertEquals("আমার সোনার বাংলা Hello and नमस्ते", txtPlain)

        val job = TranscriptionJob(
            id = 1L,
            fileName = "test.mp3",
            languageId = "bn-bd",
            languageLabel = "Bangla (BD)",
            engineUsed = "Vosk Offline",
            durationMs = 5100L,
            createdAtMs = 1700000000000L,
            segments = segments,
        )
        val json = TranscriptFormatter.formatJson(job)
        assertTrue(json.contains("\"privacy_mode\": \"strictly_on_device\""))
        assertTrue(json.contains("আমার সোনার বাংলা"))
        assertTrue(json.contains("Hello and नमस्ते"))
    }

    @Test
    fun serializeAndDeserializeSegments_roundTripsCleanly() {
        val segments = listOf(
            TranscriptSegment(
                id = "seg-1",
                index = 1,
                startMs = 1200L,
                endMs = 3400L,
                text = "Testing offline বাংলা हिन्दी",
                confidence = 0.91f,
                words = listOf(WordTiming("Testing", 1200L, 1600L, 0.91f)),
            )
        )
        val raw = TranscriptFormatter.serializeSegmentsToJson(segments)
        val restored = TranscriptFormatter.deserializeSegmentsFromJson(raw)
        assertEquals(1, restored.size)
        assertEquals("Testing offline বাংলা हिन्दी", restored[0].text)
        assertEquals(1, restored[0].words.size)
    }
}
