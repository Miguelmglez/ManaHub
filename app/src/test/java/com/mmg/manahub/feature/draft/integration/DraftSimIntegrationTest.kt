package com.mmg.manahub.feature.draft.integration

import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.feature.draft.data.engine.DefaultDraftEngine
import com.mmg.manahub.feature.draft.data.engine.HeuristicBotDrafter
import com.mmg.manahub.feature.draft.data.engine.ScoringDraftDeckBuilder
import com.mmg.manahub.feature.draft.data.engine.WeightedBoosterGenerator
import com.mmg.manahub.core.model.DraftConfig
import com.mmg.manahub.core.model.DraftMode
import com.mmg.manahub.core.model.DraftResult
import com.mmg.manahub.core.model.DraftState
import com.mmg.manahub.core.model.DraftStatus
import com.mmg.manahub.core.model.DraftableSet
import com.mmg.manahub.core.domain.repository.DraftSimRepository
import com.mmg.manahub.feature.draft.engine.DraftTestFixtures
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * End-to-end integration test for the Draft Simulator engine pipeline.
 *
 * Wires the real engine components (booster generator, bot drafter, draft engine, deck builder)
 * with an in-memory [FakeDraftSimRepository] and exercises the full happy path:
 * start → draft to completion → build deck → save. No Android framework or Room is involved,
 * so this runs as a pure JVM unit test. All [kotlin.random.Random] instances are seeded so the
 * pipeline is deterministic.
 */
@ExperimentalCoroutinesApi
class DraftSimIntegrationTest {

    private val fakeRepo = FakeDraftSimRepository()
    private val gen = WeightedBoosterGenerator(kotlin.random.Random(1))
    private val bot = HeuristicBotDrafter()
    private val engine = DefaultDraftEngine(gen, bot, kotlin.random.Random(1))
    private val deckScorer = com.mmg.manahub.feature.decks.domain.engine.DeckScorer(com.mmg.manahub.feature.decks.domain.engine.RoleClassifier())
    private val deckBuilder = ScoringDraftDeckBuilder(deckScorer)

    @Test
    fun `full draft flow setup draft deckSaved`() = runTest {
        val config = DraftConfig(setCode = "TST", mode = DraftMode.DRAFT, seatCount = 8, packCount = 3)

        // Start draft.
        val set = DraftTestFixtures.fakeDraftableSet()
        val startState = withContext(Dispatchers.Default) { engine.start(set, config) }
        fakeRepo.saveSession(startState)
        assertEquals(DraftStatus.DRAFTING, startState.status)

        // Pick the first available card from the human pack every cycle until the draft completes.
        var state = startState
        while (!engine.isComplete(state)) {
            val humanPack = state.packsInFlight[0] ?: break
            val card = humanPack.cards.firstOrNull() ?: break
            state = withContext(Dispatchers.Default) {
                engine.applyHumanPick(state, card.card.scryfallId, engine = null)
            }
            fakeRepo.saveSession(state)
        }

        assertEquals(DraftStatus.BUILDING, state.status)

        // Build and save the deck.
        val humanSeat = state.seats.first { it.isHuman }
        val deck = withContext(Dispatchers.Default) { deckBuilder.build(humanSeat) }
        val result = DraftResult(humanSeat, deck)
        val saveResult = fakeRepo.completeAndSaveDeck(result)

        assertTrue(saveResult is DataResult.Success)
        val deckId = (saveResult as DataResult.Success).data
        assertTrue(deckId.isNotBlank())

        // The built deck has a non-empty mainboard plus exactly 17 basic lands.
        assertTrue(deck.mainboard.isNotEmpty())
        val totalBasics = deck.basics.sumOf { it.count }
        assertEquals(17, totalBasics)
    }

    @Test
    fun `sealed flow immediately in building`() = runTest {
        val set = DraftTestFixtures.fakeDraftableSet()
        val config = DraftConfig(setCode = "TST", mode = DraftMode.SEALED, seatCount = 8, packCount = 6)
        val state = withContext(Dispatchers.Default) { engine.start(set, config) }

        assertEquals(DraftStatus.BUILDING, state.status)
        val humanSeat = state.seats.first { it.isHuman }
        // 6 packs × 14 cards (10 common + 3 uncommon + 1 rareMythic) = 84.
        assertEquals(6 * 14, humanSeat.pool.size)
    }
}

/**
 * In-memory [DraftSimRepository] used by [DraftSimIntegrationTest]. Stores sessions keyed by
 * set+mode and records saved deck ids. The set-resolution path returns the canonical test fixture.
 */
private class FakeDraftSimRepository : DraftSimRepository {

    private val sessions = mutableMapOf<String, DraftState>()
    private val savedDecks = mutableListOf<String>()
    private val sessionFlow = MutableStateFlow<DraftState?>(null)

    override suspend fun getDraftableSimSet(setCode: String): DataResult<DraftableSet> =
        DataResult.Success(DraftTestFixtures.fakeDraftableSet())

    override suspend fun getEngineConfig(
        setCode: String,
    ): com.mmg.manahub.core.model.EngineConfig? = null

    override fun observeActiveSession(): Flow<DraftState?> = sessionFlow

    override suspend fun saveSession(state: DraftState) {
        val id = "${state.config.setCode}-${state.config.mode.name}"
        sessions[id] = state
        sessionFlow.value = state
    }

    override suspend fun completeAndSaveDeck(result: DraftResult): DataResult<String> {
        val deckId = "deck-${result.seat.index}-${System.currentTimeMillis()}"
        savedDecks += deckId
        return DataResult.Success(deckId)
    }
}
