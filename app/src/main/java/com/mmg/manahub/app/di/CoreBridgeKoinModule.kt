package com.mmg.manahub.app.di

import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.data.remote.ScryfallRemoteDataSource
import com.mmg.manahub.core.domain.auth.AuthRepository
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.DeckRepository
import com.mmg.manahub.core.domain.repository.StatsRepository
import com.mmg.manahub.core.domain.repository.UserPreferencesRepository
import com.mmg.manahub.core.gamification.domain.repository.GamificationRepository
import com.mmg.manahub.core.util.AnalyticsHelper
import com.mmg.manahub.feature.draft.domain.repository.DraftRepository
import com.mmg.manahub.feature.draft.domain.repository.DraftSimRepository
import com.mmg.manahub.feature.friends.domain.repository.FriendRepository
import com.mmg.manahub.feature.game.domain.repository.GameSessionRepository
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * KMP migration — Phase 1 Hilt→Koin cutover. Shared "Koin bridge" for Hilt-owned singletons that are
 * consumed by MORE THAN ONE Koin island (currently Settings, Stats, Profile, Home, CommunityDecks,
 * CardDetail, Friends and Draft).
 *
 * ## Why a shared module
 * Each Koin island re-exposes its Hilt-owned dependencies as `single { instance }` (the Spike-D bridge
 * pattern). When two islands depend on the SAME singleton, registering it in both feature modules would
 * load two `single<T>` definitions for the same type into one Koin container, which throws
 * `DefinitionOverrideException` at `startKoin`. Bridged singletons that are shared across islands
 * therefore live here, in ONE place, and each feature module just resolves them via `get()`.
 *
 * As features migrate further in Phase 1, a dependency's `single { hiltInstance }` here is replaced by a
 * real Koin provider and the matching Hilt `@Provides`/`@Binds` is deleted — without ever leaving the
 * app uncompilable between commits.
 *
 * @param userPreferencesRepo the Hilt-owned [UserPreferencesRepository] singleton (Settings + Stats).
 * @param userPrefsDataStore the Hilt-owned [UserPreferencesDataStore] singleton (Settings + Profile + Home).
 * @param authRepository the Hilt-owned [AuthRepository] singleton (Settings + Profile + Home).
 * @param gameSessionRepository the Hilt-owned [GameSessionRepository] singleton (Stats + Profile + Home).
 * @param statsRepository the Hilt-owned [StatsRepository] singleton (Profile + Home).
 * @param deckRepository the Hilt-owned [DeckRepository] singleton (Stats + Home).
 * @param scryfallRemoteDataSource the Hilt-owned [ScryfallRemoteDataSource] singleton (Stats + Home).
 * @param gamificationRepository the Hilt-owned [GamificationRepository] singleton (Profile + Home).
 * @param cardRepository the Hilt-owned [CardRepository] singleton (Home + CommunityDecks + CardDetail).
 * @param analyticsHelper the Hilt-owned [AnalyticsHelper] singleton (Settings + CardDetail). Promoted
 *   here from the Settings island when CardDetail also began consuming it.
 * @param friendRepository the Hilt-owned [FriendRepository] singleton (Profile + Friends). Promoted
 *   here from the Profile island when the Friends island also began consuming it.
 * @param draftRepository the Hilt-owned [DraftRepository] singleton (Home + Draft). Promoted here from
 *   the Home island when the Draft island also began consuming it.
 * @param draftSimRepository the Hilt-owned [DraftSimRepository] singleton (Home + Draft). Promoted here
 *   from the Home island when the Draft island also began consuming it.
 * @return a Koin [Module] exposing the cross-island bridged singletons (thirteen in total).
 */
fun coreBridgeKoinModule(
    userPreferencesRepo: UserPreferencesRepository,
    userPrefsDataStore: UserPreferencesDataStore,
    authRepository: AuthRepository,
    gameSessionRepository: GameSessionRepository,
    statsRepository: StatsRepository,
    deckRepository: DeckRepository,
    scryfallRemoteDataSource: ScryfallRemoteDataSource,
    gamificationRepository: GamificationRepository,
    cardRepository: CardRepository,
    analyticsHelper: AnalyticsHelper,
    friendRepository: FriendRepository,
    draftRepository: DraftRepository,
    draftSimRepository: DraftSimRepository,
): Module = module {
    // Shared across the Settings + Stats + Profile + Home + CommunityDecks + CardDetail + Friends +
    // Draft islands — each registered exactly once.
    single { userPreferencesRepo }
    single { userPrefsDataStore }
    single { authRepository }
    single { gameSessionRepository }
    single { statsRepository }
    single { deckRepository }
    single { scryfallRemoteDataSource }
    single { gamificationRepository }
    single { cardRepository }
    single { analyticsHelper }
    single { friendRepository }
    single { draftRepository }
    single { draftSimRepository }
}
