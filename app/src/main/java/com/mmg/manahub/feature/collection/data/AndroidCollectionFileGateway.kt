package com.mmg.manahub.feature.collection.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.mmg.manahub.core.domain.collection.transfer.CollectionFileGateway
import com.mmg.manahub.core.domain.collection.transfer.FileTooLargeException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * [CollectionFileGateway] over the Storage Access Framework (picked documents) and a FileProvider
 * that exposes only `cache/exports/`.
 */
class AndroidCollectionFileGateway(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher,
) : CollectionFileGateway {

    private val appContext = context.applicationContext

    override suspend fun readText(location: String, maxBytes: Long): String = withContext(ioDispatcher) {
        val stream = appContext.contentResolver.openInputStream(Uri.parse(location))
            ?: throw IOException("Cannot open document")
        stream.use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            val out = java.io.ByteArrayOutputStream()
            var total = 0L
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > maxBytes) throw FileTooLargeException(maxBytes)
                out.write(buffer, 0, read)
            }
            out.toString(Charsets.UTF_8.name())
        }
    }

    override suspend fun writeText(location: String, content: String) = withContext(ioDispatcher) {
        // "wt" truncates: a shorter export must not leave the tail of a previous one behind.
        val stream = appContext.contentResolver.openOutputStream(Uri.parse(location), "wt")
            ?: throw IOException("Cannot open document")
        stream.use { it.write(content.toByteArray(Charsets.UTF_8)) }
    }

    override suspend fun writeShareableFile(fileName: String, content: String): String = withContext(ioDispatcher) {
        val dir = File(appContext.cacheDir, EXPORT_DIR).apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val safeName = fileName.filter { it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' }
            .ifBlank { "manahub-export.txt" }
        val file = File(dir, safeName)
        file.writeText(content, Charsets.UTF_8)
        FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", file).toString()
    }

    private companion object {
        const val EXPORT_DIR = "exports"
        const val BUFFER_SIZE = 16 * 1024
    }
}
