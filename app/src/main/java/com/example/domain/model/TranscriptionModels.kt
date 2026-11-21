package com.example.domain.model

enum class ExportFormat(val extension: String, val mimeType: String, val label: String) {
    SRT("srt", "application/x-subrip", "SRT"),
    JSON("json", "application/json", "JSON"),
    TXT("txt", "text/plain", "TXT"),
}

enum class AsrEngineMode(val displayName: String, val description: String) {
    AUTO("Smart Auto", "Uses Vosk offline model if installed, or Android On-Device ASR (API 33+)"),
    VOSK_OFFLINE("Vosk Offline Model", "Local Kaldi neural model with word-level timestamps"),
    SYSTEM_ON_DEVICE("Android On-Device ASR", "System offline speech packs (API 33+, great for Bangla/Hindi)"),
}

data class LanguageOption(
    val id: String,
    val name: String,
    val nativeName: String,
    val bcp47Tag: String,
    val shortCode: String,
    val voskModelId: String?,
)

object SupportedLanguages {
    val featured: List<LanguageOption> = listOf(
        LanguageOption(
            id = "bn-bd",
            name = "Bangla (BD)",
            nativeName = "বাংলা (বাংলাদেশ)",
            bcp47Tag = "bn-BD",
            shortCode = "BN",
            voskModelId = "vosk-bn-custom",
        ),
        LanguageOption(
            id = "bn-in",
            name = "Bangla (IN)",
            nativeName = "বাংলা (ভারত)",
            bcp47Tag = "bn-IN",
            shortCode = "BN",
            voskModelId = "vosk-bn-custom",
        ),
        LanguageOption(
            id = "en-us",
            name = "English (US)",
            nativeName = "English (US)",
            bcp47Tag = "en-US",
            shortCode = "EN",
            voskModelId = "vosk-model-small-en-us-0.15",
        ),
        LanguageOption(
            id = "en-in",
            name = "English (India)",
            nativeName = "English (India)",
            bcp47Tag = "en-IN",
            shortCode = "EN-IN",
            voskModelId = "vosk-model-small-en-in-0.4",
        ),
        LanguageOption(
            id = "hi-in",
            name = "Hindi",
            nativeName = "हिन्दी",
            bcp47Tag = "hi-IN",
            shortCode = "HI",
            voskModelId = "vosk-model-small-hi-0.22",
        ),
    )

    val additional: List<LanguageOption> = listOf(
        LanguageOption("es-es", "Spanish", "Español", "es-ES", "ES", "vosk-model-small-es-0.42"),
        LanguageOption("fr-fr", "French", "Français", "fr-FR", "FR", "vosk-model-small-fr-0.22"),
        LanguageOption("de-de", "German", "Deutsch", "de-DE", "DE", "vosk-model-small-de-0.15"),
        LanguageOption("ar-sa", "Arabic", "العربية", "ar-SA", "AR", null),
        LanguageOption("ur-pk", "Urdu", "اردو", "ur-PK", "UR", null),
        LanguageOption("pt-br", "Portuguese", "Português", "pt-BR", "PT", "vosk-model-small-pt-0.3"),
        LanguageOption("ja-jp", "Japanese", "日本語", "ja-JP", "JA", "vosk-model-small-ja-0.22"),
        LanguageOption("ru-ru", "Russian", "Русский", "ru-RU", "RU", "vosk-model-small-ru-0.22"),
    )

    val all: List<LanguageOption> = featured + additional

    fun findById(id: String): LanguageOption =
        all.find { it.id == id } ?: featured.first { it.id == "en-us" }
}

data class WordTiming(
    val word: String,
    val startMs: Long,
    val endMs: Long,
    val confidence: Float = 1.0f,
)

data class TranscriptSegment(
    val id: String,
    val index: Int,
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val confidence: Float = 1.0f,
    val words: List<WordTiming> = emptyList(),
)

data class AudioFileMetadata(
    val uriString: String,
    val displayName: String,
    val fileSizeBytes: Long,
    val durationMs: Long,
    val sampleRateHz: Int,
    val channelCount: Int,
    val mimeType: String,
    val waveformBars: List<Float> = emptyList(),
)

data class OfflineModelInfo(
    val id: String,
    val languageId: String,
    val languageName: String,
    val nativeName: String,
    val modelDisplayName: String,
    val sizeMb: Int,
    val downloadUrl: String?,
    val notes: String,
    val isInstalled: Boolean = false,
    val installedPath: String? = null,
    val isCustomImported: Boolean = false,
)

data class TranscriptionJob(
    val id: Long = 0L,
    val fileName: String,
    val languageId: String,
    val languageLabel: String,
    val engineUsed: String,
    val durationMs: Long,
    val createdAtMs: Long,
    val segments: List<TranscriptSegment>,
    val waveformBars: List<Float> = emptyList(),
)

sealed interface TranscriptionPipelinePhase {
    data object Idle : TranscriptionPipelinePhase
    data class DecodingAudio(val progress: Float, val message: String) : TranscriptionPipelinePhase
    data class Transcribing(
        val progress: Float,
        val engineLabel: String,
        val partialText: String,
        val completedCues: Int,
    ) : TranscriptionPipelinePhase
    data class Completed(val job: TranscriptionJob) : TranscriptionPipelinePhase
    data class Failed(val errorMessage: String, val suggestion: String? = null) : TranscriptionPipelinePhase
}
