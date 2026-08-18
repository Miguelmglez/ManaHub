package com.mmg.manahub.app

import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.mmg.manahub.app.navigation.AppNavGraph
import com.mmg.manahub.core.common.decodeSessionIdClaim
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.domain.repository.UserPreferencesRepository
import com.mmg.manahub.core.push.PushDeeplinkRouter
import com.mmg.manahub.core.util.recordSafeNonFatal
import com.mmg.manahub.feature.auth.presentation.AccountLinkFailureEvents
import com.mmg.manahub.core.ui.components.MagicToastHost
import com.mmg.manahub.core.ui.components.MagicToastType
import com.mmg.manahub.core.ui.components.rememberMagicToastState
import com.mmg.manahub.core.ui.theme.AppTheme
import com.mmg.manahub.core.ui.theme.LocalPreferredCurrency
import com.mmg.manahub.core.ui.theme.MagicThemeAndroid
import com.mmg.manahub.feature.gamification.presentation.GamificationCelebrationHost
import dagger.hilt.android.AndroidEntryPoint
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.SignOutScope
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.handleDeeplinks
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.Locale
import javax.inject.Inject

/**
 * Bound on the blocking pending-recovery-marker write in [MainActivity.handleSupabaseAuthDeepLink]
 * (see that method's "MARKER WRITE"/"FIX" KDoc) — generous relative to a DataStore-Preferences
 * edit's typical few-millisecond cost, but this call runs on the main thread inside
 * `onCreate`/`onNewIntent`, so it must have SOME bound rather than none.
 */
