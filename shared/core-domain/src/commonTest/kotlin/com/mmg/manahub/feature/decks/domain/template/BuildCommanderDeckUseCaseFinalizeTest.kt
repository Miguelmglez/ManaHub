package com.mmg.manahub.feature.decks.domain.template
// COMMENTS_REVIEWED: 2026-09-15

import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.domain.usecase.decks.BasicLandCalculator
import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.core.model.TagCategory
import com.mmg.manahub.feature.decks.domain.engine.DeckScorer
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.NeutralPowerResolver
import com.mmg.manahub.feature.decks.domain.engine.RoleClassifier
import com.mmg.manahub.feature.decks.domain.engine.StrategyPick
import com.mmg.manahub.feature.decks.domain.engine.analysisv3.MockCollectionRich
import com.mmg.manahub.feature.decks.domain.engine.card
import com.mmg.manahub.core.gamification.domain.ProgressionEventBus
import com.mmg.manahub.feature.decks.domain.usecase.DeckAnalysisPipeline
import com.mmg.manahub.feature.decks.domain.usecase.EvaluateDeckUseCase
import com.mmg.manahub.feature.decks.domain.usecase.InferDeckIdentityUseCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private object FinalizeTestCrashReporter : CrashReporter {
    override fun recordException(throwable: Throwable) = Unit
    override fun log(message: String) = Unit
    override fun setCustomKey(key: String, value: String) = Unit
}

/**
 * W7 Task 0 (7.0) — [BuildCommanderDeckUseCase.buildWithGroups]/[BuildCommanderDeckUseCase.finalize]
 * over the SAME genuine-scarcity scenario [BuildCommanderDeckUseCaseTest] already uses for
 * ambiguity coverage (55 off-plan manual adds crowd two competing roles below their combined
 * ideal) — reused here rather than re-derived, so this suite exercises the real ambiguity path,
 * not a hand-picked toy case.
 */
class BuildCommanderDeckUseCaseFinalizeTest {

    private fun newUseCase(): BuildCommanderDeckUseCase {
        val pipeline = DeckAnalysisPipeline(
            EvaluateDeckUseCase(DeckScorer(RoleClassifier(), NeutralPowerResolver), ProgressionEventBus()),
            InferDeckIdentityUseCase(),
            FinalizeTestCrashReporter,
        )
        return BuildCommanderDeckUseCase(pipeline, FinalizeTestCrashReporter)
    }

    private fun roleTagFor(key: String): CardTag = CardTag(key, TagCategory.ROLE)

    private fun scarcityOwned(): Triple<com.mmg.manahub.core.model.Card, List<OwnedCard>, List<ManualAdd>> {
        val commander = card(id = "cmd-finalize-scarcity", name = "Finalize Scarcity Commander", typeLine = "Legendary Creature — Human", cmc = 3.0, colors = listOf("G"), colorIdentity = listOf("G"))
        val drawFillers = (1..40).map { i ->
            OwnedCard(card(id = "fin-draw-$i", name = "Finalize Draw Filler $i", typeLine = "Sorcery", cmc = 2.0, colors = listOf("G"), colorIdentity = listOf("G"), tags = listOf(roleTagFor("card_draw"))), 1)
        }
        val removalFillers = (1..40).map { i ->
            OwnedCard(card(id = "fin-removal-$i", name = "Finalize Removal Filler $i", typeLine = "Instant", cmc = 2.0, colors = listOf("G"), colorIdentity = listOf("G"), tags = listOf(roleTagFor("removal"))), 1)
        }
        val manualAdds = (1..55).map { i ->
            ManualAdd(card(id = "fin-offplan-$i", name = "Finalize Off-plan Filler $i", typeLine = "Artifact", cmc = 1.0, colors = emptyList(), colorIdentity = emptyList()), isOwned = true)
        }
        val basics = MockCollectionRich.ownedBasics.filter { it.card.colorIdentity.contains("G") || it.card.name == "Forest" }
        val owned = drawFillers + removalFillers + basics.map { OwnedCard(it.card, it.quantity) }
        return Triple(commander, owned, manualAdds)
    }

    @Test
    fun `finalize with empty resolutions equals the single-shot invoke build byte for byte`() = runTest {
        val useCase = newUseCase()
        val (commander, owned, manualAdds) = scarcityOwned()
        val identity = setOf(ManaColor.G)

        val singleShot = useCase(DeckFormat.COMMANDER, commander, StrategyPick.Custom, identity, owned, manualAdds = manualAdds, deckId = "finalize-parity")

        val draft = useCase.buildWithGroups(DeckFormat.COMMANDER, commander, StrategyPick.Custom, identity, owned, manualAdds = manualAdds, deckId = "finalize-parity")
        assertTrue(draft.ambiguityGroups.isNotEmpty(), "expected the shared scarcity scenario to still surface ambiguity groups")
        val viaFinalize = useCase.finalize(draft, resolutions = emptyMap())

        val singleIds = singleShot.result.entries.map { it.card.scryfallId to it.quantity }.sortedBy { it.first }
        val finalizeIds = viaFinalize.result.entries.map { it.card.scryfallId to it.quantity }.sortedBy { it.first }
        assertEquals(singleIds, finalizeIds, "finalize() with no resolutions must equal the single-shot invoke() build")
        assertEquals(singleShot.result.analysis.totalScore, viaFinalize.result.analysis.totalScore)
    }

