package com.mmg.manahub.feature.collection

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.testing.TestListenableWorkerBuilder
import com.mmg.manahub.app.ManaHubApp
import com.mmg.manahub.core.data.local.MtgDatabase
import com.mmg.manahub.core.domain.collection.transfer.*
import com.mmg.manahub.core.sync.CollectionSyncWorker
import com.mmg.manahub.feature.collection.data.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.androidx.workmanager.factory.KoinWorkerFactory
import org.koin.core.context.GlobalContext

@RunWith(AndroidJUnit4::class)
class CollectionTransferDiTest {
    @Test fun actualApplicationSharesHiltDatabaseAndConstructsNewAndExistingKoinWorkers() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val koin=GlobalContext.get()
        assertSame((context.applicationContext as ManaHubApp).transferDatabase,koin.get<MtgDatabase>())
        assertTrue(koin.get<CollectionTransferRepository>() is RoomCollectionTransferRepository)
        assertSame(koin.get<RoomCollectionTransferCoordinator>(),koin.get<CollectionTransferCoordinator>())
        assertTrue(koin.get<CollectionTransferFileStore>() is AndroidCollectionTransferFileStore)
        assertNotNull(koin.get<RunTransferWork>()); assertTrue(koin.get<TransferWorkScheduler>() is AndroidTransferWorkScheduler)
        val factory=KoinWorkerFactory()
        assertNotNull(TestListenableWorkerBuilder<CollectionTransferWorker>(context).setWorkerFactory(factory).build())
        assertNotNull(TestListenableWorkerBuilder<TransferWishlistDeliveryWorker>(context).setWorkerFactory(factory).build())
        assertNotNull(TestListenableWorkerBuilder<CollectionSyncWorker>(context).setWorkerFactory(factory).build())
    }
}
