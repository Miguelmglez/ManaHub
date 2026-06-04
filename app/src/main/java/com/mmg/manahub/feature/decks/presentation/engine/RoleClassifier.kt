package com.mmg.manahub.feature.decks.presentation.engine

import com.mmg.manahub.core.domain.model.Card
import com.mmg.manahub.core.domain.model.CardTag
import com.mmg.manahub.core.domain.usecase.decks.BasicLandCalculator
import javax.inject.Inject
import javax.inject.Singleton

// ═══════════════════════════════════════════════════════════════════════════════
//  RoleClassifier
//
//  Maps Card -> Set<DeckRole>. This is the piece that gives the engine "role
//  awareness", something MagicScorer/SynergyScorer did not have.
//
//  Source of truth = the tags the tagging system already produces (locale-safe,
//  key-based). The English oracle text is used ONLY as a safety net when a card
//  arrives without the relevant tags. To improve role coverage, enrich the
//  TagDictionary rather than patching here.
// ═══════════════════════════════════════════════════════════════════════════════

@Singleton
class RoleClassifier @Inject constructor() {

    fun classify(card: Card): Set<DeckRole> {
        if (BasicLandCalculator.isLand(card)) return setOf(DeckRole.LAND)

        val roles = mutableSetOf<DeckRole>()
        val tagKeys = (card.tags + card.userTags).map { it.key }.toSet()
        val oracle = card.oracleText?.lowercase().orEmpty()

        // ── By tag (preferred) ─────────────────────────────────────────────────
        if (CardTag.MANA_ROCK.key in tagKeys || CardTag.MANA_DORK.key in tagKeys || CardTag.RAMP.key in tagKeys)
            roles += DeckRole.RAMP
        if (CardTag.DRAW_ENGINE.key in tagKeys) roles += DeckRole.CARD_ADVANTAGE
        if (CardTag.REMOVAL.key in tagKeys) roles += DeckRole.SPOT_REMOVAL
        if (CardTag.WRATH.key in tagKeys) roles += DeckRole.BOARD_WIPE
        if (CardTag.COUNTERSPELL.key in tagKeys || CardTag.PROTECTION.key in tagKeys || CardTag.STAX.key in tagKeys)
            roles += DeckRole.INTERACTION
        if (CardTag.TUTOR.key in tagKeys) roles += DeckRole.TUTOR
        if (CardTag.WIN_CON.key in tagKeys) roles += DeckRole.PAYOFF

        // ── Oracle (English) fallback when the tag is missing ──────────────────
        if (DeckRole.RAMP !in roles && (oracle.contains("add {") || oracle.contains("search your library for a basic land")))
            roles += DeckRole.RAMP
        if (DeckRole.CARD_ADVANTAGE !in roles && (oracle.contains("draw a card") || oracle.contains("draw two") || oracle.contains("draw three")))
            roles += DeckRole.CARD_ADVANTAGE
        if (DeckRole.BOARD_WIPE !in roles && (oracle.contains("destroy all") || oracle.contains("exile all") || oracle.contains("each player sacrifices")))
            roles += DeckRole.BOARD_WIPE
        if (DeckRole.SPOT_REMOVAL !in roles && DeckRole.BOARD_WIPE !in roles &&
            (oracle.contains("destroy target") || oracle.contains("exile target")))
            roles += DeckRole.SPOT_REMOVAL
        if (DeckRole.INTERACTION !in roles && oracle.contains("counter target")) roles += DeckRole.INTERACTION
        if (DeckRole.TUTOR !in roles && oracle.contains("search your library for a card")) roles += DeckRole.TUTOR

        // ── Strategy/Tribal with no hard functional role -> synergy gear ───────
        val hasStrategyTag = (card.tags + card.userTags).any {
            it.category == com.mmg.manahub.core.domain.model.TagCategory.STRATEGY ||
                it.category == com.mmg.manahub.core.domain.model.TagCategory.TRIBAL
        }
        if (roles.none { it.isFunctional } && hasStrategyTag) roles += DeckRole.SYNERGY

        // ── Creature with a relevant body and no other role -> threat ──────────
        if (roles.none { it.isFunctional } && card.typeLine.contains("Creature", ignoreCase = true) && powerToInt(card.power) >= 3)
            roles += DeckRole.THREAT

        if (roles.isEmpty()) roles += DeckRole.FILLER
        return roles
    }

    private fun powerToInt(power: String?): Int = power?.toIntOrNull() ?: 0
}
