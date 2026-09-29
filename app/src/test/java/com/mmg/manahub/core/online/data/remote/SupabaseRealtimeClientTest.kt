package com.mmg.manahub.core.online.data.remote

import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.core.online.domain.model.RealtimeDisconnectedException
import com.mmg.manahub.core.online.domain.model.RealtimeSubscribeFailedException
import com.mmg.manahub.core.online.domain.model.RealtimeSubscribeTimeoutException
import com.mmg.manahub.core.online.domain.model.SessionEvent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.plugins.PluginManager
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.serializer.KotlinXSerializer
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [SupabaseRealtimeClient] connect/disconnect lifecycle and broadcast safety.
 *
 * GROUP 1: connect — timeout, SDK failure, and early disconnect all throw a typed
 *          [com.mmg.manahub.core.online.domain.model.RealtimeConnectException] and release the channel
 * GROUP 2: connect — concurrent callers share one channel
 * GROUP 3: broadcast — never throws, returns false without a handle or when the SDK throws
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SupabaseRealtimeClientTest {

    private val channel = mockk<RealtimeChannel>(relaxed = true)
    private val realtime = mockk<Realtime>(relaxed = true)
    private val supabaseClient = mockk<SupabaseClient>()
    private val crashlytics = mockk<FirebaseCrashlytics>(relaxed = true)
    private val realtimeStatus = MutableStateFlow(Realtime.Status.CONNECTED)

    private companion object {
        const val SESSION_ID = "session-test-abc"
    }

    @Before
    fun setUp() {
        mockkStatic(FirebaseCrashlytics::class)
        every { FirebaseCrashlytics.getInstance() } returns crashlytics
        // A real PluginManager lets the SDK's `supabaseClient.realtime` extension resolve our mock.
        every { supabaseClient.pluginManager } returns PluginManager(mapOf(Realtime.key to realtime))
        every { realtime.channel(any(), any()) } returns channel
        // The client watches this for SDK-level resets; a real flow keeps the collector deterministic
        every { realtime.status } returns realtimeStatus
        every { realtime.serializer } returns KotlinXSerializer()
        every { channel.supabaseClient } returns supabaseClient
    }

    @After
    fun tearDown() {
        unmockkStatic(FirebaseCrashlytics::class)
    }

    private fun buildClient(dispatcher: TestDispatcher) =
        SupabaseRealtimeClient(supabaseClient = supabaseClient, ioDispatcher = dispatcher)

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 1 — connect failure paths
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given server never confirms subscription when connect then times out and releases the channel`() = runTest {
        // Arrange
        val client = buildClient(StandardTestDispatcher(testScheduler))
        coEvery { channel.subscribe(blockUntilSubscribed = true) } coAnswers { awaitCancellation() }

        // Act
        val error = runCatching { client.connect(SESSION_ID) }.exceptionOrNull()

        // Assert
        assertTrue("expected timeout, got $error", error is RealtimeSubscribeTimeoutException)
        coVerify(exactly = 1) { channel.unsubscribe() }
        coVerify(exactly = 1) { realtime.removeChannel(channel) }
        // No dead handle: a broadcast finds nothing to send on.
        assertFalse(client.broadcastLifeDelta(SESSION_ID, slotIndex = 0, newLife = 20))
    }

    @Test
    fun `given SDK throws during subscribe when connect then wraps it and releases the channel`() = runTest {
        // Arrange
        val client = buildClient(StandardTestDispatcher(testScheduler))
        coEvery { channel.subscribe(blockUntilSubscribed = true) } throws IllegalStateException("boom")

        // Act
        val error = runCatching { client.connect(SESSION_ID) }.exceptionOrNull()

        // Assert
        assertTrue("expected subscribe failure, got $error", error is RealtimeSubscribeFailedException)
        // Walk the chain: under -ea kotlinx's stack-trace recovery copies the exception with the original as cause
        assertTrue(generateSequence(error) { it.cause }.any { it is IllegalStateException })
        coVerify(exactly = 1) { realtime.removeChannel(channel) }
    }

    @Test
    fun `given disconnect during subscribe when connect then throws disconnected not cancellation`() = runTest {
        // Arrange
        val client = buildClient(StandardTestDispatcher(testScheduler))
        coEvery { channel.subscribe(blockUntilSubscribed = true) } coAnswers { awaitCancellation() }
        val attempt = async { runCatching { client.connect(SESSION_ID) } }
        runCurrent()

        // Act
        client.disconnect(SESSION_ID)
        val error = attempt.await().exceptionOrNull()

        // Assert
        assertTrue("expected disconnected, got $error", error is RealtimeDisconnectedException)
        // Released exactly once: disconnect() did it, connect()'s teardown must not repeat it.
        coVerify(exactly = 1) { channel.unsubscribe() }
        coVerify(exactly = 1) { realtime.removeChannel(channel) }
    }

    @Test
    fun `given failed connect when connect again then a fresh channel is created`() = runTest {
        // Arrange
        val client = buildClient(StandardTestDispatcher(testScheduler))
        coEvery { channel.subscribe(blockUntilSubscribed = true) } throws IllegalStateException("boom")
        runCatching { client.connect(SESSION_ID) }
        coEvery { channel.subscribe(blockUntilSubscribed = true) } returns Unit

        // Act
        client.connect(SESSION_ID)

        // Assert — the second attempt did not reuse the dead handle
        verify(exactly = 2) { realtime.channel(any(), any()) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 2 — concurrent connect
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given two concurrent connects for the same session then one channel is built`() = runTest {
        // Arrange
        val client = buildClient(StandardTestDispatcher(testScheduler))
        coEvery { channel.subscribe(blockUntilSubscribed = true) } returns Unit

        // Act
        val first = async { client.connect(SESSION_ID) }
        val second = async { client.connect(SESSION_ID) }
        first.await()
        second.await()

        // Assert
        verify(exactly = 1) { realtime.channel(any(), any()) }
        coVerify(exactly = 1) { channel.subscribe(blockUntilSubscribed = true) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 3 — broadcast never throws
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given no handle when broadcast then returns false without touching the SDK`() = runTest {
        // Arrange
        val client = buildClient(StandardTestDispatcher(testScheduler))

        // Act
        val sent = client.broadcastPhaseChange(SESSION_ID, "UNTAP", activePlayerSlot = 0, turnNumber = 1)

        // Assert
        assertFalse(sent)
        coVerify(exactly = 0) { channel.broadcast(any(), any<JsonObject>()) }
    }

    @Test
    fun `given SDK throws when broadcast then returns false and records a non-fatal`() = runTest {
        // Arrange
        val client = buildClient(StandardTestDispatcher(testScheduler))
        coEvery { channel.subscribe(blockUntilSubscribed = true) } returns Unit
        client.connect(SESSION_ID)
        coEvery { channel.broadcast(any(), any<JsonObject>()) } throws
            IllegalStateException("Failed to broadcast message (500)")

        // Act
        val sent = client.broadcastLifeDelta(SESSION_ID, slotIndex = 0, newLife = 18)

        // Assert
        assertFalse(sent)
        verify(exactly = 1) { crashlytics.recordException(any()) }
    }

    @Test
    fun `given buffered events when clearReplay then a late collector sees none of them`() = runTest {
        // A late collector (the game screen attaches after the HTTP snapshot) must not replay deltas
        // the snapshot already accounts for — that regressed life totals to pre-snapshot values.
        val client = buildClient(StandardTestDispatcher(testScheduler))
        coEvery { channel.subscribe(blockUntilSubscribed = true) } returns Unit
        client.connect(SESSION_ID)
        val flow = client.observeSession(SESSION_ID) as MutableSharedFlow<SessionEvent>
        flow.emit(SessionEvent.LifeDeltaReceived(slotIndex = 1, newLife = 12))
        assertEquals(1, flow.replayCache.size)

        client.clearReplay(SESSION_ID)

        assertTrue(flow.replayCache.isEmpty())
        val received = mutableListOf<SessionEvent>()
        val collectJob = launch { client.observeSession(SESSION_ID).toList(received) }
        runCurrent()
        collectJob.cancel()
        assertTrue("A late collector must not replay pre-snapshot deltas", received.isEmpty())
    }

    @Test
    fun `given an SDK socket reset when reconnected then a realtime_reset error is emitted`() = runTest {
        // RealtimeImpl.disconnect() calls channel.teardown() → callbackManager.reset(), so our flows
        // survive on a channel with no listeners; consumers must be told to rebuild.
        val client = buildClient(StandardTestDispatcher(testScheduler))
        coEvery { channel.subscribe(blockUntilSubscribed = true) } returns Unit
        client.connect(SESSION_ID)
        val flow = client.observeSession(SESSION_ID) as MutableSharedFlow<SessionEvent>
        flow.resetReplayCache()

        realtimeStatus.value = Realtime.Status.DISCONNECTED
        runCurrent()
        realtimeStatus.value = Realtime.Status.CONNECTED
        runCurrent()

        assertEquals(
            listOf(SessionEvent.Error(SupabaseRealtimeClient.REALTIME_RESET)),
            flow.replayCache,
        )
    }

    @Test
    fun `given no handle when clearReplay then it is a no-op`() = runTest {
        val client = buildClient(StandardTestDispatcher(testScheduler))

        client.clearReplay("unknown-session")
    }

    @Test
    fun `given live handle when broadcast succeeds then returns true`() = runTest {
        // Arrange
        val client = buildClient(StandardTestDispatcher(testScheduler))
        coEvery { channel.subscribe(blockUntilSubscribed = true) } returns Unit
        client.connect(SESSION_ID)

        // Act
        val sent = client.broadcastLandToggled(SESSION_ID, slotIndex = 2, played = true)

        // Assert
        assertTrue(sent)
        coVerify(exactly = 1) { channel.broadcast("land_toggled", any<JsonObject>()) }
    }
}
