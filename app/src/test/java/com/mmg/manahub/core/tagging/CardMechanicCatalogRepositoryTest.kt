package com.mmg.manahub.core.tagging

import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.data.remote.CardMechanicCatalogRemoteDataSourceContract
import com.mmg.manahub.core.data.remote.dto.CardMechanicCatalogDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardMechanicCatalogRepositoryTest {
    private val cachedJson = MutableStateFlow("[]")
    private val preferences = mockk<UserPreferencesDataStore>()
    private val remote = mockk<CardMechanicCatalogRemoteDataSourceContract>()
    private val entry = CardMechanicCatalogDto(
        key = "empower_jace",
        category = "keyword",
        labelEn = "Empower Jace",
        rules = JsonObject(emptyMap()),
        provenance = JsonObject(emptyMap()),
        revision = 1,
        reviewStatus = "active",
    )

    @Test
    fun failedRefreshPreservesLastCompleteCatalog() = runTest {
        every { preferences.cardMechanicCatalogFlow } returns cachedJson
        coEvery { preferences.saveCardMechanicCatalog(any()) } coAnswers {
            cachedJson.value = firstArg()
        }
        coEvery { remote.getActivePage(0, 500) } returns listOf(entry) andThenThrows IllegalStateException("offline")
        val repository = CardMechanicCatalogRepository(preferences, remote)

        assertTrue(repository.refresh())
        val persisted = cachedJson.value
        assertFalse(repository.refresh())
        assertEquals(listOf(entry), repository.entries.value)
        assertEquals(persisted, cachedJson.value)
        coVerify(exactly = 1) { preferences.saveCardMechanicCatalog(any()) }
    }
}
