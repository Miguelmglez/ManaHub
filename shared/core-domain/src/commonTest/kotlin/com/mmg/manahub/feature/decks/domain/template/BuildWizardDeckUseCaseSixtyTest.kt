package com.mmg.manahub.feature.decks.domain.template
// COMMENTS_REVIEWED: 2026-09-16

import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.domain.usecase.decks.BasicLandCalculator
import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeFormat
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeRoleClassifier
import com.mmg.manahub.feature.decks.domain.engine.BuildAnchor
import com.mmg.manahub.feature.decks.domain.engine.DeckScorer
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.NeutralPowerResolver
import com.mmg.manahub.feature.decks.domain.engine.RoleClassifier
import com.mmg.manahub.feature.decks.domain.engine.StrategyPick
import com.mmg.manahub.feature.decks.domain.engine.SynergyGraph
import com.mmg.manahub.feature.decks.domain.engine.analysisv3.MockCollectionRich
import com.mmg.manahub.feature.decks.domain.engine.analysisv3.fixture14MonoRedBurn
import com.mmg.manahub.feature.decks.domain.engine.card
import com.mmg.manahub.feature.decks.domain.engine.entry
import com.mmg.manahub.core.gamification.domain.ProgressionEventBus
import com.mmg.manahub.feature.decks.domain.usecase.DeckAnalysisPipeline
import com.mmg.manahub.feature.decks.domain.usecase.EvaluateDeckUseCase
import com.mmg.manahub.feature.decks.domain.usecase.InferDeckIdentityUseCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private object SixtyTestCrashReporter : CrashReporter {
    override fun recordException(throwable: Throwable) = Unit
    override fun log(message: String) = Unit
    override fun setCustomKey(key: String, value: String) = Unit
}

/**
 * Deck Wizard 60-card wave (v6), plan §5 Phase 1.3 gate + test spec §6: [BuildWizardDeckUseCase]'s
 * new [BuildAnchor.Sixty] branch over [MockCollectionRich] + fixture14's own seeds — same fixtures
 * `BuildCommanderDeckUseCaseTest` already uses, so this suite runs everywhere `commonTest` runs.
 *
 * Deviation from the original test spec's literal (c)/(f) shapes, documented once here rather than
 * per-test: `finalize`'s `resolutions` parameter stayed `Map<RoleKey, List<String>>` (unchanged from
 * pre-v6) rather than becoming `Map<RoleKey, Map<String, Int>>` — see `BuildWizardDeckUseCase
 * .finalize`'s own KDoc for the JVM-signature-clash + Kotlin-overload-ambiguity reasoning. (c)/(f)
 * below are adapted to that real, shipped API shape (a repeated id in the list means multiple
 * copies), not the spec's literal Map-of-counts example -- the underlying capability (a seed's
 * quantity clamped to its legality cap; `finalize` swapping in N copies of one alternative id) is
 * unchanged.
 */
class BuildWizardDeckUseCaseSixtyTest {

    private fun newUseCase(): BuildWizardDeckUseCase {
        val pipeline = DeckAnalysisPipeline(
            EvaluateDeckUseCase(DeckScorer(RoleClassifier(), NeutralPowerResolver), ProgressionEventBus()),
            InferDeckIdentityUseCase(),
            SixtyTestCrashReporter,
        )
        return BuildWizardDeckUseCase(pipeline, SixtyTestCrashReporter)
    }

    private fun ownedFrom(vararg pools: List<com.mmg.manahub.feature.decks.domain.engine.analysisv3.MockCollectionCard>): List<OwnedCard> =
        pools.flatMap { pool -> pool.map { OwnedCard(it.card, it.quantity) } }

    /** 6 of fixture14's 12 unique non-land cards, one seed each -- leaves the loop room to fill
     * from the pool (6x4 = 24 seed copies, same convention as Phase 6.1's harness segments). */
    private fun sixtySeeds() = fixture14MonoRedBurn().mainboard
        .filterNot { BasicLandCalculator.isLand(it.card) }
        .take(6)
        .map { it.card }

