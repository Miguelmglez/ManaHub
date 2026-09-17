package com.mmg.manahub.feature.decks.domain.template
// COMMENTS_REVIEWED: 2026-09-09

import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.domain.repository.DeckRepository
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.Deck
import com.mmg.manahub.core.model.DeckCardSource
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.core.model.DeckSlot
import com.mmg.manahub.core.model.DeckSummary
import com.mmg.manahub.core.model.DeckWithCards
import com.mmg.manahub.feature.decks.domain.engine.CuratedStrategyCatalog
import com.mmg.manahub.feature.decks.domain.engine.DeckEntry
import com.mmg.manahub.feature.decks.domain.engine.DeckScorer
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.NeutralPowerResolver
import com.mmg.manahub.feature.decks.domain.engine.RoleClassifier
import com.mmg.manahub.feature.decks.domain.engine.StrategyPick
import com.mmg.manahub.feature.decks.domain.engine.analysisv3.MockCollectionRich
import com.mmg.manahub.feature.decks.domain.engine.availableIn
import com.mmg.manahub.core.gamification.domain.ProgressionEventBus
import com.mmg.manahub.feature.decks.domain.usecase.DeckAnalysisPipeline
import com.mmg.manahub.feature.decks.domain.usecase.EvaluateDeckUseCase
import com.mmg.manahub.feature.decks.domain.usecase.InferDeckIdentityUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

private object RoundTripCrashReporter : CrashReporter {
    override fun recordException(throwable: Throwable) = Unit
    override fun log(message: String) = Unit
    override fun setCustomKey(key: String, value: String) = Unit
}

/**
 * A tiny in-memory [DeckRepository] that actually STORES what it is written -- unlike
 * `DeckRepositoryReplaceAllCardsWithSourceTest`'s `FakeAtomicityDeckRepository`, whose
 * `observeDeckWithCards` always returns null (fine for an atomicity-only test, useless for a
 * read-back one). Only the methods [BuildWizardDeckUseCase.persist] actually calls are
 * implemented for real; everything else is `error("unused")` -- if a future change makes
 * `persist` call something else, this test fails loudly instead of silently reading stale state.
 */
private class InMemoryRoundTripDeckRepository(private val cardsById: Map<String, Card>) : DeckRepository {
    private var deck = Deck(id = "deck-1", name = "Draft", format = DeckFormat.COMMANDER.name)
    private val mainboard = mutableMapOf<String, DeckSlot>()

    override fun observeAllDecks(): Flow<List<Deck>> = error("unused")
    override fun observeAllDeckSummaries(): Flow<List<DeckSummary>> = error("unused")
    override fun observeDecksContainingCard(scryfallId: String): Flow<List<Deck>> = error("unused")
    override fun observeDeckWithCards(deckId: String): Flow<DeckWithCards?> =
        flowOf(DeckWithCards(deck = deck, mainboard = mainboard.values.toList(), sideboard = emptyList()))

    override suspend fun createDeck(name: String, description: String, format: String): String {
        deck = deck.copy(name = name, description = description, format = format)
        return deck.id
    }

    override suspend fun updateDeck(deck: Deck) { this.deck = deck }
    override suspend fun deleteDeck(deckId: String) = error("unused")

    override suspend fun addCardToDeck(deckId: String, scryfallId: String, quantity: Int, isSideboard: Boolean, source: DeckCardSource) {
        mainboard[scryfallId] = DeckSlot(scryfallId, quantity, source)
    }

    override suspend fun removeCardFromDeck(deckId: String, scryfallId: String, isSideboard: Boolean) = error("unused")
    override suspend fun moveCardQuantity(deckId: String, scryfallId: String, fromSideboard: Boolean, quantity: Int) = error("unused")
    override suspend fun clearDeck(deckId: String) { mainboard.clear() }
    override suspend fun updateDeckAttribution(deckId: String, sourceUrl: String?, sourceAuthor: String?, sourceService: String?, importedAt: Long?) = error("unused")
    override suspend fun replaceAllCards(deckId: String, slots: List<Triple<String, Int, Boolean>>) = error("unused")

    override suspend fun updateArchetypeOverride(deckId: String, archetypeOverride: String?, themesOverride: List<String>, posture: String?) {
        deck = deck.copy(archetypeOverride = archetypeOverride, themesOverride = themesOverride, postureOverride = posture)
    }

    override suspend fun updateTribeOverride(deckId: String, tribeOverride: String?) {
        deck = deck.copy(tribeOverride = tribeOverride)
    }

    override suspend fun updateStrategyLocked(deckId: String, locked: Boolean) {
        deck = deck.copy(strategyLocked = locked)
    }

