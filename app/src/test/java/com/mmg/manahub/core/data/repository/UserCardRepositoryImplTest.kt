package com.mmg.manahub.core.data.repository

import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.data.local.dao.UserCardDao
import com.mmg.manahub.core.data.remote.collection.CollectionRemoteDataSource
import com.mmg.manahub.util.TestFixtures
import io.github.jan.supabase.auth.Auth
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [UserCardRepositoryImpl].
 *
 * Focus areas:
 *  - addOrIncrement() decision tree: first insert vs. duplicate key increment
 *  - updateQuantity() guards (negative, zero → delete, positive → update)
 *  - Uniqueness key composition (scryfallId + foil + altArt + condition + language + wishlist)
 */
class UserCardRepositoryImplTest {

    // ── Mocks ─────────────────────────────────────────────────────────────────

    private val userCardDao      = mockk<UserCardDao>(relaxed = true)
    private val remoteDataSource = mockk<CollectionRemoteDataSource>(relaxed = true)
    private val prefsDataStore   = mockk<UserPreferencesDataStore>(relaxed = true)
    private val supabaseAuth     = mockk<Auth>(relaxed = true)

    private lateinit var repository: UserCardRepositoryImpl

    // ── Setup ─────────────────────────────────────────────────────────────────

    @Before
    fun setUp() {
        repository = UserCardRepositoryImpl(
            userCardDao      = userCardDao,
            remoteDataSource = remoteDataSource,
            prefsDataStore   = prefsDataStore,
            supabaseAuth     = supabaseAuth,
            ioDispatcher     = UnconfinedTestDispatcher(),
        )
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 1 — addOrIncrement: first-time insert path
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given card not yet in collection when addOrIncrement then insert is called`() = runTest {
        // Arrange
        val userCard = TestFixtures.buildUserCard(scryfallId = "id-001")
        coEvery { userCardDao.insert(any()) } returns 42L   // new row id

        // Act
        repository.addOrIncrement(userCard)

        // Assert: insert was attempted
        coVerify(exactly = 1) { userCardDao.insert(any()) }
        // incrementQuantityByUniqueKey must NOT be called when insert succeeds
        coVerify(exactly = 0) { userCardDao.incrementQuantityByUniqueKey(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `given card already in collection with same unique key when addOrIncrement then quantity is incremented`() = runTest {
        // Arrange — insert returns -1 (conflict on unique key)
        val userCard = TestFixtures.buildUserCard(scryfallId = "id-001", isFoil = false, condition = "NM", language = "en")
        coEvery { userCardDao.insert(any()) } returns -1L

        // Act
        repository.addOrIncrement(userCard)

        // Assert: increment path taken — no new row
        coVerify(exactly = 1) { userCardDao.insert(any()) }
        coVerify(exactly = 1) {
            userCardDao.incrementQuantityByUniqueKey(
                scryfallId       = "id-001",
                isFoil           = false,
                isAlternativeArt = false,
                condition        = "NM",
                language         = "en",
                isInWishlist     = false,
            )
        }
    }

    @Test
    fun `given foil copy already in collection when addOrIncrement foil then foil row is incremented`() = runTest {
        // Arrange
        val foilCard = TestFixtures.buildUserCard(scryfallId = "id-001", isFoil = true)
        coEvery { userCardDao.insert(any()) } returns -1L

        // Act
        repository.addOrIncrement(foilCard)

        // Assert: isFoil = true is forwarded to the increment query
        coVerify(exactly = 1) {
            userCardDao.incrementQuantityByUniqueKey(
                scryfallId       = "id-001",
                isFoil           = true,
                isAlternativeArt = false,
                condition        = "NM",
                language         = "en",
                isInWishlist     = false,
            )
        }
    }

    @Test
    fun `given foil and non-foil copies when addOrIncrement non-foil then foil row is NOT incremented`() = runTest {
        // Arrange — non-foil insert returns -1 (non-foil duplicate exists)
        val nonFoilCard = TestFixtures.buildUserCard(scryfallId = "id-001", isFoil = false)
        coEvery { userCardDao.insert(any()) } returns -1L

        // Act
        repository.addOrIncrement(nonFoilCard)

        // Assert: isFoil = false is forwarded — foil row must not be touched
        val capturedFoil = slot<Boolean>()
        coVerify(exactly = 1) {
            userCardDao.incrementQuantityByUniqueKey(
                scryfallId       = "id-001",
                isFoil           = capture(capturedFoil),
                isAlternativeArt = any(),
                condition        = any(),
                language         = any(),
                isInWishlist     = any(),
            )
        }
        assertEquals(false, capturedFoil.captured)
    }

    @Test
    fun `given wishlist entry already exists when addOrIncrement wishlist then wishlist row is incremented`() = runTest {
        // Arrange — wishlist card insert conflicts
        val wishlistCard = TestFixtures.buildUserCard(scryfallId = "id-001", isInWishlist = true)
        coEvery { userCardDao.insert(any()) } returns -1L

        // Act
        repository.addOrIncrement(wishlistCard)

        // Assert: isInWishlist = true is correctly forwarded
        coVerify(exactly = 1) {
            userCardDao.incrementQuantityByUniqueKey(
                scryfallId       = "id-001",
                isFoil           = false,
                isAlternativeArt = false,
                condition        = "NM",
                language         = "en",
                isInWishlist     = true,
            )
        }
    }

    @Test
    fun `given collection entry and wishlist entry for same card when addOrIncrement then they are treated as separate entries`() = runTest {
        // This test documents that isInWishlist is part of the unique key.
        // A collection copy and a wishlist copy are separate rows.

        // Arrange: insert of collection copy succeeds (new row)
        val collectionCard = TestFixtures.buildUserCard(scryfallId = "id-001", isInWishlist = false)
        coEvery { userCardDao.insert(any()) } returns 10L

        // Act
        repository.addOrIncrement(collectionCard)

        // Assert: a fresh insert was created — increment was NOT called
        coVerify(exactly = 0) { userCardDao.incrementQuantityByUniqueKey(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `given different conditions when addOrIncrement then each condition is a separate row`() = runTest {
        // Arrange — NM insert succeeds (new row, no conflict with existing LP row)
        val nmCard = TestFixtures.buildUserCard(scryfallId = "id-001", condition = "NM")
        coEvery { userCardDao.insert(any()) } returns 20L

        // Act
        repository.addOrIncrement(nmCard)

        // Assert: no increment — different condition = new row
        coVerify(exactly = 0) { userCardDao.incrementQuantityByUniqueKey(any(), any(), any(), any(), any(), any()) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 2 — updateQuantity guards
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given quantity zero when updateQuantity then card is deleted`() = runTest {
        // Arrange
        coEvery { userCardDao.deleteById(any()) } returns Unit

        // Act
        repository.updateQuantity(id = 1L, quantity = 0)

        // Assert
        coVerify(exactly = 1) { userCardDao.deleteById(1L) }
        coVerify(exactly = 0) { userCardDao.updateQuantity(any(), any()) }
    }

    @Test
    fun `given positive quantity when updateQuantity then quantity is updated without deleting`() = runTest {
        // Arrange
        coEvery { userCardDao.updateQuantity(any(), any()) } returns Unit

        // Act
        repository.updateQuantity(id = 1L, quantity = 5)

        // Assert
        coVerify(exactly = 1) { userCardDao.updateQuantity(1L, 5) }
        coVerify(exactly = 0) { userCardDao.deleteById(any()) }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `given negative quantity when updateQuantity then IllegalArgumentException is thrown`() = runTest {
        // Act — must throw before touching the DAO
        repository.updateQuantity(id = 1L, quantity = -1)
    }

    @Test
    fun `given quantity one when updateQuantity then single copy remains`() = runTest {
        // Arrange
        coEvery { userCardDao.updateQuantity(any(), any()) } returns Unit

        // Act
        repository.updateQuantity(id = 1L, quantity = 1)

        // Assert: quantity = 1 is a valid update, not a deletion
        coVerify(exactly = 1) { userCardDao.updateQuantity(1L, 1) }
        coVerify(exactly = 0) { userCardDao.deleteById(any()) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 3 — deleteCard delegates correctly
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given valid id when deleteCard then deleteById is called with the correct id`() = runTest {
        // Arrange
        coEvery { userCardDao.deleteById(any()) } returns Unit

        // Act
        repository.deleteCard(id = 99L)

        // Assert
        coVerify(exactly = 1) { userCardDao.deleteById(99L) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 4 — incrementQuantity delegates correctly
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given valid id when incrementQuantity then dao incrementQuantity is called`() = runTest {
        // Arrange
        coEvery { userCardDao.incrementQuantity(any()) } returns Unit

        // Act
        repository.incrementQuantity(id = 7L)

        // Assert
        coVerify(exactly = 1) { userCardDao.incrementQuantity(7L) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 5 — getScryfallIds delegation
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given collection has entries when getScryfallIds then returns distinct ids`() = runTest {
        // Arrange
        val expected = listOf("id-001", "id-002", "id-003")
        coEvery { userCardDao.getAllScryfallIds() } returns expected

        // Act
        val result = repository.getScryfallIds()

        // Assert
        assertEquals(expected, result)
    }

    @Test
    fun `given empty collection when getScryfallIds then returns empty list`() = runTest {
        // Arrange
        coEvery { userCardDao.getAllScryfallIds() } returns emptyList()

        // Act
        val result = repository.getScryfallIds()

        // Assert
        assertEquals(emptyList<String>(), result)
    }
}
