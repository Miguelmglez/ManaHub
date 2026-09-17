package com.mmg.manahub.feature.decks.domain.template
// COMMENTS_REVIEWED: 2026-09-15

import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.domain.usecase.decks.BasicLandCalculator
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.core.model.TagCategory
import com.mmg.manahub.feature.decks.domain.engine.DeckScorer
import com.mmg.manahub.feature.decks.domain.engine.FindingSeverity
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.NeutralPowerResolver
import com.mmg.manahub.feature.decks.domain.engine.RoleClassifier
import com.mmg.manahub.feature.decks.domain.engine.StrategyPick
import com.mmg.manahub.feature.decks.domain.engine.card
import com.mmg.manahub.core.gamification.domain.ProgressionEventBus
import com.mmg.manahub.feature.decks.domain.usecase.DeckAnalysisPipeline
import com.mmg.manahub.feature.decks.domain.usecase.EvaluateDeckUseCase
import com.mmg.manahub.feature.decks.domain.usecase.InferDeckIdentityUseCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private object V3TestCrashReporter : CrashReporter {
    override fun recordException(throwable: Throwable) = Unit
    override fun log(message: String) = Unit
    override fun setCustomKey(key: String, value: String) = Unit
}

/**
 * Deck Wizard Commander v4 plan, W8 (8.1) — harness v3's commonTest segments: legality (R7/E9)
 * and land-mode (R8/R12). Self-contained synthetic fixtures, no gitignored real-collection data
 * needed (mirrors [BuildWizardDeckUseCaseTest]'s own scope note) — runs everywhere `commonTest`
 * runs, including wasmJs. The real-collection choice-determinism (HARD) and variety (TRACKED)
 * segments live in `app/src/test/.../harness/WizardHarnessV3RealCollectionTest.kt` since they need
 * the gitignored matrix.
 */
class WizardHarnessV3Test {

    private fun newUseCase(): BuildWizardDeckUseCase {
        val pipeline = DeckAnalysisPipeline(
            EvaluateDeckUseCase(DeckScorer(RoleClassifier(), NeutralPowerResolver), ProgressionEventBus()),
            InferDeckIdentityUseCase(),
            V3TestCrashReporter,
        )
        return BuildWizardDeckUseCase(pipeline, V3TestCrashReporter)
    }

    private fun roleTagFor(key: String): CardTag = CardTag(key, TagCategory.ROLE)

    /** Mirrors [com.mmg.manahub.feature.decks.domain.engine.analysisv3.MockCollections]' own
     * private `basicLand` helper (not exposed outside that file). */
    private fun basicLand(name: String, symbol: String): Card = card(
        id = "v3-basic-$name",
        name = name,
        typeLine = "Basic Land — $name",
        cmc = 0.0,
        colors = emptyList(),
        colorIdentity = listOf(symbol),
        producedMana = symbol,
    )

    private fun greenCommander(id: String) = card(
        id = id,
        name = "Test Green Commander",
        typeLine = "Legendary Creature — Human",
        cmc = 3.0,
        colors = listOf("G"),
        colorIdentity = listOf("G"),
    )

    private fun greenFillers(count: Int, roleKey: String = "sac_outlet"): List<OwnedCard> =
        (1..count).map { i ->
            OwnedCard(
                card(
                    id = "v3-filler-$roleKey-$i",
                    name = "Filler $roleKey $i",
                    typeLine = "Creature — Bear",
                    cmc = 2.0,
                    colors = listOf("G"),
                    colorIdentity = listOf("G"),
                    tags = listOf(roleTagFor(roleKey)),
                ),
                1,
            )
        }

    // ── Legality (R7/E9) ─────────────────────────────────────────────────────────────

