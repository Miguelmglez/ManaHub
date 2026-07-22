package com.mmg.manahub.feature.home.presentation

import com.mmg.manahub.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [resolveGreetingVariant] (Home widget board overhaul, TASK 2) — the pure,
 * non-`@Composable` greeting-variant selector extracted from `HomeScreen.kt`'s `greetingText()`
 * so the deterministic-by-day selection logic is testable without Compose UI test infra.
 */
class HomeScreenGreetingTest {

    private val morningVariants = setOf(
        R.string.home_greeting_morning_1, R.string.home_greeting_morning_2,
        R.string.home_greeting_morning_3, R.string.home_greeting_morning_4,
    )
    private val afternoonVariants = setOf(
        R.string.home_greeting_afternoon_1, R.string.home_greeting_afternoon_2,
        R.string.home_greeting_afternoon_3, R.string.home_greeting_afternoon_4,
    )
    private val eveningVariants = setOf(
        R.string.home_greeting_evening_1, R.string.home_greeting_evening_2, R.string.home_greeting_evening_3,
    )
    private val nightVariants = setOf(
        R.string.home_greeting_night_1, R.string.home_greeting_night_2,
        R.string.home_greeting_night_3, R.string.home_greeting_night_4,
    )
    private val signedOutVariants = setOf(
        R.string.home_greeting_signed_out_1, R.string.home_greeting_signed_out_2, R.string.home_greeting_signed_out_3,
    )

    @Test
    fun `morning hours 5 through 11 pick a morning variant`() {
        for (hour in 5..11) {
            val result = resolveGreetingVariant(hour = hour, epochDay = 100L, hasName = true)
            assertTrue("hour=$hour resolved to unexpected id $result", result in morningVariants)
        }
    }

    @Test
    fun `afternoon hours 12 through 16 pick an afternoon variant`() {
        for (hour in 12..16) {
            val result = resolveGreetingVariant(hour = hour, epochDay = 1L, hasName = true)
            assertTrue("hour=$hour resolved to unexpected id $result", result in afternoonVariants)
        }
    }

    @Test
    fun `evening hours 17 through 20 pick an evening variant`() {
        for (hour in 17..20) {
            val result = resolveGreetingVariant(hour = hour, epochDay = 1L, hasName = true)
            assertTrue("hour=$hour resolved to unexpected id $result", result in eveningVariants)
        }
    }

    @Test
    fun `night hours including the midnight wraparound pick a night variant`() {
        for (hour in listOf(21, 22, 23, 0, 1, 2, 3, 4)) {
            val result = resolveGreetingVariant(hour = hour, epochDay = 1L, hasName = true)
            assertTrue("hour=$hour resolved to unexpected id $result", result in nightVariants)
        }
    }

    @Test
    fun `signed-out (no name) always picks a signed-out variant regardless of hour`() {
        for (hour in 0..23) {
            val result = resolveGreetingVariant(hour = hour, epochDay = 5L, hasName = false)
            assertTrue("hour=$hour resolved to unexpected id $result", result in signedOutVariants)
        }
    }

    @Test
    fun `selection is deterministic for the same hour and epoch day`() {
        val first = resolveGreetingVariant(hour = 9, epochDay = 42L, hasName = true)
        val second = resolveGreetingVariant(hour = 9, epochDay = 42L, hasName = true)
        assertEquals(first, second)
    }

    @Test
    fun `selection cycles through every morning variant across consecutive epoch days`() {
        // The morning band has 4 variants; across 4 consecutive days every variant must appear
        // at least once (cyclic index by day), proving the day genuinely drives the selection
        // rather than being fixed per app install.
        val seen = (0L until 4L).map { day -> resolveGreetingVariant(hour = 9, epochDay = day, hasName = true) }.toSet()
        assertEquals(4, seen.size)
    }
}
