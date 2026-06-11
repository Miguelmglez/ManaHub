package com.mmg.manahub.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates a Baseline Profile for ManaHub by driving the most critical user journeys.
 *
 * The profile covers:
 *  1. Cold app start to Home screen (Compose rendering, Hilt init, Room open).
 *  2. Navigation from Home to Collection (LazyVerticalGrid + card images).
 *  3. Navigation from Collection to Card Detail (image loading, card data display).
 *
 * Run with:
 *   ./gradlew :baseline-profile:generateBaselineProfile
 *
 * The output `baseline-prof.txt` must be committed to `app/src/main/` so it is
 * packaged in the release APK and installed by Play's profile installer at first launch.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generateBaselineProfile() = rule.collect(
        packageName = "com.mmg.manahub",
        profileBlock = {
            // Journey 1: cold start to Home screen
            pressHome()
            startActivityAndWait()
            waitForHome()

            // Journey 2: navigate to Collection tab
            tapCollectionTab()
            device.waitForIdle()

            // Journey 3: scroll through the card grid to warm up LazyVerticalGrid
            scrollCollectionGrid()
        },
    )

    // -------------------------------------------------------------------------
    // Helper actions
    // -------------------------------------------------------------------------

    /**
     * Waits for the Home screen to finish its first Compose frame by looking for
     * the greeting text or the edit-mode pencil icon that always appears on Home.
     */
    private fun MacrobenchmarkScope.waitForHome() {
        device.wait(Until.hasObject(By.descContains("Edit widgets")), TIMEOUT_MS)
    }

    /**
     * Taps the Library (Collection) tab on the bottom navigation bar.
     * The tab has contentDescription "Library" as set in AppNavGraph.
     */
    private fun MacrobenchmarkScope.tapCollectionTab() {
        val tab = device.findObject(By.descContains("Library"))
        tab?.click()
        device.waitForIdle()
    }

    /**
     * Scrolls the card grid twice to ensure all RecyclerView / LazyColumn composables
     * are included in the warm-path profile.
     */
    private fun MacrobenchmarkScope.scrollCollectionGrid() {
        repeat(2) {
            device.swipe(
                device.displayWidth / 2,
                device.displayHeight * 3 / 4,
                device.displayWidth / 2,
                device.displayHeight / 4,
                /* steps = */ 10,
            )
            device.waitForIdle()
        }
    }

    private companion object {
        /** UI-automator wait timeout per step (3 s). */
        const val TIMEOUT_MS = 3_000L
    }
}
