package com.mmg.manahub.feature.settings.di

import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.domain.auth.AuthRepository
import com.mmg.manahub.core.domain.repository.NotificationPrefsRepository
import com.mmg.manahub.core.domain.repository.PushTokenRepository
import com.mmg.manahub.core.domain.repository.UserPreferencesRepository
import com.mmg.manahub.core.util.AnalyticsHelper
import com.mmg.manahub.core.voice.domain.VoiceModelRepository
import com.mmg.manahub.feature.auth.data.remote.UserProfileDataSource
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
 * Three dependencies are shared with other islands, so they are NOT registered here — they are bridged
 * once in `coreBridgeKoinModule` (registering the same type in two loaded modules would throw
 * `DefinitionOverrideException`) and resolved below via `get()`:
 * - [UserPreferencesRepository] — shared with Stats.
 * - [UserPreferencesDataStore] — shared with Profile.
 * - [AuthRepository] — shared with Profile.
 *
 * @return a Koin [Module] that provides the bridged singletons and the [SettingsViewModel] factory.
 */
fun settingsKoinModule(
    analyticsHelper: AnalyticsHelper,
    userProfileDataSource: UserProfileDataSource,
    pushTokenRepository: PushTokenRepository,
    notificationPrefsRepository: NotificationPrefsRepository,
    voiceModelRepository: VoiceModelRepository,
): Module = module {
    // ── Hilt → Koin bridge: re-expose the Settings-only Hilt-owned singletons to Koin (see KDoc). ──
    // (UserPreferencesRepository [Stats], UserPreferencesDataStore [Profile] and AuthRepository [Profile]
    //  are shared → bridged in coreBridgeKoinModule, not here, and resolved below via get().)
    single { analyticsHelper }
    single { userProfileDataSource }
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
