package com.mmg.magicfolder.app

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
import com.mmg.magicfolder.app.navigation.AppNavGraph
import com.mmg.magicfolder.core.data.local.LanguagePreference
import com.mmg.magicfolder.core.domain.model.AppLanguage
import com.mmg.magicfolder.core.domain.repository.UserPreferencesRepository
import com.mmg.magicfolder.core.ui.theme.AppTheme
import com.mmg.magicfolder.core.ui.theme.LocalPreferredCurrency
import com.mmg.magicfolder.core.ui.theme.MagicTheme
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var languagePreference: LanguagePreference
    @Inject lateinit var userPreferencesRepository: UserPreferencesRepository

    override fun attachBaseContext(newBase: Context) {
        val langCode = newBase
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
        }
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val theme by languagePreference.themeFlow
                .collectAsStateWithLifecycle(initialValue = AppTheme.NeonVoid)

            val userPrefs by userPreferencesRepository.preferencesFlow
                .collectAsStateWithLifecycle(initialValue = null)

            MagicTheme(theme = theme) {
                CompositionLocalProvider(
                    LocalPreferredCurrency provides (userPrefs?.preferredCurrency?.code ?: "USD"),
                ) {
                    AppNavGraph()
                }
            }
        }
    }
}
