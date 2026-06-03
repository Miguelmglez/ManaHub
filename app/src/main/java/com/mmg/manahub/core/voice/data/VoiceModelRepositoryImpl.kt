package com.mmg.manahub.core.voice.data

import android.content.Context
import android.util.Log
import com.mmg.manahub.core.voice.domain.VoiceLanguage
import com.mmg.manahub.core.voice.domain.VoiceModelRepository
import com.mmg.manahub.core.voice.domain.VoiceModelState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Default [VoiceModelRepository] backed by per-language Vosk models stored under the app's
 * private [Context.getFilesDir].
 *
 * Each language is downloaded as a zip from the model host, extracted into its own directory,
 * and validated independently. State for every language is published through [modelStates].
 */
@Singleton
class VoiceModelRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    @Named("voice") private val httpClient: OkHttpClient,
    @Named("cloudflare_base_url") private val baseUrl: String,
) : VoiceModelRepository {

    private val _states = MutableStateFlow(
        VoiceLanguage.entries.associateWith { language ->
            if (isModelValid(modelDirFor(language))) VoiceModelState.Ready
            else VoiceModelState.NotDownloaded
        }
    )
    override val modelStates: StateFlow<Map<VoiceLanguage, VoiceModelState>> = _states.asStateFlow()

    override fun modelDir(language: VoiceLanguage): File? =
        modelDirFor(language).takeIf { isModelValid(it) }

    override suspend fun download(language: VoiceLanguage) = withContext(Dispatchers.IO) {
        val current = _states.value[language]
        if (current is VoiceModelState.Ready) return@withContext
        if (current is VoiceModelState.Downloading) return@withContext

        // Upload es.zip and de.zip to Cloudflare R2 at voice/models/ before enabling these languages in production
        val url = "${baseUrl.trimEnd('/')}/voice/models/${language.modelFileName}"
        val modelDir = modelDirFor(language)
        updateState(language, VoiceModelState.Downloading(0f))

        try {
            val request = Request.Builder().url(url).build()
            val response = httpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                updateState(language, VoiceModelState.Error("Download failed (HTTP ${response.code})"))
                return@withContext
            }

            val body = response.body
            val totalBytes = body.contentLength().takeIf { it > 0 } ?: -1L
            val tempZip = File(context.cacheDir, "vosk-${language.modelDirName}.zip.tmp")

            body.byteStream().use { input ->
                FileOutputStream(tempZip).use { output ->
                    var bytesCopied = 0L
                    val buffer = ByteArray(8 * 1024)
                    var bytes = input.read(buffer)
                    while (bytes >= 0) {
                        output.write(buffer, 0, bytes)
                        bytesCopied += bytes
                        if (totalBytes > 0) {
                            updateState(language, VoiceModelState.Downloading(bytesCopied.toFloat() / totalBytes))
                        }
                        bytes = input.read(buffer)
                    }
                }
            }

            unzip(tempZip, modelDir)
            tempZip.delete()

            if (isModelValid(modelDir)) {
                updateState(language, VoiceModelState.Ready)
            } else {
                modelDir.deleteRecursively()
                updateState(language, VoiceModelState.Error("Model validation failed after extraction"))
            }
        } catch (e: Exception) {
            Log.e("VoiceModelRepository", "Download failed for ${language.modelDirName}", e)
            modelDir.deleteRecursively()
            updateState(language, VoiceModelState.Error(e.message ?: "Unknown error"))
        }
    }

    override suspend fun delete(language: VoiceLanguage) = withContext(Dispatchers.IO) {
        modelDirFor(language).deleteRecursively()
        updateState(language, VoiceModelState.NotDownloaded)
    }

    private fun modelDirFor(language: VoiceLanguage): File =
        File(context.filesDir, "voice-models/${language.modelDirName}")

    /** Atomically replaces only [language]'s entry in the published state map. */
    private fun updateState(language: VoiceLanguage, state: VoiceModelState) {
        _states.value = _states.value.toMutableMap().also { it[language] = state }.toMap()
    }

    private fun unzip(zipFile: File, destDir: File) {
        destDir.mkdirs()
        ZipInputStream(FileInputStream(zipFile)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                // Strip the top-level directory name that Vosk model zips include
                val entryName = entry.name.substringAfter("/")
                if (entryName.isNotBlank()) {
                    val target = File(destDir, entryName)
                    if (entry.isDirectory) {
                        target.mkdirs()
                    } else {
                        target.parentFile?.mkdirs()
                        FileOutputStream(target).use { out -> zis.copyTo(out) }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    private fun isModelValid(dir: File): Boolean =
        dir.exists() && dir.isDirectory && dir.listFiles()?.isNotEmpty() == true
}
