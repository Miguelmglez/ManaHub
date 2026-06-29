package com.mmg.manahub.feature.decks.di

import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.DeckRepository
import com.mmg.manahub.core.gamification.domain.ProgressionEventBus
import com.mmg.manahub.feature.decks.domain.engine.DeckMagicEngine
import com.mmg.manahub.feature.decks.domain.engine.DeckScorer
import com.mmg.manahub.feature.decks.domain.engine.EdhrecPowerResolver
import com.mmg.manahub.feature.decks.domain.engine.ManaBaseAnalyzer
import com.mmg.manahub.feature.decks.domain.engine.PowerResolver
import com.mmg.manahub.feature.decks.domain.engine.RoleClassifier
import com.mmg.manahub.feature.decks.domain.usecase.BudgetOptimizer
import com.mmg.manahub.feature.decks.domain.usecase.BuildDeckFromSeedsUseCase
import com.mmg.manahub.feature.decks.domain.usecase.CandidatePoolGenerator
import com.mmg.manahub.feature.decks.domain.usecase.EvaluateDeckUseCase
import com.mmg.manahub.feature.decks.domain.usecase.ImportDeckUseCase
import com.mmg.manahub.feature.decks.domain.usecase.InferDeckIdentityUseCase
import com.mmg.manahub.feature.decks.domain.usecase.SuggestAddsUseCase
import com.mmg.manahub.feature.decks.domain.usecase.SuggestAddsWithBudgetUseCase
import com.mmg.manahub.feature.decks.domain.usecase.SuggestCutsUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

/**
 * Hilt bindings for the Deck Doctor scoring engine and use cases.
 *
 * After the KMP migration moved `RoleClassifier`, `ManaBaseAnalyzer`, `DeckScorer`, and all deck
 * use cases to `:shared:core-domain` `commonMain` (stripping `@Inject`/`@Singleton`), Hilt can no
 * longer auto-discover them. This module provides them explicitly so all still-Hilt consumers
 * (e.g. `ScoringDraftDeckBuilder`, `DeckImprovementViewModel`, and the Koin bridge in
 * [DecksKoinModule]) keep working without any behaviour change.
 *
 * Now that `edhrec_rank` is persisted on `Card`, the recommended [EdhrecPowerResolver]
 * replaces the temporary `NeutralPowerResolver`: it derives the power signal from each
 * card's EDHREC rank on a logarithmic scale.
 *
 * All use cases that previously used `@IoDispatcher` now default to [Dispatchers.Default]
 * (pure shared code), passed explicitly here so the Hilt graph remains authoritative.
 */
@Module
@InstallIn(SingletonComponent::class)
object DeckDoctorModule {

    // ── Scoring engine ────────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun providePowerResolver(): PowerResolver =
        EdhrecPowerResolver(rankOf = { it.edhrecRank })

    @Provides
    @Singleton
    fun provideRoleClassifier(): RoleClassifier = RoleClassifier()

    @Provides
    @Singleton
    fun provideManaBaseAnalyzer(): ManaBaseAnalyzer = ManaBaseAnalyzer()

    @Provides
    @Singleton
    fun provideDeckScorer(
        roleClassifier: RoleClassifier,
        power: PowerResolver,
        manaBaseAnalyzer: ManaBaseAnalyzer,
    ): DeckScorer = DeckScorer(roleClassifier, power, manaBaseAnalyzer)

    @Provides
    @Singleton
    fun provideDeckMagicEngine(deckScorer: DeckScorer): DeckMagicEngine =
        DeckMagicEngine(deckScorer)

    // ── Candidate pool helpers ────────────────────────────────────────────────

    /** No-arg; safe to construct inline — no singletons leaked. */
    @Provides
    @Singleton
    fun provideBudgetOptimizer(): BudgetOptimizer = BudgetOptimizer()

    @Provides
    @Singleton
    fun provideCandidatePoolGenerator(
        cardRepository: CardRepository,
    ): CandidatePoolGenerator = CandidatePoolGenerator(cardRepository, Dispatchers.Default)

    // ── Deck use cases ────────────────────────────────────────────────────────

    /** No-arg; pure, stateless identity inference — no DI graph deps. */
    @Provides
    @Singleton
    fun provideInferDeckIdentityUseCase(): InferDeckIdentityUseCase = InferDeckIdentityUseCase()

    @Provides
    @Singleton
    fun provideEvaluateDeckUseCase(
        deckScorer: DeckScorer,
        progressionEventBus: ProgressionEventBus,
    ): EvaluateDeckUseCase =
        EvaluateDeckUseCase(deckScorer, progressionEventBus, Dispatchers.Default)

    @Provides
    @Singleton
    fun provideSuggestAddsUseCase(deckScorer: DeckScorer): SuggestAddsUseCase =
        SuggestAddsUseCase(deckScorer, Dispatchers.Default)

    @Provides
    @Singleton
    fun provideSuggestCutsUseCase(deckScorer: DeckScorer): SuggestCutsUseCase =
        SuggestCutsUseCase(deckScorer, Dispatchers.Default)

    @Provides
    @Singleton
    fun provideSuggestAddsWithBudgetUseCase(
        deckScorer: DeckScorer,
        candidatePoolGenerator: CandidatePoolGenerator,
        budgetOptimizer: BudgetOptimizer,
        cardRepository: CardRepository,
    ): SuggestAddsWithBudgetUseCase =
        SuggestAddsWithBudgetUseCase(
            deckScorer,
            candidatePoolGenerator,
            budgetOptimizer,
            cardRepository,
            Dispatchers.Default,
        )

    @Provides
    @Singleton
    fun provideBuildDeckFromSeedsUseCase(
        deckScorer: DeckScorer,
        roleClassifier: RoleClassifier,
        candidatePoolGenerator: CandidatePoolGenerator,
        budgetOptimizer: BudgetOptimizer,
    ): BuildDeckFromSeedsUseCase =
        BuildDeckFromSeedsUseCase(
            deckScorer,
            roleClassifier,
            candidatePoolGenerator,
            budgetOptimizer,
            Dispatchers.Default,
        )

    @Provides
    @Singleton
    fun provideImportDeckUseCase(
        cardRepository: CardRepository,
        deckRepository: DeckRepository,
    ): ImportDeckUseCase = ImportDeckUseCase(cardRepository, deckRepository)
}
