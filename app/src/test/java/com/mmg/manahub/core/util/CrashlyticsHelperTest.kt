package com.mmg.manahub.core.util

import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class CrashlyticsHelperTest {
    private val crashlytics = mockk<FirebaseCrashlytics>(relaxed = true)

    @Before
    fun setUp() {
        mockkStatic(FirebaseCrashlytics::class)
        every { FirebaseCrashlytics.getInstance() } returns crashlytics
    }

    @After
    fun tearDown() {
        unmockkStatic(FirebaseCrashlytics::class)
    }

    @Test
    fun `safe nonfatal preserves location without reporting user supplied cause`() {
        val reported = slot<Throwable>()
        every { crashlytics.recordException(capture(reported)) } returns Unit
        val original = IllegalStateException("private-user-input")

        recordSafeNonFatal("friend_cards_lookup_failed", original)

        assertEquals("[friend_cards_lookup_failed] IllegalStateException", reported.captured.message)
        assertNull(reported.captured.cause)
        assertFalse(reported.captured.stackTrace.isEmpty())
        assertEquals(original.stackTrace.toList(), reported.captured.stackTrace.toList())
    }
}
