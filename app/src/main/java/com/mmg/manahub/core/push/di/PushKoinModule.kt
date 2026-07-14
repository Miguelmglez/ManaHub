package com.mmg.manahub.core.push.di

import com.mmg.manahub.core.data.remote.push.PushTokenRemoteDataSource
import com.mmg.manahub.core.push.RegisterPushTokenWorker
import com.mmg.manahub.core.push.UnregisterPushTokenWorker
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.dsl.worker
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * KMP migration — Hilt→Koin cutover batch 6 (WorkManager subsystem). Registers the two `core/push`
 * background-retry workers as Koin `worker { }` definitions.
 *
 * [PushTokenRemoteDataSource] KEEPS its Hilt `@Inject constructor` —
 * [PushTokenRepositoryImpl][com.mmg.manahub.core.data.repository.PushTokenRepositoryImpl] (bound in the
 * still-Hilt `PushModule`) needs it as a dependency, and `PushTokenRepository` is itself
 * forward-bridged from an eager `@Inject lateinit var` field on
 * [ManaHubApp][com.mmg.manahub.app.ManaHubApp] — populated by Hilt BEFORE `startKoin()` runs (the same
 * "eager Hilt" ordering hazard documented on `core.di.KoinToHiltBridgeModule`, which rules out a reverse
 * Koin→Hilt bridge here). This is therefore a FORWARD bridge instead: `ManaHubApp` gained a new
 * `@Inject lateinit var pushTokenRemoteDataSource: PushTokenRemoteDataSource` field (Hilt already
 * constructs this exact singleton as a dependency of `PushTokenRepositoryImpl`, so the extra field costs
 * nothing) and hands the SAME instance to Koin here — no duplicate construction.
 *
 * @param pushTokenRemoteDataSource the Hilt-owned [PushTokenRemoteDataSource] singleton.
 * @return a Koin [Module] providing the bridge single and the two `worker { }` registrations.
 */
fun pushKoinModule(pushTokenRemoteDataSource: PushTokenRemoteDataSource): Module = module {
    single { pushTokenRemoteDataSource }

    worker {
        RegisterPushTokenWorker(
            appContext = androidContext(),
            workerParams = it.get(),
            dataSource = get(),
        )
    }

    worker {
        UnregisterPushTokenWorker(
            appContext = androidContext(),
            workerParams = it.get(),
            dataSource = get(),
        )
    }
}
