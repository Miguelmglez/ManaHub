package com.mmg.manahub.core.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mmg.manahub.core.data.local.MtgDatabase
import com.mmg.manahub.core.data.local.TradeListOwner
import com.mmg.manahub.core.data.local.entity.LocalOpenForTradeEntity
import com.mmg.manahub.core.data.local.entity.LocalWishlistEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TradeListOwnerScopeTest {
    private lateinit var database: MtgDatabase

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, MtgDatabase::class.java).build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun accountSwitchRetainsVerifiedOfflineRowsAndRemovesAmbiguousLegacyRows() = runBlocking {
        val wishes = database.localWishlistDao()
        val offers = database.localOpenForTradeDao()
        for (owner in listOf("account-a", "account-b", TradeListOwner.GUEST, null)) {
            val suffix = owner ?: "legacy"
            wishes.insert(LocalWishlistEntity(
                id = "wish-$suffix", scryfallId = "card-$suffix", isFoil = null,
                condition = null, language = null, ownerUserId = owner,
            ))
            offers.insert(LocalOpenForTradeEntity(
                id = "offer-$suffix", localCollectionId = "row-$suffix",
                scryfallId = "card-$suffix", ownerUserId = owner,
            ))
        }
        wishes.insert(LocalWishlistEntity(
            id = "wish-invalid-guest", scryfallId = "card-invalid-guest", isFoil = null,
            condition = null, language = null, synced = true, ownerUserId = TradeListOwner.GUEST,
        ))
        offers.insert(LocalOpenForTradeEntity(
            id = "offer-invalid-guest", localCollectionId = "row-invalid-guest",
            scryfallId = "card-invalid-guest", synced = true, ownerUserId = TradeListOwner.GUEST,
        ))

        wishes.deleteAmbiguousRows()
        offers.deleteAmbiguousRows()

        assertEquals(listOf("wish-account-a"), wishes.observeAll("account-a").first().map { it.id })
        assertEquals(listOf("wish-account-b"), wishes.observeAll("account-b").first().map { it.id })
        assertEquals(listOf("wish-${TradeListOwner.GUEST}"), wishes.observeAll(TradeListOwner.GUEST).first().map { it.id })
        assertEquals(listOf("offer-account-a"), offers.observeAll("account-a").first().map { it.id })
        assertEquals(listOf("offer-account-b"), offers.observeAll("account-b").first().map { it.id })
        assertEquals(listOf("offer-${TradeListOwner.GUEST}"), offers.observeAll(TradeListOwner.GUEST).first().map { it.id })
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM local_wishlists WHERE owner_user_id IS NULL").use {
            it.moveToFirst()
            assertEquals(0, it.getInt(0))
        }
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM local_open_for_trade WHERE owner_user_id IS NULL").use {
            it.moveToFirst()
            assertEquals(0, it.getInt(0))
        }
    }
}
