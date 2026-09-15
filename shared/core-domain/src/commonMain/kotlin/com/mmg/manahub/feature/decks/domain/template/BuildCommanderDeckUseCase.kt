package com.mmg.manahub.feature.decks.domain.template
// COMMENTS_REVIEWED: 2026-09-09

import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.domain.repository.CardSlotWrite
import com.mmg.manahub.core.domain.repository.DeckRepository
import com.mmg.manahub.core.domain.usecase.decks.BasicLandCalculator
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.DeckCardSource
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeData
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeFormat
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeRoleClassifier
import com.mmg.manahub.feature.decks.domain.engine.AxisKey
import com.mmg.manahub.feature.decks.domain.engine.CardSection
import com.mmg.manahub.feature.decks.domain.engine.CommanderPlan
import com.mmg.manahub.feature.decks.domain.engine.CommanderPlanResolver
import com.mmg.manahub.feature.decks.domain.engine.CurveTargets
import com.mmg.manahub.feature.decks.domain.engine.DeckEntry
import com.mmg.manahub.feature.decks.domain.engine.EdhrecPowerResolver
import com.mmg.manahub.feature.decks.domain.engine.LandTargetResolver
import com.mmg.manahub.feature.decks.domain.engine.isLegalForFormat
import com.mmg.manahub.feature.decks.domain.engine.ManaBaseAnalyzer
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.PlacementScorer
import com.mmg.manahub.feature.decks.domain.engine.RoleKey
import com.mmg.manahub.feature.decks.domain.engine.StrategyPick
import com.mmg.manahub.feature.decks.domain.engine.WizardPreferenceStore
import com.mmg.manahub.feature.decks.domain.engine.SynergyGraph
import com.mmg.manahub.feature.decks.domain.engine.toPin
import com.mmg.manahub.feature.decks.domain.usecase.DeckAnalysisPipeline

// Builds against the analysis objective (D1); collection-only by construction, no Scryfall/CardRepository dependency (D7/R5).

/** One card the wizard's candidate pool may consider, decoupled from whatever collection type a
 * caller's own data layer uses (`UserCardWithCard`, [com.mmg.manahub.feature.decks.domain.engine
 * .analysisv3.MockCollectionCard], …) — callers map their own type into this one. */
data class OwnedCard(val card: Card, val quantity: Int)

/** [BuildCommanderDeckUseCase]'s terminal result before persistence — see [WizardBuildResult] for
 * the shape this class produces; this wrapper adds the resolved [plan] and [pin] so a caller (the
 * write path, a test) does not need to re-resolve them. */
data class CommanderBuildOutcome(
    val result: WizardBuildResult,
    val plan: CommanderPlan,
    val pin: com.mmg.manahub.feature.decks.domain.engine.StrategyPin,
)

