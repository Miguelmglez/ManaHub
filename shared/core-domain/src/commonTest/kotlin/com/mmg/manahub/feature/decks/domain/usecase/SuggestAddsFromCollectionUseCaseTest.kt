package com.mmg.manahub.feature.decks.domain.usecase

import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.feature.decks.domain.engine.DeckScorer
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.NeutralPowerResolver
import com.mmg.manahub.feature.decks.domain.engine.RoleClassifier
import com.mmg.manahub.feature.decks.domain.engine.ScoreReason
import com.mmg.manahub.feature.decks.domain.engine.card
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.TimeSource
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest

/**
 * Deck Doctor Community/Archetype plan, Phase 2 (Motor A) — commonTest coverage for
 * [SuggestAddsFromCollectionUseCase]. Multiplatform (kotlin-test + kotlinx-coroutines-test, no
 * MockK — MockK is JVM-only per `project_kmp_spike_findings` memory), mirroring the style of the
 * existing [com.mmg.manahub.feature.decks.domain.engine.ManaBaseAnalyzerTest] commonTest suite: a
 * REAL [DeckScorer] (deterministic [NeutralPowerResolver]) rather than a mock, since this use case
 * is a thin composition over the real engine, not a candidate for behavior stubbing.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SuggestAddsFromCollectionUseCaseTest {

    private lateinit var scorer: DeckScorer
    private lateinit var useCase: SuggestAddsFromCollectionUseCase

    @BeforeTest
    fun setUp() {
        scorer = DeckScorer(RoleClassifier(), NeutralPowerResolver)
        useCase = SuggestAddsFromCollectionUseCase(deckScorer = scorer)
    }

    // ── Filtering: format legality ──────────────────────────────────────────────────

    @Test
    fun formatLegalityFilterDropsAnIllegalCandidate() = runTest {
        val legal = card(id = "legal-1", name = "Legal Bolt", colors = listOf("R"), colorIdentity = listOf("R"))
        val illegal = card(id = "illegal-1", name = "Banned Bolt", colors = listOf("R"), colorIdentity = listOf("R"))
            .copy(legalityCommander = "not_legal")
        val profile = scorer.profile(
            mainboard = emptyList(), format = DeckFormat.COMMANDER,
            colorIdentity = setOf(ManaColor.R), seedTags = emptyList(),
        )

        val result = useCase(collection = listOf(legal, illegal), mainboard = emptyList(), profile = profile)

        val ids = result.map { it.fit.card.scryfallId }
        assertTrue("legal-1" in ids, "legal candidate must be suggested")
        assertFalse("illegal-1" in ids, "illegal candidate must never be suggested")
    }

    // ── Filtering: D14 fail-closed on unresolved color identity (Commander only) ────

    @Test
    fun commanderFailsClosedOnACandidateWhoseColorsAreNotASubsetOfItsOwnColorIdentity() = runTest {
        // `colors` must always be a subset of `colorIdentity` per Magic's own rules; a card
        // where that invariant does not hold carries UNRESOLVED identity data (see the use
        // case's `hasUnresolvedColorIdentity` KDoc) and must never be suggested in Commander,
        // regardless of whether its OWN colorIdentity happens to be a legal subset of the deck's.
        val consistent = card(id = "ok-1", name = "Consistent Bolt", colors = listOf("R"), colorIdentity = listOf("R"))
        val unresolved = card(id = "bad-1", name = "Unresolved Bolt", colors = listOf("R", "W"), colorIdentity = listOf("R"))
        val profile = scorer.profile(
            mainboard = emptyList(), format = DeckFormat.COMMANDER,
            colorIdentity = setOf(ManaColor.R), seedTags = emptyList(),
        )

        val result = useCase(collection = listOf(consistent, unresolved), mainboard = emptyList(), profile = profile)

        val ids = result.map { it.fit.card.scryfallId }
        assertTrue("ok-1" in ids, "a card with consistent colors/colorIdentity must be suggested")
        assertFalse("bad-1" in ids, "a card with unresolved colors/colorIdentity must fail closed in Commander")
    }

    @Test
    fun unresolvedColorIdentityIsNotFilteredOutsideCommander() = runTest {
        // The D14 fail-closed rule is Commander-specific (60-card formats have no color-identity
        // deckbuilding restriction to begin with).
        val unresolved = card(id = "bad-2", name = "Unresolved Bolt", colors = listOf("R", "W"), colorIdentity = listOf("R"))
        val profile = scorer.profile(
            mainboard = emptyList(), format = DeckFormat.CASUAL,
            colorIdentity = setOf(ManaColor.R), seedTags = emptyList(),
        )

        val result = useCase(collection = listOf(unresolved), mainboard = emptyList(), profile = profile)

        assertTrue("bad-2" in result.map { it.fit.card.scryfallId }, "outside Commander, unresolved identity is not fail-closed")
    }

    // ── Filtering: copy limits ────────────────────────────────────────────────────────

    @Test
    fun copyLimitDropsACandidateAlreadyAtTheFormatMaxByName() = runTest {
        // 4 copies of "Lightning Bolt" already in the mainboard (60-card format, max 4-of); the
        // collection holds a 5th copy under a DIFFERENT printing (scryfallId) — must be excluded
        // even though its id is not literally in the mainboard.
        val mainboardPrinting = card(id = "bolt-main", name = "Lightning Bolt", colors = listOf("R"), colorIdentity = listOf("R"))
        val extraPrinting = card(id = "bolt-extra", name = "Lightning Bolt", colors = listOf("R"), colorIdentity = listOf("R"))
        val mainboard = listOf(com.mmg.manahub.feature.decks.domain.engine.entry(mainboardPrinting, quantity = 4))
        val profile = scorer.profile(
            mainboard = mainboard, format = DeckFormat.CASUAL,
            colorIdentity = setOf(ManaColor.R), seedTags = emptyList(),
        )

        val result = useCase(collection = listOf(extraPrinting), mainboard = mainboard, profile = profile)

        assertTrue(
            result.none { it.fit.card.scryfallId == "bolt-extra" },
            "a 5th copy by NAME must be excluded once the format's copy limit is already met",
        )
    }

    @Test
    fun commanderSingletonCopyLimitDropsADuplicatePrintingOfAMainboardCard() = runTest {
        val mainboardPrinting = card(id = "sol-main", name = "Sol Ring", colors = emptyList(), colorIdentity = emptyList())
        val duplicatePrinting = card(id = "sol-dup", name = "Sol Ring", colors = emptyList(), colorIdentity = emptyList())
        val mainboard = listOf(com.mmg.manahub.feature.decks.domain.engine.entry(mainboardPrinting, quantity = 1))
        val profile = scorer.profile(
            mainboard = mainboard, format = DeckFormat.COMMANDER,
            colorIdentity = setOf(ManaColor.R), seedTags = emptyList(),
        )

        val result = useCase(collection = listOf(duplicatePrinting), mainboard = mainboard, profile = profile)

        assertTrue(result.none { it.fit.card.scryfallId == "sol-dup" })
    }

    @Test
    fun sixtyCardFormatSuggestsAPlaysetTopUpCopyCount() = runTest {
        val bolt = card(id = "bolt-1", name = "Lightning Bolt", colors = listOf("R"), colorIdentity = listOf("R"))
        val profile = scorer.profile(
            mainboard = emptyList(), format = DeckFormat.CASUAL,
            colorIdentity = setOf(ManaColor.R), seedTags = emptyList(),
        )

        val result = useCase(collection = listOf(bolt), mainboard = emptyList(), profile = profile)

        val suggestion = result.first { it.fit.card.scryfallId == "bolt-1" }
        assertEquals(4, suggestion.suggestedCopies)
    }

    @Test
    fun commanderFormatAlwaysSuggestsExactlyOneCopy() = runTest {
        val bolt = card(id = "bolt-2", name = "Lightning Bolt", colors = listOf("R"), colorIdentity = listOf("R"))
        val profile = scorer.profile(
            mainboard = emptyList(), format = DeckFormat.COMMANDER,
            colorIdentity = setOf(ManaColor.R), seedTags = emptyList(),
        )

        val result = useCase(collection = listOf(bolt), mainboard = emptyList(), profile = profile)

        assertEquals(1, result.first { it.fit.card.scryfallId == "bolt-2" }.suggestedCopies)
    }

    // ── Ordering determinism ─────────────────────────────────────────────────────────

    @Test
    fun orderingIsDeterministicRegardlessOfCollectionInputOrder() = runTest {
        // Four otherwise-identical bodies (same tie-breaking risk) so the result depends entirely
        // on the explicit tie-break comparator, not on incidental input order / sort stability.
        val candidates = (1..4).map { i ->
            card(
                id = "twin-$i", name = "Twin $i", typeLine = "Creature — Bear",
                cmc = 2.0, colors = listOf("G"), colorIdentity = listOf("G"), power = "2", toughness = "2",
            )
        }
        val profile = scorer.profile(
            mainboard = emptyList(), format = DeckFormat.CASUAL,
            colorIdentity = setOf(ManaColor.G), seedTags = emptyList(),
        )

        val firstOrder = useCase(collection = candidates, mainboard = emptyList(), profile = profile)
            .map { it.fit.card.scryfallId }
        val secondOrder = useCase(collection = candidates.reversed(), mainboard = emptyList(), profile = profile)
            .map { it.fit.card.scryfallId }
        val thirdOrder = useCase(
            collection = listOf(candidates[2], candidates[0], candidates[3], candidates[1]),
            mainboard = emptyList(), profile = profile,
        ).map { it.fit.card.scryfallId }

        assertEquals(firstOrder, secondOrder)
        assertEquals(firstOrder, thirdOrder)
    }

    // ── Pip-intensity multiplier (D14) ──────────────────────────────────────────────

    @Test
    fun aHeavyMonoColorPipCandidateScoresLowerThanAnEquivalentHybridCandidateInAMultiColorDeck() = runTest {
        // Both cards are otherwise IDENTICAL (same type/cmc/power/no tags) so their base DeckScorer
        // fit is equal; only the mana cost's pip shape differs. `{R}{R}{R}` demands 3 HARD red pips
        // (max intensity 3); `{R/W}{R/W}{R/W}` demands the same intensity-3 read but is flagged
        // hybrid-flexible — see `pipIntensityMultiplier`'s KDoc for why a hybrid symbol earns a
        // lighter per-pip penalty. The deck runs 4 colors so the multiplier's colorCount term is
        // non-trivial.
        val monoHeavy = card(
            id = "mono-heavy", name = "Mono Heavy", typeLine = "Creature — Elemental",
            cmc = 3.0, colors = listOf("R"), colorIdentity = listOf("R"), power = "3", toughness = "3",
            manaCost = "{R}{R}{R}",
        )
        val hybridHeavy = card(
            id = "hybrid-heavy", name = "Hybrid Heavy", typeLine = "Creature — Elemental",
            cmc = 3.0, colors = listOf("R", "W"), colorIdentity = listOf("R", "W"), power = "3", toughness = "3",
            manaCost = "{R/W}{R/W}{R/W}",
        )
        val profile = scorer.profile(
            mainboard = emptyList(), format = DeckFormat.COMMANDER,
            colorIdentity = setOf(ManaColor.W, ManaColor.U, ManaColor.B, ManaColor.R), seedTags = emptyList(),
        )

        val result = useCase(collection = listOf(monoHeavy, hybridHeavy), mainboard = emptyList(), profile = profile)

        val monoScore = result.first { it.fit.card.scryfallId == "mono-heavy" }.fit.score
        val hybridScore = result.first { it.fit.card.scryfallId == "hybrid-heavy" }.fit.score
        assertTrue(
            monoScore < hybridScore - 0.05f,
            "a heavy mono-color pip card ($monoScore) must score measurably lower than an equal-gap-fill hybrid card ($hybridScore)",
        )
    }

    @Test
    fun pipMultiplierNeverPenalizesAMonoColorDeck() = runTest {
        val monoHeavy = card(
            id = "mono-only", name = "Mono Only", typeLine = "Creature — Elemental",
            cmc = 3.0, colors = listOf("R"), colorIdentity = listOf("R"), power = "3", toughness = "3",
            manaCost = "{R}{R}{R}",
        )
        val profile = scorer.profile(
            mainboard = emptyList(), format = DeckFormat.CASUAL,
            colorIdentity = setOf(ManaColor.R), seedTags = emptyList(),
        )
        val baseFit = scorer.fit(monoHeavy, profile, isOwned = true)

        val result = useCase(collection = listOf(monoHeavy), mainboard = emptyList(), profile = profile)

        // colorCount <= 1 → multiplier is a pure pass-through (1f) → the score is unchanged versus
        // the raw engine fit (no theme-role bonus resolved either, resolvedSkeleton is null here).
        assertEquals(baseFit.score, result.first().fit.score, 0.0001f)
    }

    // ── Workstream 9.5 (Deck Wizard & Engine Rework plan) -- colour-aware adds ──────────

    @Test
    fun aGrixisDeckShortOnBoardWipesRanksASecondaryColourCandidateAboveASubstituteColourCandidate() = runTest {
        // Grixis (U/B/R) is short on board wipes (empty mainboard, CONTROL wants several).
        // removal_mass affinity: W=PRIMARY, U=SUBSTITUTE (mass bounce), B=SECONDARY, R=SECONDARY,
        // G=ABSENT (ColorRoleAffinityTest's own table). Both candidates share the EXACT SAME oracle
        // pattern/confidence (DESTROY_OR_EXILE_ALL, 0.95) so the ONLY scoring difference is colour.
        val redWipe = card(
            id = "red-wipe", name = "Red Wipe", typeLine = "Sorcery", cmc = 4.0,
            colors = listOf("R"), colorIdentity = listOf("R"), oracleText = "Destroy all creatures.",
        )
        val blueWipe = card(
            id = "blue-wipe", name = "Blue Wipe", typeLine = "Sorcery", cmc = 4.0,
            colors = listOf("U"), colorIdentity = listOf("U"), oracleText = "Destroy all creatures.",
        )
        val identity = setOf(ManaColor.U, ManaColor.B, ManaColor.R)
        val profile = scorer.profile(mainboard = emptyList(), format = DeckFormat.COMMANDER, colorIdentity = identity, seedTags = emptyList())
        val resolvedSkeleton = com.mmg.manahub.feature.decks.domain.engine.ArchetypeSkeletonResolver.resolveWithColor(
            format = com.mmg.manahub.feature.decks.domain.engine.ArchetypeFormat.COMMANDER,
            archetype = com.mmg.manahub.feature.decks.domain.engine.ArchetypeId.CONTROL,
            themes = emptyList(),
            identity = identity,
        )

        val result = useCase(
            collection = listOf(redWipe, blueWipe), mainboard = emptyList(),
            profile = profile, resolvedSkeleton = resolvedSkeleton,
        )

        val redScore = result.first { it.fit.card.scryfallId == "red-wipe" }.fit.score
        val blueScore = result.first { it.fit.card.scryfallId == "blue-wipe" }.fit.score
        assertTrue(
            redScore > blueScore,
            "a Red (SECONDARY) board-wipe candidate ($redScore) must rank above an otherwise-identical " +
                "Blue (SUBSTITUTE) one ($blueScore) for the SAME gapped removal_mass role in a Grixis identity",
        )
    }

    @Test
    fun colorAffinityScalingNeverAppliesToTheGenericUnpinnedPath() = runTest {
        // No resolvedSkeleton (GENERIC/no themes) -- themeRoleBonus (and therefore colorAffinityScale)
        // must never fire, so an otherwise-identical Red vs Blue "board wipe" candidate score IDENTICALLY.
        val redWipe = card(
            id = "red-wipe-generic", name = "Red Wipe Generic", typeLine = "Sorcery", cmc = 4.0,
            colors = listOf("R"), colorIdentity = listOf("R"), oracleText = "Destroy all creatures.",
        )
        val blueWipe = card(
            id = "blue-wipe-generic", name = "Blue Wipe Generic", typeLine = "Sorcery", cmc = 4.0,
            colors = listOf("U"), colorIdentity = listOf("U"), oracleText = "Destroy all creatures.",
        )
        val identity = setOf(ManaColor.U, ManaColor.B, ManaColor.R)
        val profile = scorer.profile(mainboard = emptyList(), format = DeckFormat.COMMANDER, colorIdentity = identity, seedTags = emptyList())

        val result = useCase(collection = listOf(redWipe, blueWipe), mainboard = emptyList(), profile = profile, resolvedSkeleton = null)

        val redScore = result.first { it.fit.card.scryfallId == "red-wipe-generic" }.fit.score
        val blueScore = result.first { it.fit.card.scryfallId == "blue-wipe-generic" }.fit.score
        assertEquals(redScore, blueScore, 0.0001f, "the GENERIC/unpinned path must stay byte-identical regardless of colour affinity")
    }

    // ── Performance guard (smoke test, not a rigorous benchmark) ───────────────────────

    @OptIn(kotlin.time.ExperimentalTime::class)
    @Test
    fun scoringA5000CardCollectionCompletesWithinAFewSeconds() = runTest {
        val collection = (1..5000).map { i ->
            card(
                id = "perf-$i", name = "Perf Card $i", typeLine = "Creature — Bear",
                cmc = (i % 8).toDouble(), colors = listOf("R"), colorIdentity = listOf("R"),
                power = (i % 5).toString(), toughness = (i % 5).toString(),
            )
        }
        val profile = scorer.profile(
            mainboard = emptyList(), format = DeckFormat.CASUAL,
            colorIdentity = setOf(ManaColor.R), seedTags = emptyList(),
        )

        val mark = TimeSource.Monotonic.markNow()
        val result = useCase(collection = collection, mainboard = emptyList(), profile = profile, limit = 50)
        val elapsed = mark.elapsedNow()

        // Smoke guard against an accidentally-quadratic implementation, not a tight benchmark —
        // a generous bound to absorb CI variance while still catching an O(n^2) regression.
        assertTrue(elapsed < 3.seconds, "scoring 5000 collection cards took $elapsed, expected < 3s")
        assertTrue(result.size <= 50)
    }

    // ── Workstream 8.2 (Deck Wizard & Engine Rework plan) -- gap-driven adds, named reasons ──

    @Test
    fun aCandidateFillingAResolvedArchetypeGapCarriesFillsArchetypeGapNamingTheBand() = runTest {
        // CONTROL Commander wants removal_mass 5-7-9 (ArchetypeData.kt). An empty mainboard is
        // maximally short (current=0), so a board-wipe candidate should earn the theme-role bonus
        // AND carry ScoreReason.FillsArchetypeGap naming the exact band it fills.
        val wipe = card(
            id = "wipe", name = "Board Wipe", typeLine = "Sorcery", cmc = 4.0,
            colors = listOf("U"), colorIdentity = listOf("U"), oracleText = "Destroy all creatures.",
        )
        val identity = setOf(ManaColor.U, ManaColor.G)
        val profile = scorer.profile(mainboard = emptyList(), format = DeckFormat.COMMANDER, colorIdentity = identity, seedTags = emptyList())
        val resolvedSkeleton = com.mmg.manahub.feature.decks.domain.engine.ArchetypeSkeletonResolver.resolveWithColor(
            format = com.mmg.manahub.feature.decks.domain.engine.ArchetypeFormat.COMMANDER,
            archetype = com.mmg.manahub.feature.decks.domain.engine.ArchetypeId.CONTROL,
            themes = emptyList(),
            identity = identity,
        )

        val result = useCase(collection = listOf(wipe), mainboard = emptyList(), profile = profile, resolvedSkeleton = resolvedSkeleton)

        val fit = result.first { it.fit.card.scryfallId == "wipe" }.fit
        val reason = fit.reasons.firstOrNull { it is ScoreReason.FillsArchetypeGap } as? ScoreReason.FillsArchetypeGap
        assertTrue(reason != null, "the wipe candidate must carry a ScoreReason.FillsArchetypeGap")
        assertEquals("removal_mass", reason!!.roleKey)
        assertEquals(0, reason.current)
        // WS9's color-count modulation can adjust the raw ArchetypeData ideal for a 2-color
        // identity -- assert the SHAPE of the reason (a positive, still-unmet ideal), not a
        // hardcoded number that would silently drift whenever the modulation table is retuned.
        assertTrue(reason.ideal > reason.current, "ideal (${reason.ideal}) must exceed current (${reason.current}) for a gapped role")
        assertEquals("Control", reason.planLabel)
    }

    @Test
    fun aCandidateMatchingNoGappedRoleNeverCarriesFillsArchetypeGap() = runTest {
        // A vanilla creature with no role signal at all must never get a FillsArchetypeGap reason
        // attached, even when a resolvedSkeleton is present (the bonus/reason pair only fires when
        // the candidate actually matches a gapped role -- see themeRoleBonus's own gating).
        val vanilla = card(id = "vanilla", name = "Vanilla Bear", typeLine = "Creature — Bear", power = "2", toughness = "2")
        val identity = setOf(ManaColor.U, ManaColor.G)
        val profile = scorer.profile(mainboard = emptyList(), format = DeckFormat.COMMANDER, colorIdentity = identity, seedTags = emptyList())
        val resolvedSkeleton = com.mmg.manahub.feature.decks.domain.engine.ArchetypeSkeletonResolver.resolveWithColor(
            format = com.mmg.manahub.feature.decks.domain.engine.ArchetypeFormat.COMMANDER,
            archetype = com.mmg.manahub.feature.decks.domain.engine.ArchetypeId.CONTROL,
            themes = emptyList(),
            identity = identity,
        )

        val result = useCase(collection = listOf(vanilla), mainboard = emptyList(), profile = profile, resolvedSkeleton = resolvedSkeleton)

        val fit = result.first { it.fit.card.scryfallId == "vanilla" }.fit
        assertFalse(fit.reasons.any { it is ScoreReason.FillsArchetypeGap }, "a card matching no gapped role must never carry FillsArchetypeGap")
    }
}
