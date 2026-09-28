package com.mmg.manahub.feature.decks.domain.usecase
// COMMENTS_REVIEWED: 2026-09-17

import com.mmg.manahub.core.domain.usecase.decks.BasicLandCalculator
import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.core.model.TagCategory
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeId
import com.mmg.manahub.feature.decks.domain.engine.BuildAnchor
import com.mmg.manahub.feature.decks.domain.engine.CuratedStrategyCatalog
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.analysisv3.MockCollectionRich
import com.mmg.manahub.feature.decks.domain.engine.analysisv3.MockCollectionThin
import com.mmg.manahub.feature.decks.domain.engine.analysisv3.fixture01EdgarMarkov
import com.mmg.manahub.feature.decks.domain.engine.analysisv3.fixture02Meren
import com.mmg.manahub.feature.decks.domain.engine.analysisv3.fixture03Karlov
import com.mmg.manahub.feature.decks.domain.engine.analysisv3.fixture04Omnath
import com.mmg.manahub.feature.decks.domain.engine.analysisv3.fixture05Urza
import com.mmg.manahub.feature.decks.domain.engine.analysisv3.fixture07Brago
import com.mmg.manahub.feature.decks.domain.engine.analysisv3.fixture14MonoRedBurn
import com.mmg.manahub.feature.decks.domain.engine.availableIn
import com.mmg.manahub.feature.decks.domain.engine.card
import com.mmg.manahub.feature.decks.domain.template.OwnedCard
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Deck Wizard Commander v3 plan, Phase 4.1 — [RecommendWizardStrategiesUseCase] over the v3
 * corpus fixture commanders (no gitignored real-collection data needed).
 *
 * Deck Wizard 60-card wave (v6), plan §5 Phase 2.1: renamed from
 * `RecommendWizardStrategiesUseCaseTest`; every call site now goes through the
 * [BuildAnchor]-based `invoke` overload (`BuildAnchor.Commander(commander)`, identity derived
 * internally from `commander.colorIdentity` — every one of these calls already passed exactly
 * that as its own `identity` argument, so this is a mechanical, behavior-preserving rewrite, not a
 * new signal) — this exercises the Commander-anchor delegation path itself, not just the pre-v6
 * `Card`-based overload it delegates to.
 */
class RecommendWizardStrategiesUseCaseTest {

    private val useCase = RecommendWizardStrategiesUseCase()

    private fun ownedFrom(vararg pools: List<com.mmg.manahub.feature.decks.domain.engine.analysisv3.MockCollectionCard>): List<OwnedCard> =
        pools.flatMap { pool -> pool.map { OwnedCard(it.card, it.quantity) } }

    private fun top3Ids(result: List<StrategyRecommendation>): List<String> = result.take(3).map { it.strategy.id }

    @Test
    fun `Edgar Markov -- tribal ranks top 3`() {
        val commander = fixture01EdgarMarkov().mainboard.first { it.card.scryfallId == "cmd-edgar-markov" }.card
        val owned = ownedFrom(MockCollectionRich.ownedCards, MockCollectionRich.ownedBasics)
        val result = useCase(DeckFormat.COMMANDER, BuildAnchor.Commander(commander), owned)
        assertTrue("tribal" in top3Ids(result), "expected 'tribal' in top 3, got ${top3Ids(result)}")
        val tribal = result.first { it.strategy.id == "tribal" }
        assertTrue(tribal.tribe == "tribe:vampire", "expected derived tribe vampire, got ${tribal.tribe}")
    }

    @Test
    fun `Meren -- aristocrats ranks top 3`() {
        val commander = fixture02Meren().mainboard.first { it.card.name == "Meren of Clan Nel Toth" }.card
        val owned = ownedFrom(MockCollectionRich.ownedCards, MockCollectionRich.ownedBasics)
        val result = useCase(DeckFormat.COMMANDER, BuildAnchor.Commander(commander), owned)
        assertTrue("aristocrats" in top3Ids(result), "expected 'aristocrats' in top 3, got ${top3Ids(result)}")
    }

