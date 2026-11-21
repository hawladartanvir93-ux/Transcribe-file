package com.example.data.repository

import android.content.Context
import android.net.Uri
import com.example.data.asr.AndroidOnDeviceAsrEngine
import com.example.data.asr.ModelInstallProgress
import com.example.data.asr.OfflineModelManager
import com.example.data.asr.OnDeviceSpeechUnavailableException
import com.example.data.asr.VoskTranscriptionEngine
import com.example.data.audio.AudioDecoder
import com.example.data.audio.TranscriptFormatter
import com.example.data.local.TranscriptionDao
import com.example.data.local.TranscriptionEntity
import com.example.domain.model.AsrEngineMode
import com.example.domain.model.AudioFileMetadata
import com.example.domain.model.LanguageOption
import com.example.domain.model.OfflineModelInfo
import com.example.domain.model.TranscriptSegment
import com.example.domain.model.TranscriptionJob
import com.example.domain.model.TranscriptionPipelinePhase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.io.File

class TranscriptionRepository(
    private val context: Context,
    private val dao: TranscriptionDao,
    val modelManager: OfflineModelManager,
) {
    private val audioDecoder = AudioDecoder(context)
    private val voskEngine = VoskTranscriptionEngine()
    private val systemAsrEngine = AndroidOnDeviceAsrEngine(context)

    val allModels: Flow<List<OfflineModelInfo>> = modelManager.modelsFlow

    val historyJobs: Flow<List<TranscriptionJob>> = dao.observeAll().map { list ->
        list.map { it.toDomain() }
    }

    fun isSystemOnDeviceAsrAvailable(): Boolean = systemAsrEngine.isAvailable()

    suspend fun inspectAudio(uri: Uri): AudioFileMetadata = audioDecoder.inspectAudioUri(uri)

    fun transcribeAudio(
        audioMetadata: AudioFileMetadata,
        language: LanguageOption,
        customBcp47: String,
        engineMode: AsrEngineMode,
        maxCharsPerCue: Int,
    ): Flow<Pair<TranscriptionPipelinePhase, List<Float>?>> = kotlinx.coroutines.flow.channelFlow {
        val uri = Uri.parse(audioMetadata.uriString)
        val activeBcp47 = customBcp47.takeIf { it.isNotBlank() } ?: language.bcp47Tag
        val activeLangId = if (customBcp47.isNotBlank()) "custom" else language.id
        val activeLangLabel = if (customBcp47.isNotBlank()) "Custom ($customBcp47)" else "${language.name} • ${language.nativeName}"

        var pcmFile: File? = null
        try {
            send(TranscriptionPipelinePhase.DecodingAudio(0.04f, "Decoding ${audioMetadata.displayName} to 16 kHz Mono PCM...") to null)
            val decoded = audioDecoder.decodeTo16kMonoPcm(uri) { p ->
                trySend(
                    TranscriptionPipelinePhase.DecodingAudio(
                        progress = p,
                        message = "Decoding audio stream on-device (${(p * 100).toInt()}%)...",
                    ) to null
                )
            }
            pcmFile = decoded.pcmFile
            send(TranscriptionPipelinePhase.DecodingAudio(1f, "Audio decoded (${decoded.durationMs / 1000}s)") to decoded.waveformBars)

            val installedVoskDir = modelManager.resolveInstalledModelDir(activeLangId)
            val useVosk: Boolean
            val resolvedEngineLabel: String

            when (engineMode) {
                AsrEngineMode.VOSK_OFFLINE -> {
                    if (installedVoskDir == null) {
                        throw OnDeviceSpeechUnavailableException(
                            message = "No offline Vosk model installed for ${language.name}.",
                            suggestion = "Open the Models tab to download the ${language.name} model or import a local Vosk .zip archive.",
                        )
                    }
                    useVosk = true
                    resolvedEngineLabel = "Vosk Offline (${installedVoskDir.name})"
                }
                AsrEngineMode.SYSTEM_ON_DEVICE -> {
                    useVosk = false
                    resolvedEngineLabel = "Android On-Device ASR ($activeBcp47)"
                }
                AsrEngineMode.AUTO -> {
                    if (installedVoskDir != null) {
                        useVosk = true
                        resolvedEngineLabel = "Vosk Offline (${installedVoskDir.name})"
                    } else if (systemAsrEngine.isAvailable()) {
                        useVosk = false
                        resolvedEngineLabel = "Android On-Device ASR ($activeBcp47)"
                    } else {
                        throw OnDeviceSpeechUnavailableException(
                            message = "No offline model is ready for ${language.name} on this device.",
                            suggestion = "Open the Models tab to download a Vosk offline model (English/Hindi/etc.) or import a local Vosk .zip model.",
                        )
                    }
                }
            }

            send(
                TranscriptionPipelinePhase.Transcribing(
                    progress = 0.02f,
                    engineLabel = resolvedEngineLabel,
                    partialText = "",
                    completedCues = 0,
                ) to decoded.waveformBars
            )

            val segments = if (useVosk && installedVoskDir != null) {
                voskEngine.transcribePcm(
                    pcmFile = decoded.pcmFile,
                    modelDir = installedVoskDir,
                    maxCharsPerCue = maxCharsPerCue,
                ) { progress, partial, cues ->
                    trySend(
                        TranscriptionPipelinePhase.Transcribing(
                            progress = progress,
                            engineLabel = resolvedEngineLabel,
                            partialText = partial,
                            completedCues = cues,
                        ) to decoded.waveformBars
                    )
                }
            } else {
                systemAsrEngine.transcribePcm(
                    pcmFile = decoded.pcmFile,
                    durationMs = decoded.durationMs,
                    bcp47LanguageTag = activeBcp47,
                    audioDecoder = audioDecoder,
                ) { progress, partial, cues ->
                    trySend(
                        TranscriptionPipelinePhase.Transcribing(
                            progress = progress,
                            engineLabel = resolvedEngineLabel,
                            partialText = partial,
                            completedCues = cues,
                        ) to decoded.waveformBars
                    )
                }
            }

            if (segments.isEmpty()) {
                send(
                    TranscriptionPipelinePhase.Failed(
                        errorMessage = "No recognizable speech detected in '${audioMetadata.displayName}'.",
                        suggestion = "Verify the selected language ($activeLangLabel) matches the spoken audio, or check that the audio is not silent.",
                    ) to decoded.waveformBars
                )
                return@channelFlow
            }

            val job = TranscriptionJob(
                fileName = audioMetadata.displayName,
                languageId = activeLangId,
                languageLabel = activeLangLabel,
                engineUsed = resolvedEngineLabel,
                durationMs = decoded.durationMs,
                createdAtMs = System.currentTimeMillis(),
                segments = segments,
                waveformBars = decoded.waveformBars,
            )
            val insertedId = dao.insert(job.toEntity())
            send(TranscriptionPipelinePhase.Completed(job.copy(id = insertedId)) to decoded.waveformBars)
        } catch (e: OnDeviceSpeechUnavailableException) {
            send(TranscriptionPipelinePhase.Failed(e.message ?: "Offline engine unavailable", e.suggestion) to null)
        } catch (t: Throwable) {
            send(
                TranscriptionPipelinePhase.Failed(
                    errorMessage = t.message ?: "On-device audio processing failed.",
                    suggestion = "Ensure the file is a valid MP3, WAV, M4A, OGG, or FLAC audio track.",
                ) to null
            )
        } finally {
            pcmFile?.delete()
        }
    }

    suspend fun updateJobSegments(jobId: Long, segments: List<TranscriptSegment>) {
        if (jobId <= 0L) return
        val segmentsJson = TranscriptFormatter.serializeSegmentsToJson(segments)
        val preview = segments.joinToString(" ") { it.text.trim() }.take(180)
        dao.updateSegments(jobId, segmentsJson, preview)
    }

    suspend fun deleteHistoryJob(jobId: Long) = dao.deleteById(jobId)

    suspend fun wipeAllHistory() = dao.deleteAll()

    fun downloadModel(modelId: String): Flow<ModelInstallProgress> = modelManager.downloadModel(modelId)

    fun importLocalModelZip(
        uri: Uri,
        languageId: String,
        languageName: String,
        nativeName: String,
    ): Flow<ModelInstallProgress> = modelManager.importLocalModelZip(uri, languageId, languageName, nativeName)

    suspend fun deleteModel(modelId: String) = modelManager.deleteModel(modelId)

    private fun TranscriptionJob.toEntity(): TranscriptionEntity = TranscriptionEntity(
        id = id,
        fileName = fileName,
        languageId = languageId,
        languageLabel = languageLabel,
        engineUsed = engineUsed,
        durationMs = durationMs,
        createdAtMs = createdAtMs,
        segmentsJson = TranscriptFormatter.serializeSegmentsToJson(segments),
        waveformJson = TranscriptFormatter.serializeWaveform(waveformBars),
        fullTextPreview = segments.joinToString(" ") { it.text.trim() }.take(180),
    )

    private fun TranscriptionEntity.toDomain(): TranscriptionJob = TranscriptionJob(
        id = id,
        fileName = fileName,
        languageId = languageId,
        languageLabel = languageLabel,
        engineUsed = engineUsed,
        durationMs = durationMs,
        createdAtMs = createdAtMs,
        segments = TranscriptFormatter.deserializeSegmentsFromJson(segmentsJson),
        waveformBars = TranscriptFormatter.deserializeWaveform(waveformJson),
    )
}