    @Test
    fun `legality -- a Commander-banned card never enters the auto-placement pool for COMMANDER, but Commander Casual ignores legality`() = runTest {
        val useCase = newUseCase()
        val commander = greenCommander("cmd-v3-legality-pool")
        val banned = card(
            id = "v3-banned-1",
            name = "Banned Filler",
            typeLine = "Creature — Bear",
            cmc = 2.0,
            colors = listOf("G"),
            colorIdentity = listOf("G"),
            tags = listOf(roleTagFor("sac_outlet")),
            legalityCommander = "banned",
        )
        val owned = greenFillers(40) + listOf(OwnedCard(banned, 1))
        val identity = setOf(ManaColor.G)

        val commanderDraft = useCase.buildWithGroups(DeckFormat.COMMANDER, commander, StrategyPick.Custom, identity, owned)
        val commanderPool = commanderDraft.candidatesById.keys + commanderDraft.placedNonLand.map { it.card.scryfallId }
        assertTrue(banned.scryfallId !in commanderPool, "a Commander-banned card must never enter the COMMANDER auto-placement pool")

        val casualDraft = useCase.buildWithGroups(DeckFormat.COMMANDER_CASUAL, commander, StrategyPick.Custom, identity, owned)
        val casualPool = casualDraft.candidatesById.keys + casualDraft.placedNonLand.map { it.card.scryfallId }
        assertTrue(banned.scryfallId in casualPool, "COMMANDER_CASUAL must ignore legality entirely -- a banned card must still enter the pool")
    }

    @Test
    fun `legality -- builder and analysis agree, a manually-added banned card is a BLOCKER only for COMMANDER`() = runTest {
        val useCase = newUseCase()
        val commander = greenCommander("cmd-v3-legality-analysis")
        val banned = card(
            id = "v3-banned-2",
            name = "Banned Manual Add",
            typeLine = "Creature — Bear",
            cmc = 2.0,
            colors = listOf("G"),
            colorIdentity = listOf("G"),
            legalityCommander = "banned",
        )
        val owned = greenFillers(40)
        val identity = setOf(ManaColor.G)
        val manualAdds = listOf(ManualAdd(card = banned, isOwned = true))

        // Checks specifically for Finding.IllegalCard (a BLOCKER), never "any BLOCKER" -- these tiny
        // synthetic decks also trip DeckTooSmall (unrelated to legality), which would otherwise
        // false-positive the CASUAL side's "must never be a BLOCKER" assertion below.
        fun hasIllegalCardBlocker(outcome: CommanderBuildOutcome) =
            outcome.result.analysis.pillars.any { pillar ->
                pillar.findings.any {
                    it.severity == FindingSeverity.BLOCKER &&
                        it is com.mmg.manahub.feature.decks.domain.engine.Finding.IllegalCard
                }
            }

        val commanderOutcome = useCase(DeckFormat.COMMANDER, commander, StrategyPick.Custom, identity, owned, manualAdds = manualAdds)
        assertTrue(
            hasIllegalCardBlocker(commanderOutcome),
            "a banned manual add must surface a BLOCKER for COMMANDER -- the builder placed it (D7, manual adds always kept), analysis must agree it's illegal",
        )

        val casualOutcome = useCase(DeckFormat.COMMANDER_CASUAL, commander, StrategyPick.Custom, identity, owned, manualAdds = manualAdds)
        assertTrue(
            !hasIllegalCardBlocker(casualOutcome),
            "COMMANDER_CASUAL ignores legality entirely -- the SAME manual add must never be a BLOCKER here",
        )
    }

    // ── Lands (R8/R12) ───────────────────────────────────────────────────────────────

    @Test
    fun `lands -- non-basic OFF places only basics and still reaches the full land target`() = runTest {
        val useCase = newUseCase()
        val commander = greenCommander("cmd-v3-land-off")
        val dual = card(id = "v3-dual-1", name = "Test Dual", typeLine = "Land", colors = emptyList(), colorIdentity = listOf("G"), producedMana = "G", oracleText = "Add {G}.")
        val owned = greenFillers(30) + listOf(OwnedCard(basicLand("Forest", "G"), 40), OwnedCard(dual, 1))
        val identity = setOf(ManaColor.G)

        val outcome = useCase(DeckFormat.COMMANDER, commander, StrategyPick.Custom, identity, owned, includeNonBasicLands = false)
        val lands = outcome.result.entries.filter { BasicLandCalculator.isLand(it.card) }
        assertTrue(lands.all { BasicLandCalculator.isBasicLand(it.card) }, "non-basic lands OFF must place ONLY basics: ${lands.map { it.card.name }}")

        val draft = useCase.buildWithGroups(DeckFormat.COMMANDER, commander, StrategyPick.Custom, identity, owned, includeNonBasicLands = false)
        assertEquals(
            draft.landTarget,
            outcome.result.fillStats.lands,
            "with basics guaranteed available, land count must reach the full resolved target -- no ownership-driven gap",
        )
    }

