package com.mmg.manahub.core.model

/**
 * A single tag-dictionary entry: labels by language plus English detection rules.
 *
 * Extracted from the JVM-only `TagDictionary` so it can live in `commonMain`
 * and be consumed by the shared `StrategyAnalyzer` on all KMP targets.
 * The JVM-side `TagDictionary.Entry` is replaced by this type.
 */
data class TagDictionaryEntry(
    val key:            String,
    val category:       TagCategory,
    val labels:         Map<String, String>,
    val rules:          List<DetectionRule>,
    val baseConfidence: Float = 0.85f,
)
