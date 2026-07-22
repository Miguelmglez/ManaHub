package com.mmg.manahub.core.domain.repository

import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.feature.decks.domain.model.ComboResult

/**
 * Contract for the Commander Spellbook `find-my-combos` combo-detection source (Deck Engine
 * Unification plan D7, Phase 4.3) — the synergy browser's "Combos" tab.
 *
 * Commander Spellbook is an unofficial-adjacent third party (`backend.commanderspellbook.com`,
 * documented Swagger at `/schema/`, no API key). Every implementation MUST treat every call as
 * fallible and degrade to a cached-or-empty [ComboResult] on failure — this tab must never block
 * or crash the rest of the synergy browser (Strategies/search tabs stay fully functional offline).
 *
 * Layering mirrors [CommunityAggregateRepository]: Room cache (~7-day TTL) -> API -> stale cache
 * -> empty. Unlike [CommunityAggregateRepository] there is no reduced-sample fallback (there is
 * no meaningful client-side substitute for Spellbook's combo database).
 */
interface CommanderSpellbookRepository {

    /**
     * Finds combos the given card set already completes ([ComboResult.complete]) and combos it
     * is exactly one card away from completing ([ComboResult.almostThere]).
     *
     * @param cardNames every non-commander card name to check (deduplicated, capped by the
     *   caller to the API's 600-card limit — see
     *   [com.mmg.manahub.feature.decks.domain.usecase.FindCombosUseCase]).
     * @param commanderNames optional commander name(s), sent separately (Spellbook distinguishes
     *   commander-zone cards from the rest of the list for identity/`mustBeCommander` purposes).
     */
    suspend fun findCombos(
        cardNames: List<String>,
        commanderNames: List<String> = emptyList(),
    ): DataResult<ComboResult>
}
