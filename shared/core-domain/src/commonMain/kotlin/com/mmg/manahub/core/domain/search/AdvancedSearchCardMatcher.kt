package com.mmg.manahub.core.domain.search

import com.mmg.manahub.core.model.AdvancedSearchQuery
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.CardFunctionOption
import com.mmg.manahub.core.model.CollectionSource
import com.mmg.manahub.core.model.ColorMatchMode
import com.mmg.manahub.core.model.ComparisonOperator
import com.mmg.manahub.core.model.SearchCriterion

/**
 * Local, in-memory evaluator for an [AdvancedSearchQuery] against a single [Card] — the offline
 * counterpart of `BuildScryfallQueryUseCase` (which renders the same query as Scryfall syntax for
 * the remote search).
 *
 * Extracted from `CollectionViewModel.matchesCriterion` so Deck Studio's Analysis-tab
 * "Browse for &lt;Category&gt;" flow can filter the Collection tab with the SAME structured query
 * it sends to Scryfall for the All Cards tab.
 *
 * [SearchCriterion.CollectionStatus] is collection-local and has no equivalent on a bare [Card]:
 * the caller passes that context in explicitly as [isWishlisted] / [isForTrade], defaulted so a
 * plain-[Card] caller (Deck Studio) needs none.
 */
object AdvancedSearchCardMatcher {

    /** The color picker's colorless option; stored as an EMPTY color list on a [Card]. */
    private const val COLORLESS = "C"

    /** Scryfall oracle text embeds parenthesized reminder text, a false-positive source for
     *  substring matching — stripped before every term check, as `StrategyAnalyzer` does. */
    private val reminderTextRegex = Regex("""\([^)]*\)""")

    /** True when [card] satisfies EVERY criterion of [query] (empty query matches everything). */
    fun matches(
        card: Card,
        query: AdvancedSearchQuery,
        lenient: Boolean = false,
        isWishlisted: Boolean = false,
        isForTrade: Boolean = false,
    ): Boolean = query.criteria.all { criterion ->
        matchesCriterion(card, criterion, lenient, isWishlisted, isForTrade)
    }

    /**
     * @param lenient when true, a criterion that cannot be evaluated against locally cached data
     *   is SKIPPED (treated as matching) instead of failing the card. Today that is
     *   [SearchCriterion.CardFunction] values with no [CardFunctionOption.collectionTagKeys]
     *   mapping (a Scryfall-only facet), a [SearchCriterion.OracleTerms] oracle term against a card
     *   with no cached oracle text, and a [SearchCriterion.ManaProduction] colour/threshold against
     *   a card with no cached `producedMana`. Deck Studio's Collection tab needs this so a
     *   Scryfall-only section (curve / mana / legality) never renders a falsely-empty tab; the
     *   Collection screen's own advanced search stays strict, where an unmatchable facet legitimately
     *   means "no local card qualifies".
     */
    fun matchesCriterion(
        card: Card,
        criterion: SearchCriterion,
        lenient: Boolean = false,
        isWishlisted: Boolean = false,
        isForTrade: Boolean = false,
    ): Boolean = when (criterion) {
        is SearchCriterion.Name ->
            if (criterion.exact) card.name.equals(criterion.value, ignoreCase = true)
            else card.name.contains(criterion.value, ignoreCase = true)

        is SearchCriterion.OracleText ->
            card.oracleText?.contains(criterion.value, ignoreCase = true) == true

        is SearchCriterion.CardType -> {
            val check: (String) -> Boolean = { type -> card.typeLine.contains(type, ignoreCase = true) }
            // `exclude` renders as `-t:x` remotely, so it must negate locally too (Deck Analysis
            // curve sections are `mv=N -t:land`); the pre-extraction evaluator ignored the flag and
            // matched exactly the cards it was meant to drop.
            if (criterion.exclude) criterion.types.none(check)
            else if (criterion.matchAll) criterion.types.all(check)
            else criterion.types.any(check)
        }

        is SearchCriterion.CardFunction -> {
            val cardTagKeys = card.tags.map { it.key }.toSet() + card.userTags.map { it.key }
            val check: (String) -> Boolean = { value ->
                val tagKeys = CardFunctionOption.allFunctions
                    .find { it.scryfallValue == value }?.collectionTagKeys ?: emptySet()
                // A function with no local tag equivalent is Scryfall-only: strictly it matches
                // nothing, leniently it is not a local constraint at all.
                if (tagKeys.isEmpty()) lenient else tagKeys.any { it in cardTagKeys }
            }
            if (criterion.matchAll) criterion.functions.all(check)
            else criterion.functions.any(check)
        }

        is SearchCriterion.Colors -> matchesColorSet(card.colors, criterion.colors, criterion.mode)

        is SearchCriterion.ColorIdentity ->
            matchesColorSet(card.colorIdentity, criterion.colors, criterion.mode)

        is SearchCriterion.Rarity -> compareRarity(card.rarity, criterion.rarity)

        is SearchCriterion.ManaCost -> compareInt(card.cmc.toInt(), criterion.value, criterion.operator)

        is SearchCriterion.Price -> {
            val price = if (criterion.currency == "eur") card.priceEur else card.priceUsd
            price != null && compareDouble(price, criterion.value, criterion.operator)
        }

        is SearchCriterion.CardSet -> criterion.setCodes.contains(card.setCode.lowercase())

        is SearchCriterion.Power -> {
            val power = card.power?.toIntOrNull()
            power != null && compareInt(power, criterion.value, criterion.operator)
        }

        is SearchCriterion.Toughness -> {
            val toughness = card.toughness?.toIntOrNull()
            toughness != null && compareInt(toughness, criterion.value, criterion.operator)
        }

        is SearchCriterion.Format -> matchesFormat(card, criterion.format, criterion.legal)

        // The three sources are mutually exclusive; COLLECTION is not a constraint on a Card,
        // the caller has already chosen which list it is filtering.
        is SearchCriterion.CollectionStatus -> when (criterion.source) {
            CollectionSource.COLLECTION -> true
            CollectionSource.WISHLIST -> isWishlisted
            CollectionSource.FOR_TRADE -> isForTrade
        }

        is SearchCriterion.HasTag ->
            criterion.keys.any { key ->
                card.tags.any { it.key == key } || card.userTags.any { it.key == key }
            }

        is SearchCriterion.OracleTerms -> matchesOracleTerms(card, criterion, lenient)

        is SearchCriterion.ManaProduction -> matchesManaProduction(card, criterion, lenient)

        // Loyalty, Language, Artist and FlavorText have no locally cached field to evaluate, so
        // they are not a local constraint in either mode. Anything ADDED here silently matches
        // every card with no compile error — see CollectionViewModelTest's `else -> true` guard.
        else -> true
    }

