package com.mmg.manahub.feature.decks.domain.template

import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.domain.repository.CommunityAggregateRepository
import com.mmg.manahub.core.model.AggregateCardEntry
import com.mmg.manahub.core.model.ArchidektFormat
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.CommunityAggregate
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeData
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeFormat
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeId
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeSkeletonResolver
import com.mmg.manahub.feature.decks.domain.engine.DeckSkeletons
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.ThemeId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Deck Builder v2 (plan §3.1 row 3, §3.3 step 3). Builds a [DeckTemplate] from:
 *  - **Commander**: [CommunityAggregateRepository.getCommanderAggregate] — categories/counts from
 *    [CommunityAggregate.Commander.avgTypeDistribution] + `manaCurve`, `landTarget =
 *    avgTypeDistribution.land`.
 *  - **Casual/60**: [CommunityAggregateRepository.getSixtyAggregate] when materialized (bounded
 *    re-poll with backoff while [CommunityAggregate.Sixty.Building], then synthetic — never an
 *    infinite/blocking poll), else synthetic.
 *  - **Fallback** (offline / no aggregate data / obscure commander / persistent `Building`):
 *    synthetic template from [ArchetypeSkeletonResolver] + [com.mmg.manahub.feature.decks.domain
 *    .engine.SeedStrategy] + [DeckSkeletons] (categories from the resolved skeleton's `RoleKey`
 *    slots). A synthetic template carries no concrete card names -- see [BuildDeckFromTemplateUseCase]
 *    for how the fill stage still works against it (candidates are selected via
 *    [SuggestionCategoryResolver] matching the deck's OWN cards to a category, not via
 *    [TemplateCardRef] name lookups; a synthetic category's `cards` list is always empty, so its
 *    community-suggestion gap is honestly empty too -- consistent with the plan's risk table for
 *    weak/missing aggregates).
 *
 * Every result carries [DeckTemplate.source] so the UI can say honestly which engine built it.
 */
