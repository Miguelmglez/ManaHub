package com.mmg.magicfolder.core.ui.theme

import androidx.compose.runtime.compositionLocalOf

/**
 * Holds the user's preferred currency code ("USD" or "EUR").
 * Provided at the root of the Compose tree in MainActivity.
 * Consumed by components that format prices (e.g. CardGridItem).
 */
val LocalPreferredCurrency = compositionLocalOf { "USD" }
