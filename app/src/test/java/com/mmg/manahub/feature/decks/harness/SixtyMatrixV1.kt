package com.mmg.manahub.feature.decks.harness
// COMMENTS_REVIEWED: 2026-09-17

import com.mmg.manahub.core.domain.usecase.decks.BasicLandCalculator
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.feature.decks.domain.engine.BuildAnchor
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.StrategyPick
import com.mmg.manahub.feature.decks.domain.engine.isLegalForFormat
import com.mmg.manahub.feature.decks.domain.template.CollectionProfile
import com.mmg.manahub.feature.decks.domain.template.CollectionProfileUseCase
import com.mmg.manahub.feature.decks.domain.template.OwnedCard
import com.mmg.manahub.feature.decks.domain.usecase.RecommendWizardStrategiesUseCase
import kotlin.random.Random

// ═══════════════════════════════════════════════════════════════════════════════
//  Deck Wizard 60-card wave (v6) plan, Phase 6.2 — the real-collection Sixty harness's matrix
//  (spec generation + build runner). Reuses CommanderMatrixV2's own pipeline/use-case factory and
//  ownedPool() (both `internal`, same module — W8's own precedent for WizardHarnessV3RealCollectionTest
//  doing exactly this rather than a second copy) — this file adds ONLY the Sixty-specific matrix
//  construction (color combos, cards-anchor sampling) that has no Commander-path equivalent.
//
//  "top-6 owned color combos (via CollectionProfile.colorShares)" (plan §5 Phase 6.2 literal text):
//  colorShares itself only ranks INDIVIDUAL colors (CollectionProfileUseCase's own contract — see
//  its KDoc), not multi-color combos — there is no existing engine function that turns a share-ranked
//  color list into "the user's own color-combo leanings" (recomputeColorComboSuggestions in
//  DeckWizardViewModel does something adjacent, but scores catalog-defined combos by tag/theme, a
//  different job entirely). [topColorCombos] is a harness-only, documented judgment call: every
//  1-3-color subset of the top 4 ranked colors, scored by summed share, top 6 taken — matrix
//  CONSTRUCTION logic (deciding which specs to run), not a build-engine heuristic, so it does not
//  fall under the "no wizard-only heuristics" rule (that rule governs what BuildWizardDeckUseCase
//  itself does, not how a test picks which inputs to feed it — same category as CommanderMatrixV2's
//  own commander-eligibility/color-affinity spec-generation judgment calls).
// ═══════════════════════════════════════════════════════════════════════════════

data class SixtySpecV1(
    val label: String,
    val format: DeckFormat,
    val anchorKind: String, // "colors" | "cards"
    val identity: Set<ManaColor>,
    val seeds: List<Card>,
    val strategySource: String, // "recommended" | "custom"
    val pick: StrategyPick,
)

object SixtyMatrixV1 {

    private val SIXTY_FORMATS = listOf(DeckFormat.STANDARD, DeckFormat.PIONEER, DeckFormat.MODERN, DeckFormat.CASUAL)
    private const val TOP_COMBO_COUNT = 6
    private const val CARDS_ANCHOR_COUNT = 20
    private const val CARDS_ANCHOR_SEED_SIZE = 4
    private const val CARDS_ANCHOR_SEED: Long = 60614L // Deck Wizard 60-card wave (v6), Phase 6.2 — fixed for determinism.

    fun ownedPool(fixtures: HarnessFixtures): List<OwnedCard> = CommanderMatrixV2.ownedPool(fixtures)

    /** Every 1-3 color subset of the top 4 [CollectionProfile.colorShares] colors, scored by summed
     * share, deterministically tied-broken by the combo's own sorted color names, top
     * [TOP_COMBO_COUNT] taken (fewer when the collection's own owned colors don't reach that many
     * distinct subsets — e.g. a mono-color-heavy real collection). */
    suspend fun topColorCombos(fixtures: HarnessFixtures): List<Set<ManaColor>> {
        val profile = CollectionProfileUseCase()(fixtures.ownedCardsByName)
        val ranked = profile.colorShares.take(4)
        if (ranked.isEmpty()) return emptyList()
        val shareOf = ranked.associate { it.color to it.share }
        val colors = ranked.map { it.color }

        val combos = mutableListOf<Set<ManaColor>>()
        fun combinations(start: Int, size: Int, current: List<ManaColor>) {
            if (current.size == size) {
                combos += current.toSet()
                return
            }
            for (i in start until colors.size) {
                combinations(i + 1, size, current + colors[i])
            }
        }
        for (size in 1..3) combinations(0, size, emptyList())

        // AVERAGE share, not summed: a raw sum monotonically favors the largest subset (every
        // ranked color's share is positive, so adding one always increases the total), which
        // systematically starves out mono/2-color combos even when they're the collection's actual
        // strongest lean. Averaging reads "how concentrated is the user's own collection in exactly
        // these colors together" instead.
        return combos.distinct()
            .sortedWith(
                compareByDescending<Set<ManaColor>> { combo -> combo.sumOf { (shareOf[it] ?: 0f).toDouble() } / combo.size }
                    .thenBy { combo -> combo.sortedBy { it.name }.joinToString(",") { it.name } },
            )
            .take(TOP_COMBO_COUNT)
    }

