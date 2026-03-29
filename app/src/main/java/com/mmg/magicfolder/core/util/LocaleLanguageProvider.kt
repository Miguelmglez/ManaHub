package com.mmg.magicfolder.core.util

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
        else -> "en"
    }
}
