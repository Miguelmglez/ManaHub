package com.mmg.manahub.feature.decks.harness

import com.mmg.manahub.core.domain.usecase.decks.BasicLandCalculator
import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.core.model.UserCardWithCard
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeId
import com.mmg.manahub.feature.decks.domain.engine.ColorStrategyAffinity
import com.mmg.manahub.feature.decks.domain.engine.DeckEntry
import com.mmg.manahub.feature.decks.domain.engine.DeckScorer
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.NeutralPowerResolver
import com.mmg.manahub.feature.decks.domain.engine.RoleClassifier
import com.mmg.manahub.feature.decks.domain.engine.SeedStrategy
import com.mmg.manahub.feature.decks.domain.engine.StrategyProfile
import com.mmg.manahub.feature.decks.domain.engine.toStrategyProfile
import com.mmg.manahub.feature.decks.domain.template.BuildDeckFromTemplateUseCase
import com.mmg.manahub.feature.decks.domain.template.DeckTemplateResolver
import com.mmg.manahub.feature.decks.domain.template.DeckWizardSpec
import com.mmg.manahub.feature.decks.domain.template.TemplateBuildProgress
import com.mmg.manahub.feature.decks.domain.template.TemplateBuildResult
import com.mmg.manahub.feature.decks.domain.usecase.RankOwnedCardsForProfileUseCase
import com.mmg.manahub.feature.decks.domain.usecase.SuggestAddsFromCollectionUseCase
import com.mmg.manahub.feature.decks.domain.usecase.SuggestStrategiesForSeedsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect

// ═══════════════════════════════════════════════════════════════════════════════
//  Wizard Quality Campaign -- Phase H harness. Builds the spec matrix (§ "Spec matrix (v1 ≈ 70
//  builds)" in docs/plans/wizard-quality-campaign.md) and drives BuildDeckFromTemplateUseCase +
//  HarnessDoctorPipeline + HarnessMetricsCalculator for each spec.
// ═══════════════════════════════════════════════════════════════════════════════

data class BuildSpec(val label: String, val wizardSpec: DeckWizardSpec)

object HarnessSpecs {

    private val CASUAL_COLOR_SETS: List<Pair<String, Set<ManaColor>>> = listOf(
        "monoU" to setOf(ManaColor.U),
        "WU" to setOf(ManaColor.W, ManaColor.U),
        "BG" to setOf(ManaColor.B, ManaColor.G),
    )

    /** 9 SeedStrategy × 3 color sets + colorless + 3-color + a no-strategy baseline. */
    fun casualMatrix(): List<BuildSpec> {
        val specs = mutableListOf<BuildSpec>()
        SeedStrategy.entries.forEach { strategy ->
            CASUAL_COLOR_SETS.forEach { (colorLabel, colors) ->
                specs += BuildSpec(
                    label = "casual_${strategy.name.lowercase()}_$colorLabel",
                    wizardSpec = DeckWizardSpec(
                        format = DeckFormat.CASUAL,
                        strategyProfile = strategy.toStrategyProfile(),
                        colorIdentity = colors,
                        fillLands = true,
                    ),
                )
            }
        }
        specs += BuildSpec(
            "casual_colorless",
            DeckWizardSpec(format = DeckFormat.CASUAL, colorIdentity = emptySet(), fillLands = true),
        )
        specs += BuildSpec(
            "casual_3color_ruw",
            DeckWizardSpec(format = DeckFormat.CASUAL, colorIdentity = setOf(ManaColor.R, ManaColor.U, ManaColor.W), fillLands = true),
        )
        specs += BuildSpec(
            "casual_baseline_no_strategy",
            DeckWizardSpec(format = DeckFormat.CASUAL, colorIdentity = setOf(ManaColor.W, ManaColor.U), fillLands = true),
        )
        return specs
    }

