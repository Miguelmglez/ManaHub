package com.mmg.manahub.feature.scanner

import android.content.Context
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * Unit tests for [HashDatabase].
 *
 * Uses [HashDatabase.loadFromBytes] (VisibleForTesting) to inject synthetic binary data
 * without requiring a real Android Context or assets. The Context mock is only needed
 * to satisfy the constructor — it is never called during these tests.
 *
 * Binary format tested:
 *   4 bytes magic: "MHSH"
 *   1 byte version (ignored)
 *   4 bytes count: uint32 little-endian
 *   Per entry: 8B UUID MSB (big-endian) + 8B UUID LSB (big-endian) + 32B hash (4 × big-endian long)
 */
class HashDatabaseTest {

    private val mockContext: Context = mockk(relaxed = true)

    private lateinit var hashDatabase: HashDatabase

    @Before
    fun setUp() {
        // Context is only stored — never invoked during loadFromBytes tests.
        hashDatabase = HashDatabase(mockContext)
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Helper: binary builder
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Builds a valid card_hashes.bin binary payload from a list of (scryfallId, hash) pairs.
     *
     * @param entries List of (UUID string, LongArray of 4 longs) pairs.
     * @return ByteArray with the full binary format ready for [HashDatabase.loadFromBytes].
     */
    private fun buildBinary(entries: List<Pair<String, LongArray>>): ByteArray {
        // Header: 4 magic + 1 version + 4 count = 9 bytes
        // Per entry: 8 (UUID MSB) + 8 (UUID LSB) + 32 (4 longs) = 48 bytes
        val totalSize = 9 + entries.size * 48
        val buf = ByteBuffer.allocate(totalSize).order(ByteOrder.BIG_ENDIAN)

        // Magic "MHSH"
        buf.put("MHSH".toByteArray(Charsets.US_ASCII))
        // Version 0x01 (ignored by the parser)
        buf.put(0x01.toByte())
        // Count: uint32 little-endian
        buf.order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(entries.size)
        buf.order(ByteOrder.BIG_ENDIAN)

        for ((uuidStr, hash) in entries) {
            val uuid = UUID.fromString(uuidStr)
            buf.putLong(uuid.mostSignificantBits)
            buf.putLong(uuid.leastSignificantBits)
            buf.putLong(hash[0])
            buf.putLong(hash[1])
            buf.putLong(hash[2])
            buf.putLong(hash[3])
        }

        return buf.array()
    }

    /** Returns a zero hash (all bits unset). */
    private fun zeroHash(): LongArray = LongArray(4) { 0L }

    /**
     * Flips exactly [n] bits in the first long of a hash copy, distributing them
     * across separate bit positions to guarantee a Hamming distance of exactly [n].
     */
    private fun flipBits(hash: LongArray, n: Int): LongArray {
        val result = hash.copyOf()
        var remaining = n
        var longIdx = 0
        var bit = 0
        while (remaining > 0) {
            if (bit == 64) {
                longIdx++
                bit = 0
            }
            result[longIdx] = result[longIdx] xor (1L shl bit)
            bit++
            remaining--
        }
        return result
    }

    private val sampleUuid1 = "11111111-1111-1111-1111-111111111111"
    private val sampleUuid2 = "22222222-2222-2222-2222-222222222222"

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 1 — loadFromBytes: entry count
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun loadFromBytes_emptyDatabase_cardCountIsZero() {
        // Arrange
        val bytes = buildBinary(emptyList())

        // Act
        hashDatabase.loadFromBytes(bytes)

        // Assert
        assertEquals(0, hashDatabase.cardCount)
    }

    @Test
    fun loadFromBytes_singleEntry_cardCountIsOne() {
        // Arrange
        val bytes = buildBinary(listOf(sampleUuid1 to zeroHash()))

        // Act
        hashDatabase.loadFromBytes(bytes)

        // Assert
        assertEquals(1, hashDatabase.cardCount)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 2 — loadFromBytes: validation
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun loadFromBytes_invalidMagic_doesNotLoad() {
        // Arrange — magic bytes "XXXX" instead of "MHSH"
        val bytes = buildBinary(listOf(sampleUuid1 to zeroHash()))
        bytes[0] = 'X'.code.toByte()
        bytes[1] = 'X'.code.toByte()
        bytes[2] = 'X'.code.toByte()
        bytes[3] = 'X'.code.toByte()

        // Act
        hashDatabase.loadFromBytes(bytes)

        // Assert: silently ignored; cardCount stays at 0
        assertEquals(0, hashDatabase.cardCount)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 3 — findBestMatch: exact and near matches
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun findBestMatch_exactHash_returnsMatch() = runTest {
        // Arrange
        val hash = LongArray(4) { 0xDEAD_BEEF_CAFE_1234L }
        hashDatabase.loadFromBytes(buildBinary(listOf(sampleUuid1 to hash)))

        // Act
        val result = hashDatabase.findBestMatch(hash, maxDistance = 20)

        // Assert
        assertNotNull(result)
        assertEquals(0, result!!.distance)
        assertEquals(sampleUuid1, result.scryfallId)
    }

    @Test
    fun findBestMatch_hammingDistance1_returnsMatch() = runTest {
        // Arrange
        val storedHash = zeroHash()
        val queryHash = flipBits(storedHash, 1) // exactly 1 bit different
        hashDatabase.loadFromBytes(buildBinary(listOf(sampleUuid1 to storedHash)))

        // Act
        val result = hashDatabase.findBestMatch(queryHash, maxDistance = 20)

        // Assert
        assertNotNull(result)
        assertEquals(1, result!!.distance)
    }

    @Test
    fun findBestMatch_distanceTooHigh_returnsNull() = runTest {
        // Arrange — flip 25 bits, maxDistance default is 20
        val storedHash = zeroHash()
        val queryHash = flipBits(storedHash, 25)
        hashDatabase.loadFromBytes(buildBinary(listOf(sampleUuid1 to storedHash)))

        // Act
        val result = hashDatabase.findBestMatch(queryHash, maxDistance = 20)

        // Assert: distance 25 > maxDistance 20, so null
        assertNull(result)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 4 — findBestMatch: edge cases
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun findBestMatch_emptyDatabase_returnsNull() = runTest {
        // Arrange — no loadFromBytes called; cardCount stays 0
        val queryHash = zeroHash()

        // Act
        val result = hashDatabase.findBestMatch(queryHash, maxDistance = 20)

        // Assert
        assertNull(result)
    }

    @Test
    fun findBestMatch_bestAndSecondBest_correctDistances() = runTest {
        // Arrange — card1 is 2 bits away, card2 is 10 bits away from query
        val baseHash = zeroHash()
        val card1Hash = flipBits(baseHash, 2)  // distance 2 from baseHash
        val card2Hash = flipBits(baseHash, 10) // distance 10 from baseHash
        hashDatabase.loadFromBytes(
            buildBinary(
                listOf(
                    sampleUuid1 to card1Hash,
                    sampleUuid2 to card2Hash,
                ),
            ),
        )

        // Act — query with the base hash (all zeros)
        val result = hashDatabase.findBestMatch(baseHash, maxDistance = 20)

        // Assert
        assertNotNull(result)
        assertEquals(sampleUuid1, result!!.scryfallId)
        assertEquals(2, result.distance)
        // secondBestDistance should be greater than bestDistance
        assertTrue(
            "secondBestDistance (${result.secondBestDistance}) should be > bestDistance (${result.distance})",
            result.secondBestDistance > result.distance,
        )
    }

    @Test
    fun findBestMatch_customMaxDistance_respectsThreshold() = runTest {
        // Arrange — stored hash is 15 bits away from query
        val storedHash = zeroHash()
        val queryHash = flipBits(storedHash, 15)
        hashDatabase.loadFromBytes(buildBinary(listOf(sampleUuid1 to storedHash)))

        // Act — maxDistance=10 is less than the actual distance of 15
        val result = hashDatabase.findBestMatch(queryHash, maxDistance = 10)

        // Assert: distance 15 > maxDistance 10 → null
        assertNull(result)
    }
}
