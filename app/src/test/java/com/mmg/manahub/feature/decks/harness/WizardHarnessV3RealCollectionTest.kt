package com.mmg.manahub.feature.decks.harness
// COMMENTS_REVIEWED: 2026-09-15

import com.mmg.manahub.core.domain.usecase.decks.BasicLandCalculator
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Test

// ═══════════════════════════════════════════════════════════════════════════════
//  Deck Wizard Commander v4 plan, W8 (8.1) — harness v3's real-collection segments.
//
//  Additive to WizardCommanderHarnessV2Test (which keeps its own HARD metric set byte-identical,
//  per the plan's own constraint) — reuses CommanderMatrixV2's fixture/spec plumbing rather than a
//  second copy of the real-collection matrix wiring.
//
//  Choice determinism (HARD): finalizing a build with the engine's OWN tentative picks, made
//  explicit, must equal leaving them implicit (the single-shot path) — a real regression guard on
//  top of BuildCommanderDeckUseCase.invoke()'s "byte-identical by construction" comment.
//
//  Variety (TRACKED): median card-overlap between two different deckIds over a deterministic
//  sample of the real matrix, with a documented alert threshold (see W6c's measured 0.65 baseline
//  in docs/deck-wizard-state.md §5 — a regression toward W6b's pre-fix ~0.88-1.00 near-identical
//  builds would cross the 0.85 line below).
//
//  ./gradlew :app:testDebugUnitTest --tests "com.mmg.manahub.feature.decks.harness.WizardHarnessV3RealCollectionTest"
// ═══════════════════════════════════════════════════════════════════════════════

class WizardHarnessV3RealCollectionTest {

    private val harnessDir = FixtureLoader.locateHarnessDir()

    @Before
    fun assumeFixturesPresent() {
        Assume.assumeTrue(
            "wizard-harness fixtures not found under testdata/wizard-harness/ (gitignored -- skipping on this machine/CI)",
            harnessDir != null,
        )
    }

    private fun List<String>.toManaColors(): Set<ManaColor> =
        mapNotNull { symbol -> ManaColor.entries.firstOrNull { it.symbol == symbol } }.toSet()

    @Test
    fun `choice determinism -- finalizing with the engine's own tentative picks equals leaving them implicit`() = runBlocking {
        val dir = harnessDir!!
        val fixtures = FixtureLoader.load(dir)
        val owned = CommanderMatrixV2.ownedPool(fixtures)
        val specs = CommanderMatrixV2.specs(fixtures)

        var checked = 0
        var withGroups = 0
        for (spec in specs) {
            val identity = spec.commander.colorIdentity.toManaColors()
            val draft = runCatching {
                CommanderMatrixV2.newBuildUseCase().buildWithGroups(
                    DeckFormat.COMMANDER, spec.commander, spec.pick, identity, owned, includeNonBasicLands = true,
                )
            }.getOrNull() ?: continue
            checked++
            if (draft.ambiguityGroups.isEmpty()) continue
            withGroups++

            val explicit = CommanderMatrixV2.newBuildUseCase().finalize(draft, resolutions = draft.tentativeByRole, fillLands = true)
            val implicit = CommanderMatrixV2.newBuildUseCase().finalize(draft, resolutions = emptyMap(), fillLands = true)
            val explicitIds = explicit.result.entries.map { it.card.scryfallId to it.quantity }.sortedBy { it.first }
            val implicitIds = implicit.result.entries.map { it.card.scryfallId to it.quantity }.sortedBy { it.first }
            assertEquals(
                "spec ${spec.label}: finalizing with the engine's own tentative picks must equal the implicit (single-shot) build",
                implicitIds,
                explicitIds,
            )
        }
        println("[wizard-harness-v3] choice determinism checked=$checked withGroups=$withGroups")
        assertTrue("expected at least one real-collection spec with ambiguity groups to genuinely exercise this check", withGroups > 0)
    }

    @Test
    fun `variety -- median card-overlap between two deckIds over a deterministic real-matrix sample`() = runBlocking {
        val dir = harnessDir!!
        val fixtures = FixtureLoader.load(dir)
        val owned = CommanderMatrixV2.ownedPool(fixtures)
        val specs = CommanderMatrixV2.specs(fixtures)
        // W6b's own sampling convention (progress tracker Run 11): every 15th spec, deterministic
        // and cheap (2 builds/sample) against the up-to-180-spec real matrix.
        val sample = specs.filterIndexed { index, _ -> index % 15 == 0 }

        suspend fun nonLandIds(spec: CommanderSpecV2, identity: Set<ManaColor>, deckId: String): Set<String>? =
            runCatching {
                CommanderMatrixV2.newBuildUseCase()(
                    DeckFormat.COMMANDER, spec.commander, spec.pick, identity, owned, includeNonBasicLands = true, deckId = deckId,
                )
            }.getOrNull()?.result?.entries
                ?.filterNot { BasicLandCalculator.isLand(it.card) }
                ?.map { it.card.scryfallId }
                ?.toSet()

        val overlaps = sample.mapNotNull { spec ->
            val identity = spec.commander.colorIdentity.toManaColors()
            val a = nonLandIds(spec, identity, "wizard-harness-v3-variety-A") ?: return@mapNotNull null
            val b = nonLandIds(spec, identity, "wizard-harness-v3-variety-B") ?: return@mapNotNull null
            if (a.isEmpty() && b.isEmpty()) return@mapNotNull null
            val union = (a + b).size
            val intersection = a.intersect(b).size
            if (union == 0) 1.0 else intersection.toDouble() / union
        }.sorted()

        assertTrue("expected at least one sampled real-collection spec", overlaps.isNotEmpty())
        val median = overlaps[overlaps.size / 2]
        println("[wizard-harness-v3] variety (n=${overlaps.size}) median jaccard overlap=$median (W6c baseline 0.65)")
        // TRACKED, not HARD -- a documented alert threshold, not a gate on the exact number: W6c
        // measured median 0.65 at NEAR_TIE_BAND=0.12; a regression toward "always near-identical"
        // (W6b's pre-fix finding, ~0.88-1.00) would cross this before the median hits 1.0 outright.
        assertTrue(
            "variety alert: median overlap $median exceeds the 0.85 threshold -- NEAR_TIE_BAND may have " +
                "regressed toward exact-tie-only (see BuildCommanderDeckUseCaseTest's near-tie guard test)",
            median <= 0.85,
        )
    }
}