    /** Every owned commander-legal legendary creature with no hint, 6 with a strategy hint, 2 with
     * seeds -- see HarnessFixtures.commanderCandidates. */
    fun commanderMatrix(fixtures: HarnessFixtures): List<BuildSpec> {
        val candidates = fixtures.commanderCandidates
            .filter { HarnessMetricsCalculator.isLegal(it, DeckFormat.COMMANDER) }
            .distinctBy { it.name }
            .sortedBy { it.name }

        val specs = mutableListOf<BuildSpec>()
        candidates.forEach { commander ->
            specs += BuildSpec(
                "commander_${slug(commander.name)}",
                DeckWizardSpec(format = DeckFormat.COMMANDER, commander = commander, fillLands = true),
            )
        }
        candidates.take(6).forEachIndexed { i, commander ->
            val strategy = SeedStrategy.entries[i % SeedStrategy.entries.size]
            specs += BuildSpec(
                "commander_${slug(commander.name)}_${strategy.name.lowercase()}",
                DeckWizardSpec(format = DeckFormat.COMMANDER, commander = commander, strategyProfile = strategy.toStrategyProfile(), fillLands = true),
            )
        }
        candidates.take(2).forEach { commander ->
            val identity = commander.colorIdentity.toSet()
            val seeds = fixtures.ownedCardsByName
                .filter { it.name != commander.name && identity.containsAll(it.colorIdentity) }
                .sortedBy { it.name }
                .take(2)
            specs += BuildSpec(
                "commander_${slug(commander.name)}_seeds",
                DeckWizardSpec(format = DeckFormat.COMMANDER, commander = commander, seeds = seeds, fillLands = true),
            )
        }
        return specs
    }

    /** 6 seed-shell builds (2-4 owned cards sharing a STRATEGY tag) -- Casual format. */
    fun seedsMatrix(fixtures: HarnessFixtures): List<BuildSpec> {
        val shellTags = listOf(CardTag.TOKENS, CardTag.GRAVEYARD, CardTag.SACRIFICE, CardTag.LIFEGAIN, CardTag.RAMP, CardTag.CONTROL)
        return shellTags.mapIndexedNotNull { _, tag ->
            val seeds = fixtures.ownedCardsByName
                .filter { tag in (it.tags + it.userTags) }
                .distinctBy { it.name }
                .sortedBy { it.name }
                .take(4)
            if (seeds.size < 2) return@mapIndexedNotNull null
            val colors = seeds.flatMap { it.colorIdentity }.mapNotNull { s -> ManaColor.entries.firstOrNull { it.symbol == s } }.toSet()
            BuildSpec(
                "seeds_${tag.key}",
                DeckWizardSpec(format = DeckFormat.CASUAL, seeds = seeds, colorIdentity = colors, fillLands = true),
            )
        }
    }