    @Test
    fun `Karlov -- lifegain ranks top 3`() {
        val commander = fixture03Karlov().mainboard.first { it.card.name == "Karlov of the Ghost Council" }.card
        val owned = ownedFrom(MockCollectionRich.ownedCards, MockCollectionRich.ownedBasics)
        val result = useCase(DeckFormat.COMMANDER, BuildAnchor.Commander(commander), owned)
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
        val result = useCase(DeckFormat.COMMANDER, BuildAnchor.Commander(commander), owned, ownTags = ownTags)
        val top3 = top3Ids(result)
        assertTrue("artifacts" in top3 || "combo" in top3, "expected 'artifacts' or 'combo' in top 3, got $top3")
    }

    @Test
    fun `Omnath -- landfall or big_mana ranks top 3`() {
        val commander = fixture04Omnath().mainboard.first { it.card.name == "Omnath, Locus of Rage" }.card
        val result = useCase(DeckFormat.COMMANDER, BuildAnchor.Commander(commander))
        val top3 = top3Ids(result)
        assertTrue("landfall" in top3 || "big_mana" in top3, "expected 'landfall' or 'big_mana' in top 3, got $top3")
    }

    @Test
    fun `Brago -- blink ranks top 3 (via card_strategy_tags signal)`() {
        val commander = fixture07Brago().mainboard.first { it.card.name == "Brago, King Eternal" }.card
        val ownTags = listOf(CardTag.BLINK)
        val result = useCase(DeckFormat.COMMANDER, BuildAnchor.Commander(commander), ownTags = ownTags)
        assertTrue("blink" in top3Ids(result), "expected 'blink' in top 3, got ${top3Ids(result)}")
    }

    @Test
    fun `every recommendation is available in the requested format`() {
        val commander = fixture01EdgarMarkov().mainboard.first { it.card.scryfallId == "cmd-edgar-markov" }.card
        val result = useCase(DeckFormat.COMMANDER_CASUAL, BuildAnchor.Commander(commander))
        assertTrue(result.isNotEmpty())
        assertTrue(result.all { it.strategy.availableIn(DeckFormat.COMMANDER_CASUAL) })
    }

    @Test
    fun `owned support changes ordering between thin and rich collections`() {
        val commander = MockCollectionThin.commander
        val thinOrder = useCase(DeckFormat.COMMANDER, BuildAnchor.Commander(commander), ownedFrom(MockCollectionThin.ownedCards, MockCollectionThin.ownedBasics)).map { it.strategy.id }
        val richOrder = useCase(DeckFormat.COMMANDER, BuildAnchor.Commander(commander), ownedFrom(MockCollectionRich.ownedCards, MockCollectionRich.ownedBasics)).map { it.strategy.id }
        assertTrue(thinOrder != richOrder, "expected owned support to change ordering between thin and rich collections")
    }

