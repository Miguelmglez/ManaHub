package com.mmg.manahub.feature.settings.presentation

import com.mmg.manahub.core.model.AppLanguage
import com.mmg.manahub.core.model.CardLanguage
import com.mmg.manahub.core.model.CollectionViewMode
import com.mmg.manahub.core.model.PreferredCurrency
import com.mmg.manahub.core.model.UserPreferences
import com.mmg.manahub.core.ui.theme.AppTheme

/** One-shot feedback the screen renders as a [com.mmg.manahub.core.ui.components.MagicToast]; the screen owns the strings. */
enum class SettingsToast {
    /** A privacy visibility PATCH failed; the local value was reverted to what the server holds. */
    PRIVACY_SAVE_FAILED,
    /** A notification preference write failed; the cached map was rolled back. */
    NOTIFICATION_SAVE_FAILED,
    /** The action needs a real (non-anonymous) signed-in account. */
    SIGN_IN_REQUIRED,
}

data class SettingsUiState(
    val currentTheme: AppTheme = AppTheme.Default,
    // ── Privacy toggles ───────────────────────────────────────────────────────
    /** Mirrors the `collection_public` column in `user_profiles`. Default: true (public). */
    val collectionPublic: Boolean = true,
    /** Mirrors the `wishlist_public` column in `user_profiles`. Default: true (public). */
    val wishlistPublic: Boolean = true,
    /** Mirrors the `trade_list_public` column in `user_profiles`. Default: true (public). */
    val tradeListPublic: Boolean = true,
    /** Privacy keys (`collection_public`, `wishlist_public`, `trade_list_public`) with a write in flight; their switches are disabled. */
    val pendingPrivacyKeys: Set<String> = emptySet(),

    /** Non-null while a toast should be shown; cleared by [SettingsViewModel.clearToast]. */
    val toastMessage: SettingsToast? = null,
    /** True when [toastMessage] represents an error. */
    val toastIsError: Boolean = false,
)

data class PreferencesState(
    val userPreferences: UserPreferences = UserPreferences(
        appLanguage = AppLanguage.ENGLISH,
        cardLanguage = CardLanguage.ENGLISH,
        preferredCurrency = PreferredCurrency.USD,
        collectionViewMode = CollectionViewMode.GRID,
    )
)
