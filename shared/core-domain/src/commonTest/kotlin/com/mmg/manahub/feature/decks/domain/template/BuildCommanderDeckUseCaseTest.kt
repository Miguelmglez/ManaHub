package com.mmg.manahub.feature.decks.domain.template
// COMMENTS_REVIEWED: 2026-09-09

import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.domain.usecase.decks.BasicLandCalculator
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.core.model.TagCategory
import com.mmg.manahub.feature.decks.domain.engine.CuratedStrategyCatalog
import com.mmg.manahub.feature.decks.domain.engine.availableIn
import com.mmg.manahub.feature.decks.domain.engine.DeckScorer
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.NeutralPowerResolver
import com.mmg.manahub.feature.decks.domain.engine.RoleClassifier
import com.mmg.manahub.feature.decks.domain.engine.StrategyPick
import com.mmg.manahub.feature.decks.domain.engine.analysisv3.MockCollectionRich
import com.mmg.manahub.feature.decks.domain.engine.analysisv3.MockCollectionThin
import com.mmg.manahub.feature.decks.domain.engine.card
import com.mmg.manahub.core.gamification.domain.ProgressionEventBus
import com.mmg.manahub.feature.decks.domain.usecase.DeckAnalysisPipeline
import com.mmg.manahub.feature.decks.domain.usecase.EvaluateDeckUseCase
import com.mmg.manahub.feature.decks.domain.usecase.InferDeckIdentityUseCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private object TestCrashReporter : CrashReporter {
    val exceptions = mutableListOf<Throwable>()
    override fun recordException(throwable: Throwable) { exceptions += throwable }
    override fun log(message: String) = Unit
    override fun setCustomKey(key: String, value: String) = Unit
}

/**
 * Deck Wizard Commander v3 plan, Phase 2 gate: [BuildCommanderDeckUseCase] over the committed
 * [MockCollectionRich]/[MockCollectionThin] fixtures (Phase 0.3) — no gitignored real-collection
 * data needed, so this suite runs everywhere `commonTest` runs.
 */
class BuildCommanderDeckUseCaseTest {

    private fun newUseCase(): BuildCommanderDeckUseCase {
        val pipeline = DeckAnalysisPipeline(
            EvaluateDeckUseCase(DeckScorer(RoleClassifier(), NeutralPowerResolver), ProgressionEventBus()),
            InferDeckIdentityUseCase(),
            TestCrashReporter,
        )
        return BuildCommanderDeckUseCase(pipeline, TestCrashReporter)
    }

    private fun ownedFrom(vararg pools: List<com.mmg.manahub.feature.decks.domain.engine.analysisv3.MockCollectionCard>): List<OwnedCard> =
        pools.flatMap { pool -> pool.map { OwnedCard(it.card, it.quantity) } }

    private fun roleTagFor(key: String): CardTag = CardTag(key, TagCategory.ROLE)

    @Test
    fun `determinism -- two builds of the same spec are byte-identical`() = runTest {
        val useCase = newUseCase()
        val commander = MockCollectionThin.commander
        val owned = ownedFrom(MockCollectionThin.ownedCards, MockCollectionThin.ownedBasics)

        val first = useCase(DeckFormat.COMMANDER, commander, StrategyPick.Custom, commander.colorIdentity.toManaColors(), owned)
        val second = useCase(DeckFormat.COMMANDER, commander, StrategyPick.Custom, commander.colorIdentity.toManaColors(), owned)

        val firstIds = first.result.entries.map { it.card.scryfallId to it.quantity }.sortedBy { it.first }
        val secondIds = second.result.entries.map { it.card.scryfallId to it.quantity }.sortedBy { it.first }
        assertEquals(firstIds, secondIds, "two builds of the identical spec must place identical cards")
        assertEquals(first.result.analysis.totalScore, second.result.analysis.totalScore)
    }

