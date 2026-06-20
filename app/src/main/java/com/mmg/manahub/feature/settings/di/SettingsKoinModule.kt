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
 * @return a Koin [Module] that provides the bridged singletons and the [SettingsViewModel] factory.
 */
fun settingsKoinModule(
    userPrefsDataStore: UserPreferencesDataStore,
    userPreferencesRepo: UserPreferencesRepository,
    analyticsHelper: AnalyticsHelper,
    authRepository: AuthRepository,
    userProfileDataSource: UserProfileDataSource,
    pushTokenRepository: PushTokenRepository,
    notificationPrefsRepository: NotificationPrefsRepository,
    voiceModelRepository: VoiceModelRepository,
): Module = module {
    // ── Hilt → Koin bridge: re-expose the Hilt-owned singletons to Koin (see KDoc above). ──
    single { userPrefsDataStore }
    single { userPreferencesRepo }
    single { analyticsHelper }
    single { authRepository }
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
