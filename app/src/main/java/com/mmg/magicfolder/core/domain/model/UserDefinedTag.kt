package com.mmg.magicfolder.core.domain.model

/**
 * A tag created manually by the user that is persisted globally across the app.
 *
 * - [key]         Stable lowercase snake_case identifier, e.g. "budget_staple"
 * - [label]       Display name as entered by the user, e.g. "Budget Staple"
 * - [categoryKey] Either a [TagCategory.name] ("ARCHETYPE", "STRATEGY", …) or a
 *                 user-created category string (e.g. "Mis favoritas")
 */
data class UserDefinedTag(
    val key:         String,
    val label:       String,
    val categoryKey: String,
)