    @Test
    fun `W6 Task 3 -- same deckId rebuilds byte-identical, different deckIds diverge`() = runTest {
        // A real scarcity scenario is required to exercise the tie-break at all: MockCollectionRich
        // is curated to almost exactly reconstruct its own target deck (no real excess candidates),
        // so every candidate gets placed regardless of order and the SET never differs. Here 90
        // synthetic filler creatures all carry the SAME role tag, CMC and (null-rank) power, so their
        // marginalGain genuinely ties -- with more of them than the ~63 available non-land slots,
        // WHICH 63 make the cut is decided purely by the tie-break.
        val useCase = newUseCase()
        val commander = card(id = "cmd-vanilla", name = "Vanilla Commander", typeLine = "Legendary Creature — Human", cmc = 3.0, colors = listOf("G"), colorIdentity = listOf("G"))
        val fillers = (1..90).map { i ->
            OwnedCard(
                card(id = "filler-$i", name = "Filler Creature $i", typeLine = "Creature — Bear", cmc = 2.0, colors = listOf("G"), colorIdentity = listOf("G"), tags = listOf(roleTagFor("ramp"))),
                1,
            )
        }
        val basics = MockCollectionRich.ownedBasics.filter { it.card.colorIdentity.contains("G") || it.card.name == "Forest" }
        val owned = fillers + basics.map { OwnedCard(it.card, it.quantity) }
        val identity = setOf(ManaColor.G)

        suspend fun build(deckId: String) = useCase(DeckFormat.COMMANDER, commander, StrategyPick.Custom, identity, owned, deckId = deckId)
        fun nonLandIds(outcome: CommanderBuildOutcome) = outcome.result.entries
            .filterNot { BasicLandCalculator.isLand(it.card) }
            .map { it.card.scryfallId }
            .sorted()

        val sameA = build("deck-A")
        val sameB = build("deck-A")
        assertEquals(nonLandIds(sameA), nonLandIds(sameB), "the SAME deckId rebuilt must place identical cards (E4)")

        val deckA = build("deck-A")
        val deckB = build("deck-B")
        assertTrue(nonLandIds(deckA) != nonLandIds(deckB), "two DIFFERENT deckIds with the same commander/strategy must diverge (E4) -- got identical placements: ${nonLandIds(deckA)}")
    }

    @Test
    fun `W6 Task 4 -- ambiguity groups are structurally valid over a real fixture`() = runTest {
        val useCase = newUseCase()
        val fixture = MockCollectionRich.targetFixtures.first { fx -> fx.mainboard.any { it.card.scryfallId == "cmd-edgar-markov" } }
        val commander = fixture.mainboard.first { it.card.scryfallId == "cmd-edgar-markov" }.card
        val identity = commander.colorIdentity.toManaColors()
        val owned = ownedFrom(MockCollectionRich.ownedCards, MockCollectionRich.ownedBasics)

        val outcome = useCase(DeckFormat.COMMANDER, commander, StrategyPick.Custom, identity, owned, deckId = "edgar-ambiguity")
        val placedIds = outcome.result.entries.map { it.card.scryfallId }.toSet()

        outcome.result.ambiguityGroups.forEach { group ->
            assertTrue(group.remainingSlots > 0, "an ambiguity group must represent a real, unresolved shortfall: $group")
            assertTrue(group.candidateIds.size >= 2, "a lone remaining candidate is a placement, not an ambiguity: $group")
            assertTrue(group.candidateIds.none { it in placedIds }, "an ambiguity group must only list UNPLACED candidates: $group")
            assertTrue(group.candidateIds.toSet().size == group.candidateIds.size, "an ambiguity group must not list a candidate twice: $group")
        }
    }

