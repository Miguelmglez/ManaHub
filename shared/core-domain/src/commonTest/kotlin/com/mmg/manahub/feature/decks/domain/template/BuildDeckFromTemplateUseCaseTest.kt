package com.mmg.manahub.feature.decks.domain.template

import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.core.model.TagCategory
import com.mmg.manahub.core.model.UserCard
import com.mmg.manahub.core.model.UserCardWithCard
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeId
import com.mmg.manahub.feature.decks.domain.engine.DeckEntry
import com.mmg.manahub.feature.decks.domain.engine.DeckIdentitySeedTags
import com.mmg.manahub.feature.decks.domain.engine.DeckScorer
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.NeutralPowerResolver
import com.mmg.manahub.feature.decks.domain.engine.RoleClassifier
import com.mmg.manahub.feature.decks.domain.engine.SeedStrategy
import com.mmg.manahub.feature.decks.domain.engine.StrategyProfile
import com.mmg.manahub.feature.decks.domain.engine.ThemeId
import com.mmg.manahub.feature.decks.domain.engine.toStrategyProfile
import com.mmg.manahub.feature.decks.domain.engine.card
import com.mmg.manahub.feature.decks.domain.usecase.SuggestAddsFromCollectionUseCase
import com.mmg.manahub.feature.decks.domain.usecase.SuggestAddsFromCommunityUseCase
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
        suggestAddsFromCollectionUseCase = SuggestAddsFromCollectionUseCase(scorer),
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
            DeckWizardSpec(format = DeckFormat.CASUAL, colorIdentity = setOf(ManaColor.R), strategyProfile = SeedStrategy.MIDRANGE.toStrategyProfile()),
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
            DeckWizardSpec(format = DeckFormat.COMMANDER, commander = commander, strategyProfile = SeedStrategy.AGGRO.toStrategyProfile()),
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

    // ── Wizard Quality Campaign Wave 3 (Task 1): category-fill fit floor ──────────────

    /** A cheap-bodied threat with zero identity tags and no legacy-[DeckRole] oracle/tag match --
     * resolves to the "threat_early" category (Appendix A vocabulary, no [DeckRole] mapping, see
     * [SuggestionCategoryResolver]'s step 2.5) via purely structural matching. Scores far below
     * [BuildDeckFromTemplateUseCase.CATEGORY_FILL_FIT_FLOOR] once its own CMC-3 bucket is already
     * saturated: zero synergy (no identity tags), zero role-need/redundancy (THREAT has no slot in
     * the 60-card [com.mmg.manahub.feature.decks.domain.engine.DeckSkeletons] skeleton, so `ideal` is
     * 0 and both terms short-circuit to 0), and a curve score floored to `FULL_BUCKET_FLOOR` (0.1) --
     * base = 0.14*0.1 + 0.20*0.5(power) + 0.10*1.0(color) = 0.214, comfortably under the 0.25 floor. */
    private fun weakThreatCard(id: String = "weak", name: String = "Weak Threat") = card(
        id = id, name = name, typeLine = "Creature — Bear", cmc = 3.0,
        colors = listOf("R"), colorIdentity = listOf("R"), power = "3", toughness = "3", oracleText = null,
    )

    /** Tagless, non-creature CMC-3 filler -- resolves to [SuggestionCategory.OTHER] (no role/tag/tribe
     * match), so it only ever contributes to the deck's CMC-3 curve saturation, never to
     * "threat_early"'s own fill count. */
    private fun cmc3Filler(id: String) = card(
        id = id, name = "Filler CMC3 $id", typeLine = "Sorcery", cmc = 3.0,
        colors = listOf("R"), colorIdentity = listOf("R"), oracleText = null,
    )

    @Test
    fun `an empty owned collection never gets padded with filler -- the build declares structured gaps instead`() = runTest(dispatcher) {
        // Deck Engine Unification (D3): the no-floor top-up pass is GONE -- Motor A is the ONLY
        // placement source, and it only ever draws from the owned collection. With ZERO owned
        // nonland cards beyond the single seed, the Motor A loop's very first candidate pool is
        // empty -- there is nothing to fabricate a fill from, so the mainboard comes up far short
        // of its 60-card CASUAL target and the shortfall is reported as structured gaps rather than
        // silently shipping (or padding with) a card the user doesn't own.
        val result = runToCompletion(
            useCase(),
            DeckWizardSpec(
                format = DeckFormat.CASUAL,
                colorIdentity = setOf(ManaColor.R),
                seeds = listOf(cmc3Filler("filler")),
            ),
            emptyList(),
        )
        assertEquals(1, result.deckCards.filterNot { it.card.typeLine.contains("Basic Land") }.sumOf { it.quantity },
            "only the explicit seed can be placed -- the owned pool is empty")
        assertTrue(result.gaps.isNotEmpty(), "the build must declare a structured gap instead of silently shipping a short deck")
        assertEquals(
            result.gaps.sumOf { it.missingCount },
            DeckFormat.CASUAL.targetDeckSize - result.deckCards.sumOf { it.quantity },
            "declared gaps must reconcile EXACTLY with the true numeric shortfall (D3 invariant: cards + gaps == target)",
        )
    }

    @Test
    fun `a seed below the fit floor is always placed, the floor never applies to seeds`() = runTest(dispatcher) {
        val weakSeed = weakThreatCard(id = "weak-seed", name = "Weak Seed Threat")
        val result = runToCompletion(
            useCase(),
            DeckWizardSpec(
                format = DeckFormat.CASUAL,
                colorIdentity = setOf(ManaColor.R),
                strategyProfile = SeedStrategy.MIDRANGE.toStrategyProfile(),
                seeds = listOf(weakSeed),
            ),
        )
        assertTrue(
            result.deckCards.any { it.card.scryfallId == "weak-seed" },
            "an explicit seed must always be in the built deck, regardless of its own fit score -- seeds " +
                "are placed before the category-fill loop even runs and never pass through the floor check",
        )
    }

    @Test
    fun `category fill report honestly reflects a floored-out category that top-up never needed`() = runTest(dispatcher) {
        val weak = weakThreatCard()
        val collection = listOf(owned("Weak Threat", 4) { weak })
        // Enough seed filler (40 nonland cards, comfortably at/above CASUAL/MIDRANGE's
        // targetNonLandCount) to exhaust topUpFromCollection's own gap BEFORE it ever needs to reach
        // for the floored-out "Weak Threat" -- proves the report's honest gap isn't just an artifact
        // of top-up never getting a chance to look.
        val cheapFillers = (1..30).map { i ->
            card(
                id = "cheap-$i", name = "Cheap Filler $i", typeLine = "Sorcery", cmc = 1.0,
                colors = listOf("R"), colorIdentity = listOf("R"), oracleText = null,
            )
        }
        val bucket3Fillers = (1..10).map { i -> cmc3Filler("bucket3-$i") }
        val result = runToCompletion(
            useCase(),
            DeckWizardSpec(
                format = DeckFormat.CASUAL,
                colorIdentity = setOf(ManaColor.R),
                strategyProfile = SeedStrategy.MIDRANGE.toStrategyProfile(),
                seeds = cheapFillers + bucket3Fillers,
            ),
            collection,
        )
        val threatEarlyFill = result.report.first { it.category.id == "threat_early" }
        assertTrue(threatEarlyFill.target > 0, "threat_early must be a real, targeted category for MIDRANGE")
        assertEquals(0, threatEarlyFill.filled, "the floored-out category must show an honest 0, not padded by top-up")
        assertTrue(
            result.deckCards.none { it.card.scryfallId == "weak" },
            "top-up must not need the floored-out candidate once the seed pool alone covers the target",
        )
    }

    // ── Wizard Quality Campaign Wave 4 (Task 1): wizard/Doctor pinned-identity seed-tag parity ──

    @Test
    fun `a GRAVEYARD-hint deck's persisted archetype pin folds the same GRAVEYARD seed tag the wizard used at build time`() = runTest(dispatcher) {
        // Root cause (project_wizard_quality_campaign_wave3 memory, bucket ii): the wizard's OWN
        // recomputeProfile fed deckScorer.fit an EXPLICIT CardTag.GRAVEYARD seed tag while building
        // (via SeedStrategy.GRAVEYARD.primaryTags + the theme->tag table), but DeckDoctorOrchestrator
        // .loadAnalysis scored the finished deck with INFERENCE ONLY -- the SAME card could clear the
        // wizard's category-fill floor and still fall below the Doctor's cut floor. This test proves
        // the persisted (archetypeOverride, themesOverride) pin the wizard writes onto the Deck now
        // round-trips back to the identical CardTag.GRAVEYARD signal via the single shared
        // DeckIdentitySeedTags table -- both sides now rank a graveyard-hint deck's cards on the same
        // basis.
        val commander = card(id = "cmd", name = "Test Commander", typeLine = "Legendary Creature — Human", colorIdentity = listOf("B", "U"))
        val result = runToCompletion(
            useCase(),
            DeckWizardSpec(format = DeckFormat.COMMANDER, commander = commander, strategyProfile = SeedStrategy.GRAVEYARD.toStrategyProfile()),
        )
        assertEquals("GENERIC", result.archetypeOverride)
        assertEquals(listOf("REANIMATOR"), result.themesOverride)

        // What the wizard's own build-time recomputeProfile fed deckScorer.fit with for this build
        // (Deck Engine Unification D2: reads the RESOLVED template archetype/themes, exactly what
        // recomputeProfile itself computes -- SeedStrategy.GRAVEYARD.toStrategyProfile() resolves to
        // GENERIC + REANIMATOR, and GENERIC's own archetypeSeedTags is intentionally empty).
        val wizardExplicitTags = DeckIdentitySeedTags.forArchetype(ArchetypeId.GENERIC, listOf(ThemeId.REANIMATOR))
        assertTrue(CardTag.GRAVEYARD in wizardExplicitTags)

        // What DeckDoctorOrchestrator's pinSeedTags (Wave 4) derives, reading back the EXACT strings
        // the wizard persisted onto the Deck (writeResultIntoNewDeck -> DeckRepository
        // .updateArchetypeOverride) -- never a separately-maintained table.
        val persistedArchetype = ArchetypeId.entries.first { it.name == result.archetypeOverride }
        val persistedThemes = result.themesOverride.map { name -> ThemeId.entries.first { it.name == name } }
        val doctorPinTags = DeckIdentitySeedTags.forArchetype(persistedArchetype, persistedThemes)
        assertTrue(CardTag.GRAVEYARD in doctorPinTags, "the Doctor's persisted-pin fold-in must recover the same GRAVEYARD signal the wizard built with")

        // Concrete scoring proof: a graveyard-recursion candidate's fit score under the finished
        // deck's profile is HIGHER once the Doctor's pin fold-in is applied than it would be from
        // pure mainboard-inference alone -- the fold-in is what closes the gap, not incidental noise.
        val mainboard = result.deckCards.map { DeckEntry(card = it.card, quantity = it.quantity, isOwned = true, isSideboard = false) }
        val colorIdentity = commander.colorIdentity.mapNotNull { symbol -> ManaColor.entries.firstOrNull { it.symbol == symbol } }.toSet()
        val profileWithoutPin = scorer.profile(mainboard = mainboard, format = DeckFormat.COMMANDER, colorIdentity = colorIdentity, seedTags = emptyList())
        val profileWithPin = scorer.profile(mainboard = mainboard, format = DeckFormat.COMMANDER, colorIdentity = colorIdentity, seedTags = doctorPinTags)
        val recursionCandidate = card(
            id = "recur", name = "Graveyard Recursion Spell", typeLine = "Sorcery",
            colors = listOf("B"), colorIdentity = listOf("B"), tags = listOf(CardTag.GRAVEYARD),
        )
        val fitWithoutPin = scorer.fit(recursionCandidate, profileWithoutPin, isOwned = true).score
        val fitWithPin = scorer.fit(recursionCandidate, profileWithPin, isOwned = true).score
        assertTrue(
            fitWithPin > fitWithoutPin,
            "a graveyard-recursion candidate's Doctor fit score must rise once the deck's persisted GRAVEYARD pin is folded in",
        )
    }

    // ── Edge-case audit (Wizard Quality Campaign final gate, 2026-07-19): fillLands=false ─────

    @Test
    fun `fillLands = false must fill the FULL mainboard from owned nonland cards, never hold back land-sized slots`() = runTest(dispatcher) {
        // DIAGNOSTIC: reproduces a suspected bug where topUpFromCollection's first pass reserves
        // `plannedLands` slots (targetNonLandCount = targetMainboardSize - plannedLands) assuming
        // fillLands will later consume them -- but when fillLands=false, fillLands() never runs and
        // no second top-up pass exists outside the `if (spec.fillLands)` branch to reclaim those
        // reserved slots with nonland cards. The owned pool below has far more than 99 legal
        // candidates, so a correct build should reach the full 99-card mainboard with shortfall=0.
        val commander = card(id = "cmd", name = "Mono Red Commander", typeLine = "Legendary Creature — Human", colorIdentity = listOf("R"))
        val collection = (1..110).map { i ->
            owned("Red Filler $i", 1) {
                card(id = "filler-$i", name = "Red Filler $i", typeLine = "Sorcery", cmc = (i % 6 + 1).toDouble(), colors = listOf("R"), colorIdentity = listOf("R"), oracleText = null)
            }
        }
        val result = runToCompletion(
            useCase(),
            DeckWizardSpec(format = DeckFormat.COMMANDER, commander = commander, fillLands = false),
            collection,
        )
        assertTrue(result.deckCards.none { it.card.typeLine.contains("Basic Land") }, "fillLands=false must never materialize basics")
        assertTrue(result.gaps.isEmpty(), "no gap must be declared when >=99 legal owned nonland candidates exist and fillLands=false")
        assertEquals(99, result.deckCards.sumOf { it.quantity }, "the full 99-card mainboard must be reachable from owned nonland cards alone when fillLands=false")
    }

    // ── Edge-case audit (Wizard Quality Campaign final gate, 2026-07-19): tribe hint ──────────

    @Test
    fun `strategyProfile tribe measurably raises an owned matching-tribe candidate's fit score over an equivalent off-tribe candidate`() = runTest(dispatcher) {
        // Regression: the Direction step's tribe chips (CollectionTribeSignal) were rendered
        // selectable and echoed on the Review screen but had ZERO effect on the build --
        // DeckWizardSpec had no field to carry the pick and BuildDeckFromTemplateUseCase never
        // read one. This proves the fix: StrategyProfile.tribe ("tribe:elf") folded into
        // recomputeProfile's explicit seed tags as a synthetic TRIBAL CardTag actually shifts
        // deckScorer.fit toward candidates carrying that specific tribe key.
        val commander = card(id = "cmd", name = "Test Commander", typeLine = "Legendary Creature — Human", colorIdentity = listOf("G"))
        // Single subtype ("Elf" only, no second subtype) so TribeDeriver.subtypeKeys yields exactly
        // {tribe:elf} -- a second subtype (e.g. "Elf Warrior") would ALSO add an unmatched
        // tribe:warrior signal into synergyScore's ownWeight denominator, diluting the score via the
        // sqrt(ownWeight) term and muddying the "hint raises the score" assertion below.
        val elfCandidate = card(
            id = "elf", name = "Elf Candidate", typeLine = "Creature — Elf",
            colors = listOf("G"), colorIdentity = listOf("G"), cmc = 2.0, power = "2", toughness = "2", oracleText = null,
        )
        val offTribeCandidate = card(
            id = "bear", name = "Bear Candidate", typeLine = "Creature — Bear",
            colors = listOf("G"), colorIdentity = listOf("G"), cmc = 2.0, power = "2", toughness = "2", oracleText = null,
        )

        val profileWithoutHint = scorer.profile(
            mainboard = listOf(DeckEntry(card = commander, quantity = 1, isOwned = true, isSideboard = false)),
            format = DeckFormat.COMMANDER,
            colorIdentity = setOf(ManaColor.G),
            seedTags = emptyList(),
        )
        val profileWithHint = scorer.profile(
            mainboard = listOf(DeckEntry(card = commander, quantity = 1, isOwned = true, isSideboard = false)),
            format = DeckFormat.COMMANDER,
            colorIdentity = setOf(ManaColor.G),
            seedTags = listOf(CardTag(key = "tribe:elf", category = TagCategory.TRIBAL)),
        )

        val elfFitWithoutHint = scorer.fit(elfCandidate, profileWithoutHint, isOwned = true).score
        val elfFitWithHint = scorer.fit(elfCandidate, profileWithHint, isOwned = true).score
        val bearFitWithHint = scorer.fit(offTribeCandidate, profileWithHint, isOwned = true).score

        assertTrue(elfFitWithHint > elfFitWithoutHint, "an Elf candidate's fit must rise once the tribe:elf hint is folded into the profile's seed tags")
        assertTrue(elfFitWithHint > bearFitWithHint, "an Elf candidate must score higher than an equivalent off-tribe candidate once tribe:elf is hinted")
    }

    @Test
    fun `strategyProfile tribe flows end-to-end through DeckWizardSpec into recomputeProfile's seed tags`() = runTest(dispatcher) {
        // Plumbing/wiring smoke test (the score-level proof is the test above): a build with
        // strategyProfile.tribe set must complete normally and place the owned tribe-matching card
        // -- confirms StrategyProfile.tribe -> recomputeProfile's explicit seed tags ->
        // deckScorer.fit is wired end-to-end, not just correct in isolation at the DeckScorer level.
        val commander = card(id = "cmd", name = "Test Commander", typeLine = "Legendary Creature — Human", colorIdentity = listOf("G"))
        val elfCandidate = card(
            id = "elf", name = "Elf Candidate", typeLine = "Creature — Elf",
            colors = listOf("G"), colorIdentity = listOf("G"), cmc = 2.0, power = "2", toughness = "2", oracleText = null,
        )
        val collection = listOf(owned("Elf Candidate", 1) { elfCandidate })
        val result = runToCompletion(
            useCase(),
            DeckWizardSpec(
                format = DeckFormat.COMMANDER,
                commander = commander,
                strategyProfile = StrategyProfile(tribe = "tribe:elf"),
                fillLands = false,
            ),
            collection,
        )
        assertTrue(result.deckCards.any { it.card.scryfallId == "elf" }, "the tribe-hinted Elf candidate must be placed and the build must not fail with strategyProfile.tribe set")
    }

    // ── Motor B per-build toggle (Deck Engine Unification plan §5 Phase 3.5) ────────────────

    @Test
    fun `Motor B is never fetched when useCommunityData is false, even with the global flag on`() = runTest(dispatcher) {
        val communityRepo = FakeCommunityAggregateRepository()
        val seed = card(id = "seed", name = "Seed Card", colorIdentity = listOf("R"))
        val useCaseWithMotorB = BuildDeckFromTemplateUseCase(
            deckTemplateResolver = DeckTemplateResolver(FakeCommunityAggregateRepository(), ioDispatcher = dispatcher),
            deckScorer = scorer,
            cardRepository = basicLandRepository(),
            suggestAddsFromCollectionUseCase = SuggestAddsFromCollectionUseCase(scorer),
            ioDispatcher = dispatcher,
            communityAggregateRepository = communityRepo,
            suggestAddsFromCommunityUseCase = SuggestAddsFromCommunityUseCase(cardRepository = basicLandRepository()),
            isCommunityEngineEnabled = { true },
        )
        runToCompletion(useCaseWithMotorB, DeckWizardSpec(format = DeckFormat.CASUAL, seeds = listOf(seed), useCommunityData = false))
        assertEquals(0, communityRepo.getSixtyAggregateCallCount)
    }

    @Test
    fun `Motor B is fetched only when BOTH useCommunityData and the global flag are true`() = runTest(dispatcher) {
        val communityRepo = FakeCommunityAggregateRepository()
        val seed = card(id = "seed", name = "Seed Card", colorIdentity = listOf("R"))
        val useCaseWithMotorB = BuildDeckFromTemplateUseCase(
            deckTemplateResolver = DeckTemplateResolver(FakeCommunityAggregateRepository(), ioDispatcher = dispatcher),
            deckScorer = scorer,
            cardRepository = basicLandRepository(),
            suggestAddsFromCollectionUseCase = SuggestAddsFromCollectionUseCase(scorer),
            ioDispatcher = dispatcher,
            communityAggregateRepository = communityRepo,
            suggestAddsFromCommunityUseCase = SuggestAddsFromCommunityUseCase(cardRepository = basicLandRepository()),
            isCommunityEngineEnabled = { true },
        )
        runToCompletion(useCaseWithMotorB, DeckWizardSpec(format = DeckFormat.CASUAL, seeds = listOf(seed), useCommunityData = true))
        assertEquals(1, communityRepo.getSixtyAggregateCallCount)
    }

    @Test
    fun `Motor B stays off when the global flag is off, even if useCommunityData is true`() = runTest(dispatcher) {
        val communityRepo = FakeCommunityAggregateRepository()
        val seed = card(id = "seed", name = "Seed Card", colorIdentity = listOf("R"))
        val useCaseWithMotorB = BuildDeckFromTemplateUseCase(
            deckTemplateResolver = DeckTemplateResolver(FakeCommunityAggregateRepository(), ioDispatcher = dispatcher),
            deckScorer = scorer,
            cardRepository = basicLandRepository(),
            suggestAddsFromCollectionUseCase = SuggestAddsFromCollectionUseCase(scorer),
            ioDispatcher = dispatcher,
            communityAggregateRepository = communityRepo,
            suggestAddsFromCommunityUseCase = SuggestAddsFromCommunityUseCase(cardRepository = basicLandRepository()),
            isCommunityEngineEnabled = { false },
        )
        runToCompletion(useCaseWithMotorB, DeckWizardSpec(format = DeckFormat.CASUAL, seeds = listOf(seed), useCommunityData = true))
        assertEquals(0, communityRepo.getSixtyAggregateCallCount)
    }

    // ── Deck Engine Unification RUN 3b QA fix -- commander is Commander-only, defense in depth ──

    @Test
    fun `a CASUAL spec's out-of-band commander field is ignored by seed-tag inference and trimming`() = runTest(dispatcher) {
        // DeckWizardSpec.commander is documented as "required only when format is COMMANDER" but
        // nothing in the type system enforces that -- recomputeProfile's seed-tag inference and
        // trimExcess's protectedIds must both ignore a non-null commander on a non-Commander spec.
        // The wizard VM's own onSelectFormat now resets selectedCommander on a format switch (RUN
        // 3b), making this unreachable via normal UI flow -- but this use case must stay correct
        // independent of that VM-side fix (see recomputeProfile/trimExcess's inline comments).
        val stowawayCommander = card(
            id = "stowaway-cmd", name = "Stowaway Commander", typeLine = "Legendary Creature — Human",
            colorIdentity = listOf("R"), tags = listOf(CardTag.TRIBAL),
        )
        val withoutCommander = runToCompletion(useCase(), DeckWizardSpec(format = DeckFormat.CASUAL, colorIdentity = setOf(ManaColor.R)))
        val withStowawayCommander = runToCompletion(
            useCase(),
            DeckWizardSpec(format = DeckFormat.CASUAL, colorIdentity = setOf(ManaColor.R), commander = stowawayCommander),
        )
        assertEquals(
            withoutCommander.deckCards.map { it.card.scryfallId to it.quantity },
            withStowawayCommander.deckCards.map { it.card.scryfallId to it.quantity },
            "a non-Commander spec's commander field must never influence the build",
        )
        assertEquals(withoutCommander.archetypeOverride, withStowawayCommander.archetypeOverride)
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
