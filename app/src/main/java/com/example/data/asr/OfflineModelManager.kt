package com.example.data.asr

import android.content.Context
import android.net.Uri
import com.example.domain.model.OfflineModelInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

data class ModelInstallProgress(
    val modelId: String,
    val progress: Float,
    val statusText: String,
    val isDone: Boolean = false,
    val error: String? = null,
)

class OfflineModelManager(private val context: Context) {

    private val modelsRootDir = File(context.filesDir, "vosk_models").apply { mkdirs() }
    private val customRegistryFile = File(modelsRootDir, "custom_models.json")

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val catalog = listOf(
        OfflineModelInfo(
            id = "vosk-model-small-en-us-0.15",
            languageId = "en-us",
            languageName = "English (US)",
            nativeName = "English (US)",
            modelDisplayName = "Vosk Small EN-US v0.15",
            sizeMb = 40,
            downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip",
            notes = "Fast lightweight offline Kaldi model with word timestamps.",
        ),
        OfflineModelInfo(
            id = "vosk-model-small-en-in-0.4",
            languageId = "en-in",
            languageName = "English (India)",
            nativeName = "English (India)",
            modelDisplayName = "Vosk Small EN-IN v0.4",
            sizeMb = 36,
            downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-en-in-0.4.zip",
            notes = "Tuned for South Asian English accents & vocabulary.",
        ),
        OfflineModelInfo(
            id = "vosk-model-small-hi-0.22",
            languageId = "hi-in",
            languageName = "Hindi",
            nativeName = "हिन्दी",
            modelDisplayName = "Vosk Small Hindi v0.22",
            sizeMb = 42,
            downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-hi-0.22.zip",
            notes = "Devanagari offline speech model with word timestamps.",
        ),
        OfflineModelInfo(
            id = "vosk-model-small-es-0.42",
            languageId = "es-es",
            languageName = "Spanish",
            nativeName = "Español",
            modelDisplayName = "Vosk Small Spanish v0.42",
            sizeMb = 39,
            downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-es-0.42.zip",
            notes = "Compact offline Spanish transcription model.",
        ),
        OfflineModelInfo(
            id = "vosk-model-small-fr-0.22",
            languageId = "fr-fr",
            languageName = "French",
            nativeName = "Français",
            modelDisplayName = "Vosk Small French v0.22",
            sizeMb = 41,
            downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-fr-0.22.zip",
            notes = "Compact offline French transcription model.",
        ),
        OfflineModelInfo(
            id = "vosk-model-small-de-0.15",
            languageId = "de-de",
            languageName = "German",
            nativeName = "Deutsch",
            modelDisplayName = "Vosk Small German v0.15",
            sizeMb = 45,
            downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-de-0.15.zip",
            notes = "Compact offline German transcription model.",
        ),
        OfflineModelInfo(
            id = "vosk-model-small-pt-0.3",
            languageId = "pt-br",
            languageName = "Portuguese",
            nativeName = "Português",
            modelDisplayName = "Vosk Small Portuguese v0.3",
            sizeMb = 31,
            downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-pt-0.3.zip",
            notes = "Compact offline Portuguese transcription model.",
        ),
        OfflineModelInfo(
            id = "vosk-model-small-ja-0.22",
            languageId = "ja-jp",
            languageName = "Japanese",
            nativeName = "日本語",
            modelDisplayName = "Vosk Small Japanese v0.22",
            sizeMb = 48,
            downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-ja-0.22.zip",
            notes = "Compact offline Japanese transcription model.",
        ),
        OfflineModelInfo(
            id = "vosk-model-small-ru-0.22",
            languageId = "ru-ru",
            languageName = "Russian",
            nativeName = "Русский",
            modelDisplayName = "Vosk Small Russian v0.22",
            sizeMb = 45,
            downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-ru-0.22.zip",
            notes = "Compact offline Russian transcription model.",
        ),
    )

    private val _modelsFlow = MutableStateFlow(loadModelsSnapshot())
    val modelsFlow: Flow<List<OfflineModelInfo>> = _modelsFlow.asStateFlow()

    fun refreshModels() {
        _modelsFlow.value = loadModelsSnapshot()
    }

    fun resolveInstalledModelDir(languageId: String): File? {
        val snapshot = loadModelsSnapshot()
        val match = snapshot.firstOrNull {
            it.isInstalled && (it.languageId == languageId ||
                (languageId.startsWith("bn") && it.languageId.startsWith("bn")))
        }
        return match?.installedPath?.let { File(it) }?.takeIf { it.exists() && isValidVoskModelDir(it) }
    }

