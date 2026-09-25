package com.mmg.manahub.core.config.di

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.mmg.manahub.BuildConfig
import com.mmg.manahub.app.update.AppUpdateController
import com.mmg.manahub.core.config.FirebaseRemoteConfigRepository
import com.mmg.manahub.core.domain.config.RemoteConfigRepository
import com.mmg.manahub.core.domain.update.AppUpdateStatusProvider
import com.mmg.manahub.core.domain.update.EvaluateAppUpdateRequirementUseCase
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Remote Config (kill switches + update policy) and the app-wide in-app update controller.
 */
fun remoteConfigKoinModule(): Module = module {
    single<RemoteConfigRepository> {
        FirebaseRemoteConfigRepository(
            remoteConfig = FirebaseRemoteConfig.getInstance(),
            isDebugBuild = BuildConfig.DEBUG,
        )
    }
    single { EvaluateAppUpdateRequirementUseCase() }
    single {
        AppUpdateController(
            appContext = androidContext(),
            remoteConfigRepository = get(),
            evaluateAppUpdateRequirement = get(),
            currentVersionCode = BuildConfig.VERSION_CODE.toLong(),
        )
    } bind AppUpdateStatusProvider::class
}
