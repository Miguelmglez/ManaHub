package com.mmg.manahub.feature.decks.domain.template

import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.usecase.decks.BasicLandCalculator
import com.mmg.manahub.core.model.BasicLandDistribution
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.DeckCard
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.core.model.UserCardWithCard
import com.mmg.manahub.feature.decks.domain.engine.DeckEntry
import com.mmg.manahub.feature.decks.domain.engine.DeckProfile
import com.mmg.manahub.feature.decks.domain.engine.DeckRole
import com.mmg.manahub.feature.decks.domain.engine.DeckScorer
import com.mmg.manahub.feature.decks.domain.engine.DeckSkeletons
import com.mmg.manahub.feature.decks.domain.engine.ManaBaseAnalyzer
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.ThemeId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Deck Builder v2 (`docs/plans/deck-builder-v2-plan.md` §3.3) — the v2 builder. Fills a
 * [DeckWizardSpec] against a [DeckTemplateResolver]-resolved [DeckTemplate], emitting staged
 * [TemplateBuildProgress] (D6: long generation is fine as long as the caller gets legible progress).
 *
 * ## Ownership / collection input (deviation from the Motor A/B `List<Card>` precedent)
 * Takes [UserCardWithCard] (not a plain `List<Card>`) — unlike
 * [com.mmg.manahub.feature.decks.domain.usecase.SuggestAddsFromCollectionUseCase], this use case
 * needs REAL per-card owned quantity for the §3.6 Casual playset math
 * (`min(templateCopies, ownedCopies)` — the plan is explicit this is quantity-aware, not the flatter
 * "top up to `maxCopies`" D3 precedent used elsewhere). See `project_deck_builder_v2_phase0_1_2`
 * memory for the full reasoning.
 *
 * ## Zero alphabetical Scryfall searches (fixes root cause 1.2.1 for v2 specifically)
 * Every gap-resolution / basic-land-materialization lookup goes through
 * [CardRepository.getCardByExactName] (the cached, rate-limited `cards/named?exact=` endpoint) —
 * never [CardRepository.searchWithRawQuery]. DFC/split cards are normalized to their front face
 * (`substringBefore(" // ")`) before lookup, mirroring the existing convention in
 * `AddCardSheet`/`CardName`/`TagAnalyzers`.
 *
 * ## Determinism
 * The collection fill stage sorts by an EXPLICIT comparator (score desc, community weight desc,
 * name asc, scryfallId asc) — never collection/template iteration order.
 */
