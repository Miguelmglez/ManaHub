package com.mmg.manahub.feature.settings.presentation

import com.mmg.manahub.core.domain.model.AppLanguage
import com.mmg.manahub.core.domain.model.CardLanguage
import com.mmg.manahub.core.domain.model.CollectionViewMode
import com.mmg.manahub.core.domain.model.NewsLanguage
import com.mmg.manahub.core.domain.model.PreferredCurrency
import com.mmg.manahub.core.domain.model.UserPreferences
import com.mmg.manahub.core.ui.theme.AppTheme

data class SettingsUiState(
    val currentTheme: AppTheme = AppTheme.NeonVoid,
    // ── Privacy toggles ───────────────────────────────────────────────────────
    /** Mirrors the `collection_public` column in `user_profiles`. Default: false (private). */
    val collectionPublic: Boolean = false,
    /** Mirrors the `wishlist_public` column in `user_profiles`. Default: true (public). */
    val wishlistPublic: Boolean = true,
    /** Mirrors the `trade_list_public` column in `user_profiles`. Default: true (public). */
    val tradeListPublic: Boolean = true,
    /** Non-null while a privacy-update toast should be shown; cleared by [SettingsViewModel.clearPrivacyToast]. */
    val privacyToastMessage: String? = null,
    /** True when [privacyToastMessage] represents an error; false for a success message. */
    val privacyToastIsError: Boolean = false,
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