    @Test
    fun `W6 Task 4 -- a genuinely scarce role surfaces a real ambiguity group`() = runTest {
        // "card_draw" (ideal 12) and "removal_spot" (ideal 9, a real generic-baseline role tagged
        // via CardTag.REMOVAL's own key "removal") both get abundant, identically-scored supply --
        // but 55 off-plan manual adds (D7/R5: always kept) eat most of the 99-card budget first,
        // shrinking the remaining non-land target well below the combined 21-card ideal of the two
        // roles. Neither role can reach its own ideal before the deck runs out of room, so BOTH end
        // up with genuine leftover ties.
        val useCase = newUseCase()
        val commander = card(id = "cmd-vanilla-2", name = "Vanilla Commander II", typeLine = "Legendary Creature — Human", cmc = 3.0, colors = listOf("G"), colorIdentity = listOf("G"))
        val drawFillers = (1..40).map { i ->
            OwnedCard(card(id = "draw-$i", name = "Draw Filler $i", typeLine = "Sorcery", cmc = 2.0, colors = listOf("G"), colorIdentity = listOf("G"), tags = listOf(roleTagFor("card_draw"))), 1)
        }
        val removalFillers = (1..40).map { i ->
            OwnedCard(card(id = "removal-$i", name = "Removal Filler $i", typeLine = "Instant", cmc = 2.0, colors = listOf("G"), colorIdentity = listOf("G"), tags = listOf(roleTagFor("removal"))), 1)
        }
        val manualAdds = (1..55).map { i ->
            ManualAdd(card(id = "offplan-$i", name = "Off-plan Filler $i", typeLine = "Artifact", cmc = 1.0, colors = emptyList(), colorIdentity = emptyList()), isOwned = true)
        }
        val basics = MockCollectionRich.ownedBasics.filter { it.card.colorIdentity.contains("G") || it.card.name == "Forest" }
        val owned = drawFillers + removalFillers + basics.map { OwnedCard(it.card, it.quantity) }

        val outcome = useCase(DeckFormat.COMMANDER, commander, StrategyPick.Custom, setOf(ManaColor.G), owned, manualAdds = manualAdds, deckId = "scarcity-test")
        assertTrue(outcome.result.ambiguityGroups.isNotEmpty(), "expected at least one real ambiguity group under genuine total-slot scarcity")
        outcome.result.ambiguityGroups.forEach { group ->
            assertTrue(group.sectionId == "card_draw" || group.sectionId == "removal_spot", "unexpected section in a synthetic 2-role scenario: $group")
            assertTrue(group.candidateIds.size >= 2, "expected multiple plausible candidates, got: $group")
            assertTrue(group.remainingSlots > 0, "expected a real shortfall, got: $group")
        }
    }

    private class FakePreferenceStore(private val ids: List<String>) : com.mmg.manahub.feature.decks.domain.engine.WizardPreferenceStore {
        override suspend fun recordPick(cardId: String) = error("not needed for this test")
        override suspend fun preferredCardIds(): List<String> = ids
    }

    @Test
    fun `W6 Task 5 -- a preferred card reorders a near-tie without needing to win on gain alone`() = runTest {
        // Same tied-filler scarcity scenario as Task 3's determinism test: 90 identical "ramp"
        // fillers competing for ~63 slots, so the un-preferenced outcome is decided purely by the
        // deckId seed. Preferring one of the SEED-LOSING fillers must flip it into the final deck.
        val useCase = newUseCase()
        val commander = card(id = "cmd-vanilla-3", name = "Vanilla Commander III", typeLine = "Legendary Creature — Human", cmc = 3.0, colors = listOf("G"), colorIdentity = listOf("G"))
        val fillers = (1..90).map { i ->
            OwnedCard(card(id = "pref-filler-$i", name = "Pref Filler $i", typeLine = "Creature — Bear", cmc = 2.0, colors = listOf("G"), colorIdentity = listOf("G"), tags = listOf(roleTagFor("ramp"))), 1)
        }
        val basics = MockCollectionRich.ownedBasics.filter { it.card.colorIdentity.contains("G") || it.card.name == "Forest" }
        val owned = fillers + basics.map { OwnedCard(it.card, it.quantity) }

        val baseline = useCase(DeckFormat.COMMANDER, commander, StrategyPick.Custom, setOf(ManaColor.G), owned, deckId = "pref-test")
        val baselinePlaced = baseline.result.entries.map { it.card.scryfallId }.toSet()
        val loser = fillers.map { it.card.scryfallId }.first { it !in baselinePlaced }

        val withPreference = useCase(
            DeckFormat.COMMANDER, commander, StrategyPick.Custom, setOf(ManaColor.G), owned,
            deckId = "pref-test", preferenceStore = FakePreferenceStore(listOf(loser)),
        )
        assertTrue(loser in withPreference.result.entries.map { it.card.scryfallId }, "a preferred near-tie loser must be placed once preferred")
    }

