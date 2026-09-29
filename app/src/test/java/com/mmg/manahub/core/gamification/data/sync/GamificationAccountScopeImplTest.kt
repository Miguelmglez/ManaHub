package com.mmg.manahub.core.gamification.data.sync

import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.gamification.data.local.GamificationLocalStore
import com.mmg.manahub.core.gamification.domain.GamificationAccountScopeResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class GamificationAccountScopeImplTest {

    private val dataStore: UserPreferencesDataStore = mockk()
    private val localStore: GamificationLocalStore = mockk()
    private val syncManager: GamificationSyncManager = mockk()
    private val scope = GamificationAccountScopeImpl(dataStore, localStore, syncManager)

    @Before
    fun setUp() {
        coEvery { dataStore.setGamificationOwnerUserId(any()) } just runs
        coEvery { localStore.wipe() } just runs
        coEvery { syncManager.sync(any()) } returns Result.success(Unit)
        coEvery { syncManager.reconcileOnSignIn(any()) } returns Result.success(Unit)
        coEvery { dataStore.isGamificationVerifiedGuest() } returns true
        coEvery { dataStore.markGamificationVerifiedGuest() } just runs
    }

    @Test
    fun `guest owned store is claimed then merged into the account`() = runTest {
        coEvery { dataStore.getGamificationOwnerUserId() } returns null

        val result = scope.onSignedIn("user-a")

        assertEquals(GamificationAccountScopeResult.CLAIMED, result)
        coVerifyOrder {
            dataStore.setGamificationOwnerUserId("user-a")
            syncManager.reconcileOnSignIn("user-a")
        }
        coVerify(exactly = 0) { localStore.wipe() }
        coVerify(exactly = 0) { syncManager.sync(any()) }
    }

    @Test
    fun `legacy ownerless account rows are wiped before account B pulls its own progress`() = runTest {
        coEvery { dataStore.getGamificationOwnerUserId() } returns null
        coEvery { dataStore.isGamificationVerifiedGuest() } returns false

        val result = scope.onSignedIn("user-b")

        assertEquals(GamificationAccountScopeResult.SWITCHED, result)
        coVerifyOrder {
            localStore.wipe()
            dataStore.setGamificationOwnerUserId("user-b")
            syncManager.sync("user-b")
        }
        coVerify(exactly = 0) { syncManager.reconcileOnSignIn(any()) }
    }

    @Test
    fun `a signed out guest quarantines ambiguous rows before guest progress begins`() = runTest {
        coEvery { dataStore.getGamificationOwnerUserId() } returns null
        coEvery { dataStore.isGamificationVerifiedGuest() } returns false

        scope.onGuestActive()

        coVerifyOrder {
            localStore.wipe()
            dataStore.markGamificationVerifiedGuest()
        }
    }

    @Test
    fun `store already owned by the account runs a normal sync without merging`() = runTest {
        coEvery { dataStore.getGamificationOwnerUserId() } returns "user-a"

        val result = scope.onSignedIn("user-a")

        assertEquals(GamificationAccountScopeResult.SYNCED, result)
        coVerify(exactly = 1) { syncManager.sync("user-a") }
        coVerify(exactly = 0) { syncManager.reconcileOnSignIn(any()) }
        coVerify(exactly = 0) { localStore.wipe() }
        coVerify(exactly = 0) { dataStore.setGamificationOwnerUserId(any()) }
    }

    @Test
    fun `store owned by another account is wiped re-owned and pulled, never merged`() = runTest {
        coEvery { dataStore.getGamificationOwnerUserId() } returns "user-a"

        val result = scope.onSignedIn("user-b")

        assertEquals(GamificationAccountScopeResult.SWITCHED, result)
        coVerifyOrder {
            localStore.wipe()
            dataStore.setGamificationOwnerUserId("user-b")
            syncManager.sync("user-b")
        }
        coVerify(exactly = 0) { syncManager.reconcileOnSignIn(any()) }
    }

    @Test
    fun `a failed wipe never re-owns or syncs the foreign store`() = runTest {
        coEvery { dataStore.getGamificationOwnerUserId() } returns "user-a"
        coEvery { localStore.wipe() } throws IllegalStateException("disk full")

        runCatching { scope.onSignedIn("user-b") }

        coVerify(exactly = 0) { dataStore.setGamificationOwnerUserId(any()) }
        coVerify(exactly = 0) { syncManager.sync(any()) }
    }
}
