package com.mmg.manahub.feature.decks.domain.template

import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.core.model.UserCard
import com.mmg.manahub.core.model.UserCardWithCard
import com.mmg.manahub.feature.decks.domain.engine.DeckScorer
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.NeutralPowerResolver
import com.mmg.manahub.feature.decks.domain.engine.RoleClassifier
import com.mmg.manahub.feature.decks.domain.engine.SeedStrategy
import com.mmg.manahub.feature.decks.domain.engine.card
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Deck Builder v2, Phase 2 -- [BuildDeckFromTemplateUseCase] golden-style coverage. */
class BuildDeckFromTemplateUseCaseTest {

    private val dispatcher = StandardTestDispatcher()
    private val scorer = DeckScorer(RoleClassifier(), NeutralPowerResolver)

    private fun owned(name: String, quantity: Int, builder: () -> com.mmg.manahub.core.model.Card = { card(name = name) }) =
        UserCardWithCard(
            userCard = UserCard(id = "uc-$name", scryfallId = "scry-$name", quantity = quantity),
            card = builder(),
        )

    private fun basicLandRepository(extra: FakeCardRepository = FakeCardRepository()): FakeCardRepository {
        listOf("Plains", "Island", "Swamp", "Mountain", "Forest").forEach { name ->
            extra.seed(card(id = "basic-$name", name = name, typeLine = "Basic Land — $name", colors = emptyList(), colorIdentity = emptyList()))
        }
        return extra
    }

    private fun useCase(
        cardRepository: FakeCardRepository = basicLandRepository(),
        communityRepository: FakeCommunityAggregateRepository = FakeCommunityAggregateRepository(),
    ) = BuildDeckFromTemplateUseCase(
        deckTemplateResolver = DeckTemplateResolver(communityRepository, ioDispatcher = dispatcher),
        deckScorer = scorer,
        cardRepository = cardRepository,
        ioDispatcher = dispatcher,
    )

    @Test
    fun `Commander build fails validation with no commander picked`() = runTest(dispatcher) {
        val events = useCase().invoke(DeckWizardSpec(format = DeckFormat.COMMANDER), emptyList()).toList()
        val failed = events.filterIsInstance<TemplateBuildProgress.Failed>().single()
        assertEquals(BuildStage.VALIDATING, failed.stage)
        assertTrue(events.none { it is TemplateBuildProgress.Complete })
    }

    @Test
    fun `Commander build fails validation when a seed is outside the commander's color identity`() = runTest(dispatcher) {
        val commander = card(id = "cmd", name = "Mono Red Commander", typeLine = "Legendary Creature — Human", colorIdentity = listOf("R"))
        val offColorSeed = card(id = "seed", name = "Blue Seed", colorIdentity = listOf("U"))
        val events = useCase().invoke(
            DeckWizardSpec(format = DeckFormat.COMMANDER, commander = commander, seeds = listOf(offColorSeed)),
            emptyList(),
        ).toList()
        val failed = events.filterIsInstance<TemplateBuildProgress.Failed>().single()
        assertEquals(BuildStage.VALIDATING, failed.stage)
    }

    @Test
    fun `seeds are always in the built deck even when off the synthetic template's categories`() = runTest(dispatcher) {
        val commander = card(id = "cmd", name = "Test Commander", typeLine = "Legendary Creature — Human", colorIdentity = listOf("R"))
        // A weird oddball card that resolves to a made-up strategy tag category the template never targets.
        val oddSeed = card(id = "seed", name = "Odd Seed", typeLine = "Sorcery", colorIdentity = listOf("R"), tags = listOf(CardTag.STAX))
        val result = runToCompletion(useCase(), DeckWizardSpec(format = DeckFormat.COMMANDER, commander = commander, seeds = listOf(oddSeed)))
        assertTrue(result.deckCards.any { it.card.scryfallId == "seed" }, "seed must always be present in the built deck")
    }

    @Test
    fun `zero alphabetical Scryfall searches during a full build`() = runTest(dispatcher) {
        val commander = card(id = "cmd", name = "Test Commander", typeLine = "Legendary Creature — Human", colorIdentity = listOf("R"))
        val repo = basicLandRepository()
        val collection = listOf(
            owned("Burn A", 4) { card(id = "burn-a", name = "Burn A", colorIdentity = listOf("R"), oracleText = "Deals 3 damage to target creature.") },
        )
        runToCompletion(useCase(cardRepository = repo), DeckWizardSpec(format = DeckFormat.COMMANDER, commander = commander), collection)
        assertEquals(0, repo.searchWithRawQueryCallCount)
    }

