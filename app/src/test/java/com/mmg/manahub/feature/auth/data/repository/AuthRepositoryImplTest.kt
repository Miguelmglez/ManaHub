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
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
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
 * - [UnconfinedTestDispatcher] is used so withContext(ioDispatcher) runs
 *   synchronously, avoiding any need for advanceUntilIdle.
 * - Supabase's [Auth.sessionStatus] is backed by a [MutableStateFlow] so
 *   we can control emissions in Flow tests.
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
     * AuthRepositoryImpl.toAuthUser(). MockK relaxed mocks return null for
     * nullable fields automatically; we only set what the mapper actually reads.
     */
    private fun buildUserInfoMock(
        id: String = "user-uuid-001",
        email: String? = "test@example.com",
        provider: String = "email",
    ): UserInfo = mockk(relaxed = true) {
        every { this@mockk.id } returns id
        every { this@mockk.email } returns email
        every { this@mockk.userMetadata } returns null
        every { this@mockk.identities } returns listOf(
            mockk(relaxed = true) { every { this@mockk.provider } returns provider }
        )
    }

    private fun buildExpectedAuthUser(
        id: String = "user-uuid-001",
        email: String? = "test@example.com",
        provider: String = "email",
    ) = AuthUser(
        id          = id,
        email       = email,
        displayName = null,
        avatarUrl   = null,
        provider    = provider,
    )

    // ── Setup ─────────────────────────────────────────────────────────────────

    @Before
    fun setUp() {
        every { supabaseAuth.sessionStatus } returns sessionStatusFlow

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
        // Arrange
        val userInfoMock = buildUserInfoMock()
        coEvery { supabaseAuth.signInWith(any(), any<suspend io.github.jan.supabase.auth.providers.builtin.Email.Config.() -> Unit>()) } just Runs
        every { supabaseAuth.currentUserOrNull() } returns userInfoMock

        // Act
        val result = repository.signInWithEmail("test@example.com", "password123")

        // Assert
        assertTrue(result is AuthResult.Success)
        val user = (result as AuthResult.Success).data
        assertEquals("user-uuid-001", user.id)
        assertEquals("test@example.com", user.email)
        assertEquals("email", user.provider)
    }

    @Test
    fun `given server returns 400 when signInWithEmail then returns Error with InvalidCredentials`() = runTest {
        // Arrange
        val restException = RestException(
            error       = "invalid_grant",
            description = "Invalid credentials",
            statusCode  = 400,
        )
        coEvery { supabaseAuth.signInWith(any(), any<suspend io.github.jan.supabase.auth.providers.builtin.Email.Config.() -> Unit>()) } throws restException

        // Act
        val result = repository.signInWithEmail("wrong@example.com", "wrongpassword")

        // Assert
        assertTrue(result is AuthResult.Error)
        assertEquals(AuthError.InvalidCredentials, (result as AuthResult.Error).error)
    }

    @Test
    fun `given network failure when signInWithEmail then returns Error with NetworkError`() = runTest {
        // Arrange
        coEvery { supabaseAuth.signInWith(any(), any<suspend io.github.jan.supabase.auth.providers.builtin.Email.Config.() -> Unit>()) } throws IOException("No network")

        // Act
        val result = repository.signInWithEmail("test@example.com", "password123")

        // Assert
        assertTrue(result is AuthResult.Error)
        assertEquals(AuthError.NetworkError, (result as AuthResult.Error).error)
    }

    @Test
    fun `given HTTP timeout when signInWithEmail then returns Error with NetworkError`() = runTest {
        // Arrange: HttpRequestTimeoutException is also mapped to NetworkError
        coEvery { supabaseAuth.signInWith(any(), any<suspend io.github.jan.supabase.auth.providers.builtin.Email.Config.() -> Unit>()) } throws HttpRequestTimeoutException("https://supabase.io", 30_000L)

        // Act
        val result = repository.signInWithEmail("test@example.com", "password123")

        // Assert
        assertTrue(result is AuthResult.Error)
        assertEquals(AuthError.NetworkError, (result as AuthResult.Error).error)
    }

    @Test
    fun `given server returns 401 when signInWithEmail then returns Error with SessionExpired`() = runTest {
        // Arrange
        val restException = RestException(error = "unauthorized", description = "Unauthorized", statusCode = 401)
        coEvery { supabaseAuth.signInWith(any(), any<suspend io.github.jan.supabase.auth.providers.builtin.Email.Config.() -> Unit>()) } throws restException

        // Act
        val result = repository.signInWithEmail("test@example.com", "password123")

        // Assert
        assertTrue(result is AuthResult.Error)
        assertEquals(AuthError.SessionExpired, (result as AuthResult.Error).error)
    }

    @Test
    fun `given unexpected exception when signInWithEmail then returns Error with Unknown`() = runTest {
        // Arrange
        coEvery { supabaseAuth.signInWith(any(), any<suspend io.github.jan.supabase.auth.providers.builtin.Email.Config.() -> Unit>()) } throws RuntimeException("Unexpected failure")

        // Act
        val result = repository.signInWithEmail("test@example.com", "password123")

        // Assert
        assertTrue(result is AuthResult.Error)
        assertTrue((result as AuthResult.Error).error is AuthError.Unknown)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 2 — signUpWithEmail
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given valid new user when signUpWithEmail then calls upsertUserProfile and returns Success`() = runTest {
        // Arrange
        val userInfoMock = buildUserInfoMock()
        coEvery { supabaseAuth.signUpWith(any(), any<suspend io.github.jan.supabase.auth.providers.builtin.Email.Config.() -> Unit>()) } just Runs
        every { supabaseAuth.currentUserOrNull() } returns userInfoMock
        coEvery { userProfileDataSource.upsertUserProfile(any()) } just Runs

        // Act
        val result = repository.signUpWithEmail("new@example.com", "password123")

        // Assert
        assertTrue(result is AuthResult.Success)
        coVerify(exactly = 1) { userProfileDataSource.upsertUserProfile(any()) }
    }

    @Test
    fun `given Supabase requires email confirmation when signUpWithEmail then returns EmailConfirmationRequired`() = runTest {
        // Arrange: currentUserOrNull() returns null when email confirmation is required
        coEvery { supabaseAuth.signUpWith(any(), any<suspend io.github.jan.supabase.auth.providers.builtin.Email.Config.() -> Unit>()) } just Runs
        every { supabaseAuth.currentUserOrNull() } returns null

        // Act
        val result = repository.signUpWithEmail("confirm@example.com", "password123")

        // Assert
        assertTrue(result is AuthResult.Error)
        assertEquals(AuthError.EmailConfirmationRequired, (result as AuthResult.Error).error)
        // upsertUserProfile must NOT be called when the user is not yet active
        coVerify(exactly = 0) { userProfileDataSource.upsertUserProfile(any()) }
    }

    @Test
    fun `given server returns 422 when signUpWithEmail then returns Error with EmailAlreadyInUse`() = runTest {
        // Arrange
        val restException = RestException(error = "user_already_exists", description = "Email already in use", statusCode = 422)
        coEvery { supabaseAuth.signUpWith(any(), any<suspend io.github.jan.supabase.auth.providers.builtin.Email.Config.() -> Unit>()) } throws restException

        // Act
        val result = repository.signUpWithEmail("existing@example.com", "password123")

        // Assert
        assertTrue(result is AuthResult.Error)
        assertEquals(AuthError.EmailAlreadyInUse, (result as AuthResult.Error).error)
    }

    @Test
    fun `given network failure when signUpWithEmail then returns Error with NetworkError`() = runTest {
        // Arrange
        coEvery { supabaseAuth.signUpWith(any(), any<suspend io.github.jan.supabase.auth.providers.builtin.Email.Config.() -> Unit>()) } throws IOException("Network down")

        // Act
        val result = repository.signUpWithEmail("new@example.com", "password123")

        // Assert
        assertTrue(result is AuthResult.Error)
        assertEquals(AuthError.NetworkError, (result as AuthResult.Error).error)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 3 — signInWithGoogleIdToken
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given valid Google ID token when signInWithGoogleIdToken then returns Success and calls upsertUserProfile`() = runTest {
        // Arrange
        val userInfoMock = buildUserInfoMock(provider = "google")
        coEvery { supabaseAuth.signInWith(any(), any<suspend io.github.jan.supabase.auth.providers.builtin.IDToken.Config.() -> Unit>()) } just Runs
        every { supabaseAuth.currentUserOrNull() } returns userInfoMock
        coEvery { userProfileDataSource.upsertUserProfile(any()) } just Runs

        // Act
        val result = repository.signInWithGoogleIdToken("valid-google-id-token", "raw-nonce-abc")

        // Assert
        assertTrue(result is AuthResult.Success)
        val user = (result as AuthResult.Success).data
        assertEquals("google", user.provider)
        coVerify(exactly = 1) { userProfileDataSource.upsertUserProfile(any()) }
    }

    @Test
    fun `given invalid Google token when signInWithGoogleIdToken then returns Error with mapped AuthError`() = runTest {
        // Arrange
        val restException = RestException(error = "invalid_token", description = "Invalid ID token", statusCode = 400)
        coEvery { supabaseAuth.signInWith(any(), any<suspend io.github.jan.supabase.auth.providers.builtin.IDToken.Config.() -> Unit>()) } throws restException

        // Act
        val result = repository.signInWithGoogleIdToken("invalid-token", "raw-nonce-abc")

        // Assert
        assertTrue(result is AuthResult.Error)
        assertEquals(AuthError.InvalidCredentials, (result as AuthResult.Error).error)
    }

    @Test
    fun `given network failure when signInWithGoogleIdToken then returns Error with NetworkError`() = runTest {
        // Arrange
        coEvery { supabaseAuth.signInWith(any(), any<suspend io.github.jan.supabase.auth.providers.builtin.IDToken.Config.() -> Unit>()) } throws IOException("No connectivity")

        // Act
        val result = repository.signInWithGoogleIdToken("some-token", "some-nonce")

        // Assert
        assertTrue(result is AuthResult.Error)
        assertEquals(AuthError.NetworkError, (result as AuthResult.Error).error)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 4 — signOut
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given authenticated user when signOut then returns Success and calls supabaseAuth_signOut`() = runTest {
        // Arrange
        coEvery { supabaseAuth.signOut(any()) } just Runs

        // Act
        val result = repository.signOut()

        // Assert
        assertTrue(result is AuthResult.Success)
        assertEquals(Unit, (result as AuthResult.Success).data)
        coVerify(exactly = 1) { supabaseAuth.signOut(any()) }
    }

    @Test
    fun `given signOut throws when signOut then returns Error`() = runTest {
        // Arrange
        coEvery { supabaseAuth.signOut(any()) } throws RuntimeException("Sign-out failed")

        // Act
        val result = repository.signOut()

        // Assert
        assertTrue(result is AuthResult.Error)
        assertTrue((result as AuthResult.Error).error is AuthError.Unknown)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 5 — deleteAccount
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given authenticated user when deleteAccount then calls postgrest rpc and signOut and returns Success`() = runTest {
        // Arrange: supabaseClient is relaxed so postgrest.rpc() returns a relaxed mock automatically
        coEvery { supabaseAuth.signOut(any()) } just Runs

        // Act
        val result = repository.deleteAccount()

        // Assert
        assertTrue(result is AuthResult.Success)
        coVerify(exactly = 1) { supabaseAuth.signOut(any()) }
    }

    @Test
    fun `given postgrest rpc throws when deleteAccount then returns Error and does NOT call signOut`() = runTest {
        // Arrange: override the relaxed client to throw on rpc
        val postgrestMock = mockk<io.github.jan.supabase.postgrest.Postgrest>(relaxed = true)
        coEvery { postgrestMock.rpc(any<String>(), any()) } throws RuntimeException("RPC failed")

        // We need a fresh repository where supabaseClient.postgrest throws
        val strictClient = mockk<SupabaseClient> {
            every { this@mockk.postgrest } returns postgrestMock
        }
        val repoUnderTest = AuthRepositoryImpl(
            supabaseClient        = strictClient,
            supabaseAuth          = supabaseAuth,
            userProfileDataSource = userProfileDataSource,
            ioDispatcher          = testDispatcher,
        )

        // Act
        val result = repoUnderTest.deleteAccount()

        // Assert
        assertTrue(result is AuthResult.Error)
        // signOut should NOT be called if the RPC step fails
        coVerify(exactly = 0) { supabaseAuth.signOut(any()) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 6 — resetPassword
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given valid email when resetPassword then returns Success`() = runTest {
        // Arrange
        coEvery { supabaseAuth.resetPasswordForEmail(any(), any()) } just Runs

        // Act
        val result = repository.resetPassword("user@example.com")

        // Assert
        assertTrue(result is AuthResult.Success)
        assertEquals(Unit, (result as AuthResult.Success).data)
    }

    @Test
    fun `given unknown email when resetPassword and server returns 404 then returns Error with UserNotFound`() = runTest {
        // Arrange
        val restException = RestException(error = "not_found", description = "User not found", statusCode = 404)
        coEvery { supabaseAuth.resetPasswordForEmail(any(), any()) } throws restException

        // Act
        val result = repository.resetPassword("ghost@example.com")

        // Assert
        assertTrue(result is AuthResult.Error)
        assertEquals(AuthError.UserNotFound, (result as AuthResult.Error).error)
    }

    @Test
    fun `given network failure when resetPassword then returns Error with NetworkError`() = runTest {
        // Arrange
        coEvery { supabaseAuth.resetPasswordForEmail(any(), any()) } throws IOException("Offline")

        // Act
        val result = repository.resetPassword("user@example.com")

        // Assert
        assertTrue(result is AuthResult.Error)
        assertEquals(AuthError.NetworkError, (result as AuthResult.Error).error)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 7 — sessionState Flow
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given SessionStatus Initializing when sessionState collected then emits SessionState Loading`() = runTest {
        // Arrange: sessionStatusFlow already set to Initializing in setUp()

        // Act & Assert
        repository.sessionState.test {
            assertEquals(SessionState.Loading, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given SessionStatus NotAuthenticated when sessionState collected then emits Unauthenticated`() = runTest {
        // Arrange
        sessionStatusFlow.value = SessionStatus.NotAuthenticated(isSignOut = false)

        // Act & Assert
        repository.sessionState.test {
            assertEquals(SessionState.Unauthenticated, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given SessionStatus Authenticated when sessionState collected then emits Authenticated with AuthUser`() = runTest {
        // Arrange
        val userInfoMock = buildUserInfoMock()
        val sessionMock = mockk<io.github.jan.supabase.auth.user.UserSession>(relaxed = true) {
            every { user } returns userInfoMock
        }
        sessionStatusFlow.value = SessionStatus.Authenticated(sessionMock)

        // Act & Assert
        repository.sessionState.test {
            val state = awaitItem()
            assertTrue(state is SessionState.Authenticated)
            assertEquals("user-uuid-001", (state as SessionState.Authenticated).user.id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given SessionStatus changes from Initializing to NotAuthenticated when sessionState observed then emits Loading then Unauthenticated`() = runTest {
        // Arrange: start in Initializing
        sessionStatusFlow.value = SessionStatus.Initializing

        // Act & Assert
        repository.sessionState.test {
            assertEquals(SessionState.Loading, awaitItem())

            // Simulate session status changing
            sessionStatusFlow.value = SessionStatus.NotAuthenticated(isSignOut = false)
            assertEquals(SessionState.Unauthenticated, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given SessionStatus RefreshFailure when sessionState collected then emits Unauthenticated`() = runTest {
        // Arrange: RefreshFailure is also mapped to Unauthenticated
        sessionStatusFlow.value = SessionStatus.RefreshFailure(cause = RuntimeException("Token refresh failed"))

        // Act & Assert
        repository.sessionState.test {
            assertEquals(SessionState.Unauthenticated, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
