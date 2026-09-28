package com.mmg.manahub.core.online.presentation

import android.content.Context
import com.mmg.manahub.R
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.UnknownHostException

/**
 * Unit tests for the online error mapper.
 *
 * GROUP 1 — transport failures classify by exception type, not by their message
 * GROUP 2 — backend RPC messages keep their specific mapping
 */
class OnlineErrorMapperTest {

    private val context = mockk<Context>(relaxed = true).also { ctx ->
        // Return the resource id as its own string so assertions stay independent of the copy
        every { ctx.getString(any()) } answers { firstArg<Int>().toString() }
    }

    private fun expected(resId: Int) = resId.toString()

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 1 — type-based classification
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given UnknownHostException when mapped then the connection message is used`() {
        val error = UnknownHostException("db.supabase.co")

        assertEquals(expected(R.string.lobby_error_connection), mapOnlineBackendError(context, error))
        assertEquals("network", classifyOnlineJoinError(error))
    }

    @Test
    fun `given a wrapped IOException when mapped then the cause chain is still classified`() {
        val error = IllegalStateException("join failed", IOException("socket closed"))

        assertEquals(expected(R.string.lobby_error_connection), mapOnlineBackendError(context, error))
        assertEquals("network", classifyOnlineJoinError(error))
    }

    @Test
    fun `given a request timeout when mapped then the timeout message is used`() {
        val error = HttpRequestTimeoutException("https://db.supabase.co", 10_000L)

        assertEquals(expected(R.string.lobby_error_timeout), mapOnlineBackendError(context, error))
        assertEquals("timeout", classifyOnlineJoinError(error))
    }

    @Test
    fun `given an unclassifiable throwable when mapped then the generic message is used`() {
        val error = IllegalStateException("something odd")

        assertEquals(expected(R.string.lobby_error_generic), mapOnlineBackendError(context, error))
        assertEquals("unexpected", classifyOnlineJoinError(error))
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 2 — backend RPC messages
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given the client-side display-name failure then it maps to its own message`() {
        val error = IllegalArgumentException("Display name must be 1-32 characters")

        assertEquals(expected(R.string.lobby_error_display_name), mapOnlineBackendError(context, error))
        assertEquals("invalid_display_name", classifyOnlineJoinError(error))
    }

    @Test
    fun `given the session-full RPC message then it keeps its specific mapping`() {
        val error = IllegalStateException("Session is full")

        assertEquals(expected(R.string.lobby_error_full), mapOnlineBackendError(context, error))
        assertEquals("session_full", classifyOnlineJoinError(error))
    }

    @Test
    fun `given a message-only source when mapped then the substring cases still apply`() {
        assertEquals(
            expected(R.string.lobby_error_not_found),
            mapOnlineBackendError(context, "Session not in LOBBY status"),
        )
        assertEquals(expected(R.string.lobby_error_generic), mapOnlineBackendError(context, null as String?))
    }
}