    private fun sixtyManualAdds(seeds: List<com.mmg.manahub.core.model.Card>) =
        seeds.map { ManualAdd(it, isOwned = true, quantity = 4) }

    private fun sixtyOwned() = ownedFrom(MockCollectionRich.ownedCards, MockCollectionRich.ownedBasics)

    @Test
    fun `(a) builds exactly 60 cards or declares gaps`() = runTest {
        val useCase = newUseCase()
        val seeds = sixtySeeds()
        val draft = useCase.buildWithGroups(
            format = DeckFormat.MODERN,
            anchor = BuildAnchor.Sixty(setOf(ManaColor.R), seeds),
            strategyPick = StrategyPick.Custom,
            ownedCollection = sixtyOwned(),
            manualAdds = sixtyManualAdds(seeds),
            deckId = "sixty-a",
        )
        val outcome = useCase.finalize(draft, resolutions = emptyMap())
        val total = outcome.result.entries.sumOf { it.quantity }
        assertTrue(total <= 60, "a 60-card build must never exceed the format's own target size")
        assertEquals(60, total, "MockCollectionRich is plentiful enough to reach exactly 60")
    }

    @Test
    fun `(b) no entry exceeds min(4, owned) except basics, and seeds may exceed owned (S3)`() = runTest {
        val useCase = newUseCase()
        val seeds = sixtySeeds()
        val owned = sixtyOwned()
        val ownedByName = owned.groupBy { it.card.name }.mapValues { (_, cards) -> cards.sumOf { it.quantity } }
        val seedNames = seeds.map { it.name }.toSet()

        val draft = useCase.buildWithGroups(
            format = DeckFormat.MODERN,
            anchor = BuildAnchor.Sixty(setOf(ManaColor.R), seeds),
            strategyPick = StrategyPick.Custom,
            ownedCollection = owned,
            manualAdds = sixtyManualAdds(seeds),
            deckId = "sixty-b",
        )
        val outcome = useCase.finalize(draft, resolutions = emptyMap())

        outcome.result.entries
            .filterNot { BasicLandCalculator.isBasicLand(it.card) }
            .forEach { entry ->
                assertTrue(entry.quantity <= 4, "${entry.card.name} exceeds the format's 4-copy cap")
                if (entry.card.name !in seedNames) {
                    val ownedQty = ownedByName[entry.card.name] ?: 0
                    assertTrue(entry.quantity <= ownedQty, "engine-placed ${entry.card.name} (${entry.quantity}) exceeds owned quantity $ownedQty")
                }
            }
    }

    @Test
    fun `(c) a Vintage-restricted seed's manual quantity is clamped to CopyPolicy maxSeedCopies (1)`() = runTest {
        val useCase = newUseCase()
        val bolt = fixture14MonoRedBurn().mainboard.first { it.card.scryfallId == "mrb-bolt" }.card
        val restrictedBolt = bolt.copy(legalityVintage = "restricted")
        val owned = sixtyOwned() + OwnedCard(restrictedBolt, 10)
        val manualAdds = listOf(ManualAdd(restrictedBolt, isOwned = true, quantity = 4))

        val draft = useCase.buildWithGroups(
            format = DeckFormat.VINTAGE,
            anchor = BuildAnchor.Sixty(setOf(ManaColor.R), listOf(restrictedBolt)),
            strategyPick = StrategyPick.Custom,
            ownedCollection = owned,
            manualAdds = manualAdds,
            deckId = "sixty-c",
        )
        val outcome = useCase.finalize(draft, resolutions = emptyMap())

        val boltEntry = outcome.result.entries.firstOrNull { it.card.scryfallId == restrictedBolt.scryfallId }
        assertEquals(1, boltEntry?.quantity, "a Vintage-restricted seed must be clamped to 1 copy even when the user requested 4")
    }