class BuildCommanderDeckUseCase(
    private val deckAnalysisPipeline: DeckAnalysisPipeline,
    private val crashReporter: CrashReporter,
    private val manaBaseAnalyzer: ManaBaseAnalyzer = ManaBaseAnalyzer(),
) {

    /**
     * @param ownedCollection the caller's full owned pool (commander + basics + everything else) —
     *        this class does its own filtering (legality, identity, dedupe); pass the raw owned set.
     * @param manualAdds D7/R5: the ONLY unowned cards that can enter the build; always kept.
     * @param fillLands whether the land engine runs at all (basics are unconditional once it does —
     *        R12; `false` is a test-only escape hatch to inspect the pre-land-fill non-land board).
     * @param includeNonBasicLands R8/E10: gates ONLY Stage A (owned non-basic lands) of
     *        [fillLandsV2] — basics (Stage B) always run when [fillLands] is true, regardless of
     *        this flag. Default `false`: "Include non-basic lands", off by default per the product
     *        decision (G10).
     * @param onStage Deck Wizard Commander v3 plan, Phase 6 (6.3) — fired at each build-loop stage
     *        boundary so a caller (the wizard VM) can drive a real Generating-step progress UI.
     *        Defaulted to a no-op so every pre-existing call site/test keeps compiling unchanged.
     *        Mirrors [BuildDeckFromTemplateUseCase]'s own `TemplateBuildProgress.Stage` emissions,
     *        but as a plain callback (this use case is a single suspend function, not a `Flow`).
     * @param deckId W6 Task 3 (E4) — seeds every near-tie break (non-land placement, land Stage A
     *        orderings) via [stableSeed] instead of alphabetical card name/id. Rebuilding the SAME
     *        deck is therefore byte-identical (same [deckId] -> same seed -> same tie order); two
     *        different decks with the same commander and strategy diverge. Defaulted to `""` so
     *        every pre-existing call site/test keeps compiling unchanged (an empty deckId still
     *        seeds deterministically, it just is not tied to any real deck) — every wizard launch
     *        route requires a real `deckId` nav argument (R13), so production always supplies one.
     * @param preferenceStore W6 Task 5 (E8) — when non-null, cards the user previously chose on the
     *        Choice screen get a small, capped bonus (see [PREFERENCE_BONUS]'s own KDoc) applied
     *        AFTER the real marginal gain, so it can only reorder a near-tie, never satisfy the D8
     *        filler floor or override a band need on its own. `null` (the default) is byte-for-byte
     *        inert — every pre-existing call site/test keeps compiling unchanged.
     */
    suspend operator fun invoke(
        format: DeckFormat,
        commander: Card,
        strategyPick: StrategyPick,
        identity: Set<ManaColor>,
        ownedCollection: List<OwnedCard>,
        manualAdds: List<ManualAdd> = emptyList(),
        fillLands: Boolean = true,
        includeNonBasicLands: Boolean = false,
        onStage: (CommanderBuildStage) -> Unit = {},
        deckId: String = "",
        preferenceStore: WizardPreferenceStore? = null,
    ): CommanderBuildOutcome {
        require(format.isCommanderFormat) { "BuildCommanderDeckUseCase requires a Commander-shaped format, got $format" }
        val archetypeFormat = ArchetypeFormat.of(format)
            ?: error("BuildCommanderDeckUseCase requires a Commander-shaped format, got $format")

        onStage(CommanderBuildStage.RESOLVING_PLAN)
        val plan = CommanderPlanResolver.resolve(format, commander, strategyPick, identity)
        val pin = when (strategyPick) {
            is StrategyPick.Curated -> strategyPick.strategy.toPin(strategyPick.tribe)
            StrategyPick.Custom -> com.mmg.manahub.feature.decks.domain.engine.StrategyPin(null, null, emptyList(), null)
        }
        // The tribe axis credit source during placement is plan.internalTribe (W6b) — a curated
        // tribal pick's own pin.tribe, OR (Custom only) the commander's derived tribal-lord tribe;
        // see CommanderPlan.internalTribe's own KDoc for why this must be read from the plan, not
        // re-derived from pin.tribe here (that would silently drop the Custom case). The final
        // mainboard's own dominant tribe cannot be known before the mainboard is built, and
        // re-deriving it mid-loop would require rebuilding a SynergyGraph per placement (the O(n^2)
        // cost the graph's own header explicitly avoids) — this plan-resolved tribe is a documented
        // simplification, fixed for the whole build.
        val dominantTribeAxis = plan.internalTribe?.let { "TRIBE:${it.removePrefix(com.mmg.manahub.feature.decks.domain.engine.TribeDeriver.TRIBE_PREFIX)}" }
        val dominantTribeKey = plan.internalTribe

        val landTarget = LandTargetResolver.resolve(format, plan.skeleton, profile = null, manaBaseAnalyzer = manaBaseAnalyzer)
        val (manualNonLand, manualLand) = manualAdds.partition { !BasicLandCalculator.isLand(it.card) }
        val nonLandTarget = (NON_COMMANDER_SLOTS - landTarget - manualNonLand.size).coerceAtLeast(0)

        val curveTargets = CurveTargets.forSkeleton(plan.skeleton, nonLandCount = nonLandTarget)
        val axisIdeals = SynergyGraph.axisIdeals(archetypeFormat, nonLandCount = nonLandTarget, dominantTribeAxis = dominantTribeAxis)
        val colorCount = identity.count { it != ManaColor.C }

        // G11d: an estimated manabase (even split of landTarget across identity colours) so pipFactor's shortage term is live, not permanently inert.
        val estimatedSourcesByColor: Map<ManaColor, Int> = if (colorCount > 0) {
            val perColor = landTarget / colorCount
            identity.filter { it != ManaColor.C }.associateWith { perColor }
        } else {
            emptyMap()
        }

        // ── Candidate pool (2.1) ────────────────────────────────────────────────────────────────
        val identitySymbols = identity.map { it.symbol }.toSet()
        val manualIds = manualAdds.map { it.card.scryfallId }.toSet()
        val candidateCards = ownedCollection
            .filter { it.quantity > 0 }
            .map { it.card }
            .distinctBy { it.scryfallId }
            .filter { it.scryfallId != commander.scryfallId }
            .filter { it.scryfallId !in manualIds }
            .filterNot { BasicLandCalculator.isLand(it) }
            .filter { isLegalForFormat(it, format) }
            .filter { identitySymbols.containsAll(it.colorIdentity) }
            .distinctBy { it.name }
            .sortedBy { it.scryfallId } // deterministic base order before any scoring

        val powerResolver = EdhrecPowerResolver { it.edhrecRank }
        val candidateProfiles = candidateCards.associateWith { card ->
            PlacementScorer.CandidateProfile(
                card = card,
                roleConfidence = ArchetypeRoleClassifier.classify(card),
                axisProfile = SynergyGraph.cardAxisProfile(card, archetypeFormat, dominantTribeAxis, dominantTribeKey),
                mvBucketId = PlacementScorer.mvBucketId(card),
                powerNormalized = powerResolver.powerOf(card).normalized,
            )
        }

        // ── Seed placement state with manual non-land adds (placed FIRST, D7/R5 — never dropped
        //    even off-plan; their contribution still counts toward remaining gain for the loop) ──
        onStage(CommanderBuildStage.PLACING_MANUAL_ADDS)
        var state = PlacementScorer.PlacementState()
        val placedNonLand = mutableListOf<DeckEntry>()
        manualNonLand.forEach { manual ->
            val profile = PlacementScorer.CandidateProfile(
                card = manual.card,
                roleConfidence = ArchetypeRoleClassifier.classify(manual.card),
                axisProfile = SynergyGraph.cardAxisProfile(manual.card, archetypeFormat, dominantTribeAxis, dominantTribeKey),
                mvBucketId = PlacementScorer.mvBucketId(manual.card),
                powerNormalized = 0f,
            )
            state = fold(state, profile)
            placedNonLand += DeckEntry(card = manual.card, quantity = 1, isOwned = manual.isOwned, isSideboard = false)
        }

        // ── The loop (2.3) ──────────────────────────────────────────────────────────────────────
        onStage(CommanderBuildStage.PLACING_CARDS)
        val preferredIds = preferenceStore?.preferredCardIds()?.toSet() ?: emptySet()
        val remainingCandidates = candidateCards.toMutableList()
        var iterations = 0
        val iterationCap = candidateCards.size + nonLandTarget + ITERATION_CAP_SLACK
        while (placedNonLand.size - manualNonLand.size < nonLandTarget && remainingCandidates.isNotEmpty() && iterations < iterationCap) {
            iterations++
            var best: Card? = null
            var bestGain = 0f
            remainingCandidates.forEach { card ->
                val profile = candidateProfiles.getValue(card)
                val pip = PlacementScorer.pipFactor(card, colorCount, manaBaseAnalyzer, estimatedSourcesByColor, landTarget)
                val rawGain = PlacementScorer.marginalGain(profile, state, plan, curveTargets, axisIdeals, pip) ?: return@forEach
                // W6 Task 5 (E8): the preference bonus is applied AFTER the real objective clears
                // the D8 floor, so it can only reorder a near-tie, never conjure a placement on its own.
                val gain = if (card.scryfallId in preferredIds) rawGain + PlacementScorer.PREFERENCE_BONUS else rawGain
                if (best == null || gain > bestGain ||
                    (gain == bestGain && stableSeed(deckId, card.scryfallId) < stableSeed(deckId, best!!.scryfallId))
                ) {
                    best = card
                    bestGain = gain
                }
            }
            val chosen = best ?: break
            remainingCandidates.remove(chosen)
            state = fold(state, candidateProfiles.getValue(chosen))
            placedNonLand += DeckEntry(card = chosen, quantity = 1, isOwned = true, isSideboard = false)
        }

        // W6 Task 4 (E6): sections still short of ideal at the end of placement, with the real
        // leftover candidates whose recomputed gain clears within AMBIGUITY_EPSILON of the best.
        val ambiguityGroups = plan.skeleton.roleTargets.mapNotNull { (role, target) ->
            if (role in plan.skeleton.antiRoles) return@mapNotNull null
            val remaining = target.ideal - (state.roleCounts[role] ?: 0)
            if (remaining <= 0) return@mapNotNull null
            val scored = remainingCandidates
                .filter { c -> (candidateProfiles[c]?.roleConfidence?.get(role) ?: 0f) > 0f }
                .mapNotNull { c ->
                    val pip = PlacementScorer.pipFactor(c, colorCount, manaBaseAnalyzer, estimatedSourcesByColor, landTarget)
                    PlacementScorer.marginalGain(candidateProfiles.getValue(c), state, plan, curveTargets, axisIdeals, pip)?.let { c to it }
                }
            if (scored.size < 2) return@mapNotNull null
            val best = scored.maxOf { it.second }
            val within = scored.filter { (_, gain) -> gain >= best * (1f - AMBIGUITY_EPSILON) }.map { it.first.scryfallId }
            if (within.size < 2) return@mapNotNull null
            AmbiguityGroup(sectionId = role, candidateIds = within, remainingSlots = remaining)
        }

        // ── Land fill v2 (2.4) ──────────────────────────────────────────────────────────────────
        onStage(CommanderBuildStage.FILLING_LANDS)
        val remainingLandSlots = (landTarget - manualLand.sumOf { 1 }).coerceAtLeast(0)
        val landEntries = mutableListOf<DeckEntry>()
        manualLand.forEach { landEntries += DeckEntry(card = it.card, quantity = 1, isOwned = it.isOwned, isSideboard = false) }

        if (fillLands && remainingLandSlots > 0) {
            landEntries += fillLandsV2(
                identity = identity,
                colorCount = colorCount,
                landTarget = landTarget,
                remainingLandSlots = remainingLandSlots,
                nonLandMainboard = placedNonLand,
                commander = commander,
                ownedCollection = ownedCollection,
                usedNames = (manualNonLand.map { it.card.name } + manualLand.map { it.card.name } + placedNonLand.map { it.card.name }).toMutableSet(),
                archetypeFormat = archetypeFormat,
                includeNonBasicLands = includeNonBasicLands,
                deckId = deckId,
            )
        }

        val commanderEntry = DeckEntry(card = commander, quantity = 1, isOwned = true, isSideboard = false)
        val fullMainboard = listOf(commanderEntry) + placedNonLand + landEntries

        // ── Verify + refine (2.5) ───────────────────────────────────────────────────────────────
        onStage(CommanderBuildStage.VERIFYING_AND_REFINING)
        var health = analyze(fullMainboard, format, commander, pin)
        var analysis = health?.analysis
        if (analysis == null || hasBlocker(analysis)) {
            crashReporter.log("deck_wizard_blocker_after_build")
            crashReporter.setCustomKey("deck_wizard_blocker_commander", commander.name)
            crashReporter.recordException(IllegalStateException("[BuildCommanderDeckUseCase] deck_wizard_blocker_after_build: commander=${commander.name} format=$format"))
        }

        var refinementSwaps = 0
        var finalNonLand: List<DeckEntry> = placedNonLand
        if (analysis != null) {
            val refined = refine(
                analysis = analysis,
                nonLandMainboard = finalNonLand,
                manualIds = manualIds,
                remainingCandidates = remainingCandidates,
                candidateProfiles = candidateProfiles,
                landEntries = landEntries,
                commanderEntry = commanderEntry,
                format = format,
                commander = commander,
                pin = pin,
            )
            finalNonLand = refined.nonLand
            refinementSwaps = refined.swaps
            if (refined.swaps > 0) {
                health = analyze(listOf(commanderEntry) + finalNonLand + landEntries, format, commander, pin)
                analysis = health?.analysis ?: analysis
            }
        }

        val finalAnalysis = checkNotNull(analysis) { "DeckAnalysisPipeline.analyze returned no analysis for a Commander build" }

        val gapSections = finalAnalysis.pillars.flatMap { it.sections }
            .filter { section -> val min = section.min; min != null && section.current < min }

        val fillStats = WizardFillStats(
            placedByWizard = finalNonLand.size - manualNonLand.size,
            placedManual = manualNonLand.size,
            lands = landEntries.sumOf { it.quantity },
        )

        val result = WizardBuildResult(
            entries = listOf(commanderEntry) + finalNonLand + landEntries,
            analysis = finalAnalysis,
            gapSections = gapSections,
            fillStats = fillStats,
            refinementSwaps = refinementSwaps,
            ambiguityGroups = ambiguityGroups,
        )
        onStage(CommanderBuildStage.DONE)
        return CommanderBuildOutcome(result, plan, pin)
    }

    // ── Write path (2.6, D12/D13) ──────────────────────────────────────────────────────────────

    /**
     * Persists [outcome]'s cards + pin into [deckId] in ONE atomic write —
     * [DeckRepository.persistCommanderBuild] (Phase 8, JOB 2 — closed the 4-separate-calls gap this
     * function used to have; see that method's KDoc) plus [DeckCardSource] provenance (D13): the
     * commander and every engine-placed card are [DeckCardSource.WIZARD]; a card whose id is in
     * [manualIds] is [DeckCardSource.USER]. Deck NAME and `commanderCardId`/`coverCardId` are
     * deliberately NOT written here — those need the deck's current [com.mmg.manahub.core.model.Deck]
     * row (via `DeckRepository.updateDeck`), which this use case is never handed (only a [deckId]
     * string); Phase 6's wizard VM already holds that row (Studio's draft) and should call
     * `updateDeck` itself alongside this method, in the same build-completion step.
     */
    suspend fun persist(
        deckRepository: DeckRepository,
        deckId: String,
        commander: Card,
        manualIds: Set<String>,
        outcome: CommanderBuildOutcome,
    ) {
        val slots = outcome.result.entries.map { entry ->
            val source = if (entry.card.scryfallId == commander.scryfallId || entry.card.scryfallId !in manualIds) {
                DeckCardSource.WIZARD
            } else {
                DeckCardSource.USER
            }
            CardSlotWrite(entry.card.scryfallId, entry.quantity, isSideboard = false, source = source)
        }
        deckRepository.persistCommanderBuild(
            deckId = deckId,
            slots = slots,
            archetypeOverride = outcome.pin.archetype?.name,
            themesOverride = outcome.pin.themes.map { it.name },
            posture = outcome.pin.posture?.name,
            tribeOverride = outcome.pin.tribe,
            strategyLocked = outcome.pin.archetype != null || outcome.pin.themes.isNotEmpty(),
        )
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────────────────────

    /** W6 Task 3 (E4): a stable, platform-independent hash of `deckId:cardId` (plain FNV-1a over
     * Unicode code points -- deliberately NOT `String.hashCode()`, to stay independent of any
     * stdlib hashing guarantee across the JVM/wasmJs targets) used to break near-ties without
     * alphabetical bias. Same [deckId] always orders the same two cards the same way (rebuilding a
     * deck is byte-identical); two different [deckId]s order them differently (variety). */
    private fun stableSeed(deckId: String, cardId: String): Long {
        var hash = 1469598103934665603L
        for (c in "$deckId:$cardId") {
            hash = hash xor c.code.toLong()
            hash *= 1099511628211L
        }
        return hash
    }

    private fun fold(state: PlacementScorer.PlacementState, profile: PlacementScorer.CandidateProfile): PlacementScorer.PlacementState {
        val roleCounts = state.roleCounts.toMutableMap()
        profile.roleConfidence.forEach { (role, confidence) ->
            if (confidence > 0f) roleCounts[role] = (roleCounts[role] ?: 0) + 1
        }
        val producers = state.axisProducerCounts.toMutableMap()
        profile.axisProfile.produces.forEach { (axis, confidence) -> if (confidence > 0f) producers[axis] = (producers[axis] ?: 0) + 1 }
        val payoffs = state.axisPayoffCounts.toMutableMap()
        profile.axisProfile.consumes.forEach { (axis, confidence) -> if (confidence > 0f) payoffs[axis] = (payoffs[axis] ?: 0) + 1 }
        val curve = state.curveBucketCounts.toMutableMap()
        curve[profile.mvBucketId] = (curve[profile.mvBucketId] ?: 0) + 1
        return PlacementScorer.PlacementState(roleCounts, producers, payoffs, curve)
    }

    private suspend fun analyze(
        mainboard: List<DeckEntry>,
        format: DeckFormat,
        commander: Card,
        pin: com.mmg.manahub.feature.decks.domain.engine.StrategyPin,
    ) = runCatching {
        deckAnalysisPipeline.analyze(
            mainboard = mainboard,
            format = format,
            commander = commander,
            archetypeOverride = pin.archetype?.name,
            themesOverride = pin.themes.map { it.name },
            tribeOverride = pin.tribe,
            postureOverride = pin.posture?.name,
            emitProgression = false,
        )
    }.getOrNull()

    private fun hasBlocker(analysis: com.mmg.manahub.feature.decks.domain.engine.DeckAnalysis): Boolean =
        analysis.pillars.any { pillar -> pillar.findings.any { it.severity == com.mmg.manahub.feature.decks.domain.engine.FindingSeverity.BLOCKER } }

    private data class RefineResult(val nonLand: List<DeckEntry>, val swaps: Int)

    /** D11: ≤ [MAX_REFINEMENT_SWAPS] swaps of a wizard-placed off-plan card for the best remaining
     * unplaced candidate, accepted only when [com.mmg.manahub.feature.decks.domain.engine
     * .DeckAnalysis.totalScore] strictly increases. The commander and manual adds are never
     * touched (this loop only ever iterates [nonLandMainboard] entries whose id is NOT in
     * [manualIds]). */
    private suspend fun refine(
        analysis: com.mmg.manahub.feature.decks.domain.engine.DeckAnalysis,
        nonLandMainboard: List<DeckEntry>,
        manualIds: Set<String>,
        remainingCandidates: MutableList<Card>,
        candidateProfiles: Map<Card, PlacementScorer.CandidateProfile>,
        landEntries: List<DeckEntry>,
        commanderEntry: DeckEntry,
        format: DeckFormat,
        commander: Card,
        pin: com.mmg.manahub.feature.decks.domain.engine.StrategyPin,
    ): RefineResult {
        val offplanIds = analysis.pillars.flatMap { it.sections }
            .filter { it.id == "offplan" }
            .flatMap { section -> section.contributions.map { it.scryfallId } }
            .toMutableList()
        if (offplanIds.isEmpty() || remainingCandidates.isEmpty()) return RefineResult(nonLandMainboard, 0)

        var current = nonLandMainboard.toMutableList()
        var currentScore = analysis.totalScore
        var swaps = 0

        val offplanQueue = offplanIds.filter { id -> id !in manualIds && current.any { it.card.scryfallId == id } }.toMutableList()
        while (swaps < MAX_REFINEMENT_SWAPS && offplanQueue.isNotEmpty() && remainingCandidates.isNotEmpty()) {
            val victimId = offplanQueue.removeAt(0)
            val victim = current.firstOrNull { it.card.scryfallId == victimId } ?: continue
            val replacement = remainingCandidates.firstOrNull() ?: break

            val candidateBoard = current.filterNot { it.card.scryfallId == victimId } + DeckEntry(replacement, 1, true, false)
            val trialHealth = analyze(listOf(commanderEntry) + candidateBoard + landEntries, format, commander, pin)
            val trialScore = trialHealth?.analysis?.totalScore
            if (trialScore != null && trialScore > currentScore) {
                current = candidateBoard.toMutableList()
                currentScore = trialScore
                remainingCandidates.remove(replacement)
                swaps++
            }
        }
        return RefineResult(current, swaps)
    }

    /** Owned non-basic lands (Stage A, gated on [includeNonBasicLands] — R8/E10) -> commander+
     * mainboard-weighted basics (Stage B, always runs — R12) -> a bounded Karsten rebalance
     * (Stage C, ≤ [KARSTEN_REBALANCE_CAP] moves) — D10, fixes F9. */
    private fun fillLandsV2(
        identity: Set<ManaColor>,
        colorCount: Int,
        landTarget: Int,
        remainingLandSlots: Int,
        nonLandMainboard: List<DeckEntry>,
        commander: Card,
        ownedCollection: List<OwnedCard>,
        usedNames: MutableSet<String>,
        archetypeFormat: ArchetypeFormat,
        includeNonBasicLands: Boolean,
        deckId: String,
    ): List<DeckEntry> {
        val identitySymbols = identity.map { it.symbol }.toSet()
        val placed = mutableListOf<DeckEntry>()
        val intensity = manaBaseAnalyzer.maxSinglePipIntensity(nonLandMainboard + DeckEntry(commander, 1, true, false))
        val sources = mutableMapOf<ManaColor, Int>()

        // ── Stage A: owned non-basic lands within identity — OFF by default (R8), skipped straight
        //    to Stage B/basics when includeNonBasicLands is false; remainingLandSlots is unchanged,
        //    basics simply absorb every slot Stage A would have used. ─────────────────────────────
        if (includeNonBasicLands) {
            val mix = ArchetypeData.landMixFor(archetypeFormat, colorCount)
            val nonBasicCap = ((1.0 - (mix.basicsRatio.start + mix.basicsRatio.endInclusive) / 2.0) * landTarget)
                .let { kotlin.math.round(it).toInt() }
                .coerceIn(0, remainingLandSlots)

            val ownedNonBasics = ownedCollection.map { it.card }
                .filter { BasicLandCalculator.isLand(it) && !BasicLandCalculator.isBasicLand(it) }
                .filter { identitySymbols.containsAll(it.colorIdentity) }
                .filter { it.name !in usedNames }
                .distinctBy { it.name }

            val colorProducers = ownedNonBasics
                .map { card -> card to manaBaseAnalyzer.producedColors(card, identity).intersect(identitySymbolsToColors(identitySymbols)) }
                .filter { it.second.isNotEmpty() }
                .sortedWith(
                    compareByDescending<Pair<Card, Set<ManaColor>>> { (_, colors) ->
                        colors.sumOf { c -> (intensity[c] ?: 0).let { need -> (need - (sources[c] ?: 0)).coerceAtLeast(0) } }
                    }.thenBy { stableSeed(deckId, it.first.scryfallId) }
                )

            var remaining = nonBasicCap
            for ((card, colors) in colorProducers) {
                if (remaining <= 0) break
                if (card.name in usedNames) continue
                usedNames += card.name
                placed += DeckEntry(card, 1, true, false)
                colors.forEach { c -> sources[c] = (sources[c] ?: 0) + 1 }
                remaining--
            }
            // Colourless / rainbow utility lands only fill LEFTOVER Stage-A budget after every colour
            // deficit above has had first claim (plan 2.4: "colourless utility lands allowed only
            // while sources stay >= need for every colour" — approximated as "only once colour fixing
            // has already had priority for the whole Stage-A cap").
            val utilityLands = ownedNonBasics
                .filter { it !in colorProducers.map { pair -> pair.first } }
                .filter { ArchetypeRoleClassifier.classify(it).isNotEmpty() }
                .sortedWith(compareBy { stableSeed(deckId, it.scryfallId) })
            for (card in utilityLands) {
                if (remaining <= 0) break
                if (card.name in usedNames) continue
                val stillShort = intensity.any { (c, need) -> need > (sources[c] ?: 0) }
                if (stillShort) continue
                usedNames += card.name
                placed += DeckEntry(card, 1, true, false)
                remaining--
            }
        }

        // ── Stage B: basics, commander pips included, Phyrexian excluded (F9) ──────────────────
        val basicSlots = remainingLandSlots - placed.size
        if (basicSlots > 0) {
            val pipsRaw = manaBaseAnalyzer.pipDistribution(nonLandMainboard + DeckEntry(commander, 1, true, false))
            val pipsByColor = pipsRaw.entries.associate { (color, count) -> color.symbol to count }
            val nonBasicDeckCards = placed.map { com.mmg.manahub.core.model.DeckCard(it.card, it.quantity) }
            val distribution = BasicLandCalculator.calculateFromPips(
                pipsByColor = pipsByColor,
                nonBasicLands = nonBasicDeckCards,
                totalLandTarget = remainingLandSlots,
                commanderIdentity = identitySymbols,
            )
            val basics = materializeBasics(distribution, ownedCollection)
            basics.forEach { (name, qty) -> sources[nameToColor(name)] = (sources[nameToColor(name)] ?: 0) + qty }
            placed += basics.mapNotNull { (name, qty) ->
                if (qty <= 0) return@mapNotNull null
                val card = resolveBasicCard(name, ownedCollection) ?: return@mapNotNull null
                DeckEntry(card, qty, true, false)
            }
        }

        // ── Stage C: bounded Karsten rebalance (<= KARSTEN_REBALANCE_CAP moves) ─────────────────
        rebalance(placed, sources, intensity, ownedCollection)

        return placed
    }

    private fun identitySymbolsToColors(symbols: Set<String>): Set<ManaColor> =
        ManaColor.entries.filter { it.symbol in symbols }.toSet()

    private fun nameToColor(name: String): ManaColor = when (name) {
        "Plains" -> ManaColor.W
        "Island" -> ManaColor.U
        "Swamp" -> ManaColor.B
        "Mountain" -> ManaColor.R
        "Forest" -> ManaColor.G
        "Wastes" -> ManaColor.C
        else -> ManaColor.C
    }

    private fun materializeBasics(distribution: com.mmg.manahub.core.model.BasicLandDistribution, ownedCollection: List<OwnedCard>): List<Pair<String, Int>> =
        listOf(
            "Plains" to distribution.plains,
            "Island" to distribution.islands,
            "Swamp" to distribution.swamps,
            "Mountain" to distribution.mountains,
            "Forest" to distribution.forests,
            "Wastes" to distribution.wastes,
        )

    /**
     * R12/E13: the ONE lookup for "the [Card] object backing basic-land [name]" — used by both
     * Stage B's materialization and Stage C's rebalance, replacing two independent copies. Basics
     * are documented as an unlimited resource (never gated by ownership): the VM boundary
     * ([com.mmg.manahub.feature.decks.presentation.wizard.DeckWizardViewModel
     * .guaranteeBasicsAvailable]) is responsible for making sure every WUBRG/Wastes basic the
     * identity needs has a real [Card] object in [ownedCollection] BEFORE this use case ever runs,
     * so a `null` here should be unreachable in production. If it ever IS null (a boundary
     * regression, an offline fetch failure that degraded silently, or a `commonTest` fixture that
     * doesn't stub every basic), this returns `null` defensively (matches the pre-existing
     * drop-this-allocation behavior — never a crash) and records a non-fatal breadcrumb so the
     * regression is observable instead of silently re-dropping basics again.
     */
    private fun resolveBasicCard(name: String, ownedCollection: List<OwnedCard>): Card? {
        val card = ownedCollection.map { it.card }.firstOrNull { it.name == name }
        if (card == null) {
            crashReporter.log("deck_wizard_basic_land_unresolved")
            crashReporter.recordException(
                IllegalStateException("[BuildCommanderDeckUseCase] resolveBasicCard: '$name' missing from ownedCollection -- R12 says this should be unreachable (the VM boundary should have guaranteed it)"),
            )
        }
        return card
    }

    /** Stage C (D10): moves up to [KARSTEN_REBALANCE_CAP] basic-land copies from the
     * MOST-oversupplied colour to the MOST-undersupplied one, mutating [placed]/[sources] in
     * place. Stops early once no colour is short of [ManaBaseAnalyzer.requiredSources]. Never
     * touches non-basic entries (Stage A already resolved those against the same [intensity]).
     * R12/E13: a missing [resolveBasicCard] for the SHORT colour skips only that move
     * (`return@repeat`) rather than aborting rebalancing for every OTHER colour — this should be
     * unreachable in production (see [resolveBasicCard]'s own KDoc) but stays defensive against a
     * partial-fetch failure rather than compounding it into a total rebalance abort. */
    private fun rebalance(
        placed: MutableList<DeckEntry>,
        sources: MutableMap<ManaColor, Int>,
        intensity: Map<ManaColor, Int>,
        ownedCollection: List<OwnedCard>,
    ) {
        val totalLands = placed.sumOf { it.quantity }
        repeat(KARSTEN_REBALANCE_CAP) {
            val shortages = intensity.mapNotNull { (color, need) ->
                if (need <= 0) return@mapNotNull null
                val required = manaBaseAnalyzer.requiredSources(need, totalLands)
                val have = sources[color] ?: 0
                if (have < required) color to (required - have) else null
            }
            if (shortages.isEmpty()) return
            val shortColor = shortages.maxByOrNull { it.second }?.first ?: return
            val excessColor = sources.entries
                .filter { (color, count) -> color != shortColor && count > (intensity[color]?.let { manaBaseAnalyzer.requiredSources(it, totalLands) } ?: 0) }
                .maxByOrNull { it.value }?.key ?: return

            val excessBasicName = BasicLandCalculator.LAND_FOR_COLOR[excessColor.symbol]
            val shortBasicName = BasicLandCalculator.LAND_FOR_COLOR[shortColor.symbol] ?: return
            val excessEntryIndex = placed.indexOfFirst { it.card.name == excessBasicName && BasicLandCalculator.isBasicLand(it.card) && it.quantity > 0 }
            if (excessEntryIndex < 0) return
            val shortCard = resolveBasicCard(shortBasicName, ownedCollection) ?: return@repeat

            val excessEntry = placed[excessEntryIndex]
            placed[excessEntryIndex] = excessEntry.copy(quantity = excessEntry.quantity - 1)
            sources[excessColor] = (sources[excessColor] ?: 1) - 1
            val shortEntryIndex = placed.indexOfFirst { it.card.name == shortBasicName }
            if (shortEntryIndex >= 0) {
                placed[shortEntryIndex] = placed[shortEntryIndex].copy(quantity = placed[shortEntryIndex].quantity + 1)
            } else {
                placed += DeckEntry(shortCard, 1, true, false)
            }
            sources[shortColor] = (sources[shortColor] ?: 0) + 1
            if (placed[excessEntryIndex].quantity <= 0) placed.removeAt(excessEntryIndex)
        }
    }

    companion object {
        /** Commander formats: 100 total cards including the commander -> 99 non-commander slots. */
        private const val NON_COMMANDER_SLOTS = 99
        private const val ITERATION_CAP_SLACK = 20
        private const val MAX_REFINEMENT_SWAPS = 8
        private const val KARSTEN_REBALANCE_CAP = 5

        /** W6 Task 4 (E6): a candidate within this relative fraction of the section's best remaining
         * gain counts as a genuine alternative, not a clear loser. Judgment call, verified by hand
         * against [com.mmg.manahub.feature.decks.domain.engine.analysisv3.MockCollectionRich]: wide
         * enough to surface the plan's own worked example (7 plausible cards for the last 2 Removal
         * slots), narrow enough that a typical build does not ask dozens of questions. */
        const val AMBIGUITY_EPSILON = 0.15f
    }
}
