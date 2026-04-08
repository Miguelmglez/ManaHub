package com.mmg.magicfolder.core.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mmg.magicfolder.core.domain.model.AppLanguage
import com.mmg.magicfolder.core.domain.model.CardLanguage
import com.mmg.magicfolder.core.domain.model.NewsLanguage
import com.mmg.magicfolder.core.domain.model.PreferredCurrency
import com.mmg.magicfolder.core.domain.model.UserDefinedTag
import com.mmg.magicfolder.core.domain.model.UserPreferences
import com.mmg.magicfolder.core.domain.repository.UserPreferencesRepository
import com.mmg.magicfolder.core.ui.theme.AppTheme
import com.mmg.magicfolder.core.util.PriceFormatter.isEuropeanLocale
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

internal val Context.userPrefsDataStore by preferencesDataStore(name = "user_prefs")

private val KEY_APP_LANGUAGE      = stringPreferencesKey("app_language")
private val KEY_CARD_LANGUAGE     = stringPreferencesKey("card_language")
private val KEY_NEWS_LANGUAGES    = stringSetPreferencesKey("news_languages")
private val KEY_PREFERRED_CURRENCY = stringPreferencesKey("preferred_currency")
private val LAST_PRICE_REFRESH_KEY = longPreferencesKey("last_price_refresh")
private val AUTO_REFRESH_KEY       = booleanPreferencesKey("auto_refresh_prices")
private val AVATAR_URL_KEY         = stringPreferencesKey("avatar_url")
private val KEY_PLAYER_NAME = stringPreferencesKey("player_name")
private val KEY_APP_THEME   = stringPreferencesKey("app_theme")
private val KEY_TAG_AUTO_THRESHOLD    = floatPreferencesKey("tag_auto_threshold")
private val KEY_TAG_SUGGEST_THRESHOLD = floatPreferencesKey("tag_suggest_threshold")
private val KEY_TAG_OVERRIDES_JSON    = stringPreferencesKey("tag_dictionary_overrides")
private val KEY_USER_DEFINED_TAGS     = stringPreferencesKey("user_defined_tags")

private data class UdtRecord(val k: String, val l: String, val c: String)
private val udtListType = object : TypeToken<List<UdtRecord>>() {}.type
private val gson = Gson()

@Singleton
class UserPreferencesDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : UserPreferencesRepository {

    override val preferencesFlow: Flow<UserPreferences> = context.userPrefsDataStore.data
        .map { prefs ->
            val deviceLang = Locale.getDefault().language
            val defaultNewsLangs = buildSet {
                add("en")
                if (deviceLang == "es" || deviceLang == "de") add(deviceLang)
            }
            val defaultCurrency = if (isEuropeanLocale()) "EUR" else "USD"

            UserPreferences(
                appLanguage = AppLanguage.fromCode(
                    prefs[KEY_APP_LANGUAGE]
                        ?: "en"
                        //?: if (deviceLang in listOf("en", "es", "de")) deviceLang else "en"
                ),
                cardLanguage = CardLanguage.fromCode(
                    prefs[KEY_CARD_LANGUAGE] ?: "en"
                ),
                newsLanguages = (prefs[KEY_NEWS_LANGUAGES] ?: defaultNewsLangs)
                    .mapNotNull { NewsLanguage.entries.find { l -> l.code == it } }
                    .toSet()
                    .ifEmpty { setOf(NewsLanguage.ENGLISH) },
                preferredCurrency = PreferredCurrency.fromCode(
                    prefs[KEY_PREFERRED_CURRENCY] ?: defaultCurrency
                ),
            )
        }
        .catch { emit(defaultPreferences()) }

    override suspend fun setAppLanguage(language: AppLanguage) {
        context.userPrefsDataStore.edit { it[KEY_APP_LANGUAGE] = language.code }
        // Also persist to SharedPreferences for synchronous access in attachBaseContext
        context.getSharedPreferences("user_prefs_lang_sync", Context.MODE_PRIVATE)
            .edit()
            .putString("app_language_sync", language.code)
            .apply()
    }

    override suspend fun setCardLanguage(language: CardLanguage) {
        context.userPrefsDataStore.edit { it[KEY_CARD_LANGUAGE] = language.code }
    }

    override suspend fun setNewsLanguages(languages: Set<NewsLanguage>) {
        context.userPrefsDataStore.edit { it[KEY_NEWS_LANGUAGES] = languages.map { l -> l.code }.toSet() }
    }

    override suspend fun setPreferredCurrency(currency: PreferredCurrency) {
        context.userPrefsDataStore.edit { it[KEY_PREFERRED_CURRENCY] = currency.code }
    }

    val preferredCurrencyFlow: Flow<PreferredCurrency> = preferencesFlow
        .map { it.preferredCurrency }

    suspend fun savePreferredCurrency(currency: PreferredCurrency) {
        setPreferredCurrency(currency)
    }

    private fun defaultPreferences() = UserPreferences(
        appLanguage = AppLanguage.ENGLISH,
        cardLanguage = CardLanguage.ENGLISH,
        newsLanguages = setOf(NewsLanguage.ENGLISH),
        preferredCurrency = if (isEuropeanLocale()) PreferredCurrency.EUR else PreferredCurrency.USD,
    )


    val lastPriceRefreshFlow: Flow<Long?> = context.userPrefsDataStore.data
        .map { it[LAST_PRICE_REFRESH_KEY] }

