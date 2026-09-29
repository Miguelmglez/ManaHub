package com.mmg.manahub.core.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mmg.manahub.core.data.local.MtgDatabase
import com.mmg.manahub.core.data.local.entity.FriendEntity
import com.mmg.manahub.core.data.local.entity.FriendRequestEntity
import com.mmg.manahub.core.data.local.entity.OutgoingFriendRequestEntity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** [FriendDao.replaceAll] is one transaction and never leaves an id in two lists (F-02 / F-06). */
@RunWith(AndroidJUnit4::class)
class FriendDaoReplaceAllTransactionTest {

    private lateinit var db: MtgDatabase
    private lateinit var dao: FriendDao

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MtgDatabase::class.java).build()
        dao = db.friendDao()
    }

    @After
    fun closeDatabase() {
        db.close()
    }

    private fun friend(id: String, userId: String) = FriendEntity(id, userId, "N $userId", "#TAG", null)
    private fun incoming(id: String, from: String) = FriendRequestEntity(id, from, "N", "#TAG", null, 0L)
    private fun outgoing(id: String, to: String) = OutgoingFriendRequestEntity(id, to, "N", "#TAG", null, 0L)

    @Test
    fun anAcceptedIdIsDroppedFromBothRequestLists() = runBlocking {
        dao.replaceAll(null, listOf(incoming("fs-in", "user-b")), listOf(outgoing("fs-out", "user-c")))

        dao.replaceAll(friends = listOf(friend("fs-out", "user-c")), incoming = null, outgoing = null)

        assertEquals(listOf("fs-out"), dao.observeFriends().first().map { it.id })
        assertTrue(dao.observeOutgoingRequests().first().isEmpty())
        assertEquals(listOf("fs-in"), dao.observePendingRequests().first().map { it.id })
    }

    @Test
    fun aRequestFromAnExistingFriendIsDroppedEvenWithAnotherRowId() = runBlocking {
        dao.replaceAll(
            friends = listOf(friend("fs-1", "user-b")),
            incoming = listOf(incoming("fs-2", "user-b")),
            outgoing = emptyList(),
        )

        assertTrue(dao.observePendingRequests().first().isEmpty())
    }

    @Test
    fun observersNeverSeeTheClearedIntermediateState() = runBlocking {
        dao.replaceAll(listOf(friend("fs-1", "user-a")), emptyList(), emptyList())
        val initialEmission = CompletableDeferred<Unit>()
        val emissions = async {
            dao.observeFriends()
                .onEach { if (it.size == 1) initialEmission.complete(Unit) }
                .take(2)
                .toList()
        }
        initialEmission.await()

        dao.replaceAll(listOf(friend("fs-1", "user-a"), friend("fs-2", "user-b")), null, null)

        val sizes = emissions.await().map { it.size }
        assertEquals(listOf(1, 2), sizes)
    }

    @Test
    fun clearAllEmptiesEveryTable() = runBlocking {
        dao.replaceAll(listOf(friend("fs-1", "user-a")), listOf(incoming("fs-2", "user-b")), listOf(outgoing("fs-3", "user-c")))

        dao.clearAll()

        assertTrue(dao.observeFriends().first().isEmpty())
        assertTrue(dao.observePendingRequests().first().isEmpty())
        assertTrue(dao.observeOutgoingRequests().first().isEmpty())
    }
}
