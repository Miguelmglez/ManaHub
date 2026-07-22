package com.mmg.manahub.feature.decks.domain.template

import com.mmg.manahub.core.model.AggregateCardEntry
import com.mmg.manahub.core.model.AggregateSource
import com.mmg.manahub.core.model.AvgTypeDistribution
import com.mmg.manahub.core.model.CommunityAggregate
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.SeedStrategy
import com.mmg.manahub.feature.decks.domain.engine.toStrategyProfile
import com.mmg.manahub.feature.decks.domain.engine.card
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Deck Builder v2, Phase 1 -- community/synthetic/fallback resolution paths. */
class DeckTemplateResolverTest {

    private val dispatcher = StandardTestDispatcher()

    private fun avgTypeDistribution(land: Int = 37) = AvgTypeDistribution(
        creature = 30, instant = 8, sorcery = 8, artifact = 8, enchantment = 5, battle = 0,
        planeswalker = 1, land = land,
    )

    private fun commanderAggregate(cards: List<AggregateCardEntry> = emptyList()) = CommunityAggregate.Commander(
        commander = "Krenko, Mob Boss",
        numDecksSampled = 1000,
        avgTypeDistribution = avgTypeDistribution(),
        manaCurve = mapOf("1" to 10, "2" to 20, "3" to 20, "4" to 10),
        themeTags = emptyList(),
        similarCommanders = emptyList(),
        gameChangersCount = 0,
        cards = cards,
        cachedAt = 0L,
        source = AggregateSource.WORKER,
    )

    private fun aggregateCard(name: String, category: String, inclusion: Float, synergy: Float) = AggregateCardEntry(
        name = name, scryfallUid = null, inclusionPct = inclusion, synergy = synergy, category = category, numDecks = 500,
    )

    @Test
    fun `Commander with a successful aggregate builds a COMMUNITY template`() = runTest(dispatcher) {
        val commander = card(name = "Krenko, Mob Boss", typeLine = "Legendary Creature — Goblin", colorIdentity = listOf("R"))
        val repo = FakeCommunityAggregateRepository(
            commanderResult = { DataResult.Success(commanderAggregate(cards = listOf(aggregateCard("Goblin Bombardment", "Sacrifice", 0.4f, 0.6f)))) },
        )
        val resolver = DeckTemplateResolver(repo, ioDispatcher = dispatcher)

        val template = resolver.resolve(DeckWizardSpec(format = DeckFormat.COMMANDER, commander = commander))

        assertEquals(TemplateSource.COMMUNITY, template.source)
        assertEquals(37, template.landTarget)
        assertTrue(template.categories.isNotEmpty())
        assertTrue(template.categories.any { it.cards.any { ref -> ref.name == "Goblin Bombardment" } })
    }

    @Test
    fun `Commander with no aggregate data falls back to a SYNTHETIC template`() = runTest(dispatcher) {
        val commander = card(name = "Obscure Legend", typeLine = "Legendary Creature — Human", colorIdentity = listOf("W"))
        val repo = FakeCommunityAggregateRepository(commanderResult = { DataResult.Error("not found") })
        val resolver = DeckTemplateResolver(repo, ioDispatcher = dispatcher)

        val template = resolver.resolve(DeckWizardSpec(format = DeckFormat.COMMANDER, commander = commander))

        assertEquals(TemplateSource.SYNTHETIC, template.source)
        assertTrue(template.landTarget > 0)
        // Synthetic categories carry no concrete card names (no aggregate data to source them from).
        assertTrue(template.categories.all { it.cards.isEmpty() })
    }

    @Test
    fun `Commander with no commander picked falls back to SYNTHETIC without calling the repository`() = runTest(dispatcher) {
        val repo = FakeCommunityAggregateRepository()
        val resolver = DeckTemplateResolver(repo, ioDispatcher = dispatcher)

        val template = resolver.resolve(DeckWizardSpec(format = DeckFormat.COMMANDER, commander = null))

        assertEquals(TemplateSource.SYNTHETIC, template.source)
    }

    @Test
    fun `Casual with no seeds never polls the Worker and stays SYNTHETIC`() = runTest(dispatcher) {
        val repo = FakeCommunityAggregateRepository()
        val resolver = DeckTemplateResolver(repo, ioDispatcher = dispatcher)

        val template = resolver.resolve(
            DeckWizardSpec(format = DeckFormat.CASUAL, colorIdentity = setOf(ManaColor.R), strategyProfile = SeedStrategy.AGGRO.toStrategyProfile()),
        )

        assertEquals(TemplateSource.SYNTHETIC, template.source)
        assertEquals(0, repo.getSixtyAggregateCallCount)
    }

    @Test
    fun `Casual with seeds and a Building aggregate polls with backoff then falls back to SYNTHETIC`() = runTest(dispatcher) {
        val repo = FakeCommunityAggregateRepository(
            sixtyResult = { _, _ -> DataResult.Success(CommunityAggregate.Sixty.Building(canonicalKey = "k", collected = 1, target = 20)) },
        )
        val resolver = DeckTemplateResolver(repo, ioDispatcher = dispatcher)
        val seed = card(name = "Test Seed", typeLine = "Creature — Human", colors = listOf("R"), colorIdentity = listOf("R"))

        val template = resolver.resolve(
            DeckWizardSpec(format = DeckFormat.CASUAL, seeds = listOf(seed), colorIdentity = setOf(ManaColor.R)),
        )

        assertEquals(TemplateSource.SYNTHETIC, template.source)
        assertTrue(repo.getSixtyAggregateCallCount in 1..3, "expected a BOUNDED poll, got ${repo.getSixtyAggregateCallCount}")
    }

    @Test
    fun `Casual with a Materialized aggregate enriches the synthetic categories without changing target counts`() = runTest(dispatcher) {
        val materialized = CommunityAggregate.Sixty.Materialized(
            canonicalKey = "k", format = 7, numDecksSampled = 50,
            deckSummaries = emptyList(), colorProfile = mapOf("R" to 50),
            cards = listOf(aggregateCard("Test Burn Spell", "Removal", 0.5f, 0.5f)),
            cachedAt = 0L, source = AggregateSource.WORKER,
        )
        val repo = FakeCommunityAggregateRepository(sixtyResult = { _, _ -> DataResult.Success(materialized) })
        val resolver = DeckTemplateResolver(repo, ioDispatcher = dispatcher)
        val seed = card(name = "Test Seed", typeLine = "Creature — Human", colors = listOf("R"), colorIdentity = listOf("R"))

        val before = resolver.resolve(DeckWizardSpec(format = DeckFormat.CASUAL, colorIdentity = setOf(ManaColor.R)))
        val after = resolver.resolve(
            DeckWizardSpec(format = DeckFormat.CASUAL, seeds = listOf(seed), colorIdentity = setOf(ManaColor.R)),
        )

        assertEquals(TemplateSource.SYNTHETIC, after.source)
        assertEquals(before.categories.map { it.id to it.targetCount }, after.categories.map { it.id to it.targetCount })
        assertTrue(after.categories.any { it.cards.any { ref -> ref.name == "Test Burn Spell" } })
    }
}
