package com.mmg.manahub.feature.decks.domain.template

import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.TagCategory
import com.mmg.manahub.feature.decks.domain.engine.DeckProfile
import com.mmg.manahub.feature.decks.domain.engine.DeckRole
import com.mmg.manahub.feature.decks.domain.engine.RoleClassifier
import com.mmg.manahub.feature.decks.domain.engine.TribeDeriver

/**
 * Single source of truth for the category a card/suggestion belongs to (Deck Builder v2 plan,
 * §3.1). Stable, resource-free (KMP directive: `commonMain` never touches Android string resources
 * or Compose Multiplatform `Res` directly — [SuggestionCategory.displayLabel] is a plain English
 * string, mirroring the existing `CardTag.displayLabel` precedent, not a `strings.xml`/`Res`
 * lookup). A presentation-layer localization pass (mirroring
 * `app/.../presentation/components/DeckDoctorStrings.kt`'s "engine stays string-free" pattern) is
 * Phase 3/4 work, out of scope here — see `project_deck_builder_v2_phase0_1_2` memory for why this
 * layer intentionally stops at a plain-English label instead of wiring `strings.xml` today.
 *
 * Resolution order (plan §3.1, row 2), first match wins:
 *  1. the EDHREC/Archidekt aggregate `category` string, when the card came from a
 *     [com.mmg.manahub.core.model.CommunityAggregate] entry (already human-readable, e.g.
 *     "Removal"/"Ramp"/"Creatures").
 *  2. a [DeckRole] -> category mapping for the roles that have one clean category name
 *     (SPOT_REMOVAL->Removal, RAMP->Ramp, ...). PAYOFF/SYNERGY/THREAT/FILLER have no single clean
 *     category name and fall through to the next steps.
 *  3. the dominant derived `tribe:<x>` key on the CARD itself (via [TribeDeriver]), preferring
 *     whichever of the card's own tribes also has the highest weight in the deck's own
 *     [DeckProfile.tagFingerprint] when a profile is supplied (so grouping aligns with what the
 *     deck actually cares about), else the alphabetically-first tribe key for a deterministic
 *     card-only fallback.
 *  4. the card's first STRATEGY-category [com.mmg.manahub.core.model.CardTag] (Tokens, Sacrifice,
 *     Lifegain, ...).
 *  5. a type-line/oracle heuristic ("Mana rock" = artifact + a word-anchored `Add {` oracle clause,
 *     mirroring the conservative `\bAdd\b` pattern already used by
 *     [com.mmg.manahub.feature.decks.domain.engine.ManaBaseAnalyzer] so this never invents an
 *     off-colour/false-positive category from prose like "Additional cost"). NOTE: a REALISTIC mana
 *     rock's oracle text is already caught by step 2's RAMP role mapping (RoleClassifier's own
 *     `add (?:\{|one mana|...)` oracle safety net is broader), so this heuristic mostly only fires
 *     for the rare artifact whose "add" phrasing RoleClassifier's narrower RAMP pattern misses —
 *     it is a genuine but narrow safety net, not dead code.
 *  6. [SuggestionCategory.OTHER].
 *
 * Reused by the v2 builder ([BuildDeckFromTemplateUseCase]) and, per the plan, intended for reuse
 * later by Motor A/B grouping and Discoveries v2 (Phase 4/5, out of scope for this run) — the public
 * API therefore takes only a [Card] plus OPTIONAL aggregate/profile context, no ViewModel/UI coupling.
 */
object SuggestionCategoryResolver {

    private val roleClassifier = RoleClassifier()

    /**
     * @param aggregateCategory the raw category string from a [com.mmg.manahub.core.model
     *   .AggregateCardEntry], when this card came from a community aggregate. Null/blank skips
     *   straight to the role-based resolution.
     * @param profile the deck's own profile, used ONLY to pick which of the card's own tribes is
     *   "dominant" for the deck (step 3). Optional — a null profile still resolves a tribe category
     *   from the card alone, just without deck-context preference.
     */
    fun resolve(
        card: Card,
        aggregateCategory: String? = null,
        profile: DeckProfile? = null,
    ): SuggestionCategory {
        aggregateCategory?.trim()?.takeIf { it.isNotEmpty() }?.let { return fromAggregateLabel(it) }
        roleCategory(card)?.let { return it }
        tribeCategory(card, profile)?.let { return it }
        strategyCategory(card)?.let { return it }
        typeLineHeuristicCategory(card)?.let { return it }
        return SuggestionCategory.OTHER
    }