    @Test
    fun `W6 Task 5 -- preference never overrides a band need (an anti-role card stays unplaced)`() = runTest {
        // "stax_piece" is the GENERIC baseline's own anti-role band for a MIDRANGE-leaning identity
        // in some skeletons; simplest reliable proof here is a card with NO real role/axis signal at
        // all (clears NEITHER roleGain nor axisGain), which the D8 filler floor rejects regardless of
        // the preference bonus (the bonus is only ever ADDED to an already-non-null gain).
        val useCase = newUseCase()
        val commander = MockCollectionThin.commander
        val owned = ownedFrom(MockCollectionThin.ownedCards, MockCollectionThin.ownedBasics)
        val vanillaFillerId = "cmd-vanilla-unrelated-filler"
        val vanillaFiller = card(id = vanillaFillerId, name = "Truly Vanilla Filler", typeLine = "Creature — Bear", cmc = 2.0, colors = commander.colorIdentity, colorIdentity = commander.colorIdentity)
        val ownedWithFiller = owned + OwnedCard(vanillaFiller, 1)

        val outcome = useCase(
            DeckFormat.COMMANDER, commander, StrategyPick.Custom, commander.colorIdentity.toManaColors(), ownedWithFiller,
            preferenceStore = FakePreferenceStore(listOf(vanillaFillerId)),
        )
        assertTrue(vanillaFillerId !in outcome.result.entries.map { it.card.scryfallId }, "a card with zero role/axis gain must stay unplaced even when preferred (D8 floor)")
    }

    @Test
    fun `manual adds -- owned, unowned, off-plan, and a land are all preserved`() = runTest {
        val useCase = newUseCase()
        val commander = MockCollectionThin.commander
        val identity = commander.colorIdentity.toManaColors()
        val owned = ownedFrom(MockCollectionThin.ownedCards, MockCollectionThin.ownedBasics)

        val unownedOffPlan = card(id = "manual-unowned-1", name = "Manual Unowned Off Plan", typeLine = "Artifact", cmc = 1.0, colors = emptyList(), colorIdentity = emptyList())
        val ownedManualLand = card(id = "manual-owned-land", name = "Manual Owned Land", typeLine = "Land", cmc = 0.0, colors = emptyList(), colorIdentity = identity.map { it.symbol })
        val manualAdds = listOf(
            ManualAdd(unownedOffPlan, isOwned = false),
            ManualAdd(ownedManualLand, isOwned = true),
        )

        val outcome = useCase(DeckFormat.COMMANDER, commander, StrategyPick.Custom, identity, owned, manualAdds = manualAdds)
        val placedIds = outcome.result.entries.map { it.card.scryfallId }

        assertTrue(unownedOffPlan.scryfallId in placedIds, "an unowned, off-plan manual add must still be kept (D7/R5)")
        assertTrue(ownedManualLand.scryfallId in placedIds, "a manual land add must still be kept")
    }

    @Test
    fun `MockCollectionThin -- builds without crashing, declares gaps, places no filler, still fills lands, reports the score honestly`() = runTest {
        val useCase = newUseCase()
        val commander = MockCollectionThin.commander
        val identity = commander.colorIdentity.toManaColors()
        val owned = ownedFrom(MockCollectionThin.ownedCards, MockCollectionThin.ownedBasics)

        val outcome = useCase(DeckFormat.COMMANDER, commander, StrategyPick.Custom, identity, owned)

        val landCount = outcome.result.entries
            .filter { BasicLandCalculator.isLand(it.card) }
            .sumOf { it.quantity }
        assertTrue(landCount > 0, "a thin collection must still get SOME lands filled from its 8 owned basics")
        assertTrue(outcome.result.entries.size < 100, "a 31-card owned pool cannot fill a 100-card mainboard -- gaps are expected, not filler")

        // D8: no filler placed -- every placed non-land, non-commander entry is EITHER a candidate
        // the placement loop chose from the owned pool (never exceeds the 30-card owned non-land
        // pool size) or the commander itself. The loop can place AT MOST 30 non-land candidates --
        // if it ever placed more, that would mean it invented cards outside the collection (D7) or
        // placed something the filler floor should have rejected.
        val nonLandNonCommanderCount = outcome.result.entries.count {
            !BasicLandCalculator.isLand(it.card) && it.card.scryfallId != commander.scryfallId
        }
        assertTrue(
            nonLandNonCommanderCount <= MockCollectionThin.ownedCards.size,
            "the placement loop must never place more non-land cards than the thin pool actually owns (${MockCollectionThin.ownedCards.size}), got $nonLandNonCommanderCount -- D8's filler floor exists precisely to prevent this",
        )

        // Score is reported honestly, not inflated to hide the gap: a 39-card (approx) mainboard
        // must show a DeckTooSmall finding and a total score that is NOT the perfect/near-perfect
        // number a full 100-card deck could reach.
        val findings = outcome.result.analysis.pillars.flatMap { it.findings }
        assertTrue(
            findings.any { it is com.mmg.manahub.feature.decks.domain.engine.Finding.DeckTooSmall },
            "a mainboard this thin must surface a DeckTooSmall finding, not hide the shortfall: $findings",
        )
        assertTrue(outcome.result.analysis.totalScore < 90, "a genuinely gappy thin-collection build must not score as if it were complete, got ${outcome.result.analysis.totalScore}")
    }

