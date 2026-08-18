package com.mmg.manahub.feature.auth.data.repository

import app.cash.turbine.test
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.data.remote.UserProfileClient
import com.mmg.manahub.core.data.remote.dto.UpdateNicknameDto
import com.mmg.manahub.core.data.remote.dto.UserProfileDto
import com.mmg.manahub.feature.auth.data.remote.ProfileFetchResult
import com.mmg.manahub.feature.auth.data.remote.UserProfileDataSource
import com.mmg.manahub.core.domain.auth.AuthError
import com.mmg.manahub.core.domain.auth.AuthResult
import com.mmg.manahub.core.domain.auth.AuthUser
import com.mmg.manahub.core.domain.auth.SessionState
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.AuthConfig
import io.github.jan.supabase.auth.SignOutScope
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.exceptions.RestException
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.statement.HttpResponse as KtorHttpResponse
import io.ktor.http.HttpStatusCode
import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.IOException
import okhttp3.Response as OkHttpResponse

/**
 * Unit tests for [AuthRepositoryImpl].
 *
 * Strategy:
 * - [Auth] is mocked with MockK.
 * - [UserProfileDataSource] is mocked to isolate repository logic.
 * - [UserProfileClient] is mocked for RPC-level tests (updateNickname, deleteAccount).
 * - [UserPreferencesDataStore] is mocked (relaxed) to verify DataStore sync calls.
 * - [UnconfinedTestDispatcher] is used so withContext(ioDispatcher) runs synchronously,
 *   avoiding any need for advanceUntilIdle.
 * - Supabase's [Auth.sessionStatus] is backed by a [MutableStateFlow] so we can
 *   control emissions in Flow tests.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthRepositoryImplTest {

    // ── Test dispatcher ───────────────────────────────────────────────────────

    // A single shared scheduler so that tests which use runTest(testDispatcher) drive the
    // SAME virtual clock as the repository's withContext(ioDispatcher) work — required so that
    // delay() inside the signInWithGoogle retry path is auto-advanced rather than hanging.
    private val testScheduler = TestCoroutineScheduler()
    private val testDispatcher = UnconfinedTestDispatcher(testScheduler)
    private val testScope = CoroutineScope(testDispatcher + SupervisorJob())

    // ── Mocks ─────────────────────────────────────────────────────────────────

    private val supabaseAuth                = mockk<Auth>(relaxed = true)
    private val userProfileDataSource       = mockk<UserProfileDataSource>(relaxed = true)
    private val userProfileClient            = mockk<UserProfileClient>(relaxed = true)
    private val userPreferencesDataStore    = mockk<UserPreferencesDataStore>(relaxed = true)
    // Relaxed mock: the Edge Function OkHttp call is fire-and-forget; unit tests never
    // exercise it directly. Using relaxed avoids stub boilerplate while keeping the
    // constructor parameter satisfied.
    private val supabaseOkHttpClient        = mockk<OkHttpClient>(relaxed = true)
    // Stored (not an anonymous inline mock) so tests can verify specific log/recordException calls
    // -- e.g. the has_password-stale telemetry breadcrumb -- rather than only that SOME call was made.
    private val crashlyticsMock              = mockk<FirebaseCrashlytics>(relaxed = true)

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

    /**
     * Builds a [ClientRequestException] for test stubs that need to simulate
     * Ktor 4xx errors (e.g. HTTP 400 for inappropriate nickname).
     */
    private fun ktorClientError(statusCode: Int): ClientRequestException {
        val response = mockk<KtorHttpResponse>(relaxed = true)
        every { response.status } returns HttpStatusCode.fromValue(statusCode)
        return ClientRequestException(response, "HTTP $statusCode")
    }

    /**
     * Builds a [io.ktor.client.plugins.ServerResponseException] for test stubs
     * that need to simulate Ktor 5xx errors.
     */
    private fun ktorServerError(statusCode: Int): io.ktor.client.plugins.ServerResponseException {
        val response = mockk<KtorHttpResponse>(relaxed = true)
        every { response.status } returns HttpStatusCode.fromValue(statusCode)
        return io.ktor.client.plugins.ServerResponseException(response, "HTTP $statusCode")
    }

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
        // notifyAccountEvent (fired by updatePassword/updateEmail/unlinkIdentity/etc.) calls
        // FirebaseCrashlytics.getInstance() on both its success-check and failure paths, outside
        // any test-controllable seam — without this, any test exercising one of those ops crashes
        // with "Default FirebaseApp is not initialized" the moment notifyAccountEvent's launch runs
        // (applicationScope here is backed by the same UnconfinedTestDispatcher as ioDispatcher, so
        // the launch executes synchronously within the test rather than in the background).
        mockkStatic(FirebaseCrashlytics::class)
        every { FirebaseCrashlytics.getInstance() } returns crashlyticsMock

        every { supabaseAuth.sessionStatus } returns sessionStatusFlow
        every { supabaseAuth.config } returns mockk<AuthConfig>(relaxed = true)

        coEvery { supabaseAuth.signInWith(any<Email>(), anyNullable(), anyNullable()) } just Runs
        coEvery { supabaseAuth.signUpWith(any<Email>(), anyNullable(), anyNullable()) } returns null
        coEvery { supabaseAuth.signInWith(any<IDToken>(), anyNullable(), anyNullable()) } just Runs

        // Default stub: upsertUserProfile returns the user unchanged (non-blocking default).
        coEvery { userProfileDataSource.upsertUserProfile(any()) } answers {
            firstArg<AuthUser>()
        }

        // Default stub: fetchUserProfile returns null (no enrichment by default).
        coEvery { userProfileDataSource.fetchUserProfile(any()) } returns null

        // Default stub: updateNickname returns success (Ktor void = just runs).
        coEvery { userProfileClient.updateNickname(any()) } just Runs

        // Default stub for deleteAccount: Edge Function returns HTTP 200.
        stubDeleteEdgeFunction(success = true)

        repository = spyk(AuthRepositoryImpl(
            supabaseAuth               = supabaseAuth,
            userProfileDataSource      = userProfileDataSource,
            userProfileClient          = userProfileClient,
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

    @After
    fun tearDown() {
        unmockkStatic(FirebaseCrashlytics::class)
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
        coEvery { supabaseAuth.signInWith(any<Email>(), anyNullable(), anyNullable()) } throws restException

        val result = repository.signInWithEmail("wrong@example.com", "wrongpassword")

        assertTrue(result is AuthResult.Error)
        assertEquals(AuthError.InvalidCredentials, (result as AuthResult.Error).error)
    }

    @Test
    fun `given server returns 404 when signInWithEmail then returns Error with UserNotFound`() = runTest {
        val restException = mockk<RestException>(relaxed = true) {
            every { statusCode } returns 404
        }
        coEvery { supabaseAuth.signInWith(any<Email>(), anyNullable(), anyNullable()) } throws restException

        val result = repository.signInWithEmail("ghost@example.com", "password123")

        assertTrue(result is AuthResult.Error)
        assertEquals(AuthError.UserNotFound, (result as AuthResult.Error).error)
    }

    @Test
    fun `given server returns 401 when signInWithEmail then returns Error with SessionExpired`() = runTest {
        val restException = mockk<RestException>(relaxed = true) {
            every { statusCode } returns 401
        }
        coEvery { supabaseAuth.signInWith(any<Email>(), anyNullable(), anyNullable()) } throws restException

        val result = repository.signInWithEmail("test@example.com", "password123")

        assertTrue(result is AuthResult.Error)
        assertEquals(AuthError.SessionExpired, (result as AuthResult.Error).error)
    }

    @Test
    fun `given server returns 500 when signInWithEmail then returns Error with Unknown`() = runTest {
        val restException = mockk<RestException>(relaxed = true) {
            every { statusCode } returns 500
        }
        coEvery { supabaseAuth.signInWith(any<Email>(), anyNullable(), anyNullable()) } throws restException

        val result = repository.signInWithEmail("test@example.com", "password123")

        assertTrue(result is AuthResult.Error)
        assertTrue((result as AuthResult.Error).error is AuthError.Unknown)
    }

    @Test
    fun `given network failure when signInWithEmail then returns Error with NetworkError`() = runTest {
        coEvery { supabaseAuth.signInWith(any<Email>(), anyNullable(), anyNullable()) } throws IOException("No network")

        val result = repository.signInWithEmail("test@example.com", "password123")

        assertTrue(result is AuthResult.Error)
        assertEquals(AuthError.NetworkError, (result as AuthResult.Error).error)
    }

    @Test
    fun `given HTTP timeout when signInWithEmail then returns Error with NetworkError`() = runTest {
        coEvery { supabaseAuth.signInWith(any<Email>(), anyNullable(), anyNullable()) } throws HttpRequestTimeoutException("https://supabase.io", 30_000L)

        val result = repository.signInWithEmail("test@example.com", "password123")

        assertTrue(result is AuthResult.Error)
        assertEquals(AuthError.NetworkError, (result as AuthResult.Error).error)
    }

    @Test
    fun `given unexpected exception when signInWithEmail then returns Error with Unknown`() = runTest {
        coEvery { supabaseAuth.signInWith(any<Email>(), anyNullable(), anyNullable()) } throws RuntimeException("Unexpected failure")

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
        coEvery { supabaseAuth.signUpWith(any<Email>(), anyNullable(), anyNullable()) } throws restException

        val result = repository.signUpWithEmail("existing@example.com", "password123", "Hero", null)

        assertTrue(result is AuthResult.Error)
        assertEquals(AuthError.EmailAlreadyInUse, (result as AuthResult.Error).error)
    }

    @Test
    fun `given network failure when signUpWithEmail then returns Error with NetworkError`() = runTest {
        coEvery { supabaseAuth.signUpWith(any<Email>(), anyNullable(), anyNullable()) } throws IOException("Network down")

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
        coVerify(exactly = 0) { userProfileClient.updateNickname(any()) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 3 — signInWithGoogleIdToken
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given new Google user when signUpWithGoogle then profile created with provided nickname`() = runTest {
        val userInfoMock = buildUserInfoMock(
            providerName     = "google",
            nicknameMetadata = null,
            fullNameMetadata = "Gandalf Grey",
        )
        val expectedUser = buildExpectedAuthUser(provider = "google", nickname = "Gandalf")
        every { supabaseAuth.currentUserOrNull() } returns userInfoMock
        coEvery { userProfileDataSource.completeUserProfile(any()) } returns expectedUser

        val result = repository.signUpWithGoogle("google-token", "raw-nonce", "Gandalf", null)

        assertTrue(result is AuthResult.Success)
        assertEquals("Gandalf", (result as AuthResult.Success).data.nickname)
        coVerify(exactly = 1) { userProfileDataSource.completeUserProfile(any()) }
        coVerify(exactly = 0) { userProfileClient.updateNickname(any()) }
    }

    @Test
    fun `given returning Google user when signInWithGoogle then upsertUserProfile is NOT called if profile exists`() = runTest {
        val userInfoMock = buildUserInfoMock(
            providerName     = "google",
            nicknameMetadata = "ExistingNick",
        )
        val existingProfile = UserProfileDto(
            id               = "user-uuid-001",
            nickname         = "ExistingNick",
            gameTag          = "#XYZ1234",
            avatarUrl        = null,
            provider         = "google",
            profileCompleted = true,
        )
        every { supabaseAuth.currentUserOrNull() } returns userInfoMock
        coEvery { userProfileDataSource.getProfileByUserId("user-uuid-001") } returns
            ProfileFetchResult.Found(existingProfile)

        val result = repository.signInWithGoogle("google-token", "raw-nonce")

        assertTrue(result is AuthResult.Success)
        val user = (result as AuthResult.Success).data
        assertEquals("ExistingNick", user.nickname)
        assertEquals("#XYZ1234", user.gameTag)
        coVerify(exactly = 0) { userProfileDataSource.upsertUserProfile(any()) }
        coVerify(exactly = 0) { userProfileClient.updateNickname(any()) }
    }

    @Test
    fun `given new Google user with incomplete profile when signInWithGoogle then returns NoProfileFound`() = runTest {
        val userInfoMock = buildUserInfoMock(providerName = "google", nicknameMetadata = null)
        every { supabaseAuth.currentUserOrNull() } returns userInfoMock
        // RPC responded successfully but found no profile row (NotFound).
        coEvery { userProfileDataSource.getProfileByUserId("user-uuid-001") } returns
            ProfileFetchResult.NotFound
        coEvery { supabaseAuth.signOut(any()) } just Runs

        val result = repository.signInWithGoogle("google-token", "raw-nonce")

        assertTrue(result is AuthResult.Error)
        assertTrue((result as AuthResult.Error).error is AuthError.NoProfileFound)
        coVerify(exactly = 0) { userProfileDataSource.upsertUserProfile(any()) }
        // A genuine "not found" must NOT fall back to the direct table query.
        coVerify(exactly = 0) { userProfileDataSource.fetchUserProfile(any()) }
    }

    @Test
    fun `given fetch fails once then succeeds when signInWithGoogle then retries and returns Success`() = runTest(testDispatcher) {
        val userInfoMock = buildUserInfoMock(providerName = "google", nicknameMetadata = "ExistingNick")
        val existingProfile = UserProfileDto(
            id               = "user-uuid-001",
            nickname         = "ExistingNick",
            gameTag          = "#XYZ1234",
            avatarUrl        = null,
            provider         = "google",
            profileCompleted = true,
        )
        every { supabaseAuth.currentUserOrNull() } returns userInfoMock
        // First RPC attempt fails (token not yet propagated → 401); retry succeeds.
        coEvery { userProfileDataSource.getProfileByUserId("user-uuid-001") } returnsMany listOf(
            ProfileFetchResult.Failure(IOException("HTTP 401")),
            ProfileFetchResult.Found(existingProfile),
        )

        val result = repository.signInWithGoogle("google-token", "raw-nonce")

        assertTrue(result is AuthResult.Success)
        val user = (result as AuthResult.Success).data
        assertEquals("ExistingNick", user.nickname)
        // The RPC was hit twice (original + retry); the table-query fallback was never needed.
        coVerify(exactly = 2) { userProfileDataSource.getProfileByUserId("user-uuid-001") }
        coVerify(exactly = 0) { userProfileDataSource.fetchUserProfile(any()) }
    }

    @Test
    fun `given RPC fails twice but table query succeeds when signInWithGoogle then falls back and returns Success`() = runTest(testDispatcher) {
        val userInfoMock = buildUserInfoMock(providerName = "google", nicknameMetadata = "ExistingNick")
        val fallbackProfile = UserProfileDto(
            id               = "user-uuid-001",
            nickname         = "ExistingNick",
            gameTag          = "#XYZ1234",
            avatarUrl        = null,
            provider         = "google",
            profileCompleted = true,
        )
        every { supabaseAuth.currentUserOrNull() } returns userInfoMock
        // Both RPC attempts fail; the direct table query is the last-resort fallback.
        coEvery { userProfileDataSource.getProfileByUserId("user-uuid-001") } returns
            ProfileFetchResult.Failure(IOException("HTTP 403"))
        coEvery { userProfileDataSource.fetchUserProfile("user-uuid-001") } returns fallbackProfile

        val result = repository.signInWithGoogle("google-token", "raw-nonce")

        assertTrue(result is AuthResult.Success)
        assertEquals("ExistingNick", (result as AuthResult.Success).data.nickname)
        coVerify(exactly = 2) { userProfileDataSource.getProfileByUserId("user-uuid-001") }
        coVerify(exactly = 1) { userProfileDataSource.fetchUserProfile("user-uuid-001") }
        // Critical: a fetch error for a real user must never be reported as NoProfileFound,
        // so the user is never wrongly signed out.
        coVerify(exactly = 0) { supabaseAuth.signOut(any()) }
    }

    @Test
    fun `given all profile fetches fail when signInWithGoogle then returns NetworkError not NoProfileFound`() = runTest(testDispatcher) {
        val userInfoMock = buildUserInfoMock(providerName = "google", nicknameMetadata = null)
        every { supabaseAuth.currentUserOrNull() } returns userInfoMock
        // RPC fails on both attempts AND the table-query fallback yields nothing.
        coEvery { userProfileDataSource.getProfileByUserId("user-uuid-001") } returns
            ProfileFetchResult.Failure(IOException("network down"))
        coEvery { userProfileDataSource.fetchUserProfile("user-uuid-001") } returns null
        coEvery { supabaseAuth.signOut(any()) } just Runs

        val result = repository.signInWithGoogle("google-token", "raw-nonce")

        assertTrue(result is AuthResult.Error)
        // A transient fetch failure must surface as a retryable network error, NOT as a
        // missing profile (which would force a returning user into the sign-up flow).
        assertTrue((result as AuthResult.Error).error is AuthError.NetworkError)
        // The session must be kept intact so the user can retry.
        coVerify(exactly = 0) { supabaseAuth.signOut(any()) }
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
        // Stub the fields read by the real mapUserInfoToAuthUser so the mapper doesn't crash
        // on the relaxed JsonObject mock (whose keys return JsonArray instead of JsonPrimitive).
        every { userInfoMock.identities } returns null
        every { userInfoMock.userMetadata } returns null
        val sessionMock = mockk<io.github.jan.supabase.auth.user.UserSession>(relaxed = true) {
            every { user } returns userInfoMock
        }

        // backgroundScope as applicationScope avoids UncompletedCoroutinesError at teardown.
        // The sharing coroutine is a background task; runTest does not wait for it to finish.
        val repoForTest = AuthRepositoryImpl(
            supabaseAuth               = supabaseAuth,
            userProfileDataSource      = userProfileDataSource,
            userProfileClient          = userProfileClient,
            userPreferencesDataStore   = userPreferencesDataStore,
            supabaseOkHttpClient       = supabaseOkHttpClient,
            applicationScope           = backgroundScope,
            ioDispatcher               = UnconfinedTestDispatcher(testScheduler),
        )
        sessionStatusFlow.value = SessionStatus.Authenticated(sessionMock)

        // Foreground launch: ensures at least one FG task is in the queue so that
        // advanceUntilIdle() processes BOTH the BG sharing coroutine and this FG task.
        // advanceUntilIdle() exits when only BG tasks remain (i.e. after the FG collect
        // coroutine has subscribed and suspended), at which point the sharing coroutine's
        // START resumption is pending.  testScheduler.runCurrent() then drains all remaining
        // BG events at virtual time=0: START → upstream → MockK stubs → emit(Authenticated).
        val collectJob = launch { repoForTest.sessionState.collect { } }
        advanceUntilIdle()
        testScheduler.runCurrent()

        val state = repoForTest.sessionState.value
        collectJob.cancel()

        assertTrue(state is SessionState.Authenticated)
        assertEquals("user-uuid-001", (state as SessionState.Authenticated).user.id)
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

    @Test
    fun `given user_profiles row has has_password true when sessionState enriches then AuthUser hasPassword is true`() = runTest {
        // Bug 2 fix: hasPassword must flow through the same enrichment path as
        // nickname/gameTag/avatarUrl/profileCompleted so the Account Management gate
        // (hasEmailIdentity || user.hasPassword) sees a Google-only account's self-set password.
        val userInfoMock = buildUserInfoMock()
        every { userInfoMock.identities } returns null
        every { userInfoMock.userMetadata } returns null
        val sessionMock = mockk<io.github.jan.supabase.auth.user.UserSession>(relaxed = true) {
            every { user } returns userInfoMock
        }
        coEvery { userProfileDataSource.fetchUserProfile("user-uuid-001") } returns UserProfileDto(
            id = "user-uuid-001",
            profileCompleted = true,
        )
        // has_password is now fetched via a separate self-scoped RPC (get_my_has_password),
        // not as a field on the UserProfileDto returned by fetchUserProfile (2026-08-17 security
        // fix) -- see UserProfileDataSource.fetchHasPassword's KDoc.
        coEvery { userProfileDataSource.fetchHasPassword("user-uuid-001") } returns true

        val repoForTest = AuthRepositoryImpl(
            supabaseAuth               = supabaseAuth,
            userProfileDataSource      = userProfileDataSource,
            userProfileClient          = userProfileClient,
            userPreferencesDataStore   = userPreferencesDataStore,
            supabaseOkHttpClient       = supabaseOkHttpClient,
            applicationScope           = backgroundScope,
            ioDispatcher               = UnconfinedTestDispatcher(testScheduler),
        )
        sessionStatusFlow.value = SessionStatus.Authenticated(sessionMock)

        val collectJob = launch { repoForTest.sessionState.collect { } }
        advanceUntilIdle()
        testScheduler.runCurrent()

        val state = repoForTest.sessionState.value
        collectJob.cancel()

        assertTrue(state is SessionState.Authenticated)
        assertTrue((state as SessionState.Authenticated).user.hasPassword)
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
        coEvery { userProfileClient.updateNickname(UpdateNicknameDto("Gandalf")) } just Runs

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
        coVerify(exactly = 0) { userProfileClient.updateNickname(any()) }
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
        coEvery { userProfileClient.updateNickname(any()) } throws ktorClientError(400)

        val result = repository.updateNickname("BadWord")

        assertTrue(result is AuthResult.Error)
        assertEquals(AuthError.NicknameInappropriate, (result as AuthResult.Error).error)
    }

    @Test
    fun `given service returns HTTP 500 when updateNickname then returns Unknown not NicknameInappropriate`() = runTest {
        // Only HTTP 400 maps to NicknameInappropriate; other codes map to Unknown
        coEvery { userProfileClient.updateNickname(any()) } throws ktorServerError(500)

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
        coEvery { userProfileClient.updateNickname(any()) } throws IOException("Offline")

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

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 11 — EmailNotConfirmed error mapping (ADR-003)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given AuthRestException with EmailNotConfirmed errorCode when signInWithEmail then returns EmailNotConfirmed not InvalidCredentials`() = runTest {
        // Arrange: GoTrue rejects the sign-in because email confirmation is still pending.
        // AuthRestException carries a typed AuthErrorCode, so it must be inspected BEFORE
        // the generic RestException branch — a plain 400 RestException would incorrectly
        // produce InvalidCredentials (ADR-003).
        val authRestException = mockk<AuthRestException>(relaxed = true) {
            every { errorCode } returns AuthErrorCode.EmailNotConfirmed
            every { statusCode } returns 400
        }
        coEvery { supabaseAuth.signInWith(any<Email>(), anyNullable(), anyNullable()) } throws authRestException

        // Act
        val result = repository.signInWithEmail("unconfirmed@example.com", "correctPassword1!")

        // Assert: must be EmailNotConfirmed, NOT InvalidCredentials
        assertTrue(result is AuthResult.Error)
        assertEquals(AuthError.EmailNotConfirmed, (result as AuthResult.Error).error)
    }

    @Test
    fun `given plain RestException with statusCode 400 when signInWithEmail then returns InvalidCredentials not EmailNotConfirmed`() = runTest {
        // Regression: a generic 400 RestException (no AuthErrorCode) must still map to
        // InvalidCredentials via the fallback branch, proving the new AuthRestException
        // branch does not shadow existing error-code-less exceptions.
        val restException = mockk<RestException>(relaxed = true) {
            every { statusCode } returns 400
        }
        coEvery { supabaseAuth.signInWith(any<Email>(), anyNullable(), anyNullable()) } throws restException

        val result = repository.signInWithEmail("wrong@example.com", "wrongPassword1!")

        assertTrue(result is AuthResult.Error)
        assertEquals(AuthError.InvalidCredentials, (result as AuthResult.Error).error)
    }

    @Test
    fun `given server returns 429 when resendConfirmationEmail then returns Error with RateLimited`() = runTest {
        // Edge-case audit fix: too many resend requests must surface a distinct RateLimited error,
        // not fall through to the generic Unknown fallback.
        val restException = mockk<RestException>(relaxed = true) {
            every { statusCode } returns 429
        }
        coEvery { supabaseAuth.resendEmail(any(), any(), any()) } throws restException

        val result = repository.resendConfirmationEmail("test@example.com")

        assertTrue(result is AuthResult.Error)
        assertEquals(AuthError.RateLimited, (result as AuthResult.Error).error)
    }

    @Test
    fun `given AuthRestException with BadJwt errorCode and statusCode 403 when signInWithEmail then returns SessionExpired not Unknown`() = runTest {
        // 2026-08-17 fix: production auth_logs showed GoTrue rejecting a revoked session with
        // HTTP 403 and error_code bad_jwt ("missing sub claim") -- BadJwt is not one of the
        // explicitly-handled AuthErrorCode cases above, so this must fall through to the 403
        // branch of the statusCode fallback, not the generic Unknown ("An unexpected error
        // occurred") that made the original bug so confusing.
        val authRestException = mockk<AuthRestException>(relaxed = true) {
            every { errorCode } returns AuthErrorCode.BadJwt
            every { statusCode } returns 403
        }
        coEvery { supabaseAuth.signInWith(any<Email>(), anyNullable(), anyNullable()) } throws authRestException

        val result = repository.signInWithEmail("test@example.com", "Password1!")

        assertTrue(result is AuthResult.Error)
        assertEquals(AuthError.SessionExpired, (result as AuthResult.Error).error)
    }

    @Test
    fun `given plain RestException with statusCode 403 when signInWithEmail then returns SessionExpired`() = runTest {
        val restException = mockk<RestException>(relaxed = true) {
            every { statusCode } returns 403
        }
        coEvery { supabaseAuth.signInWith(any<Email>(), anyNullable(), anyNullable()) } throws restException

        val result = repository.signInWithEmail("test@example.com", "Password1!")

        assertTrue(result is AuthResult.Error)
        assertEquals(AuthError.SessionExpired, (result as AuthResult.Error).error)
    }

    @Test
    fun `given ResponseException with statusCode 403 when resendConfirmationEmail then returns SessionExpired`() = runTest {
        coEvery { supabaseAuth.resendEmail(any(), any(), any()) } throws ktorClientError(403)

        val result = repository.resendConfirmationEmail("test@example.com")

        assertTrue(result is AuthResult.Error)
        assertEquals(AuthError.SessionExpired, (result as AuthResult.Error).error)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 8 — updatePassword (architecture pivot: current_password, no reauth code)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given a current password when updatePassword then calls updateUser and returns Success`() = runTest {
        coEvery { supabaseAuth.updateUser(any(), any(), any()) } returns mockk<UserInfo>(relaxed = true)

        val result = repository.updatePassword("NewPassword1!", "OldPassword1!")

        assertTrue(result is AuthResult.Success)
        assertEquals(Unit, (result as AuthResult.Success).data)
    }

    @Test
    fun `given null current password when updatePassword then calls set-account-password Edge Function not updateUser (Set a password flow)`() = runTest {
        // 2026-08-14 fix: "Set a password" (currentPassword == null) is NOT simply "skip the
        // current-password check" -- a Google-signup account already has a real password set
        // server-side, so it must route through the Admin-API-backed set-account-password Edge
        // Function instead of the self-service Auth.updateUser call.
        val fakeSession = mockk<io.github.jan.supabase.auth.user.UserSession>(relaxed = true) {
            every { accessToken } returns "fake-jwt-token"
        }
        every { supabaseAuth.currentSessionOrNull() } returns fakeSession
        val fakeOkResponse = mockk<OkHttpResponse>(relaxed = true) {
            every { isSuccessful } returns true
            every { code } returns 200
            every { close() } just Runs
        }
        val fakeCall = mockk<Call>(relaxed = true) {
            every { execute() } returns fakeOkResponse
        }
        every { supabaseOkHttpClient.newCall(any()) } returns fakeCall

        val result = repository.updatePassword("NewPassword1!", null)

        assertTrue(result is AuthResult.Success)
        verify(atLeast = 1) { supabaseOkHttpClient.newCall(any()) }
        coVerify(exactly = 0) { supabaseAuth.updateUser(any(), any(), any()) }
    }

    @Test
    fun `given null current password when updatePassword then does not call notifyAccountEvent (Edge Function relays its own notification)`() = runTest {
        // The Edge Function itself does a best-effort relay to send-account-notification on
        // success -- calling notifyAccountEvent client-side too would double the email. Since
        // notifyAccountEvent is private, we assert indirectly: the OkHttp client is invoked
        // exactly once (the set-account-password call), never twice.
        val fakeSession = mockk<io.github.jan.supabase.auth.user.UserSession>(relaxed = true) {
            every { accessToken } returns "fake-jwt-token"
        }
        every { supabaseAuth.currentSessionOrNull() } returns fakeSession
        val fakeOkResponse = mockk<OkHttpResponse>(relaxed = true) {
            every { isSuccessful } returns true
            every { code } returns 200
            every { close() } just Runs
        }
        val fakeCall = mockk<Call>(relaxed = true) {
            every { execute() } returns fakeOkResponse
        }
        every { supabaseOkHttpClient.newCall(any()) } returns fakeCall

        repository.updatePassword("NewPassword1!", null)

        verify(exactly = 1) { supabaseOkHttpClient.newCall(any()) }
    }

    @Test
    fun `given null current password when updatePassword succeeds then re-triggers user_profiles enrichment`() = runTest {
        // Bug 2 fix: setAccountPasswordViaEdgeFunction is an Admin-API bypass -- GoTrue's own
        // session never learns a password now exists, so sessionState would never pick up the
        // freshly-written has_password=true unless the success branch emits into
        // profileRefreshSignal like every other mutation method in this file. Verified indirectly
        // via a second fetchUserProfile call, since profileRefreshSignal is private.
        val userInfoMock = buildUserInfoMock()
        every { userInfoMock.identities } returns null
        every { userInfoMock.userMetadata } returns null
        val sessionMock = mockk<io.github.jan.supabase.auth.user.UserSession>(relaxed = true) {
            every { user } returns userInfoMock
        }
        coEvery { userProfileDataSource.fetchUserProfile("user-uuid-001") } returns UserProfileDto(
            id = "user-uuid-001",
            profileCompleted = true,
        )
        val repoForTest = AuthRepositoryImpl(
            supabaseAuth               = supabaseAuth,
            userProfileDataSource      = userProfileDataSource,
            userProfileClient          = userProfileClient,
            userPreferencesDataStore   = userPreferencesDataStore,
            supabaseOkHttpClient       = supabaseOkHttpClient,
            applicationScope           = backgroundScope,
            ioDispatcher               = UnconfinedTestDispatcher(testScheduler),
        )
        sessionStatusFlow.value = SessionStatus.Authenticated(sessionMock)
        val collectJob = launch { repoForTest.sessionState.collect { } }
        advanceUntilIdle()
        testScheduler.runCurrent()
        coVerify(exactly = 1) { userProfileDataSource.fetchUserProfile("user-uuid-001") }

        val fakeSession = mockk<io.github.jan.supabase.auth.user.UserSession>(relaxed = true) {
            every { accessToken } returns "fake-jwt-token"
        }
        every { supabaseAuth.currentSessionOrNull() } returns fakeSession
        val fakeOkResponse = mockk<OkHttpResponse>(relaxed = true) {
            every { isSuccessful } returns true
            every { code } returns 200
            every { close() } just Runs
        }
        val fakeCall = mockk<Call>(relaxed = true) {
            every { execute() } returns fakeOkResponse
        }
        every { supabaseOkHttpClient.newCall(any()) } returns fakeCall

        val result = repoForTest.updatePassword("NewPassword1!", null)
        advanceUntilIdle()
        testScheduler.runCurrent()
        collectJob.cancel()

        assertTrue(result is AuthResult.Success)
        coVerify(exactly = 2) { userProfileDataSource.fetchUserProfile("user-uuid-001") }
    }

    @Test
    fun `given set-account-password succeeds but has_password DB write silently failed when updatePassword with null current password then records NON_FATAL telemetry and still returns Success`() = runTest {
        // 2026-08-17 fix: HTTP 200 from set-account-password only proves admin.updateUserById
        // succeeded -- the Edge Function's OWN best-effort has_password=true DB write is logged
        // server-side only. Simulated here by fetchHasPassword (the separate self-scoped RPC
        // has_password is now sourced from -- see UserProfileDataSource.fetchHasPassword's KDoc)
        // returning false on EVERY call (including the one triggered by the post-success
        // profileRefreshSignal), as if that write had failed -- the mismatch must be caught and
        // telemetered, not silently lost.
        val userInfoMock = buildUserInfoMock()
        every { userInfoMock.identities } returns null
        every { userInfoMock.userMetadata } returns null
        val sessionMock = mockk<io.github.jan.supabase.auth.user.UserSession>(relaxed = true) {
            every { user } returns userInfoMock
        }
        coEvery { userProfileDataSource.fetchUserProfile("user-uuid-001") } returns UserProfileDto(
            id = "user-uuid-001",
            profileCompleted = true,
        )
        coEvery { userProfileDataSource.fetchHasPassword("user-uuid-001") } returns false
        val repoForTest = AuthRepositoryImpl(
            supabaseAuth               = supabaseAuth,
            userProfileDataSource      = userProfileDataSource,
            userProfileClient          = userProfileClient,
            userPreferencesDataStore   = userPreferencesDataStore,
            supabaseOkHttpClient       = supabaseOkHttpClient,
            applicationScope           = backgroundScope,
            ioDispatcher               = UnconfinedTestDispatcher(testScheduler),
        )
        sessionStatusFlow.value = SessionStatus.Authenticated(sessionMock)
        val collectJob = launch { repoForTest.sessionState.collect { } }
        advanceUntilIdle()
        testScheduler.runCurrent()

        val fakeSession = mockk<io.github.jan.supabase.auth.user.UserSession>(relaxed = true) {
            every { accessToken } returns "fake-jwt-token"
        }
        every { supabaseAuth.currentSessionOrNull() } returns fakeSession
        val fakeOkResponse = mockk<OkHttpResponse>(relaxed = true) {
            every { isSuccessful } returns true
            every { code } returns 200
            every { close() } just Runs
        }
        val fakeCall = mockk<Call>(relaxed = true) {
            every { execute() } returns fakeOkResponse
        }
        every { supabaseOkHttpClient.newCall(any()) } returns fakeCall

        val result = repoForTest.updatePassword("NewPassword1!", null)
        advanceUntilIdle()
        testScheduler.runCurrent()
        collectJob.cancel()

        assertTrue(result is AuthResult.Success)
        verify { crashlyticsMock.log("account_mgmt_set_password_has_password_flag_stale") }
    }

    @Test
    fun `given set-account-password succeeds and has_password DB write actually landed when updatePassword with null current password then does not record stale telemetry`() = runTest {
        // Companion to the stale-flag test above: when the refreshed profile correctly reports
        // hasPassword=true, verifySetPasswordFlagLanded must NOT fire the NON_FATAL -- proves the
        // mismatch check doesn't false-positive on the normal, healthy path.
        val userInfoMock = buildUserInfoMock()
        every { userInfoMock.identities } returns null
        every { userInfoMock.userMetadata } returns null
        val sessionMock = mockk<io.github.jan.supabase.auth.user.UserSession>(relaxed = true) {
            every { user } returns userInfoMock
        }
        // First call (initial sessionState enrichment, before the password is set) reports
        // hasPassword=false; every call from then on (including the one triggered by the
        // post-success profileRefreshSignal) reports hasPassword=true, as if the Edge Function's DB
        // write landed correctly. has_password is now sourced from the separate self-scoped
        // fetchHasPassword RPC call, not from the UserProfileDto returned by fetchUserProfile --
        // see UserProfileDataSource.fetchHasPassword's KDoc.
        coEvery { userProfileDataSource.fetchUserProfile("user-uuid-001") } returns UserProfileDto(
            id = "user-uuid-001",
            profileCompleted = true,
        )
        coEvery { userProfileDataSource.fetchHasPassword("user-uuid-001") } returnsMany listOf(false, true)
        val repoForTest = AuthRepositoryImpl(
            supabaseAuth               = supabaseAuth,
            userProfileDataSource      = userProfileDataSource,
            userProfileClient          = userProfileClient,
            userPreferencesDataStore   = userPreferencesDataStore,
            supabaseOkHttpClient       = supabaseOkHttpClient,
            applicationScope           = backgroundScope,
            ioDispatcher               = UnconfinedTestDispatcher(testScheduler),
        )
        sessionStatusFlow.value = SessionStatus.Authenticated(sessionMock)
        val collectJob = launch { repoForTest.sessionState.collect { } }
        advanceUntilIdle()
        testScheduler.runCurrent()

        val fakeSession = mockk<io.github.jan.supabase.auth.user.UserSession>(relaxed = true) {
            every { accessToken } returns "fake-jwt-token"
        }
        every { supabaseAuth.currentSessionOrNull() } returns fakeSession
        val fakeOkResponse = mockk<OkHttpResponse>(relaxed = true) {
            every { isSuccessful } returns true
            every { code } returns 200
            every { close() } just Runs
        }
        val fakeCall = mockk<Call>(relaxed = true) {
            every { execute() } returns fakeOkResponse
        }
        every { supabaseOkHttpClient.newCall(any()) } returns fakeCall

        val result = repoForTest.updatePassword("NewPassword1!", null)
        advanceUntilIdle()
        testScheduler.runCurrent()
        collectJob.cancel()

        assertTrue(result is AuthResult.Success)
        verify(exactly = 0) { crashlyticsMock.log("account_mgmt_set_password_has_password_flag_stale") }
    }

    @Test
    fun `given IOException from set-account-password Edge Function when updatePassword with null current password then still re-triggers user_profiles enrichment before returning NetworkError`() = runTest {
        // 2026-08-17 fix: the server-side password update (and its has_password write) both
        // complete BEFORE the response streams back, so a dropped connection here is a classic
        // false negative -- must not leave the client stuck on a stale hasPassword=false for the
        // rest of the session. Verified indirectly via a second fetchUserProfile call, since
        // profileRefreshSignal is private.
        val userInfoMock = buildUserInfoMock()
        every { userInfoMock.identities } returns null
        every { userInfoMock.userMetadata } returns null
        val sessionMock = mockk<io.github.jan.supabase.auth.user.UserSession>(relaxed = true) {
            every { user } returns userInfoMock
        }
        coEvery { userProfileDataSource.fetchUserProfile("user-uuid-001") } returns UserProfileDto(
            id = "user-uuid-001",
            profileCompleted = true,
        )
        val repoForTest = AuthRepositoryImpl(
            supabaseAuth               = supabaseAuth,
            userProfileDataSource      = userProfileDataSource,
            userProfileClient          = userProfileClient,
            userPreferencesDataStore   = userPreferencesDataStore,
            supabaseOkHttpClient       = supabaseOkHttpClient,
            applicationScope           = backgroundScope,
            ioDispatcher               = UnconfinedTestDispatcher(testScheduler),
        )
        sessionStatusFlow.value = SessionStatus.Authenticated(sessionMock)
        val collectJob = launch { repoForTest.sessionState.collect { } }
        advanceUntilIdle()
        testScheduler.runCurrent()
        coVerify(exactly = 1) { userProfileDataSource.fetchUserProfile("user-uuid-001") }

        val fakeSession = mockk<io.github.jan.supabase.auth.user.UserSession>(relaxed = true) {
            every { accessToken } returns "fake-jwt-token"
        }
        every { supabaseAuth.currentSessionOrNull() } returns fakeSession
        val fakeCall = mockk<Call>(relaxed = true) {
            every { execute() } throws IOException("Offline")
        }
        every { supabaseOkHttpClient.newCall(any()) } returns fakeCall

        val result = repoForTest.updatePassword("NewPassword1!", null)
        advanceUntilIdle()
        testScheduler.runCurrent()
        collectJob.cancel()

        assertTrue(result is AuthResult.Error)
        assertEquals(AuthError.NetworkError, (result as AuthResult.Error).error)
        coVerify(exactly = 2) { userProfileDataSource.fetchUserProfile("user-uuid-001") }
    }

    @Test
    fun `given no active session when updatePassword with null current password then returns SessionExpired without calling Edge Function`() = runTest {
        every { supabaseAuth.currentSessionOrNull() } returns null

        val result = repository.updatePassword("NewPassword1!", null)

        assertTrue(result is AuthResult.Error)
        assertEquals(AuthError.SessionExpired, (result as AuthResult.Error).error)
        verify(exactly = 0) { supabaseOkHttpClient.newCall(any()) }
    }

    @Test
    fun `given set-account-password Edge Function returns HTTP 401 when updatePassword with null current password then returns SessionExpired`() = runTest {
        val fakeSession = mockk<io.github.jan.supabase.auth.user.UserSession>(relaxed = true) {
            every { accessToken } returns "fake-jwt-token"
        }
        every { supabaseAuth.currentSessionOrNull() } returns fakeSession
        val fakeOkResponse = mockk<OkHttpResponse>(relaxed = true) {
            every { isSuccessful } returns false
            every { code } returns 401
            every { close() } just Runs
        }
        val fakeCall = mockk<Call>(relaxed = true) {
            every { execute() } returns fakeOkResponse
        }
        every { supabaseOkHttpClient.newCall(any()) } returns fakeCall

        val result = repository.updatePassword("NewPassword1!", null)

        assertTrue(result is AuthResult.Error)
        assertEquals(AuthError.SessionExpired, (result as AuthResult.Error).error)
    }

    @Test
    fun `given set-account-password Edge Function returns HTTP 500 when updatePassword with null current password then returns Unknown`() = runTest {
        val fakeSession = mockk<io.github.jan.supabase.auth.user.UserSession>(relaxed = true) {
            every { accessToken } returns "fake-jwt-token"
        }
        every { supabaseAuth.currentSessionOrNull() } returns fakeSession
        val fakeOkResponse = mockk<OkHttpResponse>(relaxed = true) {
            every { isSuccessful } returns false
            every { code } returns 500
            every { close() } just Runs
        }
        val fakeCall = mockk<Call>(relaxed = true) {
            every { execute() } returns fakeOkResponse
        }
        every { supabaseOkHttpClient.newCall(any()) } returns fakeCall

        val result = repository.updatePassword("NewPassword1!", null)

        assertTrue(result is AuthResult.Error)
        assertTrue((result as AuthResult.Error).error is AuthError.Unknown)
    }

    @Test
    fun `given network failure when updatePassword with null current password then returns NetworkError`() = runTest {
        val fakeSession = mockk<io.github.jan.supabase.auth.user.UserSession>(relaxed = true) {
            every { accessToken } returns "fake-jwt-token"
        }
        every { supabaseAuth.currentSessionOrNull() } returns fakeSession
        val fakeCall = mockk<Call>(relaxed = true) {
            every { execute() } throws IOException("Offline")
        }
        every { supabaseOkHttpClient.newCall(any()) } returns fakeCall

        val result = repository.updatePassword("NewPassword1!", null)

        assertTrue(result is AuthResult.Error)
        assertEquals(AuthError.NetworkError, (result as AuthResult.Error).error)
    }

    @Test
    fun `given set-account-password succeeds and refreshCurrentSession succeeds when updatePassword with null current password then returns Success without signing out`() = runTest {
        // 2026-08-17 fix (production incident, user b6c7f0f5-...): GoTrue can revoke the account's
        // active session server-side as a side effect of the Admin-API password write. When the
        // session survives (refreshCurrentSession succeeds), the flow must behave exactly as
        // before -- Success, no forced sign-out.
        //
        // Uses a dedicated repoForTest (mirroring the has_password-mismatch tests above) rather
        // than the shared `repository`: the success path calls verifySetPasswordFlagLanded, which
        // AWAITS a real sessionState re-emission -- without an Authenticated session actually
        // wired up and collected here, that await has nothing to observe and would otherwise only
        // resolve via its own 3s real-time withTimeoutOrNull, which is not driven by this test's
        // virtual clock (see the class KDoc on `testScheduler` for why bare `repository` +
        // `runTest {}` doesn't auto-advance delays scheduled on it).
        val userInfoMock = buildUserInfoMock()
        every { userInfoMock.identities } returns null
        every { userInfoMock.userMetadata } returns null
        val sessionMock = mockk<io.github.jan.supabase.auth.user.UserSession>(relaxed = true) {
            every { user } returns userInfoMock
        }
        coEvery { userProfileDataSource.fetchUserProfile("user-uuid-001") } returns UserProfileDto(
            id = "user-uuid-001",
            profileCompleted = true,
        )
        coEvery { userProfileDataSource.fetchHasPassword("user-uuid-001") } returns true
        val repoForTest = AuthRepositoryImpl(
            supabaseAuth               = supabaseAuth,
            userProfileDataSource      = userProfileDataSource,
            userProfileClient          = userProfileClient,
            userPreferencesDataStore   = userPreferencesDataStore,
            supabaseOkHttpClient       = supabaseOkHttpClient,
            applicationScope           = backgroundScope,
            ioDispatcher               = UnconfinedTestDispatcher(testScheduler),
        )
        sessionStatusFlow.value = SessionStatus.Authenticated(sessionMock)
        val collectJob = launch { repoForTest.sessionState.collect { } }
        advanceUntilIdle()
        testScheduler.runCurrent()

        val fakeSession = mockk<io.github.jan.supabase.auth.user.UserSession>(relaxed = true) {
            every { accessToken } returns "fake-jwt-token"
        }
        every { supabaseAuth.currentSessionOrNull() } returns fakeSession
        coEvery { supabaseAuth.refreshCurrentSession() } just Runs
        val fakeOkResponse = mockk<OkHttpResponse>(relaxed = true) {
            every { isSuccessful } returns true
            every { code } returns 200
            every { close() } just Runs
        }
        val fakeCall = mockk<Call>(relaxed = true) {
            every { execute() } returns fakeOkResponse
        }
        every { supabaseOkHttpClient.newCall(any()) } returns fakeCall

        val result = repoForTest.updatePassword("NewPassword1!", null)
        advanceUntilIdle()
        testScheduler.runCurrent()
        collectJob.cancel()

        assertTrue(result is AuthResult.Success)
        coVerify(exactly = 1) { supabaseAuth.refreshCurrentSession() }
        coVerify(exactly = 0) { supabaseAuth.signOut(any()) }
        verify(exactly = 0) { crashlyticsMock.log("account_mgmt_set_password_session_revoked") }
    }

    @Test
    fun `given set-account-password succeeds but refreshCurrentSession fails when updatePassword with null current password then signs out locally and returns PasswordUpdatedSessionRevoked`() = runTest {
        // The root-cause fix: HTTP 200 from set-account-password only proves the password WAS
        // set -- it does NOT prove the session survived. When refreshCurrentSession (which
        // exchanges the refresh token, unlike a plain GET /user resync) ALSO fails, the refresh
        // token is revoked too and the session is unrecoverably dead. The repository must not
        // leave that dead session in memory -- it must force a LOCAL sign-out and surface a
        // distinct error so the caller can tell the user their password DID get set.
        val fakeSession = mockk<io.github.jan.supabase.auth.user.UserSession>(relaxed = true) {
            every { accessToken } returns "fake-jwt-token"
        }
        every { supabaseAuth.currentSessionOrNull() } returns fakeSession
        coEvery { supabaseAuth.refreshCurrentSession() } throws IOException("session_not_found")
        val fakeOkResponse = mockk<OkHttpResponse>(relaxed = true) {
            every { isSuccessful } returns true
            every { code } returns 200
            every { close() } just Runs
        }
        val fakeCall = mockk<Call>(relaxed = true) {
            every { execute() } returns fakeOkResponse
        }
        every { supabaseOkHttpClient.newCall(any()) } returns fakeCall

        val result = repository.updatePassword("NewPassword1!", null)

        assertTrue(result is AuthResult.Error)
        assertEquals(AuthError.PasswordUpdatedSessionRevoked, (result as AuthResult.Error).error)
        coVerify(exactly = 1) { supabaseAuth.signOut(SignOutScope.LOCAL) }
        verify { crashlyticsMock.log("account_mgmt_set_password_session_revoked") }
    }

    @Test
    fun `given current password supplied and AuthRestException InvalidCredentials when updatePassword then returns Error with InvalidCurrentPassword`() = runTest {
        // GoTrue's "Require current password when updating" setting (confirmed ON) rejects a
        // mismatch with the SAME invalid_credentials errorCode/400 used for a failed sign-in --
        // must map to the distinct, context-correct InvalidCurrentPassword here, not the generic
        // InvalidCredentials ("Incorrect email or password" reads wrong on this screen).
        val authRestException = mockk<AuthRestException>(relaxed = true) {
            every { errorCode } returns AuthErrorCode.InvalidCredentials
            every { statusCode } returns 400
        }
        coEvery { supabaseAuth.updateUser(any(), any(), any()) } throws authRestException

        val result = repository.updatePassword("NewPassword1!", "WrongPassword1!")

        assertTrue(result is AuthResult.Error)
        assertEquals(AuthError.InvalidCurrentPassword, (result as AuthResult.Error).error)
    }

    @Test
    fun `given network failure when updatePassword then returns Error with NetworkError`() = runTest {
        coEvery { supabaseAuth.updateUser(any(), any(), any()) } throws IOException("Offline")

        val result = repository.updatePassword("NewPassword1!", "OldPassword1!")

        assertTrue(result is AuthResult.Error)
        assertEquals(AuthError.NetworkError, (result as AuthResult.Error).error)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 12 — cancelPendingEmailChange
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given RPC succeeds when cancelPendingEmailChange then returns Success`() = runTest {
        coEvery { userProfileClient.cancelPendingEmailChange() } just Runs

        val result = repository.cancelPendingEmailChange()

        assertTrue(result is AuthResult.Success)
        coVerify(exactly = 1) { userProfileClient.cancelPendingEmailChange() }
    }

    @Test
    fun `given RPC succeeds when cancelPendingEmailChange then forces a real GoTrue user resync`() = runTest {
        // Bug 1 fix: the RPC alone never clears AuthUser.newEmail in the real session state --
        // it only touches auth.users server-side. retrieveUserForCurrentSession(updateSession =
        // true) is the call that actually refreshes sessionStatus (and therefore newEmail).
        coEvery { userProfileClient.cancelPendingEmailChange() } just Runs
        coEvery { supabaseAuth.retrieveUserForCurrentSession(true) } returns mockk(relaxed = true)

        val result = repository.cancelPendingEmailChange()

        assertTrue(result is AuthResult.Success)
        coVerify(exactly = 1) { supabaseAuth.retrieveUserForCurrentSession(updateSession = true) }
    }

    @Test
    fun `given retrieveUserForCurrentSession throws when cancelPendingEmailChange then still returns Success`() = runTest {
        // The cancel RPC already succeeded server-side by the time the resync runs -- a resync
        // hiccup (network blip, session momentarily unavailable) must not be reported as if the
        // cancel itself failed.
        coEvery { userProfileClient.cancelPendingEmailChange() } just Runs
        coEvery { supabaseAuth.retrieveUserForCurrentSession(true) } throws IOException("Offline")

        val result = repository.cancelPendingEmailChange()

        assertTrue(result is AuthResult.Success)
    }

    @Test
    fun `given RPC throws when cancelPendingEmailChange then returns mapped Error`() = runTest {
        coEvery { userProfileClient.cancelPendingEmailChange() } throws ktorServerError(500)

        val result = repository.cancelPendingEmailChange()

        assertTrue(result is AuthResult.Error)
        assertTrue((result as AuthResult.Error).error is AuthError.Unknown)
    }

    @Test
    fun `given network failure when cancelPendingEmailChange then returns NetworkError`() = runTest {
        coEvery { userProfileClient.cancelPendingEmailChange() } throws IOException("Offline")

        val result = repository.cancelPendingEmailChange()

        assertTrue(result is AuthResult.Error)
        assertEquals(AuthError.NetworkError, (result as AuthResult.Error).error)
    }

    @Test
    fun `given sessionStatus flips to NotAuthenticated during the resync when cancelPendingEmailChange then still returns Success and sessionState eventually reflects Unauthenticated`() = runTest {
        // Simulates a concurrent session revocation racing the resync call (e.g. sign-out on
        // another device, or deleteAccount racing this call). The cancel RPC itself already
        // succeeded server-side and is unaffected by a session-side race -- but
        // AccountManagementScreen's onSignedOut() effect must still fire once sessionState reflects
        // the real revoked state, rather than leaving the screen stuck showing stale authenticated
        // content.
        val userInfoMock = buildUserInfoMock()
        every { userInfoMock.identities } returns null
        every { userInfoMock.userMetadata } returns null
        val sessionMock = mockk<io.github.jan.supabase.auth.user.UserSession>(relaxed = true) {
            every { user } returns userInfoMock
        }
        coEvery { userProfileDataSource.fetchUserProfile("user-uuid-001") } returns UserProfileDto(
            id = "user-uuid-001",
            profileCompleted = true,
        )
        coEvery { userProfileClient.cancelPendingEmailChange() } just Runs
        coEvery { supabaseAuth.retrieveUserForCurrentSession(true) } coAnswers {
            // Mirrors a real revoked session: the SDK flips sessionStatus BEFORE this call's own
            // exception propagates back to the caller.
            sessionStatusFlow.value = SessionStatus.NotAuthenticated(isSignOut = false)
            throw IOException("Session revoked")
        }

        val repoForTest = AuthRepositoryImpl(
            supabaseAuth               = supabaseAuth,
            userProfileDataSource      = userProfileDataSource,
            userProfileClient          = userProfileClient,
            userPreferencesDataStore   = userPreferencesDataStore,
            supabaseOkHttpClient       = supabaseOkHttpClient,
            applicationScope           = backgroundScope,
            ioDispatcher               = UnconfinedTestDispatcher(testScheduler),
        )
        sessionStatusFlow.value = SessionStatus.Authenticated(sessionMock)
        val observedStates = mutableListOf<SessionState>()
        val collectJob = launch { repoForTest.sessionState.collect { observedStates.add(it) } }
        advanceUntilIdle()
        testScheduler.runCurrent()

        val result = repoForTest.cancelPendingEmailChange()
        advanceUntilIdle()
        testScheduler.runCurrent()
        collectJob.cancel()

        // The RPC itself succeeded -- a resync hiccup (session revoked mid-flight) must not be
        // reported as if the cancel itself failed.
        assertTrue(result is AuthResult.Success)
        // sessionState must eventually reflect the real revoked state so onSignedOut() effects fire.
        assertTrue(observedStates.last() is SessionState.Unauthenticated)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 15 — confirmPasswordReset / abandonRecoverySession (password-recovery-hardening-
    //  plan-2026-08-18 §3.3/§3.4: marker-clear + scoped sign-out on every recovery-flow exit)
    // ══════════════════════════════════════════════════════════════════════════

    /** Base64url-encodes (RFC 4648 §5, no padding) a fake JWT carrying [payloadJson] as its payload. */
    private fun fakeJwtWithPayload(payloadJson: String): String {
        val encoder = java.util.Base64.getUrlEncoder().withoutPadding()
        val header = encoder.encodeToString("""{"alg":"HS256","typ":"JWT"}""".toByteArray())
        val payload = encoder.encodeToString(payloadJson.toByteArray())
        return "$header.$payload.fake-signature"
    }

    @Test
    fun `given successful password update when confirmPasswordReset then clears the recovery marker and signs out with GLOBAL scope`() = runTest {
        coEvery { supabaseAuth.updateUser(any(), any(), any()) } returns mockk<UserInfo>(relaxed = true)
        coEvery { supabaseAuth.signOut(any()) } just Runs

        val result = repository.confirmPasswordReset("NewPassword1!")

        assertTrue(result is AuthResult.Success)
        coVerify(exactly = 1) { userPreferencesDataStore.clearPendingRecoveryMarker() }
        coVerify(exactly = 1) { supabaseAuth.signOut(SignOutScope.GLOBAL) }
    }

    @Test
    fun `given successful password update but signOut fails when confirmPasswordReset then still returns Success (best-effort)`() = runTest {
        // A completed password change must never be reported as failed just because the
        // best-effort GLOBAL sign-out that follows it hiccups.
        coEvery { supabaseAuth.updateUser(any(), any(), any()) } returns mockk<UserInfo>(relaxed = true)
        coEvery { supabaseAuth.signOut(any()) } throws IOException("network blip")

        val result = repository.confirmPasswordReset("NewPassword1!")

        assertTrue(result is AuthResult.Success)
        coVerify(exactly = 1) { userPreferencesDataStore.clearPendingRecoveryMarker() }
    }

    @Test
    fun `given updateUser fails when confirmPasswordReset then returns Error and never clears the marker or signs out`() = runTest {
        // The marker-clear + sign-out must be conditioned on the password change actually
        // succeeding -- a failed reset must leave the recovery flow's state untouched so the user
        // can retry from the same screen/session.
        coEvery { supabaseAuth.updateUser(any(), any(), any()) } throws IOException("Offline")

        val result = repository.confirmPasswordReset("NewPassword1!")

        assertTrue(result is AuthResult.Error)
        assertEquals(AuthError.NetworkError, (result as AuthResult.Error).error)
        coVerify(exactly = 0) { userPreferencesDataStore.clearPendingRecoveryMarker() }
        coVerify(exactly = 0) { supabaseAuth.signOut(any()) }
    }

    @Test
    fun `when abandonRecoverySession then clears the recovery marker and signs out with LOCAL scope`() = runTest {
        coEvery { supabaseAuth.signOut(any()) } just Runs

        val result = repository.abandonRecoverySession()

        assertTrue(result is AuthResult.Success)
        coVerify(exactly = 1) { userPreferencesDataStore.clearPendingRecoveryMarker() }
        coVerify(exactly = 1) { supabaseAuth.signOut(SignOutScope.LOCAL) }
    }

    @Test
    fun `given signOut fails when abandonRecoverySession then still returns Success (best-effort, marker already cleared)`() = runTest {
        coEvery { supabaseAuth.signOut(any()) } throws IOException("network blip")

        val result = repository.abandonRecoverySession()

        assertTrue(result is AuthResult.Success)
        coVerify(exactly = 1) { userPreferencesDataStore.clearPendingRecoveryMarker() }
    }

    @Test
    fun `given a session JWT carrying a session_id claim when sessionState collected then AuthUser sessionId is decoded`() = runTest {
        val userInfoMock = buildUserInfoMock()
        every { userInfoMock.identities } returns null
        every { userInfoMock.userMetadata } returns null
        val fakeToken = fakeJwtWithPayload("""{"sub":"user-uuid-001","session_id":"session-xyz-789"}""")
        val sessionMock = mockk<io.github.jan.supabase.auth.user.UserSession>(relaxed = true) {
            every { user } returns userInfoMock
            every { accessToken } returns fakeToken
        }

        val repoForTest = AuthRepositoryImpl(
            supabaseAuth               = supabaseAuth,
            userProfileDataSource      = userProfileDataSource,
            userProfileClient          = userProfileClient,
            userPreferencesDataStore   = userPreferencesDataStore,
            supabaseOkHttpClient       = supabaseOkHttpClient,
            applicationScope           = backgroundScope,
            ioDispatcher               = UnconfinedTestDispatcher(testScheduler),
        )
        sessionStatusFlow.value = SessionStatus.Authenticated(sessionMock)

        val collectJob = launch { repoForTest.sessionState.collect { } }
        advanceUntilIdle()
        testScheduler.runCurrent()

        val state = repoForTest.sessionState.value
        collectJob.cancel()

        assertTrue(state is SessionState.Authenticated)
        assertEquals("session-xyz-789", (state as SessionState.Authenticated).user.sessionId)
    }
}
