package com.mmg.manahub.web.common

import kotlinx.browser.window

/**
 * Attempts to copy [text] to the system clipboard via the browser's async Clipboard API
 * (`navigator.clipboard.writeText`, confirmed available in `kotlinx-browser` 0.5.0's
 * `org.w3c.dom.Navigator.clipboard`/`org.w3c.dom.clipboard.Clipboard` bindings). Returns `true`
 * when the call was issued without throwing.
 *
 * Friends completion slice (2026-08-05) -- the referral-invite "Copy link" button on
 * [com.mmg.manahub.web.profile.ProfileScreen]. Deliberately does NOT await the returned
 * `Promise<Nothing?>`: a genuine async rejection (permission denied, insecure context) is rare for
 * a same-origin, user-gesture-triggered call, and this codebase has no established pattern yet for
 * awaiting a raw JS `Promise` from a wasmJs coroutine (grepped clean before writing this -- only
 * `kotlinx.coroutines.Deferred.await()`/`awaitAll()` usages exist, which is a different type).
 * [com.mmg.manahub.web.profile.ProfileScreen] always keeps the link visible in a selectable text
 * field alongside the copy button, so even a silent async failure still leaves the user able to
 * copy it manually -- the task's own documented fallback for "don't over-engineer clipboard API
 * access".
 */
@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
fun copyToClipboardOrNull(text: String): Boolean =
    try {
        window.navigator.clipboard.writeText(text)
        true
    } catch (e: Throwable) {
        false
    }