    @Test
    fun `lands -- non-basic ON uses owned non-basics but basics stay the majority`() = runTest {
        // ArchetypeData.LAND_MIX's mono-color bucket is 1.0..1.0 basics BY DESIGN ("basics ARE the
        // land base, no dedicated fixing") -- a non-basic cap only exists for a MULTI-color
        // identity, so this needs a 2-color (Selesnya) commander, not the shared mono-green fixture.
        val useCase = newUseCase()
        val commander = card(
            id = "cmd-v3-land-on", name = "Test GW Commander", typeLine = "Legendary Creature — Human",
            cmc = 3.0, colors = listOf("G", "W"), colorIdentity = listOf("G", "W"),
        )
        val gwFillers = (1..30).map { i ->
            OwnedCard(
                card(
                    id = "v3-gw-filler-$i", name = "GW Filler $i", typeLine = "Creature — Bear", cmc = 2.0,
                    colors = listOf("G"), colorIdentity = listOf("G"), tags = listOf(roleTagFor("sac_outlet")),
                ),
                1,
            )
        }
        val dual = card(
            id = "v3-dual-2", name = "Test Dual 2", typeLine = "Land", colors = emptyList(),
            colorIdentity = listOf("G", "W"), producedMana = "GW", oracleText = "Add {G} or {W}.",
        )
        val owned = gwFillers + listOf(OwnedCard(basicLand("Forest", "G"), 40), OwnedCard(basicLand("Plains", "W"), 40), OwnedCard(dual, 1))
        val identity = setOf(ManaColor.G, ManaColor.W)

        val outcome = useCase(DeckFormat.COMMANDER, commander, StrategyPick.Custom, identity, owned, includeNonBasicLands = true)
        val lands = outcome.result.entries.filter { BasicLandCalculator.isLand(it.card) }
        val nonBasics = lands.filterNot { BasicLandCalculator.isBasicLand(it.card) }
        val basics = lands.filter { BasicLandCalculator.isBasicLand(it.card) }
        assertTrue(nonBasics.isNotEmpty(), "non-basic lands ON with an owned in-identity dual must use it")
        assertTrue(
            basics.sumOf { it.quantity } > nonBasics.sumOf { it.quantity },
            "basics must remain the majority of the manabase (D10's LAND_MIX cap)",
        )
    }

    @Test
    fun `lands -- zero OWNED basics (but the basic-land resource exists) still yields a full deck`() = runTest {
        val useCase = newUseCase()
        val commander = greenCommander("cmd-v3-land-zero-basics")
        // R12: basics are ownership-EXEMPT -- BuildWizardDeckUseCase.resolveBasicCard only needs
        // the Card OBJECT to exist in ownedCollection, never a positive owned quantity. Mirrors
        // DeckWizardViewModel.guaranteeBasicsAvailable's own contract at the VM boundary (W0.2).
        val owned = greenFillers(30) + listOf(OwnedCard(basicLand("Forest", "G"), 0))
        val identity = setOf(ManaColor.G)

        val outcome = useCase(DeckFormat.COMMANDER, commander, StrategyPick.Custom, identity, owned, includeNonBasicLands = false)
        val draft = useCase.buildWithGroups(DeckFormat.COMMANDER, commander, StrategyPick.Custom, identity, owned, includeNonBasicLands = false)
        assertEquals(
            draft.landTarget,
            outcome.result.fillStats.lands,
            "zero OWNED basics must not produce an ownership-driven land gap -- basics are exempt from ownership",
        )
    }
}
