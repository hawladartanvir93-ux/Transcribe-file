package com.example.ui.screens

import android.app.Activity
import android.content.ClipDescription
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.audio.TranscriptFormatter
import com.example.data.local.UserSettings
import com.example.domain.model.AsrEngineMode
import com.example.domain.model.AudioFileMetadata
import com.example.domain.model.LanguageOption
import com.example.domain.model.OfflineModelInfo
import com.example.domain.model.SupportedLanguages
import com.example.domain.model.TranscriptionPipelinePhase
import com.example.ui.AudioPlaybackState
import com.example.ui.components.PrivacyShieldBadge
import com.example.ui.components.SpecBadge
import com.example.ui.components.StatusDotLabel
import com.example.ui.components.WaveformVisualizer

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StudioScreen(
    settings: UserSettings,
    models: List<OfflineModelInfo>,
    loadedAudio: AudioFileMetadata?,
    pipelinePhase: TranscriptionPipelinePhase,
    playbackState: AudioPlaybackState,
    isDragOver: Boolean,
    isSystemAsrAvailable: Boolean,
    onSetDragOver: (Boolean) -> Unit,
    onAudioUriReceived: (Uri) -> Unit,
    onClearAudio: () -> Unit,
    onSelectLanguage: (String) -> Unit,
    onSetCustomBcp47: (String) -> Unit,
    onSelectEngineMode: (AsrEngineMode) -> Unit,
    onStartTranscription: () -> Unit,
    onCancelTranscription: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onOpenModelsTab: () -> Unit,
    onOpenTranscriptTab: () -> Unit,
    onPrepareExternalPicker: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    var showLanguageSheet by remember { mutableStateOf(false) }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let { onAudioUriReceived(it) }
    }

    val dragAndDropCallback = remember(activity) {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) {
                onSetDragOver(true)
            }

            override fun onExited(event: DragAndDropEvent) {
                onSetDragOver(false)
            }

            override fun onEnded(event: DragAndDropEvent) {
                onSetDragOver(false)
            }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                onSetDragOver(false)
                val androidEvent = event.toAndroidDragEvent()
                val permission = activity?.requestDragAndDropPermissions(androidEvent)
                val clipData = androidEvent.clipData
                var handled = false
                if (clipData != null && clipData.itemCount > 0) {
                    val droppedUri = clipData.getItemAt(0).uri
                    if (droppedUri != null) {
                        onAudioUriReceived(droppedUri)
                        handled = true
                    }
                }
                permission?.release()
                return handled
            }
        }
    }

    val activeLanguage = SupportedLanguages.findById(settings.selectedLanguageId)
    val installedVoskForLang = models.any {
        it.isInstalled && (it.languageId == activeLanguage.id ||
            (activeLanguage.id.startsWith("bn") && it.languageId.startsWith("bn")))
    }

    fun openFilePicker() {
        onPrepareExternalPicker()
        audioPickerLauncher.launch(
            arrayOf(
                "audio/*",
                "application/ogg",
                "application/octet-stream",
                "video/mp4",
            )
        )
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyUp && keyEvent.isCtrlPressed) {
                    when (keyEvent.key) {
                        Key.O -> {
                            openFilePicker()
                            true
                        }
                        Key.Enter -> {
                            if (loadedAudio != null && pipelinePhase is TranscriptionPipelinePhase.Idle) {
                                onStartTranscription()
                            }
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
            .dragAndDropTarget(
                shouldStartDragAndDrop = { event ->
                    val mimes = event.mimeTypes()
                    mimes.any {
                        it.startsWith("audio/") ||
                            it == "application/ogg" ||
                            it == "application/octet-stream" ||
                            it == ClipDescription.MIMETYPE_TEXT_URILIST ||
                            it == "*/*"
                    }
                },
                target = dragAndDropCallback,
            ),
        contentAlignment = Alignment.TopCenter,
    ) {
        val isDesktopTwoPane = maxWidth >= 900.dp

        @Composable
        fun HeaderSection() {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = if (isDesktopTwoPane) {
                            "MP3 / Audio → SRT • JSON • TXT  •  Ctrl+O Browse • Ctrl+Enter Transcribe"
                        } else {
                            "MP3 / Audio → SRT • JSON • TXT"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                PrivacyShieldBadge()
            }
        }

        @Composable
        fun InputAndPlayerPane() {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                DragAndDropAudioZone(
                    isDragOver = isDragOver,
                    hasAudioLoaded = loadedAudio != null,
                    onBrowseClick = ::openFilePicker,
                )

                AnimatedVisibility(visible = loadedAudio != null) {
                    loadedAudio?.let { audio ->
                        LoadedAudioCard(
                            audio = audio,
                            playbackState = playbackState,
                            isProcessing = pipelinePhase is TranscriptionPipelinePhase.DecodingAudio ||
                                pipelinePhase is TranscriptionPipelinePhase.Transcribing,
                            onClear = onClearAudio,
                            onTogglePlayPause = onTogglePlayPause,
                            onSeekTo = onSeekTo,
                        )
                    }
                }
            }
        }

        @Composable
        fun ConfigAndPipelinePane() {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Translate,
                                    contentDescription = "Language selection",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(
                                    text = stringResource(R.string.language_label),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                            TextButton(
                                onClick = { showLanguageSheet = true },
                                modifier = Modifier.testTag("more_languages_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Language,
                                    contentDescription = "All languages",
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("All Languages")
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SupportedLanguages.featured.forEach { lang ->
                                val selected = settings.customBcp47Tag.isBlank() && settings.selectedLanguageId == lang.id
                                FilterChip(
                                    selected = selected,
                                    onClick = { onSelectLanguage(lang.id) },
                                    label = {
                                        Text(
                                            text = "${lang.name} • ${lang.nativeName}",
                                            maxLines = 1,
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    ),
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("lang_chip_${lang.id}"),
                                )
                            }
                            if (settings.customBcp47Tag.isNotBlank() ||
                                SupportedLanguages.additional.any { it.id == settings.selectedLanguageId }
                            ) {
                                val label = if (settings.customBcp47Tag.isNotBlank()) {
                                    "Custom: ${settings.customBcp47Tag}"
                                } else {
                                    "${activeLanguage.name} • ${activeLanguage.nativeName}"
                                }
                                FilterChip(
                                    selected = true,
                                    onClick = { showLanguageSheet = true },
                                    label = { Text(label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    ),
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Memory,
                                contentDescription = "On-device ASR engine",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = stringResource(R.string.engine_label),
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            AsrEngineMode.entries.forEach { mode ->
                                val selected = settings.selectedEngineMode == mode
                                FilterChip(
                                    selected = selected,
                                    onClick = { onSelectEngineMode(mode) },
                                    label = { Text(mode.displayName) },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("engine_chip_${mode.name.lowercase()}"),
                                )
                            }
                        }

                        val readinessText = when {
                            installedVoskForLang -> "Vosk offline model installed for ${activeLanguage.name} (word timestamps ready)"
                            isSystemAsrAvailable -> "Android On-Device ASR available for ${activeLanguage.bcp47Tag}. Tip: Install Vosk in Models tab for word-level SRT cues."
                            else -> "No offline model installed for ${activeLanguage.name} yet — install/import in Models tab."
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            StatusDotLabel(
                                label = readinessText,
                                isOnlineReady = installedVoskForLang || isSystemAsrAvailable,
                                modifier = Modifier.weight(1f),
                            )
                            if (!installedVoskForLang) {
                                TextButton(
                                    onClick = onOpenModelsTab,
                                    modifier = Modifier.testTag("go_to_models_button"),
                                ) {
                                    Text("Models")
                                }
                            }
                        }
                    }
                }

                when (pipelinePhase) {
                    is TranscriptionPipelinePhase.Idle -> {
                        Button(
                            onClick = onStartTranscription,
                            enabled = loadedAudio != null,
                            shape = MaterialTheme.shapes.medium,
                            contentPadding = PaddingValues(vertical = 16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("transcribe_now_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.GraphicEq,
                                contentDescription = "Start offline transcription",
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (loadedAudio != null) {
                                    "Transcribe Offline (${activeLanguage.shortCode})"
                                } else {
                                    "Drop or Browse an MP3 First"
                                },
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                    is TranscriptionPipelinePhase.DecodingAudio,
                    is TranscriptionPipelinePhase.Transcribing -> {
                        LiveProcessingConsole(
                            phase = pipelinePhase,
                            onCancel = onCancelTranscription,
                        )
                    }
                    is TranscriptionPipelinePhase.Completed -> {
                        CompletedBannerCard(
                            cueCount = pipelinePhase.job.segments.size,
                            engineUsed = pipelinePhase.job.engineUsed,
                            onOpenTranscript = onOpenTranscriptTab,
                        )
                    }
                    is TranscriptionPipelinePhase.Failed -> {
                        ErrorPipelineCard(
                            message = pipelinePhase.errorMessage,
                            suggestion = pipelinePhase.suggestion,
                            onOpenModels = onOpenModelsTab,
                            onRetry = onStartTranscription,
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .widthIn(max = if (isDesktopTwoPane) 1240.dp else 820.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HeaderSection()

            if (isDesktopTwoPane) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(modifier = Modifier.weight(1f)) { InputAndPlayerPane() }
                    Box(modifier = Modifier.weight(1f)) { ConfigAndPipelinePane() }
                }
            } else {
                InputAndPlayerPane()
                ConfigAndPipelinePane()
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showLanguageSheet) {
        ModalBottomSheet(
            onDismissRequest = { showLanguageSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            AllLanguagesSheetContent(
                selectedLanguageId = settings.selectedLanguageId,
                customBcp47 = settings.customBcp47Tag,
                onSelectLanguage = {
                    onSelectLanguage(it)
                    showLanguageSheet = false
                },
                onApplyCustomTag = { tag ->
                    onSetCustomBcp47(tag)
                    showLanguageSheet = false
                },
            )
        }
    }
}

@Composable
private fun DragAndDropAudioZone(
    isDragOver: Boolean,
    hasAudioLoaded: Boolean,
    onBrowseClick: () -> Unit,
) {
    val borderColor by animateColorAsState(
        targetValue = when {
            isDragOver -> MaterialTheme.colorScheme.primary
            hasAudioLoaded -> MaterialTheme.colorScheme.outline
            else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
        },
        label = "dropZoneBorder",
    )
    val bgBrush = Brush.linearGradient(
        colors = if (isDragOver) {
            listOf(
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                MaterialTheme.colorScheme.surface,
            )
        } else {
            listOf(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                MaterialTheme.colorScheme.surface,
            )
        }
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(bgBrush)
            .drawBehind {
                val strokeWidth = if (isDragOver) 3.dp.toPx() else 1.5.dp.toPx()
                val dash = PathEffect.dashPathEffect(floatArrayOf(18f, 12f), 0f)
                drawRoundRect(
                    color = borderColor,
                    style = Stroke(width = strokeWidth, pathEffect = dash),
                    cornerRadius = CornerRadius(22.dp.toPx(), 22.dp.toPx()),
                )
            }
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onBrowseClick)
            .padding(horizontal = 20.dp, vertical = if (hasAudioLoaded) 18.dp else 28.dp)
            .testTag("drag_drop_zone"),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(if (hasAudioLoaded) 44.dp else 56.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.UploadFile,
                        contentDescription = "Drop or upload audio file",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(if (hasAudioLoaded) 22.dp else 28.dp),
                    )
                }
            }

            Text(
                text = if (isDragOver) "Release to Load Audio On-Device" else stringResource(R.string.drop_zone_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Text(
                text = stringResource(R.string.drop_zone_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedButton(
                onClick = onBrowseClick,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("browse_audio_button"),
            ) {
                Icon(
                    imageVector = Icons.Filled.FolderOpen,
                    contentDescription = "Browse device files",
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (hasAudioLoaded) "Choose Different File" else stringResource(R.string.browse_audio_button))
            }
        }
    }
}

@Composable
private fun LoadedAudioCard(
    audio: AudioFileMetadata,
    playbackState: AudioPlaybackState,
    isProcessing: Boolean,
    onClear: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
) {
    val sizeLabel = if (audio.fileSizeBytes > 0L) {
        val mb = audio.fileSizeBytes / (1024f * 1024f)
        if (mb >= 1f) String.format("%.1f MB", mb) else "${audio.fileSizeBytes / 1024L} KB"
    } else "Local URI"

    val durationMs = playbackState.durationMs.takeIf { it > 0L } ?: audio.durationMs
    val progressFrac = if (durationMs > 0L) {
        (playbackState.positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("loaded_audio_card"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.size(42.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.AudioFile,
                                contentDescription = "Loaded audio file",
                                tint = MaterialTheme.colorScheme.secondary,
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = audio.displayName,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "$sizeLabel • ${TranscriptFormatter.formatCompactTimestamp(durationMs)} • ${audio.sampleRateHz} Hz",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(
                    onClick = onClear,
                    enabled = !isProcessing,
                    modifier = Modifier.testTag("clear_audio_button"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Remove loaded audio",
                    )
                }
            }

            // Interactive Waveform + Playback Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                IconButton(
                    onClick = onTogglePlayPause,
                    enabled = playbackState.isPrepared,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .testTag("preview_play_pause_button"),
                ) {
                    Icon(
                        imageVector = if (playbackState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (playbackState.isPlaying) "Pause preview" else "Play preview",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                WaveformVisualizer(
                    bars = audio.waveformBars,
                    progress = progressFrac,
                    isActiveProcessing = isProcessing,
                    onSeekFraction = { frac ->
                        if (durationMs > 0L) onSeekTo((durationMs * frac).toLong())
                    },
                    modifier = Modifier.weight(1f),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = TranscriptFormatter.formatCompactTimestamp(playbackState.positionMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SpecBadge(label = "16kHz PCM Target", accentColor = MaterialTheme.colorScheme.tertiary)
                    SpecBadge(
                        label = if (audio.channelCount > 1) "${audio.channelCount}ch→Mono" else "Mono",
                        accentColor = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    text = TranscriptFormatter.formatCompactTimestamp(durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LiveProcessingConsole(
    phase: TranscriptionPipelinePhase,
    onCancel: () -> Unit,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(750), RepeatMode.Reverse),
        label = "pulseAlpha",
    )

    val (stepLabel, progress, detail, partial) = when (phase) {
        is TranscriptionPipelinePhase.DecodingAudio ->
            Quad("Step 1/2 • On-Device Audio Decode", phase.progress, phase.message, "")
        is TranscriptionPipelinePhase.Transcribing ->
            Quad(
                "Step 2/2 • ${phase.engineLabel}",
                phase.progress,
                "${(phase.progress * 100).toInt()}% processed • ${phase.completedCues} cues aligned",
                phase.partialText,
            )
        else -> Quad("Processing...", 0f, "", "")
    }

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f * pulseAlpha)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("processing_console_card"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stepLabel,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
                OutlinedButton(
                    onClick = onCancel,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("cancel_transcription_button"),
                ) {
                    Icon(Icons.Filled.Stop, contentDescription = "Cancel transcription", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.cancel_button))
                }
            }

            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.secondary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )

            Text(
                text = detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (partial.isNotBlank()) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = partial,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(10.dp),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun CompletedBannerCard(
    cueCount: Int,
    engineUsed: String,
    onOpenTranscript: () -> Unit,
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = "Transcription complete",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Transcribed $cueCount Subtitle Cues",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = engineUsed,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                onClick = onOpenTranscript,
                modifier = Modifier.testTag("view_transcript_button"),
            ) {
                Text("Export / Edit")
            }
        }
    }
}

@Composable
private fun ErrorPipelineCard(
    message: String,
    suggestion: String?,
    onOpenModels: () -> Unit,
    onRetry: () -> Unit,
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.ErrorOutline,
                    contentDescription = "Transcription error",
                    tint = MaterialTheme.colorScheme.error,
                )
                Text(
                    text = "Offline Processing Notice",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Text(text = message, style = MaterialTheme.typography.bodyMedium)
            if (!suggestion.isNullOrBlank()) {
                Text(
                    text = suggestion,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onOpenModels) {
                    Icon(Icons.Filled.CloudOff, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open Models")
                }
                Button(onClick = onRetry) {
                    Text("Retry")
                }
            }
        }
    }
}

@Composable
private fun AllLanguagesSheetContent(
    selectedLanguageId: String,
    customBcp47: String,
    onSelectLanguage: (String) -> Unit,
    onApplyCustomTag: (String) -> Unit,
) {
    var customInput by remember(customBcp47) { mutableStateOf(customBcp47) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Select Transcription Language",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = "All processing happens locally using either Vosk offline models or Android on-device language packs.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SupportedLanguages.all.forEach { lang: LanguageOption ->
            val isSelected = customBcp47.isBlank() && selectedLanguageId == lang.id
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectLanguage(lang.id) },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "${lang.name} (${lang.nativeName})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        )
                        Text(
                            text = "BCP-47: ${lang.bcp47Tag}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Any Other Language (Custom BCP-47 Code)",
            style = MaterialTheme.typography.titleSmall,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = customInput,
                onValueChange = { customInput = it },
                placeholder = { Text("e.g. ta-IN, ml-IN, ko-KR, it-IT") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = { onApplyCustomTag(customInput) },
                enabled = customInput.isNotBlank(),
                modifier = Modifier.minimumInteractiveComponentSize(),
            ) {
                Text("Use")
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
