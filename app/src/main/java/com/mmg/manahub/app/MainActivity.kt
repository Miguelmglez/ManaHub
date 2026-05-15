package com.mmg.manahub.app

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mmg.manahub.app.navigation.AppNavGraph
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.domain.repository.UserPreferencesRepository
import com.mmg.manahub.core.ui.theme.AppTheme
import com.mmg.manahub.core.ui.theme.LocalPreferredCurrency
import com.mmg.manahub.core.ui.theme.MagicTheme
import dagger.hilt.android.AndroidEntryPoint
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.handleDeeplinks
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (isSupabaseAuthDeepLink(intent)) {
            supabaseClient.handleDeeplinks(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        // Release the system splash immediately so the Compose splash takes over without
        // a double-loading delay. The Compose splash owns the session-resolution wait.
        splashScreen.setKeepOnScreenCondition { false }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (isSupabaseAuthDeepLink(intent)) {
            supabaseClient.handleDeeplinks(intent)
        }
        setContent {
            val theme by userPreferencesDataStore.themeFlow
                .collectAsStateWithLifecycle(initialValue = AppTheme.NeonVoid)

            val userPrefs by userPreferencesRepository.preferencesFlow
                .collectAsStateWithLifecycle(initialValue = null)

            MagicTheme(theme = theme) {
                CompositionLocalProvider(
                    LocalPreferredCurrency provides (userPrefs?.preferredCurrency ?: com.mmg.manahub.core.domain.model.PreferredCurrency.USD),
                ) {
                    AppNavGraph()
                }
            }
        }
    }
}
