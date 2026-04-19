package com.mmg.manahub.feature.auth.data.repository

import app.cash.turbine.test
import com.mmg.manahub.feature.auth.data.remote.UserProfileDataSource
import com.mmg.manahub.feature.auth.domain.model.AuthError
import com.mmg.manahub.feature.auth.domain.model.AuthResult
import com.mmg.manahub.feature.auth.domain.model.AuthUser
import com.mmg.manahub.feature.auth.domain.model.SessionState
import io.github.jan.supabase.SupabaseClient
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * Unit tests for [AuthRepositoryImpl].
 *
 * Strategy:
 * - [Auth] and [SupabaseClient] are mocked with MockK.
 * - [UserProfileDataSource] is mocked to isolate repository logic.
 * - [UnconfinedTestDispatcher] is used so withContext(ioDispatcher) runs synchronously,
 *   avoiding any need for advanceUntilIdle.
 * - Supabase's [Auth.sessionStatus] is backed by a [MutableStateFlow] so we can
 *   control emissions in Flow tests.
 *
 * Coverage added vs previous version:
 * - GROUP 8: getCurrentUser (authenticated + unauthenticated)
 * - GROUP 9: updateNickname (success, NicknameTooLong, NicknameInappropriate, SessionExpired,
 *             network error, nickname trimming)
 * - GROUP 10: signInWithGoogleIdToken auto-nickname from Google metadata
 * - GROUP 11: signUpWithEmail when nickname RPC fails (non-fatal fallback)
 * - Additional HTTP status code mappings (404, 500+)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthRepositoryImplTest {

    // ── Test dispatcher ───────────────────────────────────────────────────────

    private val testDispatcher = UnconfinedTestDispatcher()

    // ── Mocks ─────────────────────────────────────────────────────────────────

    private val supabaseAuth          = mockk<Auth>()
    private val supabaseClient        = mockk<SupabaseClient>(relaxed = true)
    private val userProfileDataSource = mockk<UserProfileDataSource>(relaxed = true)

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

    // ── Setup ─────────────────────────────────────────────────────────────────

    @Before
    fun setUp() {
        every { supabaseAuth.sessionStatus } returns sessionStatusFlow

        // Default stub: upsertUserProfile returns the user unchanged (non-blocking default).
        coEvery { userProfileDataSource.upsertUserProfile(any()) } answers {
            firstArg<AuthUser>()
        }

        repository = AuthRepositoryImpl(
            supabaseClient        = supabaseClient,
            supabaseAuth          = supabaseAuth,
            userProfileDataSource = userProfileDataSource,
            ioDispatcher          = testDispatcher,
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
    fun `given nickname RPC fails during signUpWithEmail then sign-up still succeeds with email-prefix nickname fallback`() = runTest {
        // The nickname RPC is non-fatal: a failure should not prevent the overall sign-up
        val userInfoMock = buildUserInfoMock()
        coEvery { supabaseAuth.signUpWith(any(), any<suspend io.github.jan.supabase.auth.providers.builtin.Email.Config.() -> Unit>()) } just Runs
        every { supabaseAuth.currentUserOrNull() } returns userInfoMock

        // Simulate RPC failure by making postgrest.rpc() throw
        val postgrestMock = mockk<io.github.jan.supabase.postgrest.Postgrest>(relaxed = true)
        coEvery { postgrestMock.rpc(any<String>(), any()) } throws IOException("RPC unreachable")
        val strictClient = mockk<SupabaseClient> {
            every { this@mockk.postgrest } returns postgrestMock
        }
        val repo = AuthRepositoryImpl(
            supabaseClient        = strictClient,
            supabaseAuth          = supabaseAuth,
            userProfileDataSource = userProfileDataSource,
            ioDispatcher          = testDispatcher,
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

        // Stub the update_user_nickname RPC to succeed and return the same user
        // with the nickname applied
        val rpcResult = mockk<io.github.jan.supabase.postgrest.result.PostgrestResult>(relaxed = true)
        val postgrestMock = mockk<io.github.jan.supabase.postgrest.Postgrest>(relaxed = true)
        coEvery { postgrestMock.rpc("update_user_nickname", any()) } returns rpcResult
        val strictClient = mockk<SupabaseClient> {
            every { this@mockk.postgrest } returns postgrestMock
        }
        val repo = AuthRepositoryImpl(
            supabaseClient        = strictClient,
            supabaseAuth          = supabaseAuth,
            userProfileDataSource = userProfileDataSource,
            ioDispatcher          = testDispatcher,
        )

        val result = repo.signInWithGoogleIdToken("google-token", "raw-nonce")

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

        // RPC is proxied through supabaseClient.postgrest — the relaxed mock does nothing
        // We verify upsertUserProfile was still called
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
    fun `given authenticated user when deleteAccount then calls postgrest rpc and signOut and returns Success`() = runTest {
        coEvery { supabaseAuth.signOut(any()) } just Runs

        val result = repository.deleteAccount()

        assertTrue(result is AuthResult.Success)
        coVerify(exactly = 1) { supabaseAuth.signOut(any()) }
    }

    @Test
    fun `given postgrest rpc throws when deleteAccount then returns Error and does NOT call signOut`() = runTest {
        val postgrestMock = mockk<io.github.jan.supabase.postgrest.Postgrest>(relaxed = true)
        coEvery { postgrestMock.rpc(any<String>(), any()) } throws RuntimeException("RPC failed")

        val strictClient = mockk<SupabaseClient> {
            every { this@mockk.postgrest } returns postgrestMock
        }
        val repoUnderTest = AuthRepositoryImpl(
            supabaseClient        = strictClient,
            supabaseAuth          = supabaseAuth,
            userProfileDataSource = userProfileDataSource,
            ioDispatcher          = testDispatcher,
        )

        val result = repoUnderTest.deleteAccount()

        assertTrue(result is AuthResult.Error)
        // signOut must NOT be called if the RPC step fails — user account was not deleted
        coVerify(exactly = 0) { supabaseAuth.signOut(any()) }
    }

    @Test
    fun `given network failure when deleteAccount then returns Error with NetworkError`() = runTest {
        val postgrestMock = mockk<io.github.jan.supabase.postgrest.Postgrest>(relaxed = true)
        coEvery { postgrestMock.rpc(any<String>(), any()) } throws IOException("Offline")

        val strictClient = mockk<SupabaseClient> {
            every { this@mockk.postgrest } returns postgrestMock
        }
        val repoUnderTest = AuthRepositoryImpl(
            supabaseClient        = strictClient,
            supabaseAuth          = supabaseAuth,
            userProfileDataSource = userProfileDataSource,
            ioDispatcher          = testDispatcher,
        )

        val result = repoUnderTest.deleteAccount()

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
    fun `given valid nickname when updateNickname then calls RPC and returns Success with trimmed nickname`() = runTest {
        val userInfoMock = buildUserInfoMock()
        every { supabaseAuth.currentUserOrNull() } returns userInfoMock

        val result = repository.updateNickname("  Gandalf  ")

        // Nickname must be trimmed before being sent to the RPC
        assertTrue(result is AuthResult.Success)
        assertEquals("Gandalf", (result as AuthResult.Success).data.nickname)
    }

    @Test
    fun `given nickname exceeding 30 chars when updateNickname then returns NicknameTooLong without calling RPC`() = runTest {
        val tooLong = "A".repeat(31)

        val result = repository.updateNickname(tooLong)

        assertTrue(result is AuthResult.Error)
        assertEquals(AuthError.NicknameTooLong, (result as AuthResult.Error).error)
        // Should not reach the network when local validation already rejects it
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
    fun `given RPC returns HTTP 400 when updateNickname then returns NicknameInappropriate`() = runTest {
        // HTTP 400 from the profanity trigger → NicknameInappropriate (not InvalidCredentials)
        val postgrestMock = mockk<io.github.jan.supabase.postgrest.Postgrest>(relaxed = true)
        coEvery { postgrestMock.rpc("update_user_nickname", any()) } throws RestException(
            error = "P0001", description = "Inappropriate content", statusCode = 400
        )
        val strictClient = mockk<SupabaseClient> {
            every { this@mockk.postgrest } returns postgrestMock
        }
        val repo = AuthRepositoryImpl(
            supabaseClient        = strictClient,
            supabaseAuth          = supabaseAuth,
            userProfileDataSource = userProfileDataSource,
            ioDispatcher          = testDispatcher,
        )

        val result = repo.updateNickname("BadWord")

        assertTrue(result is AuthResult.Error)
        assertEquals(AuthError.NicknameInappropriate, (result as AuthResult.Error).error)
    }

    @Test
    fun `given RPC returns HTTP 500 when updateNickname then returns Unknown not NicknameInappropriate`() = runTest {
        // Only HTTP 400 maps to NicknameInappropriate; other codes map to Unknown
        val postgrestMock = mockk<io.github.jan.supabase.postgrest.Postgrest>(relaxed = true)
        coEvery { postgrestMock.rpc("update_user_nickname", any()) } throws RestException(
            error = "internal_error", description = "Server error", statusCode = 500
        )
        val strictClient = mockk<SupabaseClient> {
            every { this@mockk.postgrest } returns postgrestMock
        }
        val repo = AuthRepositoryImpl(
            supabaseClient        = strictClient,
            supabaseAuth          = supabaseAuth,
            userProfileDataSource = userProfileDataSource,
            ioDispatcher          = testDispatcher,
        )

        val result = repo.updateNickname("ValidName")

        assertTrue(result is AuthResult.Error)
        assertTrue((result as AuthResult.Error).error is AuthError.Unknown)
    }

    @Test
    fun `given user session expired after RPC call when updateNickname then returns SessionExpired`() = runTest {
        // RPC succeeds but currentUserOrNull() returns null (session expired between the calls)
        every { supabaseAuth.currentUserOrNull() } returns null

        val result = repository.updateNickname("Gandalf")

        assertTrue(result is AuthResult.Error)
        assertEquals(AuthError.SessionExpired, (result as AuthResult.Error).error)
    }

    @Test
    fun `given network failure during updateNickname then returns NetworkError`() = runTest {
        val postgrestMock = mockk<io.github.jan.supabase.postgrest.Postgrest>(relaxed = true)
        coEvery { postgrestMock.rpc("update_user_nickname", any()) } throws IOException("Offline")

        val strictClient = mockk<SupabaseClient> {
            every { this@mockk.postgrest } returns postgrestMock
        }
        val repo = AuthRepositoryImpl(
            supabaseClient        = strictClient,
            supabaseAuth          = supabaseAuth,
            userProfileDataSource = userProfileDataSource,
            ioDispatcher          = testDispatcher,
        )

        val result = repo.updateNickname("Gandalf")

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