    /**
     * Deck Engine Unification RUN 7a (plan §5 Phase 6.1 -- "3-flow coverage"). Every spec above
     * builds a [StrategyProfile] BY HAND and calls the shared build directly; these three instead
     * drive the ACTUAL RUN 3 wizard entry-path use cases first, so the harness exercises the same
     * decision logic the wizard screens call, not just the underlying build primitive:
     *
     * - **Flow A (cards-first):** a small, USER-TRIMMED seed set (2 cards -- RC5: the user picks
     *   exact cards, never a whole synergy cluster) is ranked by [SuggestStrategiesForSeedsUseCase]
     *   exactly like `DeckWizardViewModel.recomputeSeedStrategySuggestion`; the top-ranked candidate
     *   becomes the build's [StrategyProfile].
     * - **Flow B (colors-first):** a color combo is ranked by [ColorStrategyAffinity.forColors]
     *   (mirrors `ColorsFlowDirectionContent`); the resolved profile is then handed to
     *   [RankOwnedCardsForProfileUseCase] for a "suggested seeds" list, of which the build accepts a
     *   trimmed subset (mirrors the check/uncheck UX -- not every suggestion is auto-included).
     * - **Flow C (strategy-first):** a taxonomy archetype is picked directly, then
     *   [ColorStrategyAffinity.combosFor] ranks the color combo (mirrors `StrategyFlowDirectionContent`),
     *   with the same suggested-seeds hand-off as Flow B.
     *
     * All three degrade gracefully to [StrategyProfile.EMPTY]/no seeds if the real (gitignored, user-
     * specific) fixture happens not to support the chosen tag/archetype -- this code cannot assume
     * the fixture's exact contents, only that a build must still succeed either way.
     */
    fun entryFlowMatrix(fixtures: HarnessFixtures): List<BuildSpec> {
        val specs = mutableListOf<BuildSpec>()

        // ── Flow A -- cards-first ───────────────────────────────────────────────────────────
        val flowASeeds = fixtures.ownedCardsByName
            .filter { CardTag.TOKENS in (it.tags + it.userTags) }
            .distinctBy { it.name }
            .sortedBy { it.name }
            .take(2)
        val flowASuggestion = SuggestStrategiesForSeedsUseCase()(flowASeeds)
        val flowAProfile = flowASuggestion.candidates.firstOrNull()?.profile ?: StrategyProfile.EMPTY
        val flowAColors = flowASeeds.flatMap { it.colorIdentity }
            .mapNotNull { s -> ManaColor.entries.firstOrNull { it.symbol == s } }
            .toSet()
        specs += BuildSpec(
            "entryflow_a_cards_first",
            DeckWizardSpec(format = DeckFormat.CASUAL, strategyProfile = flowAProfile, seeds = flowASeeds, colorIdentity = flowAColors, fillLands = true),
        )

        // ── Flow B -- colors-first ──────────────────────────────────────────────────────────
        val flowBColors = setOf(ManaColor.R, ManaColor.G)
        val flowBEntry = ColorStrategyAffinity.forColors(flowBColors).firstOrNull()
        val flowBProfile = flowBEntry?.toStrategyProfile(flowBColors) ?: StrategyProfile(colors = flowBColors)
        val flowBSeeds = RankOwnedCardsForProfileUseCase()(flowBProfile, fixtures.ownedCardsByName).take(2)
        specs += BuildSpec(
            "entryflow_b_colors_first",
            DeckWizardSpec(format = DeckFormat.CASUAL, strategyProfile = flowBProfile, seeds = flowBSeeds, colorIdentity = flowBColors, fillLands = true),
        )

        // ── Flow C -- strategy-first ────────────────────────────────────────────────────────
        val flowCArchetype = ArchetypeId.RAMP
        val flowCColors = ColorStrategyAffinity.combosFor(flowCArchetype, null).firstOrNull()?.first ?: setOf(ManaColor.G)
        val flowCProfile = StrategyProfile(archetype = flowCArchetype, colors = flowCColors)
        val flowCSeeds = RankOwnedCardsForProfileUseCase()(flowCProfile, fixtures.ownedCardsByName).take(2)
        specs += BuildSpec(
            "entryflow_c_strategy_first",
            DeckWizardSpec(format = DeckFormat.CASUAL, strategyProfile = flowCProfile, seeds = flowCSeeds, colorIdentity = flowCColors, fillLands = true),
        )

        return specs
    }

    private fun slug(name: String): String = name.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
}

object MatrixRunner {

