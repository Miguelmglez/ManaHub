package com.mmg.manahub.core.util

import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Returns the Scryfall-compatible language code for the current device locale.
 * Used to localise card search results without a manual language selector.
 */
@Singleton
class LocaleLanguageProvider @Inject constructor() {

    fun get(): String = when (Locale.getDefault().language) {
        "ja" -> "ja"
        "de" -> "de"
        "fr" -> "fr"
        "es" -> "es"
        "pt" -> "pt"
        "it" -> "it"
        "ko" -> "ko"
        "ru" -> "ru"
        else -> getEnglishCountryCode()
    }

    private fun getEnglishCountryCode(): String {
        val locale = Locale.getDefault()
        val country = locale.country
        val finalCountry = if (country.isNullOrBlank()) {
            locale.toLanguageTag().split("-")
                .find { it.length == 2 && it.all { c -> c.isLetter() } }
        } else {
            country
        }
        
        return when (finalCountry?.uppercase()) {
            "GB" -> "gb"
            else -> "us"
        }
    }

    companion object {
        private val EUROPEAN_COUNTRIES = setOf(
            // Eurozona
            "AT","BE","CY","EE","FI","FR","DE","GR","IE",
            "IT","LV","LT","LU","MT","NL","PT","SK","SI",
            "ES","HR",
            // Europa no eurozona pero con formato europeo
            "GB","CH","NO","SE","DK","PL","CZ","HU","RO",
            "BG","RS","BA","AL","MK","ME","XK",
            // Otros con formato europeo
            "RU","UA","TR", ""
        )

        /**
         * Returns true when the device locale uses European number formatting.
         * Falls back to the language tag when the country field is empty.
         */
        fun isEuropeanLocale(): Boolean {
            val locale = Locale.getDefault()
            val country = locale.country
            return country.uppercase() in EUROPEAN_COUNTRIES
        }
    }
}
