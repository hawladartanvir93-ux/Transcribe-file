package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.audio.TranscriptFormatter
import com.example.data.local.UserSettings
import com.example.domain.model.TranscriptionJob
import com.example.ui.components.SpecBadge
import com.example.ui.theme.ThemeMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.auth.AuthResult
import com.example.data.auth.VaultAuthSnapshot

@Composable
fun VaultScreen(
    historyJobs: List<TranscriptionJob>,
    settings: UserSettings,
    authSnapshot: VaultAuthSnapshot,
    onOpenJob: (TranscriptionJob) -> Unit,
    onDeleteJob: (Long) -> Unit,
    onWipeAll: () -> Unit,
    onLockVaultNow: () -> Unit,
    onAutoLockChange: (Boolean) -> Unit,
    onUpdateCredentials: (
        currentPassword: String,
        newNumber: String,
        newPassword: String,
        confirmPassword: String,
        onResult: (AuthResult) -> Unit,
    ) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onIncludeTimestampsTxtChange: (Boolean) -> Unit,
    onMaxCueCharsChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var searchQuery by remember { mutableStateOf("") }
    var showWipeConfirm by remember { mutableStateOf(false) }
    var showCredentialsDialog by remember { mutableStateOf(false) }

    val filteredHistory = remember(historyJobs, searchQuery) {
        if (searchQuery.isBlank()) historyJobs
        else historyJobs.filter { job ->
            job.fileName.contains(searchQuery, ignoreCase = true) ||
                job.segments.any { it.text.contains(searchQuery, ignoreCase = true) }
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 960.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "Local Vault & Settings",
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        Text(
                            text = "All history lives strictly in local encrypted-device SQLite.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (historyJobs.isNotEmpty()) {
                        OutlinedButton(
                            onClick = { showWipeConfirm = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.testTag("wipe_vault_button"),
                        ) {
                            Icon(Icons.Filled.DeleteForever, contentDescription = "Wipe all", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Wipe All")
                        }
                    }
                }
            }

            // Vault Security & Login Lock Card
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
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
                                    imageVector = Icons.Filled.Lock,
                                    contentDescription = "Vault Lock",
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Column {
                                    Text(
                                        text = "Vault Lock & Login Protection",
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    if (authSnapshot.maskedNumberHint.isNotEmpty()) {
                                        Text(
                                            text = "Active Number: ${authSnapshot.maskedNumberHint}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                            Button(
                                onClick = onLockVaultNow,
                                modifier = Modifier.minimumInteractiveComponentSize().testTag("lock_vault_button"),
                            ) {
                                Icon(Icons.Filled.LockOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Lock Now")
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Auto-Lock When Minimized", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = "Require number & password after leaving the app",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = settings.autoLockOnBackground,
                                onCheckedChange = onAutoLockChange,
                                modifier = Modifier.testTag("auto_lock_switch"),
                            )
                        }

                        OutlinedButton(
                            onClick = { showCredentialsDialog = true },
                            modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("change_credentials_button"),
                        ) {
                            Icon(Icons.Filled.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Change Number or Password")
                        }
                    }
                }
            }

            // Privacy Guarantee Card
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.VerifiedUser,
                                contentDescription = "Privacy guarantee",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = "Strict On-Device Privacy Architecture",
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                        Text(
                            text = "• Audio decoding via Android MediaExtractor + MediaCodec happens 100% locally.\n" +
                                "• Speech inference runs exclusively on-device (Vosk Kaldi or API 33+ On-Device ASR).\n" +
                                "• PBKDF2-SHA256 salted lock protects local transcripts with zero cloud sync.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Subtitle & Studio Settings
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "Subtitle & Display Preferences",
                            style = MaterialTheme.typography.titleMedium,
                        )

                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("Max Characters per SRT Cue", style = MaterialTheme.typography.bodyMedium)
                                SpecBadge(label = "${settings.maxCueChars} chars", accentColor = MaterialTheme.colorScheme.secondary)
                            }
                            Slider(
                                value = settings.maxCueChars.toFloat(),
                                onValueChange = { onMaxCueCharsChange(it.roundToInt()) },
                                valueRange = 24f..72f,
                                steps = 11,
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Include Timestamps in TXT Export", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = "Prefix lines with [MM:SS - MM:SS]",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = settings.includeTimestampsInTxt,
                                onCheckedChange = onIncludeTimestampsTxtChange,
                                modifier = Modifier.testTag("txt_timestamps_switch"),
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Material You Dynamic Colors", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = "Use system wallpaper palette (Android 12+)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = settings.useDynamicColor,
                                onCheckedChange = onDynamicColorChange,
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Theme:", style = MaterialTheme.typography.bodyMedium)
                            ThemeMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = settings.themeMode == mode,
                                    onClick = { onThemeModeChange(mode) },
                                    label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) },
                                    modifier = Modifier.minimumInteractiveComponentSize(),
                                )
                            }
                        }
                    }
                }
            }

            // Local Transcription History Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.History,
                        contentDescription = "Local transcription history",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "Saved On-Device Transcripts (${historyJobs.size})",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }

            if (historyJobs.isNotEmpty()) {
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Filter local vault...") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Filter history") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (filteredHistory.isEmpty()) {
                item {
                    Card(
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(32.dp),
                            )
                            Text(
                                text = if (historyJobs.isEmpty()) "Your private vault is empty" else "No matching transcripts",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = "Completed offline transcriptions appear here automatically for quick re-export.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                items(filteredHistory, key = { it.id }) { job ->
                    HistoryJobCard(
                        job = job,
                        onClick = { onOpenJob(job) },
                        onDelete = { onDeleteJob(job.id) },
                    )
                }
            }
        }
    }

    if (showWipeConfirm) {
        AlertDialog(
            onDismissRequest = { showWipeConfirm = false },
            title = { Text("Wipe Local Vault?") },
            text = { Text("This permanently deletes all saved transcripts from your device SQLite database. Models remain untouched.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onWipeAll()
                        showWipeConfirm = false
                    },
                ) {
                    Text("Wipe All", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showWipeConfirm = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (showCredentialsDialog) {
        ChangeCredentialsDialog(
            onDismiss = { showCredentialsDialog = false },
            onSave = { currentPw, newNum, newPw, confirmPw, onErr ->
                onUpdateCredentials(currentPw, newNum, newPw, confirmPw) { res ->
                    when (res) {
                        AuthResult.Success -> showCredentialsDialog = false
                        is AuthResult.Error -> onErr(res.message)
                    }
                }
            },
        )
    }
}

@Composable
private fun ChangeCredentialsDialog(
    onDismiss: () -> Unit,
    onSave: (
        currentPassword: String,
        newNumber: String,
        newPassword: String,
        confirmPassword: String,
        onError: (String) -> Unit,
    ) -> Unit,
) {
    var currentPw by remember { mutableStateOf("") }
    var newNumber by remember { mutableStateOf("") }
    var newPw by remember { mutableStateOf("") }
    var confirmPw by remember { mutableStateOf("") }
    var showPw by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .padding(20.dp)
                .widthIn(max = 460.dp)
                .fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Update Vault Login",
                    style = MaterialTheme.typography.headlineSmall,
                )
                OutlinedTextField(
                    value = currentPw,
                    onValueChange = { currentPw = it; errorMsg = null },
                    label = { Text("Current Password") },
                    singleLine = true,
                    visualTransformation = if (showPw) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPw = !showPw }) {
                            Icon(
                                imageVector = if (showPw) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = "Toggle password",
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("change_current_password_input"),
                )
                OutlinedTextField(
                    value = newNumber,
                    onValueChange = { raw ->
                        newNumber = raw.filter { it.isDigit() || it == '+' || it == '-' || it == ' ' }
                        errorMsg = null
                    },
                    label = { Text("New Number") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("change_new_number_input"),
                )
                OutlinedTextField(
                    value = newPw,
                    onValueChange = { newPw = it; errorMsg = null },
                    label = { Text("New Password (min 6 chars)") },
                    singleLine = true,
                    visualTransformation = if (showPw) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("change_new_password_input"),
                )
                OutlinedTextField(
                    value = confirmPw,
                    onValueChange = { confirmPw = it; errorMsg = null },
                    label = { Text("Confirm New Password") },
                    singleLine = true,
                    visualTransformation = if (showPw) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("change_confirm_password_input"),
                )
                if (!errorMsg.isNullOrBlank()) {
                    Text(
                        text = errorMsg.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("change_credentials_error_text"),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onSave(currentPw, newNumber, newPw, confirmPw) { err -> errorMsg = err }
                        },
                        modifier = Modifier.testTag("save_credentials_button"),
                    ) {
                        Text("Update")
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryJobCard(
    job: TranscriptionJob,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val dateStr = remember(job.createdAtMs) {
        SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(job.createdAtMs))
    }
    val preview = remember(job.segments) {
        job.segments.joinToString(" ") { it.text.trim() }.take(140)
    }

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("history_card_${job.id}"),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = job.fileName,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "$dateStr • ${job.languageLabel}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = "Delete history item",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (preview.isNotBlank()) {
                Text(
                    text = preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SpecBadge(label = "${job.segments.size} cues", accentColor = MaterialTheme.colorScheme.primary)
                SpecBadge(
                    label = TranscriptFormatter.formatCompactTimestamp(job.durationMs),
                    accentColor = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}