    private fun recommendedOrCustom(format: DeckFormat, anchor: BuildAnchor.Sixty, owned: List<OwnedCard>): StrategyPick {
        val recommendations = runCatching {
            RecommendWizardStrategiesUseCase()(format, anchor, ownedCollection = owned)
        }.getOrElse { emptyList() }
        val top = recommendations.firstOrNull()
        return top?.let { StrategyPick.Curated(it.strategy, it.tribe) } ?: StrategyPick.Custom
    }

    /** Colors-flow anchor specs — empty seeds (plan §5 Phase 6.2: "as a Colors-flow anchor"), every
     * tested [SIXTY_FORMATS] x [topColorCombos] x {recommended, Custom}. */
    suspend fun colorSpecs(fixtures: HarnessFixtures, owned: List<OwnedCard>): List<SixtySpecV1> {
        val combos = topColorCombos(fixtures)
        return SIXTY_FORMATS.flatMap { format ->
            combos.flatMap { identity ->
                val anchor = BuildAnchor.Sixty(identity, emptyList())
                val recommendedPick = recommendedOrCustom(format, anchor, owned)
                val label = "colors_${format.name.lowercase()}_${identity.sortedBy { it.name }.joinToString("") { it.symbol }}"
                listOf(
                    SixtySpecV1("${label}_recommended", format, "colors", identity, emptyList(), "recommended", recommendedPick),
                    SixtySpecV1("${label}_custom", format, "colors", identity, emptyList(), "custom", StrategyPick.Custom),
                )
            }
        }
    }

    /**
     * Cards-flow anchor specs (plan §5 Phase 6.2: "20 additional cards-anchor specs, random
     * owned-legal 4-card seed sets, seeded RNG for determinism") — [CARDS_ANCHOR_SEED] fixes the
     * sample across re-runs; formats rotate round-robin across [SIXTY_FORMATS] so all four are
     * exercised, and strategy source alternates recommended/Custom (even/odd index) so this segment
     * covers both without doubling the plan's own literal "20 specs" count.
     */
    fun cardsAnchorSpecs(fixtures: HarnessFixtures, owned: List<OwnedCard>): List<SixtySpecV1> {
        val rng = Random(CARDS_ANCHOR_SEED)
        val eligibleByFormat = SIXTY_FORMATS.associateWith { format ->
            fixtures.ownedCardsByName
                .filterNot { BasicLandCalculator.isLand(it) }
                .filter { isLegalForFormat(it, format) }
        }
        return (0 until CARDS_ANCHOR_COUNT).mapNotNull { i ->
            val format = SIXTY_FORMATS[i % SIXTY_FORMATS.size]
            val pool = eligibleByFormat[format].orEmpty()
            if (pool.size < CARDS_ANCHOR_SEED_SIZE) return@mapNotNull null
            val seeds = pool.shuffled(rng).take(CARDS_ANCHOR_SEED_SIZE)
            val identity = seeds.flatMap { it.colorIdentity }
                .mapNotNull { symbol -> ManaColor.entries.firstOrNull { it.symbol == symbol } }
                .toSet()
            val anchor = BuildAnchor.Sixty(identity, seeds)
            val useRecommended = i % 2 == 0
            val pick = if (useRecommended) recommendedOrCustom(format, anchor, owned) else StrategyPick.Custom
            SixtySpecV1(
                "cards_anchor_${i}_${format.name.lowercase()}", format, "cards", identity, seeds,
                if (useRecommended) "recommended" else "custom", pick,
            )
        }
    }

    suspend fun specs(fixtures: HarnessFixtures, owned: List<OwnedCard>): List<SixtySpecV1> =
        colorSpecs(fixtures, owned) + cardsAnchorSpecs(fixtures, owned)

    suspend fun run(spec: SixtySpecV1, owned: List<OwnedCard>): V3SixtyBuildMetrics {
        val anchor = BuildAnchor.Sixty(spec.identity, spec.seeds)
        val started = System.currentTimeMillis()
        val useCase1 = CommanderMatrixV2.newBuildUseCase()
        val outcome = runCatching {
            val draft = useCase1.buildWithGroups(spec.format, anchor, spec.pick, owned, includeNonBasicLands = true)
            useCase1.finalize(draft, resolutions = emptyMap(), fillLands = true)
        }.getOrElse { t ->
            return HarnessMetricsV3SixtyCalculator.forFailedBuild(spec.label, spec.format, spec.strategySource, spec.anchorKind, t.message ?: t.toString())
        }
        val runtimeMs = System.currentTimeMillis() - started

        val secondEntries = runCatching {
            val useCase2 = CommanderMatrixV2.newBuildUseCase()
            val draft2 = useCase2.buildWithGroups(spec.format, anchor, spec.pick, owned, includeNonBasicLands = true)
            useCase2.finalize(draft2, resolutions = emptyMap(), fillLands = true)
        }.getOrNull()?.result?.entries

        val ownedByName: Map<String, Int> = owned.groupBy { it.card.name }.mapValues { (_, rows) -> rows.sumOf { it.quantity } }

        return HarnessMetricsV3SixtyCalculator.compute(
            label = spec.label,
            format = spec.format,
            strategySource = spec.strategySource,
            anchorKind = spec.anchorKind,
            outcome = outcome,
            secondRunEntries = secondEntries,
            ownedByName = ownedByName,
            runtimeMs = runtimeMs,
        )
    }
}
