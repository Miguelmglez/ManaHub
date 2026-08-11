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
     * runs first (it is what imports the session — including the temporary, fully-authenticated
     * recovery session GoTrue mints for a recovery callback). A recovery callback additionally
     * routes into Compose Navigation via the existing [PushDeeplinkRouter] Activity→Compose bridge
     * (the same one FCM background deep links already use) — see `AppNavGraph.kt`'s
     * `Screen.ResetPasswordConfirm` composable KDoc for why a distinct `manahub://auth/recovery`
     * route (rather than matching the raw external intent) is required here.
     */
    private fun handleSupabaseAuthDeepLink(intent: Intent) {
        if (!isSupabaseAuthDeepLink(intent)) return
        val isRecovery = isPasswordRecoveryDeepLink(intent)
        supabaseClient.handleDeeplinks(intent)
        if (isRecovery) {
            PushDeeplinkRouter.enqueue("manahub://auth/recovery")
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
