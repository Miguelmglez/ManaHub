package com.mmg.manahub.feature.decks.domain.usecase
// COMMENTS_REVIEWED: 2026-09-09

import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.core.model.TagCategory
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.analysisv3.MockCollectionRich
import com.mmg.manahub.feature.decks.domain.engine.analysisv3.MockCollectionThin
import com.mmg.manahub.feature.decks.domain.engine.analysisv3.fixture01EdgarMarkov
import com.mmg.manahub.feature.decks.domain.engine.analysisv3.fixture02Meren
import com.mmg.manahub.feature.decks.domain.engine.analysisv3.fixture03Karlov
import com.mmg.manahub.feature.decks.domain.engine.analysisv3.fixture04Omnath
import com.mmg.manahub.feature.decks.domain.engine.analysisv3.fixture05Urza
import com.mmg.manahub.feature.decks.domain.engine.analysisv3.fixture07Brago
import com.mmg.manahub.feature.decks.domain.engine.availableIn
import com.mmg.manahub.feature.decks.domain.template.OwnedCard
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Deck Wizard Commander v3 plan, Phase 4.1 — [RecommendCommanderStrategiesUseCase] over the v3
 * corpus fixture commanders (no gitignored real-collection data needed).
 */
class RecommendCommanderStrategiesUseCaseTest {

    private val useCase = RecommendCommanderStrategiesUseCase()

    private fun ownedFrom(vararg pools: List<com.mmg.manahub.feature.decks.domain.engine.analysisv3.MockCollectionCard>): List<OwnedCard> =
        pools.flatMap { pool -> pool.map { OwnedCard(it.card, it.quantity) } }

    private fun top3Ids(result: List<StrategyRecommendation>): List<String> = result.take(3).map { it.strategy.id }

    @Test
    fun `Edgar Markov -- tribal ranks top 3`() {
        val commander = fixture01EdgarMarkov().mainboard.first { it.card.scryfallId == "cmd-edgar-markov" }.card
        val owned = ownedFrom(MockCollectionRich.ownedCards, MockCollectionRich.ownedBasics)
        val result = useCase(DeckFormat.COMMANDER, commander, commander.colorIdentity.toManaColors(), owned)
        assertTrue("tribal" in top3Ids(result), "expected 'tribal' in top 3, got ${top3Ids(result)}")
        val tribal = result.first { it.strategy.id == "tribal" }
        assertTrue(tribal.tribe == "tribe:vampire", "expected derived tribe vampire, got ${tribal.tribe}")
    }

    @Test
    fun `Meren -- aristocrats ranks top 3`() {
        val commander = fixture02Meren().mainboard.first { it.card.name == "Meren of Clan Nel Toth" }.card
        val owned = ownedFrom(MockCollectionRich.ownedCards, MockCollectionRich.ownedBasics)
        val result = useCase(DeckFormat.COMMANDER, commander, commander.colorIdentity.toManaColors(), owned)
        assertTrue("aristocrats" in top3Ids(result), "expected 'aristocrats' in top 3, got ${top3Ids(result)}")
    }

    @Test
    fun `Karlov -- lifegain ranks top 3`() {
        val commander = fixture03Karlov().mainboard.first { it.card.name == "Karlov of the Ghost Council" }.card
        val owned = ownedFrom(MockCollectionRich.ownedCards, MockCollectionRich.ownedBasics)
        val result = useCase(DeckFormat.COMMANDER, commander, commander.colorIdentity.toManaColors(), owned)
        assertTrue("lifegain" in top3Ids(result), "expected 'lifegain' in top 3, got ${top3Ids(result)}")
    }

    // Urza/Brago's own fixture-authored oracle text is too minimal to trip ArchetypeRoleClassifier's
    // matchers or SynergyGraph's axis producers at all (verified via debug instrumentation during
    // this phase -- both classify to an EMPTY role/axis map for these two commanders specifically),
    // so signal (a) alone contributes zero for them -- an honest, documented degrade case (every
    // signal must be able to be zero, per this use case's own KDoc). Production would carry these two
    // commanders on their REAL card_strategy_tags payload (signal b); these two tests supply the
    // equivalent CardTag a real tagging pass would attach, via the SAME DeckIdentitySeedTags reverse
    // map the use case itself reads (`CardTag("artifacts_matter", ...)` / `CardTag.BLINK`).

