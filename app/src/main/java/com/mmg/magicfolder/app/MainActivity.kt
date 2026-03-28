package com.mmg.magicfolder.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import com.mmg.magicfolder.app.navigation.AppNavGraph
import com.mmg.magicfolder.core.data.local.LanguagePreference
import com.mmg.magicfolder.core.ui.theme.AppTheme
import com.mmg.magicfolder.core.ui.theme.MagicTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var languagePreference: LanguagePreference

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val theme by languagePreference.themeFlow
                .collectAsStateWithLifecycle(initialValue = AppTheme.NeonVoid)

            MagicTheme(theme = theme) {
                AppNavGraph()
            }
        }
    }
}