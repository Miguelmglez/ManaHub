package com.mmg.manahub.feature.decks.domain.usecase

import com.mmg.manahub.core.domain.model.CardTag
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.core.gamification.domain.ProgressionEventBus
import com.mmg.manahub.core.gamification.domain.event.ProgressionEvent
import com.mmg.manahub.feature.decks.domain.engine.DeckRole
import com.mmg.manahub.feature.decks.domain.engine.DeckScorer
import com.mmg.manahub.feature.decks.domain.engine.DeckWarning
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.RoleClassifier
import com.mmg.manahub.feature.decks.domain.engine.card
import com.mmg.manahub.feature.decks.domain.engine.entry
import com.mmg.manahub.feature.decks.domain.engine.fixedPower
import com.mmg.manahub.feature.decks.domain.engine.landCard
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [EvaluateDeckUseCase].
 *
 * The use case is exercised against a REAL [DeckScorer] (with a real [RoleClassifier] and a
 * deterministic fixed [com.mmg.manahub.feature.decks.domain.engine.PowerResolver]), so the
 * assertions also cover the wiring into the engine.
 */
class EvaluateDeckUseCaseTest {

    private val dispatcher = StandardTestDispatcher()
    private val scorer = DeckScorer(RoleClassifier(), fixedPower(normalized = 0.6f))
    private val eventBus = ProgressionEventBus()
    private val useCase = EvaluateDeckUseCase(scorer, eventBus, dispatcher)

    @Test
    fun `derives color identity from mainboard cards`() = runTest(dispatcher) {
        val mainboard = listOf(
            entry(card(id = "c1", colorIdentity = listOf("U"))),
            entry(card(id = "c2", colorIdentity = listOf("G"))),
            entry(card(id = "c3", colorIdentity = listOf("U", "G"))),
            entry(card(id = "c4", colorIdentity = emptyList())), // colorless contributes nothing
        )

        val result = useCase(mainboard, DeckFormat.COMMANDER)

        assertEquals(setOf(ManaColor.U, ManaColor.G), result.profile.colorIdentity)
    }

    @Test
    fun `commander identity is merged and unknown symbols are dropped`() = runTest(dispatcher) {
        val mainboard = listOf(entry(card(id = "c1", colorIdentity = listOf("U"))))

        // "X" is not a WUBRG color and must be ignored; "B" must be added.
        val result = useCase(mainboard, DeckFormat.COMMANDER, commanderIdentity = setOf("B", "X"))

        assertEquals(setOf(ManaColor.U, ManaColor.B), result.profile.colorIdentity)
    }

    @Test
    fun `lands are excluded from average mana value and curve`() = runTest(dispatcher) {
        val mainboard = listOf(
            entry(card(id = "s1", cmc = 2.0)),
            entry(card(id = "s2", cmc = 4.0)),
            entry(landCard(id = "l1")),
            entry(landCard(id = "l2")),
        )

        val result = useCase(mainboard, DeckFormat.COMMANDER)

        // avg of (2,4) = 3.0; lands (cmc 0) must NOT pull it down.
        assertEquals(3.0, result.evaluation.avgCmc, 0.001)
        // The curve histogram counts only non-lands.
        assertEquals(2, result.evaluation.curveHistogram.values.sum())
    }

    @Test
    fun `commander deck with too few lands raises a TooFewLands warning`() = runTest(dispatcher) {
        // 30 non-land spells + only 5 lands → far below the Commander land floor (35).
        val spells = (1..30).map { entry(card(id = "spell-$it", cmc = 3.0)) }
        val lands = (1..5).map { entry(landCard(id = "land-$it")) }

        val result = useCase(spells + lands, DeckFormat.COMMANDER)

        assertEquals(5, result.evaluation.landCount)
        assertTrue(result.evaluation.warnings.any { it is DeckWarning.TooFewLands })
    }

    @Test
    fun `role coverage reflects classified spells and health score is in range`() = runTest(dispatcher) {
        val mainboard = listOf(
            entry(card(id = "ramp", tags = listOf(CardTag.MANA_ROCK))),
            entry(card(id = "draw", tags = listOf(CardTag.DRAW_ENGINE))),
            entry(card(id = "removal", tags = listOf(CardTag.REMOVAL))),
        ) + (1..37).map { entry(landCard(id = "land-$it")) }

        val result = useCase(mainboard, DeckFormat.COMMANDER)

        val ramp = result.evaluation.roleCoverage.first { it.role == DeckRole.RAMP }
        assertEquals(1, ramp.current)
        assertTrue("ideal should be positive for RAMP", ramp.ideal > 0)

        assertEquals(37, result.evaluation.landCount)
        assertTrue(result.evaluation.healthScore in 0..100)
    }

    @Test
    fun `a successful evaluation emits a deck_doctor FeatureExplored event`() = runTest(dispatcher) {
        val emitted = mutableListOf<ProgressionEvent>()
        val collector = backgroundScope.launch { eventBus.events.collect { emitted.add(it) } }
        runCurrent() // let the collector subscribe before the use case emits

        useCase(listOf(entry(card(id = "c1"))), DeckFormat.COMMANDER)
        runCurrent()

        val explore = emitted.filterIsInstance<ProgressionEvent.FeatureExplored>()
        assertEquals(1, explore.size)
        assertEquals("deck_doctor", explore.single().featureKey)
        collector.cancel()
    }
}
