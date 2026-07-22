package com.mmg.manahub.feature.decks.harness

import com.mmg.manahub.core.gamification.domain.ProgressionEventBus
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.core.model.TagCategory
import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeFormat
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeId
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeSkeletonResolver
import com.mmg.manahub.feature.decks.domain.engine.CardFit
import com.mmg.manahub.feature.decks.domain.engine.DeckEntry
import com.mmg.manahub.feature.decks.domain.engine.DeckIdentitySeedTags
import com.mmg.manahub.feature.decks.domain.engine.DeckScorer
import com.mmg.manahub.feature.decks.domain.engine.NeutralPowerResolver
import com.mmg.manahub.feature.decks.domain.engine.ResolvedArchetypeSkeleton
import com.mmg.manahub.feature.decks.domain.engine.RoleClassifier
import com.mmg.manahub.feature.decks.domain.engine.ThemeId
import com.mmg.manahub.feature.decks.domain.usecase.AddSuggestion
import com.mmg.manahub.feature.decks.domain.usecase.DeckHealth
import com.mmg.manahub.feature.decks.domain.usecase.EvaluateDeckUseCase
import com.mmg.manahub.feature.decks.domain.usecase.InferDeckIdentityUseCase
import com.mmg.manahub.feature.decks.domain.usecase.SuggestAddsFromCollectionUseCase
import com.mmg.manahub.feature.decks.domain.usecase.SuggestCutsUseCase

// ═══════════════════════════════════════════════════════════════════════════════
//  Wizard Quality Campaign -- Phase H harness. Drives the SAME Deck Doctor Studio-Suggestions-tab
//  pipeline (EvaluateDeckUseCase -> SuggestCutsUseCase / SuggestAddsFromCollectionUseCase, Motor A)
//  over a wizard-built deck, mirroring DeckDoctorOrchestrator.loadAnalysis /
//  recomputeAddsInternal's seed-inference and archetype-skeleton resolution EXACTLY (down to the
//  MAX_SEED_CARDS=8 cap and the IDENTITY_CATEGORIES set) -- located via
//  `graphify query "DeckDoctorOrchestrator DeckTemplateResolver"`. The orchestrator itself is not
//  reused directly: it is a stateful class wired to Room/Flow repositories the harness has no need
//  for; this object re-derives its two pure decision points (seed inference, skeleton resolution)
//  and calls the SAME three use cases the orchestrator calls.
// ═══════════════════════════════════════════════════════════════════════════════

object HarnessDoctorPipeline {

    private const val MAX_SEED_CARDS = 8
    private val IDENTITY_CATEGORIES = setOf(TagCategory.STRATEGY, TagCategory.ARCHETYPE, TagCategory.TRIBAL)

    private val deckScorer = DeckScorer(RoleClassifier(), NeutralPowerResolver)
    private val evaluateDeckUseCase = EvaluateDeckUseCase(deckScorer, ProgressionEventBus())
    private val suggestCutsUseCase = SuggestCutsUseCase(deckScorer)
    private val suggestAddsFromCollectionUseCase = SuggestAddsFromCollectionUseCase(deckScorer)
    private val inferDeckIdentityUseCase = InferDeckIdentityUseCase()

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
        val commanderIdentity = commander?.colorIdentity?.toSet().orEmpty()
        val commanderTags = commander?.let { it.tags + it.userTags }.orEmpty()
        val seedCards = inferenceSeeds(commander, mainboard)
        val inferredSeedTags = inferDeckIdentityUseCase(seedCards).seedTags
        // Wizard Quality Campaign Wave 4 (Task 1); tribeOverride added Deck Engine Unification (D2):
        // mirrors DeckDoctorOrchestrator.loadAnalysis's pin fold-in EXACTLY -- see
        // DeckDoctorOrchestrator.pinSeedTags's KDoc for why.
        val seedTags = (inferredSeedTags + pinSeedTags(archetypeOverride, themesOverride, tribeOverride)).distinct()

        val health = evaluateDeckUseCase(
            mainboard = mainboard,
            format = format,
            commanderIdentity = commanderIdentity,
            seedTags = seedTags,
            archetypeOverride = archetypeOverride,
            themesOverride = themesOverride,
            commanderTags = commanderTags,
        )
        val protectedIds = setOfNotNull(commander?.scryfallId) + (if (strategyLocked) wizardSourcedIds else emptySet())
        val cuts = suggestCutsUseCase(
            mainboard = mainboard,
            profile = health.profile,
            protectedIds = protectedIds,
        )
        val resolvedSkeleton = resolveArchetypeSkeleton(health)
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

    /** Mirrors DeckDoctorOrchestrator.inferenceSeeds EXACTLY. */
    private fun inferenceSeeds(commander: Card?, mainboard: List<DeckEntry>): List<Card> {
        val ranked = mainboard
            .map { it.card }
            .filter { it.scryfallId != commander?.scryfallId }
            .map { card -> card to identityTagCount(card) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(MAX_SEED_CARDS)
            .map { it.first }
        return (listOfNotNull(commander) + ranked).distinctBy { it.scryfallId }
    }

    private fun identityTagCount(card: Card): Int =
        (card.tags + card.userTags).count { it.category in IDENTITY_CATEGORIES }

    /** Mirrors DeckDoctorOrchestrator.pinSeedTags EXACTLY. */
    private fun pinSeedTags(archetypeOverride: String?, themesOverride: List<String>, tribeOverride: String? = null): List<CardTag> {
        val archetype = archetypeOverride?.let { name -> ArchetypeId.entries.firstOrNull { it.name == name } }
        val themes = themesOverride.mapNotNull { name -> ThemeId.entries.firstOrNull { it.name == name } }
        if (archetype == null && themes.isEmpty() && tribeOverride.isNullOrBlank()) return emptyList()
        return DeckIdentitySeedTags.forArchetype(archetype ?: ArchetypeId.GENERIC, themes, tribeOverride)
    }

    /** Mirrors DeckDoctorOrchestrator.resolveArchetypeSkeleton EXACTLY. */
    private fun resolveArchetypeSkeleton(health: DeckHealth): ResolvedArchetypeSkeleton? {
        val archetypeFormat = ArchetypeFormat.of(health.profile.format) ?: return null
        val resolution = health.archetypeResolution
        if (resolution.macro == ArchetypeId.GENERIC && resolution.themes.isEmpty()) return null
        return ArchetypeSkeletonResolver.resolveWithColor(
            format = archetypeFormat,
            archetype = resolution.macro,
            themes = resolution.themes,
            colorCount = health.profile.colorIdentity.size,
        )
    }
}
