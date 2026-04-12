package com.mmg.manahub.app

import android.content.Context
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
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var userPreferencesDataStore: UserPreferencesDataStore

    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository

    override fun attachBaseContext(newBase: Context) {
        /*val langCode = newBase
            .getSharedPreferences("user_prefs_lang_sync", Context.MODE_PRIVATE)
            .getString("app_language_sync", "")
            .orEmpty()

        val context = if (langCode.isNotEmpty()) {
            val locale = Locale(langCode)
            val config = Configuration(newBase.resources.configuration)
            config.setLocale(locale)
            newBase.createConfigurationContext(config)
        } else {
            newBase
        }*/
        // Forzamos el idioma a inglés ("en") ignorando las preferencias por ahora
        val locale = Locale("en")
        Locale.setDefault(locale)

        val config = Configuration(newBase.resources.configuration)
        config.setLocale(locale)

        val context = newBase.createConfigurationContext(config)
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
