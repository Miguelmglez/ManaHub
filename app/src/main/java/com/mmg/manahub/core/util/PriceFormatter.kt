package com.mmg.manahub.core.util

import com.mmg.manahub.core.domain.model.PreferredCurrency

object PriceFormatter {

    /**
     * Returns true when the device locale uses European number formatting.
     * Delegates to [LocaleLanguageProvider.isEuropeanLocale].
     */
    fun isEuropeanLocale(): Boolean = LocaleLanguageProvider.isEuropeanLocale()

    /**
     * Formats a price for the given [currency].
     * @param amount Numeric price value.
     * @param currency The currency to use (USD or EUR).
     * @param showSymbol Whether to include the currency symbol.
     */
    fun format(
        amount: Double?,
        currency: PreferredCurrency,
        showSymbol: Boolean = true
    ): String {
        if (amount == null) return "—"

        return when (currency) {
            PreferredCurrency.EUR -> formatEur(amount, showSymbol)
            PreferredCurrency.USD -> formatUsd(amount, showSymbol)
        }
    }

    /**
     * Overload for [String?] values as returned by Scryfall API fields.
     */
    fun format(
        amount: String?,
        currency: PreferredCurrency,
        showSymbol: Boolean = true
    ): String {
        return format(amount?.toDoubleOrNull(), currency, showSymbol)
    }

    private fun formatEur(amount: Double, showSymbol: Boolean): String {
        // European format: thousands separator = dot, decimal separator = comma, symbol at the end.
        val formatted = String.format(java.util.Locale.GERMAN, "%,.2f", amount)
        return if (showSymbol) "$formatted €" else formatted
    }

    private fun formatUsd(amount: Double, showSymbol: Boolean): String {
        // US format: thousands separator = comma, decimal separator = dot, symbol at the front.
        val formatted = String.format(java.util.Locale.US, "%,.2f", amount)
        return if (showSymbol) "\$$formatted" else formatted
    }

    /** Picks the correct price (USD or EUR) from a pair of values based on the preferred currency. */
    fun selectPrice(
        priceUsd: String?,
        priceEur: String?,
        preferredCurrency: PreferredCurrency,
    ): Pair<Double?, PreferredCurrency> {
        return if (preferredCurrency == PreferredCurrency.EUR) {
            Pair(priceEur?.toDoubleOrNull(), PreferredCurrency.EUR)
        } else {
            Pair(priceUsd?.toDoubleOrNull(), PreferredCurrency.USD)
        }
    }

    /** Picks the correct price (USD or EUR) from a pair of [Double?] values based on the preferred currency. */
    fun selectPrice(
        priceUsd: Double?,
        priceEur: Double?,
        preferredCurrency: PreferredCurrency,
    ): Pair<Double?, PreferredCurrency> {
        return if (preferredCurrency == PreferredCurrency.EUR) {
            Pair(priceEur, PreferredCurrency.EUR)
        } else {
            Pair(priceUsd, PreferredCurrency.USD)
        }
    }

    /** Formats a price directly from the two Scryfall [String?] fields. */
    fun formatFromScryfall(
        priceUsd: String?,
        priceEur: String?,
        preferredCurrency: PreferredCurrency,
        showSymbol: Boolean = true,
    ): String {
        val (amount, currency) = selectPrice(priceUsd, priceEur, preferredCurrency)
        return format(amount, currency, showSymbol)
    }

    /** Formats a price directly from the [Double?] fields on the [Card] domain model. */
    fun formatFromScryfall(
        priceUsd: Double?,
        priceEur: Double?,
        preferredCurrency: PreferredCurrency,
        showSymbol: Boolean = true,
    ): String {
        val (amount, currency) = selectPrice(priceUsd, priceEur, preferredCurrency)
        return format(amount, currency, showSymbol)
    }
}
