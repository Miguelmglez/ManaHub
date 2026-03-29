package com.mmg.magicfolder.core.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mmg.magicfolder.core.ui.theme.AppTheme
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.langDataStore by preferencesDataStore(name = "lang_prefs")
private val KEY_PLAYER_NAME = stringPreferencesKey("player_name")
private val KEY_APP_THEME   = stringPreferencesKey("app_theme")

@Singleton
class LanguagePreference @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val playerNameFlow: Flow<String> = context.langDataStore.data
        .map { prefs -> prefs[KEY_PLAYER_NAME] ?: "Player 1" }
        .catch { emit("Player 1") }

    val themeFlow: Flow<AppTheme> = context.langDataStore.data
        .map { prefs ->
            when (prefs[KEY_APP_THEME]) {
                "MEDIEVAL_GRIMOIRE" -> AppTheme.MedievalGrimoire
                "ARCANE_COSMOS"     -> AppTheme.ArcaneCosmos
                else                -> AppTheme.NeonVoid
            }
        }
        .catch { emit(AppTheme.NeonVoid) }

    suspend fun savePlayerName(name: String) {
        context.langDataStore.edit { it[KEY_PLAYER_NAME] = name }
    }

    suspend fun saveTheme(theme: AppTheme) {
        context.langDataStore.edit { prefs ->
            prefs[KEY_APP_THEME] = when (theme) {
                is AppTheme.NeonVoid         -> "NEON_VOID"
                is AppTheme.MedievalGrimoire -> "MEDIEVAL_GRIMOIRE"
                is AppTheme.ArcaneCosmos     -> "ARCANE_COSMOS"
            }
        }
    }
}
