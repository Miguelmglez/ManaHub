package com.mmg.manahub.core.util

import java.util.Locale

/**
 * Plain-JVM [PriceFormatter] actual (Deck Engine Unification plan, RUN 5 / D5 — added so
 * :tools:tag-pipeline can depend on this module's commonMain). Identical logic to the Android
 * actual (`PriceFormatterPlatform.android.kt`) — both target a real `java.util.Locale`-backed JVM,
 * so there is no reason for the two to diverge. Not currently exercised by the tag pipeline (it
 * never formats a price), but the `expect`/`actual` pair must resolve for every declared target for
 * commonMain to compile at all.
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

internal actual fun platformIsEuropeanLocale(): Boolean {
    val country = Locale.getDefault().country
    return country.uppercase() in EUROPEAN_COUNTRIES
}
