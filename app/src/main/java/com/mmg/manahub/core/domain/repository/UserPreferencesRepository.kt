package com.mmg.manahub.core.domain.repository

import com.mmg.manahub.core.domain.model.AppLanguage
import com.mmg.manahub.core.domain.model.CardLanguage
import com.mmg.manahub.core.domain.model.NewsLanguage
import com.mmg.manahub.core.domain.model.PreferredCurrency
import com.mmg.manahub.core.domain.model.UserDefinedTag
import com.mmg.manahub.core.domain.model.UserPreferences
import kotlinx.coroutines.flow.Flow

/**
 * Domain-level contract for user preferences.
 *
 * Presentation-layer code should depend only on this interface,
 * never on the concrete DataStore implementation.
 */
interface UserPreferencesRepository {
    val preferencesFlow: Flow<UserPreferences>
    val preferredCurrencyFlow: Flow<PreferredCurrency>
    val lastPriceRefreshFlow: Flow<Long?>
    val autoRefreshPricesFlow: Flow<Boolean>
    val userDefinedTagsFlow: Flow<List<UserDefinedTag>>

    suspend fun setAppLanguage(language: AppLanguage)
    suspend fun setCardLanguage(language: CardLanguage)
    suspend fun setNewsLanguages(languages: Set<NewsLanguage>)
    suspend fun setPreferredCurrency(currency: PreferredCurrency)
    suspend fun saveLastPriceRefresh(timestamp: Long)
    suspend fun saveAutoRefreshPrices(enabled: Boolean)
    suspend fun saveUserDefinedTag(tag: UserDefinedTag)
    suspend fun deleteUserDefinedTag(key: String)
}
