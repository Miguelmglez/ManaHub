package com.mmg.manahub.core.util

/**
 * wasmJs implementation: manual number formatting with thousands separators and 2 decimal places.
 *
 * Does not rely on any JS/browser API — pure Kotlin arithmetic, safe for all wasm runtimes.
 */
internal actual fun formatCurrencyAmount(amount: Double, locale: PriceFormatter.NumberLocale): String {
    val isNegative = amount < 0.0
    val absAmount = if (isNegative) -amount else amount

    // Format to 2 decimal places via rounding
    val wholePart = absAmount.toLong()
    val fractionalPart = ((absAmount - wholePart) * 100 + 0.5).toLong()
    val decimalStr = fractionalPart.toString().padStart(2, '0')

    // Add thousands separators
    val wholeStr = wholePart.toString()
    val withSeparators = buildString {
        val startIndex = wholeStr.length % 3
        wholeStr.forEachIndexed { index, char ->
            if (index > 0 && (index - startIndex) % 3 == 0) {
                append(
                    when (locale) {
                        PriceFormatter.NumberLocale.US -> ','
                        PriceFormatter.NumberLocale.EUROPEAN -> '.'
                    }
                )
            }
            append(char)
        }
    }

    val decimalSeparator = when (locale) {
        PriceFormatter.NumberLocale.US -> '.'
        PriceFormatter.NumberLocale.EUROPEAN -> ','
    }

    val result = "$withSeparators$decimalSeparator$decimalStr"
    return if (isNegative) "-$result" else result
}

/**
 * wasmJs implementation: returns false (US format) as a safe default.
 *
 * TODO: once kotlinx-browser is wired (Phase 2 web work), read `navigator.language`
 *  via proper Kotlin/Wasm JS interop and check against the European country/language sets.
 */
internal actual fun platformIsEuropeanLocale(): Boolean = false
