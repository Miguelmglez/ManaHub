package com.mmg.manahub.feature.scanner

// COMMENTED OUT — replaced by ML Kit OCR. See CardRecognizer for the new pipeline.
/*
import android.content.Context
import androidx.annotation.VisibleForTesting
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory database of L2-normalised card embedding vectors for cosine nearest-neighbour search.
 *
 * Binary format of `assets/card_embeddings.bin` (and downloaded `card_embeddings.bin`):
 * ```
 * Header (13 bytes):
 *   [0..3]  magic   = "MHEV" (ASCII, 4 bytes)
 *   [4]     version = 1      (uint8)
 *   [5..8]  count   = N      (uint32, little-endian)
 *   [9..12] dims    = 1024   (uint32, little-endian)
 *
 * Per entry (N × (16 + dims×4) bytes):
 *   [0..7]   UUID MSB  (big-endian int64)
 *   [8..15]  UUID LSB  (big-endian int64)
 *   [16..]   embedding (dims × float32, little-endian, L2-normalised)
 * ```
 *
 * Embeddings are L2-normalised at generation time, so cosine similarity equals the
 * dot product of two stored unit vectors — no division needed at query time.
 *
 * Thread safety: [loadFromAssets] or [loadFromFile] may be called on any thread.
 * The internal load method is @Synchronized.  [findBestMatch] takes a consistent
 * snapshot under the same lock before scanning, so a concurrent reload cannot
 * corrupt an in-progress search.
 *
 * @param context Application context used to open the asset on first run.
 */
@Singleton
class EmbeddingDatabase @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    companion object {
        /** Binary format version this runtime expects. Reject any other value. */
        internal const val EXPECTED_VERSION = 1

        /**
         * Expected embedding dimension. Must match [com.mmg.manahub.feature.scanner.CardEmbeddingModel]'s
         * OUTPUT_DIMS. A downloaded binary with a different dim count is rejected to prevent
         * silent garbage results from mismatched model/database versions.
         */
        internal const val EXPECTED_DIMS = 1024

        /**
         * Maximum cosine-similarity gap below which a match is considered ambiguous.
         *
         * Raised from 0.05 → 0.08: with a generic MobileNetV3-Small backbone the top-2
         * scores for visually similar cards (same artist, same colour identity) are often
         * only 0.04–0.06 apart.  A wider gap forces the pipeline to demand more separation
         * before it commits to a single card, which reduces false-positive confirmations.
         */
        const val AMBIGUITY_GAP = 0.08f

