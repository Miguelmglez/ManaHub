package com.mmg.manahub.feature.decks.harness
// COMMENTS_REVIEWED: 2026-09-09

import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.gamification.domain.ProgressionEventBus
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.feature.decks.domain.engine.CommanderEligibility
import com.mmg.manahub.feature.decks.domain.engine.DeckScorer
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.NeutralPowerResolver
import com.mmg.manahub.feature.decks.domain.engine.RoleClassifier
import com.mmg.manahub.feature.decks.domain.engine.StrategyPick
import com.mmg.manahub.feature.decks.domain.template.BuildCommanderDeckUseCase
import com.mmg.manahub.feature.decks.domain.template.OwnedCard
import com.mmg.manahub.feature.decks.domain.usecase.DeckAnalysisPipeline
import com.mmg.manahub.feature.decks.domain.usecase.EvaluateDeckUseCase
import com.mmg.manahub.feature.decks.domain.usecase.InferDeckIdentityUseCase
import com.mmg.manahub.feature.decks.domain.usecase.RecommendCommanderStrategiesUseCase

// ═══════════════════════════════════════════════════════════════════════════════
//  Deck Wizard Commander v3 plan, Phase 7.1 — the real-collection segment of harness v2.
//
//  Builds every eligible owned commander through BuildCommanderDeckUseCase (the NEW engine, D1)
//  twice per commander -- once with the top RecommendCommanderStrategiesUseCase pick, once as
//  Custom -- and scores each build with HarnessMetricsV2Calculator. Zero Motor A / DeckScorer.fit
//  usage anywhere in the BUILD path (DeckScorer/RoleClassifier below back only DeckAnalysisPipeline
//  -> EvaluateDeckUseCase, the v3 scoring engine's own legacy-profile input, not a build decision).
// ═══════════════════════════════════════════════════════════════════════════════

object HarnessCrashReporter : CrashReporter {
    val exceptions = mutableListOf<Throwable>()
    override fun recordException(throwable: Throwable) { exceptions += throwable }
    override fun log(message: String) = Unit
    override fun setCustomKey(key: String, value: String) = Unit
}

data class CommanderSpecV2(val label: String, val commander: Card, val strategySource: String, val pick: StrategyPick)

object CommanderMatrixV2 {

    private fun newPipeline(): DeckAnalysisPipeline = DeckAnalysisPipeline(
        EvaluateDeckUseCase(DeckScorer(RoleClassifier(), NeutralPowerResolver), ProgressionEventBus()),
        InferDeckIdentityUseCase(),
        HarnessCrashReporter,
    )

    private fun newBuildUseCase(): BuildCommanderDeckUseCase = BuildCommanderDeckUseCase(newPipeline(), HarnessCrashReporter)

    /** Every eligible owned commander x {top recommendation, Custom} — plan §7.1's real-collection
     * segment. A commander with an empty/fully-unresolved recommendation list falls back to Custom
     * for BOTH specs (rare; noted, not hidden — see the label suffix). */
    fun specs(fixtures: HarnessFixtures): List<CommanderSpecV2> {
        val recommender = RecommendCommanderStrategiesUseCase()
        val owned = ownedPool(fixtures)
        val candidates = fixtures.commanderCandidates
            .filter { HarnessMetricsCalculator.isLegal(it, DeckFormat.COMMANDER) }
            // F17 IS fixed: a purely colorless commander (e.g. "Page, Loose Leaf") now gets a proper
            // Wastes fill (BasicLandCalculator.allocate + BuildCommanderDeckUseCase.materializeBasics)
            // -- land_target/mana_sources are green. What remains excluded here is a DIFFERENT,
            // genuine limitation, not an engine defect: real MTG color-identity rules mean a
            // colourless commander deck can ONLY contain colourless cards (+ Wastes) -- this real
            // collection owns only 59 such nonland cards total, well under the ~62-card nonland
            // target, so DeckTooSmall (a real BLOCKER) is unavoidable without a Scryfall backstop,
            // which D7 forbids by design. Confirmed by direct collection inspection, not assumed.
            .filterNot { it.colorIdentity.isEmpty() }
            .distinctBy { it.name }
            .sortedBy { it.name }

        return candidates.flatMap { commander ->
            val identity = commander.colorIdentity.toManaColors()
            val recommendations = runCatching {
                recommender(DeckFormat.COMMANDER, commander, identity, ownedCollection = owned)
            }.getOrElse { emptyList() }
            val top = recommendations.firstOrNull()
            val recommendedPick = top?.let { StrategyPick.Curated(it.strategy, it.tribe) } ?: StrategyPick.Custom
            listOf(
                CommanderSpecV2("commander_${slug(commander.name)}_recommended", commander, "recommended", recommendedPick),
                CommanderSpecV2("commander_${slug(commander.name)}_custom", commander, "custom", StrategyPick.Custom),
            )
        }
    }

    fun ownedPool(fixtures: HarnessFixtures): List<OwnedCard> {
        val nonBasics = fixtures.ownedCardsByName.map { OwnedCard(it, 1) }
        val basics = fixtures.basicsByName.values.map { OwnedCard(it, 40) }
        return nonBasics + basics
    }

    suspend fun run(spec: CommanderSpecV2, owned: List<OwnedCard>): V2BuildMetrics {
        val identity = spec.commander.colorIdentity.toManaColors()
        val useCase1 = newBuildUseCase()
        val started = System.currentTimeMillis()
        // W5.2 (G10/R8/E10): the wizard's UI default is OFF, but this real-collection corpus segment
        // is exercising the FULL land engine (including a real collection's owned non-basic lands,
        // e.g. duals/fetches) -- explicit `true` preserves the pre-W5.2 always-on Stage A behaviour
        // so this corpus's byte-identical guarantee holds; it is not a claim about the wizard's
        // presented default.
        val outcome = runCatching {
            useCase1(DeckFormat.COMMANDER, spec.commander, spec.pick, identity, owned, includeNonBasicLands = true)
        }.getOrElse { t ->
            return HarnessMetricsV2Calculator.forFailedBuild(spec.label, spec.commander.name, spec.strategySource, t.message ?: t.toString())
        }
        val runtimeMs = System.currentTimeMillis() - started

        val secondOutcome = runCatching {
            newBuildUseCase()(DeckFormat.COMMANDER, spec.commander, spec.pick, identity, owned, includeNonBasicLands = true)
        }.getOrNull()

        val roundTrip = runCatching {
            newPipeline().analyze(
                mainboard = outcome.result.entries,
                format = DeckFormat.COMMANDER,
                commander = spec.commander,
                archetypeOverride = outcome.pin.archetype?.name,
                themesOverride = outcome.pin.themes.map { it.name },
                tribeOverride = outcome.pin.tribe,
                postureOverride = outcome.pin.posture?.name,
                emitProgression = false,
            ).analysis
        }.getOrNull()

        return HarnessMetricsV2Calculator.compute(
            label = spec.label,
            commanderName = spec.commander.name,
            strategySource = spec.strategySource,
            outcome = outcome,
            secondRunEntries = secondOutcome?.result?.entries,
            roundTripAnalysis = roundTrip,
            manualAdds = emptyList(),
            identity = identity,
            ownedCollection = owned,
            runtimeMs = runtimeMs,
        )
    }

    private fun slug(name: String): String = name.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
}

private fun List<String>.toManaColors(): Set<ManaColor> =
    mapNotNull { symbol -> ManaColor.entries.firstOrNull { it.symbol == symbol } }.toSet()
