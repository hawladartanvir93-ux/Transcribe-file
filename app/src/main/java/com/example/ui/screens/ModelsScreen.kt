package com.example.ui.screens

import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.asr.ModelInstallProgress
import com.example.domain.model.LanguageOption
import com.example.domain.model.OfflineModelInfo
import com.example.domain.model.SupportedLanguages
import com.example.ui.components.SpecBadge

@Composable
fun ModelsScreen(
    models: List<OfflineModelInfo>,
    modelProgress: Map<String, ModelInstallProgress>,
    isSystemAsrAvailable: Boolean,
    onDownloadModel: (String) -> Unit,
    onDeleteModel: (String) -> Unit,
    onImportLocalZip: (Uri, LanguageOption) -> Unit,
    onPrepareExternalPicker: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var importTargetLang by remember { mutableStateOf(SupportedLanguages.featured.first()) }

    val zipPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let { onImportLocalZip(it, importTargetLang) }
    }

    val importProgress = modelProgress["import_local"]

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 820.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Offline Language Models",
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        text = "Open-source Kaldi/Vosk neural models execute 100% locally with zero network during transcription.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Android On-Device Speech Status Card (supports Bangla, Hindi, English natively on API 33+)
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PhoneAndroid,
                                contentDescription = "Android system on-device ASR",
                                tint = MaterialTheme.colorScheme.tertiary,
                            )
                            Text(
                                text = "System On-Device ASR (API ${Build.VERSION.SDK_INT})",
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                        Text(
                            text = if (isSystemAsrAvailable) {
                                "Active on this device! Supports Bangla (bn-BD / bn-IN), Hindi (hi-IN), English (en-US), and 50+ system offline packs without extra app model downloads."
                            } else {
                                "System On-Device SpeechRecognizer is unavailable on this Android build (API 33+ required). Install or import a Vosk offline model below for universal offline transcription."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Air-gapped Local Model ZIP Import Card
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Archive,
                                contentDescription = "Import local Vosk ZIP model",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = "Import Local Vosk Model (.zip)",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        Text(
                            text = "Air-gapped option: sideload any Kaldi-compatible Vosk model ZIP (including Bangla, large English, or Hindi models) straight from device storage.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SupportedLanguages.featured.forEach { lang ->
                                FilterChip(
                                    selected = importTargetLang.id == lang.id,
                                    onClick = { importTargetLang = lang },
                                    label = { Text(lang.name) },
                                )
                            }
                        }

                        if (importProgress != null && !importProgress.isDone && importProgress.error == null) {
                            LinearProgressIndicator(
                                progress = { importProgress.progress },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            )
                            Text(
                                text = importProgress.statusText,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                onPrepareExternalPicker()
                                zipPickerLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("import_local_model_button"),
                        ) {
                            Text("Select .zip Archive for ${importTargetLang.name}")
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Open-Source AlphaCephei Vosk Catalog",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            items(models, key = { it.id }) { model ->
                ModelCatalogCard(
                    model = model,
                    progress = modelProgress[model.id],
                    onDownload = { onDownloadModel(model.id) },
                    onDelete = { onDeleteModel(model.id) },
                )
            }
        }
    }
}

@Composable
private fun ModelCatalogCard(
    model: OfflineModelInfo,
    progress: ModelInstallProgress?,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    val isDownloading = progress != null && !progress.isDone && progress.error == null

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = 1.dp,
            color = if (model.isInstalled) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ),
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
                        text = "${model.languageName} (${model.nativeName})",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = model.modelDisplayName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (model.isInstalled) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SpecBadge(label = "Ready • ${model.sizeMb} MB", accentColor = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.testTag("delete_model_${model.id}"),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteOutline,
                                contentDescription = "Delete offline model",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                } else {
                    Button(
                        onClick = onDownload,
                        enabled = !isDownloading && model.downloadUrl != null,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("download_model_${model.id}"),
                    ) {
                        Icon(Icons.Filled.CloudDownload, contentDescription = "Download", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("${model.sizeMb} MB")
                    }
                }
            }

            Text(
                text = model.notes,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (isDownloading) {
                LinearProgressIndicator(
                    progress = { progress.progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                )
                Text(
                    text = progress.statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            } else if (progress?.error != null) {
                Text(
                    text = "Error: ${progress.error}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            } else if (model.isInstalled) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = "Stored strictly on-device in private app vault",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
