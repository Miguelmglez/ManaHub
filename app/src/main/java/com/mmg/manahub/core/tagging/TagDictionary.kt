package com.mmg.manahub.core.tagging

import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.TagCategory
import com.mmg.manahub.core.util.CardTypeTranslator
import java.util.Locale

// ═══════════════════════════════════════════════════════════════════════════════
//  KMP migration compatibility layer (Deck Engine Unification plan, RUN 5 / D5).
//
//  [TagDictionary]'s base dictionary + override machinery moved to `:shared:core-data`
//  `commonMain` (`com.mmg.manahub.core.data.tagging.TagDictionary`) so `:tools:tag-pipeline` (a
//  plain-JVM CLI) can reuse the EXACT SAME production rule engine as the in-app tagging pipeline —
//  zero drift, mirroring the earlier moves of `KeywordAnalyzer`/`TypeLineAnalyzer`/
//  `GameChangerAnalyzer`/`StrategyAnalyzer` documented in this package's `TagAnalyzers.kt`.
//
//  [TagDictionary]/[TagOverride] are re-exported here via typealias so every existing `:app` call
//  site (`TagDictionaryRepository`, `TagDictionaryViewModel`, the Settings → Tag Dictionary screen,
//  tests) keeps compiling with zero changes. The ONE piece that genuinely could not move —
//  `localize` — stays here as an extension function: it needs `java.util.Locale` (JVM-only,
//  unavailable on wasmJs) and `:app`'s own [CardTypeTranslator] (an Android-only concern, out of
//  scope for commonMain). It is implemented purely against the shared object's PUBLIC API
//  ([TagDictionary.get]), so it behaves identically to the pre-move member-function version.
//
//  The old `TagDictionary.Entry` nested typealias (`@Suppress("unused")`, verified unreferenced
//  anywhere else in `:app`) was dropped rather than carried forward — it had no call sites.
// ═══════════════════════════════════════════════════════════════════════════════

/** Re-export: shared object, same API (see the shared file's KDoc for design decisions). */
typealias TagDictionary = com.mmg.manahub.core.data.tagging.TagDictionary

/** Re-export: shared data class, same API. */
typealias TagOverride = com.mmg.manahub.core.data.tagging.TagOverride

/** Localize a CardTag for the current locale. Returns null if unknown. */
fun TagDictionary.localize(tag: CardTag, lang: String = currentLang()): String? = when (tag.category) {
    TagCategory.TYPE -> {
        // "basic_land" -> "Basic Land"
        val words = tag.key.split("_")
            .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }

        // Delegate to the type translator (single source of truth).
        CardTypeTranslator.translateTypeLine(words, lang).takeIf { it.isNotBlank() }
    }
    else -> get(tag.key)?.labels?.get(lang)
        ?: get(tag.key)?.labels?.get("en")
}

private fun currentLang(): String = Locale.getDefault().language.lowercase()
