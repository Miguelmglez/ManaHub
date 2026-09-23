package com.mmg.manahub.feature.collection

import com.mmg.manahub.feature.collection.presentation.importexport.ImportProgressThrottle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The resolver reports once per unmatched line; the UI must not see one update per line. */
class ImportProgressThrottleTest {

    @Test
    fun `five thousand unresolved lines produce far fewer than sixty updates`() {
        var now = 0L
        val throttle = ImportProgressThrottle(nowMillis = { now })
        val total = 5_000

        val emitted = (1..total).count { processed ->
            now += 1
            throttle.shouldEmit(processed, total)
        }

        assertTrue("emitted $emitted updates", emitted < 60)
    }

    @Test
    fun `the final call always emits, whatever the interval`() {
        var now = 0L
        val throttle = ImportProgressThrottle(nowMillis = { now })

        throttle.shouldEmit(1, 10)
        now += 1

        assertEquals(true, throttle.shouldEmit(10, 10))
    }

    @Test
    fun `a slow resolution still emits about once per interval`() {
        var now = 0L
        val throttle = ImportProgressThrottle(nowMillis = { now })
        val total = 1_000

        val emitted = (1 until total).count { processed ->
            now += 100
            throttle.shouldEmit(processed, total)
        }

        // ~100 s of work: the 250 ms floor never binds, so the 1 % step decides — one per percent.
        assertTrue("emitted $emitted updates", emitted in 90..100)
    }
}
