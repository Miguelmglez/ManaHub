package com.mmg.manahub.feature.auth.domain.usecase

import com.mmg.manahub.feature.auth.domain.model.AuthError
import com.mmg.manahub.feature.auth.domain.model.AuthResult
import com.mmg.manahub.feature.auth.domain.model.AuthUser
import com.mmg.manahub.feature.auth.domain.model.SessionState
import com.mmg.manahub.feature.auth.domain.repository.AuthRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for all Auth UseCases.
 *
 * Strategy:
 * - [AuthRepository] is mocked for every test group.
 * - Tests verify that each use case delegates to the correct repository method
 *   with the correct arguments (including email trimming where applicable).
 * - No business logic lives in the use cases themselves — these tests act as
 *   contracts that prevent accidental parameter mutations or wrong method calls.
 */
class AuthUseCasesTest {

    // ── Mock ──────────────────────────────────────────────────────────────────

    private val repository = mockk<AuthRepository>()

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private val dummyAuthUser = AuthUser(
        id          = "user-uuid-001",
        email       = "user@example.com",
        displayName = "Test User",
        avatarUrl   = null,
        provider    = "email",
    )

    private val successUser    = AuthResult.Success(dummyAuthUser)
    private val successUnit    = AuthResult.Success(Unit)
    private val networkError   = AuthResult.Error(AuthError.NetworkError)

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 1 — SignInWithEmailUseCase
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given email with whitespace when SignInWithEmailUseCase invoked then calls repository with trimmed email`() = runTest {
        // Arrange
        coEvery { repository.signInWithEmail("user@example.com", "password123") } returns successUser
        val useCase = SignInWithEmailUseCase(repository)

        // Act
        val result = useCase("  user@example.com  ", "password123")

        // Assert: email must be trimmed before reaching the repository
        coVerify(exactly = 1) { repository.signInWithEmail("user@example.com", "password123") }
        assertEquals(successUser, result)
    }

    @Test
    fun `given clean email when SignInWithEmailUseCase invoked then delegates to repository as-is`() = runTest {
        // Arrange
        coEvery { repository.signInWithEmail("user@example.com", "password123") } returns successUser
        val useCase = SignInWithEmailUseCase(repository)

        // Act
        val result = useCase("user@example.com", "password123")

        // Assert
        coVerify(exactly = 1) { repository.signInWithEmail("user@example.com", "password123") }
        assertEquals(successUser, result)
    }

    @Test
    fun `given repository returns Error when SignInWithEmailUseCase invoked then propagates Error`() = runTest {
        // Arrange
        coEvery { repository.signInWithEmail(any(), any()) } returns networkError
        val useCase = SignInWithEmailUseCase(repository)

        // Act
        val result = useCase("user@example.com", "password123")

        // Assert: error is not swallowed by the use case
        assertTrue(result is AuthResult.Error)
        assertEquals(AuthError.NetworkError, (result as AuthResult.Error).error)
    }

    @Test
    fun `given email with internal spaces when SignInWithEmailUseCase invoked then does NOT strip internal spaces`() = runTest {
        // Arrange: trim() only removes leading/trailing whitespace, not internal spaces
        coEvery { repository.signInWithEmail("user @example.com", any()) } returns networkError
        val useCase = SignInWithEmailUseCase(repository)

        // Act
        useCase("user @example.com", "password123")

        // Assert: internal space is preserved (trim does not affect it)
        coVerify(exactly = 1) { repository.signInWithEmail("user @example.com", "password123") }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 2 — SignUpWithEmailUseCase
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given email with leading and trailing spaces when SignUpWithEmailUseCase invoked then calls repository with trimmed email`() = runTest {
        // Arrange
        coEvery { repository.signUpWithEmail("new@example.com", "password123") } returns successUser
        val useCase = SignUpWithEmailUseCase(repository)

        // Act
        val result = useCase("  new@example.com  ", "password123")

        // Assert
        coVerify(exactly = 1) { repository.signUpWithEmail("new@example.com", "password123") }
        assertEquals(successUser, result)
    }

    @Test
    fun `given clean email when SignUpWithEmailUseCase invoked then delegates to repository unchanged`() = runTest {
        // Arrange
        coEvery { repository.signUpWithEmail("new@example.com", "password123") } returns successUser
        val useCase = SignUpWithEmailUseCase(repository)

        // Act
        val result = useCase("new@example.com", "password123")

        // Assert
        coVerify(exactly = 1) { repository.signUpWithEmail("new@example.com", "password123") }
        assertEquals(successUser, result)
    }

    @Test
    fun `given password is NOT trimmed when SignUpWithEmailUseCase invoked then password is forwarded as-is`() = runTest {
        // Arrange: password may legitimately contain spaces — only email is trimmed
        coEvery { repository.signUpWithEmail(any(), "  pass with spaces  ") } returns successUser
        val useCase = SignUpWithEmailUseCase(repository)

        // Act
        useCase("new@example.com", "  pass with spaces  ")

        // Assert: password is not touched
        coVerify(exactly = 1) { repository.signUpWithEmail("new@example.com", "  pass with spaces  ") }
    }