        private const val TAG = "EmbeddingDatabase"
        private const val MAGIC = "MHEV"
    }

    /** Scryfall UUIDs in the same order as [embeddings]. */
    private var scryfallIds: Array<String> = emptyArray()

    /**
     * Flat array of L2-normalised embedding vectors.
     * Entry [i] occupies indices `[i*dims .. i*dims+dims-1]`.
     */
    private var embeddings: FloatArray = FloatArray(0)

    /** Embedding dimensionality — read from the file header (expected: 576). */
    private var dims: Int = 0

    /** Number of cards currently loaded. Zero if the asset is missing or the DB is empty. */
    @Volatile
    var cardCount: Int = 0
        private set

    // ─────────────────────────────────────────────────────────────────────────
    //  Public load API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Loads the embedding database from `assets/card_embeddings.bin`.
     *
     * Safe to call when the asset is absent — logs a warning and leaves [cardCount] at 0
     * (scanner will be disabled until a download succeeds).
     */
    fun loadFromAssets() {
        try {
            context.assets.openFd("card_embeddings.bin").use { afd ->
                FileInputStream(afd.fileDescriptor).use { fis ->
                    val channel = fis.channel
                    val buffer = channel.map(FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.length)
                    loadFromBufferInternal(buffer, "assets")
                }
            }
        } catch (e: java.io.FileNotFoundException) {
            android.util.Log.w(TAG, "card_embeddings.bin not found in assets — ML scanner disabled on first launch")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to load card_embeddings.bin from assets", e)
        }
    }

    /**
     * Reloads the embedding database from a previously downloaded [file].
     *
     * Called by [EmbeddingDatabaseUpdateWorker] after a successful Cloudflare R2 download.
     * Thread-safe: the internal load method is @Synchronized.
     */
    fun loadFromFile(file: File) {
        try {
            FileInputStream(file).use { fis ->
                val channel = fis.channel
                val buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size())
                loadFromBufferInternal(buffer, "file")
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to load embedding database from file: ${file.absolutePath}", e)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Query API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Searches the database for the card whose embedding has the highest cosine similarity
     * to [embedding].
     *
     * Runs on [Dispatchers.Default] (CPU-bound linear dot-product scan over ~55 K unit vectors).
     *
     * @param embedding     L2-normalised query vector produced by [CardEmbeddingModel.extract].
     * @param minSimilarity Minimum cosine similarity required to accept a match (default 0.70).
     *                      Raised from the original 0.72 to reduce false-positive identifications.
     *                      A MobileNetV3-Small backbone trained on ImageNet produces embeddings
     *                      that are not highly discriminative for MTG card art; accepting weak
     *                      matches at 0.72 led to wrong cards being confirmed.  At 0.80 the
     *                      scanner correctly rejects ambiguous frames and waits for a cleaner shot.
     * @return [CardMatch] with the best matching scryfallId and similarity scores, or null if
     *         the best candidate falls below [minSimilarity] or the database is empty.
     */
    suspend fun findBestMatch(
        embedding: FloatArray,
        minSimilarity: Float = 0.70f,
    ): CardMatch? = withContext(Dispatchers.Default) {
        // Take a consistent snapshot so that a concurrent loadFromFile cannot leave us
        // iterating with a mismatched ids/embeddings/cardCount triple.
        val (snapCount, snapDims, snapEmbeddings, snapIds) = synchronized(this@EmbeddingDatabase) {
            Quad(cardCount, dims, embeddings, scryfallIds)
        }

        if (snapCount == 0) {
            android.util.Log.d(TAG, "findBestMatch: database is empty")
            return@withContext null
        }

        var bestSim = -Float.MAX_VALUE
        var bestIdx = -1
        var secondSim = -Float.MAX_VALUE

        for (i in 0 until snapCount) {
            val sim = dotProduct(embedding, snapEmbeddings, i * snapDims, snapDims)
            when {
                sim > bestSim -> {
                    secondSim = bestSim
                    bestSim = sim
                    bestIdx = i
                }
                sim > secondSim -> secondSim = sim
            }
        }

        android.util.Log.d(
            TAG,
            "findBestMatch: bestSim=%.4f, secondSim=%.4f, gap=%.4f, threshold=%.2f"
                .format(bestSim, secondSim, bestSim - secondSim, minSimilarity),
        )

        if (bestIdx == -1 || bestSim < minSimilarity) return@withContext null

        CardMatch(
            scryfallId = snapIds[bestIdx],
            similarity = bestSim,
            secondBestSimilarity = if (secondSim == -Float.MAX_VALUE) 0f else secondSim,
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Test helper
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns the stored embedding for the given [scryfallId], or null if not found.
     *
     * Exposed for instrumented tests only — not part of the public API.
     */
    @VisibleForTesting
    internal fun getEmbeddingById(scryfallId: String): FloatArray? {
        val (snapCount, snapDims, snapEmbeddings, snapIds) = synchronized(this) {
            Quad(cardCount, dims, embeddings.copyOf(cardCount * dims), scryfallIds.copyOf(cardCount))
        }
        val idx = snapIds.indexOf(scryfallId)
        if (idx < 0) return null
        return FloatArray(snapDims) { snapEmbeddings[idx * snapDims + it] }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Internal binary parser
    // ─────────────────────────────────────────────────────────────────────────

    @Synchronized
    private fun loadFromBufferInternal(buf: ByteBuffer, source: String) {
        try {
            buf.order(ByteOrder.BIG_ENDIAN) // Start with default order

            // ── Validate magic header "MHEV" ──────────────────────────────
            val magic = ByteArray(4).also { buf.get(it) }
            if (!magic.contentEquals(MAGIC.toByteArray(Charsets.US_ASCII))) {
                android.util.Log.e(TAG, "Invalid magic bytes — expected '$MAGIC', rejecting file (source=$source)")
                return
            }

            // ── Validate version byte ─────────────────────────────────────
            val version = buf.get().toInt() and 0xFF
            if (version != EXPECTED_VERSION) {
                android.util.Log.w(
                    TAG,
                    "card_embeddings.bin version=$version; expected $EXPECTED_VERSION — rejecting. " +
                        "Regenerate with tools/embedding-generator/generate_embeddings.py",
                )
                return
            }

            // ── Read count and dims (little-endian) ───────────────────────
            buf.order(ByteOrder.LITTLE_ENDIAN)
            val count = buf.getINT()
            val fileDims = buf.getINT()

            // ── Guard against implausible values ──────────────────────────
            if (count < 0 || count > 100_000) {
                android.util.Log.e(
                    TAG,
                    "Rejected card_embeddings.bin: count=$count is outside safe range [0, 100000]",
                )
                return
            }

            if (fileDims != EXPECTED_DIMS) {
                android.util.Log.e(
                    TAG,
                    "Rejected card_embeddings.bin: dims=$fileDims does not match model output ($EXPECTED_DIMS). " +
                        "Re-download the database or update the app.",
                )
                return
            }

            if (count == 0) {
                android.util.Log.e(TAG, "Empty embedding database from $source — ML scanner disabled")
                return
            }

            // ── Allocate and parse entries ────────────────────────────────
            val resolvedSource = if (source == "file") "download:v$version" else source
            val ids = Array(count) { "" }
            val flat = FloatArray(count * fileDims)

            // UUID fields are big-endian; embeddings are little-endian
            for (i in 0 until count) {
                buf.order(ByteOrder.BIG_ENDIAN)
                val msb = buf.long
                val lsb = buf.long
                ids[i] = UUID(msb, lsb).toString()

                buf.order(ByteOrder.LITTLE_ENDIAN)
                val base = i * fileDims
                for (d in 0 until fileDims) {
                    flat[base + d] = buf.float
                }
            }

            // ── Atomically commit all three fields ────────────────────────
            scryfallIds = ids
            embeddings = flat
            dims = fileDims
            cardCount = count

            android.util.Log.i(
                TAG,
                "Active embedding DB: count=$count, version=$version, source=$resolvedSource, " +
                    "dims=$fileDims",
            )
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to parse embedding database (source=$source)", e)
        }
    }

    /** Extension to handle ByteBuffer int reading with explicit order if needed. */
    private fun ByteBuffer.getINT(): Int = int

    // ─────────────────────────────────────────────────────────────────────────
    //  Math utilities
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Computes the dot product between [query] and the vector stored at offset [baseIdx]
     * in the flat [embeddingArray]. Both vectors must be L2-normalised — this equals
     * their cosine similarity.
     */
    private fun dotProduct(
        query: FloatArray,
        embeddingArray: FloatArray,
        baseIdx: Int,
        dimensions: Int,
    ): Float {
        var sum = 0f
        for (d in 0 until dimensions) {
            sum += query[d] * embeddingArray[baseIdx + d]
        }
        return sum
    }

    /** Lightweight tuple to take a consistent 4-field snapshot under a single lock. */
    private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}
*/
