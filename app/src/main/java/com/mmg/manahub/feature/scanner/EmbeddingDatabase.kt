package com.mmg.manahub.feature.scanner

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
 */
@Singleton
class EmbeddingDatabase @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    companion object {
        internal const val EXPECTED_VERSION = 1
        internal const val EXPECTED_DIMS = 1024
        const val AMBIGUITY_GAP = 0.08f
        private const val TAG = "EmbeddingDatabase"
        private const val MAGIC = "MHEV"
    }

    private var scryfallIds: Array<String> = emptyArray()
    private var embeddings: FloatArray = FloatArray(0)
    private var dims: Int = 0

    @Volatile
    var cardCount: Int = 0
        private set

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
            // Log.w(TAG, "card_embeddings.bin not found")
        } catch (e: Exception) {
            // Log.e(TAG, "Failed to load", e)
        }
    }

    fun loadFromFile(file: File) {
        try {
            FileInputStream(file).use { fis ->
                val channel = fis.channel
                val buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size())
                loadFromBufferInternal(buffer, "file")
            }
        } catch (e: Exception) {
            // Log.e(TAG, "Failed to load", e)
        }
    }

    suspend fun findBestMatch(
        embedding: FloatArray,
        minSimilarity: Float = 0.70f,
    ): CardMatch? = withContext(Dispatchers.Default) {
        val (snapCount, snapDims, snapEmbeddings, snapIds) = synchronized(this@EmbeddingDatabase) {
            Quad(cardCount, dims, embeddings, scryfallIds)
        }

        if (snapCount == 0) return@withContext null

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

        if (bestIdx == -1 || bestSim < minSimilarity) return@withContext null

        CardMatch(
            scryfallId = snapIds[bestIdx],
            similarity = bestSim,
            secondBestSimilarity = if (secondSim == -Float.MAX_VALUE) 0f else secondSim,
        )
    }

    @VisibleForTesting
    internal fun getEmbeddingById(scryfallId: String): FloatArray? {
        val (snapCount, snapDims, snapEmbeddings, snapIds) = synchronized(this) {
            Quad(cardCount, dims, embeddings.copyOf(cardCount * dims), scryfallIds.copyOf(cardCount))
        }
        val idx = snapIds.indexOf(scryfallId)
        if (idx < 0) return null
        return FloatArray(snapDims) { snapEmbeddings[idx * snapDims + it] }
    }

    @Synchronized
    private fun loadFromBufferInternal(buf: ByteBuffer, source: String) {
        try {
            buf.order(ByteOrder.BIG_ENDIAN)
            val magic = ByteArray(4).also { buf.get(it) }
            if (!magic.contentEquals(MAGIC.toByteArray(Charsets.US_ASCII))) return

            val version = buf.get().toInt() and 0xFF
            if (version != EXPECTED_VERSION) return

            buf.order(ByteOrder.LITTLE_ENDIAN)
            val count = buf.int
            val fileDims = buf.int

            if (count < 0 || count > 100_000) return
            if (fileDims != EXPECTED_DIMS) return
            if (count == 0) return

            val ids = Array(count) { "" }
            val flat = FloatArray(count * fileDims)

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

            scryfallIds = ids
            embeddings = flat
            dims = fileDims
            cardCount = count
        } catch (e: Exception) {
            // Log.e
        }
    }

    private fun dotProduct(query: FloatArray, embeddingArray: FloatArray, baseIdx: Int, dimensions: Int): Float {
        var sum = 0f
        for (d in 0 until dimensions) sum += query[d] * embeddingArray[baseIdx + d]
        return sum
    }

    private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}