    @Test
    fun `finalizing twice with the same resolutions is deterministic`() = runTest {
        val useCase = newUseCase()
        val (commander, owned, manualAdds) = scarcityOwned()
        val identity = setOf(ManaColor.G)

        val draft = useCase.buildWithGroups(DeckFormat.COMMANDER, commander, StrategyPick.Custom, identity, owned, manualAdds = manualAdds, deckId = "finalize-determinism")
        val group = draft.ambiguityGroups.first()
        val resolution = mapOf(group.sectionId to listOf(group.candidateIds.first()))

        val first = useCase.finalize(draft, resolutions = resolution)
        val second = useCase.finalize(draft, resolutions = resolution)

        val firstIds = first.result.entries.map { it.card.scryfallId to it.quantity }.sortedBy { it.first }
        val secondIds = second.result.entries.map { it.card.scryfallId to it.quantity }.sortedBy { it.first }
        assertEquals(firstIds, secondIds, "finalizing the SAME draft with the SAME resolutions twice must be byte-identical")
    }

    @Test
    fun `a user pick swaps only within its own group -- every other card is untouched`() = runTest {
        val useCase = newUseCase()
        val (commander, owned, manualAdds) = scarcityOwned()
        val identity = setOf(ManaColor.G)

        val draft = useCase.buildWithGroups(DeckFormat.COMMANDER, commander, StrategyPick.Custom, identity, owned, manualAdds = manualAdds, deckId = "finalize-eviction-safety")
        val group = draft.ambiguityGroups.first()
        val tentativeDefaults = draft.tentativeByRole.getValue(group.sectionId).toSet()
        val chosenReplacement = group.candidateIds.first()

        val defaultOutcome = useCase.finalize(draft, resolutions = emptyMap())
        val resolvedOutcome = useCase.finalize(draft, resolutions = mapOf(group.sectionId to listOf(chosenReplacement)))

        val defaultIds = defaultOutcome.result.entries.map { it.card.scryfallId }.toSet()
        val resolvedIds = resolvedOutcome.result.entries.map { it.card.scryfallId }.toSet()

        // Exactly one tentative default left the board, exactly the chosen replacement entered it --
        // no other card (manual add, other placement, land) changed at all.
        val removed = defaultIds - resolvedIds
        val added = resolvedIds - defaultIds
        assertEquals(1, removed.size, "resolving one slot must evict exactly one card: $removed")
        assertTrue(removed.first() in tentativeDefaults, "the evicted card must be one of the group's OWN tentative defaults")
        assertEquals(setOf(chosenReplacement), added, "the only new card must be the user's chosen replacement")
    }

    @Test
    fun `round-trip identity holds for a resolved deck`() = runTest {
        val useCase = newUseCase()
        val (commander, owned, manualAdds) = scarcityOwned()
        val identity = setOf(ManaColor.G)

        val draft = useCase.buildWithGroups(DeckFormat.COMMANDER, commander, StrategyPick.Custom, identity, owned, manualAdds = manualAdds, deckId = "finalize-round-trip")
        val group = draft.ambiguityGroups.first()
        val outcome = useCase.finalize(draft, resolutions = mapOf(group.sectionId to listOf(group.candidateIds.first())))

        val pipeline = DeckAnalysisPipeline(
            EvaluateDeckUseCase(DeckScorer(RoleClassifier(), NeutralPowerResolver), ProgressionEventBus()),
            InferDeckIdentityUseCase(),
            FinalizeTestCrashReporter,
        )
        val reanalyzed = pipeline.analyze(
            mainboard = outcome.result.entries,
            format = DeckFormat.COMMANDER,
            commander = commander,
            archetypeOverride = outcome.pin.archetype?.name,
            themesOverride = outcome.pin.themes.map { it.name },
            tribeOverride = outcome.pin.tribe,
            postureOverride = outcome.pin.posture?.name,
            emitProgression = false,
        ).analysis
        assertEquals(outcome.result.analysis.totalScore, reanalyzed?.totalScore, "re-analyzing a RESOLVED mainboard must reproduce the same score")
    }

    @Test
    fun `a resolution beyond the group's remainingSlots cap is clamped, never over-applied`() = runTest {
        val useCase = newUseCase()
        val (commander, owned, manualAdds) = scarcityOwned()
        val identity = setOf(ManaColor.G)

        val draft = useCase.buildWithGroups(DeckFormat.COMMANDER, commander, StrategyPick.Custom, identity, owned, manualAdds = manualAdds, deckId = "finalize-cap")
        val group = draft.ambiguityGroups.first()
        // Ask for every candidate the group offers, even though only `remainingSlots` tentative
        // slots physically exist for this role -- finalize must clamp, not throw or overfill.
        val defaultOutcome = useCase.finalize(draft, resolutions = emptyMap())
        val overAskedOutcome = useCase.finalize(draft, resolutions = mapOf(group.sectionId to group.candidateIds))

        val defaultTotal = defaultOutcome.result.entries.sumOf { it.quantity }
        val overAskedTotal = overAskedOutcome.result.entries.sumOf { it.quantity }
        assertEquals(defaultTotal, overAskedTotal, "asking for more replacements than a group has slots must not change the deck's total card count")

        // Exactly `remainingSlots` tentative defaults may be swapped out -- never more, regardless
        // of how many ids the caller asked for.
        val tentativeDefaults = draft.tentativeByRole.getValue(group.sectionId).toSet()
        val overAskedIds = overAskedOutcome.result.entries.map { it.card.scryfallId }.toSet()
        val evictedDefaults = tentativeDefaults - overAskedIds
        assertEquals(group.remainingSlots, evictedDefaults.size, "over-asking must still evict exactly remainingSlots defaults, not more")
    }
}