    @Test
    fun `Custom strategy pick always resolves a real, non-empty skeleton (F2 stays dead)`() = runTest {
        // MockCollectionThin.commander is fixture01EdgarMarkov's Edgar Markov, which since F18/E12
        // (W6 Task 1) carries an AGGRO tag -- Custom's build hint therefore biases toward AGGRO
        // bands rather than the generic baseline for THIS specific commander (see
        // CommanderPlanResolverTest for the isolated no-tag-signal case). F2's own guarantee is
        // narrower than "archetype == null": Custom must never resolve to an absent/empty skeleton,
        // regardless of which archetype (or none) the build hint lands on.
        val useCase = newUseCase()
        val commander = MockCollectionThin.commander
        val identity = commander.colorIdentity.toManaColors()
        val owned = ownedFrom(MockCollectionThin.ownedCards, MockCollectionThin.ownedBasics)

        val outcome = useCase(DeckFormat.COMMANDER, commander, StrategyPick.Custom, identity, owned)
        assertEquals(com.mmg.manahub.feature.decks.domain.engine.ArchetypeId.AGGRO, outcome.plan.skeleton.archetype, "F18: Edgar Markov's Custom build hint resolves AGGRO")
        assertTrue(outcome.result.analysis.pillars.isNotEmpty(), "Custom must still target real bands, not zero targets (F2)")
    }

    @Test
    fun `W0_1 -- a Commander-banned owned card is structurally excluded from the COMMANDER candidate pool`() = runTest {
        val useCase = newUseCase()
        val commander = MockCollectionThin.commander
        val identity = commander.colorIdentity.toManaColors()
        val bannedOwned = com.mmg.manahub.feature.decks.domain.engine.card(
            id = "banned-owned-1",
            name = "Banned Owned Candidate",
            typeLine = "Creature — Vampire",
            cmc = 2.0,
            colors = identity.map { it.symbol },
            colorIdentity = identity.map { it.symbol },
            legalityCommander = "banned",
        )
        val owned = ownedFrom(MockCollectionThin.ownedCards, MockCollectionThin.ownedBasics) +
            OwnedCard(bannedOwned, 1)

        val commanderOutcome = useCase(DeckFormat.COMMANDER, commander, StrategyPick.Custom, identity, owned)
        assertTrue(
            commanderOutcome.result.entries.none { it.card.scryfallId == bannedOwned.scryfallId },
            "a banned owned card must never enter a COMMANDER build's candidate pool",
        )
    }

