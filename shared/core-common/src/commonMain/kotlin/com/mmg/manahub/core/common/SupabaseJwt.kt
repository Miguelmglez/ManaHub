package com.mmg.manahub.core.common

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Decodes the `is_anonymous` claim from a Supabase (GoTrue) JWT access token.
 *
 * ## Why this exists
 * GoTrue puts `is_anonymous` as a TOP-LEVEL claim on the session's JWT access token. It is
 * **not** nested inside `app_metadata` or `user_metadata`, and supabase-kt's `UserInfo` DTO
 * (`io.github.jan.supabase.auth.user.UserInfo`, verified via `auth-kt-jvm` 3.1.4 sources —
 * `UserInfo` has `appMetadata`/`userMetadata`/etc. but no `isAnonymous` property or `is_anonymous`
 * field anywhere) does not expose this claim at all. Both the Android and the web client used to
 * read `userInfo.appMetadata?.get("is_anonymous")`, which always evaluated to null/false because
 * the claim was never nested under `app_metadata` in the first place — confirmed live against
 * production data (`is_anonymous = true` rows in `auth.users` all had an empty `raw_app_meta_data`).
 * The access token is the only place this claim is actually present.
 *
 * ## Decoding approach
 * A JWT is three base64url segments (`header.payload.signature`) joined by dots; the middle
 * segment is a JSON object of claims. This function base64url-decodes that segment and reads the
 * top-level boolean `is_anonymous` claim directly. No signature verification is performed (or
 * needed) here: the token was already validated by GoTrue when the SDK obtained the session, and
 * this helper is read-only/informational — it feeds UX-level feature gating (e.g. hiding
 * account-gated widgets from guests), never an authorization decision. The real security boundary
 * is server-side RLS, which independently re-validates the token on every request.
 *
 * Pure commonMain: only kotlin-stdlib + kotlinx.serialization.json, no `android.*`/`java.*`/browser
 * API — safe to call from `androidMain`, `wasmJsMain`, or any future target.
 *
 * @param accessToken The session's JWT access token (e.g. `UserSession.accessToken`), or null.
 * @return `true` only when the decoded payload has a top-level `"is_anonymous": true` claim.
 *   Returns `false` for a normal (non-anonymous) session, a null/blank token, a malformed JWT, or
 *   any decode failure — this helper never throws.
 */
fun decodeIsAnonymousClaim(accessToken: String?): Boolean {
    if (accessToken.isNullOrBlank()) return false
    val payloadSegment = accessToken.split(".").getOrNull(1) ?: return false
    return try {
        val payloadJson = decodeBase64UrlToString(payloadSegment)
        val payload = Json.parseToJsonElement(payloadJson) as? JsonObject ?: return false
        payload["is_anonymous"]?.jsonPrimitive?.booleanOrNull == true
    } catch (_: Exception) {
        // Malformed/undecodable token: fail closed on the claim (never treat garbage input as
        // proof of anonymity), consistent with the pre-existing null-safe read style in this area.
        false
    }
}

