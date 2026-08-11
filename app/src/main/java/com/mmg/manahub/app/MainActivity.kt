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
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.mmg.manahub.app.navigation.AppNavGraph
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.domain.repository.UserPreferencesRepository
import com.mmg.manahub.core.push.PushDeeplinkRouter
import com.mmg.manahub.core.util.recordSafeNonFatal
import com.mmg.manahub.core.ui.components.MagicToastHost
import com.mmg.manahub.core.ui.components.MagicToastType
import com.mmg.manahub.core.ui.components.rememberMagicToastState
import com.mmg.manahub.core.ui.theme.AppTheme
import com.mmg.manahub.core.ui.theme.LocalPreferredCurrency
import com.mmg.manahub.core.ui.theme.MagicThemeAndroid
import com.mmg.manahub.feature.gamification.presentation.GamificationCelebrationHost
import dagger.hilt.android.AndroidEntryPoint
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.handleDeeplinks
import java.util.Locale
import javax.inject.Inject

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
     * redirect, AND password recovery — all three share the scheme/host). [handleDeeplinks] always
     * runs first — it is what imports the session (including the temporary, fully-authenticated
     * recovery session GoTrue mints for a recovery callback) — and a recovery callback additionally
     * routes into Compose Navigation via the existing [PushDeeplinkRouter] Activity→Compose bridge
     * (the same one FCM background deep links already use) — see `AppNavGraph.kt`'s
     * `Screen.ResetPasswordConfirm` composable KDoc for why a distinct `manahub://auth/recovery`
     * route (rather than matching the raw external intent) is required here.
     *
     * SECURITY — two layers of defense against a forged intent (this Activity's `manahub://auth`
     * intent filter is `exported=true` with no path/signature restriction, so any installed app can
     * send it an arbitrary `Intent`):
     *
     * 1. (Primary gate, enforced downstream) [isPasswordRecoveryDeepLink] only reflects what the
     *    URI *claims* — it is attacker-controlled and proves nothing about the resulting session.
     *    The actual authorization check is [AuthUser.isRecoverySession] (the server-issued `amr`
     *    JWT claim), verified by `AuthViewModel.confirmPasswordReset` and
     *    `ResetPasswordConfirmScreen` before any password change is allowed — see their SECURITY
     *    KDocs. This method's `isRecoveryClaim` is only ever used to decide whether to attempt
     *    navigation, never as proof of a genuine recovery flow.
     * 2. (Defense-in-depth, this method) [PushDeeplinkRouter.enqueue] is called ONLY from inside
     *    [handleDeeplinks]'s `onSessionSuccess` callback, which the SDK invokes ONLY after it has
     *    actually parsed real tokens out of the intent and imported a session
     *    (`Auth.parseFragmentAndImportSession` requires `access_token`/`refresh_token`/
     *    `expires_in`/`token_type` to all be present in the fragment, or it throws before any
     *    session is imported/callback fires — verified against `auth-kt` 3.1.4 sources). A forged
     *    intent that merely sets `type=recovery` with no real tokens attached — e.g.
     *    `Intent(ACTION_VIEW, "manahub://auth?type=recovery")` — never reaches `onSessionSuccess`,
     *    so the recovery deep link is never even enqueued for that case. This narrows the window
     *    before layer 1 even runs; it is NOT a substitute for layer 1 (a forged intent CAN carry a
     *    real, currently-valid recovery token replayed from an intercepted email, which WOULD reach
     *    `onSessionSuccess` — layer 1 is what actually stops that case, since the resulting
     *    session's `amr` claim would legitimately say "recovery" and this is in fact then a real,
     *    intended recovery flow).
     *
     * `onSessionSuccess` fires from the SDK's internal `authScope`, which this project leaves on
     * its default `Dispatchers.Default` (no `coroutineDispatcher` override in
     * `SupabaseClientFactory.kt`) — i.e. NOT the main thread. [PushDeeplinkRouter.enqueue] can
     * synchronously call `NavController.navigate(...)`, which requires the main thread, so the
     * enqueue is explicitly hopped via [runOnUiThread].
     *
     * The `try/catch` guards the synchronous half of [handleDeeplinks]: `parseSessionFromFragment`
     * throws a plain `IllegalArgumentException` when the fragment is missing a required token field
     * (exactly the forged, token-less `manahub://auth#type=recovery` case) — uncaught, this would
     * crash the app from `onCreate`/`onNewIntent` on nothing more than a malicious intent from
     * another app. [recordSafeNonFatal] logs the failure (never the token/intent data).
     */
    private fun handleSupabaseAuthDeepLink(intent: Intent) {
        if (!isSupabaseAuthDeepLink(intent)) return
        val isRecoveryClaim = isPasswordRecoveryDeepLink(intent)
        try {
            supabaseClient.handleDeeplinks(intent) {
                // Reached only when handleDeeplinks actually imported a session from genuine
                // tokens present in this intent — see the SECURITY KDoc above (layer 2).
                if (isRecoveryClaim) {
                    runOnUiThread {
                        PushDeeplinkRouter.enqueue("manahub://auth/recovery")
                    }
                }
            }
        } catch (e: Exception) {
            recordSafeNonFatal("main_activity_auth_deeplink_parse_failed", e)
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
