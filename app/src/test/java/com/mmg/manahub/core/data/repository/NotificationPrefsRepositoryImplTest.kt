package com.mmg.manahub.core.data.repository

import com.mmg.manahub.core.common.DispatcherProvider
import com.mmg.manahub.core.domain.repository.NotificationPrefsUnauthenticatedException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Unit tests for [NotificationPrefsRepositoryImpl].
 *
 * GROUP 1 — cache is keyed by the signed-in user (unauthenticated first load, sign-in, user switch)
 * GROUP 2 — writes: grouped atomic upsert, optimistic publish + rollback, unauthenticated failure
 */
class NotificationPrefsRepositoryImplTest {

    private class FakeRemote : NotificationPrefsRemoteSource {
        val rows = mutableMapOf<String, Map<String, Boolean>>()
        var fetchCount = 0
        var failNextUpsert = false
        var failFetch = false

        override suspend fun fetch(userId: String): Map<String, Boolean> {
            fetchCount++
            if (failFetch) throw IOException("offline")
            return rows[userId] ?: emptyMap()
        }

        override suspend fun upsert(userId: String, prefs: Map<String, Boolean>) {
            if (failNextUpsert) { failNextUpsert = false; throw IOException("offline") }
            rows[userId] = prefs
        }
    }

    private val remote = FakeRemote()
    private var userId: String? = null

    private fun repository() = NotificationPrefsRepositoryImpl(
        remote = remote,
        currentUserId = { userId },
        dispatcherProvider = DispatcherProvider(),
    )

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 1 — cache keyed by user
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given no signed-in user when first subscribed then map is empty and nothing is fetched`() = runTest {
        val repo = repository()

        val prefs = repo.prefsFlow.first()

        assertTrue(prefs.isEmpty())
        assertEquals(0, remote.fetchCount)
    }

    @Test
    fun `given unauthenticated first load when user signs in and refreshes then the row is loaded`() = runTest {
        // Arrange — the old code latched loaded=true on the empty unauthenticated start and never reloaded
        remote.rows["u1"] = mapOf("trade_proposed" to false)
        val repo = repository()
        assertTrue(repo.prefsFlow.first().isEmpty())

        // Act
        userId = "u1"
        val result = repo.refresh()

        // Assert
        assertTrue(result.isSuccess)
        assertEquals(mapOf("trade_proposed" to false), repo.prefsFlow.first())
        assertEquals(false, repo.isEventEnabled("trade_proposed"))
        assertEquals(1, remote.fetchCount)
    }

    @Test
    fun `given user A loaded when user B subscribes then A's overrides are never served`() = runTest {
        remote.rows["a"] = mapOf("friend_request" to false)
        remote.rows["b"] = emptyMap()
        val repo = repository()
        userId = "a"
        assertEquals(mapOf("friend_request" to false), repo.prefsFlow.first())

        userId = "b"

        assertTrue(repo.prefsFlow.first().isEmpty())
        assertTrue(repo.isEventEnabled("friend_request"))
    }

    @Test
    fun `given signed-out user when subscribed again then the cache is cleared`() = runTest {
        remote.rows["a"] = mapOf("friend_request" to false)
        val repo = repository()
        userId = "a"
        assertEquals(mapOf("friend_request" to false), repo.prefsFlow.first())

        userId = null

        assertTrue(repo.prefsFlow.first().isEmpty())
    }

    @Test
    fun `given fetch fails when subscribed then map stays empty and a later refresh recovers`() = runTest {
        remote.rows["a"] = mapOf("trade_accepted" to false)
        remote.failFetch = true
        val repo = repository()
        userId = "a"
        assertTrue(repo.prefsFlow.first().isEmpty())

        remote.failFetch = false
        assertTrue(repo.refresh().isSuccess)

        assertEquals(mapOf("trade_accepted" to false), repo.prefsFlow.first())
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 2 — writes
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given group write when it succeeds then one merged row is upserted and published`() = runTest {
        remote.rows["a"] = mapOf("friend_request" to false)
        userId = "a"
        val repo = repository()

        val result = repo.setEventsEnabled(listOf("trade_proposed", "trade_countered"), enabled = false)

        assertTrue(result.isSuccess)
        val expected = mapOf("friend_request" to false, "trade_proposed" to false, "trade_countered" to false)
        assertEquals(expected, remote.rows["a"])
        assertEquals(expected, repo.prefsFlow.first())
    }

    @Test
    fun `given upsert fails when group write then the cached map is rolled back and failure returned`() = runTest {
        remote.rows["a"] = mapOf("friend_request" to false)
        userId = "a"
        val repo = repository()
        assertEquals(mapOf("friend_request" to false), repo.prefsFlow.first())
        remote.failNextUpsert = true

        val result = repo.setEventsEnabled(listOf("trade_proposed"), enabled = false)

        assertTrue(result.isFailure)
        assertEquals(mapOf("friend_request" to false), repo.prefsFlow.first())
        assertEquals(mapOf("friend_request" to false), remote.rows["a"])
    }

    @Test
    fun `given no signed-in user when write then failure is NotificationPrefsUnauthenticatedException`() = runTest {
        val repo = repository()

        val result = repo.setEventEnabled("trade_proposed", enabled = false)

        assertTrue(result.exceptionOrNull() is NotificationPrefsUnauthenticatedException)
        assertTrue(remote.rows.isEmpty())
    }
}