    /**
     * Local mirror of [SearchCriterion.OracleTerms]'s Scryfall rendering
     * (`oracle:"a" (oracle:"b" or oracle:"c") t:d` — see `BuildScryfallQueryUseCase`), so Deck
     * Analysis's dictionary-translated role sections and every `fingerprint:` theme section filter
     * the Collection tab instead of showing the whole collection.
     *
     * Oracle text is normalized the same way the tagging engine's `StrategyAnalyzer` normalizes it
     * before running the very [com.mmg.manahub.core.model.DetectionRule]s these terms come from —
     * lowercase, parenthesized reminder text stripped, the card's own (face) names replaced with
     * `~`. That normalization is duplicated rather than shared because `StrategyAnalyzer` lives in
     * `:shared:core-data`, which depends on this module, not the other way round.
     */
    private fun matchesOracleTerms(card: Card, criterion: SearchCriterion.OracleTerms, lenient: Boolean): Boolean {
        // The `t:` half is evaluable whatever the oracle text says, so it is applied first — a
        // type-line-only criterion never degrades to "cannot evaluate" (the trap `core/tagging
        // /CLAUDE.md` documents for StrategyAnalyzer's own blank-oracle short-circuit).
        if (criterion.typeLineAnyOf.isNotEmpty() &&
            criterion.typeLineAnyOf.none { card.typeLine.contains(it, ignoreCase = true) }
        ) return false

        if (criterion.allOf.isEmpty() && criterion.anyOfGroups.isEmpty()) return true

        // Cannot evaluate: an absent oracle text is "unknown" (an uncached row, or a multi-face
        // card whose text lives on `cardFaces`), not "matches nothing" — strictly it fails the
        // card, leniently it is skipped, exactly like the CardFunction empty-mapping branch.
        val text = normalizedOracleText(card)
        if (text.isBlank()) return lenient

        if (criterion.allOf.any { !text.contains(it.lowercase()) }) return false
        // An empty group is no constraint — `BuildScryfallQueryUseCase` renders no clause for it.
        return criterion.anyOfGroups.all { group ->
            group.isEmpty() || group.any { text.contains(it.lowercase()) }
        }
    }

    /**
     * Local mirror of [SearchCriterion.ManaProduction]'s Scryfall rendering
     * (`t:land produces:w produces>=2`), backing Deck Analysis's per-color Mana Base sections and
     * the general `mana_fix` role. [com.mmg.manahub.core.model.Card.producedMana] is a compact
     * WUBRG-subset string (`"WU"`), never JSON.
     *
     * One accepted divergence from Scryfall: `produces>=N` counts colorless `C` remotely, but
     * `producedMana` keeps only WUBRG letters (`CardDtoMapper.toCompactWubrg`), so a
     * colorless-only producer counts as 0 distinct colors locally.
     */
    private fun matchesManaProduction(card: Card, criterion: SearchCriterion.ManaProduction, lenient: Boolean): Boolean {
        // `t:land` is a separate, always-evaluable clause — applied first so a lenient Mana Base
        // section still narrows to lands even when production data is missing.
        if (criterion.requireLand && !card.typeLine.contains("land", ignoreCase = true)) return false

        if (criterion.colors.isEmpty() && criterion.minDistinctColors == null) return true

        // Cannot evaluate: "" means BOTH "produces no mana" and "row never re-fetched since
        // producedMana was added (Room v42)" — strictly it fails the card, leniently it is skipped,
        // mirroring the CardFunction empty-mapping branch rather than guessing which one it is.
        val produced = card.producedMana.uppercase()
        if (produced.isBlank()) return lenient

        // Each `produces:X` renders as its own clause, so multiple colors are AND'd, not OR'd.
        if (criterion.colors.any { !produced.contains(it.uppercase()) }) return false
        return criterion.minDistinctColors?.let { produced.toSet().size >= it } ?: true
    }