    fun downloadModel(modelId: String): Flow<ModelInstallProgress> = flow {
        val info = catalog.find { it.id == modelId }
        val url = info?.downloadUrl
        if (info == null || url == null) {
            emit(ModelInstallProgress(modelId, 0f, "Download unavailable", error = "No download URL for $modelId"))
            return@flow
        }

        val tempZip = File(context.cacheDir, "$modelId.zip.tmp")
        val targetDir = File(modelsRootDir, modelId)

        try {
            emit(ModelInstallProgress(modelId, 0.02f, "Connecting to AlphaCephei open-source mirror..."))
            val request = Request.Builder().url(url).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("HTTP ${response.code}: ${response.message}")
                }
                val body = response.body ?: throw IllegalStateException("Empty download response")
                val totalBytes = body.contentLength()
                var downloaded = 0L

                body.byteStream().use { input ->
                    FileOutputStream(tempZip).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            if (totalBytes > 0L) {
                                val pct = (downloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                                val mbDone = downloaded / (1024f * 1024f)
                                val mbTotal = totalBytes / (1024f * 1024f)
                                emit(
                                    ModelInstallProgress(
                                        modelId = modelId,
                                        progress = pct * 0.85f,
                                        statusText = String.format("Downloading %.1f / %.1f MB", mbDone, mbTotal),
                                    )
                                )
                            } else {
                                val mbDone = downloaded / (1024f * 1024f)
                                emit(
                                    ModelInstallProgress(
                                        modelId = modelId,
                                        progress = 0.45f,
                                        statusText = String.format("Downloading %.1f MB...", mbDone),
                                    )
                                )
                            }
                        }
                    }
                }
            }

            emit(ModelInstallProgress(modelId, 0.88f, "Unpacking offline model..."))
            targetDir.deleteRecursively()
            targetDir.mkdirs()
            tempZip.inputStream().use { fis ->
                unzipSafely(fis, targetDir)
            }

            val resolvedRoot = locateVoskModelRoot(targetDir)
                ?: throw IllegalStateException("Downloaded archive did not contain a valid Vosk model directory.")

            // Flatten if unpacked inside a nested subdirectory
            if (resolvedRoot != targetDir) {
                val staging = File(modelsRootDir, "${modelId}_staging")
                staging.deleteRecursively()
                resolvedRoot.renameTo(staging)
                targetDir.deleteRecursively()
                staging.renameTo(targetDir)
            }

            refreshModels()
            emit(ModelInstallProgress(modelId, 1f, "Installed & ready offline", isDone = true))
        } catch (t: Throwable) {
            targetDir.deleteRecursively()
            emit(
                ModelInstallProgress(
                    modelId = modelId,
                    progress = 0f,
                    statusText = "Failed",
                    error = t.message ?: "Download failed",
                )
            )
        } finally {
            tempZip.delete()
        }
    }.flowOn(Dispatchers.IO)

    fun importLocalModelZip(
        uri: Uri,
        languageId: String,
        languageName: String,
        nativeName: String,
    ): Flow<ModelInstallProgress> = flow {
        val customId = "custom_${languageId}_${System.currentTimeMillis()}"
        val targetDir = File(modelsRootDir, customId)
        try {
            emit(ModelInstallProgress(customId, 0.15f, "Reading local model archive..."))
            targetDir.mkdirs()
            val stream = context.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Unable to open selected ZIP archive.")
            stream.use { input ->
                emit(ModelInstallProgress(customId, 0.55f, "Unpacking model strictly on-device..."))
                unzipSafely(input, targetDir)
            }

            val resolvedRoot = locateVoskModelRoot(targetDir)
                ?: throw IllegalStateException("ZIP does not contain a valid Kaldi/Vosk model (missing conf/ or am/ directory).")

            if (resolvedRoot != targetDir) {
                val staging = File(modelsRootDir, "${customId}_staging")
                staging.deleteRecursively()
                resolvedRoot.renameTo(staging)
                targetDir.deleteRecursively()
                staging.renameTo(targetDir)
            }

            val sizeMb = (folderSizeBytes(targetDir) / (1024L * 1024L)).toInt().coerceAtLeast(1)
            val customInfo = OfflineModelInfo(
                id = customId,
                languageId = languageId,
                languageName = languageName,
                nativeName = nativeName,
                modelDisplayName = "Custom $languageName Model",
                sizeMb = sizeMb,
                downloadUrl = null,
                notes = "Locally imported ZIP archive (100% air-gapped).",
                isInstalled = true,
                installedPath = targetDir.absolutePath,
                isCustomImported = true,
            )
            saveCustomModelEntry(customInfo)
            refreshModels()
            emit(ModelInstallProgress(customId, 1f, "Imported offline model", isDone = true))
        } catch (t: Throwable) {
            targetDir.deleteRecursively()
            emit(
                ModelInstallProgress(
                    modelId = customId,
                    progress = 0f,
                    statusText = "Import failed",
                    error = t.message ?: "Failed to import ZIP model",
                )
            )
        }
    }.flowOn(Dispatchers.IO)

    suspend fun deleteModel(modelId: String) = withContext(Dispatchers.IO) {
        File(modelsRootDir, modelId).deleteRecursively()
        removeCustomModelEntry(modelId)
        refreshModels()
    }

    private fun loadModelsSnapshot(): List<OfflineModelInfo> {
        val builtIn = catalog.map { info ->
            val dir = File(modelsRootDir, info.id)
            val installed = dir.exists() && isValidVoskModelDir(dir)
            info.copy(
                isInstalled = installed,
                installedPath = if (installed) dir.absolutePath else null,
            )
        }
        val custom = readCustomModelEntries().mapNotNull { info ->
            val dir = File(modelsRootDir, info.id)
            if (dir.exists() && isValidVoskModelDir(dir)) {
                info.copy(isInstalled = true, installedPath = dir.absolutePath)
            } else {
                null
            }
        }
        return builtIn + custom
    }

    private fun unzipSafely(inputStream: InputStream, destDir: File) {
        val destCanonical = destDir.canonicalPath
        ZipInputStream(BufferedInputStream(inputStream)).use { zis ->
            val buffer = ByteArray(16 * 1024)
            var entry = zis.nextEntry
            while (entry != null) {
                val outFile = File(destDir, entry.name)
                val outCanonical = outFile.canonicalPath
                if (!outCanonical.startsWith(destCanonical + File.separator) && outCanonical != destCanonical) {
                    throw SecurityException("Blocked unsafe ZIP entry path: ${entry.name}")
                }
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { fos ->
                        while (true) {
                            val count = zis.read(buffer)
                            if (count <= 0) break
                            fos.write(buffer, 0, count)
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    private fun locateVoskModelRoot(root: File): File? {
        if (isValidVoskModelDir(root)) return root
        root.listFiles()?.forEach { child ->
            if (child.isDirectory) {
                val found = locateVoskModelRoot(child)
                if (found != null) return found
            }
        }
        return null
    }

    private fun isValidVoskModelDir(dir: File): Boolean {
        val confDir = File(dir, "conf")
        val amDir = File(dir, "am")
        val graphDir = File(dir, "graph")
        return confDir.isDirectory && (amDir.isDirectory || graphDir.isDirectory || File(dir, "final.mdl").exists())
    }

    private fun folderSizeBytes(dir: File): Long {
        var total = 0L
        dir.walkTopDown().forEach { if (it.isFile) total += it.length() }
        return total
    }

    private fun readCustomModelEntries(): List<OfflineModelInfo> {
        if (!customRegistryFile.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(customRegistryFile.readText())
            List(arr.length()) { idx ->
                val obj = arr.getJSONObject(idx)
                OfflineModelInfo(
                    id = obj.getString("id"),
                    languageId = obj.getString("languageId"),
                    languageName = obj.getString("languageName"),
                    nativeName = obj.getString("nativeName"),
                    modelDisplayName = obj.getString("modelDisplayName"),
                    sizeMb = obj.optInt("sizeMb", 10),
                    downloadUrl = null,
                    notes = obj.optString("notes", "Locally imported ZIP model."),
                    isCustomImported = true,
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun saveCustomModelEntry(info: OfflineModelInfo) {
        val existing = readCustomModelEntries().filterNot { it.id == info.id } + info
        writeCustomEntries(existing)
    }

    private fun removeCustomModelEntry(id: String) {
        val filtered = readCustomModelEntries().filterNot { it.id == id }
        writeCustomEntries(filtered)
    }

    private fun writeCustomEntries(entries: List<OfflineModelInfo>) {
        val arr = JSONArray()
        entries.forEach { info ->
            arr.put(
                JSONObject().apply {
                    put("id", info.id)
                    put("languageId", info.languageId)
                    put("languageName", info.languageName)
                    put("nativeName", info.nativeName)
                    put("modelDisplayName", info.modelDisplayName)
                    put("sizeMb", info.sizeMb)
                    put("notes", info.notes)
                }
            )
        }
        customRegistryFile.writeText(arr.toString())
    }
}
