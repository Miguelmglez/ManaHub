package com.mmg.manahub.feature.scanner

import android.content.Context
import androidx.annotation.VisibleForTesting
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Represents the result of a perceptual-hash lookup against the local card database.
 *
 * @property scryfallId        Scryfall UUID of the best-matching card.
 * @property distance          Hamming distance between the query hash and the best match (0 = identical).
 * @property secondBestDistance Hamming distance of the second-best match; used to compute ambiguity.
 */
data class HashMatch(
    val scryfallId: String,
    val distance: Int,
    val secondBestDistance: Int = Int.MAX_VALUE,
)

/**
 * In-memory database of 256-bit perceptual hashes for MTG card artwork.
 *
 * Binary format of `assets/card_hashes.bin`:
 * - 4 bytes magic:  "MHSH" (ASCII)
 * - 1 byte version: (ignored, reserved for future use)
 * - 4 bytes count:  uint32 little-endian, number of entries
 * - Per entry (repeating):
 *   - 8 bytes UUID MSB  (big-endian long)
 *   - 8 bytes UUID LSB  (big-endian long)
 *   - 32 bytes hash     (4 × big-endian long = 256 bits, MSB first)
 *
 * The hash bit order matches the Python generator:
 *   `int.to_bytes(32, 'big')` → bit 0 of the integer is the MSB of byte 0.
 *
 * Thread safety: [loadFromAssets] must be called once before any [findBestMatch] calls.
 * After loading, all public state is read-only and safe for concurrent access.
 */
@Singleton
class HashDatabase @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** Scryfall UUIDs in the same order as [hashes]. */
    private var scryfallIds: Array<String> = emptyArray()

    /**
     * Flat array of 256-bit hashes stored as 4 longs per card.
     * Entry [i] occupies indices [i*4 .. i*4+3].
     */
    private var hashes: LongArray = LongArray(0)

    /** Number of cards loaded from the binary asset. Zero if the asset is missing. */
    @Volatile var cardCount: Int = 0
        private set

    /**
     * Loads the hash database from `assets/card_hashes.bin`.
     *
     * Safe to call when the asset is missing — logs a warning and leaves [cardCount] at 0.
     * Must be called before any [findBestMatch] invocation (done automatically by [ScannerModule]).
     */
    fun loadFromAssets() {
        try {
            val bytes = context.assets.open("card_hashes.bin").readBytes()
            loadFromBytesInternal(bytes)
        } catch (e: java.io.FileNotFoundException) {
            android.util.Log.w(
                "HashDatabase",
                "card_hashes.bin not found in assets — pHash scanner disabled",
            )
        } catch (e: Exception) {
            android.util.Log.e("HashDatabase", "Failed to load card_hashes.bin", e)
        }
    }

    /**
     * Loads the hash database from a [ByteArray].
     *
     * This method is intended **for testing only** — it replaces the asset loading path so that
     * unit tests can inject a synthetic binary without needing an Android [Context] or real assets.
     *
     * The byte format is identical to `assets/card_hashes.bin`:
     * - 4 bytes magic: "MHSH" (ASCII)
     * - 1 byte version (ignored)
     * - 4 bytes count: uint32 little-endian
     * - Per entry: 8B UUID MSB + 8B UUID LSB + 32B hash (4 × big-endian long)
     *
     * On format errors this method silently leaves [cardCount] at 0.
     */
    @VisibleForTesting
    internal fun loadFromBytes(bytes: ByteArray) {
        loadFromBytesInternal(bytes)
    }

    private fun loadFromBytesInternal(bytes: ByteArray) {
        try {
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)

            // Validate magic header "MHSH"
            val magic = ByteArray(4).also { buf.get(it) }
            if (!magic.contentEquals("MHSH".toByteArray(Charsets.US_ASCII))) {
                android.util.Log.e("HashDatabase", "Invalid magic bytes in card_hashes.bin")
                return
            }

            // Skip version byte (reserved)
            buf.get()

            // Count is uint32 little-endian
            buf.order(ByteOrder.LITTLE_ENDIAN)
            val count = buf.int
            buf.order(ByteOrder.BIG_ENDIAN)

            val ids = Array(count) { "" }
            val hashFlat = LongArray(count * 4)

            for (i in 0 until count) {
                // UUID: MSB + LSB both big-endian longs
                val msb = buf.long
                val lsb = buf.long
                ids[i] = UUID(msb, lsb).toString()

                // 256-bit hash: 4 big-endian longs (MSB first)
                hashFlat[i * 4 + 0] = buf.long
                hashFlat[i * 4 + 1] = buf.long
                hashFlat[i * 4 + 2] = buf.long
                hashFlat[i * 4 + 3] = buf.long
            }

            scryfallIds = ids
            hashes = hashFlat
            cardCount = count
            android.util.Log.i("HashDatabase", "Loaded $count card hashes from assets")
        } catch (e: java.io.FileNotFoundException) {
            android.util.Log.w(
                "HashDatabase",
                "card_hashes.bin not found in assets — pHash scanner disabled",
            )
        } catch (e: Exception) {
            android.util.Log.e("HashDatabase", "Failed to load card_hashes.bin", e)
        }
    }

    /**
     * Searches the database for the card whose hash is closest (minimum Hamming distance)
     * to [queryHash].
     *
     * Runs on [Dispatchers.Default] (CPU-bound linear scan).
     *
     * @param queryHash   256-bit DCT hash packed as 4 longs (MSB first).
     * @param maxDistance Maximum acceptable Hamming distance (default 20 out of 256).
     * @return [HashMatch] if a card within [maxDistance] is found, null otherwise.
     */
    suspend fun findBestMatch(queryHash: LongArray, maxDistance: Int = 20): HashMatch? =
        withContext(Dispatchers.Default) {
            if (cardCount == 0) return@withContext null

            var bestDist = Int.MAX_VALUE
            var bestIdx = -1
            var secondDist = Int.MAX_VALUE

            for (i in 0 until cardCount) {
                val dist = hammingDistance(queryHash, i)
                when {
                    dist < bestDist -> {
                        secondDist = bestDist
                        bestDist = dist
                        bestIdx = i
                    }
                    dist < secondDist -> secondDist = dist
                }
            }

            if (bestIdx == -1 || bestDist > maxDistance) return@withContext null

            HashMatch(
                scryfallId = scryfallIds[bestIdx],
                distance = bestDist,
                secondBestDistance = secondDist,
            )
        }

    /**
     * Computes the Hamming distance between [query] and the hash at database index [idx].
     * Each of the 4 longs is XOR-ed and the total bit-count of set bits is returned.
     */
    private fun hammingDistance(query: LongArray, idx: Int): Int {
        val base = idx * 4
        return java.lang.Long.bitCount(query[0] xor hashes[base + 0]) +
            java.lang.Long.bitCount(query[1] xor hashes[base + 1]) +
            java.lang.Long.bitCount(query[2] xor hashes[base + 2]) +
            java.lang.Long.bitCount(query[3] xor hashes[base + 3])
    }
}