    /**
     * Resolves a category from a raw aggregate category STRING alone (no [Card] available yet) --
     * used by [DeckTemplateResolver] when grouping a [com.mmg.manahub.core.model.AggregateCardEntry]
     * list into [TemplateCategory] buckets, before any candidate card is resolved. Public because it
     * is a legitimate standalone entry point, not just an internal step of [resolve].
     */
    fun resolveAggregateOnly(rawCategory: String): SuggestionCategory =
        rawCategory.trim().takeIf { it.isNotEmpty() }?.let { fromAggregateLabel(it) } ?: SuggestionCategory.OTHER

    private fun fromAggregateLabel(raw: String): SuggestionCategory {
        val label = raw.replaceFirstChar { it.uppercase() }
        val id = raw.trim().lowercase().map { if (it.isLetterOrDigit()) it else '_' }.joinToString("")
        return SuggestionCategory(id, label)
    }

    private fun roleCategory(card: Card): SuggestionCategory? {
        val roles = roleClassifier.classify(card)
        val topRole = roles.entries.maxByOrNull { it.value }?.key ?: return null
        return ROLE_CATEGORIES[topRole]
    }

    private fun tribeCategory(card: Card, profile: DeckProfile?): SuggestionCategory? {
        val cardTribes = TribeDeriver.tribeKeys(card)
        if (cardTribes.isEmpty()) return null
        val chosen = profile?.tagFingerprint
            ?.filterKeys { it in cardTribes }
            ?.maxByOrNull { it.value }
            ?.key
            ?: cardTribes.min()
        val tribeWord = chosen.removePrefix(TribeDeriver.TRIBE_PREFIX)
        if (tribeWord.isEmpty()) return null
        val label = pluralizeTribeWord(tribeWord).replaceFirstChar { it.uppercase() }
        return SuggestionCategory("tribe_$tribeWord", label)
    }

    private fun strategyCategory(card: Card): SuggestionCategory? {
        val tag = (card.tags + card.userTags).firstOrNull { it.category == TagCategory.STRATEGY }
            ?: return null
        return SuggestionCategory(tag.key, tag.displayLabel)
    }

    private fun typeLineHeuristicCategory(card: Card): SuggestionCategory? {
        if (!card.typeLine.contains("Artifact", ignoreCase = true)) return null
        val oracle = card.oracleText?.lowercase() ?: return null
        return if (ADD_MANA_CLAUSE.containsMatchIn(oracle)) SuggestionCategory.MANA_ROCKS else null
    }

    /**
     * Best-effort English pluralization for a singular tribe subtype word ("elf" -> "elves",
     * "goblin" -> "goblins", "zombie" -> "zombies"). Display-only — never persisted, never fed back
     * into a query or a tag key.
     */
    private fun pluralizeTribeWord(word: String): String = when {
        word.endsWith("y") && word.length > 1 && word[word.length - 2] !in "aeiou" ->
            word.dropLast(1) + "ies"
        word.endsWith("fe") -> word.dropLast(2) + "ves"
        word.endsWith("f") -> word.dropLast(1) + "ves"
        word.endsWith("s") || word.endsWith("x") || word.endsWith("z") ||
            word.endsWith("ch") || word.endsWith("sh") -> word + "es"
        else -> word + "s"
    }

    /** Word-anchored so "Additional cost" never matches (mirrors `ManaBaseAnalyzer`'s `\bAdd\b`). */
    private val ADD_MANA_CLAUSE = Regex("\\badd\\b")

    private val ROLE_CATEGORIES: Map<DeckRole, SuggestionCategory> = mapOf(
        DeckRole.RAMP to SuggestionCategory("ramp", "Ramp"),
        DeckRole.CARD_ADVANTAGE to SuggestionCategory("card_draw", "Card Draw"),
        DeckRole.SPOT_REMOVAL to SuggestionCategory("removal", "Removal"),
        DeckRole.BOARD_WIPE to SuggestionCategory("board_wipes", "Board Wipes"),
        DeckRole.INTERACTION to SuggestionCategory("interaction", "Interaction"),
        DeckRole.TUTOR to SuggestionCategory("tutors", "Tutors"),
        DeckRole.LAND to SuggestionCategory.LANDS,
    )
}

/** Stable id (grouping/keying) + a plain-English display label for a suggestion category. */
data class SuggestionCategory(val id: String, val displayLabel: String) {
    companion object {
        val OTHER = SuggestionCategory("other", "Other")
        val LANDS = SuggestionCategory("lands", "Lands")
        val MANA_ROCKS = SuggestionCategory("mana_rocks", "Mana Rocks")
    }
}
