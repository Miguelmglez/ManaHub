package com.mmg.manahub.core.domain.repository

import com.mmg.manahub.core.domain.model.AppLanguage
import com.mmg.manahub.core.domain.model.CardLanguage
import com.mmg.manahub.core.domain.model.NewsLanguage
import com.mmg.manahub.core.domain.model.PreferredCurrency
import com.mmg.manahub.core.domain.model.UserPreferences
import kotlinx.coroutines.flow.Flow

interface UserPreferencesRepository {
    val preferencesFlow: Flow<UserPreferences>
    suspend fun setAppLanguage(language: AppLanguage)
    suspend fun setCardLanguage(language: CardLanguage)
    suspend fun setNewsLanguages(languages: Set<NewsLanguage>)
    suspend fun setPreferredCurrency(currency: PreferredCurrency)
}
