package com.mmg.manahub.feature.settings.di

import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.domain.auth.AuthRepository
import com.mmg.manahub.core.domain.repository.NotificationPrefsRepository
import com.mmg.manahub.core.domain.repository.PushTokenRepository
import com.mmg.manahub.core.domain.repository.UserPreferencesRepository
import com.mmg.manahub.core.voice.domain.VoiceModelRepository
import com.mmg.manahub.feature.settings.presentation.SettingsViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * KMP migration — Phase 0 Spike D. The first "Koin island": [SettingsViewModel] is resolved by Koin
 * (`koinViewModel()`) while every other feature stays on Hilt. This proves Hilt and Koin can run
 * side-by-side in `:app`, so the Hilt→Koin cutover (Phase 1) can be done incrementally per feature
 * instead of as a single big-bang PR.
 *
 * ## Bridge pattern (the key Spike-D deliverable)
 * [SettingsViewModel] depends on eight singletons that are still owned by the Hilt object graph
 * (repositories / data sources / helpers). Rather than re-providing them in Koin — which would
 * duplicate construction and risk two divergent singleton instances (e.g. two `UserPreferencesDataStore`
 * pointing at the same file) — this module is built by [settingsKoinModule], which receives the
 * already-constructed Hilt instances and re-exposes them to Koin as `single { }`. `ManaHubApp` is the
 * bridge: it `@Inject`s the eight Hilt singletons (they are part of its Hilt graph) and passes them
 * into [settingsKoinModule] when it calls `startKoin`.
 *
 * As features migrate in Phase 1, each dependency's `single { }` here is replaced by a real Koin
 * provider, and the corresponding Hilt `@Provides`/`@Binds` is deleted — so the bridge shrinks to
 * nothing without ever leaving the app uncompilable between commits.
 *
 * Four dependencies are shared with other islands, so they are NOT registered here — they are bridged
 * once in `coreBridgeKoinModule` (registering the same type in two loaded modules would throw
 * `DefinitionOverrideException`) and resolved below via `get()`:
 * - `UserPreferencesRepository` — shared with Stats.
 * - `UserPreferencesDataStore` — shared with Profile.
 * - `AuthRepository` — shared with Profile.
 * - `AnalyticsHelper` — now shared with CardDetail, so it was PROMOTED from this island into
 *   `coreBridgeKoinModule`; resolved below via `get()` instead of being registered here.
 *
 * ## KMP migration — Hilt→Koin cutover batch 5
 * `UserProfileDataSource` is now NATIVELY Koin-built in `authKoinModule` (the feature-private Hilt
 * `AuthModule` — which used to `@Provides` it — was deleted). It is shared with the Auth island
 * (`AuthRepositoryImpl`, via `coreBridgeKoinModule`), so this island's own `single { userProfileDataSource }`
 * registration was removed (the promote-then-shrink ritual) — [SettingsViewModel] now resolves it
 * cross-module via `get()` instead of a `ManaHubApp` forward-bridge field.
 *
 * @return a Koin [Module] that provides the bridged singletons and the [SettingsViewModel] factory.
 */
fun settingsKoinModule(
    pushTokenRepository: PushTokenRepository,
    notificationPrefsRepository: NotificationPrefsRepository,
    voiceModelRepository: VoiceModelRepository,
): Module = module {
    // ── Hilt → Koin bridge: re-expose the Settings-only Hilt-owned singletons to Koin (see KDoc). ──
    // (UserPreferencesRepository [Stats], UserPreferencesDataStore [Profile], AuthRepository [Profile],
    //  AnalyticsHelper [CardDetail] and UserProfileDataSource [Auth] are shared → bridged/natively built
    //  elsewhere, not here, and resolved below via get().)
    single { pushTokenRepository }
    single { notificationPrefsRepository }
    single { voiceModelRepository }

    // ── The Koin island: SettingsViewModel is now resolved by Koin, not Hilt. ──
    viewModel {
        SettingsViewModel(
            userPrefsDataStore = get(),
            userPreferencesRepo = get(),
            analyticsHelper = get(),
            authRepository = get(),
            userProfileDataSource = get(),
            pushTokenRepository = get(),
            notificationPrefsRepository = get(),
            voiceModelRepository = get(),
        )
    }
}