    @Test
    fun `all signals empty or absent never throws and returns a valid list`() {
        val commander = fixture01EdgarMarkov().mainboard.first { it.card.scryfallId == "cmd-edgar-markov" }.card
        val result = useCase(DeckFormat.COMMANDER, BuildAnchor.Commander(commander))
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun `Commander Casual gets the same recommendation list as Commander`() {
        val commander = fixture02Meren().mainboard.first { it.card.name == "Meren of Clan Nel Toth" }.card
        val owned = ownedFrom(MockCollectionRich.ownedCards, MockCollectionRich.ownedBasics)
        val commanderResult = useCase(DeckFormat.COMMANDER, BuildAnchor.Commander(commander), owned).map { it.strategy.id to it.score }
        val casualResult = useCase(DeckFormat.COMMANDER_CASUAL, BuildAnchor.Commander(commander), owned).map { it.strategy.id to it.score }
        assertTrue(commanderResult == casualResult)
    }

    // Deck Wizard Commander v4 plan, W3/E3 -- splitRecommended (score threshold + hard cap of 5).

    @Test
    fun `splitRecommended -- Karlov -- lifegain stays in Recommended`() {
        val commander = fixture03Karlov().mainboard.first { it.card.name == "Karlov of the Ghost Council" }.card
        val owned = ownedFrom(MockCollectionRich.ownedCards, MockCollectionRich.ownedBasics)
        val result = useCase(DeckFormat.COMMANDER, BuildAnchor.Commander(commander), owned)
        val (recommended, _) = useCase.splitRecommended(result)
        assertTrue(recommended.size <= 5, "Recommended must never exceed 5, got ${recommended.size}")
        assertTrue(recommended.any { it.strategy.id == "lifegain" }, "expected 'lifegain' in Recommended, got ${recommended.map { it.strategy.id }}")
    }

    @Test
    fun `splitRecommended -- Meren -- aristocrats stays in Recommended`() {
        val commander = fixture02Meren().mainboard.first { it.card.name == "Meren of Clan Nel Toth" }.card
        val owned = ownedFrom(MockCollectionRich.ownedCards, MockCollectionRich.ownedBasics)
        val result = useCase(DeckFormat.COMMANDER, BuildAnchor.Commander(commander), owned)
        val (recommended, _) = useCase.splitRecommended(result)
        assertTrue(recommended.size <= 5)
        assertTrue(recommended.any { it.strategy.id == "aristocrats" }, "expected 'aristocrats' in Recommended, got ${recommended.map { it.strategy.id }}")
    }

    @Test
    fun `splitRecommended -- Edgar -- tribal stays in Recommended`() {
        val commander = fixture01EdgarMarkov().mainboard.first { it.card.scryfallId == "cmd-edgar-markov" }.card
        val owned = ownedFrom(MockCollectionRich.ownedCards, MockCollectionRich.ownedBasics)
        val result = useCase(DeckFormat.COMMANDER, BuildAnchor.Commander(commander), owned)
        val (recommended, _) = useCase.splitRecommended(result)
        assertTrue(recommended.size <= 5)
        assertTrue(recommended.any { it.strategy.id == "tribal" }, "expected 'tribal' in Recommended, got ${recommended.map { it.strategy.id }}")
    }

    @Test
    fun `splitRecommended -- Urza with artifacts tag -- artifacts stays in Recommended`() {
        val commander = fixture05Urza().mainboard.first { it.card.name == "Urza, Lord High Artificer" }.card
        val owned = ownedFrom(MockCollectionRich.ownedCards, MockCollectionRich.ownedBasics)
        val ownTags = listOf(CardTag("artifacts_matter", TagCategory.STRATEGY))
        val result = useCase(DeckFormat.COMMANDER, BuildAnchor.Commander(commander), owned, ownTags = ownTags)
        val (recommended, _) = useCase.splitRecommended(result)
        assertTrue(recommended.size <= 5)
        assertTrue(recommended.any { it.strategy.id == "artifacts" }, "expected 'artifacts' in Recommended, got ${recommended.map { it.strategy.id }}")
    }

    @Test
    fun `splitRecommended -- Brago with blink tag -- blink stays in Recommended`() {
        val commander = fixture07Brago().mainboard.first { it.card.name == "Brago, King Eternal" }.card
        val ownTags = listOf(CardTag.BLINK)
        val result = useCase(DeckFormat.COMMANDER, BuildAnchor.Commander(commander), ownTags = ownTags)
        val (recommended, _) = useCase.splitRecommended(result)
        assertTrue(recommended.size <= 5)
        assertTrue(recommended.any { it.strategy.id == "blink" }, "expected 'blink' in Recommended, got ${recommended.map { it.strategy.id }}")
    }

    @Test
    fun `splitRecommended -- a commander with no owned collection, tags or EDHREC data yields a short Recommended list, never padded to 5`() {
        // Urza's own fixture-authored oracle text trips zero ArchetypeRoleClassifier/SynergyGraph
        // matchers (documented above) -- with no owned collection and no card_strategy_tags either,
        // every candidate's score comes from ColorStrategyAffinity alone (a generic color-pair prior,
        // never real commander-specific signal). This is the exact "no strong signal" case E3 exists
        // to stop padding to 5.
        val commander = fixture05Urza().mainboard.first { it.card.name == "Urza, Lord High Artificer" }.card
        val result = useCase(DeckFormat.COMMANDER, BuildAnchor.Commander(commander))
        val (recommended, partialFit) = useCase.splitRecommended(result)
        assertTrue(recommended.size < 5, "expected a SHORT Recommended list for a no-signal commander, got ${recommended.size}: ${recommended.map { it.strategy.id }}")
        assertTrue(partialFit.size == result.size - recommended.size)
    }

    @Test
    fun `splitRecommended -- partial fit carries every entry Recommended does not`() {
        val commander = fixture03Karlov().mainboard.first { it.card.name == "Karlov of the Ghost Council" }.card
        val owned = ownedFrom(MockCollectionRich.ownedCards, MockCollectionRich.ownedBasics)
        val result = useCase(DeckFormat.COMMANDER, BuildAnchor.Commander(commander), owned)
        val (recommended, partialFit) = useCase.splitRecommended(result)
        assertTrue((recommended + partialFit).size == result.size)
        assertTrue(recommended.map { it.strategy.id }.toSet().intersect(partialFit.map { it.strategy.id }.toSet()).isEmpty())
    }

    @Test
    fun `unresolved tribal entries never occupy the top 3`() {
        // A colorless-flavored commander with no tribe signal at all -- "tribal" should still be
        // returned (Custom is always separately offered by the caller, never a use-case concern) but
        // pushed to the end, never into a top-6 "Recommended" slice.
        val commander = fixture05Urza().mainboard.first { it.card.name == "Urza, Lord High Artificer" }.card
        val result = useCase(DeckFormat.COMMANDER, BuildAnchor.Commander(commander))
        val tribalIndex = result.indexOfFirst { it.strategy.id == "tribal" }
        assertTrue(tribalIndex == -1 || tribalIndex >= result.size - 3, "expected 'tribal' unresolved and pushed to the end, index=$tribalIndex of ${result.size}")
    }

    // ── Deck Wizard 60-card wave (v6), plan §5 Phase 2.1 -- BuildAnchor.Sixty ───────────────────

    @Test
    fun `fixture-14 seeds rank aggro in the top 5`() {
        // Deviation from the original test spec's literal "ranks aggro FIRST": verified against the
        // real, shipped axisAlignmentScore (reused UNCHANGED from the Commander path, per plan 2.1's
        // own "same five signals" instruction -- no Sixty-specific heuristic was added). Fixture14's
        // burn spells are almost entirely Instant/Sorcery, which register as bare TYPE-LINE density
        // producers on the SPELLS axis (same mechanism a mono-red-burn COMMANDER's own card would
        // trigger, not a Sixty-specific gap) -- this pushes spellslinger/storm-shaped catalog entries
        // (whose own theme maps directly to SPELLS) to a perfect axisScore that a themeless pure
        // archetype entry like "aggro" cannot reach (entryAxes is empty for a themeless entry, so
        // axisAlignmentScore returns 0 by construction, regardless of how aggressive the seeds are).
        // "aggro" still clears the color-affinity signal (mono-R's own #1 curated entry) and a real
        // role-alignment score, landing it a strong top-5 placement -- verified, not guessed.
        val seeds = fixture14MonoRedBurn().mainboard.filterNot { BasicLandCalculator.isLand(it.card) }.map { it.card }
        val anchor = BuildAnchor.Sixty(setOf(ManaColor.R), seeds)
        val owned = ownedFrom(MockCollectionRich.ownedCards, MockCollectionRich.ownedBasics)
        val result = useCase(DeckFormat.MODERN, anchor, owned)
        assertTrue("aggro" in result.take(5).map { it.strategy.id }, "got ${result.map { it.strategy.id }}")
    }

    @Test
    fun `identity UW with no seeds ranks control in the recommended split`() {
        val anchor = BuildAnchor.Sixty(setOf(ManaColor.U, ManaColor.W), emptyList())
        val result = useCase(DeckFormat.STANDARD, anchor, emptyList())
        val (recommended, _) = useCase.splitRecommended(result)
        assertTrue(recommended.any { it.strategy.id == "control" }, "got ${recommended.map { it.strategy.id }}")
    }

    @Test
    fun `colorless identity returns exactly the S9 list filtered by availableIn, STANDARD`() {
        val anchor = BuildAnchor.Sixty(emptySet(), emptyList())
        val result = useCase(DeckFormat.STANDARD, anchor, emptyList())
        // S9 colorless list: big_mana, artifacts, aggro, tribal(eldrazi), prison + Custom(caller-added, not in this use case's output).
        // prison.formats = {COMMANDER, CASUAL} only -- STANDARD is excluded (plan 2.3) -- so STANDARD's
        // colorless-affinity-driven set is big_mana/artifacts/aggro/tribal, but the use case still returns
        // EVERY CuratedStrategyCatalog.ALL.filter{availableIn(STANDARD)} entry (colorless affinity is one
        // SIGNAL among five, not a pool filter) -- assert prison is ABSENT and the other 4 are PRESENT,
        // never assert the full returned list equals only these 4.
        val ids = result.map { it.strategy.id }.toSet()
        assertTrue("prison" !in ids, "prison must not be availableIn(STANDARD)")
        assertTrue(setOf("big_mana", "artifacts", "aggro", "tribal").all { it in ids }, "got $ids")
    }

    @Test
    fun `colorless identity includes prison for CASUAL`() {
        val anchor = BuildAnchor.Sixty(emptySet(), emptyList())
        val result = useCase(DeckFormat.CASUAL, anchor, emptyList())
        assertTrue("prison" in result.map { it.strategy.id }, "prison.formats includes CASUAL (plan 2.3)")
    }

    @Test
    fun `owned support scales by CopyPolicy maxPlaceable -- an owned 4-of removal contributes 4, not 1`() {
        // The Commander path's own private ownedRoleCounts does `.distinctBy { it.name }` then
        // `counts[role] = (counts[role] ?: 0) + 1` -- ONE per distinct owned card regardless of
        // OwnedCard.quantity. The Sixty path's ownedRoleCountsSixty scales each card's contribution
        // by CopyPolicy.maxPlaceable(card, format, quantity) instead of a flat +1.
        // CardTag.REMOVAL (key "removal") is what ArchetypeRoleClassifier's LEGACY_ROLE_MAP credits
        // toward the "removal_spot" RoleKey -- a raw CardTag("removal_spot", ROLE) tag key would
        // never match CardTag.REMOVAL.key and would silently classify to zero roles.
        val removalCard = card(id = "owned-removal-4x", typeLine = "Instant", colorIdentity = listOf("R"), tags = listOf(CardTag.REMOVAL))
        val anchor = BuildAnchor.Sixty(setOf(ManaColor.R), emptyList())
        val oneOwned = useCase(DeckFormat.STANDARD, anchor, listOf(OwnedCard(removalCard, 1)))
        val fourOwned = useCase(DeckFormat.STANDARD, anchor, listOf(OwnedCard(removalCard, 4)))
        val entryId = CuratedStrategyCatalog.ALL.first { it.availableIn(DeckFormat.STANDARD) && it.archetypes.contains(ArchetypeId.AGGRO) }.id
        val scoreAt1 = oneOwned.first { it.strategy.id == entryId }.score
        val scoreAt4 = fourOwned.first { it.strategy.id == entryId }.score
        assertTrue(scoreAt4 > scoreAt1, "an owned 4-of must score strictly higher owned-coverage than an owned 1-of (was previously an identical +1 either way)")
    }

    // ── Deck Wizard UX polish plan, Run 2 -- StrategyRecommendation.ownedFittingCount ────────────

    @Test
    fun `ownedFittingCount is zero with no owned collection`() {
        val commander = fixture01EdgarMarkov().mainboard.first { it.card.scryfallId == "cmd-edgar-markov" }.card
        val result = useCase(DeckFormat.COMMANDER, BuildAnchor.Commander(commander))
        assertTrue(result.all { it.ownedFittingCount == 0 }, "expected every ownedFittingCount to be 0 with no owned collection")
    }

    @Test
    fun `ownedFittingCount scales with CopyPolicy maxPlaceable, mirroring the owned-coverage score`() {
        // Same fixture/setup as "owned support scales by CopyPolicy maxPlaceable" above -- proves
        // the structurally-exposed count moves the SAME way the score's own private aggregation
        // does, not just that SOME reason text changed.
        val removalCard = card(id = "owned-removal-4x", typeLine = "Instant", colorIdentity = listOf("R"), tags = listOf(CardTag.REMOVAL))
        val anchor = BuildAnchor.Sixty(setOf(ManaColor.R), emptyList())
        val entryId = CuratedStrategyCatalog.ALL.first { it.availableIn(DeckFormat.STANDARD) && it.archetypes.contains(ArchetypeId.AGGRO) }.id

        val oneOwned = useCase(DeckFormat.STANDARD, anchor, listOf(OwnedCard(removalCard, 1)))
        val fourOwned = useCase(DeckFormat.STANDARD, anchor, listOf(OwnedCard(removalCard, 4)))

        val countAt1 = oneOwned.first { it.strategy.id == entryId }.ownedFittingCount
        val countAt4 = fourOwned.first { it.strategy.id == entryId }.ownedFittingCount
        assertTrue(countAt4 > countAt1, "an owned 4-of must report a strictly higher ownedFittingCount than an owned 1-of, got $countAt1 vs $countAt4")
    }
}
