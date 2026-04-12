package com.mmg.manahub.core.util

import com.mmg.manahub.core.domain.model.PreferredCurrency

object PriceFormatter {

    /**
     * Determina si el dispositivo usa formato europeo (utilizado para el valor inicial por defecto).
     * Delega la lógica a LocaleLanguageProvider.
     */
    fun isEuropeanLocale(): Boolean = LocaleLanguageProvider.isEuropeanLocale()

    /**
     * Formatea un precio dado su valor y divisa preferida.
     * @param amount El valor numérico.
     * @param currency La divisa a utilizar (USD o EUR).
     * @param showSymbol Si se debe incluir el símbolo de la moneda.
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
     * Sobrecarga para String? (formato habitual de APIs).
     */
    fun format(
        amount: String?,
        currency: PreferredCurrency,
        showSymbol: Boolean = true
    ): String {
        return format(amount?.toDoubleOrNull(), currency, showSymbol)
    }

    private fun formatEur(amount: Double, showSymbol: Boolean): String {
        // Formato europeo: separador de miles = punto, separador decimal = coma, símbolo al final
        val formatted = String.format(java.util.Locale.GERMAN, "%,.2f", amount)
        return if (showSymbol) "$formatted €" else formatted
    }

    private fun formatUsd(amount: Double, showSymbol: Boolean): String {
        // Formato americano: separador de miles = coma, separador decimal = punto, símbolo al inicio
        val formatted = String.format(java.util.Locale.US, "%,.2f", amount)
        return if (showSymbol) "\$$formatted" else formatted
    }

    /**
     * Selecciona el precio correcto (USD o EUR) de un par de valores según la preferencia.
     */
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

    /**
     * Selecciona el precio correcto (USD o EUR) de un par de valores según la preferencia.
     */
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

    /**
     * Formatea directamente desde los dos campos de Scryfall (String?).
     */
    fun formatFromScryfall(
        priceUsd: String?,
        priceEur: String?,
        preferredCurrency: PreferredCurrency,
        showSymbol: Boolean = true,
    ): String {
        val (amount, currency) = selectPrice(priceUsd, priceEur, preferredCurrency)
        return format(amount, currency, showSymbol)
    }

    /**
     * Formatea directamente desde Double? (campos del modelo Card).
     */
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