    @Test
    fun `W0_1 -- builder and analysis agree on the same legality verdict per format`() = runTest {
        val commander = MockCollectionThin.commander
        val bannedCard = com.mmg.manahub.feature.decks.domain.engine.card(
            id = "banned-analysis-1",
            name = "Banned Analysis Candidate",
            typeLine = "Sorcery",
            cmc = 2.0,
            colors = emptyList(),
            colorIdentity = emptyList(),
            legalityCommander = "banned",
        )
        val mainboard = listOf(com.mmg.manahub.feature.decks.domain.engine.DeckEntry(bannedCard, 1, isOwned = true))
        val pipeline = DeckAnalysisPipeline(
            EvaluateDeckUseCase(DeckScorer(RoleClassifier(), NeutralPowerResolver), ProgressionEventBus()),
            InferDeckIdentityUseCase(),
            TestCrashReporter,
        )

        val commanderHealth = pipeline.analyze(
            mainboard = mainboard, format = DeckFormat.COMMANDER, commander = commander,
            archetypeOverride = null, themesOverride = emptyList(), emitProgression = false,
        )
        val casualHealth = pipeline.analyze(
            mainboard = mainboard, format = DeckFormat.COMMANDER_CASUAL, commander = commander,
            archetypeOverride = null, themesOverride = emptyList(), emitProgression = false,
        )

        val commanderIllegal = commanderHealth.analysis?.pillars.orEmpty().flatMap { it.findings }
            .any { it is com.mmg.manahub.feature.decks.domain.engine.Finding.IllegalCard }
        val casualIllegal = casualHealth.analysis?.pillars.orEmpty().flatMap { it.findings }
            .any { it is com.mmg.manahub.feature.decks.domain.engine.Finding.IllegalCard }

        assertTrue(commanderIllegal, "AnalysisEngine must flag a banned card as illegal for COMMANDER (matches the builder's own exclusion)")
        assertTrue(!casualIllegal, "AnalysisEngine must NOT flag a banned card as illegal for COMMANDER_CASUAL (R7)")
    }

    @Test
    fun `round-trip identity -- the returned analysis matches re-analyzing the assembled mainboard`() = runTest {
        val useCase = newUseCase()
        val commander = MockCollectionRich.targetFixtures.first().mainboard
            .first { it.card.typeLine.contains("Legendary", ignoreCase = true) }.card
        val identity = commander.colorIdentity.toManaColors()
        val owned = ownedFrom(MockCollectionRich.ownedCards, MockCollectionRich.ownedBasics)

        val curated = CuratedStrategyCatalog.ALL.firstOrNull { it.availableIn(DeckFormat.COMMANDER) && !it.requiresTribe }
        val pick = curated?.let { StrategyPick.Curated(it, null) } ?: StrategyPick.Custom

        val outcome = useCase(DeckFormat.COMMANDER, commander, pick, identity, owned)

        val pipeline = DeckAnalysisPipeline(
            EvaluateDeckUseCase(DeckScorer(RoleClassifier(), NeutralPowerResolver), ProgressionEventBus()),
            InferDeckIdentityUseCase(),
            TestCrashReporter,
        )
        val rebuiltHealth = pipeline.analyze(
            mainboard = outcome.result.entries,
            format = DeckFormat.COMMANDER,
            commander = commander,
            archetypeOverride = outcome.pin.archetype?.name,
            themesOverride = outcome.pin.themes.map { it.name },
            tribeOverride = outcome.pin.tribe,
            postureOverride = outcome.pin.posture?.name,
            emitProgression = false,
        )
        val rebuiltAnalysis = rebuiltHealth.analysis?.copy(debugSynergyGraph = null)
        val originalAnalysis = outcome.result.analysis.copy(debugSynergyGraph = null)

        assertEquals(
            originalAnalysis.totalScore,
            rebuiltAnalysis?.totalScore,
            "round_trip_identity: the wizard's own returned score must equal re-analyzing the persisted deck",
        )
        assertEquals(originalAnalysis, rebuiltAnalysis, "round_trip_identity: full DeckAnalysis must match, not just totalScore")
    }

