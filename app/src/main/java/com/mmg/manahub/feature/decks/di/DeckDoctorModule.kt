package com.mmg.manahub.feature.decks.di

import com.mmg.manahub.feature.decks.domain.engine.EdhrecPowerResolver
import com.mmg.manahub.feature.decks.domain.engine.PowerResolver
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt bindings for the Deck Doctor scoring engine.
 *
 * `DeckScorer` declares `power: PowerResolver = NeutralPowerResolver` as a Kotlin
 * default, but Hilt does NOT honor constructor defaults — without an explicit binding
 * any injection of `DeckScorer` fails the build. This module supplies the production
 * [PowerResolver].
 *
 * `RoleClassifier` and `DeckScorer` are `@Inject constructor`, so they need no explicit
 * `@Provides`.
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
}
