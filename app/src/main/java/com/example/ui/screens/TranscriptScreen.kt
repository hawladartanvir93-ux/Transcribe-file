package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.audio.TranscriptFormatter
import com.example.domain.model.ExportFormat
import com.example.domain.model.TranscriptSegment
import com.example.domain.model.TranscriptionJob
import com.example.ui.AudioPlaybackState
import com.example.ui.components.SpecBadge
import com.example.ui.theme.JetBrainsMonoFontFamily
import kotlinx.coroutines.launch

private enum class TranscriptViewMode(val label: String) {
    CUES("Cue Editor"),
    SRT("SRT"),
    JSON("JSON"),
    TXT("TXT"),
}

@Composable
fun TranscriptScreen(
    activeJob: TranscriptionJob?,
    selectedExportFormat: ExportFormat,
    playbackState: AudioPlaybackState,
    buildExportContent: (ExportFormat) -> String,
    suggestedFileName: (ExportFormat) -> String,
    onSelectExportFormat: (ExportFormat) -> Unit,
    onSaveExportUri: (Uri, ExportFormat) -> Unit,
    onCreateShareUri: suspend (ExportFormat) -> Uri?,
    onUpdateCueText: (String, String) -> Unit,
    onShiftCueTiming: (String, Long, Long) -> Unit,
    onDeleteCue: (String) -> Unit,
    onSeekAudio: (Long) -> Unit,
    onShowMessage: (String) -> Unit,
    onGoToStudio: () -> Unit,
    onPrepareExternalPicker: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var viewMode by remember { mutableStateOf(TranscriptViewMode.CUES) }
    var searchQuery by remember { mutableStateOf("") }

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*"),
    ) { uri ->
        uri?.let { onSaveExportUri(it, selectedExportFormat) }
    }

    if (activeJob == null) {
        Box(
            modifier = modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 420.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(68.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Subtitles,
                            contentDescription = "No active transcript",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(34.dp),
                        )
                    }
                }
                Text(
                    text = "No Active Transcript Yet",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = "Drop an MP3 in the Studio or pick a past session from your local Vault to inspect, edit cues, and export SRT, JSON, or TXT.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = onGoToStudio,
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("empty_go_studio_button"),
                ) {
                    Text("Open Studio")
                }
            }
        }
        return
    }

    val filteredSegments = remember(activeJob.segments, searchQuery) {
        if (searchQuery.isBlank()) activeJob.segments
        else activeJob.segments.filter { it.text.contains(searchQuery, ignoreCase = true) }
    }

    val activeExportFormat = when (viewMode) {
        TranscriptViewMode.SRT -> ExportFormat.SRT
        TranscriptViewMode.JSON -> ExportFormat.JSON
        TranscriptViewMode.TXT -> ExportFormat.TXT
        TranscriptViewMode.CUES -> selectedExportFormat
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        val isDesktopTwoPane = maxWidth >= 920.dp

        Column(
            modifier = Modifier
                .widthIn(max = if (isDesktopTwoPane) 1280.dp else 860.dp)
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Job Header Card
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = activeJob.fileName,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${activeJob.languageLabel} • ${activeJob.engineUsed}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        SpecBadge(
                            label = "${activeJob.segments.size} cues",
                            accentColor = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val wordCount = activeJob.segments.sumOf {
                            it.text.trim().split(Regex("\\s+")).filter { w -> w.isNotEmpty() }.size
                        }
                        SpecBadge(
                            label = "Duration ${TranscriptFormatter.formatCompactTimestamp(activeJob.durationMs)}",
                            accentColor = MaterialTheme.colorScheme.secondary,
                        )
                        SpecBadge(
                            label = "$wordCount words",
                            accentColor = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
            }

            // View / Format Switcher (Compact Mobile) or Format Tabs (Desktop Preview)
            if (!isDesktopTwoPane) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    TranscriptViewMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = viewMode == mode,
                            onClick = {
                                viewMode = mode
                                when (mode) {
                                    TranscriptViewMode.SRT -> onSelectExportFormat(ExportFormat.SRT)
                                    TranscriptViewMode.JSON -> onSelectExportFormat(ExportFormat.JSON)
                                    TranscriptViewMode.TXT -> onSelectExportFormat(ExportFormat.TXT)
                                    TranscriptViewMode.CUES -> Unit
                                }
                            },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = TranscriptViewMode.entries.size,
                            ),
                            modifier = Modifier.testTag("transcript_mode_${mode.name.lowercase()}"),
                        ) {
                            Text(mode.label, maxLines = 1)
                        }
                    }
                }
            }

            @Composable
            fun CueEditorPane(paneModifier: Modifier = Modifier) {
                Column(modifier = paneModifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search transcript (Bangla, Hindi, English...)") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search cues") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth().testTag("transcript_search_input"),
                    )

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 12.dp),
                    ) {
                        items(filteredSegments, key = { it.id }) { segment ->
                            EditableCueCard(
                                segment = segment,
                                canSeekAudio = playbackState.isPrepared,
                                onSeek = { onSeekAudio(segment.startMs) },
                                onTextChange = { onUpdateCueText(segment.id, it) },
                                onShiftStart = { delta -> onShiftCueTiming(segment.id, delta, 0L) },
                                onShiftEnd = { delta -> onShiftCueTiming(segment.id, 0L, delta) },
                                onDelete = { onDeleteCue(segment.id) },
                            )
                        }
                    }
                }
            }

            @Composable
            fun CodePreviewPane(format: ExportFormat, paneModifier: Modifier = Modifier) {
                val codeContent = remember(activeJob, format) {
                    buildExportContent(format)
                }
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = paneModifier.fillMaxSize(),
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Description,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = suggestedFileName(format),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                            Text(
                                text = "${codeContent.lines().size} lines",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .horizontalScroll(rememberScrollState())
                                .padding(14.dp),
                        ) {
                            Text(
                                text = codeContent,
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.testTag("export_code_preview"),
                            )
                        }
                    }
                }
            }

            // Main Content Area: Side-by-side on PC/Desktop, Tabbed on Mobile
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (isDesktopTwoPane) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        CueEditorPane(paneModifier = Modifier.weight(1f))
                        CodePreviewPane(format = selectedExportFormat, paneModifier = Modifier.weight(1f))
                    }
                } else if (viewMode == TranscriptViewMode.CUES) {
                    CueEditorPane()
                } else {
                    CodePreviewPane(format = activeExportFormat)
                }
            }

            // Export Format Picker + Action Bar
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (isDesktopTwoPane || viewMode == TranscriptViewMode.CUES) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Format:",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            ExportFormat.entries.forEach { fmt ->
                                FilterChip(
                                    selected = selectedExportFormat == fmt,
                                    onClick = { onSelectExportFormat(fmt) },
                                    label = { Text(fmt.label) },
                                    modifier = Modifier.testTag("export_format_${fmt.extension}"),
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = {
                                onSelectExportFormat(activeExportFormat)
                                onPrepareExternalPicker()
                                saveLauncher.launch(suggestedFileName(activeExportFormat))
                            },
                            modifier = Modifier
                                .weight(1.3f)
                                .minimumInteractiveComponentSize()
                                .testTag("save_export_button"),
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = "Save file", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save .${activeExportFormat.label}", maxLines = 1)
                        }

                        OutlinedButton(
                            onClick = {
                                val text = buildExportContent(activeExportFormat)
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("VaultScribe ${activeExportFormat.label}", text))
                                onShowMessage("Copied ${activeExportFormat.label} to clipboard")
                            },
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("copy_export_button"),
                        ) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copy", maxLines = 1)
                        }

                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val shareUri = onCreateShareUri(activeExportFormat)
                                    if (shareUri != null) {
                                        onPrepareExternalPicker()
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = activeExportFormat.mimeType
                                            putExtra(Intent.EXTRA_STREAM, shareUri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(intent, "Share ${activeExportFormat.label}"))
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("share_export_button"),
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = "Share", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Share", maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditableCueCard(
    segment: TranscriptSegment,
    canSeekAudio: Boolean,
    onSeek: () -> Unit,
    onTextChange: (String) -> Unit,
    onShiftStart: (Long) -> Unit,
    onShiftEnd: (Long) -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = canSeekAudio, onClick = onSeek)
                        .padding(vertical = 4.dp, horizontal = 6.dp),
                ) {
                    SpecBadge(label = "#${segment.index}", accentColor = MaterialTheme.colorScheme.primary)
                    Text(
                        text = "${TranscriptFormatter.formatSrtTimestamp(segment.startMs)} → ${TranscriptFormatter.formatSrtTimestamp(segment.endMs)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    if (canSeekAudio) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Play from cue timestamp",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }

                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = "Delete subtitle cue",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            OutlinedTextField(
                value = segment.text,
                onValueChange = onTextChange,
                textStyle = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SmallNudgeChip("-0.2s Start") { onShiftStart(-200L) }
                    SmallNudgeChip("+0.2s Start") { onShiftStart(200L) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SmallNudgeChip("-0.2s End") { onShiftEnd(-200L) }
                    SmallNudgeChip("+0.2s End") { onShiftEnd(200L) }
                }
            }
        }
    }
}

@Composable
private fun SmallNudgeChip(label: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
        )
    }
}
