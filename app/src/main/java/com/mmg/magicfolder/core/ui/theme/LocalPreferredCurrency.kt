package com.mmg.magicfolder.core.ui.theme

import androidx.compose.runtime.compositionLocalOf
import com.mmg.magicfolder.core.domain.model.PreferredCurrency

/**
 * Holds the user's preferred currency.
 * Provided at the root of the Compose tree in MainActivity.
 * Consumed by components that format prices.
 */
val LocalPreferredCurrency = compositionLocalOf { PreferredCurrency.USD }
