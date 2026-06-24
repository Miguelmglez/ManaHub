package com.mmg.manahub.core.util

import com.mmg.manahub.core.model.PreferredCurrency

/**
 * Formats card prices for display, supporting USD and EUR currencies.
 * Uses platform-specific number formatting via [formatCurrencyAmount].
 */
object PriceFormatter {

    /**
     * Supported number format locales for currency display.
     */
    enum class NumberLocale {
        /** US format: comma thousands, dot decimal, symbol prefix ($1,234.56) */
        US,
        /** European format: dot thousands, comma decimal, symbol suffix (1.234,56 EUR) */
        EUROPEAN,
    }

    /**
     * Returns true when the device locale uses European number formatting.
     */
    fun isEuropeanLocale(): Boolean = platformIsEuropeanLocale()

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
        val formatted = formatCurrencyAmount(amount, NumberLocale.EUROPEAN)
        return if (showSymbol) "$formatted €" else formatted
    }

    private fun formatUsd(amount: Double, showSymbol: Boolean): String {
        val formatted = formatCurrencyAmount(amount, NumberLocale.US)
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

    /** Formats a price directly from the [Double?] fields on the Card domain model. */
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

/**
 * Platform-specific: formats a [Double] amount with thousands separators and 2 decimal places
 * according to the given [locale].
 */
internal expect fun formatCurrencyAmount(amount: Double, locale: PriceFormatter.NumberLocale): String

/**
 * Platform-specific: returns true when the device/browser locale uses European number formatting.
 */
internal expect fun platformIsEuropeanLocale(): Boolean
