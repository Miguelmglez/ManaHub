package com.mmg.magicfolder.core.util

object PriceFormatter {

    // Locales europeos: países donde se usa EUR
    // o donde el formato decimal europeo es estándar
    private val EUROPEAN_COUNTRIES = setOf(
        // Eurozona
        "AT","BE","CY","EE","FI","FR","DE","GR","IE",
        "IT","LV","LT","LU","MT","NL","PT","SK","SI",
        "ES","HR",
        // Europa no eurozona pero con formato europeo
        "GB","CH","NO","SE","DK","PL","CZ","HU","RO",
        "BG","RS","BA","AL","MK","ME","XK",
        // Otros con formato europeo
        "RU","UA","TR"
    )

    // Determina si el dispositivo usa formato europeo
    fun isEuropeanLocale(): Boolean {
        val locale = java.util.Locale.getDefault()
        return locale.country.uppercase() in EUROPEAN_COUNTRIES
    }

    // Devuelve "eur" o "usd" según el locale del dispositivo
    fun getPreferredCurrency(): String {
        return if (isEuropeanLocale()) "eur" else "usd"
    }

    // Formatea un precio dado su valor y divisa
    // Europeo: "1.234,56 €"  (punto miles, coma decimal, símbolo al final)
    // USD:     "$1,234.56"   (coma miles, punto decimal, símbolo al inicio)
    fun format(
        amount: Double?,
        currency: String = getPreferredCurrency(),
        showSymbol: Boolean = true
    ): String {
        if (amount == null) return "—"

        return when (currency.lowercase()) {
            "eur" -> formatEur(amount, showSymbol)
            "usd" -> formatUsd(amount, showSymbol)
            else  -> formatUsd(amount, showSymbol)
        }
    }

    // Sobrecarga para String? (como viene de la API)
    fun format(
        amount: String?,
        currency: String = getPreferredCurrency(),
        showSymbol: Boolean = true
    ): String {
        return format(amount?.toDoubleOrNull(), currency, showSymbol)
    }

    private fun formatEur(amount: Double, showSymbol: Boolean): String {
        // Formato europeo: separador de miles = punto
        //                  separador decimal  = coma
        //                  símbolo al final con espacio
        val formatted = String.format(
            java.util.Locale.GERMAN,  // usa formato europeo
            "%,.2f",
            amount
        )
        return if (showSymbol) "$formatted €" else formatted
    }

    private fun formatUsd(amount: Double, showSymbol: Boolean): String {
        // Formato americano: separador de miles = coma
        //                    separador decimal  = punto
        //                    símbolo al inicio
        val formatted = String.format(
            java.util.Locale.US,
            "%,.2f",
            amount
        )
        return if (showSymbol) "\$$formatted" else formatted
    }

    // Para valores de Scryfall (String?): elige el campo correcto según el locale
    fun selectPrice(
        priceUsd: String?,
        priceEur: String?
    ): Pair<Double?, String> {
        return if (isEuropeanLocale()) {
            Pair(priceEur?.toDoubleOrNull(), "eur")
        } else {
            Pair(priceUsd?.toDoubleOrNull(), "usd")
        }
    }

    // Para valores Double? directamente: elige según el locale
    fun selectPrice(
        priceUsd: Double?,
        priceEur: Double?
    ): Pair<Double?, String> {
        return if (isEuropeanLocale()) {
            Pair(priceEur, "eur")
        } else {
            Pair(priceUsd, "usd")
        }
    }

    // Formatea directamente desde los dos campos de Scryfall (String?)
    fun formatFromScryfall(
        priceUsd: String?,
        priceEur: String?,
        showSymbol: Boolean = true
    ): String {
        val (amount, currency) = selectPrice(priceUsd, priceEur)
        return format(amount, currency, showSymbol)
    }

    // Formatea directamente desde Double? (como vienen del modelo Card)
    fun formatFromScryfall(
        priceUsd: Double?,
        priceEur: Double?,
        showSymbol: Boolean = true
    ): String {
        val (amount, currency) = selectPrice(priceUsd, priceEur)
        return format(amount, currency, showSymbol)
    }
}