class BuildDeckFromTemplateUseCase(
    private val deckTemplateResolver: DeckTemplateResolver,
    private val deckScorer: DeckScorer,
    private val cardRepository: CardRepository,
    private val manaBaseAnalyzer: ManaBaseAnalyzer = ManaBaseAnalyzer(),
    private val crashReporter: CrashReporter? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    operator fun invoke(spec: DeckWizardSpec, collection: List<UserCardWithCard>): Flow<TemplateBuildProgress> = flow {
        emit(TemplateBuildProgress.Stage(BuildStage.VALIDATING))
        val validationError = validate(spec)
        if (validationError != null) {
            emit(TemplateBuildProgress.Failed(BuildStage.VALIDATING, validationError))
            return@flow
        }

        emit(TemplateBuildProgress.Stage(BuildStage.ANALYZING_COLLECTION))
        val ownedQuantityByName = collection
            .groupBy { it.card.name }
            .mapValues { (_, entries) -> entries.sumOf { it.userCard.quantity } }
        val ownedCards = analyzeCollection(spec, collection.map { it.card }.distinctBy { it.scryfallId })

        emit(TemplateBuildProgress.Stage(BuildStage.FETCHING_COMMUNITY))
        val template = try {
            deckTemplateResolver.resolve(spec)
        } catch (t: Throwable) {
            crashReporter?.recordException(RuntimeException("deck_builder_v2_template_resolve_failed", t))
            emit(TemplateBuildProgress.Failed(BuildStage.FETCHING_COMMUNITY, "Could not resolve a build template."))
            return@flow
        }

        emit(TemplateBuildProgress.Stage(BuildStage.MAPPING_CATEGORIES))
        val seedTags = (spec.strategyHint?.primaryTags.orEmpty() + themeSeedTags(template.archetypeInfo.themes)).distinct()
        val seedEntries = spec.seeds.map { seed ->
            DeckEntry(card = seed, quantity = 1, isOwned = ownedQuantityByName.containsKey(seed.name), isSideboard = false)
        }
        val profile = deckScorer.profile(
            mainboard = seedEntries,
            format = spec.format,
            colorIdentity = template.colorIdentity,
            seedTags = seedTags,
        )
        val categories = mapCategories(template, spec, profile)

        emit(TemplateBuildProgress.Stage(BuildStage.FILLING_FROM_COLLECTION))
        val fill = fillFromCollection(spec, categories, ownedCards, seedEntries, profile, ownedQuantityByName)

        emit(TemplateBuildProgress.Stage(BuildStage.RESOLVING_GAPS))
        val gapResult = resolveGaps(categories, fill)

        emit(TemplateBuildProgress.Stage(BuildStage.FILLING_LANDS))
        val landEntries = if (spec.fillLands) {
            fillLands(spec, template, fill.placedEntries, seedEntries, profile, seedTags)
        } else {
            emptyList()
        }

        if (gapResult.unresolvedMisses > 0) {
            crashReporter?.log("deck_builder_v2_gap_resolution_partial")
            crashReporter?.setCustomKey("deck_builder_v2_miss_count", gapResult.unresolvedMisses.toString())
            crashReporter?.recordException(
                RuntimeException("deck_builder_v2_gap_resolution_partial: misses=${gapResult.unresolvedMisses}")
            )
        }

        emit(TemplateBuildProgress.Stage(BuildStage.DONE))
        val report = categories.map { category ->
            CategoryFill(
                category = SuggestionCategory(category.id, category.label),
                filled = fill.filledCounts[category.id] ?: 0,
                target = category.targetCount,
            )
        }
        val result = TemplateBuildResult(
            deckCards = fill.placedEntries + landEntries,
            communitySuggestions = gapResult.suggestions,
            report = report,
            templateSource = template.source,
            archetypeInfo = template.archetypeInfo,
            archetypeOverride = template.archetypeInfo.archetype.name,
            themesOverride = template.archetypeInfo.themes.map { it.name },
            colorConsistencyWarning = spec.format.isSixtyCardConstructed &&
                template.colorIdentity.count { it != ManaColor.C } > COLOR_DISCIPLINE_LIMIT,
            gamePlan = template.gamePlan,
        )
        emit(TemplateBuildProgress.Complete(result))
    }.flowOn(ioDispatcher)

    // ── VALIDATING ──────────────────────────────────────────────────────────────

    private fun isLegal(card: Card, format: DeckFormat): Boolean {
        fun ok(s: String) = s.equals("legal", true) || s.equals("restricted", true)
        return when (format) {
            DeckFormat.STANDARD -> ok(card.legalityStandard)
            DeckFormat.PIONEER -> ok(card.legalityPioneer)
            DeckFormat.MODERN -> ok(card.legalityModern)
            DeckFormat.LEGACY -> ok(card.legalityLegacy)
            DeckFormat.VINTAGE -> ok(card.legalityVintage)
            DeckFormat.PAUPER -> ok(card.legalityPauper)
            DeckFormat.COMMANDER -> ok(card.legalityCommander)
            DeckFormat.CASUAL, DeckFormat.DRAFT -> true
        }
    }

    private fun validate(spec: DeckWizardSpec): String? {
        if (spec.format == DeckFormat.COMMANDER) {
            val commander = spec.commander ?: return "A commander is required for the Commander format."
            if (!isLegal(commander, DeckFormat.COMMANDER)) {
                return "${commander.name} is not legal as a Commander."
            }
            val identity = commander.colorIdentity.toSet()
            val offIdentitySeed = spec.seeds.firstOrNull { seed -> seed.colorIdentity.any { it !in identity } }
            if (offIdentitySeed != null) {
                return "${offIdentitySeed.name} is outside ${commander.name}'s color identity."
            }
        }
        return null
    }

    // ── ANALYZING_COLLECTION ────────────────────────────────────────────────────

    private fun analyzeCollection(spec: DeckWizardSpec, ownedCards: List<Card>): List<Card> {
        val commanderIdentity = spec.commander?.colorIdentity?.toSet()
        return ownedCards
            .filterNot { BasicLandCalculator.isLand(it) }
            .filter { isLegal(it, spec.format) }
            .filter { card ->
                when {
                    spec.format == DeckFormat.COMMANDER && commanderIdentity != null ->
                        commanderIdentity.containsAll(card.colorIdentity)
                    // D9: Casual has no identity rule -- the wizard's chosen colors are a spell
                    // COLOR filter, not a color-identity restriction. Colorless cards always pass.
                    // NOTE: spec.colorIdentity.isEmpty() is a VALID, intentional filter state (the
                    // wizard's "Colorless" pick, see deck_seeds_identity_colorless) -- it must still
                    // gate this branch (never fall through to `else -> true`), otherwise a Casual
                    // "colorless" build silently admits cards of every color.
                    spec.format.isSixtyCardConstructed -> {
                        val cardColors = card.colors.mapNotNull { s -> ManaColor.entries.firstOrNull { it.symbol == s } }
                        cardColors.isEmpty() || cardColors.all { it in spec.colorIdentity }
                    }
                    else -> true
                }
            }
    }

    // ── MAPPING_CATEGORIES ──────────────────────────────────────────────────────

    /** Merges every seed into its resolved category, creating a zero-target category for a seed
     * whose category the template did not already declare (seeds are NEVER dropped). Preserves the
     * template's own category ORDER (plan §3.3 step 5: "per category, in template order"),
     * appending seed-only categories at the end. */
    private fun mapCategories(template: DeckTemplate, spec: DeckWizardSpec, profile: DeckProfile): List<TemplateCategory> {
        val existingIds = template.categories.mapTo(mutableSetOf()) { it.id }
        val extra = mutableListOf<TemplateCategory>()
        spec.seeds.forEach { seed ->
            val category = SuggestionCategoryResolver.resolve(seed, profile = profile)
            if (existingIds.add(category.id)) {
                extra += TemplateCategory(id = category.id, label = category.displayLabel, targetCount = 0, cards = emptyList())
            }
        }
        return template.categories + extra
    }

    // ── FILLING_FROM_COLLECTION ─────────────────────────────────────────────────

    private data class ScoredCandidate(val card: Card, val score: Float, val communityWeight: Float)

    private class FillState {
        val placedEntries = mutableListOf<DeckEntry>()
        val usedNames = mutableSetOf<String>()
        val filledCounts = mutableMapOf<String, Int>()
        val sameCardShortfalls = mutableMapOf<String, MutableList<TemplateCardSuggestion>>()
    }

    private fun fillFromCollection(
        spec: DeckWizardSpec,
        categories: List<TemplateCategory>,
        ownedCards: List<Card>,
        seedEntries: List<DeckEntry>,
        profile: DeckProfile,
        ownedQuantityByName: Map<String, Int>,
    ): FillState {
        val state = FillState()
        state.placedEntries += seedEntries
        seedEntries.forEach { entry ->
            state.usedNames += entry.card.name
            val categoryId = SuggestionCategoryResolver.resolve(entry.card, profile = profile).id
            state.filledCounts[categoryId] = (state.filledCounts[categoryId] ?: 0) + entry.quantity
        }

        val candidatesByCategory = ownedCards
            .filterNot { it.name in state.usedNames }
            .groupBy { card -> SuggestionCategoryResolver.resolve(card, profile = profile).id }

        val multiCopyFormat = spec.format.isSixtyCardConstructed
        val maxCopies = spec.format.maxCopies

        categories.forEach { category ->
            val alreadyPlaced = state.filledCounts[category.id] ?: 0
            var remaining = category.targetCount - alreadyPlaced
            if (remaining <= 0) return@forEach

            val candidates = candidatesByCategory[category.id].orEmpty().filterNot { it.name in state.usedNames }
            val scored = candidates
                .map { card ->
                    val fit = deckScorer.fit(card, profile, isOwned = true)
                    val ref = matchingTemplateRef(card, category)
                    val weight = ref?.weight ?: 0f
                    ScoredCandidate(card, (fit.score + weight * COMMUNITY_BONUS_SCALE).coerceIn(0f, 2f), weight)
                }
                .sortedWith(
                    compareByDescending<ScoredCandidate> { it.score }
                        .thenByDescending { it.communityWeight }
                        .thenBy { it.card.name }
                        .thenBy { it.card.scryfallId }
                )

            var rank = 0
            for (candidate in scored) {
                if (remaining <= 0) break
                if (candidate.card.name in state.usedNames) continue
                state.usedNames += candidate.card.name

                val desiredCopies = if (multiCopyFormat) importanceTierCopies(rank).coerceAtMost(maxCopies) else 1
                val ownedCopies = ownedQuantityByName[candidate.card.name] ?: 1
                val placedCopies = minOf(desiredCopies, ownedCopies, maxCopies).coerceAtLeast(1)

                state.placedEntries += DeckEntry(card = candidate.card, quantity = placedCopies, isOwned = true, isSideboard = false)
                state.filledCounts[category.id] = (state.filledCounts[category.id] ?: 0) + placedCopies
                remaining -= placedCopies
                rank++

                val shortfall = desiredCopies - placedCopies
                if (shortfall > 0) {
                    state.sameCardShortfalls.getOrPut(category.id) { mutableListOf() } +=
                        TemplateCardSuggestion(card = candidate.card, weight = candidate.score, suggestedCopies = shortfall)
                }
            }
        }
        return state
    }

    private fun matchingTemplateRef(card: Card, category: TemplateCategory): TemplateCardRef? {
        val frontFace = card.name.substringBefore(" // ")
        return category.cards.firstOrNull { it.name == card.name || it.name == frontFace }
    }

    /** Playset-slot-importance proxy (§3.6): the best-fitting card in a category is the "core"
     * pick (4x), the next two are "key support" (3x), the next three are "flex" (2x), the rest are
     * "top-end/situational" (1x). Rank-based rather than a separate slot classifier -- monotonic
     * with the same fit score the fill stage already sorted by. */
    private fun importanceTierCopies(rank: Int): Int = when {
        rank == 0 -> 4
        rank in 1..2 -> 3
        rank in 3..5 -> 2
        else -> 1
    }

    // ── RESOLVING_GAPS ──────────────────────────────────────────────────────────

    private class GapResult(val suggestions: List<CategorySuggestions>, val unresolvedMisses: Int)

    private suspend fun resolveGaps(categories: List<TemplateCategory>, fill: FillState): GapResult {
        val suggestions = mutableListOf<CategorySuggestions>()
        var misses = 0
        categories.forEach { category ->
            val filled = fill.filledCounts[category.id] ?: 0
            val remaining = (category.targetCount - filled).coerceAtLeast(0)
            val sameCardShortfalls = fill.sameCardShortfalls[category.id].orEmpty()

            val resolvedUnowned = if (remaining > 0) {
                val unownedRefs = category.cards
                    .filterNot { it.name in fill.usedNames }
                    .sortedWith(compareByDescending<TemplateCardRef> { it.weight }.thenBy { it.name })
                    .take(remaining)
                unownedRefs.mapNotNull { ref ->
                    val card = resolveByExactName(ref.name)
                    if (card == null) {
                        misses++
                        null
                    } else {
                        fill.usedNames += card.name
                        TemplateCardSuggestion(card = card, weight = ref.weight, suggestedCopies = ref.copies)
                    }
                }
            } else {
                emptyList()
            }

            val combined = sameCardShortfalls + resolvedUnowned
            if (combined.isNotEmpty()) {
                suggestions += CategorySuggestions(
                    category = SuggestionCategory(category.id, category.label),
                    suggestions = combined,
                )
            }
        }
        return GapResult(suggestions, misses)
    }

    /** Exact-name lookup ONLY (never [CardRepository.searchWithRawQuery]) -- DFC/split names are
     * normalized to their front face first. Returns null on any miss (card renamed/typo/etc.);
     * callers count misses and skip silently, never abort the build. */
    private suspend fun resolveByExactName(name: String): Card? {
        val normalized = name.substringBefore(" // ").trim()
        if (normalized.isEmpty()) return null
        return runCatching { cardRepository.getCardByExactName(normalized) }.getOrNull()?.getOrNull()
    }

    // ── FILLING_LANDS ───────────────────────────────────────────────────────────

    private suspend fun fillLands(
        spec: DeckWizardSpec,
        template: DeckTemplate,
        placedEntries: List<DeckEntry>,
        seedEntries: List<DeckEntry>,
        seedProfile: DeckProfile,
        seedTags: List<CardTag>,
    ): List<DeckEntry> {
        val nonBasicLandSeeds = spec.seeds.filter { BasicLandCalculator.isLand(it) && !BasicLandCalculator.isBasicLand(it) }

        val landTarget = if (spec.format.isSixtyCardConstructed) {
            // §3.6: refine the archetype-driven land count by the ACTUAL placed non-land mix
            // (ramp/curve/draw) -- dynamicLandIdeal can only RELAX the count, never exceed it.
            val fullProfile = deckScorer.profile(
                mainboard = placedEntries,
                format = spec.format,
                colorIdentity = template.colorIdentity,
                seedTags = seedTags,
            )
            manaBaseAnalyzer.dynamicLandIdeal(fullProfile).takeIf { it > 0 } ?: template.landTarget
        } else {
            template.landTarget.takeIf { it > 0 } ?: DeckSkeletons.forFormat(spec.format).idealFor(DeckRole.LAND)
        }

        val mainboardDeckCards = placedEntries.map { DeckCard(it.card, it.quantity, it.isOwned) }
        val nonBasicLandDeckCards = nonBasicLandSeeds.map { DeckCard(it, 1, true) }
        // BasicLandCalculator.calculate falls back to an EVEN SPLIT across `commanderIdentity` only
        // when the mainboard has zero colored pips (totalWeight == 0) -- with a bare `null` for
        // every non-Commander format, that fallback never fires and the calculator silently returns
        // an all-ZERO BasicLandDistribution (an unplayable, land-less deck). A Casual build's filled
        // mainboard can land at zero colored pips two ways: the wizard's own "Colorless" (0-color)
        // pick, or incidentally for any color-light/artifact-heavy fill -- so the wizard's OWN chosen
        // spec.colorIdentity is the correct color-restriction fallback here, mirroring what the
        // Commander branch already does with the commander's identity.
        val landColorIdentity = when {
            spec.format == DeckFormat.COMMANDER -> spec.commander?.colorIdentity?.toSet()
            spec.colorIdentity.isNotEmpty() -> spec.colorIdentity.map { it.symbol }.toSet()
            // Genuinely 0-color ("Colorless") Casual pick: 0 basics is the best available result --
            // this calculator has no "Wastes"/colorless-land concept. Known, out-of-scope limitation
            // (adding Wastes support is a separate feature, not a fix for this bug).
            else -> null
        }

        val distribution = BasicLandCalculator.calculate(
            mainboard = mainboardDeckCards,
            nonBasicLands = nonBasicLandDeckCards,
            totalLandTarget = landTarget,
            commanderIdentity = landColorIdentity,
        )
        return materializeBasics(distribution)
    }

    private suspend fun materializeBasics(distribution: BasicLandDistribution): List<DeckEntry> {
        val wanted = listOf(
            "Plains" to distribution.plains,
            "Island" to distribution.islands,
            "Swamp" to distribution.swamps,
            "Mountain" to distribution.mountains,
            "Forest" to distribution.forests,
        ).filter { it.second > 0 }
        return wanted.mapNotNull { (name, qty) ->
            val card = resolveByExactName(name) ?: return@mapNotNull null
            DeckEntry(card = card, quantity = qty, isOwned = true, isSideboard = false)
        }
    }

    // ── Shared helpers ──────────────────────────────────────────────────────────

    /** Fixed allowlist mapping the archetype layer's [ThemeId] onto the legacy [CardTag] seed
     * vocabulary [DeckScorer.profile] consumes -- an unmapped theme contributes no seed tag, never a
     * guess (mirrors [DeckTemplateResolver.mapStrategyToArchetype]'s own "never guess" convention). */
    private fun themeSeedTags(themes: List<ThemeId>): List<CardTag> = themes.mapNotNull { theme ->
        when (theme) {
            ThemeId.TOKENS -> CardTag.TOKENS
            ThemeId.ARISTOCRATS -> CardTag.SACRIFICE
            ThemeId.LIFEGAIN -> CardTag.LIFEGAIN
            ThemeId.TRIBAL -> CardTag.TRIBAL
            ThemeId.REANIMATOR, ThemeId.SELF_MILL, ThemeId.MILL -> CardTag.GRAVEYARD
            ThemeId.STAX -> CardTag.STAX
            ThemeId.ENCHANTRESS -> CardTag.ENCHANTRESS
            ThemeId.BLINK -> CardTag.BLINK
            else -> null
        }
    }

    private companion object {
        /** Additive nudge (kept small vs. the engine's own ~0.2-0.34 term weights) so an
         * exact-name aggregate match breaks ties toward the community-endorsed pick without ever
         * overriding a clearly-better DeckScorer fit. */
        const val COMMUNITY_BONUS_SCALE = 0.25f
        const val COLOR_DISCIPLINE_LIMIT = 2
    }
}
