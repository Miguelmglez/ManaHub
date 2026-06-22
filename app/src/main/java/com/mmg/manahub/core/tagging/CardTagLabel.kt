package com.mmg.manahub.core.tagging

import com.mmg.manahub.core.model.CardTag

/**
 * Resolves the localized display label for a [CardTag].
 *
 * Label resolution lives here (in the `:app` tagging engine) rather than on the
 * pure [CardTag] model so the model can stay platform-agnostic and live in
 * `:shared:core-model` (`commonMain`). [TagDictionary.localize] depends on
 * `java.util.Locale` and [CardTypeTranslator], both JVM-only, which is why the
 * label was extracted out of the model.
 *
 * Falls back to a humanized form of the canonical key when the dictionary has no
 * entry for the tag.
 */
fun CardTag.label(): String =
    TagDictionary.localize(this) ?: key
        .replace('_', ' ')
        .replaceFirstChar { it.uppercase() }
