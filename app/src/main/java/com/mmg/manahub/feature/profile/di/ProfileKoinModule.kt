package com.mmg.manahub.feature.profile.di

import com.mmg.manahub.core.data.local.dao.SurveyAnswerDao
import com.mmg.manahub.core.domain.repository.StatsRepository
import com.mmg.manahub.core.gamification.domain.repository.GamificationRepository
import com.mmg.manahub.core.gamification.domain.usecase.ClaimQuestRewardUseCase
import com.mmg.manahub.feature.profile.presentation.ProfileEditViewModel
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
 * [ProfileViewModel] depends on singletons still owned by the Hilt object graph. Rather than
 * re-providing them in Koin — which would risk duplicate construction / divergent state — `ManaHubApp`
 * is the bridge: it `@Inject`s the already-constructed Hilt instances and passes them into
 * [profileKoinModule], which re-exposes the Profile-only ones to Koin as `single { }`.
 *
 * Six of the eight dependencies are SHARED with other islands and are therefore NOT registered here —
 * they are bridged exactly once in `coreBridgeKoinModule` (registering the same type in two loaded
 * modules would throw `DefinitionOverrideException`), and this module resolves them via `get()`:
 * - [GameSessionRepository] — shared with the Stats + Home islands.
 * - [UserPreferencesDataStore] — shared with the Settings + Home islands.
 * - [AuthRepository] — shared with the Settings + Home islands.
 * - [StatsRepository] — shared with the Home island.
 * - [GamificationRepository] — shared with the Home island.
 * - `FriendRepository` — shared with the Friends island.
 *
 * [SurveyAnswerDao] is a Profile-only singleton bridged here.
 *
 * ## KMP migration — Hilt→Koin cutover batch 4
 * [ClaimQuestRewardUseCase] is no longer bridged from `ManaHubApp` — it is now a NATIVE Koin single in
 * `com.mmg.manahub.core.gamification.di.gamificationEngineKoinModule` (shared with `QuestReconciler`, the
 * whole Hilt `core.gamification.di.GamificationModule` was deleted). [ProfileViewModel] still resolves it
 * via `get()`, unchanged.
 *
 * ## [ProfileEditViewModel] (remediation slice P1.3)
 * [ProfileEditViewModel] backs `ProfileEditSheet` and is added to THIS module (rather than a new
 * standalone one) because Profile is already its natural feature owner. All three of its constructor
 * deps — [com.mmg.manahub.core.data.remote.ScryfallRemoteDataSource],
 * [com.mmg.manahub.core.data.local.UserPreferencesDataStore] and
 * [com.mmg.manahub.core.domain.auth.AuthRepository] — are already bridged in `coreBridgeKoinModule`
 * (shared with other islands), so no new `ManaHubApp` bridging or module constructor param is needed.
 *
 * ## Cross-island consumer: `ShareInviteUseCase` (Account/Share-profile slice)
 * [ProfileViewModel] now resolves `ShareInviteUseCase` (a Friends-domain use case registered in
 * `friendsKoinModule`, not here) via `get()` to back `ProfileViewModel.fetchShareLink()`, which feeds
 * the "Share my profile" CTA on `AccountSection`/`ShareProfileSheet` hosted by `ProfileScreen`. No new
 * registration is added here — `friendsKoinModule` and this module load together in the same
 * `ManaHubApp` `modules(...)` call, so the definition is visible cross-module.
 *
 * @return a Koin [Module] that provides the Profile-only bridged singletons and the [ProfileViewModel]
 *   / [ProfileEditViewModel] factories.
 */
fun profileKoinModule(
    surveyAnswerDao: SurveyAnswerDao,
): Module = module {
    // ── Hilt → Koin bridge: re-expose the Profile-only Hilt-owned singletons to Koin. ──
    // (gameSessionRepo, userPreferencesDataStore, authRepository, statsRepository, gamificationRepository
    //  and friendRepository are shared → bridged in coreBridgeKoinModule, not here, to avoid
    //  DefinitionOverrideException. claimQuestRewardUseCase is now a native single in
    //  gamificationEngineKoinModule — resolved via get() below, not re-registered.)
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
            claimQuestRewardUseCase = get(),
            // ShareInviteUseCase is registered in friendsKoinModule (Friends-only use case, only
            // depends on the bridged FriendRepository) — resolved here via get() since both
            // modules load together in ManaHubApp's single modules(...) call.
            shareInviteUseCase = get(),
        )
    }

    // ── ProfileEditViewModel (P1.3): all deps already bridged in coreBridgeKoinModule. ──
    viewModel {
        ProfileEditViewModel(
            scryfallRemoteDataSource = get(),
            userPreferencesDataStore = get(),
            authRepository = get(),
        )
    }
}
