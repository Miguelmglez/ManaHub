package com.mmg.manahub.core.common

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
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