    @Test
    fun `deck cards and community suggestions are never mixed in the same structure`() = runTest(dispatcher) {
        val commander = card(id = "cmd", name = "Test Commander", typeLine = "Legendary Creature — Human", colorIdentity = listOf("R"))
        val result = runToCompletion(useCase(), DeckWizardSpec(format = DeckFormat.COMMANDER, commander = commander))
        val deckIds = result.deckCards.map { it.card.scryfallId }.toSet()
        val suggestionIds = result.communitySuggestions.flatMap { it.suggestions }.map { it.card.scryfallId }.toSet()
        assertTrue(deckIds.intersect(suggestionIds).isEmpty(), "an owned deck card must never also appear as an unowned suggestion")
    }

    @Test
    fun `Casual playset fill grants more copies to the best-fitting card in a category, capped by owned quantity`() = runTest(dispatcher) {
        val bestRemoval = card(id = "best", name = "Best Removal", colors = listOf("R"), colorIdentity = listOf("R"), oracleText = "Destroy target creature.")
        val worseRemoval = card(id = "worse", name = "Worse Removal", colors = listOf("R"), colorIdentity = listOf("R"), oracleText = "Destroy target tapped creature.")
        val collection = listOf(owned("Best Removal", quantity = 4) { bestRemoval }, owned("Worse Removal", quantity = 1) { worseRemoval })
        val result = runToCompletion(
            useCase(),
            DeckWizardSpec(format = DeckFormat.CASUAL, colorIdentity = setOf(ManaColor.R), strategyHint = SeedStrategy.MIDRANGE),
            collection,
        )
        val bestEntry = result.deckCards.first { it.card.scryfallId == "best" }
        // Owns 4 copies -> the rank-0 (best-fit) importance tier (4x) is fully satisfiable.
        assertEquals(4, bestEntry.quantity)
        val worseEntry = result.deckCards.firstOrNull { it.card.scryfallId == "worse" }
        if (worseEntry != null) {
            // Owns only 1 -> never exceeds the owned-copies cap regardless of its importance tier.
            assertEquals(1, worseEntry.quantity)
        }
    }

    @Test
    fun `Casual colorless build only admits colorless collection cards`() = runTest(dispatcher) {
        // Regression for the "colorIdentity.isEmpty() short-circuits the color filter" bug: a
        // wizard user who picks 0 colors on the Identity step means "build me a colorless deck"
        // (see the "Colorless" chip / deck_seeds_identity_colorless string) -- a colored card must
        // never leak into that build just because spec.colorIdentity happens to be empty.
        // Uses the "removal" role (removal_spot is one of the SIXTY-generic skeleton's actual
        // target categories, per ArchetypeData.generic(SIXTY) -- "ramp" is NOT, so a mana-rock
        // fixture would never be placeable regardless of the color-filter fix under test).
        val colorlessCard = card(
            id = "colorless", name = "Colorless Removal", typeLine = "Sorcery",
            colors = emptyList(), colorIdentity = emptyList(), oracleText = "Destroy target creature.",
        )
        val coloredCard = card(
            id = "colored", name = "Red Removal", typeLine = "Sorcery",
            colors = listOf("R"), colorIdentity = listOf("R"), oracleText = "Destroy target creature.",
        )
        val collection = listOf(owned("Colorless Removal", 2) { colorlessCard }, owned("Red Removal", 2) { coloredCard })
        val result = runToCompletion(
            useCase(),
            DeckWizardSpec(format = DeckFormat.CASUAL, colorIdentity = emptySet()),
            collection,
        )
        assertTrue(
            result.deckCards.any { it.card.scryfallId == "colorless" },
            "a colorless card must be admitted into a colorless (0-color) Casual build",
        )
        assertTrue(
            result.deckCards.none { it.card.scryfallId == "colored" },
            "a colored card must never appear in a colorless Casual build",
        )
    }

    @Test
    fun `Commander build materializes basic lands honoring the color identity`() = runTest(dispatcher) {
        val commander = card(id = "cmd", name = "Mono Red Commander", typeLine = "Legendary Creature — Human", colorIdentity = listOf("R"))
        val result = runToCompletion(useCase(), DeckWizardSpec(format = DeckFormat.COMMANDER, commander = commander))
        val basics = result.deckCards.filter { it.card.typeLine.contains("Basic Land") }
        assertTrue(basics.isNotEmpty(), "a Commander build with fillLands=true must materialize basics")
        assertTrue(basics.all { it.card.name == "Mountain" }, "mono-red identity must only produce Mountains")
    }

