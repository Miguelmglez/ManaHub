package com.mmg.manahub.core.gamification.di

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GamificationStoreReadyTest {
    @Test
    fun `guest cannot read or write account A progress after signout`() {
        assertFalse(isGamificationStoreReady(null, true, "user-a", false))
    }

    @Test
    fun `account B stays blocked while account A store is still present`() {
        assertFalse(isGamificationStoreReady("user-b", false, "user-a", false))
        assertTrue(isGamificationStoreReady("user-b", false, "user-b", false))
    }

    @Test
    fun `verified guest can use ownerless store while unresolved session stays blocked`() {
        assertTrue(isGamificationStoreReady(null, true, null, true))
        assertFalse(isGamificationStoreReady(null, false, null, true))
        assertFalse(isGamificationStoreReady(null, true, null, false))
    }
}
