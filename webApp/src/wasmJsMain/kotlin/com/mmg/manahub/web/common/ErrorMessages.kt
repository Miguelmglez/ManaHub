package com.mmg.manahub.web.common

import com.mmg.manahub.core.common.CrashReporter

/**
 * Converts [this] into a short, generic, user-facing message for [action] (e.g. `"rename the
 * deck"`, `"add ${'$'}{card.name}"`) instead of surfacing the raw SDK/network exception text
 * (`e.message`) directly in the UI.
 *
 * Web security-pass fix (2026-08-04): every web ViewModel used to do
 * `_uiState.update { it.copy(error = e.message ?: "...") }`, leaking Ktor/Supabase SDK
 * implementation detail (raw exception class names, HTTP status lines) to the end user. The real
 * [Throwable] is still recorded via [crashReporter] so the underlying cause isn't lost -- only
 * kept off-screen, matching CLAUDE.md's telemetry rule (Non-Fatals at silent-error points) while
 * never logging PII (only the throwable + its type/stack go to the reporter, never rendered text).
 *
 * Deliberately `:webApp`-local, not `shared/core-common`: Android's own ViewModels use the same
 * raw `e.message ?: "..."` pattern in several places today (no established shared convention to
 * match), so this stays scoped to the web-UX concern that motivated it rather than introducing a
 * new cross-platform convention Android doesn't also follow yet.
 *
 * **Caller contract: catch [Throwable], never `catch (e: Exception)`.** Verified live (Playwright,
 * offline `+`/increment on a real deck): a failed browser `fetch()` inside Ktor's `Js` engine
 * surfaces on wasmJs as `kotlin.Error: Fail to fetch` -- a [kotlin.Error], NOT a
 * [kotlin.Exception] subtype. A call site written as `catch (e: Exception) { ...toUserFacingMessage... }`
 * silently never triggers on a real network failure: the coroutine's exception propagates
 * uncaught (logged to the browser console only), the UI shows nothing at all -- worse than the
 * raw-`e.message` bug this helper replaces, since even that used to show the RAW message on this
 * same catch clause (which, on reflection, means the ORIGINAL `catch (e: Exception)` sites almost
 * certainly never fired on a genuine fetch failure either -- this was a pre-existing silent-failure
 * gap this fix surfaced, not one it introduced). Every current call site
 * (`AuthViewModel.signInAsGuest`, `CardSearchViewModel.addToCollection`, all six
 * `DeckEditorViewModel` mutation methods, `DeckListViewModel.createDeck`) uses `catch (e: Throwable)`.
 */
fun Throwable.toUserFacingMessage(action: String, crashReporter: CrashReporter): String {
    crashReporter.recordException(this)
    return "Couldn't $action. Please try again."
}
