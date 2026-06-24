package com.mmg.manahub.core.util

import java.util.Locale

/**
 * Android implementation: uses [java.util.Locale] + [String.format] for locale-aware number formatting.
 */
internal actual fun formatCurrencyAmount(amount: Double, locale: PriceFormatter.NumberLocale): String {
    val javaLocale = when (locale) {
        PriceFormatter.NumberLocale.US -> Locale.US
        PriceFormatter.NumberLocale.EUROPEAN -> Locale.GERMAN
    }
    return String.format(javaLocale, "%,.2f", amount)
}

private val EUROPEAN_COUNTRIES = setOf(
    "AT", "BE", "CY", "EE", "FI", "FR", "DE", "GR", "IE",
    "IT", "LV", "LT", "LU", "MT", "NL", "PT", "SK", "SI",
    "ES", "HR",
    "GB", "CH", "NO", "SE", "DK", "PL", "CZ", "HU", "RO",
    "BG", "RS", "BA", "AL", "MK", "ME", "XK",
    "RU", "UA", "TR"
)

/**
 * Android implementation: checks device locale country against European country set.
 */
internal actual fun platformIsEuropeanLocale(): Boolean {
    val country = Locale.getDefault().country
    return country.uppercase() in EUROPEAN_COUNTRIES
}