private const val MARKER_WRITE_TIMEOUT_MS = 2_000L

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    var videoPlayerActive by mutableStateOf(false)
        private set

    var isInPiP by mutableStateOf(false)
        private set

    fun setPlayerActive(active: Boolean) {
        videoPlayerActive = active
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (videoPlayerActive) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPiP = isInPictureInPictureMode
    }

    @Inject
    lateinit var userPreferencesDataStore: UserPreferencesDataStore

    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository

    @Inject
    lateinit var supabaseClient: SupabaseClient

    /**
     * Guards against a malicious app injecting a forged manahub://auth intent.
     * MainActivity is exported=true (required for LAUNCHER), so any installed app
     * can send it an intent. Only pass auth intents to the Supabase SDK.
     */
    private fun isSupabaseAuthDeepLink(intent: Intent): Boolean {
        if (intent.action != Intent.ACTION_VIEW) return false
        val uri = intent.data ?: return false
        return uri.scheme == "manahub" && uri.host == "auth"
    }

    /**
     * True when [intent] is the Supabase auth deep link AND carries `type=recovery` — i.e. this is
     * a "forgot password" recovery callback, distinct from the signup-confirmation and
     * Google-identity-link callbacks that share the exact same `manahub://auth` scheme/host (see
     * `SupabaseClientFactory.kt`'s `Auth { scheme = ...; host = "auth" }` config — path is
     * unconstrained by the manifest intent filter, but neither of those two other callbacks ever
     * carries `type=recovery`).
     *
     * Checks BOTH the query string and the URL fragment: this project's Supabase Auth plugin
     * defaults to `FlowType.IMPLICIT` (no `flowType` override in `SupabaseClientFactory.kt`), which
     * delivers `type=recovery` in the URL FRAGMENT (`#access_token=...&type=recovery`) — `Uri`
     * parses fragments natively via [Uri.getFragment]. The query-param check is a defensive
     * second path in case the project's flow type is ever switched to PKCE.
     */
    private fun isPasswordRecoveryDeepLink(intent: Intent): Boolean {
        if (!isSupabaseAuthDeepLink(intent)) return false
        val uri = intent.data ?: return false
        val queryType = uri.getQueryParameter("type")
        val fragmentType = uri.fragment
            ?.split("&")
            ?.firstOrNull { it.startsWith("type=") }
            ?.substringAfter("type=")
        return queryType == "recovery" || fragmentType == "recovery"
    }

    /**
     * Handles every `manahub://auth` callback (signup confirmation, Google identity-link OAuth
     * redirect, AND password recovery — all three share the scheme/host). [handleDeeplinks] is what
     * imports the session (including the temporary, fully-authenticated recovery session GoTrue
     * mints for a recovery callback). Routing a recovery callback into Compose Navigation is no
     * longer this method's job (hardened 2026-08-18) — see the SECURITY/RACE FIX KDoc below and
     * `AppNavGraph.kt`'s recovery-routing `LaunchedEffect` KDoc for where that now lives.
     *
     * SECURITY — defense against a forged intent (this Activity's `manahub://auth` intent filter is
     * `exported=true` with no path/signature restriction, so any installed app can send it an
     * arbitrary `Intent`):
     *
     * (Primary gate, enforced downstream) [isPasswordRecoveryDeepLink] only reflects what the
     * URI *claims* — it is attacker-controlled and proves nothing about the resulting session.
     * The actual authorization check is [AuthUser.isRecoverySession] (the server-issued `amr`
     * JWT claim, matched against GoTrue's own `"recovery"`/`"otp"`/`"magiclink"` set — see
     * `decodeAmrIndicatesRecoverySession`'s KDoc in `core-common` for the citation), verified by
     * `AuthViewModel.confirmPasswordReset` and `ResetPasswordConfirmScreen` before any password
     * change is allowed — see their SECURITY KDocs. This method's `isRecoveryClaim` is only ever
     * used for the Crashlytics breadcrumbs below, never as proof of a genuine recovery flow — a
     * forged, token-less intent (e.g. `Intent(ACTION_VIEW, "manahub://auth?type=recovery")`) never
     * reaches `onSessionSuccess` (`Auth.parseFragmentAndImportSession` requires
     * `access_token`/`refresh_token`/`expires_in`/`token_type` to all be present in the fragment,
     * or it throws before any session is imported/callback fires — verified against the actual
     * `auth-kt` 3.5.0 sources, the version this project's `supabase-bom` resolves), so the
     * `auth_deeplink_recovery_session_imported` breadcrumb below can never fire for that case
     * either. A forged intent CAN still carry a real, currently-valid recovery token replayed from
     * an intercepted email, which WOULD reach `onSessionSuccess` and fire that breadcrumb —
     * [AuthUser.isRecoverySession] is what actually stops that case from being exploitable, since
     * the resulting session's `amr` claim would legitimately match GoTrue's own recovery-method set
     * and this is in fact then a real, intended recovery flow.
     *
     * SECURITY/RACE FIX (2026-08-17, CRITICAL; hardened 2026-08-18): `onSessionSuccess` fires
     * INSIDE `Auth.parseFragmentAndImportSession` (auth-kt 3.5.0, `UrlUtils.kt`) BEFORE that same
     * function calls `importSession(newSession, source = SessionSource.External)` — the call that
     * actually updates `sessionStatus`, which `AuthRepositoryImpl.sessionState` reacts to.
     * Enqueuing navigation synchronously from this callback therefore raced ahead of
     * `sessionState` actually reflecting the new recovery session: `ResetPasswordConfirmScreen`
     * would read a STALE `sessionState` on first composition (`Unauthenticated`, or a prior
     * non-recovery `Authenticated`) and render the exact same "invalid/expired link" error the
     * `isRecoverySession` gate exists to show for a genuinely forged intent — a false positive
     * against a real recovery link, indistinguishable from the gate doing its job.
     *
     * The original fix (2026-08-17) had this method wait — bounded by a 3s timeout — on
     * `AuthRepository.sessionState` before enqueueing navigation via `PushDeeplinkRouter`. That
     * closed the visible symptom but reopened a NARROWER version of the same bug: `handleDeeplinks`
     * (below) has already imported and PERSISTED the recovery session (Keystore-backed
     * `SessionManager`, `autoLoadFromStorage = true`) the instant this callback starts running —
     * before the wait coroutine even begins. If the process died during that bounded wait (e.g.
     * the user backgrounds the app right after tapping the email link and Android reclaims memory),
     * the coroutine died with it — no crash, no non-fatal, nothing enqueued — while the recovery
     * session survived in persistent storage. On relaunch, `onCreate` receives a plain launcher
     * `Intent`, not `manahub://auth`, so this method never runs again; `sessionState` naturally
     * resolves to `Authenticated(isRecoverySession = true)` from restored storage, but nothing was
     * left to route the user to `Screen.ResetPasswordConfirm` — same end state as the original bug
     * (landing on Home fully authenticated via the recovery session), different trigger.
     *
     * FIX: routing to `Screen.ResetPasswordConfirm` is no longer owned by this method or by
     * `PushDeeplinkRouter` at all. `AppNavGraph`'s top-level `LaunchedEffect` now observes
     * `AuthRepository.sessionState` directly and navigates the instant it sees
     * `Authenticated(isRecoverySession = true)`, REGARDLESS of how that session came to exist —
     * a fresh deep-link tap, a cold start that restored an already-persisted recovery session, or
     * a warm resume. This has no timeout and no dependency on this Activity's intent lifecycle, so
     * it structurally cannot reproduce either the original race or the process-death variant: the
     * moment `sessionState` reflects a recovery-authenticated session, in ANY process incarnation,
     * routing fires. See `AppNavGraph.kt`'s recovery-routing `LaunchedEffect` KDoc and
     * `feedback_supabase_deeplink_onsessionsuccess_race` for the full mechanism. This callback's
     * only remaining job for the recovery case is a Crashlytics breadcrumb confirming the SDK
     * actually imported a recovery session from this intent — pure observability, no longer
     * load-bearing for correctness.
     *
     * GENERAL RULE for this file: never navigate a security-gated screen directly off
     * [handleDeeplinks]'s `onSessionSuccess`, and never gate that routing on this Activity's
     * intent/coroutine lifecycle surviving to completion — always drive it off the app's own
     * reactive session state at the point where the screen is actually rendered.
     *
     * MARKER WRITE (password-recovery-hardening-plan-2026-08-18 §3.1; hardened again same-day
     * after a review found a narrower reopen of fix #1's own bug): `AuthUser.isRecoverySession`
     * (the `amr` claim) is NECESSARY but not SUFFICIENT proof of a genuine recovery session —
     * verified live, GoTrue also tags a signup-email-confirmation session `amr: otp`, the exact
     * same value a real recovery-link consumption gets (see `decodeAmrIndicatesRecoverySession`'s
     * KDoc in `core-common`). This callback is therefore also where the app-side one-shot marker is
     * armed: ONLY when [isRecoveryClaim] is true, it decodes the imported session's `session_id`
     * claim ([decodeSessionIdClaim]) and persists it (with the current time) via
     * [UserPreferencesDataStore.setPendingRecoveryMarker] — the marker `AppNavGraph`,
     * `ResetPasswordConfirmScreen`, and `AuthViewModel.confirmPasswordReset` all now additionally
     * require (bound to this exact session id, with a 15-minute TTL) before treating a session as
     * an active recovery flow.
     *
     * GOAL (not an absolute invariant — see "WHAT IS ACTUALLY GUARANTEED" below for the precise,
     * verified claim): minimize the interval in which a recovery session is DURABLY persisted while
     * its marker is NOT. `handleDeeplinks` (below) already persists the imported session to the
     * Keystore-backed `SessionManager` (`autoLoadFromStorage = true`) SYNCHRONOUSLY, before this
     * `onSessionSuccess` callback body even starts running. An earlier version of this fix armed
     * the marker with `lifecycleScope.launch { withContext(NonCancellable) { ... } }` — an
     * asynchronous write — which reopened a NARROWER version of the exact bug fix #1 above exists
     * to eliminate: a process death between "session persisted" and "marker write commits" (a real,
     * demonstrated failure mode on this project, not theoretical — see
     * `feedback_supabase_deeplink_onsessionsuccess_race`) leaves a restored session on relaunch
     * with `isRecoverySession = true` and no marker, so `isActiveRecoveryFlow` fails closed and
     * NOTHING routes the user anywhere — same end state as the original bug (stranded fully
     * authenticated on Home, no "set new password" prompt), reached through a millisecond-scale
     * window instead of a 3-second one. `NonCancellable` did not fully close this either: it only
     * protects work once execution is already inside the block — `lifecycleScope.launch` can be
     * cancelled before its body ever starts if the Lifecycle is already destroyed.
     *
     * FIX: the write is now a SHORT BLOCKING call (`runBlocking { withTimeout(...) { ... } }`) on
     * this callback's own thread — `handleSupabaseAuthDeepLink` always runs on the main thread from
     * `onCreate`/`onNewIntent`, both synchronous, non-suspending Activity lifecycle methods, so
     * there is no `suspend` context to bridge into otherwise. A DataStore-Preferences edit is a few
     * milliseconds; paying that against a security boundary is the correct trade. Do NOT revert
     * this to an async `launch` under the assumption that `NonCancellable` alone made it safe — it
     * did not, see above. Also do NOT "fix" a marker-absent-but-isRecoverySession-true state by
     * treating it as an implicit abandon-and-sign-out at startup or anywhere else:
     * `AuthRepository.confirmEmailUpdate`'s email-change confirmation deep link legitimately
     * produces exactly that shape (`amr: otp`, no marker — MainActivity only arms the marker for
     * `type=recovery`) mid-flow, and signing the user out there would break a successful email
     * change, not stop a recovery attempt. This was explicitly proposed (as a startup reconcile:
     * "`isRecoverySession && marker == null` sustained → force `signOut(LOCAL)`") during the
     * 2026-08-18 security audit and REJECTED for exactly this reason — do not re-derive it. The
     * only variant that would actually work is tagging every deep-link import with its own type so
     * a *missing* marker becomes distinguishable from a *non-recovery* one; that is materially more
     * machinery than the residual gap below justifies.
     *
     * WHAT IS ACTUALLY GUARANTEED, AND WHAT IS NOT (2026-08-18 security-audit finding — the earlier
     * wording here claimed an absolute invariant that was never actually verified; do not restore
     * it): `markerArmed` below, together with the neutralizing `signOut(LOCAL)` when it is `false`,
     * closes every HANDLED failure mode of arming the marker — a write timeout, a write exception,
     * and a missing `session_id` claim all end with the just-imported session signed out again
     * rather than left authenticated with no marker. The one gap this does NOT close: the process
     * dying DURING the write itself — inside DataStore's `edit{}`, before it returns control to this
     * method at all — runs no `catch` branch and reaches no neutralizing `signOut` call, because
     * nothing here resumes execution to run it. On relaunch: the recovery session restores from
     * storage as `Authenticated(isRecoverySession = true)`, no marker exists, and nothing re-attempts
     * the write or the sign-out. `isActiveRecoveryFlow` still fails closed, so the user is NOT routed
     * to the reset screen — but they are also not signed out, and continue into the app authenticated
     * without ever setting a password.
     *
     * This residual is ACCEPTED, not fixed: it is triggerable only by the device owner, on their own
     * account, via their own recovery link, by killing the process at the exact instant the write is
     * in flight — not inducible by a third party, and the outcome is bounded to that one account on
     * that one device. Closing it would mean adding more logic to the single most delicate path in
     * this feature, and that new code would carry its own defect risk against a threat nobody but the
     * account owner can actually cause. If this residual is ever revisited, evaluate it against the
     * rejected startup-reconcile mitigation above first — the email-change conflict is the reason
     * that specific approach does not work, not a reason no approach could.
     *
     * A missing `session_id` claim (should not happen in practice on this project) is recorded as a
     * non-fatal in addition to the neutralizing sign-out above.
     *
     * The `try/catch` guards the synchronous half of [handleDeeplinks]: `parseSessionFromFragment`
     * throws a plain `IllegalArgumentException` when the fragment is missing a required token field
     * — the forged, token-less `manahub://auth#type=recovery` case, but ALSO every genuine failure
     * mode (GoTrue rejecting the link, or the OAuth provider returning an `error=`/denial redirect
     * instead of a token-bearing one — neither carries the required token fields either) — uncaught,
     * this would crash the app from `onCreate`/`onNewIntent` on nothing more than a malicious intent
     * from another app. [recordSafeNonFatal] logs the failure (never the token/intent data).
     *
     * [AccountLinkFailureEvents.notifyDeepLinkFailed] is also fired here — see its KDoc for why:
     * without it, a failed "Link Google account" OAuth redirect left the button on
     * `AccountManagementScreen` silently re-enabled with no explanation, since that screen's own
     * loading state was already cleared the moment the Custom Tab launched. This is best-effort
     * and scoped to the app-foregrounded case only.
     */
    private fun handleSupabaseAuthDeepLink(intent: Intent) {
        if (!isSupabaseAuthDeepLink(intent)) return
        val isRecoveryClaim = isPasswordRecoveryDeepLink(intent)
        // Entry breadcrumb + a filterable claim-type key set BEFORE the parse attempt, so it is
        // attached context on the recordSafeNonFatal call below if parsing throws. Previously the
        // ONLY signal on this whole path was the failure branch — there was no way to establish a
        // baseline traffic volume or tell whether failures skew toward type=recovery specifically.
        FirebaseCrashlytics.getInstance().apply {
            log("auth_deeplink_received")
            setCustomKey("auth_deeplink_is_recovery_claim", isRecoveryClaim)
        }
        try {
            // NAMED parameter (not trailing-lambda position) deliberately: handleDeeplinks declares
            // BOTH onSessionSuccess AND onError as trailing function-type params with defaults —
            // trailing-lambda syntax (`handleDeeplinks(intent) { ... }`) binds to the LAST declared
            // parameter, which is onError, NOT onSessionSuccess. That silently left the callback
            // below wired to the wrong parameter (onSessionSuccess quietly kept its no-op default;
            // onError is never invoked by the IMPLICIT flow branch this project uses either, per
            // auth-kt 3.5.0's own handleDeeplinks source, so the callback body never ran at all) —
            // a latent, undetected bug (the recovery-session-imported breadcrumb it set out to log
            // never actually fired) that had no visible effect until this fix needed the real
            // UserSession to decode session_id from. Fixed by naming the parameter explicitly so
            // the binding is unambiguous regardless of declaration order.
            supabaseClient.handleDeeplinks(intent, onSessionSuccess = { session ->
                // Reached only when handleDeeplinks actually imported a session from genuine
                // tokens present in this intent — see the SECURITY KDoc above (layer 2).
                //
                // RACE FIX (2026-08-17, CRITICAL; hardened 2026-08-18) — see the SECURITY/RACE FIX
                // KDoc above: this callback no longer enqueues navigation, nor waits on
                // sessionState itself — both were still process-death-fragile. Routing to
                // Screen.ResetPasswordConfirm is now owned entirely by AppNavGraph's reactive
                // sessionState observer, which has no timeout and no dependency on this callback
                // or this Activity instance surviving. This breadcrumb is pure observability:
                // confirms the SDK actually imported a recovery session from this intent (distinct
                // from AppNavGraph's own breadcrumb, which fires only once the user is actually
                // routed to the reset-password screen).
                if (isRecoveryClaim) {
                    FirebaseCrashlytics.getInstance().log("auth_deeplink_recovery_session_imported")
                    // MARKER WRITE — see the KDoc above ("GOAL", "FIX", and "WHAT IS ACTUALLY
                    // GUARANTEED, AND WHAT IS NOT") for the full rationale. Deliberately BLOCKING
                    // (runBlocking, not lifecycleScope.launch): the session was already persisted
                    // synchronously by handleDeeplinks above, so this closes every HANDLED failure
                    // mode of arming the marker before this method returns (timeout, exception,
                    // missing session_id — each neutralized below via markerArmed). It does NOT
                    // close process death during the write itself — that is a documented, accepted
                    // residual, not a guarantee this comment claims to provide. Bounded by
                    // withTimeout: "a DataStore edit is a few milliseconds" is true in the common
                    // case but is not a bound, and this runs on the main thread inside
                    // onCreate/onNewIntent — a stuck write must degrade into the same safe fallback
                    // below rather than hang the UI thread.
                    val recoverySessionId = decodeSessionIdClaim(session.accessToken)
                    val markerArmed = if (recoverySessionId != null) {
                        val markedAtEpochMs = System.currentTimeMillis()
                        try {
                            runBlocking {
                                withTimeout(MARKER_WRITE_TIMEOUT_MS) {
                                    userPreferencesDataStore.setPendingRecoveryMarker(
                                        recoverySessionId,
                                        markedAtEpochMs,
                                    )
                                }
                            }
                            true
                        } catch (e: TimeoutCancellationException) {
                            recordSafeNonFatal("main_activity_recovery_marker_write_timed_out", e)
                            false
                        } catch (e: Exception) {
                            // A dedicated key, distinct from main_activity_auth_deeplink_parse_failed
                            // below: this is a DataStore write failure AFTER a genuine recovery
                            // session was already imported, not a parse/token failure — must not be
                            // misattributed as one, and must not trigger
                            // AccountLinkFailureEvents.notifyDeepLinkFailed() (that toast is
                            // specifically for the "Link Google account" flow).
                            recordSafeNonFatal("main_activity_recovery_marker_write_failed", e)
                            false
                        }
                    } else {
                        recordSafeNonFatal(
                            "main_activity_recovery_marker_session_id_missing",
                            IllegalStateException("Recovery session JWT had no session_id claim"),
                        )
                        false
                    }
                    if (!markerArmed) {
                        // ACTIVELY NEUTRALIZE (T6 adversarial-audit HIGH fix, 2026-08-18): fail
                        // closed on the MARKER means isActiveRecoveryFlow can never be satisfied —
                        // but that alone only stops ROUTING to the reset screen, it does NOT sign
                        // the user out. Left as just a log, the just-imported recovery session
                        // (already durably persisted by handleDeeplinks above) stays fully
                        // authenticated with no marker to ever unlock the reset form — the user
                        // silently lands in the app signed in, never prompted for a new password.
                        // That is the ORIGINAL reported bug (passwordless login via a recovery
                        // link), reached through a marker-arming failure instead of the amr-matching
                        // bug fix #2 closed. LOCAL sign-out (same scope as
                        // AuthRepositoryImpl.abandonRecoverySession, and for the identical reason —
                        // offline-safe, cannot fail, doesn't touch the user's other devices) turns
                        // this failure mode into "tap the link again" instead of "silently signed in
                        // without ever setting a password". Best-effort: signOut itself failing must
                        // not crash deep-link handling — the marker/neutralize attempt already
                        // recorded its own non-fatal above regardless.
                        runCatching {
                            runBlocking { withTimeout(MARKER_WRITE_TIMEOUT_MS) { supabaseClient.auth.signOut(SignOutScope.LOCAL) } }
                        }
                    }
                }
            })
        } catch (e: Exception) {
            recordSafeNonFatal("main_activity_auth_deeplink_parse_failed", e)
            AccountLinkFailureEvents.notifyDeepLinkFailed()
        }
    }

    override fun attachBaseContext(newBase: Context) {
        val langCode = newBase
            .getSharedPreferences("user_prefs_lang_sync", Context.MODE_PRIVATE)
            .getString("app_language_sync", "en")
            ?: "en"

        val locale = Locale(langCode)
        Locale.setDefault(locale)

        val config = Configuration(newBase.resources.configuration)
        config.setLocale(locale)

        val context = newBase.createConfigurationContext(config)
        super.attachBaseContext(context)
    }

    /**
     * Routes an FCM background notification tap. When the app is in background/killed, FCM
     * delivers the `data` payload as Intent extras directly to this Activity. The deeplink is
     * forwarded to [PushDeeplinkRouter], which buffers it if the NavController is not yet composed
     * (cold start) and flushes it once navigation registers.
     */
    private fun handlePushDeeplink(intent: Intent?) {
        val deeplink = intent?.getStringExtra("deeplink") ?: return
        PushDeeplinkRouter.enqueue(deeplink)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleSupabaseAuthDeepLink(intent)
        handlePushDeeplink(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        // Release the system splash immediately so the Compose splash takes over without
        // a double-loading delay. The Compose splash owns the session-resolution wait.
        splashScreen.setKeepOnScreenCondition { false }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleSupabaseAuthDeepLink(intent)
        // Cold-start from a notification tap: buffered until AppNavGraph registers its navigator.
        handlePushDeeplink(intent)

        val appUpdateManager = AppUpdateManagerFactory.create(this)
        val appUpdateInfoTask = appUpdateManager.appUpdateInfo

        setContent {
            val updateToastState = rememberMagicToastState()

            androidx.compose.runtime.LaunchedEffect(Unit) {
                appUpdateInfoTask.addOnSuccessListener { appUpdateInfo ->
                    if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                        && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
                    ) {
                        updateToastState.show(
                            message = "New app update available. Tap to update.",
                            type = MagicToastType.INFO,
                            durationMs = 10000,
                            onClick = {
                                appUpdateManager.startUpdateFlowForResult(
                                    appUpdateInfo,
                                    AppUpdateType.FLEXIBLE,
                                    this@MainActivity,
                                    500
                                )
                            }
                        )
                    }
                }
            }

            val theme by userPreferencesDataStore.themeFlow
                .collectAsStateWithLifecycle(initialValue = AppTheme.NeonVoid)

            val userPrefs by userPreferencesRepository.preferencesFlow
                .collectAsStateWithLifecycle(initialValue = null)

            MagicThemeAndroid(theme = theme) {
                CompositionLocalProvider(
                    LocalPreferredCurrency provides (userPrefs?.preferredCurrency ?: com.mmg.manahub.core.model.PreferredCurrency.USD),
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AppNavGraph(isInPiP = isInPiP)
                        // Global achievement-unlock celebration overlay (ADR-002, Phase 1). Hosted
                        // here so a celebration plays above any screen; suppressed when the master
                        // gamification toggle is off (handled inside the host's ViewModel).
                        GamificationCelebrationHost()
                        MagicToastHost(
                            state = updateToastState,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding(),
                        )
                    }
                }
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 500) {
            if (resultCode != RESULT_OK) {
                // Update failed or cancelled
            }
        }
    }
}