    suspend fun run(specs: List<BuildSpec>, fixtures: HarnessFixtures): List<BuildMetrics> {
        val cardRepository = FixtureCardRepository(fixtures.cardsByName, fixtures.basicsByName)
        val communityRepository = NoCommunityAggregateRepository()
        val deckScorer = DeckScorer(RoleClassifier(), NeutralPowerResolver)
        // Deck Engine Unification (D1): the harness drives the SAME Motor A engine production uses --
        // Motor B stays at its nullable/`{false}` defaults (community disabled), mirroring the
        // NoCommunityAggregateRepository used for DeckTemplateResolver above.
        val useCase = BuildDeckFromTemplateUseCase(
            deckTemplateResolver = DeckTemplateResolver(communityRepository, ioDispatcher = Dispatchers.Default),
            deckScorer = deckScorer,
            cardRepository = cardRepository,
            suggestAddsFromCollectionUseCase = SuggestAddsFromCollectionUseCase(deckScorer),
            ioDispatcher = Dispatchers.Default,
        )
        val ownedQuantityByName = fixtures.collection
            .groupBy { it.card.name }
            .mapValues { (_, rows) -> rows.sumOf { it.userCard.quantity } }

        return specs.map { spec ->
            runCatching { runOne(spec, useCase, fixtures, ownedQuantityByName) }
                .getOrElse { t -> HarnessMetricsCalculator.forFailedBuild(spec.label, spec.wizardSpec.format, t.message ?: t.toString()) }
        }
    }

    private suspend fun runOne(
        spec: BuildSpec,
        useCase: BuildDeckFromTemplateUseCase,
        fixtures: HarnessFixtures,
        ownedQuantityByName: Map<String, Int>,
    ): BuildMetrics {
        val result1 = build(useCase, spec.wizardSpec, fixtures.collection)
            ?: return HarnessMetricsCalculator.forFailedBuild(spec.label, spec.wizardSpec.format, "build did not complete")
        val result2 = build(useCase, spec.wizardSpec, fixtures.collection)

        val eligibleOwnedNames = eligibleOwnedNames(spec.wizardSpec, fixtures)

        // Deck Engine Unification RUN 7a (plan §7 acceptance criterion 4 / BUG-1 regression guard):
        // mirror DeckWizardViewModel.writeResultIntoNewDeck's EXACT write. The commander is persisted
        // as a qty-1 mainboard entry ALONGSIDE (never inside) TemplateBuildResult.deckCards --
        // BuildDeckFromTemplateUseCase.mainboardTargetSize reserves the slot but by design never
        // places the row itself (see that use case's own KDoc). Production's Doctor pipeline
        // (DeckDoctorOrchestrator.loadAnalysis) reads the PERSISTED Room mainboard, commander row
        // included, so the harness's Doctor evaluation must see the SAME combined list -- otherwise it
        // silently under-represents the commander's own tag/curve/role contribution to the deck
        // profile AND fails to exclude the commander from Motor A's "not already in the deck"
        // candidate filter (SuggestAddsFromCollectionUseCase.mainboardIds), both real fidelity gaps
        // relative to what production actually evaluates.
        val commander = spec.wizardSpec.commander
        val commanderEntry = if (spec.wizardSpec.format == DeckFormat.COMMANDER && commander != null) {
            DeckEntry(card = commander, quantity = 1, isOwned = true, isSideboard = false)
        } else {
            null
        }
        val persistedMainboard = listOfNotNull(commanderEntry) + result1.deckCards
        // The explicit "commander appears as an entry in the built deck" assert (plan §7 criterion 4):
        // vacuously true for non-Commander specs; for Commander specs, true only when the commander
        // merged in exactly once (never missing, never double-counted against a use-case-placed row).
        val commanderEntryOk = commanderEntry == null ||
            persistedMainboard.count { it.card.scryfallId == commanderEntry.card.scryfallId } == 1

        // Deck Engine Unification (D4): every wizard build persists strategyLocked=true and
        // source=WIZARD on every placed card (DeckWizardViewModel.writeResultIntoNewDeck), commander
        // included (RUN 7a) -- mirror that here so the harness's coherence-cuts-v2 metric exercises
        // the SAME structural exclusion production gets, not a weaker approximation.
        val wizardSourcedIds = persistedMainboard.map { it.card.scryfallId }.toSet()
        val doctor = HarnessDoctorPipeline.evaluate(
            mainboard = persistedMainboard,
            format = spec.wizardSpec.format,
            commander = spec.wizardSpec.commander,
            collection = fixtures.ownedCardsByName,
            archetypeOverride = result1.archetypeOverride,
            themesOverride = result1.themesOverride,
            tribeOverride = spec.wizardSpec.strategyProfile.tribe,
            strategyLocked = true,
            wizardSourcedIds = wizardSourcedIds,
        )

        // WS8.1 round-trip invariant: re-rank cuts against the SAME doctor.health.profile with NO
        // structural protection (only the commander) -- the real, non-structurally-guaranteed test.
        // Reuses doctor's own profile rather than a second full evaluate (see
        // HarnessDoctorPipeline.cutsWithProtection's KDoc for why).
        val unlockedProtectedIds = setOfNotNull(commanderEntry?.card?.scryfallId)
        val cutsUnlocked = HarnessDoctorPipeline.cutsWithProtection(
            mainboard = persistedMainboard,
            profile = doctor.health.profile,
            protectedIds = unlockedProtectedIds,
            resolvedSkeleton = HarnessDoctorPipeline.resolveArchetypeSkeleton(doctor.health),
        )

        return HarnessMetricsCalculator.compute(
            label = spec.label,
            format = spec.wizardSpec.format,
            result = result1,
            secondRunDeckCards = result2?.deckCards,
            ownedQuantityByName = ownedQuantityByName,
            eligibleOwnedNames = eligibleOwnedNames,
            commanderIdentity = spec.wizardSpec.commander?.colorIdentity?.toSet().orEmpty(),
            seedCardIds = spec.wizardSpec.seeds.map { it.scryfallId }.toSet(),
            commanderCardId = spec.wizardSpec.commander?.scryfallId,
            commanderEntryOk = commanderEntryOk,
            doctorProfile = doctor.health.profile,
            cuts = doctor.cuts,
            cutsUnlocked = cutsUnlocked,
            adds = doctor.adds,
            warnings = doctor.health.evaluation.warnings,
            overallScore = doctor.health.evaluation.healthScore.toFloat(),
        )
    }

