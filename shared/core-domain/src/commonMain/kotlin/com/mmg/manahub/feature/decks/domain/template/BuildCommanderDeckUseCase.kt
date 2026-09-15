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

/**
 * W7 Task 0 (7.0) — the non-land placement loop's output BEFORE land fill/verify/refine/persist,
 * carrying everything [BuildCommanderDeckUseCase.finalize] needs to complete the build. Every slot
 * in [placedNonLand] that belongs to one of [ambiguityGroups] is currently occupied by the engine's
 * own seeded pick (a tentative default, per [tentativeByRole]) — the SAME card a single-shot build
 * would keep — so a caller that never resolves anything gets a byte-identical result to the old
 * one-pass build. Resolving a group only ever swaps ITS OWN tentative slot(s); no other card in the
 * board is touched, which is what fixes the "no room" defect the old after-the-fact ambiguity
 * detection had (see [BuildCommanderDeckUseCase.buildWithGroups]'s own KDoc).
 */
data class CommanderDraftBuild(
    val format: DeckFormat,
    val commander: Card,
    val identity: Set<ManaColor>,
    val plan: CommanderPlan,
    val pin: com.mmg.manahub.feature.decks.domain.engine.StrategyPin,
    val archetypeFormat: ArchetypeFormat,
    val placedNonLand: List<DeckEntry>,
    val manualNonLandCount: Int,
    val manualIds: Set<String>,
    val manualLand: List<DeckEntry>,
    val landTarget: Int,
    val remainingLandSlots: Int,
    val colorCount: Int,
    val ownedCollection: List<OwnedCard>,
    val includeNonBasicLands: Boolean,
    val deckId: String,
    /** Cards never placed by the loop — the ONLY pool [finalize] may still place from (refine, or a
     * user's resolution swap), so a swap can never evict an unrelated already-placed card. */
    val remainingCandidates: List<Card>,
    val candidateProfiles: Map<Card, PlacementScorer.CandidateProfile>,
    /** Every candidate ever scored, by id — resolves an [AmbiguityGroup.candidateIds] entry (or a
     * user's chosen replacement id) back to its [Card] for [finalize]'s swap. */
    val candidatesById: Map<String, Card>,
    /** [RoleKey] -> the scryfallIds of [placedNonLand] slots currently holding a tentative default
     * for that role, in placement order — [finalize] replaces the first N of these (N = however many
     * ids a resolution supplies) with the caller's chosen replacements; the rest keep their default. */
    val tentativeByRole: Map<RoleKey, List<String>>,
    val ambiguityGroups: List<AmbiguityGroup>,
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
        // W7 Task 0 (7.0): the single-shot path is just buildWithGroups -> finalize with no
        // resolutions, i.e. every tentative default stands -- this is what makes "finalizing with
        // the engine's own picks equals the single-shot build" true BY CONSTRUCTION, not by a
        // separate equality test happening to pass.
        val draft = buildWithGroups(
            format = format,
            commander = commander,
            strategyPick = strategyPick,
            identity = identity,
            ownedCollection = ownedCollection,
            manualAdds = manualAdds,
            includeNonBasicLands = includeNonBasicLands,
            onStage = onStage,
            deckId = deckId,
            preferenceStore = preferenceStore,
        )
        return finalize(draft, resolutions = emptyMap(), fillLands = fillLands, onStage = onStage)
    }

    /**
     * W7 Task 0 (7.0) — runs plan resolution, the candidate pool, and the non-land placement loop
     * ONLY (no land fill, no verify/refine, no persist); returns a [CommanderDraftBuild] for
     * [finalize] to complete. This REPLACES the old defect where `ambiguityGroups` were computed
     * AFTER the whole loop had already filled every non-land slot with other cards, so a group like
     * "pick 2 of these 7 Removal" had no room left — honouring the user's pick would have meant
     * evicting an unrelated card. Ambiguity is now detected LIVE, at the exact iteration a slot is
     * decided: when the chosen card fills a role still short of its ideal AND at least one other
     * still-unplaced candidate for that same role clears within [AMBIGUITY_EPSILON] of its gain, the
     * chosen card's OWN slot is marked tentative for that role (its own KDoc). The chosen card still
     * gets placed immediately (seeded variety, E4, stays real) — it is simply flagged as swappable.
     */
    suspend fun buildWithGroups(
        format: DeckFormat,
        commander: Card,
        strategyPick: StrategyPick,
        identity: Set<ManaColor>,
        ownedCollection: List<OwnedCard>,
        manualAdds: List<ManualAdd> = emptyList(),
        includeNonBasicLands: Boolean = false,
        onStage: (CommanderBuildStage) -> Unit = {},
        deckId: String = "",
        preferenceStore: WizardPreferenceStore? = null,
    ): CommanderDraftBuild {
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
        // W7 Task 0 (7.0): per-role tentative-slot tracking, live during the loop -- see
        // CommanderDraftBuild.tentativeByRole's KDoc for why this replaces the old after-the-fact
        // (and therefore roomless) ambiguity computation.
        val tentativeSlotIdsByRole = mutableMapOf<RoleKey, MutableList<String>>()
        val tentativeAlternatesByRole = mutableMapOf<RoleKey, MutableSet<String>>()
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
                // W6c (E4/R9): an exact-float-tie check almost never fires against live scored data
                // (see G11's own root-cause note), so real variety needs a RELATIVE near-tie band,
                // not just an equality check. `currentBest` is snapshotted before the branch so the
                // seed comparison below always reads the candidate that held `best` at the START of
                // this decision, never one just written by an earlier branch in the same call.
                val currentBest = best
                when {
                    currentBest == null -> { best = card; bestGain = gain }
                    gain > bestGain * (1f + NEAR_TIE_BAND) -> { best = card; bestGain = gain }
                    gain > bestGain * (1f - NEAR_TIE_BAND) -> {
                        // Near-tie: the running bestGain stays anchored to the STRONGEST gain seen
                        // in the band (so the band doesn't drift downward pick after pick), but the
                        // WINNER among the tied candidates is decided by the seed, not by gain order.
                        if (gain > bestGain) bestGain = gain
                        if (stableSeed(deckId, card.scryfallId) < stableSeed(deckId, currentBest.scryfallId)) best = card
                    }
                    else -> Unit // clearly worse than the current best -- not a candidate for this slot
                }
            }
            val chosen = best ?: break
            val chosenProfile = candidateProfiles.getValue(chosen)

            // W7 Task 0 (7.0): does THIS slot fill a role still short of ideal, with a genuine
            // still-unplaced alternative at THIS exact decision point? First eligible role wins (a
            // card rarely ties for two roles at once; documented simplification, see the class KDoc).
            val tentativeRole = plan.skeleton.roleTargets.keys.firstOrNull { role ->
                role !in plan.skeleton.antiRoles &&
                    (chosenProfile.roleConfidence[role] ?: 0f) > 0f &&
                    (state.roleCounts[role] ?: 0) < plan.skeleton.roleTargets.getValue(role).ideal
            }
            if (tentativeRole != null) {
                val alternates = remainingCandidates.asSequence()
                    .filter { it != chosen }
                    .filter { c -> (candidateProfiles[c]?.roleConfidence?.get(tentativeRole) ?: 0f) > 0f }
                    .mapNotNull { c ->
                        val pip = PlacementScorer.pipFactor(c, colorCount, manaBaseAnalyzer, estimatedSourcesByColor, landTarget)
                        val rawGain = PlacementScorer.marginalGain(candidateProfiles.getValue(c), state, plan, curveTargets, axisIdeals, pip) ?: return@mapNotNull null
                        val gain = if (c.scryfallId in preferredIds) rawGain + PlacementScorer.PREFERENCE_BONUS else rawGain
                        c.scryfallId to gain
                    }
                    .filter { (_, gain) -> gain >= bestGain * (1f - AMBIGUITY_EPSILON) }
                    .map { it.first }
                    .toList()
                if (alternates.isNotEmpty()) {
                    tentativeSlotIdsByRole.getOrPut(tentativeRole) { mutableListOf() } += chosen.scryfallId
                    tentativeAlternatesByRole.getOrPut(tentativeRole) { mutableSetOf() } += alternates
                }
            }

            remainingCandidates.remove(chosen)
            state = fold(state, chosenProfile)
            placedNonLand += DeckEntry(card = chosen, quantity = 1, isOwned = true, isSideboard = false)
        }

        // Alternates recorded mid-loop can themselves get placed later (for a DIFFERENT role) --
        // only a card that is STILL unplaced when the whole loop ends is a genuinely available
        // swap-in, so the final candidate pool is the filter, not the snapshot taken at record time.
        val finalRemainingIds = remainingCandidates.map { it.scryfallId }.toSet()
        val ambiguityGroups = tentativeAlternatesByRole.mapNotNull { (role, altIds) ->
            val slots = tentativeSlotIdsByRole[role] ?: return@mapNotNull null
            val available = altIds.filter { it in finalRemainingIds }
            if (available.size < 2) return@mapNotNull null
            AmbiguityGroup(sectionId = role, candidateIds = available.sorted(), remainingSlots = slots.size)
        }

        val remainingLandSlots = (landTarget - manualLand.sumOf { 1 }).coerceAtLeast(0)
        return CommanderDraftBuild(
            format = format,
            commander = commander,
            identity = identity,
            plan = plan,
            pin = pin,
            archetypeFormat = archetypeFormat,
            placedNonLand = placedNonLand,
            manualNonLandCount = manualNonLand.size,
            manualIds = manualIds,
            manualLand = manualLand.map { DeckEntry(card = it.card, quantity = 1, isOwned = it.isOwned, isSideboard = false) },
            landTarget = landTarget,
            remainingLandSlots = remainingLandSlots,
            colorCount = colorCount,
            ownedCollection = ownedCollection,
            includeNonBasicLands = includeNonBasicLands,
            deckId = deckId,
            remainingCandidates = remainingCandidates,
            candidateProfiles = candidateProfiles,
            candidatesById = candidateCards.associateBy { it.scryfallId },
            tentativeByRole = tentativeSlotIdsByRole,
            ambiguityGroups = ambiguityGroups,
        )
    }

    /**
     * W7 Task 0 (7.0) — completes a [CommanderDraftBuild]: applies [resolutions] (a swap within the
     * SAME tentative slot(s) only, never touching any other card — see [CommanderDraftBuild]'s own
     * KDoc), then runs land fill, verify/refine, and produces the final [WizardBuildResult]. Does
     * NOT persist — the caller (the wizard VM) still calls [persist] itself, in the SAME single
     * atomic transaction as before (W7 Task 2/E11: only WHEN it is called moved, to Choice-screen
     * resolution time, not the mechanism).
     *
     * @param resolutions [RoleKey] -> the user's FINAL selection for that role: up to
     *        `remainingSlots` ids drawn from [CommanderDraftBuild.tentativeByRole]'s own ids for that
     *        role UNION the group's own [AmbiguityGroup.candidateIds] — i.e. "which cards should end
     *        up occupying this role's swappable slots", not merely "which replacements to apply". An
     *        id in the selection that already IS a tentative default for the role stays in its own
     *        slot; a tentative default absent from the selection is replaced, one-for-one, by the
     *        selection's chosen alternatives (an alternative is any selected id that is not itself a
     *        tentative default). Selecting fewer than `remainingSlots` ids never shrinks the deck —
     *        any tentative slot with no replacement to fill it keeps the engine's own default. An id
     *        outside the tentative-∪-candidateIds union is dropped defensively rather than applied. An
     *        empty (or partially-empty) map is exactly "let the wizard finish": every unresolved slot
     *        stays at its seeded default, which is what makes this call byte-identical to the
     *        single-shot [invoke] path when [resolutions] is empty.
     */
    suspend fun finalize(
        draft: CommanderDraftBuild,
        resolutions: Map<RoleKey, List<String>> = emptyMap(),
        fillLands: Boolean = true,
        onStage: (CommanderBuildStage) -> Unit = {},
    ): CommanderBuildOutcome {
        val groupsByRole = draft.ambiguityGroups.associateBy { it.sectionId }
        val placedNonLand = draft.placedNonLand.toMutableList()
        val remainingCandidates = draft.remainingCandidates.toMutableList()
        resolutions.forEach { (role, chosenIds) ->
            val group = groupsByRole[role] ?: return@forEach
            val tentativeSlots = draft.tentativeByRole[role] ?: return@forEach
            val tentativeSlotSet = tentativeSlots.toSet()
            val unionIds = tentativeSlotSet + group.candidateIds
            // The user's final selection for this role, capped to how many swappable slots it
            // actually has — an id outside tentative-∪-alternatives is dropped, never applied.
            val selection = chosenIds.filter { it in unionIds }.distinct().take(tentativeSlots.size)
            val selectionSet = selection.toSet()
            // Tentative defaults the user did NOT keep, in their original slot order — these are the
            // ONLY slots a replacement may land in (a kept default never moves).
            val droppedSlots = tentativeSlots.filterNot { it in selectionSet }
            // Selected ids that are not themselves a tentative default -- the alternatives the user
            // actually chose, in the order they appeared in the resolution.
            val newAlternativeIds = selection.filterNot { it in tentativeSlotSet }
            droppedSlots.forEachIndexed { index, tentativeId ->
                val replacementId = newAlternativeIds.getOrNull(index) ?: return@forEachIndexed
                val replacement = draft.candidatesById[replacementId] ?: return@forEachIndexed
                val slotIndex = placedNonLand.indexOfFirst { it.card.scryfallId == tentativeId }
                if (slotIndex >= 0) {
                    placedNonLand[slotIndex] = DeckEntry(card = replacement, quantity = 1, isOwned = true, isSideboard = false)
                }
                remainingCandidates.removeAll { it.scryfallId == replacementId }
            }
        }

        // ── Land fill v2 (2.4) ──────────────────────────────────────────────────────────────────
        onStage(CommanderBuildStage.FILLING_LANDS)
        val landEntries = mutableListOf<DeckEntry>()
        landEntries += draft.manualLand
        if (fillLands && draft.remainingLandSlots > 0) {
            landEntries += fillLandsV2(
                identity = draft.identity,
                colorCount = draft.colorCount,
                landTarget = draft.landTarget,
                remainingLandSlots = draft.remainingLandSlots,
                nonLandMainboard = placedNonLand,
                commander = draft.commander,
                ownedCollection = draft.ownedCollection,
                usedNames = (placedNonLand.map { it.card.name } + draft.manualLand.map { it.card.name }).toMutableSet(),
                archetypeFormat = draft.archetypeFormat,
                includeNonBasicLands = draft.includeNonBasicLands,
                deckId = draft.deckId,
            )
        }

        val commanderEntry = DeckEntry(card = draft.commander, quantity = 1, isOwned = true, isSideboard = false)
        val fullMainboard = listOf(commanderEntry) + placedNonLand + landEntries

        // ── Verify + refine (2.5) ───────────────────────────────────────────────────────────────
        onStage(CommanderBuildStage.VERIFYING_AND_REFINING)
        var health = analyze(fullMainboard, draft.format, draft.commander, draft.pin)
        var analysis = health?.analysis
        if (analysis == null || hasBlocker(analysis)) {
            crashReporter.log("deck_wizard_blocker_after_build")
            crashReporter.setCustomKey("deck_wizard_blocker_commander", draft.commander.name)
            crashReporter.recordException(IllegalStateException("[BuildCommanderDeckUseCase] deck_wizard_blocker_after_build: commander=${draft.commander.name} format=${draft.format}"))
        }

        var refinementSwaps = 0
        var finalNonLand: List<DeckEntry> = placedNonLand
        if (analysis != null) {
            val refined = refine(
                analysis = analysis,
                nonLandMainboard = finalNonLand,
                manualIds = draft.manualIds,
                remainingCandidates = remainingCandidates,
                candidateProfiles = draft.candidateProfiles,
                landEntries = landEntries,
                commanderEntry = commanderEntry,
                format = draft.format,
                commander = draft.commander,
                pin = draft.pin,
            )
            finalNonLand = refined.nonLand
            refinementSwaps = refined.swaps
            if (refined.swaps > 0) {
                health = analyze(listOf(commanderEntry) + finalNonLand + landEntries, draft.format, draft.commander, draft.pin)
                analysis = health?.analysis ?: analysis
            }
        }

        val finalAnalysis = checkNotNull(analysis) { "DeckAnalysisPipeline.analyze returned no analysis for a Commander build" }

        val gapSections = finalAnalysis.pillars.flatMap { it.sections }
            .filter { section -> val min = section.min; min != null && section.current < min }

        val fillStats = WizardFillStats(
            placedByWizard = finalNonLand.size - draft.manualNonLandCount,
            placedManual = draft.manualNonLandCount,
            lands = landEntries.sumOf { it.quantity },
        )

        val result = WizardBuildResult(
            entries = listOf(commanderEntry) + finalNonLand + landEntries,
            analysis = finalAnalysis,
            gapSections = gapSections,
            fillStats = fillStats,
            refinementSwaps = refinementSwaps,
            ambiguityGroups = draft.ambiguityGroups,
        )
        onStage(CommanderBuildStage.DONE)
        return CommanderBuildOutcome(result, draft.plan, draft.pin)
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

    /** W6c (E4/R9): descending-by-[keyOf], but items whose key sits within [NEAR_TIE_BAND] of the
     * top value of their own cluster are treated as a near-tie and ordered by [stableSeed] instead
     * -- the one-time-sort counterpart to the main placement loop's inline near-tie band above,
     * used by [fillLandsV2]'s Stage A ordering so seeded variety applies to every ordering the seed
     * governs, not just the non-land loop. */
    private fun <T> nearTieOrdered(items: List<T>, deckId: String, idOf: (T) -> String, keyOf: (T) -> Double): List<T> {
        if (items.size <= 1) return items
        val sorted = items.sortedByDescending(keyOf)
        val result = ArrayList<T>(items.size)
        var i = 0
        while (i < sorted.size) {
            val top = keyOf(sorted[i])
            val band = top * NEAR_TIE_BAND
            var j = i + 1
            while (j < sorted.size && (top - keyOf(sorted[j])) <= band) j++
            result += sorted.subList(i, j).sortedBy { stableSeed(deckId, idOf(it)) }
            i = j
        }
        return result
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

            val colorProducers = nearTieOrdered(
                items = ownedNonBasics
                    .map { card -> card to manaBaseAnalyzer.producedColors(card, identity).intersect(identitySymbolsToColors(identitySymbols)) }
                    .filter { it.second.isNotEmpty() },
                deckId = deckId,
                idOf = { (card, _) -> card.scryfallId },
                keyOf = { (_, colors) ->
                    colors.sumOf { c -> (intensity[c] ?: 0).let { need -> (need - (sources[c] ?: 0)).coerceAtLeast(0) } }.toDouble()
                },
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

        /** W6c (E4/R9): candidates whose marginal gain sits within this RELATIVE band of the
         * running best are a near-tie, broken by [stableSeed] instead of raw gain -- this is what
         * makes E4's seeded variety real against live, non-integer scored data, where an exact
         * float tie almost never happens (G11's own root-cause note). Deliberately SMALLER than
         * [AMBIGUITY_EPSILON]: a near-tie the wizard silently auto-resolves must be narrower than a
         * near-tie worth asking the user about, or this would auto-decide cases E6's ambiguity
         * detector is supposed to surface as a question. Chosen from harness evidence trading off
         * variety (Jaccard card-overlap between two different `deckId` builds of the same
         * commander+strategy) against the real-collection score distribution -- see
         * docs/plans/deck-wizard-commander-v4-progress.md's W6c entry for the measured curve. */
        const val NEAR_TIE_BAND = 0.12f
    }
}
