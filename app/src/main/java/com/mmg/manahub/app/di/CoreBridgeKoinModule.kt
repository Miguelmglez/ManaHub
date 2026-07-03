package com.mmg.manahub.app.di

import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.common.DispatcherProvider
import com.mmg.manahub.core.common.provideCrashReporter
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.data.remote.ScryfallRemoteDataSource
import com.mmg.manahub.core.domain.auth.AuthRepository
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.DeckRepository
import com.mmg.manahub.core.domain.repository.StatsRepository
import com.mmg.manahub.core.domain.repository.UserPreferencesRepository
import com.mmg.manahub.core.gamification.domain.ProgressionEventBus
import com.mmg.manahub.core.gamification.domain.repository.GamificationRepository
import com.mmg.manahub.core.util.AnalyticsHelper
import com.mmg.manahub.core.domain.repository.DraftRepository
import com.mmg.manahub.core.domain.repository.DraftSimRepository
import com.mmg.manahub.core.domain.repository.FriendRepository
import com.mmg.manahub.feature.game.domain.repository.GameSessionRepository
import com.mmg.manahub.feature.tournament.data.repository.TournamentRepositoryImpl
import com.mmg.manahub.feature.tournament.domain.repository.TournamentRepository
import com.mmg.manahub.core.domain.repository.OpenForTradeRepository
import com.mmg.manahub.core.data.repository.TradesRepository
import com.mmg.manahub.core.domain.repository.WishlistRepository
import kotlinx.coroutines.Dispatchers
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
 * @param tradesRepository the Hilt-owned [TradesRepository] singleton (Trades + Friends + the still-Hilt
 *   `HomeViewModel`/`FriendDetailViewModel`). Promoted here from the Friends island when the Trades island
 *   also began consuming it; the Hilt `TradesModule` is KEPT (its other bindings serve Hilt features).
 * @param wishlistRepository the Hilt-owned [WishlistRepository] singleton (Trades + Home + CardDetail +
 *   the still-Hilt Collection/DeckStudio/DeckImprovement). Promoted here from the Home island when the
 *   Trades island also began consuming it.
 * @param openForTradeRepository the Hilt-owned [OpenForTradeRepository] singleton (Trades + CardDetail +
 *   the still-Hilt Collection). Promoted here from the CardDetail island when the Trades island also
 *   began consuming it.
 * @param progressionEventBus the Hilt-owned [ProgressionEventBus] singleton (gamification event bus).
 *   Bridged here (from the ALREADY-existing `ManaHubApp` field used by the gamification engine) so that
 *   natively-Koin-constructed repositories — starting with [TournamentRepository] — emit onto the SAME
 *   instance the Hilt-owned `GamificationEngine` collects. Constructing a second `ProgressionEventBus` in
 *   Koin would silently orphan its events (nothing would ever collect them).
 * @return a Koin [Module] exposing the cross-island bridged singletons (sixteen in total, plus the
 *   natively-Koin-constructed [TournamentRepository]).
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
    tradesRepository: TradesRepository,
    wishlistRepository: WishlistRepository,
    openForTradeRepository: OpenForTradeRepository,
    progressionEventBus: ProgressionEventBus,
): Module = module {
    // ── KMP platform abstractions (not Hilt-owned — instantiated directly). ──
    single<CrashReporter> { provideCrashReporter() }
    single { DispatcherProvider() }

    // Shared across the Settings + Stats + Profile + Home + CommunityDecks + CardDetail + Friends +
    // Draft + Tournament + Trades islands — each registered exactly once.
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
    single { tradesRepository }
    single { wishlistRepository }
    single { openForTradeRepository }
    single { progressionEventBus }

    // ── TournamentRepository: natively Koin-constructed (Hilt `TournamentModule` deleted). ──
    // Shared by the Home + Game + Tournament islands (all Koin now) — registered exactly once here.
    // `TournamentDao` and `GenerateNextRoundUseCase` are registered as singles in `tournamentKoinModule`
    // (Tournament-only); Koin resolves `get()` across modules regardless of declaration site.
    single<TournamentRepository> {
        TournamentRepositoryImpl(
            dao = get(),
            progressionEventBus = get(),
            generateNextRound = get(),
            ioDispatcher = Dispatchers.IO,
        )
    }
}