    @Test
    fun `Urza -- artifacts or combo ranks top 3 (via card_strategy_tags signal)`() {
        val commander = fixture05Urza().mainboard.first { it.card.name == "Urza, Lord High Artificer" }.card
        val owned = ownedFrom(MockCollectionRich.ownedCards, MockCollectionRich.ownedBasics)
        val ownTags = listOf(CardTag("artifacts_matter", TagCategory.STRATEGY))
        val result = useCase(DeckFormat.COMMANDER, commander, commander.colorIdentity.toManaColors(), owned, ownTags = ownTags)
        val top3 = top3Ids(result)
        assertTrue("artifacts" in top3 || "combo" in top3, "expected 'artifacts' or 'combo' in top 3, got $top3")
    }

    @Test
    fun `Omnath -- landfall or big_mana ranks top 3`() {
        val commander = fixture04Omnath().mainboard.first { it.card.name == "Omnath, Locus of Rage" }.card
        val result = useCase(DeckFormat.COMMANDER, commander, commander.colorIdentity.toManaColors())
        val top3 = top3Ids(result)
        assertTrue("landfall" in top3 || "big_mana" in top3, "expected 'landfall' or 'big_mana' in top 3, got $top3")
    }

    @Test
    fun `Brago -- blink ranks top 3 (via card_strategy_tags signal)`() {
        val commander = fixture07Brago().mainboard.first { it.card.name == "Brago, King Eternal" }.card
        val ownTags = listOf(CardTag.BLINK)
        val result = useCase(DeckFormat.COMMANDER, commander, commander.colorIdentity.toManaColors(), ownTags = ownTags)
        assertTrue("blink" in top3Ids(result), "expected 'blink' in top 3, got ${top3Ids(result)}")
    }

    @Test
    fun `every recommendation is available in the requested format`() {
        val commander = fixture01EdgarMarkov().mainboard.first { it.card.scryfallId == "cmd-edgar-markov" }.card
        val result = useCase(DeckFormat.COMMANDER_CASUAL, commander, commander.colorIdentity.toManaColors())
        assertTrue(result.isNotEmpty())
        assertTrue(result.all { it.strategy.availableIn(DeckFormat.COMMANDER_CASUAL) })
    }

    @Test
    fun `owned support changes ordering between thin and rich collections`() {
        val commander = MockCollectionThin.commander
        val identity = commander.colorIdentity.toManaColors()
        val thinOrder = useCase(DeckFormat.COMMANDER, commander, identity, ownedFrom(MockCollectionThin.ownedCards, MockCollectionThin.ownedBasics)).map { it.strategy.id }
        val richOrder = useCase(DeckFormat.COMMANDER, commander, identity, ownedFrom(MockCollectionRich.ownedCards, MockCollectionRich.ownedBasics)).map { it.strategy.id }
        assertTrue(thinOrder != richOrder, "expected owned support to change ordering between thin and rich collections")
    }

    @Test
    fun `all signals empty or absent never throws and returns a valid list`() {
        val commander = fixture01EdgarMarkov().mainboard.first { it.card.scryfallId == "cmd-edgar-markov" }.card
        val result = useCase(DeckFormat.COMMANDER, commander, commander.colorIdentity.toManaColors())
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun `Commander Casual gets the same recommendation list as Commander`() {
        val commander = fixture02Meren().mainboard.first { it.card.name == "Meren of Clan Nel Toth" }.card
        val owned = ownedFrom(MockCollectionRich.ownedCards, MockCollectionRich.ownedBasics)
        val identity = commander.colorIdentity.toManaColors()
        val commanderResult = useCase(DeckFormat.COMMANDER, commander, identity, owned).map { it.strategy.id to it.score }
        val casualResult = useCase(DeckFormat.COMMANDER_CASUAL, commander, identity, owned).map { it.strategy.id to it.score }
        assertTrue(commanderResult == casualResult)
    }

    @Test
    fun `unresolved tribal entries never occupy the top 3`() {
        // A colorless-flavored commander with no tribe signal at all -- "tribal" should still be
        // returned (Custom is always separately offered by the caller, never a use-case concern) but
        // pushed to the end, never into a top-6 "Recommended" slice.
        val commander = fixture05Urza().mainboard.first { it.card.name == "Urza, Lord High Artificer" }.card
        val result = useCase(DeckFormat.COMMANDER, commander, commander.colorIdentity.toManaColors())
        val tribalIndex = result.indexOfFirst { it.strategy.id == "tribal" }
        assertTrue(tribalIndex == -1 || tribalIndex >= result.size - 3, "expected 'tribal' unresolved and pushed to the end, index=$tribalIndex of ${result.size}")
    }
}

private fun List<String>.toManaColors(): Set<ManaColor> =
    mapNotNull { symbol -> ManaColor.entries.firstOrNull { it.symbol == symbol } }.toSet()