    /** Lowercase -> strip parenthesized reminder text -> replace each face name with `~`. */
    private fun normalizedOracleText(card: Card): String {
        val raw = card.oracleText?.takeIf { it.isNotBlank() } ?: return ""
        var text = reminderTextRegex.replace(raw.lowercase(), "")
        card.name.split(" // ")
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .sortedByDescending { it.length } // replace the longest face first
            .forEach { name -> text = text.replace(name, "~") }
        return text
    }


    /**
     * The local half of [ColorMatchMode], and it MUST stay truth-table-identical to the half
     * `BuildScryfallQueryUseCase` renders — the two disagreeing is exactly the defect the mode enum
     * was introduced to kill. Each branch below names the Scryfall clause it mirrors.
     *
     * [AT_MOST][ColorMatchMode.AT_MOST] admits the empty set: a colorless card fits inside every
     * identity, which is why artifacts and basic lands are legal in any Commander deck.
     *
     * `"C"` is the picker's COLORLESS option, never a sixth color — the model stores colorless as
     * an EMPTY [Card.colors] / [Card.colorIdentity], so it is stripped here and reintroduced as an
     * "is the card colorless" test, exactly where the renderer emits its `=c` clause.
     */
    private fun matchesColorSet(cardColors: List<String>, criterionColors: Set<String>, mode: ColorMatchMode): Boolean {
        // Mirrors the renderer's `return null` for an empty selection: not a constraint at all.
        if (criterionColors.isEmpty()) return true
        val card = cardColors.map { it.uppercase() }.toSet()
        val wanted = criterionColors.map { it.uppercase() }.toSet()
        val letters = wanted - COLORLESS
        val wantsColorless = COLORLESS in wanted
        return when (mode) {
            // `(c>=w or c>=u)` / `(… or c=c)`
            ColorMatchMode.ANY_OF ->
                letters.any { it in card } || (wantsColorless && card.isEmpty())
            // `c=c` when colorless was the only pick, else `c<=wu` with C dropped
            ColorMatchMode.AT_MOST ->
                if (letters.isEmpty()) card.isEmpty() else letters.containsAll(card)
            // `c=c` / `c=wu`
            ColorMatchMode.EXACTLY ->
                if (letters.isEmpty()) card.isEmpty() else card == letters
            // `c=c` / `c>=wu`
            ColorMatchMode.AT_LEAST ->
                if (letters.isEmpty()) card.isEmpty() else card.containsAll(letters)
        }
    }

    private fun compareRarity(cardRarity: String, targetRarity: List<String>): Boolean =
        targetRarity.isEmpty() || targetRarity.contains(cardRarity.lowercase())

    private fun matchesFormat(card: Card, formats: List<String>, legal: Boolean): Boolean =
        formats.any { format ->
            val legality = when (format) {
                "standard" -> card.legalityStandard
                "pioneer" -> card.legalityPioneer
                "modern" -> card.legalityModern
                "commander" -> card.legalityCommander
                else -> return@any false
            }
            if (legal) legality == "legal" else legality != "legal"
        }

    private fun compareInt(cardVal: Int, target: Int, op: ComparisonOperator): Boolean = when (op) {
        ComparisonOperator.EQUAL -> cardVal == target
        ComparisonOperator.LESS -> cardVal < target
        ComparisonOperator.LESS_OR_EQUAL -> cardVal <= target
        ComparisonOperator.GREATER -> cardVal > target
        ComparisonOperator.GREATER_OR_EQUAL -> cardVal >= target
        ComparisonOperator.NOT_EQUAL -> cardVal != target
    }

    private fun compareDouble(cardVal: Double, target: Double, op: ComparisonOperator): Boolean = when (op) {
        ComparisonOperator.EQUAL -> cardVal == target
        ComparisonOperator.LESS -> cardVal < target
        ComparisonOperator.LESS_OR_EQUAL -> cardVal <= target
        ComparisonOperator.GREATER -> cardVal > target
        ComparisonOperator.GREATER_OR_EQUAL -> cardVal >= target
        ComparisonOperator.NOT_EQUAL -> cardVal != target
    }
}
