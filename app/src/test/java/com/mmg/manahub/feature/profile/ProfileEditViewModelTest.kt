package com.mmg.manahub.feature.profile

import app.cash.turbine.test
import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.data.remote.ScryfallRemoteDataSource
import com.mmg.manahub.core.data.remote.dto.CardDto
import com.mmg.manahub.core.data.remote.dto.ImageUrisDto
import com.mmg.manahub.core.data.remote.dto.SearchResultDto
import com.mmg.manahub.core.domain.auth.AuthError
import com.mmg.manahub.core.domain.auth.AuthRepository
import com.mmg.manahub.core.domain.auth.AuthResult
import com.mmg.manahub.core.domain.auth.AuthUser
import com.mmg.manahub.core.domain.auth.SessionState
import com.mmg.manahub.feature.profile.presentation.ProfileEditViewModel
import io.ktor.client.plugins.ResponseException
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileEditViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val scryfall = mockk<ScryfallRemoteDataSource>()
    private val prefs = mockk<UserPreferencesDataStore>(relaxed = true)
    private val authRepository = mockk<AuthRepository>(relaxed = true)
    private val crashReporter = mockk<CrashReporter>(relaxed = true)

    private val playerNameFlow = MutableStateFlow("Wizard")
    private val avatarUrlFlow = MutableStateFlow<String?>(null)
    private val sessionFlow = MutableStateFlow<SessionState>(SessionState.Unauthenticated)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { prefs.playerNameFlow } returns playerNameFlow
        every { prefs.avatarUrlFlow } returns avatarUrlFlow
        every { authRepository.sessionState } returns sessionFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun card(id: String, name: String = id): CardDto = mockk(relaxed = true) {
        every { this@mockk.name } returns name
        every { imageUris } returns ImageUrisDto(artCrop = "https://art/$id")
        every { cardFaces } returns null
        every { colors } returns emptyList()
    }

    private fun page(vararg ids: String, hasMore: Boolean) =
        SearchResultDto(totalCards = ids.size, hasMore = hasMore, data = ids.map { card(it) })

    private fun notFound(): ResponseException {
        val response = mockk<HttpResponse>(relaxed = true) {
            every { status } returns HttpStatusCode.NotFound
        }
        return ResponseException(response, "")
    }

    private fun signedIn() {
        sessionFlow.value = SessionState.Authenticated(
            AuthUser(id = "u1", email = null, nickname = "Wizard", gameTag = "#A", avatarUrl = null, provider = "email"),
        )
    }

    private fun build() = ProfileEditViewModel(scryfall, prefs, authRepository, crashReporter)

    // ── P-03 retry / failed page ─────────────────────────────────────────────

    @Test
    fun `an offline first load can be retried`() = runTest {
        coEvery { scryfall.searchPlaneswalkerArts(any(), 1) } throws IOException("offline") andThen page("a", hasMore = false)
        val vm = build()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.loadFailed)

        vm.retry()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.loadFailed)
        assertEquals(listOf("https://art/a"), vm.uiState.value.artworks.map { it.artCropUrl })
        coVerify(exactly = 2) { scryfall.searchPlaneswalkerArts(any(), 1) }
    }

    @Test
    fun `a failed later page keeps the current page and retry requests the same page`() = runTest {
        coEvery { scryfall.searchPlaneswalkerArts(any(), 1) } returns page("a", "b", hasMore = true)
        coEvery { scryfall.searchPlaneswalkerArts(any(), 2) } throws IOException("offline") andThen page("c", hasMore = false)
        val vm = build()
        advanceUntilIdle()

        vm.loadNextPage()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.appendFailed)
        assertEquals(1, vm.uiState.value.currentPage)

        vm.loadNextPage()
        advanceUntilIdle()
        coVerify(exactly = 1) { scryfall.searchPlaneswalkerArts(any(), 2) }

        vm.retry()
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.currentPage)
        assertEquals(listOf("a", "b", "c").map { "https://art/$it" }, vm.uiState.value.artworks.map { it.artCropUrl })
    }

    @Test
    fun `a search with no results shows the empty state instead of an error`() = runTest {
        coEvery { scryfall.searchPlaneswalkerArts(any(), 1) } throws notFound()
        val vm = build()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.loadFailed)
        assertTrue(vm.uiState.value.artworks.isEmpty())
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun `opening the sheet retries a failed first load and drops an abandoned draft`() = runTest {
        coEvery { scryfall.searchPlaneswalkerArts(any(), 1) } throws IOException("offline") andThen page("a", hasMore = false)
        val vm = build()
        advanceUntilIdle()
        vm.onNameChange("Draft")
        vm.selectArt("https://art/x")

        vm.onSheetOpened()
        advanceUntilIdle()

        assertEquals("Wizard", vm.uiState.value.pendingName)
        assertEquals(null, vm.uiState.value.pendingSelection)
        assertEquals(1, vm.uiState.value.artworks.size)
    }

    // ── P-04 filter race ─────────────────────────────────────────────────────

    @Test
    fun `a filter change during a page load keeps only the new filter's unique results`() = runTest {
        val slowPage = CompletableDeferred<SearchResultDto>()
        coEvery { scryfall.searchPlaneswalkerArts("t:planeswalker unique:art", 1) } returns page("a", "b", hasMore = true)
        coEvery { scryfall.searchPlaneswalkerArts("t:planeswalker unique:art", 2) } coAnswers { slowPage.await() }
        coEvery { scryfall.searchPlaneswalkerArts("t:planeswalker unique:art c:w", 1) } returns page("w1", "b", hasMore = false)
        val vm = build()
        advanceUntilIdle()

        vm.loadNextPage()
        advanceUntilIdle()
        vm.toggleColorFilter("W")
        advanceUntilIdle()
        slowPage.complete(page("b", "c", hasMore = false))
        advanceUntilIdle()

        val urls = vm.uiState.value.artworks.map { it.artCropUrl }
        assertEquals(listOf("https://art/w1", "https://art/b"), urls)
        assertEquals(urls.distinct(), urls)
    }

    // ── P-10 name draft ──────────────────────────────────────────────────────

    @Test
    fun `a stored-name change does not overwrite a name the user is editing`() = runTest {
        coEvery { scryfall.searchPlaneswalkerArts(any(), any()) } returns page(hasMore = false)
        val vm = build()
        advanceUntilIdle()

        vm.onNameChange("New")
        playerNameFlow.value = "Server"
        advanceUntilIdle()

        assertEquals("New", vm.uiState.value.pendingName)
        assertEquals("Server", vm.uiState.value.currentName)
    }

    @Test
    fun `an untouched name follows the stored name`() = runTest {
        coEvery { scryfall.searchPlaneswalkerArts(any(), any()) } returns page(hasMore = false)
        val vm = build()
        advanceUntilIdle()

        playerNameFlow.value = "Server"
        advanceUntilIdle()

        assertEquals("Server", vm.uiState.value.pendingName)
    }

    // ── P-02 / P-12 save paths ───────────────────────────────────────────────

    @Test
    fun `an invalid nickname cannot be saved`() = runTest {
        coEvery { scryfall.searchPlaneswalkerArts(any(), any()) } returns page(hasMore = false)
        val vm = build()
        advanceUntilIdle()

        vm.onNameChange("José")
        vm.confirmChanges()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.canSave)
        coVerify(exactly = 0) { prefs.savePlayerName(any()) }
    }

    @Test
    fun `a signed-in rename rejected by the server keeps the local name and reports the reason`() = runTest {
        signedIn()
        coEvery { scryfall.searchPlaneswalkerArts(any(), any()) } returns page(hasMore = false)
        coEvery { authRepository.updateNickname("Jace") } returns AuthResult.Error(AuthError.NicknameInappropriate)
        val vm = build()
        advanceUntilIdle()

        vm.events.test {
            vm.onNameChange("  Jace ")
            vm.confirmChanges()
            advanceUntilIdle()
            assertEquals(
                ProfileEditViewModel.Event.SaveFailed(ProfileEditViewModel.SaveFailure.NICKNAME_INAPPROPRIATE),
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 0) { prefs.savePlayerName(any()) }
        assertFalse(vm.uiState.value.isSaving)
        assertEquals("  Jace ", vm.uiState.value.pendingName)
    }

    @Test
    fun `a signed-in rename writes locally only after the server accepts the trimmed name`() = runTest {
        signedIn()
        coEvery { scryfall.searchPlaneswalkerArts(any(), any()) } returns page(hasMore = false)
        coEvery { authRepository.updateNickname("Jace") } returns AuthResult.Success(mockk(relaxed = true))
        val vm = build()
        advanceUntilIdle()

        vm.events.test {
            vm.onNameChange("Jace ")
            vm.confirmChanges()
            advanceUntilIdle()
            assertEquals(ProfileEditViewModel.Event.Saved, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 1) { prefs.savePlayerName("Jace") }
    }

    @Test
    fun `a guest rename is saved locally without a server call`() = runTest {
        coEvery { scryfall.searchPlaneswalkerArts(any(), any()) } returns page(hasMore = false)
        val vm = build()
        advanceUntilIdle()

        vm.onNameChange("Chandra")
        vm.confirmChanges()
        advanceUntilIdle()

        coVerify(exactly = 1) { prefs.savePlayerName("Chandra") }
        coVerify(exactly = 0) { authRepository.updateNickname(any()) }
        coVerify(exactly = 0) { authRepository.updateAvatarUrl(any()) }
    }

    @Test
    fun `a rejected avatar change is not written locally`() = runTest {
        signedIn()
        coEvery { scryfall.searchPlaneswalkerArts(any(), any()) } returns page(hasMore = false)
        coEvery { authRepository.updateAvatarUrl("https://art/a") } returns AuthResult.Error(AuthError.NetworkError)
        val vm = build()
        advanceUntilIdle()

        vm.selectArt("https://art/a")
        vm.confirmChanges()
        advanceUntilIdle()

        coVerify(exactly = 0) { prefs.saveAvatarUrl(any()) }
        assertEquals("https://art/a", vm.uiState.value.pendingSelection)
    }

    @Test
    fun `removing the avatar reaches the server before the local value is cleared`() = runTest {
        signedIn()
        coEvery { scryfall.searchPlaneswalkerArts(any(), any()) } returns page(hasMore = false)
        coEvery { authRepository.updateAvatarUrl(null) } returns AuthResult.Success(Unit)
        val vm = build()
        advanceUntilIdle()

        vm.removeAvatar()
        advanceUntilIdle()

        coVerify(exactly = 1) { authRepository.updateAvatarUrl(null) }
        coVerify(exactly = 1) { prefs.saveAvatarUrl(null) }
    }

    @Test
    fun `a failed avatar removal keeps the avatar and reports it`() = runTest {
        signedIn()
        coEvery { scryfall.searchPlaneswalkerArts(any(), any()) } returns page(hasMore = false)
        coEvery { authRepository.updateAvatarUrl(null) } returns AuthResult.Error(AuthError.NetworkError)
        val vm = build()
        advanceUntilIdle()

        vm.events.test {
            vm.removeAvatar()
            advanceUntilIdle()
            assertEquals(ProfileEditViewModel.Event.AvatarRemoveFailed, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 0) { prefs.saveAvatarUrl(any()) }
    }
}
