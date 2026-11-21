package com.example.ui

import android.app.Application
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.asr.ModelInstallProgress
import com.example.data.asr.OfflineModelManager
import com.example.data.audio.TranscriptFormatter
import com.example.data.auth.AuthResult
import com.example.data.auth.VaultAuthRepository
import com.example.data.auth.VaultAuthSnapshot
import com.example.data.local.UserPreferencesRepository
import com.example.data.local.UserSettings
import com.example.data.local.VaultDatabase
import com.example.data.repository.TranscriptionRepository
import com.example.domain.model.AsrEngineMode
import com.example.domain.model.AudioFileMetadata
import com.example.domain.model.ExportFormat
import com.example.domain.model.LanguageOption
import com.example.domain.model.OfflineModelInfo
import com.example.domain.model.SupportedLanguages
import com.example.domain.model.TranscriptSegment
import com.example.domain.model.TranscriptionJob
import com.example.domain.model.TranscriptionPipelinePhase
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class AudioPlaybackState(
    val isPrepared: Boolean = false,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
)

sealed interface UiOneShotEvent {
    data class ShowMessage(val message: String) : UiOneShotEvent
    data object NavigateToTranscript : UiOneShotEvent
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = VaultDatabase.getInstance(application)
    private val modelManager = OfflineModelManager(application)
    private val prefsRepo = UserPreferencesRepository(application)
    private val authRepo = VaultAuthRepository.getInstance(application)
    private val repository = TranscriptionRepository(
        context = application,
        dao = database.transcriptionDao(),
        modelManager = modelManager,
    )