    // ── R12/E13 (Deck Wizard Commander v4, W5.3) -- basics are unlimited, never gated by ownership ──
    /**
     * The literal acceptance test named in the v4 plan (§4 W5.3) and the campaign brief: an
     * `ownedCollection` with the commander + a real non-land pool but ZERO basic-land copies on
     * hand must still reach a full 100-card deck with a correct, non-degenerate basic-land spread,
     * and never report a `ColorSourceShortage`/`UnfixedSplash` for an OWNERSHIP reason.
     *
     * [BuildCommanderDeckUseCase] is pure `commonMain` with NO `CardRepository` (D7/R5 -- see this
     * file's own header comment) -- it cannot itself fetch a real [Card] the collection has never
     * seen. The production guarantee that a real basic-land [Card] object always exists in
     * `ownedCollection` by the time this use case runs is [com.mmg.manahub.feature.decks
     * .presentation.wizard.DeckWizardViewModel.guaranteeBasicsAvailable] (Android, tested at the VM
     * layer in `DeckWizardViewModelTest`'s W0_2/W5_3 cases). This test exercises the OTHER half of
     * the R12 guarantee, the half this pure engine test CAN prove: basic-land placement must never
     * be gated by OWNED QUANTITY -- a basic [OwnedCard] at quantity 0 (a real Card object the app
     * knows about, but the collection reports zero actual copies of -- exactly what a genuinely
     * unscanned-basics collection looks like once the VM boundary above has run) must still be
     * placed in full, unlimited quantity. [resolveBasicCard]/[fillLandsV2]'s Stage B never consult
     * [OwnedCard.quantity] for a basic (only its `.card`), so this is the correct, safe way to prove
     * the R12 rule at this layer without inventing a synthetic scryfallId (which would violate the
     * collection-only architecture and could corrupt a real persisted deck's card references).
     */
    @Test
    fun `R12 -- a collection with ZERO owned basic-land copies still reaches 100 cards with a correct spread and no ownership-driven shortage`() = runTest {
        val useCase = newUseCase()
        val fixture = MockCollectionRich.targetFixtures.first { fx -> fx.mainboard.any { it.card.scryfallId == "cmd-edgar-markov" } }
        val commander = fixture.mainboard.first { it.card.scryfallId == "cmd-edgar-markov" }.card
        val identity = commander.colorIdentity.toManaColors()
        val zeroQtyBasics = identity.mapNotNull { color ->
            val name = com.mmg.manahub.core.domain.usecase.decks.BasicLandCalculator.LAND_FOR_COLOR[color.symbol] ?: return@mapNotNull null
            OwnedCard(
                com.mmg.manahub.feature.decks.domain.engine.card(
                    id = "r12-zero-qty-$name", name = name, typeLine = "Basic Land — $name",
                    cmc = 0.0, colors = emptyList(), colorIdentity = listOf(color.symbol),
                ),
                quantity = 0,
            )
        }
        val owned = ownedFrom(MockCollectionRich.ownedCards) + zeroQtyBasics

        val outcome = useCase(DeckFormat.COMMANDER, commander, StrategyPick.Custom, identity, owned)

        assertEquals(100, outcome.result.entries.sumOf { it.quantity }, "a zero-owned-basics collection must still reach exactly 100 cards")
        val landEntries = outcome.result.entries.filter { com.mmg.manahub.core.domain.usecase.decks.BasicLandCalculator.isLand(it.card) }
        assertTrue(landEntries.sumOf { it.quantity } > 0, "basics must actually be placed despite zero owned quantity")
        assertTrue(
            landEntries.all { com.mmg.manahub.core.domain.usecase.decks.BasicLandCalculator.isBasicLand(it.card) },
            "no non-basic land was owned in this fixture -- every placed land must be a basic",
        )

        val shortageFindings = outcome.result.analysis.pillars.flatMap { it.findings }
            .filter { it is com.mmg.manahub.feature.decks.domain.engine.Finding.ColorSourceShortage || it is com.mmg.manahub.feature.decks.domain.engine.Finding.UnfixedSplash }
        assertTrue(shortageFindings.isEmpty(), "zero owned basic quantity must never cause an ownership-driven mana-base shortage: $shortageFindings")

        // NOTE: gapSections legitimately CAN report an unrelated role gap here (e.g. "role:mana_fix",
        // a mana-ROCK/creature role -- MockCollectionRich's shared 150-distractor pool is not sized
        // per-commander and can genuinely lack enough fixing artifacts for Edgar specifically; this
        // is a real content gap, orthogonal to R12) -- the R12-specific claim is narrower and already
        // covered above: no ownership-driven COLOR SOURCE shortage/splash finding, and the full land
        // count is reached. Asserting on gapSections generically here would conflate "the wizard
        // built a complete, correctly-fixed mana base" (R12's actual promise) with "the shared mock
        // pool happens to contain enough mana rocks for this one commander" (unrelated to this fix).
    }
}

private fun List<String>.toManaColors(): Set<ManaColor> =
    mapNotNull { symbol -> ManaColor.entries.firstOrNull { it.symbol == symbol } }.toSet()
