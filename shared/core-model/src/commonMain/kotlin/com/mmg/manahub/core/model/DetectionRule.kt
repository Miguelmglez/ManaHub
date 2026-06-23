package com.mmg.manahub.core.model

/**
 * A structured oracle-text matching rule used by the tag analysis engine.
 *
 * All fields default to empty (match-everything) so callers only specify
 * the conditions they care about. [confidence] overrides the parent entry's
 * base confidence when non-null.
 *
 * Extracted from the JVM-only `TagDictionary` so it can live in `commonMain`
 * and be consumed by the shared tagging analyzers on all KMP targets.
 */
data class DetectionRule(
    val allOf:          List<String> = emptyList(),
    val anyOf:          List<String> = emptyList(),
    val noneOf:         List<String> = emptyList(),
    val typeLineAnyOf:  List<String> = emptyList(),
    val typeLineNoneOf: List<String> = emptyList(),
    val confidence:     Float?       = null,
)
