package com.mmg.manahub.core.common

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests for [sha256Hex] against the standard NIST FIPS 180-4 test vectors — catches any bug in
 * the pure-Kotlin `commonMain` implementation (padding, endianness, rotate direction) immediately,
 * since a subtly-wrong SHA-256 would otherwise only surface as silent puzzle-guess mismatches.
 */
class Sha256Test {

    @Test
    fun `empty string matches the known NIST digest`() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            sha256Hex(""),
        )
    }

    @Test
    fun `abc matches the known NIST digest`() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            sha256Hex("abc"),
        )
    }

    @Test
    fun `same input always hashes to the same digest`() {
        assertEquals(sha256Hex("Lightning Bolt"), sha256Hex("Lightning Bolt"))
    }

    @Test
    fun `different inputs hash to different digests`() {
        assertEquals(false, sha256Hex("Lightning Bolt") == sha256Hex("Lightning Strike"))
    }

    @Test
    fun `digest is always 64 lowercase hex characters`() {
        val digest = sha256Hex("some normalized card name + a daily salt")
        assertEquals(64, digest.length)
        assertEquals(true, digest.all { it in "0123456789abcdef" })
    }
}
