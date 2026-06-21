package com.mmg.manahub.feature.profile.di

import com.mmg.manahub.core.data.local.dao.SurveyAnswerDao
import com.mmg.manahub.core.domain.repository.StatsRepository
import com.mmg.manahub.core.gamification.domain.repository.GamificationRepository
import com.mmg.manahub.feature.profile.presentation.ProfileViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * KMP migration — Phase 1 Hilt→Koin cutover. The Profile feature is the third "Koin island" (after
 * Settings and Stats): [ProfileViewModel] is resolved by Koin (`koinViewModel()`) while every other
 * feature stays on Hilt. This continues the incremental, per-feature cutover proven by Spike D.
 *
 * ## Bridge pattern (same as Settings + Stats)
 * [ProfileViewModel] depends on seven singletons still owned by the Hilt object graph. Rather than
 * re-providing them in Koin — which would risk duplicate construction / divergent state — `ManaHubApp`
 * is the bridge: it `@Inject`s the already-constructed Hilt instances and passes them into
 * [profileKoinModule], which re-exposes the Profile-only ones to Koin as `single { }`.
 *
 * Six of the seven dependencies are SHARED with other islands and are therefore NOT registered here —
 * they are bridged exactly once in `coreBridgeKoinModule` (registering the same type in two loaded
 * modules would throw `DefinitionOverrideException`), and this module resolves them via `get()`:
 * - [GameSessionRepository] — shared with the Stats + Home islands.
 * - [UserPreferencesDataStore] — shared with the Settings + Home islands.
 * - [AuthRepository] — shared with the Settings + Home islands.
 * - [StatsRepository] — shared with the Home island.
 * - [GamificationRepository] — shared with the Home island.
 * - `FriendRepository` — shared with the Friends island. PROMOTED into `coreBridgeKoinModule` (and
 *   this param/`single` removed) when the Friends island also began consuming it.
 *
 * As features migrate, each `single { hiltInstance }` here is replaced by a real Koin provider and the
 * matching Hilt `@Provides`/`@Binds` is deleted — so the bridge shrinks to nothing without ever leaving
 * the app uncompilable between commits.
 *
 * @return a Koin [Module] that provides the Profile-only bridged singletons and the [ProfileViewModel] factory.
 */
fun profileKoinModule(
    surveyAnswerDao: SurveyAnswerDao,
): Module = module {
    // ── Hilt → Koin bridge: re-expose the Profile-only Hilt-owned singletons to Koin. ──
    // (gameSessionRepo, userPreferencesDataStore, authRepository, statsRepository, gamificationRepository
    //  and friendRepository are shared → bridged in coreBridgeKoinModule, not here, to avoid
    //  DefinitionOverrideException.)
    single { surveyAnswerDao }

    // ── The Koin island: ProfileViewModel is now resolved by Koin, not Hilt. ──
    viewModel {
        ProfileViewModel(
            statsRepo = get(),
            gameSessionRepo = get(),
            surveyAnswerDao = get(),
            userPreferencesDataStore = get(),
            friendRepository = get(),
            authRepository = get(),
            gamificationRepository = get(),
        )
    }
}
