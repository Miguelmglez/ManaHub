package com.mmg.manahub.core.gamification.data.local

import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.data.local.dao.GamificationDao
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.test.runTest
import org.junit.Test

class GamificationLocalStoreTest {

    private val dao: GamificationDao = mockk()
    private val dataStore: UserPreferencesDataStore = mockk()
    private val store = GamificationLocalStore(dao, dataStore)

    @Test
    fun `wipe clears the Room tables before the preferences`() = runTest {
        coEvery { dao.wipeAll() } just runs
        coEvery { dataStore.clearGamificationLocalState() } just runs

        store.wipe()

        coVerifyOrder {
            dao.wipeAll()
            dataStore.clearGamificationLocalState()
        }
    }

    @Test
    fun `a failed Room wipe leaves the owner untouched`() = runTest {
        coEvery { dao.wipeAll() } throws IllegalStateException("db locked")

        runCatching { store.wipe() }

        coVerify(exactly = 0) { dataStore.clearGamificationLocalState() }
    }
}
