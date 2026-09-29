package com.mmg.manahub.feature.decks.domain.engine
// COMMENTS_REVIEWED: 2026-09-16

// Single source for role<->CardTag membership; see ADR-009 for per-key citations.
object CategoryVocabulary {

    // Counter production no longer implies a payoff; landfall keeps its keyword equivalence.
    private val WIDENED_MEMBERSHIP: Map<RoleKey, Set<String>> = mapOf(
        "landfall_payoff" to setOf("landfall_payoff", "landfall"),
    )

    // No matching CardTag exists for these (see ADR-009); legacy LEGACY_ROLE_MAP roles are excluded
    // from this table entirely (RoleClassifier's own oracle fallback stays invisible to Browse).
    private val NO_TAG_EQUIVALENT: Set<RoleKey> = setOf(
        "removal_spot", "removal_mass", "finisher", "equipment_or_aura", "tribe_members",
    )

    fun cardTagKeysFor(roleKey: RoleKey): Set<String> = when {
        roleKey in NO_TAG_EQUIVALENT -> emptySet()
        roleKey in WIDENED_MEMBERSHIP -> WIDENED_MEMBERSHIP.getValue(roleKey)
        else -> setOf(roleKey)
    }
}
