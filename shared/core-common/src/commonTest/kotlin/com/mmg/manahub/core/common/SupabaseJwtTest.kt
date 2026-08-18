package com.mmg.manahub.core.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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

    // ── decodeAmrIndicatesRecoverySession ──────────────────────────────────────────
    // Matches GoTrue's own AuthenticationMethod.IsRecovery() (github.com/supabase/auth,
    // internal/models/factor.go): "recovery", "otp", AND "magiclink" are all genuine recovery
    // sessions. A live-captured JWT from a real recovery-link tap showed method "otp" — GoTrue's
    // /verify?type=recovery handler tags the session with the underlying OTP mechanism, not the
    // literal "recovery" string — so "otp" is the case that matters most in practice.

    @Test
    fun `given amr array with recovery method object when decoded then returns true`() {
        val token = fakeJwt(
            """{"sub":"user-1","amr":[{"method":"recovery","timestamp":1700000000}]}"""
        )

        assertTrue(decodeAmrIndicatesRecoverySession(token))
    }

    @Test
    fun `given amr array with otp method object when decoded then returns true`() {
        // This is the shape GoTrue actually emits for a real "forgot password" email link tap
        // (verified via a live-captured JWT) — the most important case this function must match.
        val token = fakeJwt(
            """{"sub":"user-1","amr":[{"method":"otp","timestamp":1700000000}]}"""
        )

        assertTrue(decodeAmrIndicatesRecoverySession(token))
    }

    @Test
    fun `given amr array with magiclink method object when decoded then returns true`() {
        val token = fakeJwt(
            """{"sub":"user-1","amr":[{"method":"magiclink","timestamp":1700000000}]}"""
        )

        assertTrue(decodeAmrIndicatesRecoverySession(token))
    }

    @Test
    fun `given amr array with recovery among multiple methods when decoded then returns true`() {
        val token = fakeJwt(
            """{"sub":"user-1","amr":[{"method":"password","timestamp":1},{"method":"recovery","timestamp":2}]}"""
        )

        assertTrue(decodeAmrIndicatesRecoverySession(token))
    }

    @Test
    fun `given amr array with only password method when decoded then returns false`() {
        val token = fakeJwt(
            """{"sub":"user-1","amr":[{"method":"password","timestamp":1700000000}]}"""
        )

        assertFalse(decodeAmrIndicatesRecoverySession(token))
    }

    @Test
    fun `given amr array with email signup method when decoded then returns false`() {
        // Regression guard: GoTrue serializes signup-confirmation as the distinct string
        // "email/signup" (AuthenticationMethod.EmailSignup.String()), which is NOT one of the
        // three IsRecovery()-matched values — a signup-confirmation session must never satisfy
        // this check, resolving the previously-flagged signup/recovery ambiguity.
        val token = fakeJwt(
            """{"sub":"user-1","amr":[{"method":"email/signup","timestamp":1700000000}]}"""
        )

        assertFalse(decodeAmrIndicatesRecoverySession(token))
    }

    @Test
    fun `given amr array with bare string recovery entry when decoded then returns true`() {
        // Defensive fallback shape — not the documented Supabase encoding, but tolerated.
        val token = fakeJwt("""{"sub":"user-1","amr":["recovery"]}""")

        assertTrue(decodeAmrIndicatesRecoverySession(token))
    }

    @Test
    fun `given empty amr array when decoded then returns false`() {
        val token = fakeJwt("""{"sub":"user-1","amr":[]}""")

        assertFalse(decodeAmrIndicatesRecoverySession(token))
    }

    @Test
    fun `given no amr claim when decoded then returns false`() {
        val token = fakeJwt("""{"sub":"user-1"}""")

        assertFalse(decodeAmrIndicatesRecoverySession(token))
    }

    @Test
    fun `given amr claim that is not an array when decoded then returns false`() {
        val token = fakeJwt("""{"sub":"user-1","amr":"recovery"}""")

        assertFalse(decodeAmrIndicatesRecoverySession(token))
    }

    @Test
    fun `given null token when decoding amr claim then returns false`() {
        assertFalse(decodeAmrIndicatesRecoverySession(null))
    }

    @Test
    fun `given blank token when decoding amr claim then returns false`() {
        assertFalse(decodeAmrIndicatesRecoverySession("   "))
    }

    @Test
    fun `given malformed token with no dots when decoding amr claim then returns false`() {
        assertFalse(decodeAmrIndicatesRecoverySession("not-a-jwt"))
    }

    @Test
    fun `given token with undecodable payload segment when decoding amr claim then returns false`() {
        assertFalse(decodeAmrIndicatesRecoverySession("header.%%%not-base64%%%.signature"))
    }

    @Test
    fun `given token whose payload segment is not a JSON object when decoding amr claim then returns false`() {
        val payload = encodeBase64Url("""["not","an","object"]""")

        assertFalse(decodeAmrIndicatesRecoverySession("header.$payload.signature"))
    }

    // ── decodeAmrIndicatesRecoverySession — signup-confirmation-ambiguity regression guard ──
    // password-recovery-hardening-plan-2026-08-18 §2.1: verified live against production data,
    // GoTrue tags a signup-email-confirmation session `amr: otp` — the SAME value a genuine
    // recovery-link consumption gets. This decoder's job is only to detect the `amr` METHOD value;
    // it is fundamentally unable to disambiguate the two flows on its own. Pinned here as a
    // documentation/characterization test so a future reader does not mistake "otp -> true" for a
    // bug: the caller-side fix is requiring decodeSessionIdClaim + an app-side marker IN ADDITION
    // to this function's result (see AuthViewModel.isActiveRecoveryFlow) — this function's matched
    // set must not be narrowed to "solve" the ambiguity, since "otp" is also the case that matters
    // most for genuine recovery links in practice.
    @Test
    fun `given otp amr claim shaped like a signup-confirmation session when decoded then still returns true (amr alone cannot disambiguate signup from recovery)`() {
        // Structurally identical to a genuine recovery session's amr claim -- decodeAmrIndicatesRecoverySession
        // has no way to tell them apart; disambiguation is the caller's job (session-id-bound marker).
        val signupConfirmationShapedToken = fakeJwt(
            """{"sub":"user-1","amr":[{"method":"otp","timestamp":1700000000}]}"""
        )

        assertTrue(decodeAmrIndicatesRecoverySession(signupConfirmationShapedToken))
    }

    // ── decodeSessionIdClaim ──────────────────────────────────────────────────────

    @Test
    fun `given top-level session_id claim when decoded then returns its value`() {
        val token = fakeJwt("""{"sub":"user-1","session_id":"session-abc-123"}""")

        assertEquals("session-abc-123", decodeSessionIdClaim(token))
    }

    @Test
    fun `given no session_id claim when decoded then returns null`() {
        val token = fakeJwt("""{"sub":"user-1"}""")

        assertNull(decodeSessionIdClaim(token))
    }

    @Test
    fun `given session_id claim that is not a string when decoded then returns null`() {
        val token = fakeJwt("""{"sub":"user-1","session_id":12345}""")

        assertNull(decodeSessionIdClaim(token))
    }

    @Test
    fun `given null token when decoding session_id claim then returns null`() {
        assertNull(decodeSessionIdClaim(null))
    }

    @Test
    fun `given blank token when decoding session_id claim then returns null`() {
        assertNull(decodeSessionIdClaim("   "))
    }

    @Test
    fun `given malformed token with no dots when decoding session_id claim then returns null`() {
        assertNull(decodeSessionIdClaim("not-a-jwt"))
    }

    @Test
    fun `given token with undecodable payload segment when decoding session_id claim then returns null`() {
        assertNull(decodeSessionIdClaim("header.%%%not-base64%%%.signature"))
    }

    @Test
    fun `given token whose payload segment is not a JSON object when decoding session_id claim then returns null`() {
        val payload = encodeBase64Url("""["not","an","object"]""")

        assertNull(decodeSessionIdClaim("header.$payload.signature"))
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