    @Test
    fun `Casual build with zero colored mana pips still materializes basics split across the chosen colors`() = runTest(dispatcher) {
        // Regression: BasicLandCalculator.calculate falls back to an even split across
        // `commanderIdentity` only when the mainboard has zero colored pips; for every non-Commander
        // format `commanderIdentity` used to be a bare `null`, so that fallback never fired and the
        // calculator silently returned an ALL-ZERO BasicLandDistribution -- an unplayable 60-card
        // deck with 0 lands. All-colorless owned removal cards reproduce the zero-colored-pip
        // mainboard even though the wizard's OWN colorIdentity pick (W/U) is non-empty.
        val colorlessRemovalA = card(
            id = "removal-a", name = "Colorless Removal A", typeLine = "Artifact",
            colors = emptyList(), colorIdentity = emptyList(), oracleText = "Destroy target creature.",
        )
        val colorlessRemovalB = card(
            id = "removal-b", name = "Colorless Removal B", typeLine = "Artifact",
            colors = emptyList(), colorIdentity = emptyList(), oracleText = "Destroy target tapped creature.",
        )
        val collection = listOf(
            owned("Colorless Removal A", 4) { colorlessRemovalA },
            owned("Colorless Removal B", 4) { colorlessRemovalB },
        )
        val result = runToCompletion(
            useCase(),
            DeckWizardSpec(format = DeckFormat.CASUAL, colorIdentity = setOf(ManaColor.W, ManaColor.U)),
            collection,
        )
        val basics = result.deckCards.filter { it.card.typeLine.contains("Basic Land") }
        assertTrue(
            basics.isNotEmpty(),
            "a Casual build with zero colored mana pips must still materialize basics from the wizard's chosen color identity, not zero",
        )
        val basicNames = basics.map { it.card.name }.toSet()
        assertTrue(
            basicNames.all { it == "Plains" || it == "Island" },
            "basics must be split only across the wizard's chosen colors (W/U), never an unchosen color",
        )
    }

    @Test
    fun `fillLands = false never materializes basics`() = runTest(dispatcher) {
        val commander = card(id = "cmd", name = "Mono Red Commander", typeLine = "Legendary Creature — Human", colorIdentity = listOf("R"))
        val result = runToCompletion(useCase(), DeckWizardSpec(format = DeckFormat.COMMANDER, commander = commander, fillLands = false))
        assertTrue(result.deckCards.none { it.card.typeLine.contains("Basic Land") })
    }

    @Test
    fun `Casual color consistency warning fires only above 2 colors`() = runTest(dispatcher) {
        val twoColor = runToCompletion(useCase(), DeckWizardSpec(format = DeckFormat.CASUAL, colorIdentity = setOf(ManaColor.R, ManaColor.U)))
        assertTrue(!twoColor.colorConsistencyWarning)
        val threeColor = runToCompletion(useCase(), DeckWizardSpec(format = DeckFormat.CASUAL, colorIdentity = setOf(ManaColor.R, ManaColor.U, ManaColor.G)))
        assertTrue(threeColor.colorConsistencyWarning)
    }

    @Test
    fun `archetype override is always a raw enum name, never a display string`() = runTest(dispatcher) {
        val commander = card(id = "cmd", name = "Test Commander", typeLine = "Legendary Creature — Human", colorIdentity = listOf("R"))
        val result = runToCompletion(
            useCase(),
            DeckWizardSpec(format = DeckFormat.COMMANDER, commander = commander, strategyHint = SeedStrategy.AGGRO),
        )
        assertEquals("AGGRO", result.archetypeOverride)
    }

    @Test
    fun `build is deterministic across two runs with the same inputs`() = runTest(dispatcher) {
        val commander = card(id = "cmd", name = "Test Commander", typeLine = "Legendary Creature — Human", colorIdentity = listOf("R"))
        val collection = listOf(
            owned("Burn A", 2) { card(id = "burn-a", name = "Burn A", colorIdentity = listOf("R"), oracleText = "Deals 3 damage to target creature.") },
            owned("Burn B", 2) { card(id = "burn-b", name = "Burn B", colorIdentity = listOf("R"), oracleText = "Deals 2 damage to target creature.") },
        )
        val spec = DeckWizardSpec(format = DeckFormat.COMMANDER, commander = commander)
        val first = runToCompletion(useCase(), spec, collection)
        val second = runToCompletion(useCase(), spec, collection)
        assertEquals(first.deckCards.map { it.card.scryfallId to it.quantity }, second.deckCards.map { it.card.scryfallId to it.quantity })
        assertEquals(first.report, second.report)
    }

    private suspend fun runToCompletion(
        useCase: BuildDeckFromTemplateUseCase,
        spec: DeckWizardSpec,
        collection: List<UserCardWithCard> = emptyList(),
    ): TemplateBuildResult {
        val events = useCase.invoke(spec, collection).toList()
        val complete = events.filterIsInstance<TemplateBuildProgress.Complete>().singleOrNull()
        assertIs<TemplateBuildProgress.Complete>(complete ?: events.last(), "build did not complete: ${events.lastOrNull()}")
        return complete!!.result
    }
}
