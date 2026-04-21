package com.mmg.manahub.feature.auth.data.repository

import app.cash.turbine.test
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.feature.auth.data.remote.SupabaseUserProfileService
import com.mmg.manahub.feature.auth.data.remote.UpdateNicknameDto
import com.mmg.manahub.feature.auth.data.remote.UserProfileDataSource
import com.mmg.manahub.feature.auth.data.remote.UserProfileRetrofitDto
import com.mmg.manahub.feature.auth.domain.model.AuthError
import com.mmg.manahub.feature.auth.domain.model.AuthResult
import com.mmg.manahub.feature.auth.domain.model.AuthUser
import com.mmg.manahub.feature.auth.domain.model.SessionState
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.exceptions.RestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.IOException

/**
 * Unit tests for [AuthRepositoryImpl].
 *
 * Strategy:
 * - [Auth] is mocked with MockK.
 * - [UserProfileDataSource] is mocked to isolate repository logic.
 * - [SupabaseUserProfileService] is mocked for RPC-level tests (updateNickname, deleteAccount).
 * - [UserPreferencesDataStore] is mocked (relaxed) to verify DataStore sync calls.
 * - [UnconfinedTestDispatcher] is used so withContext(ioDispatcher) runs synchronously,
 *   avoiding any need for advanceUntilIdle.
 * - Supabase's [Auth.sessionStatus] is backed by a [MutableStateFlow] so we can
 *   control emissions in Flow tests.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthRepositoryImplTest {

    // ── Test dispatcher ───────────────────────────────────────────────────────

    private val testDispatcher = UnconfinedTestDispatcher()

    // ── Mocks ─────────────────────────────────────────────────────────────────

    private val supabaseAuth                = mockk<Auth>()
    private val userProfileDataSource       = mockk<UserProfileDataSource>(relaxed = true)
    private val supabaseUserProfileService  = mockk<SupabaseUserProfileService>(relaxed = true)
    private val userPreferencesDataStore    = mockk<UserPreferencesDataStore>(relaxed = true)

    // Controls the Auth.sessionStatus Flow across tests
    private val sessionStatusFlow = MutableStateFlow<SessionStatus>(SessionStatus.Initializing)

    // ── SUT ───────────────────────────────────────────────────────────────────

    private lateinit var repository: AuthRepositoryImpl

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Builds a minimal [UserInfo] mock with the fields referenced by
     * AuthRepositoryImpl.toAuthUser(). Relaxed mocks return null for
     * nullable fields automatically; we only set what the mapper actually reads.
     */
    private fun buildUserInfoMock(
        id: String = "user-uuid-001",
        email: String? = "test@example.com",
        provider: String = "email",
        nicknameMetadata: String? = null,
        gameTagMetadata: String? = null,
        avatarMetadata: String? = null,
        fullNameMetadata: String? = null,
    ): UserInfo = mockk(relaxed = true) {
        every { this@mockk.id }    returns id
        every { this@mockk.email } returns email

        val metadataMap = buildMap {
            nicknameMetadata?.let { put("nickname", JsonPrimitive(it)) }
            gameTagMetadata?.let  { put("game_tag", JsonPrimitive(it)) }
            avatarMetadata?.let   { put("avatar_url", JsonPrimitive(it)) }
            fullNameMetadata?.let { put("full_name", JsonPrimitive(it)) }
        }

        every { this@mockk.userMetadata } returns
                if (metadataMap.isEmpty()) null
                else buildJsonObject { metadataMap.forEach { (k, v) -> put(k, v) } }

        every { this@mockk.identities } returns listOf(
            mockk(relaxed = true) { every { this@mockk.provider } returns provider }
        )
    }

    private fun buildExpectedAuthUser(
        id: String = "user-uuid-001",
        email: String? = "test@example.com",
        provider: String = "email",
        nickname: String? = null,  // null → falls back to email prefix in toAuthUser()
    ) = AuthUser(
        id        = id,
        email     = email,
        nickname  = nickname ?: email?.substringBefore('@'),
        gameTag   = null,
        avatarUrl = null,
        provider  = provider,
    )

    /** Builds a successful Retrofit [Response] with no body. */
    private fun successResponse(): Response<Unit> = Response.success(Unit)

    /** Builds a failed Retrofit [Response] with the given HTTP status code. */
    private fun errorResponse(code: Int): Response<Unit> =
        Response.error(code, "".toResponseBody(null))

    // ── Setup ─────────────────────────────────────────────────────────────────

    @Before
    fun setUp() {
        every { supabaseAuth.sessionStatus } returns sessionStatusFlow

        // Default stub: upsertUserProfile returns the user unchanged (non-blocking default).
        coEvery { userProfileDataSource.upsertUserProfile(any()) } answers {
            firstArg<AuthUser>()
        }

        // Default stub: fetchUserProfile returns null (no enrichment by default).
        coEvery { userProfileDataSource.fetchUserProfile(any()) } returns null

        // Default stub: updateNickname returns success.
        coEvery { supabaseUserProfileService.updateNickname(any()) } returns successResponse()

        // Default stub: deleteCurrentUser returns success.
        coEvery { supabaseUserProfileService.deleteCurrentUser() } returns successResponse()

        repository = AuthRepositoryImpl(
            supabaseAuth               = supabaseAuth,
            userProfileDataSource      = userProfileDataSource,
            supabaseUserProfileService = supabaseUserProfileService,
            userPreferencesDataStore   = userPreferencesDataStore,
            ioDispatcher               = testDispatcher,
        )
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 1 — signInWithEmail
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given valid credentials when signInWithEmail then returns Success with AuthUser`() = runTest {
        val userInfoMock = buildUserInfoMock()
        coEvery { supabaseAuth.signInWith(any(), any<suspend io.github.jan.supabase.auth.providers.builtin.Email.Config.() -> Unit>()) } just Runs
        every { supabaseAuth.currentUserOrNull() } returns userInfoMock

        val result = repository.signInWithEmail("test@example.com", "password123")

        assertTrue(result is AuthResult.Success)
        val user = (result as AuthResult.Success).data
        assertEquals("user-uuid-001", user.id)
        assertEquals("test@example.com", user.email)
        assertEquals("email", user.provider)
    }

    @Test
    fun `given valid credentials when signInWithEmail then syncs to DataStore`() = runTest {
        val userInfoMock = buildUserInfoMock(nicknameMetadata = "TestUser")
        coEvery { supabaseAuth.signInWith(any(), any<suspend io.github.jan.supabase.auth.providers.builtin.Email.Config.() -> Unit>()) } just Runs
        every { supabaseAuth.currentUserOrNull() } returns userInfoMock

        repository.signInWithEmail("test@example.com", "password123")

        coVerify(atLeast = 1) { userPreferencesDataStore.savePlayerName(any()) }
    }

    @Test
    fun `given server returns 400 when signInWithEmail then returns Error with InvalidCredentials`() = runTest {
        val restException = RestException(error = "invalid_grant", description = "Invalid credentials", statusCode = 400)
        coEvery { supabaseAuth.signInWith(any(), any<suspend io.github.jan.supabase.auth.providers.builtin.Email.Config.() -> Unit>()) } throws restException

        val result = repository.signInWithEmail("wrong@example.com", "wrongpassword")

        assertTrue(result is AuthResult.Error)
        assertEquals(AuthError.InvalidCredentials, (result as AuthResult.Error).error)
    }

    @Test
    fun `given server returns 404 when signInWithEmail then returns Error with UserNotFound`() = runTest {
        val restException = RestException(error = "not_found", description = "User not found", statusCode = 404)
        coEvery { supabaseAuth.signInWith(any(), any<suspend io.github.jan.supabase.auth.providers.builtin.Email.Config.() -> Unit>()) } throws restException

        val result = repository.signInWithEmail("ghost@example.com", "password123")

        assertTrue(result is AuthResult.Error)
        assertEquals(AuthError.UserNotFound, (result as AuthResult.Error).error)
    }

    @Test
    fun `given server returns 401 when signInWithEmail then returns Error with SessionExpired`() = runTest {
        val restException = RestException(error = "unauthorized", description = "Unauthorized", statusCode = 401)
        coEvery { supabaseAuth.signInWith(any(), any<suspend io.github.jan.supabase.auth.providers.builtin.Email.Config.() -> Unit>()) } throws restException

        val result = repository.signInWithEmail("test@example.com", "password123")

        assertTrue(result is AuthResult.Error)
        assertEquals(AuthError.SessionExpired, (result as AuthResult.Error).error)
    }

    @Test
    fun `given server returns 500 when signInWithEmail then returns Error with Unknown`() = runTest {
        val restException = RestException(error = "internal_error", description = "Internal Server Error", statusCode = 500)
        coEvery { supabaseAuth.signInWith(any(), any<suspend io.github.jan.supabase.auth.providers.builtin.Email.Config.() -> Unit>()) } throws restException

        val result = repository.signInWithEmail("test@example.com", "password123")

        assertTrue(result is AuthResult.Error)
        assertTrue((result as AuthResult.Error).error is AuthError.Unknown)
    }

    @Test
    fun `given network failure when signInWithEmail then returns Error with NetworkError`() = runTest {
        coEvery { supabaseAuth.signInWith(any(), any<suspend io.github.jan.supabase.auth.providers.builtin.Email.Config.() -> Unit>()) } throws IOException("No network")

        val result = repository.signInWithEmail("test@example.com", "password123")

        assertTrue(result is AuthResult.Error)
        assertEquals(AuthError.NetworkError, (result as AuthResult.Error).error)
    }

    @Test
    fun `given HTTP timeout when signInWithEmail then returns Error with NetworkError`() = runTest {
        coEvery { supabaseAuth.signInWith(any(), any<suspend io.github.jan.supabase.auth.providers.builtin.Email.Config.() -> Unit>()) } throws HttpRequestTimeoutException("https://supabase.io", 30_000L)

        val result = repository.signInWithEmail("test@example.com", "password123")

        assertTrue(result is AuthResult.Error)
        assertEquals(AuthError.NetworkError, (result as AuthResult.Error).error)
    }

    @Test
    fun `given unexpected exception when signInWithEmail then returns Error with Unknown`() = runTest {
        coEvery { supabaseAuth.signInWith(any(), any<suspend io.github.jan.supabase.auth.providers.builtin.Email.Config.() -> Unit>()) } throws RuntimeException("Unexpected failure")

        val result = repository.signInWithEmail("test@example.com", "password123")

        assertTrue(result is AuthResult.Error)
        assertTrue((result as AuthResult.Error).error is AuthError.Unknown)
    }

    @Test
    fun `given user_profiles has richer data when signInWithEmail then returns enriched AuthUser`() = runTest {
        val userInfoMock = buildUserInfoMock()
        coEvery { supabaseAuth.signInWith(any(), any<suspend io.github.jan.supabase.auth.providers.builtin.Email.Config.() -> Unit>()) } just Runs
        every { supabaseAuth.currentUserOrNull() } returns userInfoMock
        coEvery { userProfileDataSource.fetchUserProfile("user-uuid-001") } returns
            com.mmg.manahub.feature.auth.data.remote.UserProfileDto(
                id = "user-uuid-001",
                nickname = "ServerNick",
                gameTag = "#ABCD",
                avatarUrl = "https://example.com/avatar.jpg",
                provider = "email",
            )

        val result = repository.signInWithEmail("test@example.com", "password123")

        assertTrue(result is AuthResult.Success)
        val user = (result as AuthResult.Success).data
        assertEquals("ServerNick", user.nickname)
        assertEquals("#ABCD", user.gameTag)
        assertEquals("https://example.com/avatar.jpg", user.avatarUrl)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 2 — signUpWithEmail
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given valid new user when signUpWithEmail then calls upsertUserProfile and returns Success`() = runTest {
        val userInfoMock = buildUserInfoMock()
        val expectedUser = buildExpectedAuthUser()
        coEvery { supabaseAuth.signUpWith(any(), any<suspend io.github.jan.supabase.auth.providers.builtin.Email.Config.() -> Unit>()) } just Runs
        every { supabaseAuth.currentUserOrNull() } returns userInfoMock
        coEvery { userProfileDataSource.upsertUserProfile(any()) } returns expectedUser

        val result = repository.signUpWithEmail("new@example.com", "password123", "Hero")

        assertTrue(result is AuthResult.Success)
        coVerify(exactly = 1) { userProfileDataSource.upsertUserProfile(any()) }
    }

    @Test
    fun `given Supabase requires email confirmation when signUpWithEmail then returns EmailConfirmationRequired`() = runTest {
        // currentUserOrNull() returns null when email confirmation is pending
        coEvery { supabaseAuth.signUpWith(any(), any<suspend io.github.jan.supabase.auth.providers.builtin.Email.Config.() -> Unit>()) } just Runs
        every { supabaseAuth.currentUserOrNull() } returns null

        val result = repository.signUpWithEmail("confirm@example.com", "password123", "Hero")

        assertTrue(result is AuthResult.Error)
        assertEquals(AuthError.EmailConfirmationRequired, (result as AuthResult.Error).error)
        // Profile upsert must NOT fire when the user session is not yet active
        coVerify(exactly = 0) { userProfileDataSource.upsertUserProfile(any()) }
    }

    @Test
    fun `given server returns 422 when signUpWithEmail then returns Error with EmailAlreadyInUse`() = runTest {
        val restException = RestException(error = "user_already_exists", description = "Email already in use", statusCode = 422)
        coEvery { supabaseAuth.signUpWith(any(), any<suspend io.github.jan.supabase.auth.providers.builtin.Email.Config.() -> Unit>()) } throws restException

        val result = repository.signUpWithEmail("existing@example.com", "password123", "Hero")

        assertTrue(result is AuthResult.Error)
        assertEquals(AuthError.EmailAlreadyInUse, (result as AuthResult.Error).error)
    }

    @Test
    fun `given network failure when signUpWithEmail then returns Error with NetworkError`() = runTest {
        coEvery { supabaseAuth.signUpWith(any(), any<suspend io.github.jan.supabase.auth.providers.builtin.Email.Config.() -> Unit>()) } throws IOException("Network down")

        val result = repository.signUpWithEmail("new@example.com", "password123", "Hero")

        assertTrue(result is AuthResult.Error)
        assertEquals(AuthError.NetworkError, (result as AuthResult.Error).error)
    }

    @Test
    fun `given nickname service fails during signUpWithEmail then sign-up still succeeds`() = runTest {
        // The nickname RPC is non-fatal: a failure should not prevent the overall sign-up
        val userInfoMock = buildUserInfoMock()
        coEvery { supabaseAuth.signUpWith(any(), any<suspend io.github.jan.supabase.auth.providers.builtin.Email.Config.() -> Unit>()) } just Runs
        every { supabaseAuth.currentUserOrNull() } returns userInfoMock
        coEvery { supabaseUserProfileService.updateNickname(any()) } throws IOException("RPC unreachable")

        val repo = AuthRepositoryImpl(
            supabaseAuth               = supabaseAuth,
            userProfileDataSource      = userProfileDataSource,
            supabaseUserProfileService = supabaseUserProfileService,
            userPreferencesDataStore   = userPreferencesDataStore,
            ioDispatcher               = testDispatcher,
        )

        val result = repo.signUpWithEmail("new@example.com", "password123", "Hero")

        // Sign-up is still a success despite the RPC failure
        assertTrue(result is AuthResult.Success)
        // The user is persisted via upsertUserProfile even if nickname update failed
        coVerify(exactly = 1) { userProfileDataSource.upsertUserProfile(any()) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 3 — signInWithGoogleIdToken
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given valid Google ID token when signInWithGoogleIdToken then returns Success and calls upsertUserProfile`() = runTest {
        val userInfoMock = buildUserInfoMock(provider = "google")
        val expectedUser = buildExpectedAuthUser(provider = "google")
        coEvery { supabaseAuth.signInWith(any(), any<suspend io.github.jan.supabase.auth.providers.builtin.IDToken.Config.() -> Unit>()) } just Runs
        every { supabaseAuth.currentUserOrNull() } returns userInfoMock
        coEvery { userProfileDataSource.upsertUserProfile(any()) } returns expectedUser

        val result = repository.signInWithGoogleIdToken("valid-google-id-token", "raw-nonce-abc")

        assertTrue(result is AuthResult.Success)
        assertEquals("google", (result as AuthResult.Success).data.provider)
        coVerify(exactly = 1) { userProfileDataSource.upsertUserProfile(any()) }
    }

    @Test
    fun `given Google user with full_name metadata and no nickname when signInWithGoogleIdToken then nickname is auto-set from full_name`() = runTest {
        // When a Google user signs in for the first time, toAuthUser() returns nickname = null
        // because the metadata key "nickname" is not set yet. The repository should then
        // auto-populate it from the "full_name" metadata field.
        val userInfoMock = buildUserInfoMock(
            provider          = "google",
            nicknameMetadata  = null,          // no nickname yet
            fullNameMetadata  = "Gandalf Grey", // Google display name
        )
        coEvery { supabaseAuth.signInWith(any(), any<suspend io.github.jan.supabase.auth.providers.builtin.IDToken.Config.() -> Unit>()) } just Runs
        every { supabaseAuth.currentUserOrNull() } returns userInfoMock
        coEvery { supabaseUserProfileService.updateNickname(any()) } returns successResponse()

        val result = repository.signInWithGoogleIdToken("google-token", "raw-nonce")

        assertTrue(result is AuthResult.Success)
        // The auto-generated nickname should start with the Google display name characters
        val nickname = (result as AuthResult.Success).data.nickname
        assertTrue("Expected nickname derived from full_name 'Gandalf Grey', got: $nickname",
            nickname == "Gandalf Grey" || nickname == "Gandalf Grey".take(30))
    }

    @Test
    fun `given Google user with nickname already set when signInWithGoogleIdToken then nickname RPC is NOT called`() = runTest {
        // Returning users already have a nickname — the auto-set logic must be skipped
        val userInfoMock = buildUserInfoMock(
            provider         = "google",
            nicknameMetadata = "ExistingNick",
        )
        coEvery { supabaseAuth.signInWith(any(), any<suspend io.github.jan.supabase.auth.providers.builtin.IDToken.Config.() -> Unit>()) } just Runs
        every { supabaseAuth.currentUserOrNull() } returns userInfoMock

        repository.signInWithGoogleIdToken("google-token", "raw-nonce")

        // updateNickname service must NOT be called when nickname is already set
        coVerify(exactly = 0) { supabaseUserProfileService.updateNickname(any()) }
        // upsertUserProfile is still called
        coVerify(exactly = 1) { userProfileDataSource.upsertUserProfile(any()) }
    }

    @Test
    fun `given invalid Google token when signInWithGoogleIdToken then returns Error with mapped AuthError`() = runTest {
        val restException = RestException(error = "invalid_token", description = "Invalid ID token", statusCode = 400)
        coEvery { supabaseAuth.signInWith(any(), any<suspend io.github.jan.supabase.auth.providers.builtin.IDToken.Config.() -> Unit>()) } throws restException

        val result = repository.signInWithGoogleIdToken("invalid-token", "raw-nonce-abc")

        assertTrue(result is AuthResult.Error)
        assertEquals(AuthError.InvalidCredentials, (result as AuthResult.Error).error)
    }

    @Test
    fun `given network failure when signInWithGoogleIdToken then returns Error with NetworkError`() = runTest {
        coEvery { supabaseAuth.signInWith(any(), any<suspend io.github.jan.supabase.auth.providers.builtin.IDToken.Config.() -> Unit>()) } throws IOException("No connectivity")

        val result = repository.signInWithGoogleIdToken("some-token", "some-nonce")

        assertTrue(result is AuthResult.Error)
        assertEquals(AuthError.NetworkError, (result as AuthResult.Error).error)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 4 — signOut
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given authenticated user when signOut then returns Success and calls supabaseAuth_signOut`() = runTest {
        coEvery { supabaseAuth.signOut(any()) } just Runs

        val result = repository.signOut()

        assertTrue(result is AuthResult.Success)
        assertEquals(Unit, (result as AuthResult.Success).data)
        coVerify(exactly = 1) { supabaseAuth.signOut(any()) }
    }

    @Test
    fun `given signOut throws when signOut then returns Error`() = runTest {
        coEvery { supabaseAuth.signOut(any()) } throws RuntimeException("Sign-out failed")

        val result = repository.signOut()

        assertTrue(result is AuthResult.Error)
        assertTrue((result as AuthResult.Error).error is AuthError.Unknown)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 5 — deleteAccount
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given authenticated user when deleteAccount then calls service deleteCurrentUser and signOut and returns Success`() = runTest {
        coEvery { supabaseAuth.signOut(any()) } just Runs

        val result = repository.deleteAccount()

        assertTrue(result is AuthResult.Success)
        coVerify(exactly = 1) { supabaseUserProfileService.deleteCurrentUser() }
        coVerify(exactly = 1) { supabaseAuth.signOut(any()) }
    }

    @Test
    fun `given deleteCurrentUser service throws when deleteAccount then returns Error and does NOT call signOut`() = runTest {
        coEvery { supabaseUserProfileService.deleteCurrentUser() } throws RuntimeException("RPC failed")

        val result = repository.deleteAccount()

        assertTrue(result is AuthResult.Error)
        // signOut must NOT be called if the RPC step fails — user account was not deleted
        coVerify(exactly = 0) { supabaseAuth.signOut(any()) }
    }

    @Test
    fun `given deleteCurrentUser returns HTTP error when deleteAccount then returns Error`() = runTest {
        coEvery { supabaseUserProfileService.deleteCurrentUser() } returns errorResponse(500)

        val result = repository.deleteAccount()

        assertTrue(result is AuthResult.Error)
    }

    @Test
    fun `given network failure when deleteAccount then returns Error with NetworkError`() = runTest {
        coEvery { supabaseUserProfileService.deleteCurrentUser() } throws IOException("Offline")

        val result = repository.deleteAccount()

        assertTrue(result is AuthResult.Error)
        assertEquals(AuthError.NetworkError, (result as AuthResult.Error).error)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 6 — resetPassword
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given valid email when resetPassword then returns Success`() = runTest {
        coEvery { supabaseAuth.resetPasswordForEmail(any(), any()) } just Runs

        val result = repository.resetPassword("user@example.com")

        assertTrue(result is AuthResult.Success)
        assertEquals(Unit, (result as AuthResult.Success).data)
    }

    @Test
    fun `given unknown email when resetPassword and server returns 404 then returns Error with UserNotFound`() = runTest {
        val restException = RestException(error = "not_found", description = "User not found", statusCode = 404)
        coEvery { supabaseAuth.resetPasswordForEmail(any(), any()) } throws restException

        val result = repository.resetPassword("ghost@example.com")

        assertTrue(result is AuthResult.Error)
        assertEquals(AuthError.UserNotFound, (result as AuthResult.Error).error)
    }

    @Test
    fun `given network failure when resetPassword then returns Error with NetworkError`() = runTest {
        coEvery { supabaseAuth.resetPasswordForEmail(any(), any()) } throws IOException("Offline")

        val result = repository.resetPassword("user@example.com")

        assertTrue(result is AuthResult.Error)
        assertEquals(AuthError.NetworkError, (result as AuthResult.Error).error)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 7 — sessionState Flow
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given SessionStatus Initializing when sessionState collected then emits SessionState Loading`() = runTest {
        repository.sessionState.test {
            assertEquals(SessionState.Loading, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given SessionStatus NotAuthenticated when sessionState collected then emits Unauthenticated`() = runTest {
        sessionStatusFlow.value = SessionStatus.NotAuthenticated(isSignOut = false)

        repository.sessionState.test {
            assertEquals(SessionState.Unauthenticated, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given SessionStatus NotAuthenticated after sign-out when sessionState collected then emits Unauthenticated`() = runTest {
        // isSignOut = true covers the explicit sign-out path
        sessionStatusFlow.value = SessionStatus.NotAuthenticated(isSignOut = true)

        repository.sessionState.test {
            assertEquals(SessionState.Unauthenticated, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given SessionStatus Authenticated when sessionState collected then emits Authenticated with AuthUser`() = runTest {
        val userInfoMock = buildUserInfoMock()
        val sessionMock = mockk<io.github.jan.supabase.auth.user.UserSession>(relaxed = true) {
            every { user } returns userInfoMock
        }
        sessionStatusFlow.value = SessionStatus.Authenticated(sessionMock)

        repository.sessionState.test {
            // Fast first emit (from auth metadata)
            val state = awaitItem()
            assertTrue(state is SessionState.Authenticated)
            assertEquals("user-uuid-001", (state as SessionState.Authenticated).user.id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given SessionStatus changes from Initializing to NotAuthenticated when sessionState observed then emits Loading then Unauthenticated`() = runTest {
        sessionStatusFlow.value = SessionStatus.Initializing

        repository.sessionState.test {
            assertEquals(SessionState.Loading, awaitItem())
            sessionStatusFlow.value = SessionStatus.NotAuthenticated(isSignOut = false)
            assertEquals(SessionState.Unauthenticated, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given SessionStatus RefreshFailure when sessionState collected then emits Unauthenticated`() = runTest {
        sessionStatusFlow.value = SessionStatus.RefreshFailure(cause = RuntimeException("Token refresh failed"))

        repository.sessionState.test {
            assertEquals(SessionState.Unauthenticated, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 8 — getCurrentUser
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given authenticated user when getCurrentUser then returns mapped AuthUser`() = runTest {
        val userInfoMock = buildUserInfoMock(id = "abc-123", email = "me@example.com")
        every { supabaseAuth.currentUserOrNull() } returns userInfoMock

        val result = repository.getCurrentUser()

        assertEquals("abc-123", result?.id)
        assertEquals("me@example.com", result?.email)
    }

    @Test
    fun `given no active session when getCurrentUser then returns null`() = runTest {
        every { supabaseAuth.currentUserOrNull() } returns null

        val result = repository.getCurrentUser()

        assertNull(result)
    }

    @Test
    fun `given user with nickname in metadata when getCurrentUser then nickname is populated from metadata`() = runTest {
        val userInfoMock = buildUserInfoMock(nicknameMetadata = "Merlin")
        every { supabaseAuth.currentUserOrNull() } returns userInfoMock

        val result = repository.getCurrentUser()

        assertEquals("Merlin", result?.nickname)
    }

    @Test
    fun `given user with no nickname metadata when getCurrentUser then nickname falls back to email prefix`() = runTest {
        val userInfoMock = buildUserInfoMock(email = "gandalf@shire.com", nicknameMetadata = null)
        every { supabaseAuth.currentUserOrNull() } returns userInfoMock

        val result = repository.getCurrentUser()

        // toAuthUser() falls back to email.substringBefore('@') when nickname metadata is absent
        assertEquals("gandalf", result?.nickname)
    }

    @Test
    fun `given user with null email and no nickname when getCurrentUser then nickname is null`() = runTest {
        val userInfoMock = buildUserInfoMock(email = null, nicknameMetadata = null)
        every { supabaseAuth.currentUserOrNull() } returns userInfoMock

        val result = repository.getCurrentUser()

        assertNull(result?.nickname)
    }

    @Test
    fun `given user with game_tag in metadata when getCurrentUser then gameTag is populated`() = runTest {
        val userInfoMock = buildUserInfoMock(gameTagMetadata = "#A3KX9Z")
        every { supabaseAuth.currentUserOrNull() } returns userInfoMock

        val result = repository.getCurrentUser()

        assertEquals("#A3KX9Z", result?.gameTag)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 9 — updateNickname
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given valid nickname when updateNickname then calls service and returns Success with trimmed nickname`() = runTest {
        val userInfoMock = buildUserInfoMock()
        every { supabaseAuth.currentUserOrNull() } returns userInfoMock
        coEvery { supabaseUserProfileService.updateNickname(UpdateNicknameDto("Gandalf")) } returns successResponse()

        val result = repository.updateNickname("  Gandalf  ")

        // Nickname must be trimmed before being sent to the service
        assertTrue(result is AuthResult.Success)
        assertEquals("Gandalf", (result as AuthResult.Success).data.nickname)
    }

    @Test
    fun `given nickname exceeding 30 chars when updateNickname then returns NicknameTooLong without calling service`() = runTest {
        val tooLong = "A".repeat(31)

        val result = repository.updateNickname(tooLong)

        assertTrue(result is AuthResult.Error)
        assertEquals(AuthError.NicknameTooLong, (result as AuthResult.Error).error)
        // Should not reach the network when local validation already rejects it
        coVerify(exactly = 0) { supabaseUserProfileService.updateNickname(any()) }
    }

    @Test
    fun `given nickname with exactly 30 chars when updateNickname then validation passes and returns Success`() = runTest {
        val exactly30 = "A".repeat(30)
        val userInfoMock = buildUserInfoMock()
        every { supabaseAuth.currentUserOrNull() } returns userInfoMock

        val result = repository.updateNickname(exactly30)

        assertTrue(result is AuthResult.Success)
    }

    @Test
    fun `given service returns HTTP 400 when updateNickname then returns NicknameInappropriate`() = runTest {
        // HTTP 400 from the profanity trigger → NicknameInappropriate (not InvalidCredentials)
        coEvery { supabaseUserProfileService.updateNickname(any()) } returns errorResponse(400)

        val result = repository.updateNickname("BadWord")

        assertTrue(result is AuthResult.Error)
        assertEquals(AuthError.NicknameInappropriate, (result as AuthResult.Error).error)
    }

    @Test
    fun `given service returns HTTP 500 when updateNickname then returns Unknown not NicknameInappropriate`() = runTest {
        // Only HTTP 400 maps to NicknameInappropriate; other codes map to Unknown
        coEvery { supabaseUserProfileService.updateNickname(any()) } returns errorResponse(500)

        val result = repository.updateNickname("ValidName")

        assertTrue(result is AuthResult.Error)
        assertTrue((result as AuthResult.Error).error is AuthError.Unknown)
    }

    @Test
    fun `given user session expired after service call when updateNickname then returns SessionExpired`() = runTest {
        // Service succeeds but currentUserOrNull() returns null (session expired between the calls)
        every { supabaseAuth.currentUserOrNull() } returns null

        val result = repository.updateNickname("Gandalf")

        assertTrue(result is AuthResult.Error)
        assertEquals(AuthError.SessionExpired, (result as AuthResult.Error).error)
    }

    @Test
    fun `given network failure during updateNickname then returns NetworkError`() = runTest {
        coEvery { supabaseUserProfileService.updateNickname(any()) } throws IOException("Offline")

        val result = repository.updateNickname("Gandalf")

        assertTrue(result is AuthResult.Error)
        assertEquals(AuthError.NetworkError, (result as AuthResult.Error).error)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 10 — toAuthUser mapper (edge cases)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given user with no identities when getCurrentUser then provider defaults to email`() = runTest {
        // Edge case: identities list is empty — must not crash and must fall back to "email"
        val userInfoMock = mockk<UserInfo>(relaxed = true) {
            every { id }            returns "abc-123"
            every { email }         returns "test@example.com"
            every { userMetadata }  returns null
            every { identities }    returns emptyList()
        }
        every { supabaseAuth.currentUserOrNull() } returns userInfoMock

        val result = repository.getCurrentUser()

        // identities?.firstOrNull()?.provider → null → ?: "email"
        assertEquals("email", result?.provider)
    }

    @Test
    fun `given user with google provider when getCurrentUser then provider is google`() = runTest {
        val userInfoMock = buildUserInfoMock(provider = "google")
        every { supabaseAuth.currentUserOrNull() } returns userInfoMock

        val result = repository.getCurrentUser()

        assertEquals("google", result?.provider)
    }
}
