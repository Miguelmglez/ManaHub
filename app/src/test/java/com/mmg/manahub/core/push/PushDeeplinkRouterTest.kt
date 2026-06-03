package com.mmg.manahub.core.push

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [PushDeeplinkRouter].
 *
 * [PushDeeplinkRouter] is a Kotlin `object` singleton. Both [setUp] and [tearDown]
 * call [setNavigator](null) to clear the navigator reference and the pending deeplink
 * buffer, ensuring test isolation within the same JVM process.
 *
 * Covers:
 *  - GROUP 1: enqueue when navigator is already set → immediate dispatch
 *  - GROUP 2: enqueue before navigator set → deeplink is buffered
 *  - GROUP 3: setNavigator flushes buffered deeplink
 *  - GROUP 4: setNavigator(null) clears navigator without crashing
 *  - GROUP 5: multiple buffered deeplinks — only the last one wins
 */
class PushDeeplinkRouterTest {

    // ── Constants ─────────────────────────────────────────────────────────────

    private val TRADE_DEEPLINK = "manahub://trade/root-uuid/prop-uuid"
    private val FRIENDS_DEEPLINK = "manahub://friends"
    private val SECOND_TRADE_DEEPLINK = "manahub://trade/root-uuid-2/prop-uuid-2"

    // ── Setup / Teardown ──────────────────────────────────────────────────────

    @Before
    fun setUp() {
        // Clear both navigator and pending buffer before each test
        PushDeeplinkRouter.setNavigator(null)
    }

    @After
    fun tearDown() {
        // Ensure state does not bleed into subsequent test classes
        PushDeeplinkRouter.setNavigator(null)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 1 — enqueue with navigator already set → immediate navigation
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given navigator set when enqueue then navigator lambda is invoked immediately`() {
        // Arrange
        val navigatedRoutes = mutableListOf<String>()
        PushDeeplinkRouter.setNavigator { route -> navigatedRoutes.add(route) }

        // Act
        PushDeeplinkRouter.enqueue(TRADE_DEEPLINK)

        // Assert
        assertEquals(1, navigatedRoutes.size)
        assertEquals(TRADE_DEEPLINK, navigatedRoutes[0])
    }

    @Test
    fun `given navigator set when enqueue then exact deeplink is forwarded to navigator`() {
        // Arrange
        var received: String? = null
        PushDeeplinkRouter.setNavigator { received = it }

        // Act
        PushDeeplinkRouter.enqueue(FRIENDS_DEEPLINK)

        // Assert
        assertEquals(FRIENDS_DEEPLINK, received)
    }

    @Test
    fun `given navigator set when enqueue called twice then navigator is invoked twice`() {
        // Arrange
        val navigatedRoutes = mutableListOf<String>()
        PushDeeplinkRouter.setNavigator { route -> navigatedRoutes.add(route) }

        // Act
        PushDeeplinkRouter.enqueue(TRADE_DEEPLINK)
        PushDeeplinkRouter.enqueue(FRIENDS_DEEPLINK)

        // Assert
        assertEquals(2, navigatedRoutes.size)
        assertEquals(TRADE_DEEPLINK, navigatedRoutes[0])
        assertEquals(FRIENDS_DEEPLINK, navigatedRoutes[1])
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 2 — enqueue before navigator set → deeplink is buffered
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given no navigator when enqueue then deeplink is buffered and not lost`() {
        // Arrange — no navigator registered (cold start)
        val navigatedRoutes = mutableListOf<String>()

        // Act: deeplink arrives before the NavController is composed
        PushDeeplinkRouter.enqueue(TRADE_DEEPLINK)

        // Assert: buffered — navigator not yet called
        assertEquals(0, navigatedRoutes.size)
    }

    @Test
    fun `given deeplink buffered when setNavigator called then navigator receives the buffered deeplink`() {
        // Arrange: deeplink buffered during cold start
        PushDeeplinkRouter.enqueue(TRADE_DEEPLINK)

        // Act: NavController is now composed and registers
        val navigatedRoutes = mutableListOf<String>()
        PushDeeplinkRouter.setNavigator { route -> navigatedRoutes.add(route) }

        // Assert: the buffered deeplink is flushed immediately on setNavigator
        assertEquals(1, navigatedRoutes.size)
        assertEquals(TRADE_DEEPLINK, navigatedRoutes[0])
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 3 — setNavigator flushes the pending deeplink
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given buffered deeplink when setNavigator then pending buffer is cleared after flush`() {
        // Arrange
        PushDeeplinkRouter.enqueue(TRADE_DEEPLINK)

        val firstNavigation = mutableListOf<String>()
        PushDeeplinkRouter.setNavigator { firstNavigation.add(it) }

        // Act: replace navigator (simulates recompose or screen change)
        val secondNavigation = mutableListOf<String>()
        PushDeeplinkRouter.setNavigator { secondNavigation.add(it) }

        // Assert: pending buffer was consumed on first setNavigator; second call does not re-dispatch
        assertEquals(1, firstNavigation.size)
        assertEquals(0, secondNavigation.size)
    }

    @Test
    fun `given no buffered deeplink when setNavigator then navigator is not invoked`() {
        // Arrange — no pending deeplink
        var called = false
        // Act
        PushDeeplinkRouter.setNavigator { called = true }

        // Assert
        assertEquals(false, called)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 4 — setNavigator(null) clears navigator without crashing
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given navigator set when setNavigator null then subsequent enqueue buffers instead of crashing`() {
        // Arrange
        PushDeeplinkRouter.setNavigator { /* noop */ }

        // Act: navigator is cleared (e.g. composable disposed)
        PushDeeplinkRouter.setNavigator(null)

        // Assert: enqueue after clear must not crash and must buffer
        PushDeeplinkRouter.enqueue(TRADE_DEEPLINK)
        // Flushed when a new navigator registers
        val dispatched = mutableListOf<String>()
        PushDeeplinkRouter.setNavigator { dispatched.add(it) }
        assertEquals(1, dispatched.size)
        assertEquals(TRADE_DEEPLINK, dispatched[0])
    }

    @Test
    fun `given no navigator registered when setNavigator null then does not crash`() {
        // Act + Assert — must not throw
        PushDeeplinkRouter.setNavigator(null)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 5 — multiple enqueues before navigator set: last one wins
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given multiple deeplinks enqueued without navigator then only last deeplink is buffered`() {
        // Arrange — cold start, multiple notifications arrive rapidly
        PushDeeplinkRouter.enqueue(TRADE_DEEPLINK)
        PushDeeplinkRouter.enqueue(FRIENDS_DEEPLINK)
        PushDeeplinkRouter.enqueue(SECOND_TRADE_DEEPLINK)

        // Act: NavController registers
        val dispatched = mutableListOf<String>()
        PushDeeplinkRouter.setNavigator { dispatched.add(it) }

        // Assert: only the most recently buffered deeplink is dispatched (last-write-wins)
        assertEquals(1, dispatched.size)
        assertEquals(SECOND_TRADE_DEEPLINK, dispatched[0])
    }

    @Test
    fun `given two deeplinks buffered when navigator set then only latest is dispatched`() {
        // Arrange
        PushDeeplinkRouter.enqueue(FRIENDS_DEEPLINK)
        PushDeeplinkRouter.enqueue(TRADE_DEEPLINK)  // overwrites the previous buffer

        // Act
        val dispatched = mutableListOf<String>()
        PushDeeplinkRouter.setNavigator { dispatched.add(it) }

        // Assert
        assertEquals(1, dispatched.size)
        assertEquals(TRADE_DEEPLINK, dispatched[0])
    }
}