    @Test
    fun `(d) copies sum into role counts -- a 4-of Lightning Bolt contributes 4 to removal_spot`() = runTest {
        val useCase = newUseCase()
        val seeds = sixtySeeds()
        val draft = useCase.buildWithGroups(
            format = DeckFormat.MODERN,
            anchor = BuildAnchor.Sixty(setOf(ManaColor.R), seeds),
            strategyPick = StrategyPick.Custom,
            ownedCollection = sixtyOwned(),
            manualAdds = sixtyManualAdds(seeds),
            deckId = "sixty-d",
        )
        val outcome = useCase.finalize(draft, resolutions = emptyMap())

        val boltEntry = outcome.result.entries.first { it.card.scryfallId == "mrb-bolt" }
        assertEquals(4, boltEntry.quantity)
        val roleCounts = ArchetypeRoleClassifier.deckRoleCounts(outcome.result.entries.filterNot { BasicLandCalculator.isLand(it.card) })
        assertTrue((roleCounts["removal_spot"] ?: 0) >= 4, "Lightning Bolt's CardTag.REMOVAL maps to removal_spot -- 4 copies must contribute at least 4")
    }

    @Test
    fun `(e) colorless identity places only colorless cards, basics are Wastes only`() = runTest {
        val useCase = newUseCase()
        val colorlessSeed = card(id = "colorless-seed", name = "Colorless Seed", typeLine = "Artifact Creature — Golem", cmc = 3.0, colors = emptyList(), colorIdentity = emptyList())
        val fillers = (1..20).map { i ->
            OwnedCard(
                card(id = "colorless-filler-$i", name = "Colorless Filler $i", typeLine = "Artifact Creature — Golem", cmc = 2.0, colors = emptyList(), colorIdentity = emptyList()),
                4,
            )
        }
        val wastes = OwnedCard(card(id = "wastes-c", name = "Wastes", typeLine = "Basic Land", cmc = 0.0, colors = emptyList(), colorIdentity = emptyList(), producedMana = "C"), 40)
        val owned = fillers + listOf(wastes)

        val draft = useCase.buildWithGroups(
            format = DeckFormat.STANDARD,
            anchor = BuildAnchor.Sixty(emptySet(), listOf(colorlessSeed)),
            strategyPick = StrategyPick.Custom,
            ownedCollection = owned,
            manualAdds = listOf(ManualAdd(colorlessSeed, isOwned = true, quantity = 1)),
            deckId = "sixty-e",
        )
        val outcome = useCase.finalize(draft, resolutions = emptyMap())

        outcome.result.entries.forEach { assertTrue(it.card.colorIdentity.isEmpty(), "${it.card.name} has a non-empty color identity in a colorless build") }
        val landEntries = outcome.result.entries.filter { BasicLandCalculator.isLand(it.card) }
        assertTrue(landEntries.isNotEmpty(), "a 60-card colorless build must still fill its land slots")
        landEntries.forEach { assertEquals("Wastes", it.card.name, "the only legal basic for an empty identity is Wastes") }
    }

