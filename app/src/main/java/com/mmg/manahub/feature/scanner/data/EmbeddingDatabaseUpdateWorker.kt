package com.mmg.manahub.feature.scanner.data

// COMMENTED OUT — replaced by ML Kit OCR. See CardRecognizer for the new pipeline.
/*
import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that checks Cloudflare R2 for a newer version of `card_embeddings.bin`
 * and downloads it when available.
 *
 * Version negotiation:
 * - Fetches a JSON manifest at [VERSION_URL] with `version` (int), `sha256` (hex), `count` (int),
 *   and `dims` (int) fields.
 * - If the server version is not greater than the locally persisted version, exits immediately
 *   with [Result.success] to avoid unnecessary downloads.
 *
 * On network or parse failures the worker returns [Result.retry] so WorkManager re-attempts
 * with its default exponential backoff policy.
 *
 * On success: verifies SHA-256 integrity, renames the temp file into place, hot-reloads
 * [EmbeddingDatabase], and persists the new version to [UserPreferencesDataStore].
 */
@HiltWorker
class EmbeddingDatabaseUpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val embeddingDatabase: EmbeddingDatabase,
    private val userPreferencesDataStore: UserPreferencesDataStore,
    private val okHttpClient: OkHttpClient,
) : CoroutineWorker(context, params) {

    companion object {

        /** Unique name used with [androidx.work.WorkManager.enqueueUniqueWork]. */
        const val WORK_NAME = "embedding_db_update"

        /** WorkData key for download progress (Float 0f–1f). Readable via [WorkInfo.progress]. */
        const val KEY_PROGRESS = "download_progress"

        private const val BASE_URL = "https://pub-d194f30a23634bec9379d34eeba2cd29.r2.dev"
        private const val VERSION_URL = "$BASE_URL/version.json"
        private const val BINARY_URL = "$BASE_URL/card_embeddings.bin"

        /** Fallback file size used when the server omits a Content-Length header. */
        private const val KNOWN_BINARY_BYTES = 228_433_949L

        private const val TAG = "EmbeddingDbWorker"

        /** Builds a one-time [OneTimeWorkRequest] constrained to network availability. */
        fun buildRequest(): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<EmbeddingDatabaseUpdateWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val localVersion = userPreferencesDataStore.getEmbeddingDbVersion()
            android.util.Log.i(TAG, "Starting update check — local version: $localVersion")

            // 1. Fetch version manifest from R2.
            val manifest = fetchVersionManifest()
            if (manifest == null) {
                android.util.Log.w(TAG, "Could not reach R2 version manifest — will retry")
                return@withContext Result.retry()
            }
            android.util.Log.i(TAG, "Server version: ${manifest.version}, local: $localVersion")

            if (manifest.version <= localVersion) {
                android.util.Log.i(
                    TAG,
                    "Already up to date (v$localVersion). Cards in memory: ${embeddingDatabase.cardCount}",
                )
                return@withContext Result.success()
            }

            // 2. Download binary to a temp file to avoid corrupting the live DB on failure.
            android.util.Log.i(TAG, "Downloading card_embeddings.bin v${manifest.version} from R2…")
            val tempFile = File(applicationContext.filesDir, "card_embeddings.bin.tmp")
            val downloaded = downloadBinary(tempFile)
            if (!downloaded) {
                android.util.Log.w(TAG, "Download failed — will retry")
                return@withContext Result.retry()
            }
            android.util.Log.i(TAG, "Download complete (${tempFile.length() / 1024} KB)")

            // 3. Verify SHA-256 integrity before accepting the file.
            val expectedSha256 = manifest.sha256
            if (expectedSha256 == null) {
                android.util.Log.w(TAG, "version.json missing sha256 field — skipping integrity check")
            } else {
                val actualSha256 = tempFile.computeSha256()
                if (actualSha256 != expectedSha256) {
                    tempFile.delete()
                    android.util.Log.e(
                        TAG,
                        "SHA-256 mismatch — expected=${expectedSha256.take(8)}…, " +
                            "got=${actualSha256.take(8)}…. File deleted.",
                    )
                    return@withContext Result.failure()
                }
                android.util.Log.i(TAG, "SHA-256 verified: ${actualSha256.take(8)}…")
            }

            // 4. Atomic rename into the final location.
            val finalFile = File(applicationContext.filesDir, "card_embeddings.bin")
            if (!tempFile.renameTo(finalFile)) {
                tempFile.delete()
                android.util.Log.w(TAG, "Rename failed — will retry")
                return@withContext Result.retry()
            }

            // 5. Hot-reload the in-memory embedding database.
            embeddingDatabase.loadFromFile(finalFile)
            android.util.Log.i(
                TAG,
                "Embedding DB hot-reloaded — ${embeddingDatabase.cardCount} cards now in memory",
            )

            // 6. Persist the new version so subsequent runs skip the download.
            userPreferencesDataStore.saveEmbeddingDbVersion(manifest.version)

            Result.success()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Update failed, will retry", e)
            Result.retry()
        }
    }

    private data class VersionManifest(
        val version: Int,
        val sha256: String?,
        val count: Int,
        val dims: Int,
    )

    /**
     * Fetches the version manifest from R2 and parses the JSON fields.
     * Returns null on any network or parse error.
     */
    private suspend fun fetchVersionManifest(): VersionManifest? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(VERSION_URL).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val json = JSONObject(body)
                val version = json.optInt("version", -1).takeIf { it > 0 } ?: return@withContext null
                val sha256 = json.optString("sha256", "").takeIf { it.isNotEmpty() }
                val count = json.optInt("count", 0)
                val dims = json.optInt("dims", 0)
                VersionManifest(version, sha256, count, dims)
            }
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Version fetch failed", e)
            null
        }
    }

    /**
     * Downloads the binary from [BINARY_URL] into [dest], reporting progress via [setProgress].
     *
     * Uses a dedicated [OkHttpClient] with a 5-minute read timeout.
     * Progress is emitted as a Float 0f–1f under key [KEY_PROGRESS].
     *
     * @return true on success, false on any network or I/O error.
     */
    private suspend fun downloadBinary(dest: File): Boolean = withContext(Dispatchers.IO) {
        val downloadClient = okHttpClient.newBuilder()
            .readTimeout(5, TimeUnit.MINUTES)
            .build()
        try {
            val request = Request.Builder().url(BINARY_URL).build()
            downloadClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext false
                val body = response.body ?: return@withContext false
                val totalBytes = body.contentLength().takeIf { it > 0 } ?: KNOWN_BINARY_BYTES
                var bytesRead = 0L
                var lastReportedProgress = -1f
                val buffer = ByteArray(65_536)
                dest.outputStream().use { out ->
                    body.byteStream().use { stream ->
                        while (true) {
                            val n = stream.read(buffer)
                            if (n == -1) break
                            out.write(buffer, 0, n)
                            bytesRead += n
                            val progress = (bytesRead.toFloat() / totalBytes).coerceIn(0f, 1f)
                            if (progress - lastReportedProgress >= 0.01f) {
                                lastReportedProgress = progress
                                setProgress(workDataOf(KEY_PROGRESS to progress))
                            }
                        }
                    }
                }
            }
            true
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Binary download failed", e)
            false
        }
    }

    /** Computes the SHA-256 hex digest of this file. */
    private fun File.computeSha256(): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        inputStream().use { stream ->
            val buffer = ByteArray(8192)
            var read: Int
            while (stream.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
*/
