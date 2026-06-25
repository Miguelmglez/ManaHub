package com.mmg.manahub.feature.decks.di

import com.mmg.manahub.feature.decks.domain.engine.DeckScorer
import com.mmg.manahub.feature.decks.domain.engine.EdhrecPowerResolver
import com.mmg.manahub.feature.decks.domain.engine.ManaBaseAnalyzer
import com.mmg.manahub.feature.decks.domain.engine.PowerResolver
import com.mmg.manahub.feature.decks.domain.engine.RoleClassifier
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt bindings for the Deck Doctor scoring engine.
 *
 * After the KMP migration moved `RoleClassifier`, `ManaBaseAnalyzer` and `DeckScorer` to
 * `:shared:core-domain` `commonMain` (stripping `@Inject`/`@Singleton`), Hilt can no longer
 * auto-discover them. This module provides them explicitly so all still-Hilt consumers
 * (e.g. `ScoringDraftDeckBuilder`, `DeckImprovementViewModel`) keep working.
 *
 * Now that `edhrec_rank` is persisted on `Card`, the recommended [EdhrecPowerResolver]
 * replaces the temporary `NeutralPowerResolver`: it derives the power signal from each
 * card's EDHREC rank on a logarithmic scale.
 */
@Module
@InstallIn(SingletonComponent::class)
object DeckDoctorModule {

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
}
