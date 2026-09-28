package com.mmg.manahub.core.tagging

import com.mmg.manahub.core.data.local.CardMechanicRefreshCheckpoint
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.data.remote.dto.CardMechanicCatalogDto
import com.mmg.manahub.core.domain.usecase.card.HydrateCollectionStrategyTagsUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class CardMechanicCatalogRehydratorTest {
    private val preferences: UserPreferencesDataStore = mockk()
    private val hydrate: HydrateCollectionStrategyTagsUseCase = mockk()
    private val catalog = listOf(
        CardMechanicCatalogDto(
            key = "empower_jace", category = "keyword", labelEn = "Empower Jace",
            rules = JsonObject(emptyMap()), provenance = JsonObject(emptyMap()), revision = 1,
            reviewStatus = "active",
        ),
    )

    @Test
    fun `signed in owner resumes a keyset page and checkpoints only after the page succeeds`() = runTest {
        val checkpoints = mutableListOf<CardMechanicRefreshCheckpoint>()
        every { preferences.cardMechanicRefreshCheckpointFlow } answers { flowOf(checkpoints.toList()) }
        coEvery { preferences.saveCardMechanicRefreshCheckpoint(any()) } answers {
            val updated = firstArg<CardMechanicRefreshCheckpoint>()
            checkpoints.removeAll { it.ownerUserId == updated.ownerUserId }
            checkpoints += updated
        }
        coEvery { hydrate.rehydrateCatalogPage("owner-a", "", any(), any()) } returns
            HydrateCollectionStrategyTagsUseCase.CatalogPageResult("card-200", true)
        coEvery { hydrate.rehydrateCatalogPage("owner-a", "card-200", any(), any()) } returns
            HydrateCollectionStrategyTagsUseCase.CatalogPageResult(null, false)

        CardMechanicCatalogRehydrator(preferences, hydrate) { "owner-a" }.rehydrate("owner-a", catalog)

        assertEquals("owner-a", checkpoints.single().ownerUserId)
        assertEquals("card-200", checkpoints.single().cursor)
        assertEquals(true, checkpoints.single().complete)
        coVerify(exactly = 2) { hydrate.rehydrateCatalogPage("owner-a", any(), any(), any()) }
    }

    @Test
    fun `account change stops old owner before publishing its cursor`() = runTest {
        var currentOwner = "owner-a"
        val checkpoints = mutableListOf<CardMechanicRefreshCheckpoint>()
        every { preferences.cardMechanicRefreshCheckpointFlow } answers { flowOf(checkpoints.toList()) }
        coEvery { preferences.saveCardMechanicRefreshCheckpoint(any()) } answers {
            checkpoints.clear()
            checkpoints += firstArg<CardMechanicRefreshCheckpoint>()
        }
        coEvery { hydrate.rehydrateCatalogPage("owner-a", "", any(), any()) } answers {
            currentOwner = "owner-b"
            HydrateCollectionStrategyTagsUseCase.CatalogPageResult("card-200", true)
        }

        CardMechanicCatalogRehydrator(preferences, hydrate) { currentOwner }.rehydrate("owner-a", catalog)

        assertEquals("", checkpoints.single().cursor)
        coVerify(exactly = 1) { hydrate.rehydrateCatalogPage("owner-a", "", any(), any()) }
    }

    @Test
    fun `account switch preserves previous owner's completed checkpoint`() = runTest {
        var currentOwner = "owner-a"
        val checkpoints = mutableListOf<CardMechanicRefreshCheckpoint>()
        every { preferences.cardMechanicRefreshCheckpointFlow } answers { flowOf(checkpoints.toList()) }
        coEvery { preferences.saveCardMechanicRefreshCheckpoint(any()) } answers {
            val updated = firstArg<CardMechanicRefreshCheckpoint>()
            checkpoints.removeAll { it.ownerUserId == updated.ownerUserId }
            checkpoints += updated
        }
        coEvery { hydrate.rehydrateCatalogPage(any(), any(), any(), any()) } returns
            HydrateCollectionStrategyTagsUseCase.CatalogPageResult("card-1", false)
        val rehydrator = CardMechanicCatalogRehydrator(preferences, hydrate) { currentOwner }

        rehydrator.rehydrate("owner-a", catalog)
        currentOwner = "owner-b"
        rehydrator.rehydrate("owner-b", catalog)
        currentOwner = "owner-a"
        rehydrator.rehydrate("owner-a", catalog)

        assertEquals(setOf("owner-a", "owner-b"), checkpoints.map { it.ownerUserId }.toSet())
        coVerify(exactly = 1) { hydrate.rehydrateCatalogPage("owner-a", any(), any(), any()) }
        coVerify(exactly = 1) { hydrate.rehydrateCatalogPage("owner-b", any(), any(), any()) }
    }
}
