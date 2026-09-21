package com.mmg.manahub.feature.decks.domain.template
// COMMENTS_REVIEWED: 2026-09-16

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
import com.mmg.manahub.feature.decks.domain.engine.BasicLandPlanner
import com.mmg.manahub.feature.decks.domain.engine.BuildAnchor
import com.mmg.manahub.feature.decks.domain.engine.CardSection
import com.mmg.manahub.feature.decks.domain.engine.WizardPlan
import com.mmg.manahub.feature.decks.domain.engine.WizardPlanResolver
import com.mmg.manahub.feature.decks.domain.engine.CopyPolicy
import com.mmg.manahub.feature.decks.domain.engine.CurveTargets
import com.mmg.manahub.feature.decks.domain.engine.DeckEntry
import com.mmg.manahub.feature.decks.domain.engine.EdhrecPowerResolver
import com.mmg.manahub.feature.decks.domain.engine.LandTargetResolver
import com.mmg.manahub.feature.decks.domain.engine.commanderOrNull
import com.mmg.manahub.feature.decks.domain.engine.isLegalForFormat
import com.mmg.manahub.feature.decks.domain.engine.ManaBaseAnalyzer
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.PlacementScorer
import com.mmg.manahub.feature.decks.domain.engine.RoleKey
import com.mmg.manahub.feature.decks.domain.engine.StrategyPick
import com.mmg.manahub.feature.decks.domain.engine.StrategyPin
import com.mmg.manahub.feature.decks.domain.engine.TribeDeriver
import com.mmg.manahub.feature.decks.domain.engine.WizardPreferenceStore
import com.mmg.manahub.feature.decks.domain.engine.SynergyGraph
import com.mmg.manahub.feature.decks.domain.engine.DeckAnalysis
import com.mmg.manahub.feature.decks.domain.engine.FindingSeverity
import com.mmg.manahub.feature.decks.domain.engine.toPin
import com.mmg.manahub.feature.decks.domain.usecase.DeckAnalysisPipeline

// Builds against the analysis objective (D1); collection-only by construction, no Scryfall/CardRepository dependency (D7/R5).

/** One card the wizard's candidate pool may consider, decoupled from whatever collection type a
 * caller's own data layer uses (`UserCardWithCard`, [com.mmg.manahub.feature.decks.domain.engine
 * .analysisv3.MockCollectionCard], …) — callers map their own type into this one. */
data class OwnedCard(val card: Card, val quantity: Int)

/**
 * One manually-added card (D7/1.2): the ONLY way an unowned card can enter a build (R5 — no
 * Scryfall backstop). Always kept by the placement engine, even off-plan.
 *
 * @property isOwned `false` for a card added via the Advanced Search "All cards" tab that the user
 *           does not own — persisted honestly (see [com.mmg.manahub.core.model.DeckCardSource],
 *           though provenance itself is `USER` regardless of ownership — D13).
 * @property quantity Deck Wizard 60-card wave (v6, plan §5 Phase 1.3): appended, defaulted to 1 so
 *           every pre-v6 call site compiles unchanged (Commander seeds are always exactly 1 copy).
 *           [BuildWizardDeckUseCase] clamps this to [com.mmg.manahub.feature.decks.domain.engine
 *           .CopyPolicy.maxSeedCopies] (legality only, never owned-clamped — S3) before placing it.
 */
data class ManualAdd(val card: Card, val isOwned: Boolean, val quantity: Int = 1)

/** [BuildWizardDeckUseCase]'s terminal result before persistence — see [WizardBuildResult] for
 * the shape this class produces; this wrapper adds the resolved [plan] and [pin] so a caller (the
 * write path, a test) does not need to re-resolve them. */
data class WizardBuildOutcome(
    val result: WizardBuildResult,
    val plan: WizardPlan,
    val pin: StrategyPin,
)

/** Deck Wizard 60-card wave (v6), plan §5 Phase 1.3: kept so every pre-v6 call site/test that
 * names `CommanderBuildOutcome` compiles unchanged — removed in Phase 7. */
typealias CommanderBuildOutcome = WizardBuildOutcome

/**
 * W7 Task 0 (7.0) — the non-land placement loop's output BEFORE land fill/verify/refine/persist,
 * carrying everything [BuildWizardDeckUseCase.finalize] needs to complete the build. Every slot
 * in [placedNonLand] that belongs to one of [ambiguityGroups] is currently occupied by the engine's
 * own seeded pick (a tentative default, per [tentativeByRole]) — the SAME card a single-shot build
 * would keep — so a caller that never resolves anything gets a byte-identical result to the old
 * one-pass build. Resolving a group only ever swaps ITS OWN tentative slot(s); no other card in the
 * board is touched, which is what fixes the "no room" defect the old after-the-fact ambiguity
 * detection had (see [BuildWizardDeckUseCase.buildWithGroups]'s own KDoc).
 */
data class WizardDraftBuild(
    val format: DeckFormat,
    /** Deck Wizard 60-card wave (v6): what this build is targeted around — a [BuildAnchor.Commander]
     * card or a [BuildAnchor.Sixty] colors+seeds pick. */
    val anchor: BuildAnchor,
    /** [BuildAnchor.commanderOrNull] hoisted for convenience — `null` for a [BuildAnchor.Sixty]
     * build (v6: was non-null `Card` pre-v6; every internal/external reader of this field lives
     * inside this file — verified by grep before this change). */
    val commander: Card?,
    val identity: Set<ManaColor>,
    val plan: WizardPlan,
    val pin: StrategyPin,
    val archetypeFormat: ArchetypeFormat,
    /** Deck Wizard 60-card wave (v6, S2): ONE [DeckEntry] per card, `quantity` = copies placed —
     * was one entry per COPY pre-v6 (every Commander copy count is 1, so this is a no-op shape
     * change for the Commander path: `size` and `sumOf { quantity }` coincide). */
    val placedNonLand: List<DeckEntry>,
    /** v6: copies (was entry COUNT pre-v6) — coincide for Commander (every manual add is 1 copy). */
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
    /** Deck Wizard 60-card wave (v6, S2, plan 1.3): for every id in [candidatesById], how many MORE
     * copies of it the engine could still place (`CopyPolicy.maxPlaceable` minus however many
     * copies are already in [placedNonLand] at draft time) — the headroom [finalize] clamps a
     * resolution's requested copies against. Always `<= 1` for Commander (every candidate's own
     * `maxPlaceable` is 1). */
    val candidateMaxCopies: Map<String, Int>,
    /** [RoleKey] -> the scryfallIds of [placedNonLand] slots currently holding a tentative default
     * for that role, in placement order — [finalize] replaces the first N of these (N = however many
     * ids a resolution supplies) with the caller's chosen replacements; the rest keep their default.
     * v6 (S5): ONE ID PER TENTATIVE COPY — an id may appear more than once when 2+ copies of the
     * same card were each placed into an ambiguous slot for this role (never happens for Commander,
     * where every card's own cap is 1). */
    val tentativeByRole: Map<RoleKey, List<String>>,
    val ambiguityGroups: List<AmbiguityGroup>,
    /** W8 (telemetry): how many main-loop placements were a preferred card (E8's
     * [PlacementScorer.PREFERENCE_BONUS] applied) at the moment they were chosen -- surfaced onto
     * [WizardFillStats] so the wizard can report preference-prior hit rate without re-deriving it. */
    val preferenceBonusAppliedCount: Int = 0,
    /** D4/S8 -- non-manual cards placed AFTER the main loop found no more skeleton/axis-relevant
     * candidate: has its own classified role (any [RoleKey], not necessarily one the skeleton
     * targets) but would not otherwise have cleared the loop's own D8 floor. Never populated while
     * the main loop could still place a skeleton/axis-relevant candidate. */
    val fallbackStandaloneIds: List<String> = emptyList(),
    /** D4/S8 -- the genuine last resort: no classified role and no axis edge either, placed only
     * because neither the main loop nor the Standalone fallback had anything left to offer. */
    val fallbackOffPlanIds: List<String> = emptyList(),
)

