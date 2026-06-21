package com.mmg.manahub.feature.game.di

import com.mmg.manahub.core.gamification.domain.GamificationEngine
import com.mmg.manahub.core.nearby.domain.repository.NearbySessionRepository
import com.mmg.manahub.core.online.domain.usecase.AdvancePhaseUseCase
import com.mmg.manahub.core.online.domain.usecase.ConfirmDefeatUseCase
import com.mmg.manahub.core.online.domain.usecase.LeaveSessionUseCase
import com.mmg.manahub.core.online.domain.usecase.NextTurnUseCase
import com.mmg.manahub.core.online.domain.usecase.ObserveSessionUseCase
import com.mmg.manahub.core.online.domain.usecase.RevokeDefeatUseCase
import com.mmg.manahub.core.online.domain.usecase.ToggleLandPlayedUseCase
import com.mmg.manahub.core.online.domain.usecase.UpdateCommanderDamageUseCase
import com.mmg.manahub.core.online.domain.usecase.UpdateCounterUseCase
import com.mmg.manahub.core.online.domain.usecase.UpdateLifeUseCase
import com.mmg.manahub.core.voice.domain.VoiceCommandRecognizer
import com.mmg.manahub.feature.game.domain.usecase.EvaluatePlayerEliminationUseCase
import com.mmg.manahub.feature.game.presentation.GameResultStripViewModel
import com.mmg.manahub.feature.game.presentation.GameSetupViewModel
import com.mmg.manahub.feature.game.presentation.GameViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * KMP migration — Phase 1 Hilt→Koin cutover. The Game feature is the twentieth (and LAST non-excluded)
 * "Koin island": all three game ViewModels ([GameViewModel], [GameSetupViewModel],
 * [GameResultStripViewModel]) are resolved by Koin (`koinViewModel()`). This completes the per-feature
 * cutover for everything except the three explicitly DEFERRED platform-heavy features (online sessions,
 * voice control, camera scanner), which stay on Hilt for now.
 *
 * ## Bridge pattern (same as the earlier islands)
 * `ManaHubApp` is the bridge: it `@Inject`s the still-Hilt-owned singletons and passes the Game-only ones
 * into [gameKoinModule], which re-exposes them to Koin as `single { }`.
 *
 * ## The voice/online coupling — migrate Game WITHOUT migrating voice/online (the hard part)
 * [GameViewModel] integrates the DEFERRED `core/voice` and `core/online` (+ `core/nearby`) features.
 * Those features are EXCLUDED from the KMP migration for now and MUST stay Hilt-owned. So this island
 * migrates ONLY the game ViewModels, while the voice/online/nearby singletons they depend on remain
 * constructed by Hilt and are BRIDGED into this Koin module (exactly like the Tournament use-case bridge):
 *  - [VoiceCommandRecognizer] (core/voice) — KEEP Hilt; bridged.
 *  - The eleven online session use cases + [NearbySessionRepository] (core/online + core/nearby) — KEEP
 *    Hilt; bridged. (Their own DI graphs, broadcast-first sync, and the still-Hilt `feature/online` lobby
 *    VMs are untouched.)
 * This guarantees ONE shared instance of each across both DI graphs; nothing under `core/voice`,
 * `feature/online`, `core/nearby`, or `feature/scanner` is de-Hilt'd.
 *
 * ## What is NOT registered here (resolved via get() from already-loaded modules)
 * - [com.mmg.manahub.feature.game.domain.repository.GameSessionRepository],
 *   [com.mmg.manahub.feature.tournament.domain.repository.TournamentRepository],
 *   [com.mmg.manahub.core.util.AnalyticsHelper] and
 *   [com.mmg.manahub.core.data.local.UserPreferencesDataStore] are bridged in `coreBridgeKoinModule`.
 * - [com.mmg.manahub.feature.tournament.domain.usecase.RecordMatchResultUseCase] (the SINGLE
 *   finish-and-advance write path) is already a `single` in `tournamentKoinModule` — resolved via `get()`,
 *   NOT re-registered (a second `single<T>` would throw `DefinitionOverrideException`). The game-played
 *   tournament-result flow keeps routing through that identical instance.
 * - [com.mmg.manahub.core.voice.domain.VoiceModelRepository] is already a `single` in `settingsKoinModule`
 *   — resolved via `get()` for [GameSetupViewModel], NOT re-registered.
 *
 * ## What IS bridged here (Game-only / deferred-feature, from the Hilt graph)
 * - [GamificationEngine] (core/gamification) — Hilt-owned, not previously a Koin `single`; bridged for
 *   [GameResultStripViewModel].
 * - [EvaluatePlayerEliminationUseCase] (game) — game-only; bridged.
 * - The voice/online/nearby singletons listed above.
 *
 * The [GameViewModel] factory resolves a Koin-injected `SavedStateHandle` (`savedStateHandle = get()`),
 * which carries the `mode`/`playerCount` nav args from the back-stack entry's `CreationExtras` exactly as
 * Hilt did. At the call-site [GameViewModel] is Activity-scoped (game state must persist across all
 * navigation): `koinViewModel(viewModelStoreOwner = activity)` — the exact equivalent of the old
 * `hiltViewModel(activity)`.
 *
 * The feature-private Hilt `GameModule` (`@Binds GameSessionRepository`) is KEPT (NOT converted/deleted):
 * the repo is bridged in `coreBridgeKoinModule` and is also consumed by the still-Hilt online/nearby code,
 * so the binding must stay alive.
 *
 * @param observeSession the Hilt-owned [ObserveSessionUseCase] (core/online — deferred, KEEP Hilt).
 * @param updateLife the Hilt-owned [UpdateLifeUseCase] (core/online — deferred, KEEP Hilt).
 * @param advancePhase the Hilt-owned [AdvancePhaseUseCase] (core/online — deferred, KEEP Hilt).
 * @param nextTurn the Hilt-owned [NextTurnUseCase] (core/online — deferred, KEEP Hilt).
 * @param updateCounter the Hilt-owned [UpdateCounterUseCase] (core/online — deferred, KEEP Hilt).
 * @param updateCommanderDamage the Hilt-owned [UpdateCommanderDamageUseCase] (core/online — deferred).
 * @param confirmDefeat the Hilt-owned [ConfirmDefeatUseCase] (core/online — deferred, KEEP Hilt).
 * @param revokeDefeat the Hilt-owned [RevokeDefeatUseCase] (core/online — deferred, KEEP Hilt).
 * @param leaveSession the Hilt-owned [LeaveSessionUseCase] (core/online — deferred, KEEP Hilt).
 * @param toggleLandPlayed the Hilt-owned [ToggleLandPlayedUseCase] (core/online — deferred, KEEP Hilt).
 * @param nearbyRepository the Hilt-owned [NearbySessionRepository] (core/nearby — deferred, KEEP Hilt).
 * @param voiceCommandRecognizer the Hilt-owned [VoiceCommandRecognizer] (core/voice — deferred, KEEP Hilt).
 * @param evaluatePlayerElimination the Hilt-owned [EvaluatePlayerEliminationUseCase] (game only).
 * @param gamificationEngine the Hilt-owned [GamificationEngine] singleton (game only consumer in Koin).
 * @return a Koin [Module] providing the Game-only bridged singletons and the three ViewModel factories.
 */
