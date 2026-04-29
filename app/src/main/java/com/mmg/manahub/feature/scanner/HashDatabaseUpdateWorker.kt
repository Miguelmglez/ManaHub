package com.mmg.manahub.feature.scanner

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
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
 * WorkManager worker that checks a Cloudflare R2 public bucket for a newer version
 * of `card_hashes.bin` and downloads it when available.
 *
 * Version negotiation uses a JSON manifest at [VERSION_URL] with a `"version"` integer
 * field. If the server version is not greater than the locally persisted version, the
 * worker exits immediately with [Result.success].
 *
 * On any network or parse failure the worker returns [Result.retry] so that
 * WorkManager re-attempts with its default backoff policy.
 */
@HiltWorker
class HashDatabaseUpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val hashDatabase: HashDatabase,
    private val userPreferencesDataStore: UserPreferencesDataStore,
    private val okHttpClient: OkHttpClient,
) : CoroutineWorker(context, params) {

    companion object {

        /** Unique name used with [androidx.work.WorkManager.enqueueUniqueWork]. */
        const val WORK_NAME = "hash_db_update"

        private const val BASE_URL = "https://pub-d194f30a23634bec9379d34eeba2cd29.r2.dev"
        private const val VERSION_URL = "$BASE_URL/card_hashes/version.json"
        private const val BINARY_URL = "$BASE_URL/card_hashes/card_hashes.bin"

        /** Builds a one-time [OneTimeWorkRequest] constrained to network availability. */
        fun buildRequest(): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<HashDatabaseUpdateWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // 1. Fetch version manifest from R2.
            val serverVersion = fetchServerVersion() ?: return@withContext Result.retry()
            val localVersion = userPreferencesDataStore.getHashDbVersion()

            if (serverVersion <= localVersion) {
                // Already up to date — nothing to do.
                return@withContext Result.success()
            }

            // 2. Download binary to a temp file to avoid corrupting the live DB on failure.
            val tempFile = File(applicationContext.filesDir, "card_hashes.bin.tmp")
            val downloaded = downloadBinary(tempFile)
            if (!downloaded) return@withContext Result.retry()

            // 3. Atomic rename into the final location.
            val finalFile = File(applicationContext.filesDir, "card_hashes.bin")
            if (!tempFile.renameTo(finalFile)) {
                tempFile.delete()
                return@withContext Result.retry()
            }

            // 4. Hot-reload the in-memory hash database.
            hashDatabase.loadFromFile(finalFile)

            // 5. Persist the new version so subsequent runs skip the download.
            userPreferencesDataStore.saveHashDbVersion(serverVersion)

            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("HashDatabaseUpdateWorker", "Update failed, will retry", e)
            Result.retry()
        }
    }

    /**
     * Fetches the version manifest from R2 and returns the `"version"` integer,
     * or `null` on any network or parse error.
     */
    private suspend fun fetchServerVersion(): Int? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(VERSION_URL).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                JSONObject(body).optInt("version", -1).takeIf { it > 0 }
            }
        } catch (e: Exception) {
            android.util.Log.w("HashDatabaseUpdateWorker", "Version fetch failed", e)
            null
        }
    }

    /**
     * Downloads the binary from [BINARY_URL] and writes it to [dest].
     * Uses a dedicated client with a 5-minute read timeout (the shared client's
     * 10s default is too short for a multi-MB binary over a slow connection).
     * Returns `true` on success, `false` on any network or I/O error.
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
                dest.outputStream().use { out -> body.byteStream().copyTo(out) }
            }
            true
        } catch (e: Exception) {
            android.util.Log.w("HashDatabaseUpdateWorker", "Binary download failed", e)
            false
        }
    }
}
