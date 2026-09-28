package com.mmg.manahub.feature.collection.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.mmg.manahub.BuildConfig
import com.mmg.manahub.core.domain.collection.transfer.CollectionFileGateway
import com.mmg.manahub.core.domain.collection.transfer.FileTooLargeException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
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

    /**
     * Decodes while streaming instead of buffering the bytes and decoding once: a 5 MB document
     * buffered as bytes and then converted to a String peaks at roughly five times its own size
     * (a doubling ByteArrayOutputStream plus the UTF-16 copy) before parsing even starts.
     *
     * [maxBytes] is still enforced on the BYTE count, counted through a metering stream, because
     * that is what the user picked and what the error message says.
     */
    override suspend fun readText(location: String, maxBytes: Long): String = withContext(ioDispatcher) {
        val uri = Uri.parse(location)
        val stream = appContext.contentResolver.openInputStream(uri) ?: throw IOException("Cannot open document")
        val out = StringBuilder(reportedSize(uri, maxBytes))
        try {
            // A blocking read into another process honours neither the timeout nor cancellation, so
            // the only way to free the thread is to close the stream from outside it.
            withTimeout(READ_TIMEOUT_MS) {
                // Requires ioDispatcher to stay elastic: bound it (or limitedParallelism(1)) and
                // this closer queues behind the blocking read it exists to interrupt.
                val closer = launch {
                    try {
                        awaitCancellation()
                    } finally {
                        runCatching { stream.close() }
                    }
                }
                try {
                    val metered = MeteredInputStream(stream, maxBytes)
                    metered.reader(Charsets.UTF_8).use { reader ->
                        val buffer = CharArray(BUFFER_SIZE)
                        while (true) {
                            val read = reader.read(buffer)
                            if (read < 0) break
                            out.appendRange(buffer, 0, read)
                        }
                    }
                } finally {
                    closer.cancel()
                }
            }
        } catch (e: TimeoutCancellationException) {
            throw IOException("Document read timed out", e)
        } finally {
            runCatching { stream.close() }
        }
        out.toString()
    }

    /** The document's own size when the provider reports one, clamped so it cannot itself OOM. */
    private fun reportedSize(uri: Uri, maxBytes: Long): Int = runCatching {
        appContext.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
            ?.takeIf { it > 0 }
            ?.coerceAtMost(maxBytes)
            ?.toInt()
    }.getOrNull() ?: DEFAULT_TEXT_CAPACITY

    /** Fails the read as soon as more than [maxBytes] have been pulled, before decoding them. */
    private class MeteredInputStream(
        private val delegate: java.io.InputStream,
        private val maxBytes: Long,
    ) : java.io.InputStream() {
        private var total = 0L

        override fun read(): Int = delegate.read().also { if (it >= 0) count(1) }

        override fun read(b: ByteArray, off: Int, len: Int): Int =
            delegate.read(b, off, len).also { if (it > 0) count(it.toLong()) }

        private fun count(read: Long) {
            total += read
            if (total > maxBytes) throw FileTooLargeException(maxBytes)
        }
    }

    override suspend fun writeText(location: String, content: String) = withContext(ioDispatcher) {
        // "wt" truncates: a shorter export must not leave the tail of a previous one behind.
        val stream = appContext.contentResolver.openOutputStream(Uri.parse(location), "wt")
            ?: throw IOException("Cannot open document")
        stream.use { it.write(content.toByteArray(Charsets.UTF_8)) }
    }

    /**
     * Each export gets its own timestamped sub-directory, so the visible file name stays clean while
     * a URI another app was granted keeps resolving. Some apps (Gmail) only read the stream when the
     * message is sent, long after the next export would have overwritten a shared name.
     */
    override suspend fun writeShareableFile(fileName: String, content: String): String = withContext(ioDispatcher) {
        val root = File(appContext.cacheDir, EXPORT_DIR).apply { mkdirs() }
        prune(root)
        // The allowlist alone still permits ".."; reject any all-dot name outright.
        val safeName = fileName.filter { it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' }
            .takeIf { it.isNotBlank() && it.any { c -> c != '.' } }
            ?: "manahub-export.txt"
        val dir = File(root, System.currentTimeMillis().toString()).apply { mkdirs() }
        val file = File(dir, safeName)
        file.writeText(content, Charsets.UTF_8)
        // BuildConfig.APPLICATION_ID, not packageName: this must track the manifest's
        // ${applicationId}.fileprovider authority even under a build-type suffix.
        FileProvider.getUriForFile(appContext, "${BuildConfig.APPLICATION_ID}.fileprovider", file).toString()
    }

    private fun prune(root: File) {
        val existing = root.listFiles()?.sortedByDescending { it.lastModified() } ?: return
        val cutoff = System.currentTimeMillis() - MAX_EXPORT_AGE_MS
        existing.forEachIndexed { index, entry ->
            if (index >= MAX_EXPORTS_KEPT - 1 || entry.lastModified() < cutoff) entry.deleteRecursively()
        }
    }

    private companion object {
        const val EXPORT_DIR = "exports"
        const val BUFFER_SIZE = 16 * 1024
        const val DEFAULT_TEXT_CAPACITY = 64 * 1024
        const val MAX_EXPORTS_KEPT = 5
        const val MAX_EXPORT_AGE_MS = 24L * 60 * 60 * 1000

        /** Generous for the 5 MB ceiling; a provider that has not delivered by then is stalled. */
        const val READ_TIMEOUT_MS = 45_000L
    }
}
