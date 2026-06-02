package com.mmg.manahub.core.voice.data

import android.content.Context
import android.util.Log
import com.mmg.manahub.core.voice.domain.VoiceModelRepository
import com.mmg.manahub.core.voice.domain.VoiceModelState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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

@Singleton
class VoiceModelRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    @Named("voice") private val httpClient: OkHttpClient,
    @Named("cloudflare_base_url") private val baseUrl: String,
) : VoiceModelRepository {

    private val modelDir = File(context.filesDir, "voice-models/en")

    private val _state = MutableStateFlow<VoiceModelState>(
        if (isModelValid()) VoiceModelState.Ready else VoiceModelState.NotDownloaded
    )

    override fun observeState(): Flow<VoiceModelState> = _state.asStateFlow()

    override fun modelDir(): File? = modelDir.takeIf { isModelValid() }

    override suspend fun download() = withContext(Dispatchers.IO) {
        if (_state.value is VoiceModelState.Ready) return@withContext
        if (_state.value is VoiceModelState.Downloading) return@withContext

        val url = "${baseUrl.trimEnd('/')}/voice/models/en.zip"
        _state.value = VoiceModelState.Downloading(0f)

        try {
            val request = Request.Builder().url(url).build()
            val response = httpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                _state.value = VoiceModelState.Error("Download failed (HTTP ${response.code})")
                return@withContext
            }

            val body = response.body
            val totalBytes = body.contentLength().takeIf { it > 0 } ?: -1L
            val tempZip = File(context.cacheDir, "vosk-en.zip.tmp")

            body.byteStream().use { input ->
                FileOutputStream(tempZip).use { output ->
                    var bytesCopied = 0L
                    val buffer = ByteArray(8 * 1024)
                    var bytes = input.read(buffer)
                    while (bytes >= 0) {
                        output.write(buffer, 0, bytes)
                        bytesCopied += bytes
                        if (totalBytes > 0) {
                            _state.value = VoiceModelState.Downloading(bytesCopied.toFloat() / totalBytes)
                        }
                        bytes = input.read(buffer)
                    }
                }
            }

            unzip(tempZip, modelDir)
            tempZip.delete()

            if (isModelValid()) {
                _state.value = VoiceModelState.Ready
            } else {
                modelDir.deleteRecursively()
                _state.value = VoiceModelState.Error("Model validation failed after extraction")
            }
        } catch (e: Exception) {
            Log.e("VoiceModelRepository", "Download failed", e)
            modelDir.deleteRecursively()
            _state.value = VoiceModelState.Error(e.message ?: "Unknown error")
        }
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

    private fun isModelValid(): Boolean =
        modelDir.exists() && modelDir.isDirectory && modelDir.listFiles()?.isNotEmpty() == true
}
