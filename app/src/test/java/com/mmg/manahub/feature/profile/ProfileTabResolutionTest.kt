package com.mmg.manahub.feature.profile

import com.mmg.manahub.feature.profile.presentation.ProfileTab
import com.mmg.manahub.feature.profile.presentation.resolveProfileTab
import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileTabResolutionTest {

    @Test
    fun `route argument maps to its tab and unknown values open Overview`() {
        assertEquals(ProfileTab.QUESTS, ProfileTab.fromRouteArg("quests"))
        assertEquals(ProfileTab.ACHIEVEMENTS, ProfileTab.fromRouteArg("ACHIEVEMENTS"))
        assertEquals(ProfileTab.REWARDS, ProfileTab.fromRouteArg("rewards"))
        assertEquals(ProfileTab.OVERVIEW, ProfileTab.fromRouteArg("nope"))
        assertEquals(ProfileTab.OVERVIEW, ProfileTab.fromRouteArg(null))
    }

    @Test
    fun `a gamification deep link falls back to Overview while gamification is unavailable`() {
        ProfileTab.entries.forEach { tab ->
            assertEquals(ProfileTab.OVERVIEW, resolveProfileTab(tab, gamificationAvailable = false))
        }
    }

    @Test
    fun `the selected tab is shown while gamification is available`() {
        ProfileTab.entries.forEach { tab ->
            assertEquals(tab, resolveProfileTab(tab, gamificationAvailable = true))
        }
    }
}
