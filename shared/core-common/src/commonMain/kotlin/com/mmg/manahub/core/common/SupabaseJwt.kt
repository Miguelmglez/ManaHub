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
 * token and reports whether it indicates this session was established via a genuine "forgot
 * password" recovery flow, as opposed to a normal password/OAuth/anonymous sign-in.
 *
 * ## Why this exists (security)
 * `manahub://auth` is an `exported=true` deep link with no path/signature restriction (required
 * for the Play Store launcher intent filter on the same Activity), so any app on the device can
 * fire `Intent(ACTION_VIEW, "manahub://auth?type=recovery")` at ManaHub. `MainActivity`'s
 * `type=recovery` check on the incoming URI is purely attacker-controlled — it proves nothing
 * about how the CURRENT session was actually established. Gating
 * `AuthRepository.confirmPasswordReset` (a no-nonce password change) on `SessionState
 * .Authenticated` alone would let an attacker walk an already-logged-in user straight to "set new
 * password" via a forged intent. `amr` is the server-issued, non-forgeable signal.
 *
 * ## Matched values (corrected 2026-08-18 — verified against GoTrue's own Go source, not guessed)
 * `github.com/supabase/auth` (GoTrue), `internal/models/factor.go`, defines an
 * `AuthenticationMethod` enum whose `IsRecovery()` method — the EXACT logic GoTrue's own
 * `Session.IsRecovery()` (`internal/models/sessions.go`) uses server-side — treats THREE values as
 * equivalent for this purpose:
 * ```go
 * func (authMethod AuthenticationMethod) IsRecovery() bool {
 *     switch authMethod {
 *     case OTP, MagicLink, Recovery:
 *         return true
 *     default:
 *         return false
 *     }
 * }
 * ```
 * These serialize to the JWT `amr[].method` strings `"otp"`, `"magiclink"`, and `"recovery"`
 * respectively (`AuthenticationMethod.String()`, same file). A live-captured JWT from tapping a
 * genuine recovery email link on this project showed `amr: [{"method":"otp",...}]` — GoTrue's
 * `/verify?type=recovery` handler tags the resulting session with the underlying OTP verification
 * mechanism (`"otp"`), not the request's own `type` param's string (`"recovery"`) — which is
 * exactly why `IsRecovery()` was written to include `OTP` alongside the literal `Recovery` value.
 * An earlier version of this function matched ONLY the literal string `"recovery"`, which never
 * actually matches real recovery sessions minted by this flow — a fail-closed bug, not a security
 * gap (it just meant genuine recovery links, not forged ones, were rejected).
 *
 * ## Signup-confirmation ambiguity is REAL — this claim alone is NOT sufficient authorization
 * (corrected 2026-08-18; an earlier version of this KDoc claimed the opposite — that claim was
 * disproven by live data and must not be resurrected). It is tempting to assume `EmailSignup`
 * serializing to the distinct string `"email/signup"` means a signup-confirmation session's `amr`
 * can never satisfy this check — but that assumption is about which STRING VALUE GoTrue could
 * theoretically use, not about which one it ACTUALLY uses for the session it mints. Verified live
 * against project `uimogilwuixgkgfcfmyb`'s `auth.mfa_amr_claims` ⋈ `auth.sessions` ⋈ `auth.users`:
 * of five `otp`-tagged rows, THREE were signup-email confirmations (created within ~50ms of
 * `email_confirmed_at`, `recovery_sent_at` NULL), not recoveries. `"email/signup"` never appears in
 * this project's `mfa_amr_claims` at all — GoTrue's `/verify` handler issues the session with
 * `models.OTP` for signup confirmation too, the same value it uses for a genuine recovery-link
 * consumption. Consequence: a brand-new user tapping "Confirm your email" produces a session that
 * satisfies [decodeAmrIndicatesRecoverySession] exactly as if it were a real recovery session.
 *
 * **This is why a caller of this function MUST additionally require a one-shot, app-side marker
 * proving a genuine recovery deep link (not a signup-confirmation one) produced the CURRENT
 * session**, matched by the session's own `session_id` claim (see the session-id-claim decoder in
 * this file) and consumed on completion/abandon — `amr` alone gates nothing conclusively; it only
 * narrows "some OTP-class exchange happened" versus "a password/OAuth sign-in happened". Do not
 * remove or weaken the marker requirement on the theory that this function's matched-value set
 * already excludes signup — it does not, in practice, on this project. Full evidence and design:
 * `docs/plans/password-recovery-hardening-plan-2026-08-18.md` §2.1/§3.
 *
 * Source: `github.com/supabase/auth`, `internal/models/factor.go`
 * (`AuthenticationMethod.IsRecovery()` / `.String()`) and `internal/models/sessions.go`
 * (`Session.IsRecovery()`) — verify against upstream before changing this set again.
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
 *   `"recovery"`, `"otp"`, or `"magiclink"` (GoTrue's own `IsRecovery()` set — see above). Returns
 *   `false` for a normal (non-recovery) session, a null/blank token, a malformed JWT, a
 *   missing/non-array `amr` claim, or any decode failure — this helper never throws, and a decode
 *   failure is treated as "not a recovery session" (fail closed: this claim gates a sensitive
 *   no-nonce password-change path, so an undecodable token must never be treated as proof of a
 *   genuine recovery flow).
 */
fun decodeAmrIndicatesRecoverySession(accessToken: String?): Boolean {
    if (accessToken.isNullOrBlank()) return false
    val payloadSegment = accessToken.split(".").getOrNull(1) ?: return false
    return try {
        val payloadJson = decodeBase64UrlToString(payloadSegment)
        val payload = Json.parseToJsonElement(payloadJson) as? JsonObject ?: return false
        val amr = payload["amr"] as? JsonArray ?: return false
        amr.any { entry ->
            val method = (entry as? JsonObject)?.get("method")?.jsonPrimitive?.contentOrNull
                ?: (entry as? JsonPrimitive)?.takeIf { it.isString }?.content
            method in RECOVERY_AMR_METHODS
        }
    } catch (_: Exception) {
        // Malformed/undecodable token: fail closed (never treat garbage input as proof of a
        // genuine recovery session), consistent with decodeIsAnonymousClaim's error handling.
        false
    }
}

/**
 * The `amr[].method` string values GoTrue's own `AuthenticationMethod.IsRecovery()`
 * (`github.com/supabase/auth`, `internal/models/factor.go`) treats as a genuine recovery session.
 * See [decodeAmrIndicatesRecoverySession]'s KDoc for the full citation and rationale.
 */
private val RECOVERY_AMR_METHODS = setOf("recovery", "otp", "magiclink")

/**
 * Decodes the `session_id` claim from a Supabase (GoTrue) JWT access token.
 *
 * ## Why this exists
 * [decodeAmrIndicatesRecoverySession] alone is NOT sufficient proof that the current session came
 * from a genuine password-recovery deep link — see that function's KDoc for the live-verified
 * signup-confirmation-ambiguity evidence. The app-side fix is a one-shot marker
 * (`pending_recovery_session_id` + a timestamp, see `UserPreferencesDataStore` in the Android app
 * module) written ONLY when a genuine recovery deep link is imported, bound to the specific session
 * that resulted from it. Binding by `session_id` (rather than a plain boolean "a recovery happened
 * recently" flag) is what makes the marker non-transferable: a marker written for one session can
 * never validate a DIFFERENT session that happens to be current later (e.g. after a sign-out and a
 * new sign-in, or a forged intent racing a real one). `session_id` is a top-level claim GoTrue puts
 * on every access token, identifying the `auth.sessions` row the token was minted for — every
 * refreshed token for that same session carries the same `session_id`, but a different session
 * (even for the same user) gets a different one.
 *
 * ## Decoding approach
 * Same pattern as [decodeIsAnonymousClaim] and [decodeAmrIndicatesRecoverySession] (see their
 * KDocs): hand-decode the JWT payload segment rather than relying on a typed SDK claims model,
 * since `UserSession`/`UserInfo` in this project's pinned supabase-kt version do not expose
 * `session_id` as a typed property.
 *
 * Pure commonMain: only kotlin-stdlib + kotlinx.serialization.json, no `android.*`/`java.*`/browser
 * API — safe to call from `androidMain`, `wasmJsMain`, or any future target.
 *
 * @param accessToken The session's JWT access token (e.g. `UserSession.accessToken`), or null.
 * @return The decoded `session_id` claim value, or `null` for a null/blank token, a malformed JWT,
 *   a missing/non-string `session_id` claim, or any decode failure — this helper never throws. A
 *   `null` return must be treated as "no marker can ever match this session" by callers (fail
 *   closed), never as "skip the check".
 */
fun decodeSessionIdClaim(accessToken: String?): String? {
    if (accessToken.isNullOrBlank()) return null
    val payloadSegment = accessToken.split(".").getOrNull(1) ?: return null
    return try {
        val payloadJson = decodeBase64UrlToString(payloadSegment)
        val payload = Json.parseToJsonElement(payloadJson) as? JsonObject ?: return null
        // Explicitly require a STRING primitive (not just any content via .contentOrNull, which
        // would silently stringify a non-string value like a JSON number) — this claim gates a
        // security-sensitive marker match, so a malformed/unexpected shape must fail closed rather
        // than coerce.
        (payload["session_id"] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
    } catch (_: Exception) {
        // Malformed/undecodable token: fail closed (null), consistent with the other decoders in
        // this file — a decode failure must never be treated as "no marker check needed".
        null
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
