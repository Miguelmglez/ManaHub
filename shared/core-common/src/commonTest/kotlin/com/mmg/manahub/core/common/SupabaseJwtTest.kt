package com.mmg.manahub.core.common

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for [decodeIsAnonymousClaim].
 *
 * Fixtures are hand-built fake JWTs (`header.payload.signature`, base64url-encoded, no padding —
 * the signature segment is never read so it's left as an arbitrary placeholder). [encodeBase64Url]
 * is a tiny test-only mirror of the production decoder, kept dependency-free for the same
 * cross-target (Android/wasmJs/JVM) portability reason documented on [decodeIsAnonymousClaim].
 */
class SupabaseJwtTest {

    private fun fakeJwt(payloadJson: String): String {
        val header = encodeBase64Url("""{"alg":"HS256","typ":"JWT"}""")
        val payload = encodeBase64Url(payloadJson)
        return "$header.$payload.fake-signature"
    }

    @Test
    fun `given top-level is_anonymous true claim when decoded then returns true`() {
        val token = fakeJwt("""{"sub":"user-1","is_anonymous":true}""")

        assertTrue(decodeIsAnonymousClaim(token))
    }

    @Test
    fun `given top-level is_anonymous false claim when decoded then returns false`() {
        val token = fakeJwt("""{"sub":"user-1","is_anonymous":false}""")

        assertFalse(decodeIsAnonymousClaim(token))
    }

    @Test
    fun `given no is_anonymous claim when decoded then returns false`() {
        val token = fakeJwt("""{"sub":"user-1"}""")

        assertFalse(decodeIsAnonymousClaim(token))
    }

    @Test
    fun `given is_anonymous nested under app_metadata not top-level when decoded then returns false`() {
        // Regression guard for the original bug: app_metadata is NOT where GoTrue puts this claim.
        val token = fakeJwt("""{"sub":"user-1","app_metadata":{"is_anonymous":true}}""")

        assertFalse(decodeIsAnonymousClaim(token))
    }

    @Test
    fun `given null token when decoded then returns false`() {
        assertFalse(decodeIsAnonymousClaim(null))
    }

    @Test
    fun `given blank token when decoded then returns false`() {
        assertFalse(decodeIsAnonymousClaim("   "))
    }

    @Test
    fun `given malformed token with no dots when decoded then returns false`() {
        assertFalse(decodeIsAnonymousClaim("not-a-jwt"))
    }

    @Test
    fun `given token with undecodable payload segment when decoded then returns false`() {
        assertFalse(decodeIsAnonymousClaim("header.%%%not-base64%%%.signature"))
    }

    @Test
    fun `given token whose payload segment is not a JSON object when decoded then returns false`() {
        val payload = encodeBase64Url("""["not","an","object"]""")

        assertFalse(decodeIsAnonymousClaim("header.$payload.signature"))
    }

    // ── decodeAmrIncludesRecoveryClaim ──────────────────────────────────────────

    @Test
    fun `given amr array with recovery method object when decoded then returns true`() {
        val token = fakeJwt(
            """{"sub":"user-1","amr":[{"method":"recovery","timestamp":1700000000}]}"""
        )

        assertTrue(decodeAmrIncludesRecoveryClaim(token))
    }

    @Test
    fun `given amr array with recovery among multiple methods when decoded then returns true`() {
        val token = fakeJwt(
            """{"sub":"user-1","amr":[{"method":"password","timestamp":1},{"method":"recovery","timestamp":2}]}"""
        )

        assertTrue(decodeAmrIncludesRecoveryClaim(token))
    }

    @Test
    fun `given amr array with only password method when decoded then returns false`() {
        val token = fakeJwt(
            """{"sub":"user-1","amr":[{"method":"password","timestamp":1700000000}]}"""
        )

        assertFalse(decodeAmrIncludesRecoveryClaim(token))
    }

    @Test
    fun `given amr array with bare string recovery entry when decoded then returns true`() {
        // Defensive fallback shape — not the documented Supabase encoding, but tolerated.
        val token = fakeJwt("""{"sub":"user-1","amr":["recovery"]}""")

        assertTrue(decodeAmrIncludesRecoveryClaim(token))
    }

    @Test
    fun `given empty amr array when decoded then returns false`() {
        val token = fakeJwt("""{"sub":"user-1","amr":[]}""")

        assertFalse(decodeAmrIncludesRecoveryClaim(token))
    }

    @Test
    fun `given no amr claim when decoded then returns false`() {
        val token = fakeJwt("""{"sub":"user-1"}""")

        assertFalse(decodeAmrIncludesRecoveryClaim(token))
    }

    @Test
    fun `given amr claim that is not an array when decoded then returns false`() {
        val token = fakeJwt("""{"sub":"user-1","amr":"recovery"}""")

        assertFalse(decodeAmrIncludesRecoveryClaim(token))
    }

    @Test
    fun `given null token when decoding amr claim then returns false`() {
        assertFalse(decodeAmrIncludesRecoveryClaim(null))
    }

    @Test
    fun `given blank token when decoding amr claim then returns false`() {
        assertFalse(decodeAmrIncludesRecoveryClaim("   "))
    }

    @Test
    fun `given malformed token with no dots when decoding amr claim then returns false`() {
        assertFalse(decodeAmrIncludesRecoveryClaim("not-a-jwt"))
    }

    @Test
    fun `given token with undecodable payload segment when decoding amr claim then returns false`() {
        assertFalse(decodeAmrIncludesRecoveryClaim("header.%%%not-base64%%%.signature"))
    }

    @Test
    fun `given token whose payload segment is not a JSON object when decoding amr claim then returns false`() {
        val payload = encodeBase64Url("""["not","an","object"]""")

        assertFalse(decodeAmrIncludesRecoveryClaim("header.$payload.signature"))
    }

    private fun encodeBase64Url(text: String): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        val bytes = text.encodeToByteArray()
        val builder = StringBuilder((bytes.size * 4) / 3 + 3)
        var i = 0
        while (i < bytes.size) {
            val b0 = bytes[i].toInt() and 0xFF
            val b1 = if (i + 1 < bytes.size) bytes[i + 1].toInt() and 0xFF else 0
            val b2 = if (i + 2 < bytes.size) bytes[i + 2].toInt() and 0xFF else 0

            val triple = (b0 shl 16) or (b1 shl 8) or b2
            builder.append(alphabet[(triple shr 18) and 0x3F])
            builder.append(alphabet[(triple shr 12) and 0x3F])
            if (i + 1 < bytes.size) builder.append(alphabet[(triple shr 6) and 0x3F])
            if (i + 2 < bytes.size) builder.append(alphabet[triple and 0x3F])
            i += 3
        }
        // No padding, matching real JWT segments.
        return builder.toString()
    }
}