    val autoRefreshPricesFlow: Flow<Boolean> = context.userPrefsDataStore.data
        .map { it[AUTO_REFRESH_KEY] ?: false }

    suspend fun saveLastPriceRefresh(timestamp: Long) {
        context.userPrefsDataStore.edit { it[LAST_PRICE_REFRESH_KEY] = timestamp }
    }

    suspend fun saveAutoRefreshPrices(enabled: Boolean) {
        context.userPrefsDataStore.edit { it[AUTO_REFRESH_KEY] = enabled }
    }

    // ── Avatar URL ────────────────────────────────────────────────────────────

    val avatarUrlFlow: Flow<String?> = context.userPrefsDataStore.data
        .map { it[AVATAR_URL_KEY] }
        .catch { emit(null) }

    suspend fun saveAvatarUrl(url: String?) {
        context.userPrefsDataStore.edit { preferences ->
            if (url == null)
                preferences.remove(AVATAR_URL_KEY)
            else
                preferences[AVATAR_URL_KEY] = url
        }
    }
    val playerNameFlow: Flow<String> = context.userPrefsDataStore.data
        .map { prefs -> prefs[KEY_PLAYER_NAME] ?: "Player 1" }
        .catch { emit("Player 1") }

    val themeFlow: Flow<AppTheme> = context.userPrefsDataStore.data
        .map { prefs ->
            when (prefs[KEY_APP_THEME]) {
                "MEDIEVAL_GRIMOIRE" -> AppTheme.MedievalGrimoire
                "ARCANE_COSMOS"     -> AppTheme.ArcaneCosmos
                else                -> AppTheme.NeonVoid
            }
        }
        .catch { emit(AppTheme.NeonVoid) }

    suspend fun savePlayerName(name: String) {
        context.userPrefsDataStore.edit { it[KEY_PLAYER_NAME] = name }
    }

    // ── Tag auto-tagger thresholds ────────────────────────────────────────────

    val tagAutoThresholdFlow: Flow<Float> = context.userPrefsDataStore.data
        .map { it[KEY_TAG_AUTO_THRESHOLD] ?: 0.90f }
        .catch { emit(0.90f) }

    val tagSuggestThresholdFlow: Flow<Float> = context.userPrefsDataStore.data
        .map { it[KEY_TAG_SUGGEST_THRESHOLD] ?: 0.60f }
        .catch { emit(0.60f) }

    suspend fun saveTagAutoThreshold(value: Float) {
        context.userPrefsDataStore.edit { it[KEY_TAG_AUTO_THRESHOLD] = value.coerceIn(0f, 1f) }
    }

    suspend fun saveTagSuggestThreshold(value: Float) {
        context.userPrefsDataStore.edit { it[KEY_TAG_SUGGEST_THRESHOLD] = value.coerceIn(0f, 1f) }
    }

    // ── Tag dictionary overrides (JSON blob) ─────────────────────────────────

    val tagDictionaryOverridesFlow: Flow<String> = context.userPrefsDataStore.data
        .map { it[KEY_TAG_OVERRIDES_JSON] ?: "[]" }
        .catch { emit("[]") }

    suspend fun saveTagDictionaryOverrides(json: String) {
        context.userPrefsDataStore.edit { it[KEY_TAG_OVERRIDES_JSON] = json }
    }

    // ── User-defined tags ─────────────────────────────────────────────────────

    val userDefinedTagsFlow: Flow<List<UserDefinedTag>> = context.userPrefsDataStore.data
        .map { prefs ->
            val json = prefs[KEY_USER_DEFINED_TAGS] ?: "[]"
            runCatching<List<UserDefinedTag>> {
                val records: List<UdtRecord> = gson.fromJson(json, udtListType) ?: emptyList()
                records.map { UserDefinedTag(key = it.k, label = it.l, categoryKey = it.c) }
            }.getOrDefault(emptyList())
        }
        .catch { emit(emptyList()) }

    suspend fun saveUserDefinedTag(tag: UserDefinedTag) {
        context.userPrefsDataStore.edit { prefs ->
            val json = prefs[KEY_USER_DEFINED_TAGS] ?: "[]"
            val records: MutableList<UdtRecord> = runCatching<List<UdtRecord>> {
                gson.fromJson(json, udtListType) ?: emptyList()
            }.getOrDefault(emptyList()).toMutableList()
            records.removeAll { it.k == tag.key }
            records.add(UdtRecord(k = tag.key, l = tag.label, c = tag.categoryKey))
            prefs[KEY_USER_DEFINED_TAGS] = gson.toJson(records)
        }
    }

    suspend fun deleteUserDefinedTag(key: String) {
        context.userPrefsDataStore.edit { prefs ->
            val json = prefs[KEY_USER_DEFINED_TAGS] ?: "[]"
            val records: MutableList<UdtRecord> = runCatching<List<UdtRecord>> {
                gson.fromJson(json, udtListType) ?: emptyList()
            }.getOrDefault(emptyList()).toMutableList()
            records.removeAll { it.k == key }
            prefs[KEY_USER_DEFINED_TAGS] = gson.toJson(records)
        }
    }

    suspend fun saveTheme(theme: AppTheme) {
        context.userPrefsDataStore.edit { prefs ->
            prefs[KEY_APP_THEME] = when (theme) {
                is AppTheme.NeonVoid         -> "NEON_VOID"
                is AppTheme.MedievalGrimoire -> "MEDIEVAL_GRIMOIRE"
                is AppTheme.ArcaneCosmos     -> "ARCANE_COSMOS"
            }
        }
    }

}