    private suspend fun build(
        useCase: BuildDeckFromTemplateUseCase,
        spec: DeckWizardSpec,
        collection: List<UserCardWithCard>,
    ): TemplateBuildResult? {
        var result: TemplateBuildResult? = null
        useCase(spec, collection).collect { progress ->
            if (progress is TemplateBuildProgress.Complete) result = progress.result
        }
        return result
    }

    /** Mirrors [BuildDeckFromTemplateUseCase]'s private `analyzeCollection` EXACTLY -- feeds the
     * coherence-adds metric ("0 Motor-A adds of an OWNED card that passed the wizard's OWN
     * filters"). Duplicated here since that function is private to the use case. */
    private fun eligibleOwnedNames(spec: DeckWizardSpec, fixtures: HarnessFixtures): Set<String> {
        val commanderIdentity = spec.commander?.colorIdentity?.toSet()
        val commanderId = spec.commander?.scryfallId
        return fixtures.ownedCardsByName
            .filterNot { BasicLandCalculator.isLand(it) }
            // RUN 7a: mirrors analyzeCollection's own commander-exclusion fix (BUG-1 follow-up) --
            // the commander is placed via its own dedicated path, never eligible as a regular pick.
            // (commanderId hoisted to a local val -- cross-module property smart-casts don't apply.)
            .filterNot { commanderId != null && it.scryfallId == commanderId }
            .filter { HarnessMetricsCalculator.isLegal(it, spec.format) }
            .filter { card ->
                when {
                    spec.format == DeckFormat.COMMANDER && commanderIdentity != null ->
                        commanderIdentity.containsAll(card.colorIdentity)
                    spec.format.isSixtyCardConstructed -> {
                        val cardColors = card.colors.mapNotNull { s -> ManaColor.entries.firstOrNull { it.symbol == s } }
                        cardColors.isEmpty() || cardColors.all { it in spec.colorIdentity }
                    }
                    else -> true
                }
            }
            .map { it.name }
            .toSet()
    }
}