class DeckTemplateResolver(
    private val communityAggregateRepository: CommunityAggregateRepository,
    /** Optional (nullable-defaulted, mirrors [BuildDeckFromTemplateUseCase]'s own [CrashReporter]
     * param so no existing test call site breaks). Every fall-through to [syntheticTemplate] is the
     * exact "community aggregate missing/poor" degraded path the plan's risk table (§5) flags for
     * obscure commanders -- without this, that fallback was 100% silent. */
    private val crashReporter: CrashReporter? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    suspend fun resolve(spec: DeckWizardSpec): DeckTemplate = withContext(ioDispatcher) {
        when (spec.format) {
            DeckFormat.COMMANDER -> resolveCommander(spec)
            else -> resolveSixty(spec)
        }
    }

    // ── Commander ───────────────────────────────────────────────────────────────

    private suspend fun resolveCommander(spec: DeckWizardSpec): DeckTemplate {
        val commander = spec.commander ?: run {
            logSyntheticFallback("commander", "no_commander")
            return syntheticTemplate(spec)
        }
        val result = runCatching { communityAggregateRepository.getCommanderAggregate(commander.name) }
            .getOrNull()
        val aggregate = (result as? DataResult.Success)?.data ?: run {
            logSyntheticFallback("commander", if (result == null) "fetch_failed" else "empty_or_error")
            return syntheticTemplate(spec)
        }
        return fromCommanderAggregate(aggregate, spec)
    }

    /** Breadcrumb-only (never [CrashReporter.recordException]) -- falling back to [syntheticTemplate]
     * is an expected, by-design degraded path (plan §5 risk table calls it "mitigated"), not a bug.
     * We want FREQUENCY visibility into how often community data is missing/poor, not crash noise. */
    private fun logSyntheticFallback(format: String, reason: String) {
        crashReporter?.log("deck_template_resolver_fallback_synthetic")
        crashReporter?.setCustomKey("deck_template_resolver_format", format)
        crashReporter?.setCustomKey("deck_template_resolver_reason", reason)
    }

    private fun fromCommanderAggregate(aggregate: CommunityAggregate.Commander, spec: DeckWizardSpec): DeckTemplate {
        val categories = groupCardsByCategory(aggregate.cards)
        val manaCurveTarget = normalizeCurve(aggregate.manaCurve)
        val colorIdentity = spec.commander?.colorIdentity?.toManaColorSet() ?: spec.colorIdentity
        val themes = matchThemeTags(aggregate.themeTags.map { it.name }, spec.themeHint)
        return DeckTemplate(
            source = TemplateSource.COMMUNITY,
            categories = categories,
            landTarget = aggregate.avgTypeDistribution.land.coerceAtLeast(0),
            manaCurveTarget = manaCurveTarget,
            colorIdentity = colorIdentity,
            archetypeInfo = DeckTemplateArchetypeInfo(archetype = ArchetypeId.GENERIC, themes = themes),
            gamePlan = spec.strategyHint?.description,
        )
    }

    // ── Casual / 60-card ────────────────────────────────────────────────────────

    /**
     * D9: synthetic is PRIMARY for 60-card constructed. Archidekt's aggregate only ENRICHES the
     * synthetic template's card pool when it materializes within a bounded poll -- it never blocks
     * or replaces the synthetic skeleton wholesale (the synthetic skeleton is archetype-aware and
     * strategy-driven; the aggregate only contributes concrete card names/weights).
     */
    private suspend fun resolveSixty(spec: DeckWizardSpec): DeckTemplate {
        val synthetic = syntheticTemplate(spec)
        val signature = signatureCards(spec)
        if (signature.isEmpty()) {
            logSyntheticFallback("sixty", "no_signature_cards")
            return synthetic
        }

        var materialized: CommunityAggregate.Sixty.Materialized? = null
        var lastWasBuilding = false
        for (attempt in 0 until SIXTY_POLL_ATTEMPTS) {
            val result = runCatching {
                communityAggregateRepository.getSixtyAggregate(signature, ArchidektFormat.CUSTOM.apiId)
            }.getOrNull()
            when (val data = (result as? DataResult.Success)?.data) {
                is CommunityAggregate.Sixty.Materialized -> {
                    materialized = data
                    lastWasBuilding = false
                    break // found it -- stop polling immediately, no wasted requests.
                }
                is CommunityAggregate.Sixty.Building -> {
                    lastWasBuilding = true
                    if (attempt < SIXTY_POLL_ATTEMPTS - 1) delay(SIXTY_POLL_BACKOFF_MS * (attempt + 1))
                }
                else -> {
                    lastWasBuilding = false
                    break // null / error -- stop polling, fall through to synthetic.
                }
            }
        }
        val aggregate = materialized ?: run {
            logSyntheticFallback("sixty", if (lastWasBuilding) "building_timeout" else "fetch_failed_or_empty")
            return synthetic
        }
        return enrichWithSixtyAggregate(synthetic, aggregate)
    }

    /**
     * Opportunistic enrichment: attaches the aggregate's cards onto the synthetic categories,
     * WITHOUT changing the synthetic skeleton's target counts, land target or curve (those stay
     * archetype/strategy-driven per D9).
     *
     * The synthetic template's category ids are [com.mmg.manahub.feature.decks.domain.engine
     * .RoleKey] archetype-vocabulary strings ("removal_spot", "ramp", "finisher", ...) -- a
     * DIFFERENT vocabulary from the aggregate's own free-form category labels ("Removal", "Card
     * Draw", ...) grouped via [SuggestionCategoryResolver.resolveAggregateOnly]. Matching those two
     * vocabularies by exact id would silently merge nothing, so this uses a small, explicit
     * allowlist ([ROLE_KEY_TO_AGGREGATE_LABELS]) instead of guessing. A `RoleKey` with no allowlist
     * entry simply stays without enrichment (its `cards` list is left empty) -- same honest-gap
     * behavior as an obscure/missing aggregate.
     */
    private fun enrichWithSixtyAggregate(
        synthetic: DeckTemplate,
        aggregate: CommunityAggregate.Sixty.Materialized,
    ): DeckTemplate {
        if (aggregate.cards.isEmpty()) return synthetic
        val byAggregateCategoryLabel = aggregate.cards.groupBy { it.category.trim().lowercase() }
        val merged = synthetic.categories.map { category ->
            val labels = ROLE_KEY_TO_AGGREGATE_LABELS[category.id] ?: return@map category
            val matchedCards = labels.flatMap { byAggregateCategoryLabel[it].orEmpty() }
            if (matchedCards.isEmpty()) return@map category
            val refs = matchedCards
                .map { entry ->
                    TemplateCardRef(
                        name = entry.name,
                        weight = (entry.inclusionPct * WEIGHT_INCLUSION + entry.synergy * WEIGHT_SYNERGY).coerceIn(0f, 1f),
                        category = SuggestionCategory(category.id, category.label),
                    )
                }
                .sortedWith(compareByDescending<TemplateCardRef> { it.weight }.thenBy { it.name })
            category.copy(cards = refs)
        }
        return synthetic.copy(categories = merged, source = TemplateSource.SYNTHETIC)
    }

    /** Mirrors `DeckDoctorOrchestrator.signatureCards`: the 2-3 LEAST popular (highest EDHREC rank
     * number = lowest global frequency) named cards, so the Worker's sample matches a genuinely
     * distinctive shell rather than generic staples. Falls back to the first seed names when no
     * seed carries a resolved `edhrecRank`. */
    private fun signatureCards(spec: DeckWizardSpec): List<String> {
        val ranked = spec.seeds.filter { it.edhrecRank != null }
        val chosen = if (ranked.isNotEmpty()) {
            ranked.sortedByDescending { it.edhrecRank }.map { it.name }.distinct().take(SIGNATURE_CARD_COUNT)
        } else {
            spec.seeds.map { it.name }.distinct().take(SIGNATURE_CARD_COUNT)
        }
        return chosen.sorted()
    }

    // ── Synthetic fallback (archetype layer, no community data) ───────────────────

    private fun syntheticTemplate(spec: DeckWizardSpec): DeckTemplate {
        val archetypeFormat = if (spec.format == DeckFormat.COMMANDER) ArchetypeFormat.COMMANDER else ArchetypeFormat.SIXTY
        val (archetypeId, themeId) = mapStrategyToArchetype(spec.strategyHint)
        val colorIdentity = if (spec.format == DeckFormat.COMMANDER) {
            spec.commander?.colorIdentity?.toManaColorSet() ?: spec.colorIdentity
        } else {
            spec.colorIdentity
        }
        val resolved = ArchetypeSkeletonResolver.resolveWithColor(
            format = archetypeFormat,
            archetype = archetypeId,
            themes = listOfNotNull(themeId),
            colorCount = colorIdentity.count { it != ManaColor.C },
        )
        val categories = resolved.roleTargets
            .filterKeys { it != ArchetypeData.MANA_FIX_KEY }
            .map { (roleKey, target) ->
                val category = roleKeyToSuggestionCategory(roleKey)
                TemplateCategory(
                    id = category.id,
                    label = category.displayLabel,
                    targetCount = target.ideal,
                    cards = emptyList(),
                )
            }
        val deckFormat = spec.format
        return DeckTemplate(
            source = TemplateSource.SYNTHETIC,
            categories = categories,
            landTarget = resolved.lands.ideal,
            manaCurveTarget = DeckSkeletons.forFormat(deckFormat).targetCurve,
            colorIdentity = colorIdentity,
            archetypeInfo = DeckTemplateArchetypeInfo(archetype = archetypeId, themes = listOfNotNull(themeId)),
            gamePlan = spec.strategyHint?.description,
        )
    }

    /**
     * Fixed allowlist mapping a wizard [com.mmg.manahub.feature.decks.domain.engine.SeedStrategy]
     * hint onto the archetype layer's own [ArchetypeId]/[ThemeId] vocabulary -- both enumerate a
     * FIXED, closed set of macro-strategies/themes (mirrors [ArchetypeFormat.of]'s "never guess"
     * convention: an unmapped/null hint resolves to GENERIC with no theme, never an invented one).
     */
    private fun mapStrategyToArchetype(hint: com.mmg.manahub.feature.decks.domain.engine.SeedStrategy?): Pair<ArchetypeId, ThemeId?> =
        when (hint) {
            com.mmg.manahub.feature.decks.domain.engine.SeedStrategy.AGGRO -> ArchetypeId.AGGRO to null
            com.mmg.manahub.feature.decks.domain.engine.SeedStrategy.CONTROL -> ArchetypeId.CONTROL to null
            com.mmg.manahub.feature.decks.domain.engine.SeedStrategy.COMBO -> ArchetypeId.COMBO to null
            com.mmg.manahub.feature.decks.domain.engine.SeedStrategy.MIDRANGE -> ArchetypeId.MIDRANGE to null
            com.mmg.manahub.feature.decks.domain.engine.SeedStrategy.RAMP -> ArchetypeId.RAMP to null
            com.mmg.manahub.feature.decks.domain.engine.SeedStrategy.TOKENS -> ArchetypeId.GENERIC to ThemeId.TOKENS
            com.mmg.manahub.feature.decks.domain.engine.SeedStrategy.GRAVEYARD -> ArchetypeId.GENERIC to ThemeId.REANIMATOR
            com.mmg.manahub.feature.decks.domain.engine.SeedStrategy.LIFEGAIN -> ArchetypeId.GENERIC to ThemeId.LIFEGAIN
            com.mmg.manahub.feature.decks.domain.engine.SeedStrategy.TRIBAL -> ArchetypeId.GENERIC to ThemeId.TRIBAL
            null -> ArchetypeId.GENERIC to null
        }

    // ── Shared helpers ──────────────────────────────────────────────────────────

    private fun groupCardsByCategory(cards: List<AggregateCardEntry>): List<TemplateCategory> =
        cards
            .groupBy { it.category.trim().ifEmpty { "Other" } }
            .map { (rawCategory, entries) ->
                val refs = entries
                    .map { entry ->
                        val category = SuggestionCategoryResolver.resolveAggregateOnly(entry.category)
                        TemplateCardRef(
                            name = entry.name,
                            weight = (entry.inclusionPct * WEIGHT_INCLUSION + entry.synergy * WEIGHT_SYNERGY)
                                .coerceIn(0f, 1f),
                            category = category,
                        )
                    }
                    .sortedWith(
                        compareByDescending<TemplateCardRef> { it.weight }.thenBy { it.name }
                    )
                val category = refs.firstOrNull()?.category
                    ?: SuggestionCategoryResolver.resolveAggregateOnly(rawCategory)
                TemplateCategory(
                    id = category.id,
                    label = category.displayLabel,
                    targetCount = refs.size,
                    cards = refs,
                )
            }
            .sortedBy { it.id }

    private fun normalizeCurve(rawCurve: Map<String, Int>): Map<Int, Float> {
        val total = rawCurve.values.sum()
        if (total <= 0) return emptyMap()
        return rawCurve.entries
            .mapNotNull { (bucket, count) ->
                val cmc = bucket.trim().removeSuffix("+").toIntOrNull()?.coerceIn(0, 7) ?: return@mapNotNull null
                cmc to count
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, counts) -> counts.sum().toFloat() / total }
    }

    /** Fixed allowlist ([com.mmg.manahub.core.model.CommunityAggregate.Commander.themeTags] are
     * free-form EDHREC theme names) -- an unmapped theme tag simply contributes no [ThemeId], never
     * a guess. At most 2 themes are kept (mirrors [ArchetypeSkeletonResolver]'s own 2-theme cap).
     *
     * @param preferredThemeHint [DeckWizardSpec.themeHint] (Phase 3 wizard Identity-step theme
     *   picker, or a Discoveries v2 tribe "Build this" hand-off) — when it matches one of the
     *   aggregate-derived [ThemeId]s, that theme is moved to the FRONT so the user's explicit pick
     *   actually wins the 2-theme cap instead of being silently dropped by aggregate ordering.
     *   A hint that matches nothing is a no-op, never an invented theme.
     */
    private fun matchThemeTags(rawTags: List<String>, preferredThemeHint: String? = null): List<ThemeId> {
        val normalized = rawTags.map { it.trim().lowercase() }
        val matched = ThemeId.entries
            .filter { theme -> normalized.any { it.contains(theme.displayName.lowercase()) } }
        val hint = preferredThemeHint?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        val preferred = hint?.let { h -> matched.firstOrNull { it.displayName.lowercase() == h || h.contains(it.displayName.lowercase()) } }
        val ordered = if (preferred != null) listOf(preferred) + matched.filterNot { it == preferred } else matched
        return ordered.take(2)
    }

    private fun titleCase(roleKey: String): String =
        roleKey.split('_').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }

    /**
     * Bridges the archetype layer's [com.mmg.manahub.feature.decks.domain.engine.RoleKey]
     * vocabulary onto [SuggestionCategoryResolver]'s own category id space for the `RoleKey`s that
     * have a clean [com.mmg.manahub.feature.decks.domain.engine.DeckRole] equivalent -- this is
     * REQUIRED, not cosmetic: [BuildDeckFromTemplateUseCase.fillFromCollection] buckets owned
     * candidates by `SuggestionCategoryResolver.resolve(card).id`, so a synthetic category whose id
     * stayed a raw `RoleKey` string ("removal_spot") would never match any candidate bucket (which
     * only ever produces "removal", "ramp", "card_draw", "board_wipes", "interaction", "tutors",
     * "lands" via the role step -- see [SuggestionCategoryResolver]'s `ROLE_CATEGORIES`).
     *
     * `RoleKey`s with no [DeckRole] equivalent (`finisher`, `threat_early`, `sac_outlet`,
     * `recursion`, ...) keep their own raw-`RoleKey`-derived id/label -- KNOWN LIMITATION: those
     * synthetic categories report an honest target but currently receive zero automatic
     * collection-fill/gap-suggestion content (`SuggestionCategoryResolver` never emits those ids for
     * a card). Extending `DeckRole`/`RoleClassifier` (or adding a dedicated `RoleKey`-aware
     * resolution step) to close this is future work -- see `project_deck_builder_v2_phase0_1_2`
     * memory.
     */
    private fun roleKeyToSuggestionCategory(roleKey: String): SuggestionCategory =
        ROLE_KEY_TO_SUGGESTION_CATEGORY[roleKey] ?: SuggestionCategory(roleKey, titleCase(roleKey))

    private companion object {
        const val SIGNATURE_CARD_COUNT = 3
        const val SIXTY_POLL_ATTEMPTS = 3
        const val SIXTY_POLL_BACKOFF_MS = 400L

        /** Weight blends inclusion (how many sampled decks run the card) and synergy (how well it
         * fits THIS shell) -- synergy carries slightly more signal (plan §3.1: "weight blends
         * inclusionPct x synergy"). */
        const val WEIGHT_INCLUSION = 0.4f
        const val WEIGHT_SYNERGY = 0.6f

        /**
         * Best-effort allowlist bridging the archetype layer's [com.mmg.manahub.feature.decks
         * .domain.engine.RoleKey] vocabulary onto the lowercase category labels a
         * [CommunityAggregate.Sixty] entry is expected to carry (Archidekt/Worker-side taxonomy,
         * `docs/adr/ADR-004-community-api-contracts.md`). Not verified against the LIVE Worker for
         * this batch (Phase 1 note: optional/best-effort) -- a `RoleKey` with no match here simply
         * gets zero enrichment rather than a wrong one; tune this table once real Worker responses
         * are inspected (see `project_deck_builder_v2_phase0_1_2` memory).
         */
        val ROLE_KEY_TO_AGGREGATE_LABELS: Map<String, Set<String>> = mapOf(
            "ramp" to setOf("ramp"),
            "card_draw" to setOf("card draw", "draw"),
            "removal" to setOf("removal", "spot removal", "creature removal"),
            "board_wipes" to setOf("board wipes", "mass removal", "wraths"),
            "tutors" to setOf("tutors", "tutor"),
            "interaction" to setOf("interaction", "counterspells", "protection"),
            "sac_outlet" to setOf("sacrifice", "sac outlets"),
            "finisher" to setOf("creatures", "threats", "win conditions"),
            "threat_early" to setOf("creatures", "early threats", "aggro"),
        )

        /** See [roleKeyToSuggestionCategory]'s KDoc for why this bridge exists. Keys are raw
         * [com.mmg.manahub.feature.decks.domain.engine.RoleKey] strings from [ArchetypeData]. */
        val ROLE_KEY_TO_SUGGESTION_CATEGORY: Map<String, SuggestionCategory> = mapOf(
            "ramp" to SuggestionCategory("ramp", "Ramp"),
            "card_draw" to SuggestionCategory("card_draw", "Card Draw"),
            "removal_spot" to SuggestionCategory("removal", "Removal"),
            "removal_mass" to SuggestionCategory("board_wipes", "Board Wipes"),
            "counterspell" to SuggestionCategory("interaction", "Interaction"),
            "tutor" to SuggestionCategory("tutors", "Tutors"),
        )
    }
}

private fun List<String>.toManaColorSet(): Set<ManaColor> =
    mapNotNull { symbol -> ManaColor.entries.firstOrNull { it.symbol == symbol } }.toSet()