    val authSnapshot: StateFlow<VaultAuthSnapshot> = authRepo.authStateFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = VaultAuthSnapshot(),
    )

    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    private val _isAuthSubmitting = MutableStateFlow(false)
    val isAuthSubmitting: StateFlow<Boolean> = _isAuthSubmitting.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    val settings: StateFlow<UserSettings> = prefsRepo.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = UserSettings(),
    )

    val models: StateFlow<List<OfflineModelInfo>> = repository.allModels.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
    )

    val historyJobs: StateFlow<List<TranscriptionJob>> = repository.historyJobs.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
    )

    private val _modelProgress = MutableStateFlow<Map<String, ModelInstallProgress>>(emptyMap())
    val modelProgress: StateFlow<Map<String, ModelInstallProgress>> = _modelProgress.asStateFlow()

    private val _loadedAudio = MutableStateFlow<AudioFileMetadata?>(null)
    val loadedAudio: StateFlow<AudioFileMetadata?> = _loadedAudio.asStateFlow()

    private val _pipelinePhase = MutableStateFlow<TranscriptionPipelinePhase>(TranscriptionPipelinePhase.Idle)
    val pipelinePhase: StateFlow<TranscriptionPipelinePhase> = _pipelinePhase.asStateFlow()

    private val _activeJob = MutableStateFlow<TranscriptionJob?>(null)
    val activeJob: StateFlow<TranscriptionJob?> = _activeJob.asStateFlow()

    private val _selectedExportFormat = MutableStateFlow(ExportFormat.SRT)
    val selectedExportFormat: StateFlow<ExportFormat> = _selectedExportFormat.asStateFlow()

    private val _isDragOver = MutableStateFlow(false)
    val isDragOver: StateFlow<Boolean> = _isDragOver.asStateFlow()

    private val _playbackState = MutableStateFlow(AudioPlaybackState())
    val playbackState: StateFlow<AudioPlaybackState> = _playbackState.asStateFlow()

    private val _events = MutableSharedFlow<UiOneShotEvent>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    val isSystemAsrAvailable: Boolean = repository.isSystemOnDeviceAsrAvailable()

    private var activeTranscriptionTask: Job? = null
    private var mediaPlayer: MediaPlayer? = null
    private var playbackTickerJob: Job? = null
    private var pendingAudioUri: Uri? = null
    private var suppressNextAutoLock: Boolean = false

    fun suppressAutoLockForExternalPicker() {
        suppressNextAutoLock = true
    }

    fun clearExternalPickerSuppression() {
        suppressNextAutoLock = false
    }

    fun setDragOver(over: Boolean) {
        _isDragOver.value = over
    }

    fun showMessage(message: String) {
        _events.tryEmit(UiOneShotEvent.ShowMessage(message))
    }

    fun clearAuthError() {
        _authError.value = null
    }

    fun registerAccount(number: String, password: String, confirmPassword: String) {
        if (_isAuthSubmitting.value) return
        viewModelScope.launch {
            _isAuthSubmitting.value = true
            _authError.value = null
            when (val res = withContext(Dispatchers.Default) { authRepo.register(number, password, confirmPassword) }) {
                AuthResult.Success -> {
                    _isUnlocked.value = true
                    flushPendingAudioIfAny()
                }
                is AuthResult.Error -> _authError.value = res.message
            }
            _isAuthSubmitting.value = false
        }
    }

    fun login(number: String, password: String) {
        if (_isAuthSubmitting.value) return
        viewModelScope.launch {
            _isAuthSubmitting.value = true
            _authError.value = null
            when (val res = withContext(Dispatchers.Default) { authRepo.login(number, password) }) {
                AuthResult.Success -> {
                    _isUnlocked.value = true
                    flushPendingAudioIfAny()
                }
                is AuthResult.Error -> _authError.value = res.message
            }
            _isAuthSubmitting.value = false
        }
    }

    fun lockVault() {
        releaseMediaPlayer()
        _isUnlocked.value = false
        _authError.value = null
    }

    fun onAppBackgrounded(isChangingConfigurations: Boolean = false) {
        if (isChangingConfigurations) return
        if (suppressNextAutoLock) {
            suppressNextAutoLock = false
            return
        }
        if (settings.value.autoLockOnBackground && authSnapshot.value.isAccountRegistered) {
            lockVault()
        }
    }

    fun setAutoLockOnBackground(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setAutoLockOnBackground(enabled) }
    }

    fun updateCredentials(
        currentPassword: String,
        newNumber: String,
        newPassword: String,
        confirmPassword: String,
        onResult: (AuthResult) -> Unit,
    ) {
        viewModelScope.launch {
            val res = withContext(Dispatchers.Default) {
                authRepo.updateCredentials(currentPassword, newNumber, newPassword, confirmPassword)
            }
            onResult(res)
            if (res is AuthResult.Success) {
                _events.tryEmit(UiOneShotEvent.ShowMessage("Vault number & password updated"))
            }
        }
    }

    fun resetAccountAndWipeVault() {
        viewModelScope.launch {
            cancelTranscription()
            releaseMediaPlayer()
            _loadedAudio.value = null
            _activeJob.value = null
            _pipelinePhase.value = TranscriptionPipelinePhase.Idle
            repository.wipeAllHistory()
            authRepo.resetAccount()
            _authError.value = null
            _isUnlocked.value = false
        }
    }

    private fun flushPendingAudioIfAny() {
        val pending = pendingAudioUri ?: return
        pendingAudioUri = null
        onAudioFileSelected(pending)
    }

    fun onAudioFileSelected(uri: Uri) {
        if (!_isUnlocked.value) {
            pendingAudioUri = uri
            return
        }
        viewModelScope.launch {
            runCatching {
                getApplication<Application>().contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            try {
                val meta = repository.inspectAudio(uri)
                _loadedAudio.value = meta
                _pipelinePhase.value = TranscriptionPipelinePhase.Idle
                prepareMediaPlayer(uri, meta.durationMs)
            } catch (t: Throwable) {
                _events.tryEmit(UiOneShotEvent.ShowMessage("Could not read audio file: ${t.message}"))
            }
        }
    }

    fun clearLoadedAudio() {
        cancelTranscription()
        releaseMediaPlayer()
        _loadedAudio.value = null
        _pipelinePhase.value = TranscriptionPipelinePhase.Idle
    }

    fun startTranscription() {
        val audio = _loadedAudio.value ?: return
        if (activeTranscriptionTask?.isActive == true) return

        val currentSettings = settings.value
        val language = SupportedLanguages.findById(currentSettings.selectedLanguageId)

        activeTranscriptionTask = viewModelScope.launch {
            repository.transcribeAudio(
                audioMetadata = audio,
                language = language,
                customBcp47 = currentSettings.customBcp47Tag,
                engineMode = currentSettings.selectedEngineMode,
                maxCharsPerCue = currentSettings.maxCueChars,
            ).collect { (phase, waveform) ->
                if (waveform != null && waveform.isNotEmpty()) {
                    _loadedAudio.update { it?.copy(waveformBars = waveform) }
                }
                _pipelinePhase.value = phase
                if (phase is TranscriptionPipelinePhase.Completed) {
                    _activeJob.value = phase.job
                    _events.tryEmit(UiOneShotEvent.ShowMessage("Offline transcription complete (${phase.job.segments.size} cues)"))
                    _events.tryEmit(UiOneShotEvent.NavigateToTranscript)
                }
            }
        }
    }

    fun cancelTranscription() {
        activeTranscriptionTask?.cancel()
        activeTranscriptionTask = null
        _pipelinePhase.value = TranscriptionPipelinePhase.Idle
    }

    fun selectLanguage(languageId: String) {
        viewModelScope.launch {
            prefsRepo.setSelectedLanguage(languageId)
            prefsRepo.setCustomBcp47("")
        }
    }

    fun setCustomBcp47Tag(tag: String) {
        viewModelScope.launch { prefsRepo.setCustomBcp47(tag) }
    }

    fun selectEngineMode(mode: AsrEngineMode) {
        viewModelScope.launch { prefsRepo.setEngineMode(mode) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { prefsRepo.setThemeMode(mode) }
    }

    fun setUseDynamicColor(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setUseDynamicColor(enabled) }
    }

    fun setIncludeTimestampsInTxt(include: Boolean) {
        viewModelScope.launch { prefsRepo.setIncludeTimestampsInTxt(include) }
    }

    fun setMaxCueChars(maxChars: Int) {
        viewModelScope.launch { prefsRepo.setMaxCueChars(maxChars) }
    }

    fun selectExportFormat(format: ExportFormat) {
        _selectedExportFormat.value = format
    }

    fun updateCueText(segmentId: String, newText: String) {
        val current = _activeJob.value ?: return
        val updatedSegments = current.segments.map { seg ->
            if (seg.id == segmentId) seg.copy(text = newText) else seg
        }
        val updatedJob = current.copy(segments = updatedSegments)
        _activeJob.value = updatedJob
        viewModelScope.launch {
            repository.updateJobSegments(updatedJob.id, updatedSegments)
        }
    }

    fun shiftCueTiming(segmentId: String, startDeltaMs: Long, endDeltaMs: Long) {
        val current = _activeJob.value ?: return
        val updatedSegments = current.segments.map { seg ->
            if (seg.id == segmentId) {
                val newStart = (seg.startMs + startDeltaMs).coerceAtLeast(0L)
                val newEnd = (seg.endMs + endDeltaMs).coerceAtLeast(newStart + 200L)
                seg.copy(startMs = newStart, endMs = newEnd)
            } else seg
        }
        val updatedJob = current.copy(segments = updatedSegments)
        _activeJob.value = updatedJob
        viewModelScope.launch {
            repository.updateJobSegments(updatedJob.id, updatedSegments)
        }
    }

    fun deleteCue(segmentId: String) {
        val current = _activeJob.value ?: return
        val updatedSegments = current.segments
            .filterNot { it.id == segmentId }
            .mapIndexed { idx, seg -> seg.copy(index = idx + 1) }
        val updatedJob = current.copy(segments = updatedSegments)
        _activeJob.value = updatedJob
        viewModelScope.launch {
            repository.updateJobSegments(updatedJob.id, updatedSegments)
        }
    }

    fun buildExportContent(format: ExportFormat): String {
        val job = _activeJob.value ?: return ""
        return when (format) {
            ExportFormat.SRT -> TranscriptFormatter.formatSrt(job.segments)
            ExportFormat.JSON -> TranscriptFormatter.formatJson(job)
            ExportFormat.TXT -> TranscriptFormatter.formatTxt(
                segments = job.segments,
                includeTimestamps = settings.value.includeTimestampsInTxt,
            )
        }
    }

    fun suggestedExportFileName(format: ExportFormat): String {
        val base = _activeJob.value?.fileName
            ?.substringBeforeLast('.')
            ?.replace(Regex("[^A-Za-z0-9._-]"), "_")
            ?.takeIf { it.isNotBlank() }
            ?: "transcript"
        return "${base}.${format.extension}"
    }

    fun saveExportToUri(uri: Uri, format: ExportFormat) {
        val content = buildExportContent(format)
        if (content.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                getApplication<Application>().contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(content.toByteArray(Charsets.UTF_8))
                    out.flush()
                }
                _events.tryEmit(UiOneShotEvent.ShowMessage("Saved ${suggestedExportFileName(format)} on-device"))
            } catch (t: Throwable) {
                _events.tryEmit(UiOneShotEvent.ShowMessage("Save failed: ${t.message}"))
            }
        }
    }

    suspend fun createShareFileUri(format: ExportFormat): Uri? = withContext(Dispatchers.IO) {
        val content = buildExportContent(format)
        if (content.isBlank()) return@withContext null
        val app = getApplication<Application>()
        val exportDir = File(app.cacheDir, "exports").apply { mkdirs() }
        val outFile = File(exportDir, suggestedExportFileName(format))
        outFile.writeText(content, Charsets.UTF_8)
        FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", outFile)
    }

    fun downloadModel(modelId: String) {
        if (_modelProgress.value[modelId]?.let { !it.isDone && it.error == null } == true) return
        viewModelScope.launch {
            repository.downloadModel(modelId).collect { progress ->
                _modelProgress.update { it + (modelId to progress) }
                if (progress.isDone) {
                    _events.tryEmit(UiOneShotEvent.ShowMessage("Offline model ready: $modelId"))
                } else if (progress.error != null) {
                    _events.tryEmit(UiOneShotEvent.ShowMessage("Model error: ${progress.error}"))
                }
            }
        }
    }

    fun importLocalModelZip(uri: Uri, languageOption: LanguageOption) {
        viewModelScope.launch {
            repository.importLocalModelZip(
                uri = uri,
                languageId = languageOption.id,
                languageName = languageOption.name,
                nativeName = languageOption.nativeName,
            ).collect { progress ->
                _modelProgress.update { it + ("import_local" to progress) }
                if (progress.isDone) {
                    _events.tryEmit(UiOneShotEvent.ShowMessage("Imported offline ${languageOption.name} model"))
                } else if (progress.error != null) {
                    _events.tryEmit(UiOneShotEvent.ShowMessage("Import failed: ${progress.error}"))
                }
            }
        }
    }

    fun deleteModel(modelId: String) {
        viewModelScope.launch {
            repository.deleteModel(modelId)
            _modelProgress.update { it - modelId }
            _events.tryEmit(UiOneShotEvent.ShowMessage("Removed local model"))
        }
    }

    fun openHistoryJob(job: TranscriptionJob) {
        _activeJob.value = job
        _events.tryEmit(UiOneShotEvent.NavigateToTranscript)
    }

    fun deleteHistoryJob(jobId: Long) {
        viewModelScope.launch {
            repository.deleteHistoryJob(jobId)
            if (_activeJob.value?.id == jobId) {
                _activeJob.value = null
            }
        }
    }

    fun wipeAllHistory() {
        viewModelScope.launch {
            repository.wipeAllHistory()
            _activeJob.value = null
            _events.tryEmit(UiOneShotEvent.ShowMessage("All local transcriptions wiped"))
        }
    }

    fun togglePlayPause() {
        val player = mediaPlayer ?: return
        if (!_playbackState.value.isPrepared) return
        if (player.isPlaying) {
            player.pause()
            playbackTickerJob?.cancel()
            _playbackState.update { it.copy(isPlaying = false, positionMs = player.currentPosition.toLong()) }
        } else {
            player.start()
            _playbackState.update { it.copy(isPlaying = true) }
            startPlaybackTicker()
        }
    }

    fun seekTo(ms: Long) {
        val player = mediaPlayer ?: return
        if (!_playbackState.value.isPrepared) return
        val clamped = ms.coerceIn(0L, _playbackState.value.durationMs.coerceAtLeast(1L))
        player.seekTo(clamped.toInt())
        _playbackState.update { it.copy(positionMs = clamped) }
    }

    private fun prepareMediaPlayer(uri: Uri, fallbackDurationMs: Long) {
        releaseMediaPlayer()
        try {
            val player = MediaPlayer()
            player.setDataSource(getApplication(), uri)
            player.setOnPreparedListener { mp ->
                _playbackState.value = AudioPlaybackState(
                    isPrepared = true,
                    isPlaying = false,
                    positionMs = 0L,
                    durationMs = mp.duration.toLong().coerceAtLeast(fallbackDurationMs),
                )
            }
            player.setOnCompletionListener {
                playbackTickerJob?.cancel()
                _playbackState.update { it.copy(isPlaying = false, positionMs = 0L) }
            }
            player.setOnErrorListener { _, _, _ ->
                _playbackState.value = AudioPlaybackState()
                true
            }
            player.prepareAsync()
            mediaPlayer = player
        } catch (_: Throwable) {
            _playbackState.value = AudioPlaybackState()
        }
    }

    private fun startPlaybackTicker() {
        playbackTickerJob?.cancel()
        playbackTickerJob = viewModelScope.launch {
            while (isActive) {
                val player = mediaPlayer
                if (player != null && player.isPlaying) {
                    _playbackState.update { it.copy(positionMs = player.currentPosition.toLong()) }
                } else {
                    break
                }
                delay(120L)
            }
        }
    }

    private fun releaseMediaPlayer() {
        playbackTickerJob?.cancel()
        runCatching { mediaPlayer?.stop() }
        runCatching { mediaPlayer?.release() }
        mediaPlayer = null
        _playbackState.value = AudioPlaybackState()
    }

    override fun onCleared() {
        super.onCleared()
        activeTranscriptionTask?.cancel()
        releaseMediaPlayer()
    }
}