fun gameKoinModule(
    observeSession: ObserveSessionUseCase,
    updateLife: UpdateLifeUseCase,
    advancePhase: AdvancePhaseUseCase,
    nextTurn: NextTurnUseCase,
    updateCounter: UpdateCounterUseCase,
    updateCommanderDamage: UpdateCommanderDamageUseCase,
    confirmDefeat: ConfirmDefeatUseCase,
    revokeDefeat: RevokeDefeatUseCase,
    leaveSession: LeaveSessionUseCase,
    toggleLandPlayed: ToggleLandPlayedUseCase,
    nearbyRepository: NearbySessionRepository,
    voiceCommandRecognizer: VoiceCommandRecognizer,
    evaluatePlayerElimination: EvaluatePlayerEliminationUseCase,
    gamificationEngine: GamificationEngine,
): Module = module {
    // ── Hilt → Koin bridge: re-expose the Game-only + deferred (voice/online/nearby) Hilt-owned
    //    singletons to Koin. The deferred features themselves stay 100% Hilt. ──
    single { observeSession }
    single { updateLife }
    single { advancePhase }
    single { nextTurn }
    single { updateCounter }
    single { updateCommanderDamage }
    single { confirmDefeat }
    single { revokeDefeat }
    single { leaveSession }
    single { toggleLandPlayed }
    single { nearbyRepository }
    single { voiceCommandRecognizer }
    single { evaluatePlayerElimination }
    single { gamificationEngine }

    // ── The Koin island: the three game ViewModels are now resolved by Koin, not Hilt. ──
    viewModel {
        GameViewModel(
            savedStateHandle = get(),
            gameSessionRepo = get(),                 // coreBridge
            tournamentRepo = get(),                  // coreBridge
            recordMatchResultUseCase = get(),        // tournamentKoinModule single
            analyticsHelper = get(),                 // coreBridge
            observeSessionUseCase = get(),
            updateLifeUseCase = get(),
            advancePhaseUseCase = get(),
            nextTurnUseCase = get(),
            updateCounterUseCase = get(),
            updateCommanderDamageUseCase = get(),
            confirmDefeatUseCase = get(),
            revokeDefeatUseCase = get(),
            leaveSessionUseCase = get(),
            nearbyRepo = get(),
            toggleLandPlayedUseCase = get(),
            voiceCommandRecognizer = get(),
            evaluatePlayerEliminationUseCase = get(),
            appContext = androidContext(),
        )
    }
    viewModel {
        GameSetupViewModel(
            userPreferencesDataStore = get(),        // coreBridge
            voiceModelRepository = get(),            // settingsKoinModule single
            appContext = androidContext(),
        )
    }
    viewModel {
        GameResultStripViewModel(
            engine = get(),
            userPreferencesDataStore = get(),        // coreBridge
        )
    }
}