    @Test
    fun `given repository returns EmailConfirmationRequired when SignUpWithEmailUseCase invoked then propagates Error`() = runTest {
        // Arrange
        val errorResult = AuthResult.Error(AuthError.EmailConfirmationRequired)
        coEvery { repository.signUpWithEmail(any(), any()) } returns errorResult
        val useCase = SignUpWithEmailUseCase(repository)

        // Act
        val result = useCase("new@example.com", "password123")

        // Assert
        assertTrue(result is AuthResult.Error)
        assertEquals(AuthError.EmailConfirmationRequired, (result as AuthResult.Error).error)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 3 — SignInWithGoogleUseCase
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given valid token and nonce when SignInWithGoogleUseCase invoked then delegates to repository with same arguments`() = runTest {
        // Arrange
        coEvery { repository.signInWithGoogleIdToken("google-id-token", "raw-nonce") } returns successUser
        val useCase = SignInWithGoogleUseCase(repository)

        // Act
        val result = useCase("google-id-token", "raw-nonce")

        // Assert: arguments must reach the repository unchanged
        coVerify(exactly = 1) { repository.signInWithGoogleIdToken("google-id-token", "raw-nonce") }
        assertEquals(successUser, result)
    }

    @Test
    fun `given repository returns Error when SignInWithGoogleUseCase invoked then propagates Error`() = runTest {
        // Arrange
        coEvery { repository.signInWithGoogleIdToken(any(), any()) } returns networkError
        val useCase = SignInWithGoogleUseCase(repository)

        // Act
        val result = useCase("some-token", "some-nonce")

        // Assert
        assertEquals(networkError, result)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 4 — SignOutUseCase
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `when SignOutUseCase invoked then delegates to repository_signOut`() = runTest {
        // Arrange
        coEvery { repository.signOut() } returns successUnit
        val useCase = SignOutUseCase(repository)

        // Act
        val result = useCase()

        // Assert
        coVerify(exactly = 1) { repository.signOut() }
        assertEquals(successUnit, result)
    }

    @Test
    fun `given repository signOut fails when SignOutUseCase invoked then propagates Error`() = runTest {
        // Arrange
        coEvery { repository.signOut() } returns networkError
        val useCase = SignOutUseCase(repository)

        // Act
        val result = useCase()

        // Assert
        assertEquals(networkError, result)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 5 — DeleteAccountUseCase
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `when DeleteAccountUseCase invoked then delegates to repository_deleteAccount`() = runTest {
        // Arrange
        coEvery { repository.deleteAccount() } returns successUnit
        val useCase = DeleteAccountUseCase(repository)

        // Act
        val result = useCase()

        // Assert
        coVerify(exactly = 1) { repository.deleteAccount() }
        assertEquals(successUnit, result)
    }

    @Test
    fun `given repository deleteAccount returns Error when DeleteAccountUseCase invoked then propagates Error`() = runTest {
        // Arrange
        val error = AuthResult.Error(AuthError.SessionExpired)
        coEvery { repository.deleteAccount() } returns error
        val useCase = DeleteAccountUseCase(repository)

        // Act
        val result = useCase()

        // Assert
        assertTrue(result is AuthResult.Error)
        assertEquals(AuthError.SessionExpired, (result as AuthResult.Error).error)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 6 — ResetPasswordUseCase
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given email with whitespace when ResetPasswordUseCase invoked then calls repository with trimmed email`() = runTest {
        // Arrange
        coEvery { repository.resetPassword("user@example.com") } returns successUnit
        val useCase = ResetPasswordUseCase(repository)

        // Act
        val result = useCase("  user@example.com  ")

        // Assert: ResetPasswordUseCase also trims the email
        coVerify(exactly = 1) { repository.resetPassword("user@example.com") }
        assertEquals(successUnit, result)
    }

    @Test
    fun `given clean email when ResetPasswordUseCase invoked then delegates to repository unchanged`() = runTest {
        // Arrange
        coEvery { repository.resetPassword("user@example.com") } returns successUnit
        val useCase = ResetPasswordUseCase(repository)

        // Act
        useCase("user@example.com")

        // Assert
        coVerify(exactly = 1) { repository.resetPassword("user@example.com") }
    }

    @Test
    fun `given repository returns Error when ResetPasswordUseCase invoked then propagates Error`() = runTest {
        // Arrange
        coEvery { repository.resetPassword(any()) } returns networkError
        val useCase = ResetPasswordUseCase(repository)

        // Act
        val result = useCase("user@example.com")

        // Assert
        assertEquals(networkError, result)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 7 — GetSessionStateUseCase
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `when GetSessionStateUseCase invoked then returns the repository sessionState flow`() = runTest {
        // Arrange
        val sessionFlow = flowOf(SessionState.Loading)
        every { repository.sessionState } returns sessionFlow
        val useCase = GetSessionStateUseCase(repository)

        // Act
        val result = useCase()

        // Assert: the use case is a transparent proxy — same flow reference
        assertEquals(SessionState.Loading, result.first())
    }

    @Test
    fun `given repository emits Unauthenticated when GetSessionStateUseCase flow collected then emits Unauthenticated`() = runTest {
        // Arrange
        every { repository.sessionState } returns flowOf(SessionState.Unauthenticated)
        val useCase = GetSessionStateUseCase(repository)

        // Act
        val result = useCase().first()

        // Assert
        assertEquals(SessionState.Unauthenticated, result)
    }

    @Test
    fun `given repository emits Authenticated when GetSessionStateUseCase flow collected then emits Authenticated`() = runTest {
        // Arrange
        val authenticatedState = SessionState.Authenticated(dummyAuthUser)
        every { repository.sessionState } returns flowOf(authenticatedState)
        val useCase = GetSessionStateUseCase(repository)

        // Act
        val result = useCase().first()

        // Assert
        assertTrue(result is SessionState.Authenticated)
        assertEquals(dummyAuthUser.id, (result as SessionState.Authenticated).user.id)
    }
}
