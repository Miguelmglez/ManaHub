package com.mmg.magicfolder.core.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mmg.magicfolder.core.domain.model.AppLanguage
import com.mmg.magicfolder.core.domain.model.CardLanguage
import com.mmg.magicfolder.core.domain.model.NewsLanguage
import com.mmg.magicfolder.core.domain.model.PreferredCurrency
import com.mmg.magicfolder.core.domain.model.UserPreferences
import com.mmg.magicfolder.core.domain.repository.UserPreferencesRepository
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
                        ?: if (deviceLang in listOf("en", "es", "de")) deviceLang else "en"
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

    private fun defaultPreferences() = UserPreferences(
        appLanguage = AppLanguage.ENGLISH,
        cardLanguage = CardLanguage.ENGLISH,
        newsLanguages = setOf(NewsLanguage.ENGLISH),
        preferredCurrency = if (isEuropeanLocale()) PreferredCurrency.EUR else PreferredCurrency.USD,
    )

    private fun isEuropeanLocale(): Boolean {
        val europeanCountries = setOf(
            "AT","BE","CY","EE","FI","FR","DE","GR","IE","IT","LV","LT","LU","MT","NL",
            "PT","SK","SI","ES","HR","GB","CH","NO","SE","DK","PL","CZ","HU","RO",
            "BG","RS","BA","AL","MK","ME","XK","RU","UA","TR"
        )
        return Locale.getDefault().country.uppercase() in europeanCountries
    }
}