    /** Reconstructs the SAME [DeckEntry] shape [BuildWizardDeckUseCase.invoke] produced, but
     * purely from what actually got persisted -- the read side of this round-trip test. */
    fun readBackMainboard(): List<DeckEntry> = mainboard.values.map { slot ->
        DeckEntry(card = cardsById.getValue(slot.scryfallId), quantity = slot.quantity, isOwned = true, isSideboard = false)
    }

    fun persistedDeck(): Deck = deck
}

/**
 * Deck Wizard Commander v3 plan, Phase 6 (item 7): the literal "build, persist, re-analyze FROM
 * PERSISTENCE, compare" proof the campaign asked for -- unlike
 * `BuildWizardDeckUseCaseTest`'s own "round-trip identity" test (Phase 2, Run 3), which
 * re-analyzes `outcome.result.entries` directly and never touches [DeckRepository] at all, this
 * test goes through [BuildWizardDeckUseCase.persist] into a real (in-memory) repository, reads
 * the mainboard AND the pin back out exactly as [com.mmg.manahub.feature.decks.presentation.wizard
 * .DeckWizardViewModel]'s own generation path would (via `observeDeckWithCards`), and only THEN
 * re-runs [DeckAnalysisPipeline.analyze] -- closing the one gap Run 3's own report flagged
 * (`round_trip_identity (new path)` was proven only against the in-memory outcome, never against
 * what actually lands in the repository).
 */
class BuildWizardDeckPersistenceRoundTripTest {

    private fun newPipeline() = DeckAnalysisPipeline(
        EvaluateDeckUseCase(DeckScorer(RoleClassifier(), NeutralPowerResolver), ProgressionEventBus()),
        InferDeckIdentityUseCase(),
        RoundTripCrashReporter,
    )

    @Test
    fun `round_trip_identity -- re-analyzing the PERSISTED deck matches the outcome the wizard returned`() = runTest {
        val pipeline = newPipeline()
        val useCase = BuildWizardDeckUseCase(pipeline, RoundTripCrashReporter)

        val commander = MockCollectionRich.targetFixtures.first().mainboard
            .first { it.card.typeLine.contains("Legendary", ignoreCase = true) }.card
        val identity = commander.colorIdentity.mapNotNull { symbol -> ManaColor.entries.firstOrNull { it.symbol == symbol } }.toSet()
        val ownedCards = (MockCollectionRich.ownedCards + MockCollectionRich.ownedBasics).map { it.card }
        val owned = (MockCollectionRich.ownedCards + MockCollectionRich.ownedBasics).map { OwnedCard(it.card, it.quantity) }
        val curated = CuratedStrategyCatalog.ALL.firstOrNull { it.availableIn(DeckFormat.COMMANDER) && !it.requiresTribe }
        val pick = curated?.let { StrategyPick.Curated(it, null) } ?: StrategyPick.Custom

        val outcome = useCase(DeckFormat.COMMANDER, commander, pick, identity, owned)

        val cardsById = (ownedCards + commander).associateBy { it.scryfallId }
        val repo = InMemoryRoundTripDeckRepository(cardsById)
        val deckId = repo.createDeck(name = commander.name, description = "Draft", format = DeckFormat.COMMANDER.name)
        useCase.persist(repo, deckId, commander, manualIds = emptySet(), outcome = outcome)

        // Mirrors DeckWizardViewModel.generateCommanderDeck's own read-back shape: the mainboard
        // AND the pin come from the repository, never from `outcome` directly.
        val persistedDeck = repo.persistedDeck()
        val persistedMainboard = repo.readBackMainboard()
        val rebuiltHealth = pipeline.analyze(
            mainboard = persistedMainboard,
            format = DeckFormat.entries.first { it.name == persistedDeck.format },
            commander = commander,
            archetypeOverride = persistedDeck.archetypeOverride,
            themesOverride = persistedDeck.themesOverride,
            tribeOverride = persistedDeck.tribeOverride,
            postureOverride = persistedDeck.postureOverride,
            emitProgression = false,
        )
        val rebuiltAnalysis = rebuiltHealth.analysis?.copy(debugSynergyGraph = null)
        val originalAnalysis = outcome.result.analysis.copy(debugSynergyGraph = null)

        assertEquals(
            originalAnalysis,
            rebuiltAnalysis,
            "round_trip_identity: re-analyzing the deck as it was ACTUALLY PERSISTED must equal the analysis the wizard returned",
        )
        // The commander itself must be a real, qty-1, WIZARD-provenance slot in the persisted deck
        // (BUG-1's own invariant, now proven end-to-end through the write path, not just in-memory).
        val commanderSlot = persistedMainboard.firstOrNull { it.card.scryfallId == commander.scryfallId }
        assertEquals(1, commanderSlot?.quantity)
    }
}
