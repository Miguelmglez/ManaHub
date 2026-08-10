@file:OptIn(ExperimentalUnsignedTypes::class)

package com.mmg.manahub.core.common

/**
 * Pure-Kotlin, dependency-free SHA-256 (NIST FIPS 180-4) implementation.
 *
 * Lives entirely in `commonMain` with NO `expect`/`actual` split. SHA-256 is pure bit arithmetic
 * over bytes, so a single implementation works identically on every current and future KMP target
 * (Android, wasmJs, plain jvm, and beyond) with zero per-platform code. This module targets THREE
 * platforms (android/wasmJs/jvm — see this module's `build.gradle.kts`); an `expect` here would
 * require a real `actual` on every one of them or the missing target fails to compile outright
 * (`jvmMain` in particular carries no platform code today). A pure-common implementation sidesteps
 * that risk entirely.
 *
 * Used by the Daily Puzzle feature's `SubmitPuzzleGuessUseCase` to hash a normalized guessed card
 * name for comparison against a puzzle's `answerNameHash`, without ever pulling the plaintext
 * answer name into memory client-side.
 */

/** Round constants — first 32 bits of the fractional parts of the cube roots of the first 64 primes. */
private val SHA256_K: UIntArray = uintArrayOf(
    0x428a2f98u, 0x71374491u, 0xb5c0fbcfu, 0xe9b5dba5u, 0x3956c25bu, 0x59f111f1u, 0x923f82a4u, 0xab1c5ed5u,
    0xd807aa98u, 0x12835b01u, 0x243185beu, 0x550c7dc3u, 0x72be5d74u, 0x80deb1feu, 0x9bdc06a7u, 0xc19bf174u,
    0xe49b69c1u, 0xefbe4786u, 0x0fc19dc6u, 0x240ca1ccu, 0x2de92c6fu, 0x4a7484aau, 0x5cb0a9dcu, 0x76f988dau,
    0x983e5152u, 0xa831c66du, 0xb00327c8u, 0xbf597fc7u, 0xc6e00bf3u, 0xd5a79147u, 0x06ca6351u, 0x14292967u,
    0x27b70a85u, 0x2e1b2138u, 0x4d2c6dfcu, 0x53380d13u, 0x650a7354u, 0x766a0abbu, 0x81c2c92eu, 0x92722c85u,
    0xa2bfe8a1u, 0xa81a664bu, 0xc24b8b70u, 0xc76c51a3u, 0xd192e819u, 0xd6990624u, 0xf40e3585u, 0x106aa070u,
    0x19a4c116u, 0x1e376c08u, 0x2748774cu, 0x34b0bcb5u, 0x391c0cb3u, 0x4ed8aa4au, 0x5b9cca4fu, 0x682e6ff3u,
    0x748f82eeu, 0x78a5636fu, 0x84c87814u, 0x8cc70208u, 0x90befffau, 0xa4506cebu, 0xbef9a3f7u, 0xc67178f2u,
)

/** Initial hash value — first 32 bits of the fractional parts of the square roots of the first 8 primes. */
private val SHA256_H0: UIntArray = uintArrayOf(
    0x6a09e667u, 0xbb67ae85u, 0x3c6ef372u, 0xa54ff53au,
    0x510e527fu, 0x9b05688cu, 0x1f83d9abu, 0x5be0cd19u,
)

/** Computes the SHA-256 digest of [input] (UTF-8 encoded) and returns it as a lowercase hex string. */
fun sha256Hex(input: String): String {
    val digest = sha256(input.encodeToByteArray())
    val hex = StringBuilder(64)
    for (byte in digest) {
        val value = byte.toInt() and 0xff
        hex.append(HEX_CHARS[value ushr 4])
        hex.append(HEX_CHARS[value and 0x0f])
    }
    return hex.toString()
}

private val HEX_CHARS = "0123456789abcdef"

/** Computes the raw 32-byte SHA-256 digest of [message]. */
private fun sha256(message: ByteArray): ByteArray {
    val h = SHA256_H0.copyOf()

    // --- Padding: append 0x80, zero-pad until length % 64 == 56, then an 8-byte big-endian bit length. ---
    val bitLength = message.size.toULong() * 8u
    var paddedLength = message.size + 1
    while (paddedLength % 64 != 56) paddedLength++
    paddedLength += 8

    val padded = ByteArray(paddedLength)
    message.copyInto(padded)
    padded[message.size] = 0x80.toByte()
    for (i in 0 until 8) {
        padded[paddedLength - 1 - i] = ((bitLength shr (8 * i)) and 0xffu).toByte()
    }

    val w = UIntArray(64)
    var offset = 0
    while (offset < padded.size) {
        for (i in 0 until 16) {
            val base = offset + i * 4
            w[i] = (byteAsUInt(padded[base]) shl 24) or
                (byteAsUInt(padded[base + 1]) shl 16) or
                (byteAsUInt(padded[base + 2]) shl 8) or
                byteAsUInt(padded[base + 3])
        }
        for (i in 16 until 64) {
            val s0 = w[i - 15].rotateRight(7) xor w[i - 15].rotateRight(18) xor (w[i - 15] shr 3)
            val s1 = w[i - 2].rotateRight(17) xor w[i - 2].rotateRight(19) xor (w[i - 2] shr 10)
            w[i] = w[i - 16] + s0 + w[i - 7] + s1
        }

        var a = h[0]; var b = h[1]; var c = h[2]; var d = h[3]
        var e = h[4]; var f = h[5]; var g = h[6]; var hh = h[7]

        for (i in 0 until 64) {
            val s1 = e.rotateRight(6) xor e.rotateRight(11) xor e.rotateRight(25)
            val ch = (e and f) xor (e.inv() and g)
            val temp1 = hh + s1 + ch + SHA256_K[i] + w[i]
            val s0 = a.rotateRight(2) xor a.rotateRight(13) xor a.rotateRight(22)
            val maj = (a and b) xor (a and c) xor (b and c)
            val temp2 = s0 + maj

            hh = g; g = f; f = e; e = d + temp1
            d = c; c = b; b = a; a = temp1 + temp2
        }

        h[0] += a; h[1] += b; h[2] += c; h[3] += d
        h[4] += e; h[5] += f; h[6] += g; h[7] += hh

        offset += 64
    }

    val result = ByteArray(32)
    for (i in 0 until 8) {
        result[i * 4] = (h[i] shr 24).toByte()
        result[i * 4 + 1] = (h[i] shr 16).toByte()
        result[i * 4 + 2] = (h[i] shr 8).toByte()
        result[i * 4 + 3] = h[i].toByte()
    }
    return result
}

/** Widens [byte] to [UInt] via its unsigned (0..255) value — never sign-extended. */
private fun byteAsUInt(byte: Byte): UInt = (byte.toInt() and 0xff).toUInt()