    @Test
    fun `(f) finalize can move two tentative copies onto the same alternative id`() = runTest {
        val useCase = newUseCase()
        // A deliberately SCARCE scenario (mirrors BuildCommanderDeckUseCaseFinalizeTest
        // .scarcityOwned's proven shape exactly, scaled to a 60-card nonLandTarget): 30 off-plan
        // manual adds crowd out most of the non-land slots, leaving only a handful for 30
        // same-role removal_spot fillers -- genuine multi-way ties over too few remaining slots,
        // which is what makes the live ambiguity detector reliably mark tentative slots (raw
        // abundance with NO scarcity, tried first, never produced a >=2-slot group).
        // "removal_spot" is the RoleKey CardTag.REMOVAL maps TO (via ArchetypeRoleClassifier's
        // LEGACY_ROLE_MAP -> RoleClassifier), not a tag key itself -- the card must carry
        // CardTag.REMOVAL (key "removal"), same as fixture14's own Lightning Bolt.
        val removalFillers = (1..30).map { i ->
            OwnedCard(card(id = "sixty-fin-removal-$i", name = "Sixty Finalize Removal Filler $i", typeLine = "Instant", cmc = 2.0, colors = listOf("R"), colorIdentity = listOf("R"), tags = listOf(CardTag.REMOVAL)), 4)
        }
        val offPlanManualAdds = (1..30).map { i ->
            ManualAdd(card(id = "sixty-fin-offplan-$i", name = "Sixty Finalize Off-plan Filler $i", typeLine = "Artifact", cmc = 1.0, colors = emptyList(), colorIdentity = emptyList()), isOwned = true)
        }
        val basics = MockCollectionRich.ownedBasics.filter { it.card.colorIdentity.contains("R") || it.card.name == "Mountain" }
        val owned = removalFillers + basics.map { OwnedCard(it.card, it.quantity) }

        val draft = useCase.buildWithGroups(
            format = DeckFormat.MODERN,
            anchor = BuildAnchor.Sixty(setOf(ManaColor.R), emptyList()),
            strategyPick = StrategyPick.Custom,
            ownedCollection = owned,
            manualAdds = offPlanManualAdds,
            deckId = "sixty-finalize-multi-copy",
        )
        val group = draft.ambiguityGroups.firstOrNull { it.remainingSlots >= 2 && it.candidateIds.isNotEmpty() }
        assertTrue(group != null, "expected the crowded-removal scenario to surface a >=2-slot ambiguity group")
        val altId = group.candidateIds.first()

        val outcome = useCase.finalize(draft, resolutions = mapOf(group.sectionId to listOf(altId, altId)))
        val altEntry = outcome.result.entries.first { it.card.scryfallId == altId }
        assertEquals(2, altEntry.quantity, "requesting the same alternative id twice must place exactly 2 copies of it")
    }

    // ── Plan §10 extras ────────────────────────────────────────────────────────────────────────

    @Test
    fun `deckRoleCounts is quantity-aware -- a quantity 4 entry contributes 4x its confidence`() {
        val removalCard = card(id = "qty-removal", tags = listOf(CardTag.REMOVAL))
        val counts = ArchetypeRoleClassifier.deckRoleCounts(listOf(entry(removalCard, quantity = 4)))
        assertEquals(4, counts["removal_spot"])
    }

    @Test
    fun `SynergyGraph AxisState producerCopies sums quantity, not entry count`() {
        // Artifact is one of PlacementScorer's own "bare type-line density producer" axes (ARTIFACTS/
        // SPELLS/ENCHANTMENTS) -- credited from the TYPE LINE alone, so this needs no role tag.
        val artifactCard = card(id = "qty-artifact", typeLine = "Artifact")
        val graph = SynergyGraph.build(listOf(entry(artifactCard, quantity = 4)), ArchetypeFormat.SIXTY)
        val artifactsAxis = graph.axes.firstOrNull { it.axis == "ARTIFACTS" }
        assertEquals(4, artifactsAxis?.producerCopies, "a quantity-4 entry must contribute 4 producer copies, not 1")
    }

    @Test
    fun `refine never leaves a zero-quantity entry on the board`() = runTest {
        val useCase = newUseCase()
        val seeds = sixtySeeds()
        val draft = useCase.buildWithGroups(
            format = DeckFormat.MODERN,
            anchor = BuildAnchor.Sixty(setOf(ManaColor.R), seeds),
            strategyPick = StrategyPick.Custom,
            ownedCollection = sixtyOwned(),
            manualAdds = sixtyManualAdds(seeds),
            deckId = "sixty-refine-zero-check",
        )
        val outcome = useCase.finalize(draft, resolutions = emptyMap())
        assertTrue(outcome.result.entries.all { it.quantity > 0 }, "refine's decrement-or-remove must never leave a 0-quantity slot")
    }
}
