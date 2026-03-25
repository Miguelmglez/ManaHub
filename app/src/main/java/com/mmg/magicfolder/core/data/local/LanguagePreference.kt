package com.mmg.magicfolder.core.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.langDataStore by preferencesDataStore(name = "lang_prefs")
private val KEY_LANG = stringPreferencesKey("selected_lang")

@Singleton
class LanguagePreference @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val languageFlow: Flow<String> = context.langDataStore.data
        .map { prefs -> prefs[KEY_LANG] ?: "en" }
        .catch { emit("en") }

    suspend fun set(lang: String) {
        context.langDataStore.edit { it[KEY_LANG] = lang }
    }
}