class BuildWizardDeckUseCase(
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
     * @param deckId W6 Task 3 (E4) — seeds every near-tie break (non-land placement, land Stage A
     *        orderings) via [stableSeed] instead of alphabetical card name/id. Rebuilding the SAME
     *        deck is therefore byte-identical (same [deckId] -> same seed -> same tie order); two
     *        different decks with the same commander and strategy diverge. Defaulted to `""` so
     *        every pre-existing call site/test keeps compiling unchanged.
     * @param preferenceStore W6 Task 5 (E8) — when non-null, cards the user previously chose on the
     *        Choice screen get a small, capped bonus (see [PlacementScorer.PREFERENCE_BONUS]'s own
     *        KDoc) applied AFTER the real marginal gain. `null` (the default) is byte-for-byte inert.
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
        onStage: (WizardBuildStage) -> Unit = {},
        deckId: String = "",
        preferenceStore: WizardPreferenceStore? = null,
    ): WizardBuildOutcome {
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

    /** Deck Wizard 60-card wave (v6), plan §5 Phase 1.3: the pre-v6 Commander-only signature, kept
     * so every existing call site/test compiles unchanged — a thin delegate onto
     * [BuildAnchor.Commander]. [identity] is UNUSED (same precedent as [WizardPlanResolver]'s own
     * 4-arg delegate, Phase 1.2): the sole production caller always derived it as
     * `commander.colorIdentity`, which the anchor-based overload now derives internally. */
    @Suppress("UNUSED_PARAMETER")
    suspend fun buildWithGroups(
        format: DeckFormat,
        commander: Card,
        strategyPick: StrategyPick,
        identity: Set<ManaColor>,
        ownedCollection: List<OwnedCard>,
        manualAdds: List<ManualAdd> = emptyList(),
        includeNonBasicLands: Boolean = false,
        onStage: (WizardBuildStage) -> Unit = {},
        deckId: String = "",
        preferenceStore: WizardPreferenceStore? = null,
    ): WizardDraftBuild = buildWithGroups(
        format = format,
        anchor = BuildAnchor.Commander(commander),
        strategyPick = strategyPick,
        ownedCollection = ownedCollection,
        manualAdds = manualAdds,
        includeNonBasicLands = includeNonBasicLands,
        onStage = onStage,
        deckId = deckId,
        preferenceStore = preferenceStore,
    )

    /**
     * W7 Task 0 (7.0) — runs plan resolution, the candidate pool, and the non-land placement loop
     * ONLY (no land fill, no verify/refine, no persist); returns a [WizardDraftBuild] for
     * [finalize] to complete. Ambiguity is detected LIVE, at the exact iteration a slot is decided:
     * when the chosen card fills a role still short of its ideal AND at least one other
     * still-unplaced candidate for that same role clears within [AMBIGUITY_EPSILON] of its gain, the
     * chosen card's OWN slot is marked tentative for that role. The chosen card still gets placed
     * immediately (seeded variety, E4, stays real) — it is simply flagged as swappable.
     *
     * Deck Wizard 60-card wave (v6), plan §5 Phase 1.3 (S1/S2/S5): generalized from "every card is
     * exactly 1 copy, exactly 1 role slot" to copy-aware placement. A candidate stays in
     * `remainingCandidates` (and may keep winning iterations) until its own
     * [CopyPolicy.maxPlaceable] is reached; each additional copy's marginal gain is computed with
     * `copyIndex` = however many copies are already on the board (S5's consistency credit). For
     * every Commander candidate `maxPlaceable` is exactly 1 (`CopyPolicy`'s own Commander-shaped
     * branch), so `copyIndex` is always 0 and this degenerates BYTE-IDENTICALLY to the pre-v6
     * per-card loop — asserted by the existing Commander test suites (rule 0.3), not by reasoning.
     */
    suspend fun buildWithGroups(
        format: DeckFormat,
        anchor: BuildAnchor,
        strategyPick: StrategyPick,
        ownedCollection: List<OwnedCard>,
        manualAdds: List<ManualAdd> = emptyList(),
        includeNonBasicLands: Boolean = false,
        onStage: (WizardBuildStage) -> Unit = {},
        deckId: String = "",
        preferenceStore: WizardPreferenceStore? = null,
    ): WizardDraftBuild {
        when (anchor) {
            is BuildAnchor.Commander -> require(format.isCommanderFormat) {
                "BuildWizardDeckUseCase requires a Commander-shaped format for a Commander anchor, got $format"
            }
            is BuildAnchor.Sixty -> require(!format.isCommanderFormat) {
                "BuildWizardDeckUseCase requires a non-Commander format for a Sixty anchor, got $format"
            }
        }
        val archetypeFormat = ArchetypeFormat.of(format)
            ?: error("BuildWizardDeckUseCase requires a format with an archetype skeleton, got $format")
        val commander = anchor.commanderOrNull

        onStage(WizardBuildStage.RESOLVING_PLAN)
        val plan = WizardPlanResolver.resolve(format, anchor, strategyPick)
        val pin = when (strategyPick) {
            is StrategyPick.Curated -> strategyPick.strategy.toPin(strategyPick.tribe)
            StrategyPick.Custom -> StrategyPin(null, null, emptyList(), null)
        }
        val identity: Set<ManaColor> = when (anchor) {
            is BuildAnchor.Commander -> anchor.card.colorIdentity.toManaColorSet()
            is BuildAnchor.Sixty -> anchor.identity
        }
        // The tribe axis credit source during placement is plan.internalTribe (W6b) — a curated
        // tribal pick's own pin.tribe, OR (Custom only) the anchor's derived tribal-lord tribe; see
        // WizardPlan.internalTribe's own KDoc for why this must be read from the plan, not
        // re-derived here (that would silently drop the Custom case).
        val dominantTribeAxis = plan.internalTribe?.let { "TRIBE:${it.removePrefix(TribeDeriver.TRIBE_PREFIX)}" }
        val dominantTribeKey = plan.internalTribe

        val landTarget = LandTargetResolver.resolve(format, plan.skeleton, profile = null, manaBaseAnalyzer = manaBaseAnalyzer)
        val (manualNonLandRaw, manualLandRaw) = manualAdds.partition { !BasicLandCalculator.isLand(it.card) }
        // S3: manual adds are kept at the user's requested quantity, clamped ONLY by legality
        // (CopyPolicy.maxSeedCopies) -- never by ownership; the engine's OWN extra copies (the main
        // loop below) are separately capped by CopyPolicy.maxPlaceable (owned-clamped). For
        // Commander every manual add's quantity is already 1, so this clamp is a no-op there.
        fun clampedManualQuantity(manual: ManualAdd) =
            manual.quantity.coerceAtMost(CopyPolicy.maxSeedCopies(manual.card, format)).coerceAtLeast(1)
        val manualNonLand = manualNonLandRaw.map { it to clampedManualQuantity(it) }
        val manualLandClamped = manualLandRaw.map { it to clampedManualQuantity(it) }
        val manualNonLandCopies = manualNonLand.sumOf { it.second }

        val totalSlots = if (anchor is BuildAnchor.Commander) NON_COMMANDER_SLOTS else format.targetDeckSize
        val nonLandTarget = (totalSlots - landTarget - manualNonLandCopies).coerceAtLeast(0)

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

        // ── Candidate pool (2.1, S2/S3) ─────────────────────────────────────────────────────────
        val identitySymbols = identity.map { it.symbol }.toSet()
        val manualIds = manualAdds.map { it.card.scryfallId }.toSet()
        val commanderId = commander?.scryfallId
        // S2: owned quantity per NAME, summed across every printing -- computed BEFORE the
        // printing dedupe below (a user owning 2x printing A + 2x printing B of the same name owns
        // 4 for CopyPolicy's purposes, even though the pool keeps only ONE printing per name).
        val ownedByName: Map<String, Int> = ownedCollection
            .filter { it.quantity > 0 }
            .groupBy { it.card.name }
            .mapValues { (_, owned) -> owned.sumOf { it.quantity } }
        val candidateCards = ownedCollection
            .filter { it.quantity > 0 }
            .map { it.card }
            .distinctBy { it.scryfallId }
            .filter { it.scryfallId != commanderId }
            .filter { it.scryfallId !in manualIds }
            .filterNot { BasicLandCalculator.isLand(it) }
            .filter { isLegalForFormat(it, format) }
            .filter { identitySymbols.containsAll(it.colorIdentity) }
            .distinctBy { it.name } // one PRINTING per name -- copies are tracked on that printing
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
        // S2: the TOTAL copies the engine may EVER place of this candidate -- static for the whole
        // build (basics are excluded from candidateCards above, so Int.MAX_VALUE never appears).
        val maxPlaceable: Map<String, Int> = candidateCards.associate { card ->
            card.scryfallId to CopyPolicy.maxPlaceable(card, format, ownedByName[card.name])
        }

        // ── Seed placement state with manual non-land adds (placed FIRST, D7/R5 — never dropped
        //    even off-plan; their contribution still counts toward remaining gain for the loop) ──
        onStage(WizardBuildStage.PLACING_MANUAL_ADDS)
        var state = PlacementScorer.PlacementState()
        // v6 (S2): a LinkedHashMap keyed by scryfallId -- ONE entry per card, `quantity` = copies,
        // preserving first-placement order (manual adds first, then the main loop/fallback tiers)
        // the same way the pre-v6 flat one-entry-per-copy MutableList did.
        val placedNonLand = LinkedHashMap<String, DeckEntry>()
        manualNonLand.forEach { (manual, quantity) ->
            val profile = PlacementScorer.CandidateProfile(
                card = manual.card,
                roleConfidence = ArchetypeRoleClassifier.classify(manual.card),
                axisProfile = SynergyGraph.cardAxisProfile(manual.card, archetypeFormat, dominantTribeAxis, dominantTribeKey),
                mvBucketId = PlacementScorer.mvBucketId(manual.card),
                powerNormalized = 0f,
            )
            repeat(quantity) { state = fold(state, profile) }
            placedNonLand[manual.card.scryfallId] = DeckEntry(card = manual.card, quantity = quantity, isOwned = manual.isOwned, isSideboard = false)
        }

        fun copiesPlaced(id: String) = placedNonLand[id]?.quantity ?: 0
        fun totalNonLandCopiesPlaced() = placedNonLand.values.sumOf { it.quantity } - manualNonLandCopies
        fun placeCopy(card: Card) {
            val existing = placedNonLand[card.scryfallId]
            placedNonLand[card.scryfallId] = if (existing != null) {
                existing.copy(quantity = existing.quantity + 1)
            } else {
                DeckEntry(card = card, quantity = 1, isOwned = true, isSideboard = false)
            }
        }

        // ── The loop (2.3, S2/S5) ───────────────────────────────────────────────────────────────
        onStage(WizardBuildStage.PLACING_CARDS)
        val preferredIds = preferenceStore?.preferredCardIds()?.toSet() ?: emptySet()
        val remainingCandidates = candidateCards.toMutableList()
        var iterations = 0
        val iterationCap = candidateCards.sumOf { maxPlaceable.getValue(it.scryfallId) } + nonLandTarget + ITERATION_CAP_SLACK
        // W7 Task 0 (7.0): per-role tentative-slot tracking, live during the loop -- see
        // WizardDraftBuild.tentativeByRole's KDoc for why this replaces the old after-the-fact
        // (and therefore roomless) ambiguity computation.
        val tentativeSlotIdsByRole = mutableMapOf<RoleKey, MutableList<String>>()
        // W7 Task B (E4) -- the Choice screen orders a section's alternatives by marginal gain, so
        // this keeps the BEST gain ever recorded for each alternate id (an id can be re-scored at
        // multiple decision points across the loop) rather than only its membership.
        val tentativeAlternateGainsByRole = mutableMapOf<RoleKey, MutableMap<String, Float>>()
        var preferenceBonusAppliedCount = 0
        while (totalNonLandCopiesPlaced() < nonLandTarget && remainingCandidates.isNotEmpty() && iterations < iterationCap) {
            iterations++
            // S7: a candidate that would push a non-anti role past its own max is excluded outright
            // this iteration whenever a non-overflowing candidate still clears the D8 floor --
            // "loses to any on-plan alternative" rather than merely scoring lower against it. A
            // single forward pass records both signals so the selection pass below never re-scores.
            var anyNonOverflowingPositive = false
            val rawGains = HashMap<String, Float?>(remainingCandidates.size)
            val overflowFlags = HashMap<String, Boolean>(remainingCandidates.size)
            remainingCandidates.forEach { card ->
                val profile = candidateProfiles.getValue(card)
                val pip = PlacementScorer.pipFactor(card, colorCount, manaBaseAnalyzer, estimatedSourcesByColor, landTarget)
                val copyIndex = copiesPlaced(card.scryfallId)
                val gain = PlacementScorer.marginalGain(profile, state, plan, curveTargets, axisIdeals, pip, copyIndex = copyIndex)
                val overflow = PlacementScorer.causesRoleOverflow(profile, plan, state.roleCounts)
                rawGains[card.scryfallId] = gain
                overflowFlags[card.scryfallId] = overflow
                if (gain != null && gain > 0f && !overflow) anyNonOverflowingPositive = true
            }

            var best: Card? = null
            var bestGain = 0f
            remainingCandidates.forEach { card ->
                val rawGain = rawGains[card.scryfallId] ?: return@forEach
                if (overflowFlags[card.scryfallId] == true && anyNonOverflowingPositive) return@forEach
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
            if (chosen.scryfallId in preferredIds) preferenceBonusAppliedCount++
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
                        val rawGain = PlacementScorer.marginalGain(candidateProfiles.getValue(c), state, plan, curveTargets, axisIdeals, pip, copyIndex = copiesPlaced(c.scryfallId)) ?: return@mapNotNull null
                        val gain = if (c.scryfallId in preferredIds) rawGain + PlacementScorer.PREFERENCE_BONUS else rawGain
                        c.scryfallId to gain
                    }
                    .filter { (_, gain) -> gain >= bestGain * (1f - AMBIGUITY_EPSILON) }
                    .toList()
                if (alternates.isNotEmpty()) {
                    tentativeSlotIdsByRole.getOrPut(tentativeRole) { mutableListOf() } += chosen.scryfallId
                    val gainsForRole = tentativeAlternateGainsByRole.getOrPut(tentativeRole) { mutableMapOf() }
                    alternates.forEach { (id, gain) ->
                        val existing = gainsForRole[id]
                        if (existing == null || gain > existing) gainsForRole[id] = gain
                    }
                }
            }

            placeCopy(chosen)
            // S2: the candidate is removed the MOMENT it hits its own cap, so a maxed card never
            // lists as an ambiguity alternate on a later iteration -- for Commander maxPlaceable is
            // always 1, so this fires on the FIRST copy, byte-identical to the pre-v6 unconditional
            // `remainingCandidates.remove(chosen)`.
            if (copiesPlaced(chosen.scryfallId) >= (maxPlaceable[chosen.scryfallId] ?: 1)) {
                remainingCandidates.remove(chosen)
            }
            state = fold(state, chosenProfile)
        }

        // ── Fallback (D4/S8) ────────────────────────────────────────────────────────────────────
        // The loop above only ever chose a skeleton/axis-relevant candidate (the D8 floor) and, per
        // the overflow gate, never one that overflows a role while a clean alternative existed --
        // it stops the moment none remain, not because slots ran out. Fill whatever is still open
        // with the best Standalone card (any classified role, even one the skeleton never targets
        // -- AnalysisEngine's own "standalone" bucket), off-plan (no role, no edge) only once that
        // pool is exhausted too. Both tiers are recorded so the Choice screen can flag them honestly.
        val fallbackStandaloneIds = mutableListOf<String>()
        val fallbackOffPlanIds = mutableListOf<String>()
        if (totalNonLandCopiesPlaced() < nonLandTarget && remainingCandidates.isNotEmpty()) {
            val eligible = remainingCandidates.filterNot { card ->
                candidateProfiles.getValue(card).roleConfidence.any { (role, confidence) -> confidence > 0f && role in plan.skeleton.antiRoles }
            }
            val (standalonePool, offPlanPool) = eligible.partition { candidateProfiles.getValue(it).roleConfidence.isNotEmpty() }

            // S2: a Standalone/off-plan card may itself be placed up to its OWN maxPlaceable before
            // the ordering moves on to the next candidate -- for Commander that cap is always 1, so
            // this degenerates to the pre-v6 single-copy-per-card fallback loop.
            fun placeFallback(pool: List<Card>, idSink: MutableList<String>) {
                val ordered = nearTieOrdered(
                    items = pool,
                    deckId = deckId,
                    idOf = { it.scryfallId },
                    keyOf = { candidateProfiles.getValue(it).powerNormalized.toDouble() },
                )
                for (card in ordered) {
                    if (totalNonLandCopiesPlaced() >= nonLandTarget) break
                    val cap = maxPlaceable[card.scryfallId] ?: 1
                    val profile = candidateProfiles.getValue(card)
                    var placedAny = false
                    while (totalNonLandCopiesPlaced() < nonLandTarget && copiesPlaced(card.scryfallId) < cap) {
                        state = fold(state, profile)
                        placeCopy(card)
                        placedAny = true
                    }
                    if (placedAny) {
                        remainingCandidates.remove(card)
                        idSink += card.scryfallId
                    }
                }
            }
            placeFallback(standalonePool, fallbackStandaloneIds)
            if (totalNonLandCopiesPlaced() < nonLandTarget) placeFallback(offPlanPool, fallbackOffPlanIds)
        }

        // Alternates recorded mid-loop can themselves get placed later (for a DIFFERENT role) --
        // only a card that is STILL unplaced when the whole loop ends is a genuinely available
        // swap-in, so the final candidate pool is the filter, not the snapshot taken at record time.
        val finalRemainingIds = remainingCandidates.map { it.scryfallId }.toSet()
        // W7 Task B (E4): candidateIds is ordered by marginal gain (best first), the same `deckId`
        // seed breaking a gain tie -- this is the order a Choice screen renders, capped display-side.
        val ambiguityGroups = tentativeAlternateGainsByRole.mapNotNull { (role, gains) ->
            val slots = tentativeSlotIdsByRole[role] ?: return@mapNotNull null
            val available = gains.filterKeys { it in finalRemainingIds }
            if (available.size < 2) return@mapNotNull null
            val ordered = available.entries.sortedWith(
                compareByDescending<Map.Entry<String, Float>> { it.value }
                    .thenBy { stableSeed(deckId, it.key) },
            ).map { it.key }
            AmbiguityGroup(sectionId = role, candidateIds = ordered, remainingSlots = slots.size)
        }

        val manualLandEntries = manualLandClamped.map { (manual, quantity) ->
            DeckEntry(card = manual.card, quantity = quantity, isOwned = manual.isOwned, isSideboard = false)
        }
        val remainingLandSlots = (landTarget - manualLandEntries.sumOf { it.quantity }).coerceAtLeast(0)
        val candidatesById = candidateCards.associateBy { it.scryfallId }
        val candidateMaxCopies: Map<String, Int> = candidatesById.mapValues { (id, _) ->
            ((maxPlaceable[id] ?: 0) - copiesPlaced(id)).coerceAtLeast(0)
        }

        return WizardDraftBuild(
            format = format,
            anchor = anchor,
            commander = commander,
            identity = identity,
            plan = plan,
            pin = pin,
            archetypeFormat = archetypeFormat,
            placedNonLand = placedNonLand.values.toList(),
            manualNonLandCount = manualNonLandCopies,
            manualIds = manualIds,
            manualLand = manualLandEntries,
            landTarget = landTarget,
            remainingLandSlots = remainingLandSlots,
            colorCount = colorCount,
            ownedCollection = ownedCollection,
            includeNonBasicLands = includeNonBasicLands,
            deckId = deckId,
            remainingCandidates = remainingCandidates,
            candidateProfiles = candidateProfiles,
            candidatesById = candidatesById,
            candidateMaxCopies = candidateMaxCopies,
            tentativeByRole = tentativeSlotIdsByRole,
            ambiguityGroups = ambiguityGroups,
            preferenceBonusAppliedCount = preferenceBonusAppliedCount,
            fallbackStandaloneIds = fallbackStandaloneIds,
            fallbackOffPlanIds = fallbackOffPlanIds,
        )
    }

    /**
     * W7 Task 0 (7.0) — completes a [WizardDraftBuild]: applies [resolutions] (a swap within the
     * SAME tentative slot(s) only, never touching any other card — see [WizardDraftBuild]'s own
     * KDoc), then runs land fill, verify/refine, and produces the final [WizardBuildResult]. Does
     * NOT persist — the caller still calls [persist] itself.
     *
     * Deck Wizard 60-card wave (v6), plan §5 Phase 1.3 (S5): the SIGNATURE is UNCHANGED from
     * pre-v6 — still `Map<RoleKey, List<String>>` — but the semantics generalize: a REPEATED id in
     * the list means the caller wants MULTIPLE copies of that same card in the role's swappable
     * slots (Commander's own world is exactly this with no repeats, since every card's own cap is
     * 1). A same-named `finalize` overload differing ONLY in a Map value's generic type argument
     * (`Map<RoleKey, List<String>>` vs `Map<RoleKey, Map<String, Int>>`, as an earlier draft of
     * this plan literally specified) is NOT implementable here: it is both a genuine JVM platform
     * signature clash (both erase to the same `(..., Map, ...)` bytecode signature) AND a Kotlin
     * SOURCE-level overload-resolution ambiguity for any call site passing a generically-inferred
     * argument (`emptyMap()`, MockK's `any()`) — verified against this codebase's own
     * `DeckWizardViewModelTest.kt`, which mocks `finalize(any(), any(), any(), any())` at ~25 call
     * sites that would all become ambiguous. The repeated-id-list design delivers the identical
     * capability (multi-copy resolution, `BuildWizardDeckUseCaseSixtyTest`'s own case (f)) with
     * ZERO signature change, so every pre-v6 call site (including those 25) keeps compiling with
     * NO edits at all.
     *
     * Per-role algorithm (generalizes the pre-v6 contract 1:1 — see this method's own git history
     * for the pre-v6 single-copy version this specializes to when no id ever repeats):
     * `T` = `tentativeByRole[role].size` (copies); `tentativeCopies` = each id's own multiplicity
     * within that list. The caller's [chosenIds] list is read in order, each id's own occurrences
     * clamped to its remaining headroom (`candidateMaxCopies[id] + tentativeCopies[id]` — an id may
     * "keep" every one of its OWN tentative copies even with zero fresh headroom) and the running
     * total clamped to `T` (drop from the END of the caller's list order) into `desiredFlat`. Each
     * tentative slot occupant is then matched 1:1 against `desiredFlat` (consuming one instance per
     * match, so "2 copies of X tentative, 2 copies of X desired" stays fully in place); unmatched
     * tentative occupants are `droppedSlots`, unmatched desired ids are `newAlternativeIds` — paired
     * index-for-index, applied only where BOTH sides exist (a dropped slot with no available
     * alternative keeps its engine-seeded default, exactly as the pre-v6 algorithm's own
     * `getOrNull(index) ?: return@forEachIndexed` skip did). An empty [resolutions] map is exactly
     * "let the wizard finish" — every unresolved slot stays at its seeded default (byte-identical to
     * the single-shot [invoke] path).
     */
    suspend fun finalize(
        draft: WizardDraftBuild,
        resolutions: Map<RoleKey, List<String>> = emptyMap(),
        fillLands: Boolean = true,
        onStage: (WizardBuildStage) -> Unit = {},
    ): WizardBuildOutcome {
        val groupsByRole = draft.ambiguityGroups.associateBy { it.sectionId }
        val placedNonLandMap = LinkedHashMap<String, DeckEntry>()
        draft.placedNonLand.forEach { placedNonLandMap[it.card.scryfallId] = it }
        val remainingCandidates = draft.remainingCandidates.toMutableList()

        resolutions.forEach { (role, chosenIds) ->
            val group = groupsByRole[role] ?: return@forEach
            val tentativeSlots = draft.tentativeByRole[role] ?: return@forEach
            if (tentativeSlots.isEmpty()) return@forEach
            val totalSlots = tentativeSlots.size
            val tentativeCopies = tentativeSlots.groupingBy { it }.eachCount()
            val unionIds = tentativeCopies.keys + group.candidateIds.toSet()

            val desiredFlat = mutableListOf<String>()
            val perIdUsed = mutableMapOf<String, Int>()
            outer@ for (id in chosenIds) {
                if (id !in unionIds) continue
                if (desiredFlat.size >= totalSlots) break@outer
                val cap = (draft.candidateMaxCopies[id] ?: 0) + (tentativeCopies[id] ?: 0)
                val used = perIdUsed[id] ?: 0
                if (used >= cap) continue
                perIdUsed[id] = used + 1
                desiredFlat += id
            }

            // Consume one desired instance per tentative occupant it matches -- what's left over on
            // each side is the real diff (see this method's own KDoc for the full derivation).
            val remainingDesired = desiredFlat.toMutableList()
            val droppedSlots = mutableListOf<String>()
            tentativeSlots.forEach { id ->
                val idx = remainingDesired.indexOf(id)
                if (idx >= 0) remainingDesired.removeAt(idx) else droppedSlots += id
            }
            val newAlternativeIds = remainingDesired

            droppedSlots.forEachIndexed { index, tentativeId ->
                val replacementId = newAlternativeIds.getOrNull(index) ?: return@forEachIndexed
                val replacement = draft.candidatesById[replacementId] ?: return@forEachIndexed

                val current = placedNonLandMap[tentativeId]
                if (current != null) {
                    if (current.quantity > 1) {
                        placedNonLandMap[tentativeId] = current.copy(quantity = current.quantity - 1)
                    } else {
                        placedNonLandMap.remove(tentativeId)
                    }
                }

                val existingReplacement = placedNonLandMap[replacementId]
                placedNonLandMap[replacementId] = if (existingReplacement != null) {
                    existingReplacement.copy(quantity = existingReplacement.quantity + 1)
                } else {
                    DeckEntry(card = replacement, quantity = 1, isOwned = true, isSideboard = false)
                }
                val replacementCap = (draft.candidateMaxCopies[replacementId] ?: 0) + (tentativeCopies[replacementId] ?: 0)
                if ((placedNonLandMap[replacementId]?.quantity ?: 0) >= replacementCap) {
                    remainingCandidates.removeAll { it.scryfallId == replacementId }
                }
            }
        }
        val placedNonLand = placedNonLandMap.values.toList()

        // ── Land fill v2 (2.4) ──────────────────────────────────────────────────────────────────
        onStage(WizardBuildStage.FILLING_LANDS)
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
                format = draft.format,
                includeNonBasicLands = draft.includeNonBasicLands,
                deckId = draft.deckId,
            )
        }

        val commanderEntry = draft.commander?.let { DeckEntry(card = it, quantity = 1, isOwned = true, isSideboard = false) }
        val fullMainboard = listOfNotNull(commanderEntry) + placedNonLand + landEntries

        // ── Verify + refine (2.5) ───────────────────────────────────────────────────────────────
        onStage(WizardBuildStage.VERIFYING_AND_REFINING)
        var health = analyze(fullMainboard, draft.format, draft.commander, draft.pin)
        var analysis = health?.analysis
        if (analysis == null || hasBlocker(analysis)) {
            crashReporter.log("deck_wizard_blocker_after_build")
            crashReporter.setCustomKey("deck_wizard_blocker_commander", "${draft.format}_${draft.identity.size}c")
            crashReporter.recordException(IllegalStateException("[BuildWizardDeckUseCase] deck_wizard_blocker_after_build: format=${draft.format} identitySize=${draft.identity.size}"))
        }

        var refinementSwaps = 0
        var finalNonLand: List<DeckEntry> = placedNonLand
        val fallbackStandaloneIds = draft.fallbackStandaloneIds.toMutableSet()
        val fallbackOffPlanIds = draft.fallbackOffPlanIds.toMutableSet()
        if (analysis != null) {
            val ownedByName: Map<String, Int> = draft.ownedCollection
                .filter { it.quantity > 0 }
                .groupBy { it.card.name }
                .mapValues { (_, owned) -> owned.sumOf { it.quantity } }
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
                plan = draft.plan,
                ownedByName = ownedByName,
                fallbackStandaloneIds = fallbackStandaloneIds,
                fallbackOffPlanIds = fallbackOffPlanIds,
            )
            finalNonLand = refined.nonLand
            refinementSwaps = refined.swaps
            if (refined.swaps > 0) {
                health = analyze(listOfNotNull(commanderEntry) + finalNonLand + landEntries, draft.format, draft.commander, draft.pin)
                analysis = health?.analysis ?: analysis
            }
        }

        val finalAnalysis = checkNotNull(analysis) { "DeckAnalysisPipeline.analyze returned no analysis for a wizard build" }

        val gapSections = finalAnalysis.pillars.flatMap { it.sections }
            .filter { section -> val min = section.min; min != null && section.current < min }

        // D4: refine() may have swapped a fallback pick for a genuinely better replacement -- only
        // ids still on the final board are still a fallback the Choice screen needs to flag.
        val finalNonLandIds = finalNonLand.map { it.card.scryfallId }.toSet()
        val finalFallbackStandaloneIds = fallbackStandaloneIds.filter { it in finalNonLandIds }
        val finalFallbackOffPlanIds = fallbackOffPlanIds.filter { it in finalNonLandIds }

        val fillStats = WizardFillStats(
            placedByWizard = finalNonLand.sumOf { it.quantity } - draft.manualNonLandCount,
            placedManual = draft.manualNonLandCount,
            lands = landEntries.sumOf { it.quantity },
            preferenceBonusAppliedCount = draft.preferenceBonusAppliedCount,
            fallbackStandaloneCount = finalFallbackStandaloneIds.size,
            fallbackOffPlanCount = finalFallbackOffPlanIds.size,
            // v6: finalNonLand is already ONE entry per distinct name (the pool is deduped by name
            // at build time), so its own entry count IS the distinct-names count.
            distinctNames = finalNonLand.size,
            fourOfCount = finalNonLand.count { it.quantity == 4 },
        )

        val result = WizardBuildResult(
            entries = listOfNotNull(commanderEntry) + finalNonLand + landEntries,
            analysis = finalAnalysis,
            gapSections = gapSections,
            fillStats = fillStats,
            refinementSwaps = refinementSwaps,
            ambiguityGroups = draft.ambiguityGroups,
            fallbackStandaloneIds = finalFallbackStandaloneIds,
            fallbackOffPlanIds = finalFallbackOffPlanIds,
        )
        onStage(WizardBuildStage.DONE)
        return WizardBuildOutcome(result, draft.plan, draft.pin)
    }

    // ── Write path (2.6, D12/D13) ──────────────────────────────────────────────────────────────

    /** Deck Wizard 60-card wave (v6), plan §5 Phase 1.3: the pre-v6 Commander-only signature, kept
     * so every existing call site/test compiles unchanged -- a thin delegate onto
     * [BuildAnchor.Commander]. */
    suspend fun persist(
        deckRepository: DeckRepository,
        deckId: String,
        commander: Card,
        manualIds: Set<String>,
        outcome: WizardBuildOutcome,
    ) = persist(deckRepository, deckId, BuildAnchor.Commander(commander), manualIds, outcome)

    /**
     * Persists [outcome]'s cards + pin into [deckId] in ONE atomic write —
     * [DeckRepository.persistWizardBuild] plus [DeckCardSource] provenance (D13): [anchor]'s own
     * commander (if any) and every engine-placed card are [DeckCardSource.WIZARD]; a card whose id
     * is in [manualIds] is [DeckCardSource.USER]. Deck NAME and `commanderCardId`/`coverCardId` are
     * deliberately NOT written here — those need the deck's current [com.mmg.manahub.core.model.Deck]
     * row (via `DeckRepository.updateDeck`), which this use case is never handed (only a [deckId]
     * string); the wizard VM already holds that row and should call `updateDeck` itself alongside
     * this method, in the same build-completion step.
     */
    suspend fun persist(
        deckRepository: DeckRepository,
        deckId: String,
        anchor: BuildAnchor,
        manualIds: Set<String>,
        outcome: WizardBuildOutcome,
    ) {
        val commanderId = anchor.commanderOrNull?.scryfallId
        val slots = outcome.result.entries.map { entry ->
            val source = if (entry.card.scryfallId == commanderId || entry.card.scryfallId !in manualIds) {
                DeckCardSource.WIZARD
            } else {
                DeckCardSource.USER
            }
            CardSlotWrite(entry.card.scryfallId, entry.quantity, isSideboard = false, source = source)
        }
        deckRepository.persistWizardBuild(
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

    private fun List<String>.toManaColorSet(): Set<ManaColor> =
        mapNotNull { symbol -> ManaColor.entries.firstOrNull { it.symbol == symbol } }.toSet()

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
        commander: Card?,
        pin: StrategyPin,
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

    private fun hasBlocker(analysis: DeckAnalysis): Boolean =
        analysis.pillars.any { pillar -> pillar.findings.any { it.severity == FindingSeverity.BLOCKER } }

    private data class RefineResult(val nonLand: List<DeckEntry>, val swaps: Int)

    /** v6 (S2): merge-or-insert one more copy of [card] into this board (one [DeckEntry] per card
     * stays the invariant throughout [refine], same as [WizardDraftBuild.placedNonLand]). */
    private fun List<DeckEntry>.plusCopy(card: Card): List<DeckEntry> {
        val idx = indexOfFirst { it.card.scryfallId == card.scryfallId }
        return if (idx >= 0) {
            toMutableList().also { it[idx] = it[idx].copy(quantity = it[idx].quantity + 1) }
        } else {
            this + DeckEntry(card, 1, true, false)
        }
    }

    /** v6 (S2): decrement-or-remove one copy of [cardId] from this board -- the [refine] counterpart
     * to [plusCopy]. A no-op if [cardId] is not on the board. */
    private fun List<DeckEntry>.minusCopy(cardId: String): List<DeckEntry> {
        val idx = indexOfFirst { it.card.scryfallId == cardId }
        if (idx < 0) return this
        val entry = this[idx]
        return if (entry.quantity > 1) {
            toMutableList().also { it[idx] = entry.copy(quantity = entry.quantity - 1) }
        } else {
            toMutableList().also { it.removeAt(idx) }
        }
    }

    /**
     * X5 -- bounded local search, replacing the old fixed-victim-order/first-remaining-candidate
     * swap loop. Each round retargets the WORST current wizard-placed card (off-plan first, then
     * role overflow, then lowest EDHREC power as a cheap "least individually strong" tiebreak),
     * shortlists the [REFINE_TRIAL_SAMPLE] remaining candidates [PlacementScorer.marginalGain]
     * ranks highest against the board WITHOUT that victim (a fast pre-filter, pip-neutral by
     * design -- this is a ranking heuristic only), verifies each shortlisted trial through a real
     * [DeckAnalysis], and keeps whichever raises `totalScore` the most. Stops the moment a round
     * finds no improving swap (a local optimum) or [REFINE_MAX_SWAPS] is reached. The commander and
     * manual adds are never touched (only entries whose id is NOT in [manualIds] are ever a
     * victim). A swap that clears a fallback pick removes its id from
     * [fallbackStandaloneIds]/[fallbackOffPlanIds] so the Choice screen only flags what is still on
     * the final board.
     *
     * Deck Wizard 60-card wave (v6, plan §5 Phase 1.3, S10 risks): victim/replacement are now COPY
     * units, not whole entries -- a victim entry with `quantity > 1` loses exactly ONE copy
     * ([minusCopy]), and a shortlisted replacement gains exactly one MORE copy ([plusCopy]),
     * respecting [CopyPolicy.maxPlaceable] via [ownedByName] (a candidate already at its own cap on
     * the board-without-victim is excluded from the shortlist outright). For Commander every
     * `maxPlaceable` is 1, so every entry's `quantity` is always 1 and this degenerates
     * byte-identically to the pre-v6 whole-entry swap.
     */
    private suspend fun refine(
        analysis: DeckAnalysis,
        nonLandMainboard: List<DeckEntry>,
        manualIds: Set<String>,
        remainingCandidates: MutableList<Card>,
        candidateProfiles: Map<Card, PlacementScorer.CandidateProfile>,
        landEntries: List<DeckEntry>,
        commanderEntry: DeckEntry?,
        format: DeckFormat,
        commander: Card?,
        pin: StrategyPin,
        plan: WizardPlan,
        ownedByName: Map<String, Int>,
        fallbackStandaloneIds: MutableSet<String>,
        fallbackOffPlanIds: MutableSet<String>,
    ): RefineResult {
        if (remainingCandidates.isEmpty()) return RefineResult(nonLandMainboard, 0)

        var current: List<DeckEntry> = nonLandMainboard
        var currentAnalysis = analysis
        var currentScore = analysis.totalScore
        var swaps = 0

        // Curve/axis targets scale off the board's own COPY count, which stays fixed across every
        // 1-copy swap this loop ever makes -- computed once, not per round.
        val nonLandCopyCount = current.sumOf { it.quantity }
        val curveTargetsNow = CurveTargets.forSkeleton(plan.skeleton, nonLandCount = nonLandCopyCount)
        val archetypeFormat = ArchetypeFormat.of(format) ?: return RefineResult(current, 0)
        val dominantTribeAxis = plan.internalTribe?.let { "TRIBE:${it.removePrefix(TribeDeriver.TRIBE_PREFIX)}" }
        val axisIdealsNow = SynergyGraph.axisIdeals(archetypeFormat, nonLandCount = nonLandCopyCount, dominantTribeAxis = dominantTribeAxis)

        while (swaps < REFINE_MAX_SWAPS && remainingCandidates.isNotEmpty()) {
            val offplanIdsNow = currentAnalysis.pillars.flatMap { it.sections }
                .filter { it.id == "offplan" }
                .flatMap { section -> section.contributions.map { it.scryfallId } }
                .toSet()
            val roleCountsNow = ArchetypeRoleClassifier.deckRoleCounts(current)
            fun overflowCountOf(entry: DeckEntry): Int = ArchetypeRoleClassifier.classify(entry.card).keys.count { role ->
                role !in plan.skeleton.antiRoles &&
                    plan.skeleton.roleTargets[role]?.let { (roleCountsNow[role] ?: 0) > it.max } == true
            }

            val victim = current.asSequence()
                .filter { it.card.scryfallId !in manualIds }
                .sortedWith(
                    compareByDescending<DeckEntry> { if (it.card.scryfallId in offplanIdsNow) 1 else 0 }
                        .thenByDescending { overflowCountOf(it) }
                        .thenBy { candidateProfiles[it.card]?.powerNormalized ?: 1f },
                )
                .firstOrNull { it.card.scryfallId in offplanIdsNow || overflowCountOf(it) > 0 } ?: break

            val boardWithoutVictim = current.minusCopy(victim.card.scryfallId)
            val stateWithoutVictim = boardWithoutVictim.fold(PlacementScorer.PlacementState()) { acc, entry ->
                val profile = candidateProfiles[entry.card] ?: return@fold acc
                var next = acc
                repeat(entry.quantity) { next = fold(next, profile) }
                next
            }
            // A null gain is PlacementScorer's own off-plan definition (no role gain, no axis
            // gain) -- dropped here rather than sentinel-scored, so a thin pool can never swap
            // in a fresh off-plan card without it landing in fallbackOffPlanIds first. A candidate
            // already at its own maxPlaceable on boardWithoutVictim is excluded outright (v6).
            val shortlist = remainingCandidates
                .mapNotNull { candidate ->
                    val profile = candidateProfiles[candidate] ?: return@mapNotNull null
                    val alreadyOnBoard = boardWithoutVictim.firstOrNull { it.card.scryfallId == candidate.scryfallId }?.quantity ?: 0
                    val cap = CopyPolicy.maxPlaceable(candidate, format, ownedByName[candidate.name])
                    if (alreadyOnBoard >= cap) return@mapNotNull null
                    val gain = PlacementScorer.marginalGain(profile, stateWithoutVictim, plan, curveTargetsNow, axisIdealsNow, pipFactor = 1f, copyIndex = alreadyOnBoard)
                    gain?.let { candidate to it }
                }
                .sortedByDescending { it.second }
                .take(REFINE_TRIAL_SAMPLE)
                .map { it.first }
            if (shortlist.isEmpty()) break

            var bestReplacement: Card? = null
            var bestTrialScore = currentScore
            var bestTrialAnalysis: DeckAnalysis? = null
            for (replacement in shortlist) {
                val candidateBoard = boardWithoutVictim.plusCopy(replacement)
                val trialHealth = analyze(listOfNotNull(commanderEntry) + candidateBoard + landEntries, format, commander, pin)
                val trialScore = trialHealth?.analysis?.totalScore ?: continue
                if (trialScore > bestTrialScore) {
                    bestTrialScore = trialScore
                    bestReplacement = replacement
                    bestTrialAnalysis = trialHealth.analysis
                }
            }
            val replacement = bestReplacement ?: break

            current = boardWithoutVictim.plusCopy(replacement)
            val replacementQuantity = current.firstOrNull { it.card.scryfallId == replacement.scryfallId }?.quantity ?: 0
            val replacementCap = CopyPolicy.maxPlaceable(replacement, format, ownedByName[replacement.name])
            if (replacementQuantity >= replacementCap) remainingCandidates.remove(replacement)
            fallbackStandaloneIds.remove(victim.card.scryfallId)
            fallbackOffPlanIds.remove(victim.card.scryfallId)
            currentScore = bestTrialScore
            currentAnalysis = bestTrialAnalysis ?: currentAnalysis
            swaps++
        }
        return RefineResult(current, swaps)
    }

    /** Owned non-basic lands (Stage A, gated on [includeNonBasicLands] — R8/E10) -> Stage B+C
     * (commander+mainboard-weighted basics, then a bounded Karsten rebalance) delegated to
     * [BasicLandPlanner.planBasics] — D10, fixes F9; extracted (Deck Wizard UX polish plan, Run 1
     * §1.1) so Studio's land-delta suggestion can call the identical counts logic. [commander] is
     * `null` for a [BuildAnchor.Sixty] build (v6) -- every commander-pip reference below degrades to
     * `listOfNotNull(commander?.let { ... })`, an empty addition. */
    private fun fillLandsV2(
        identity: Set<ManaColor>,
        colorCount: Int,
        landTarget: Int,
        remainingLandSlots: Int,
        nonLandMainboard: List<DeckEntry>,
        commander: Card?,
        ownedCollection: List<OwnedCard>,
        usedNames: MutableSet<String>,
        archetypeFormat: ArchetypeFormat,
        format: DeckFormat,
        includeNonBasicLands: Boolean,
        deckId: String,
    ): List<DeckEntry> {
        val identitySymbols = identity.map { it.symbol }.toSet()
        val placed = mutableListOf<DeckEntry>()
        val commanderPipEntry = listOfNotNull(commander?.let { DeckEntry(it, 1, true, false) })
        val intensity = manaBaseAnalyzer.maxSinglePipIntensity(nonLandMainboard + commanderPipEntry)
        val sources = mutableMapOf<ManaColor, Int>()

        // ── Stage A: owned non-basic lands within identity — OFF by default (R8), skipped straight
        //    to Stage B/basics when includeNonBasicLands is false; remainingLandSlots is unchanged,
        //    basics simply absorb every slot Stage A would have used. ─────────────────────────────
        if (includeNonBasicLands) {
            val mix = ArchetypeData.landMixFor(archetypeFormat, colorCount)
            val nonBasicCap = ((1.0 - (mix.basicsRatio.start + mix.basicsRatio.endInclusive) / 2.0) * landTarget)
                .let { kotlin.math.round(it).toInt() }
                .coerceIn(0, remainingLandSlots)

            // Deck Wizard 60-card wave (v6), Phase 6.2 fix: a rotating 60-card format (Standard/
            // Pioneer) must never receive an owned non-basic land that isn't legal there -- this
            // filter was missing entirely, so an opted-in includeNonBasicLands=true build could place
            // e.g. a Legacy-only dual into a Standard deck, tripping AnalysisEngine's own
            // Finding.IllegalCard BLOCKER post-build. Caught by the Phase 6.2 real-collection Sixty
            // harness (0/17 Standard specs passed before this fix, legality pillar's own predicate
            // reused -- never a second one).
            val ownedNonBasics = ownedCollection.map { it.card }
                .filter { BasicLandCalculator.isLand(it) && !BasicLandCalculator.isBasicLand(it) }
                .filter { identitySymbols.containsAll(it.colorIdentity) }
                .filter { isLegalForFormat(it, format) }
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

        // ── Stage B+C: BasicLandPlanner (Deck Wizard UX polish plan, Run 1 §1.1) — pip-weighted
        //    distribution + bounded Karsten rebalance, extracted so Studio's own land-delta math can
        //    call the exact same counts logic. Materializing the counts into real Card entries via
        //    resolveBasicCard stays here (the planner is pure counts, no ownedCollection). ─────────
        val basicSlots = remainingLandSlots - placed.size
        if (basicSlots > 0) {
            val nonBasicDeckCards = placed.map { com.mmg.manahub.core.model.DeckCard(it.card, it.quantity) }
            val basicCounts = BasicLandPlanner.planBasics(
                identity = identity,
                landTarget = remainingLandSlots,
                nonLandMainboard = nonLandMainboard + commanderPipEntry,
                nonBasicLands = nonBasicDeckCards,
                manaBaseAnalyzer = manaBaseAnalyzer,
            )
            placed += ManaColor.entries.mapNotNull { color ->
                val qty = basicCounts[color] ?: 0
                if (qty <= 0) return@mapNotNull null
                val name = BasicLandCalculator.LAND_FOR_COLOR[color.symbol] ?: "Wastes"
                val card = resolveBasicCard(name, ownedCollection) ?: return@mapNotNull null
                DeckEntry(card, qty, true, false)
            }
        }

        return placed
    }

    private fun identitySymbolsToColors(symbols: Set<String>): Set<ManaColor> =
        ManaColor.entries.filter { it.symbol in symbols }.toSet()

    /**
     * R12/E13: the ONE lookup for "the [Card] object backing basic-land [name]" — used by Stage B's
     * materialization of [BasicLandPlanner.planBasics]'s output. Basics are documented as an
     * unlimited resource (never gated by ownership): the VM boundary
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
                IllegalStateException("deck_wizard_basic_land_unresolved"),
            )
        }
        return card
    }

    companion object {
        /** Commander formats: 100 total cards including the commander -> 99 non-commander slots. */
        private const val NON_COMMANDER_SLOTS = 99
        private const val ITERATION_CAP_SLACK = 20

        /** X5 -- generous vs. the old fixed 8 (quality over speed, user directive): a local search
         * that verifies every trial through a real analysis needs headroom to actually reach a
         * local optimum on a build with several weak/off-plan slots, not stop arbitrarily early. */
        private const val REFINE_MAX_SWAPS = 20

        /** X5 -- how many remaining candidates get a full trial analysis per round, pre-filtered by
         * [PlacementScorer.marginalGain] (cheap) against the board without that round's victim.
         * Bounds refine's cost to `REFINE_MAX_SWAPS * REFINE_TRIAL_SAMPLE` real analyses in the
         * worst case, rather than testing every remaining candidate every round. */
        private const val REFINE_TRIAL_SAMPLE = 4

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