/**
 * Decodes the `amr` (Authentication Methods Reference) claim from a Supabase (GoTrue) JWT access
 * token and reports whether it contains a `"recovery"` entry — i.e. whether this session was
 * established via a genuine "forgot password" recovery-link/OTP exchange, as opposed to a normal
 * password/OAuth/anonymous sign-in.
 *
 * ## Why this exists (security)
 * `manahub://auth` is an `exported=true` deep link with no path/signature restriction (required
 * for the Play Store launcher intent filter on the same Activity), so any app on the device can
 * fire `Intent(ACTION_VIEW, "manahub://auth?type=recovery")` at ManaHub. `MainActivity`'s
 * `type=recovery` check on the incoming URI is purely attacker-controlled — it proves nothing
 * about how the CURRENT session was actually established. Gating
 * `AuthRepository.confirmPasswordReset` (a no-nonce password change) on `SessionState
 * .Authenticated` alone would let an attacker walk an already-logged-in user straight to "set new
 * password" via a forged intent. `amr` is the server-issued, non-forgeable signal: GoTrue records
 * `{"method": "recovery", ...}` in this claim ONLY when the session was minted by exchanging a real
 * recovery-link token (`Auth.resetPasswordForEmail` → tap the emailed link → real `access_token` in
 * the deep link's fragment/query). This is a first-party-documented claim value (Supabase's
 * `jwt-fields` guide lists `"recovery"` as a standard `amr.method`), not a proposal — distinct from
 * the separate, still-open GoTrue feature request (supabase/supabase#45210) to have the SERVER
 * itself restrict what a recovery session's token can do; here we only need to read the claim that
 * already exists to gate our OWN client-side screen/ViewModel.
 *
 * ## Decoding approach
 * Same pattern as [decodeIsAnonymousClaim] (see its KDoc): hand-decode the JWT payload segment
 * rather than relying on a typed SDK claims model, since `UserSession`/`UserInfo` in this project's
 * pinned supabase-kt version do not expose `amr` as a typed property. Per Supabase's `jwt-fields`
 * documentation, `amr` is a JSON array of objects shaped `{"method": string, "timestamp": number}`;
 * a bare-string array entry is also accepted defensively in case of a future/alternate encoding.
 *
 * Pure commonMain: only kotlin-stdlib + kotlinx.serialization.json, no `android.*`/`java.*`/browser
 * API — safe to call from `androidMain`, `wasmJsMain`, or any future target.
 *
 * @param accessToken The session's JWT access token (e.g. `UserSession.accessToken`), or null.
 * @return `true` only when the decoded payload's `amr` array contains an entry whose `method` is
 *   exactly `"recovery"`. Returns `false` for a normal (non-recovery) session, a null/blank token,
 *   a malformed JWT, a missing/non-array `amr` claim, or any decode failure — this helper never
 *   throws, and a decode failure is treated as "not a recovery session" (fail closed: this claim
 *   gates a sensitive no-nonce password-change path, so an undecodable token must never be treated
 *   as proof of a genuine recovery flow).
 */
fun decodeAmrIncludesRecoveryClaim(accessToken: String?): Boolean {
    if (accessToken.isNullOrBlank()) return false
    val payloadSegment = accessToken.split(".").getOrNull(1) ?: return false
    return try {
        val payloadJson = decodeBase64UrlToString(payloadSegment)
        val payload = Json.parseToJsonElement(payloadJson) as? JsonObject ?: return false
        val amr = payload["amr"] as? JsonArray ?: return false
        amr.any { entry ->
            val method = (entry as? JsonObject)?.get("method")?.jsonPrimitive?.contentOrNull
                ?: (entry as? JsonPrimitive)?.takeIf { it.isString }?.content
            method == "recovery"
        }
    } catch (_: Exception) {
        // Malformed/undecodable token: fail closed (never treat garbage input as proof of a
        // genuine recovery session), consistent with decodeIsAnonymousClaim's error handling.
        false
    }
}

/**
 * Decodes a base64url (RFC 4648 §5) string — as used by JWT segments — into its UTF-8 text.
 *
 * Deliberately hand-rolled instead of `kotlin.io.encoding.Base64.UrlSafe`: that stdlib API is
 * still `@ExperimentalEncodingApi` as of Kotlin 2.3.20 and this module targets three Kotlin
 * platforms (Android, wasmJs, JVM) — a small, dependency-free decoder avoids any risk of the
 * experimental API's behavior or availability drifting across targets/toolchain upgrades.
 * JWT segments omit the `=` padding that RFC 4648 base64 normally requires; invalid characters
 * (there should be none in a well-formed token) are skipped defensively rather than throwing.
 */
private fun decodeBase64UrlToString(segment: String): String {
    val bytes = ArrayList<Byte>((segment.length * 3) / 4 + 3)
    var buffer = 0
    var bitsCollected = 0
    for (c in segment) {
        val value = BASE64_URL_ALPHABET.indexOf(c)
        if (value < 0) continue // Skip padding ('=') and any unexpected character.
        buffer = (buffer shl 6) or value
        bitsCollected += 6
        if (bitsCollected >= 8) {
            bitsCollected -= 8
            bytes.add(((buffer shr bitsCollected) and 0xFF).toByte())
        }
    }
    return bytes.toByteArray().decodeToString()
}

private const val BASE64_URL_ALPHABET =
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
