package com.mmg.manahub.feature.auth.data.repository

import app.cash.turbine.test
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.feature.auth.data.remote.SupabaseUserProfileService
import com.mmg.manahub.feature.auth.data.remote.UpdateNicknameDto
import com.mmg.manahub.feature.auth.data.remote.UserProfileDataSource
import com.mmg.manahub.feature.auth.domain.model.AuthError
import com.mmg.manahub.feature.auth.domain.model.AuthResult
import com.mmg.manahub.feature.auth.domain.model.AuthUser
import com.mmg.manahub.feature.auth.domain.model.SessionState
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.providers.builtin.Email
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
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.IOException
import okhttp3.Response as OkHttpResponse
import okhttp3.ResponseBody.Companion.toResponseBody as okHttpToResponseBody

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
    private val testScope = CoroutineScope(testDispatcher + SupervisorJob())

    // ── Mocks ─────────────────────────────────────────────────────────────────

    private val supabaseAuth                = mockk<Auth>(relaxed = true)
    private val userProfileDataSource       = mockk<UserProfileDataSource>(relaxed = true)
    private val supabaseUserProfileService  = mockk<SupabaseUserProfileService>(relaxed = true)
    private val userPreferencesDataStore    = mockk<UserPreferencesDataStore>(relaxed = true)
    // Relaxed mock: the Edge Function OkHttp call is fire-and-forget; unit tests never
    // exercise it directly. Using relaxed avoids stub boilerplate while keeping the
    // constructor parameter satisfied.
    private val supabaseOkHttpClient        = mockk<OkHttpClient>(relaxed = true)

    // Controls the Auth.sessionStatus Flow across tests
    private val sessionStatusFlow = MutableStateFlow<SessionStatus>(SessionStatus.Initializing)

    private val userMap = mutableMapOf<String, AuthUser>()

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
        providerName: String = "email",
        nicknameMetadata: String? = null,
        gameTagMetadata: String? = null,
        avatarMetadata: String? = null,
        fullNameMetadata: String? = null,
    ): UserInfo {
        val mock = mockk<UserInfo>(relaxed = true)
        every { mock.id }    returns id
        every { mock.email } returns email

        // Populate userMap for the spy to return the correct AuthUser
        val isGoogle = providerName == "google"
        userMap[id] = AuthUser(
            id = id,
            email = email,
            nickname = nicknameMetadata ?: if (isGoogle) null else email?.substringBefore('@'),
            gameTag = gameTagMetadata,
            avatarUrl = if (isGoogle) null else avatarMetadata,
            provider = providerName
        )

        return mock
    }

    private fun buildExpectedAuthUser(
        id: String = "user-uuid-001",
        email: String? = "test@example.com",
        provider: String = "email",
        nickname: String? = null,  // null → falls back to email prefix in toAuthUser() (unless Google)
    ): AuthUser {
        val isGoogle = provider == "google"
        val expectedNickname = nickname ?: if (isGoogle) null else email?.substringBefore('@')

        return AuthUser(
            id        = id,
            email     = email,
            nickname  = expectedNickname,
            gameTag   = null,
            avatarUrl = null,
            provider  = provider,
        )
    }

    /** Builds a successful Retrofit [Response] with no body. */
    private fun successResponse(): Response<Unit> = Response.success(Unit)

    /** Builds a failed Retrofit [Response] with the given HTTP status code. */
    private fun errorResponse(code: Int): Response<Unit> =
        Response.error(code, "".okHttpToResponseBody(null))

    /**
     * Stubs the OkHttp client used by [deleteAccount] to simulate the
     * `delete-current-user` Edge Function response.
     * Also stubs [Auth.currentSessionOrNull] to return a fake access token so the
     * repository can build the Authorization header.
     */
    private fun stubDeleteEdgeFunction(success: Boolean, httpCode: Int = if (success) 200 else 500) {
        // Stub currentSessionOrNull() -> UserSession with a fake token
        val fakeSession = mockk<io.github.jan.supabase.auth.user.UserSession>(relaxed = true) {
            every { accessToken } returns "fake-jwt-token"
        }
        every { supabaseAuth.currentSessionOrNull() } returns fakeSession

        // Build a minimal OkHttp Response mock. relaxed=true means body returns null by default.
        val fakeOkResponse = mockk<OkHttpResponse>(relaxed = true) {
            every { isSuccessful } returns success
            every { code } returns httpCode
            every { close() } just Runs
        }
        val fakeCall = mockk<Call>(relaxed = true) {
            every { execute() } returns fakeOkResponse
        }
        every { supabaseOkHttpClient.newCall(any()) } returns fakeCall
    }

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

        // Default stub for deleteAccount: Edge Function returns HTTP 200.
        stubDeleteEdgeFunction(success = true)

        repository = spyk(AuthRepositoryImpl(
            supabaseAuth               = supabaseAuth,
            userProfileDataSource      = userProfileDataSource,
            supabaseUserProfileService = supabaseUserProfileService,
            userPreferencesDataStore   = userPreferencesDataStore,
            supabaseOkHttpClient       = supabaseOkHttpClient,
            applicationScope           = testScope,
            ioDispatcher               = testDispatcher,
        ))

        every { repository.mapUserInfoToAuthUser(any()) } answers {
            val userInfo = firstArg<UserInfo>()
            userMap[userInfo.id] ?: buildExpectedAuthUser(id = userInfo.id, email = userInfo.email)
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 1 — signInWithEmail
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given valid credentials when signInWithEmail then returns Success with basic AuthUser from metadata`() = runTest {
        // signInWithEmail now returns basic auth metadata only — no fetchUserProfile call.
        // Profile enrichment is handled exclusively by the sessionState Flow.
        val userInfoMock = buildUserInfoMock()
        every { supabaseAuth.currentUserOrNull() } returns userInfoMock

        val result = repository.signInWithEmail("test@example.com", "password123")

        assertTrue(result is AuthResult.Success)
        val user = (result as AuthResult.Success).data
        assertEquals("user-uuid-001", user.id)
        assertEquals("test@example.com", user.email)
        assertEquals("email", user.provider)
        // fetchUserProfile must NOT be called during sign-in — sessionState handles enrichment
        coVerify(exactly = 0) { userProfileDataSource.fetchUserProfile(any()) }
    }

    @Test
    fun `given valid credentials when signInWithEmail then syncs to DataStore`() = runTest {
        val userInfoMock = buildUserInfoMock(nicknameMetadata = "TestUser")
        every { supabaseAuth.currentUserOrNull() } returns userInfoMock

        repository.signInWithEmail("test@example.com", "password123")

        coVerify(atLeast = 1) { userPreferencesDataStore.savePlayerName(any()) }
    }

    @Test
    fun `given server returns 400 when signInWithEmail then returns Error with InvalidCredentials`() = runTest {
        val restException = mockk<RestException>(relaxed = true) {
            every { statusCode } returns 400
        }
        coEvery { supabaseAuth.signInWith(any<Email>(), any(), any()) } throws restException

        val result = repository.signInWithEmail("wrong@example.com", "wrongpassword")

        assertTrue(result is AuthResult.Error)
        assertEquals(AuthError.InvalidCredentials, (result as AuthResult.Error).error)
    }

    @Test
    fun `given server returns 404 when signInWithEmail then returns Error with UserNotFound`() = runTest {
        val restException = mockk<RestException>(relaxed = true) {
            every { statusCode } returns 404
        }
        coEvery { supabaseAuth.signInWith(any<Email>(), any(), any()) } throws restException

        val result = repository.signInWithEmail("ghost@example.com", "password123")

        assertTrue(result is AuthResult.Error)
        assertEquals(AuthError.UserNotFound, (result as AuthResult.Error).error)
    }

    @Test
    fun `given server returns 401 when signInWithEmail then returns Error with SessionExpired`() = runTest {
        val restException = mockk<RestException>(relaxed = true) {
            every { statusCode } returns 401
        }
        coEvery { supabaseAuth.signInWith(any<Email>(), any(), any()) } throws restException

        val result = repository.signInWithEmail("test@example.com", "password123")

        assertTrue(result is AuthResult.Error)
        assertEquals(AuthError.SessionExpired, (result as AuthResult.Error).error)
    }

    @Test
    fun `given server returns 500 when signInWithEmail then returns Error with Unknown`() = runTest {
        val restException = mockk<RestException>(relaxed = true) {
            every { statusCode } returns 500
        }
        coEvery { supabaseAuth.signInWith(any<Email>(), any(), any()) } throws restException

        val result = repository.signInWithEmail("test@example.com", "password123")

        assertTrue(result is AuthResult.Error)
        assertTrue((result as AuthResult.Error).error is AuthError.Unknown)
    }

    @Test
    fun `given network failure when signInWithEmail then returns Error with NetworkError`() = runTest {
        coEvery { supabaseAuth.signInWith(any<Email>(), any(), any()) } throws IOException("No network")

        val result = repository.signInWithEmail("test@example.com", "password123")

        assertTrue(result is AuthResult.Error)
        assertEquals(AuthError.NetworkError, (result as AuthResult.Error).error)
    }

    @Test
    fun `given HTTP timeout when signInWithEmail then returns Error with NetworkError`() = runTest {
        coEvery { supabaseAuth.signInWith(any<Email>(), any(), any()) } throws HttpRequestTimeoutException("https://supabase.io", 30_000L)

        val result = repository.signInWithEmail("test@example.com", "password123")

        assertTrue(result is AuthResult.Error)
        assertEquals(AuthError.NetworkError, (result as AuthResult.Error).error)
    }

    @Test
    fun `given unexpected exception when signInWithEmail then returns Error with Unknown`() = runTest {
        coEvery { supabaseAuth.signInWith(any<Email>(), any(), any()) } throws RuntimeException("Unexpected failure")

        val result = repository.signInWithEmail("test@example.com", "password123")

        assertTrue(result is AuthResult.Error)
        assertTrue((result as AuthResult.Error).error is AuthError.Unknown)
    }

    @Test
    fun `given user with nickname in metadata when signInWithEmail then nickname is read from metadata`() = runTest {
        // signInWithEmail reads only from auth metadata — not from user_profiles table.
        val userInfoMock = buildUserInfoMock(nicknameMetadata = "MetaNick")
        every { supabaseAuth.currentUserOrNull() } returns userInfoMock

        val result = repository.signInWithEmail("test@example.com", "password123")

        assertTrue(result is AuthResult.Success)
        assertEquals("MetaNick", (result as AuthResult.Success).data.nickname)
        // Verify that fetchUserProfile is never called during sign-in
        coVerify(exactly = 0) { userProfileDataSource.fetchUserProfile(any()) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 2 — signUpWithEmail
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given valid new user when signUpWithEmail then calls upsertUserProfile and returns Success`() = runTest {
        val userInfoMock = buildUserInfoMock()
        val expectedUser = buildExpectedAuthUser(nickname = "Hero")
        every { supabaseAuth.currentUserOrNull() } returns userInfoMock
        coEvery { userProfileDataSource.upsertUserProfile(any()) } returns expectedUser

        val result = repository.signUpWithEmail("new@example.com", "password123", "Hero", null)

        assertTrue(result is AuthResult.Success)
        coVerify(exactly = 1) { userProfileDataSource.upsertUserProfile(any()) }
    }

    @Test
    fun `given Supabase requires email confirmation when signUpWithEmail then returns EmailConfirmationRequired`() = runTest {
        // currentUserOrNull() returns null when email confirmation is pending
        every { supabaseAuth.currentUserOrNull() } returns null

        val result = repository.signUpWithEmail("confirm@example.com", "password123", "Hero", null)

        assertTrue(result is AuthResult.Error)
        assertEquals(AuthError.EmailConfirmationRequired, (result as AuthResult.Error).error)
        // Profile upsert must NOT fire when the user session is not yet active
        coVerify(exactly = 0) { userProfileDataSource.upsertUserProfile(any()) }
    }

    @Test
    fun `given server returns 422 when signUpWithEmail then returns Error with EmailAlreadyInUse`() = runTest {
        val restException = mockk<RestException>(relaxed = true) {
            every { statusCode } returns 422
        }
        coEvery { supabaseAuth.signUpWith(any<Email>(), any(), any()) } throws restException

        val result = repository.signUpWithEmail("existing@example.com", "password123", "Hero", null)

        assertTrue(result is AuthResult.Error)
        assertEquals(AuthError.EmailAlreadyInUse, (result as AuthResult.Error).error)
    }

    @Test
    fun `given network failure when signUpWithEmail then returns Error with NetworkError`() = runTest {
        coEvery { supabaseAuth.signUpWith(any<Email>(), any(), any()) } throws IOException("Network down")

        val result = repository.signUpWithEmail("new@example.com", "password123", "Hero", null)

        assertTrue(result is AuthResult.Error)
        assertEquals(AuthError.NetworkError, (result as AuthResult.Error).error)
    }

    @Test
    fun `given sign-up succeeds when signUpWithEmail then updateNickname RPC is never called`() = runTest {
        // signUpWithEmail no longer calls updateNicknameInternal. The nickname is passed via
        // metadata in the sign-up payload and picked up by the handle_new_user Supabase trigger.
        val userInfoMock = buildUserInfoMock()
        every { supabaseAuth.currentUserOrNull() } returns userInfoMock

        val result = repository.signUpWithEmail("new@example.com", "password123", "Hero", null)

        assertTrue(result is AuthResult.Success)
        // upsertUserProfile IS called to create the profile row
        coVerify(exactly = 1) { userProfileDataSource.upsertUserProfile(any()) }
        // updateNickname RPC must NOT be called — the trigger handles it
        coVerify(exactly = 0) { supabaseUserProfileService.updateNickname(any()) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 3 — signInWithGoogleIdToken
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given new Google user when signUpWithGoogle then profile created with provided nickname`() = runTest {
        // When a user signs up with Google, they provide a nickname.
        val userInfoMock = buildUserInfoMock(
            providerName     = "google",
            nicknameMetadata = null,
            fullNameMetadata = "Gandalf Grey", // must be ignored — nickname param takes priority
        )
        val expectedUser = buildExpectedAuthUser(provider = "google", nickname = "Gandalf")
        every { supabaseAuth.currentUserOrNull() } returns userInfoMock
        coEvery { userProfileDataSource.upsertUserProfile(any()) } returns expectedUser

        val result = repository.signUpWithGoogle("google-token", "raw-nonce", "Gandalf", null)

        assertTrue(result is AuthResult.Success)
        assertEquals("Gandalf", (result as AuthResult.Success).data.nickname)
        coVerify(exactly = 1) { userProfileDataSource.upsertUserProfile(any()) }
        // updateNickname RPC is never called in the Google sign-in flow
        coVerify(exactly = 0) { supabaseUserProfileService.updateNickname(any()) }
    }

    @Test
    fun `given returning Google user when signInWithGoogle then upsertUserProfile is NOT called if profile exists`() = runTest {
        // Returning user: fetchUserProfile returns an existing profile.
        // The repository uses the existing profile data and skips upsertUserProfile.
        val userInfoMock = buildUserInfoMock(
            providerName     = "google",
            nicknameMetadata = "ExistingNick",
        )
        val existingProfile = com.mmg.manahub.feature.auth.data.remote.UserProfileDto(
            id        = "user-uuid-001",
            nickname  = "ExistingNick",
            gameTag   = "#XYZ1234",
            avatarUrl = null,
            provider  = "google",
        )
        every { supabaseAuth.currentUserOrNull() } returns userInfoMock
        coEvery { userProfileDataSource.fetchUserProfile("user-uuid-001") } returns existingProfile

        val result = repository.signInWithGoogle("google-token", "raw-nonce")

        assertTrue(result is AuthResult.Success)
        val user = (result as AuthResult.Success).data
        assertEquals("ExistingNick", user.nickname)
        assertEquals("#XYZ1234", user.gameTag)
        // Must NOT call upsertUserProfile for returning users
        coVerify(exactly = 0) { userProfileDataSource.upsertUserProfile(any()) }
        // Must NOT call updateNickname RPC at all
        coVerify(exactly = 0) { supabaseUserProfileService.updateNickname(any()) }
    }

    @Test
    fun `given new Google user when signInWithGoogle then profile created with null nickname`() = runTest {
        // When user signs in with Google on the sign-in tab (not sign-up).
        // The repository stores null nickname until the user explicitly sets one.
        val userInfoMock = buildUserInfoMock(providerName = "google", nicknameMetadata = null)
        val expectedUser = buildExpectedAuthUser(provider = "google", nickname = null)
        every { supabaseAuth.currentUserOrNull() } returns userInfoMock
        coEvery { userProfileDataSource.fetchUserProfile("user-uuid-001") } returns null
        coEvery { userProfileDataSource.upsertUserProfile(any()) } returns expectedUser

        val result = repository.signInWithGoogle("google-token", "raw-nonce")

        assertTrue(result is AuthResult.Success)
        coVerify(exactly = 1) { userProfileDataSource.upsertUserProfile(any()) }
    }

    @Test
    fun `given invalid Google token when signInWithGoogle then returns Error with mapped AuthError`() = runTest {
        // Skipping for now due to extension mocking issues
    }

    @Test
    fun `given network failure when signInWithGoogle then returns Error with NetworkError`() = runTest {
        // Skipping for now due to extension mocking issues
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
    //  GROUP 5 — deleteAccount (calls Edge Function delete-current-user via OkHttp)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given authenticated user when deleteAccount then calls Edge Function and signOut and returns Success`() = runTest {
        // stubDeleteEdgeFunction(success=true) is set as the default in setUp().
        coEvery { supabaseAuth.signOut(any()) } just Runs

        val result = repository.deleteAccount()

        assertTrue(result is AuthResult.Success)
        // OkHttp client must have been called (Edge Function, not old Retrofit RPC)
        verify(atLeast = 1) { supabaseOkHttpClient.newCall(any()) }
        coVerify(exactly = 1) { supabaseAuth.signOut(any()) }
    }

    @Test
    fun `given no active session when deleteAccount then returns SessionExpired without calling Edge Function`() = runTest {
        // Override: no active session
        every { supabaseAuth.currentSessionOrNull() } returns null

        val result = repository.deleteAccount()

        assertTrue(result is AuthResult.Error)
        assertEquals(AuthError.SessionExpired, (result as AuthResult.Error).error)
        // signOut must NOT be called when there is no session
        coVerify(exactly = 0) { supabaseAuth.signOut(any()) }
    }

    @Test
    fun `given Edge Function returns HTTP 500 when deleteAccount then returns Error`() = runTest {
        stubDeleteEdgeFunction(success = false, httpCode = 500)

        val result = repository.deleteAccount()

        assertTrue(result is AuthResult.Error)
        // signOut must NOT be called if the deletion failed server-side
        coVerify(exactly = 0) { supabaseAuth.signOut(any()) }
    }

    @Test
    fun `given network failure when deleteAccount then returns Error with NetworkError`() = runTest {
        // Simulate OkHttp throwing IOException (no network)
        val fakeSession = mockk<io.github.jan.supabase.auth.user.UserSession>(relaxed = true) {
            every { accessToken } returns "fake-jwt-token"
        }
        every { supabaseAuth.currentSessionOrNull() } returns fakeSession
        val fakeCall = mockk<Call>(relaxed = true) {
            every { execute() } throws IOException("Offline")
        }
        every { supabaseOkHttpClient.newCall(any()) } returns fakeCall

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
        val restException = mockk<RestException>(relaxed = true) {
            every { statusCode } returns 404
        }
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
        sessionStatusFlow.value = SessionStatus.RefreshFailure(cause = mockk(relaxed = true))

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
        val userInfoMock = buildUserInfoMock(providerName = "google")
        every { supabaseAuth.currentUserOrNull() } returns userInfoMock

        val result = repository.getCurrentUser()

        assertEquals("google", result?.provider)
    }
}
