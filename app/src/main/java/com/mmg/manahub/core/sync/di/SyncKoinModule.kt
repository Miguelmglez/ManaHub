package com.mmg.manahub.core.sync.di

import com.mmg.manahub.core.data.local.dao.StatsDao
import com.mmg.manahub.core.sync.CollectionStatsSyncWorker
import com.mmg.manahub.core.sync.PriceRefreshWorker
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.dsl.worker
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * KMP migration — Hilt→Koin cutover batch 6 (WorkManager subsystem). Registers the two `core/sync`
 * workers that are NOT already co-located with their sole non-shared dependency —
 * [com.mmg.manahub.core.sync.CollectionSyncWorker]'s `worker { }` lives in
 * `feature.collection.di.collectionKoinModule`, next to the
 * [com.mmg.manahub.core.sync.SyncManager] bridge single it needs.
 *
 * ## Dependencies (all resolved via `get()` from modules already loaded elsewhere — none re-registered)
 * - [com.mmg.manahub.core.domain.auth.AuthRepository] — native single in `app.di.coreBridgeKoinModule`.
 * - [com.mmg.manahub.core.domain.repository.FriendRepository] — native single in
 *   `app.di.coreBridgeKoinModule`.
 * - [com.mmg.manahub.core.data.local.SyncPreferencesStore] — native single in
 *   `core.gamification.di.gamificationEngineKoinModule` (constructed directly from `androidContext()`;
 *   see that module's KDoc for why a second Koin-built instance alongside the Hilt-built one used
 *   internally by [com.mmg.manahub.core.sync.SyncManager] is behaviourally safe).
 * - [com.mmg.manahub.core.data.local.UserPreferencesDataStore] — bridged in `coreBridgeKoinModule`.
 * - [com.mmg.manahub.core.data.usecase.collection.RefreshCollectionPricesUseCase] — native single in
 *   `core.di.SharedDomainKoinModule` (KMP migration batch 2).
 *
 * [StatsDao] (Room, stays androidMain / Hilt-`DatabaseModule`-owned) is the only NEW forward-bridge this
 * batch introduces — [com.mmg.manahub.app.ManaHubApp] gained an `@Inject lateinit var statsDao: StatsDao`
 * field for it, exactly like every other Room DAO bridge in this migration (`TournamentDao`, `FriendDao`, ...).
 *
 * [CollectionStatsSyncWorker] and [PriceRefreshWorker] were converted from `@HiltWorker`/`@AssistedInject`
 * to plain `CoroutineWorker`s resolved by Koin's `worker { }` DSL (`koin-androidx-workmanager`) — see
 * `core.di.SyncModule.provideWorkManager` for how the resulting [androidx.work.DelegatingWorkerFactory]
 * dispatches to Koin's `KoinWorkerFactory` alongside the still-Hilt `HiltWorkerFactory` (the latter only
 * serves the excluded scanner's dormant `@HiltWorker`, if one is ever reinstated).
 *
 * @param statsDao the Hilt/Room-owned [StatsDao] singleton (this module only).
 * @return a Koin [Module] providing the [StatsDao] bridge and the two `worker { }` registrations.
 */
fun syncKoinModule(statsDao: StatsDao): Module = module {
    single { statsDao }

    worker {
        CollectionStatsSyncWorker(
            appContext = androidContext(),
            workerParams = it.get(),
            authRepo = get(),
            syncPrefs = get(),
            statsDao = get(),
            friendRepo = get(),
        )
    }

    worker {
        PriceRefreshWorker(
            appContext = androidContext(),
            workerParams = it.get(),
            refreshPricesUseCase = get(),
            userPreferencesDataStore = get(),
        )
    }
}
