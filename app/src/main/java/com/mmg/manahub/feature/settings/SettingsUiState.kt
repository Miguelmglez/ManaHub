package com.mmg.manahub.feature.settings

import com.mmg.manahub.core.domain.model.AppLanguage
import com.mmg.manahub.core.domain.model.CardLanguage
import com.mmg.manahub.core.domain.model.CollectionViewMode
import com.mmg.manahub.core.domain.model.NewsLanguage
import com.mmg.manahub.core.domain.model.PreferredCurrency
import com.mmg.manahub.core.domain.model.UserPreferences
import com.mmg.manahub.core.ui.theme.AppTheme

data class SettingsUiState(
    val autoRefreshPrices: Boolean = false,
    val currentTheme: AppTheme = AppTheme.NeonVoid,
)

data class PreferencesState(
    val userPreferences: UserPreferences = UserPreferences(
        appLanguage = AppLanguage.ENGLISH,
        cardLanguage = CardLanguage.ENGLISH,
        newsLanguages = setOf(NewsLanguage.ENGLISH),
        preferredCurrency = PreferredCurrency.USD,
        collectionViewMode = CollectionViewMode.GRID,
    )
)
