package com.mmg.manahub.feature.decks.harness
// COMMENTS_REVIEWED: 2026-09-08

import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.gamification.domain.ProgressionEventBus
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeFormat
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeSkeletonResolver
import com.mmg.manahub.feature.decks.domain.engine.CardFit
import com.mmg.manahub.feature.decks.domain.engine.DeckEntry
import com.mmg.manahub.feature.decks.domain.engine.DeckProfile
import com.mmg.manahub.feature.decks.domain.engine.DeckScorer
import com.mmg.manahub.feature.decks.domain.engine.NeutralPowerResolver
import com.mmg.manahub.feature.decks.domain.engine.ResolvedArchetypeSkeleton
import com.mmg.manahub.feature.decks.domain.engine.RoleClassifier
import com.mmg.manahub.feature.decks.domain.usecase.AddSuggestion
import com.mmg.manahub.feature.decks.domain.usecase.DeckAnalysisPipeline
import com.mmg.manahub.feature.decks.domain.usecase.DeckHealth
import com.mmg.manahub.feature.decks.domain.usecase.EvaluateDeckUseCase
import com.mmg.manahub.feature.decks.domain.usecase.InferDeckIdentityUseCase
import com.mmg.manahub.feature.decks.domain.usecase.SuggestAddsFromCollectionUseCase
import com.mmg.manahub.feature.decks.domain.usecase.SuggestCutsUseCase

// ═══════════════════════════════════════════════════════════════════════════════
//  Wizard Quality Campaign -- Phase H harness. Drives the SAME Deck Doctor Studio-Suggestions-tab
//  pipeline (DeckAnalysisPipeline -> SuggestCutsUseCase / SuggestAddsFromCollectionUseCase, Motor A)
//  over a wizard-built deck. Deck Wizard Commander v3 plan (E4, D2): the seed-inference + pin-fold +
//  evaluate slice now delegates to the SAME DeckAnalysisPipeline the orchestrator and (later) the
//  wizard use -- this object no longer re-derives that sequence itself. The orchestrator ITSELF is
//  still not reused directly: it is a stateful class wired to Room/Flow repositories the harness has
//  no need for; this object calls the shared pipeline plus the SAME two Motor A use cases the
//  orchestrator used to call before Motor A was retired from its own path.
// ═══════════════════════════════════════════════════════════════════════════════

/** A silent [CrashReporter] for the harness -- a pure JVM test tool, no real crash/log backend. */
private object NoOpCrashReporter : CrashReporter {
    override fun recordException(throwable: Throwable) = Unit
    override fun log(message: String) = Unit
    override fun setCustomKey(key: String, value: String) = Unit
}

object HarnessDoctorPipeline {

    private val deckScorer = DeckScorer(RoleClassifier(), NeutralPowerResolver)
    private val evaluateDeckUseCase = EvaluateDeckUseCase(deckScorer, ProgressionEventBus())
    private val suggestCutsUseCase = SuggestCutsUseCase(deckScorer)
    private val suggestAddsFromCollectionUseCase = SuggestAddsFromCollectionUseCase(deckScorer)
    private val inferDeckIdentityUseCase = InferDeckIdentityUseCase()
    private val deckAnalysisPipeline = DeckAnalysisPipeline(evaluateDeckUseCase, inferDeckIdentityUseCase, NoOpCrashReporter)

    data class DoctorResult(
        val health: DeckHealth,
        val cuts: List<CardFit>,
        val adds: List<AddSuggestion>,
    )

    suspend fun evaluate(
        mainboard: List<DeckEntry>,
        format: DeckFormat,
        commander: Card?,
        collection: List<Card>,
        archetypeOverride: String?,
        themesOverride: List<String>,
        tribeOverride: String? = null,
        // Deck Engine Unification (D4): mirrors DeckDoctorOrchestrator.cutProtectedIds EXACTLY --
        // strategyLocked=true (every wizard build persists this) + wizardSourcedIds (every wizard
        // build persists source=WIZARD on every placed card) means `cuts` structurally excludes the
        // wizard's own placements, the same hard guarantee production gets. Defaults to
        // false/emptySet so a non-wizard caller of this pipeline is unaffected.
        strategyLocked: Boolean = false,
        wizardSourcedIds: Set<String> = emptySet(),
    ): DoctorResult {
        // Deck Wizard Commander v3 plan (E4, D2): seed inference + pin fold + evaluate now live in
        // the ONE shared DeckAnalysisPipeline (also used by DeckDoctorOrchestrator and, later, the
        // wizard) -- see that class's KDoc. emitProgression=false: this is a pure measurement tool,
        // not a real user session, so it must never advance the Deck Doctor exploration quest.
        val health = deckAnalysisPipeline.analyze(
            mainboard = mainboard,
            format = format,
            commander = commander,
            archetypeOverride = archetypeOverride,
            themesOverride = themesOverride,
            tribeOverride = tribeOverride,
            emitProgression = false,
        )
        val protectedIds = setOfNotNull(commander?.scryfallId) + (if (strategyLocked) wizardSourcedIds else emptySet())
        val resolvedSkeleton = resolveArchetypeSkeleton(health)
        val cuts = suggestCutsUseCase(
            mainboard = mainboard,
            profile = health.profile,
            protectedIds = protectedIds,
            resolvedSkeleton = resolvedSkeleton,
        )
        val adds = runCatching {
            suggestAddsFromCollectionUseCase(
                collection = collection,
                mainboard = mainboard,
                profile = health.profile,
                resolvedSkeleton = resolvedSkeleton,
            )
        }.getOrDefault(emptyList())

        return DoctorResult(health = health, cuts = cuts, adds = adds)
    }

    /**
     * Deck Wizard & Engine Rework plan Workstream 8.1 (round-trip alignment invariant): re-ranks
     * cuts against an ARBITRARY [protectedIds] set over the SAME [profile] a prior [evaluate] call
     * already produced -- lets the harness compare the LOCKED cuts ranking (commander + every
     * wizard-placed card protected) against the UNLOCKED ranking (commander only) without paying
     * for a second full evaluate (Motor A/B + ArchetypeEvaluator would rerun for no reason, since
     * lock state never changes the profile/health themselves, only which ids [SuggestCutsUseCase]
     * excludes). Reuses the SAME [suggestCutsUseCase] instance [evaluate] uses.
     */
    suspend fun cutsWithProtection(
        mainboard: List<DeckEntry>,
        profile: DeckProfile,
        protectedIds: Set<String>,
        resolvedSkeleton: ResolvedArchetypeSkeleton?,
    ): List<CardFit> = suggestCutsUseCase(
        mainboard = mainboard,
        profile = profile,
        protectedIds = protectedIds,
        resolvedSkeleton = resolvedSkeleton,
    )

    /** Mirrors DeckDoctorOrchestrator.resolveArchetypeSkeleton EXACTLY. Public (not private) so
     * [HarnessMatrixRunner]'s WS8.1 round-trip UNLOCKED re-rank can resolve the SAME skeleton
     * [evaluate] used, keeping the two cut rankings comparable under the identical WS8.3 layers. */
    fun resolveArchetypeSkeleton(health: DeckHealth): ResolvedArchetypeSkeleton? {
        val archetypeFormat = ArchetypeFormat.of(health.profile.format) ?: return null
        val resolution = health.archetypeResolution
        if (resolution.macro == null && resolution.themes.isEmpty()) return null
        return ArchetypeSkeletonResolver.resolveWithColor(
            format = archetypeFormat,
            archetype = resolution.macro,
            themes = resolution.themes,
            identity = health.profile.colorIdentity,
        )
    }
}
