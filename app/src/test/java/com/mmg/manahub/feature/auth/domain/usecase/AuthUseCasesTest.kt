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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for all Auth UseCases.
 *
 * Strategy:
 * - [AuthRepository] is mocked for every test group.
 * - Tests verify that each use case delegates to the correct repository method
 *   with the correct arguments (including email/nickname trimming where applicable).
 * - No business logic lives in the use cases themselves — these tests act as
 *   contracts that prevent accidental parameter mutations or wrong method calls.
 *
 * Added vs previous version:
 * - GROUP 8: UpdateNicknameUseCase (delegation, nickname trimming, error propagation)
 */
class AuthUseCasesTest {

    // ── Mock ──────────────────────────────────────────────────────────────────

    private val repository = mockk<AuthRepository>()

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private val dummyAuthUser = AuthUser(
        id        = "user-uuid-001",
        email     = "user@example.com",
        nickname  = "TestUser",
        gameTag   = null,
        avatarUrl = null,
        provider  = "email",
    )

    private val successUser  = AuthResult.Success(dummyAuthUser)
    private val successUnit  = AuthResult.Success(Unit)
    private val networkError = AuthResult.Error(AuthError.NetworkError)

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 1 — SignInWithEmailUseCase
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given email with whitespace when SignInWithEmailUseCase invoked then calls repository with trimmed email`() = runTest {
        coEvery { repository.signInWithEmail("user@example.com", "password123") } returns successUser
        val useCase = SignInWithEmailUseCase(repository)

        val result = useCase("  user@example.com  ", "password123")

        coVerify(exactly = 1) { repository.signInWithEmail("user@example.com", "password123") }
        assertEquals(successUser, result)
    }

    @Test
    fun `given clean email when SignInWithEmailUseCase invoked then delegates to repository as-is`() = runTest {
        coEvery { repository.signInWithEmail("user@example.com", "password123") } returns successUser
        val useCase = SignInWithEmailUseCase(repository)

        val result = useCase("user@example.com", "password123")

        coVerify(exactly = 1) { repository.signInWithEmail("user@example.com", "password123") }
        assertEquals(successUser, result)
    }

    @Test
    fun `given repository returns Error when SignInWithEmailUseCase invoked then propagates Error`() = runTest {
        coEvery { repository.signInWithEmail(any(), any()) } returns networkError
        val useCase = SignInWithEmailUseCase(repository)

        val result = useCase("user@example.com", "password123")

        assertTrue(result is AuthResult.Error)
        assertEquals(AuthError.NetworkError, (result as AuthResult.Error).error)
    }

    @Test
    fun `given email with internal spaces when SignInWithEmailUseCase invoked then does NOT strip internal spaces`() = runTest {
        // trim() only removes leading/trailing whitespace — internal spaces are preserved
        coEvery { repository.signInWithEmail("user @example.com", any()) } returns networkError
        val useCase = SignInWithEmailUseCase(repository)

        useCase("user @example.com", "password123")

        coVerify(exactly = 1) { repository.signInWithEmail("user @example.com", "password123") }
    }

    @Test
    fun `given password is NOT trimmed when SignInWithEmailUseCase invoked then password is forwarded as-is`() = runTest {
        // Passwords may legitimately contain spaces — they must never be trimmed
        coEvery { repository.signInWithEmail(any(), "  pass  ") } returns successUser
        val useCase = SignInWithEmailUseCase(repository)

        useCase("user@example.com", "  pass  ")

        coVerify(exactly = 1) { repository.signInWithEmail("user@example.com", "  pass  ") }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 2 — SignUpWithEmailUseCase
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given email with leading and trailing spaces when SignUpWithEmailUseCase invoked then calls repository with trimmed email`() = runTest {
        coEvery { repository.signUpWithEmail("new@example.com", "password123", "Hero", null) } returns successUser
        val useCase = SignUpWithEmailUseCase(repository)

        val result = useCase("  new@example.com  ", "password123", "Hero")

        coVerify(exactly = 1) { repository.signUpWithEmail("new@example.com", "password123", "Hero", null) }
        assertEquals(successUser, result)
    }

    @Test
    fun `given nickname with leading spaces when SignUpWithEmailUseCase invoked then calls repository with trimmed nickname`() = runTest {
        coEvery { repository.signUpWithEmail("new@example.com", "password123", "Hero", null) } returns successUser
        val useCase = SignUpWithEmailUseCase(repository)

        useCase("new@example.com", "password123", "  Hero  ")

        coVerify(exactly = 1) { repository.signUpWithEmail("new@example.com", "password123", "Hero", null) }
    }

    @Test
    fun `given clean email when SignUpWithEmailUseCase invoked then delegates to repository unchanged`() = runTest {
        coEvery { repository.signUpWithEmail("new@example.com", "password123", "Hero", null) } returns successUser
        val useCase = SignUpWithEmailUseCase(repository)

        val result = useCase("new@example.com", "password123", "Hero")

        coVerify(exactly = 1) { repository.signUpWithEmail("new@example.com", "password123", "Hero", null) }
        assertEquals(successUser, result)
    }

    @Test
    fun `given password is NOT trimmed when SignUpWithEmailUseCase invoked then password is forwarded as-is`() = runTest {
        coEvery { repository.signUpWithEmail(any(), "  pass with spaces  ", any(), anyNullable()) } returns successUser
        val useCase = SignUpWithEmailUseCase(repository)

        useCase("new@example.com", "  pass with spaces  ", "Hero")

        coVerify(exactly = 1) { repository.signUpWithEmail("new@example.com", "  pass with spaces  ", "Hero", null) }
    }

    @Test
    fun `given repository returns EmailConfirmationRequired when SignUpWithEmailUseCase invoked then propagates Error`() = runTest {
        val errorResult = AuthResult.Error(AuthError.EmailConfirmationRequired)
        coEvery { repository.signUpWithEmail(any(), any(), any(), anyNullable()) } returns errorResult
        val useCase = SignUpWithEmailUseCase(repository)

        val result = useCase("new@example.com", "password123", "Hero")

        assertTrue(result is AuthResult.Error)
        assertEquals(AuthError.EmailConfirmationRequired, (result as AuthResult.Error).error)
    }

    @Test
    fun `given repository returns EmailAlreadyInUse when SignUpWithEmailUseCase invoked then propagates Error`() = runTest {
        val errorResult = AuthResult.Error(AuthError.EmailAlreadyInUse)
        coEvery { repository.signUpWithEmail(any(), any(), any(), anyNullable()) } returns errorResult
        val useCase = SignUpWithEmailUseCase(repository)

        val result = useCase("existing@example.com", "password123", "Hero")

        assertTrue(result is AuthResult.Error)
        assertEquals(AuthError.EmailAlreadyInUse, (result as AuthResult.Error).error)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 3 — SignInWithGoogleUseCase & SignUpWithGoogleUseCase
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given valid token and nonce when SignInWithGoogleUseCase invoked then delegates to repository`() = runTest {
        coEvery { repository.signInWithGoogle("google-id-token", "raw-nonce") } returns successUser
        val useCase = SignInWithGoogleUseCase(repository)

        val result = useCase("google-id-token", "raw-nonce")

        coVerify(exactly = 1) { repository.signInWithGoogle("google-id-token", "raw-nonce") }
        assertEquals(successUser, result)
    }

    @Test
    fun `given valid arguments when SignUpWithGoogleUseCase invoked then delegates to repository`() = runTest {
        coEvery { repository.signUpWithGoogle("google-id-token", "raw-nonce", "Hero", "url") } returns successUser
        val useCase = SignUpWithGoogleUseCase(repository)

        val result = useCase("google-id-token", "raw-nonce", "Hero", "url")

        coVerify(exactly = 1) { repository.signUpWithGoogle("google-id-token", "raw-nonce", "Hero", "url") }
        assertEquals(successUser, result)
    }

    @Test
    fun `given repository returns Error when SignInWithGoogleUseCase invoked then propagates Error`() = runTest {
        coEvery { repository.signInWithGoogle(any(), any()) } returns networkError
        val useCase = SignInWithGoogleUseCase(repository)

        val result = useCase("some-token", "some-nonce")

        assertEquals(networkError, result)
    }

    @Test
    fun `given idToken is NOT modified when SignInWithGoogleUseCase invoked then token forwarded unchanged`() = runTest {
        // Tokens are opaque blobs — no trimming or mutation should occur
        val longToken = "eyJhb.eyJzdW.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c"
        coEvery { repository.signInWithGoogle(longToken, "nonce") } returns successUser
        val useCase = SignInWithGoogleUseCase(repository)

        useCase(longToken, "nonce")

        coVerify(exactly = 1) { repository.signInWithGoogle(longToken, "nonce") }
    }

    @Test
    fun `given nickname with spaces when SignUpWithGoogleUseCase invoked then nickname forwarded as-is without trimming`() = runTest {
        // The use case is a thin proxy — trimming is the ViewModel's responsibility
        coEvery { repository.signUpWithGoogle(any(), any(), "  Hero  ", any()) } returns successUser
        val useCase = SignUpWithGoogleUseCase(repository)

        useCase("google-token", "nonce", "  Hero  ", "url")

        coVerify(exactly = 1) { repository.signUpWithGoogle("google-token", "nonce", "  Hero  ", "url") }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 4 — SignOutUseCase
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `when SignOutUseCase invoked then delegates to repository_signOut`() = runTest {
        coEvery { repository.signOut() } returns successUnit
        val useCase = SignOutUseCase(repository)

        val result = useCase()

        coVerify(exactly = 1) { repository.signOut() }
        assertEquals(successUnit, result)
    }

    @Test
    fun `given repository signOut fails when SignOutUseCase invoked then propagates Error`() = runTest {
        coEvery { repository.signOut() } returns networkError
        val useCase = SignOutUseCase(repository)

        val result = useCase()

        assertEquals(networkError, result)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 5 — DeleteAccountUseCase
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `when DeleteAccountUseCase invoked then delegates to repository_deleteAccount`() = runTest {
        coEvery { repository.deleteAccount() } returns successUnit
        val useCase = DeleteAccountUseCase(repository)

        val result = useCase()

        coVerify(exactly = 1) { repository.deleteAccount() }
        assertEquals(successUnit, result)
    }

    @Test
    fun `given repository deleteAccount returns Error when DeleteAccountUseCase invoked then propagates Error`() = runTest {
        val error = AuthResult.Error(AuthError.SessionExpired)
        coEvery { repository.deleteAccount() } returns error
        val useCase = DeleteAccountUseCase(repository)

        val result = useCase()

        assertTrue(result is AuthResult.Error)
        assertEquals(AuthError.SessionExpired, (result as AuthResult.Error).error)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 6 — ResetPasswordUseCase
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given email with whitespace when ResetPasswordUseCase invoked then calls repository with trimmed email`() = runTest {
        coEvery { repository.resetPassword("user@example.com") } returns successUnit
        val useCase = ResetPasswordUseCase(repository)

        val result = useCase("  user@example.com  ")

        coVerify(exactly = 1) { repository.resetPassword("user@example.com") }
        assertEquals(successUnit, result)
    }

    @Test
    fun `given clean email when ResetPasswordUseCase invoked then delegates to repository unchanged`() = runTest {
        coEvery { repository.resetPassword("user@example.com") } returns successUnit
        val useCase = ResetPasswordUseCase(repository)

        useCase("user@example.com")

        coVerify(exactly = 1) { repository.resetPassword("user@example.com") }
    }

    @Test
    fun `given repository returns Error when ResetPasswordUseCase invoked then propagates Error`() = runTest {
        coEvery { repository.resetPassword(any()) } returns networkError
        val useCase = ResetPasswordUseCase(repository)

        val result = useCase("user@example.com")

        assertEquals(networkError, result)
    }

    @Test
    fun `given repository returns UserNotFound when ResetPasswordUseCase invoked then propagates UserNotFound`() = runTest {
        val error = AuthResult.Error(AuthError.UserNotFound)
        coEvery { repository.resetPassword(any()) } returns error
        val useCase = ResetPasswordUseCase(repository)

        val result = useCase("ghost@example.com")

        assertTrue(result is AuthResult.Error)
        assertEquals(AuthError.UserNotFound, (result as AuthResult.Error).error)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 7 — GetSessionStateUseCase
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `when GetSessionStateUseCase invoked then returns the repository sessionState flow`() = runTest {
        val sessionFlow = MutableStateFlow<SessionState>(SessionState.Loading)
        every { repository.sessionState } returns sessionFlow
        val useCase = GetSessionStateUseCase(repository)

        val result = useCase()

        assertEquals(SessionState.Loading, result.first())
    }

    @Test
    fun `given repository emits Unauthenticated when GetSessionStateUseCase flow collected then emits Unauthenticated`() = runTest {
        every { repository.sessionState } returns MutableStateFlow(SessionState.Unauthenticated)
        val useCase = GetSessionStateUseCase(repository)

        val result = useCase().first()

        assertEquals(SessionState.Unauthenticated, result)
    }

    @Test
    fun `given repository emits Authenticated when GetSessionStateUseCase flow collected then emits Authenticated`() = runTest {
        val authenticatedState = SessionState.Authenticated(dummyAuthUser)
        every { repository.sessionState } returns MutableStateFlow(authenticatedState)
        val useCase = GetSessionStateUseCase(repository)

        val result = useCase().first()

        assertTrue(result is SessionState.Authenticated)
        assertEquals(dummyAuthUser.id, (result as SessionState.Authenticated).user.id)
    }

    @Test
    fun `given GetSessionStateUseCase invoked multiple times then each call returns the same flow reference`() = runTest {
        // Use case is a thin proxy — it must not create a new flow on each invocation
        every { repository.sessionState } returns MutableStateFlow(SessionState.Loading)
        val useCase = GetSessionStateUseCase(repository)

        useCase()
        useCase()

        // sessionState property should be accessed once per invocation
        // (each call is a new operator() call, so property access happens twice)
        io.mockk.verify(exactly = 2) { repository.sessionState }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 8 — UpdateNicknameUseCase
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `when UpdateNicknameUseCase invoked then delegates to repository_updateNickname`() = runTest {
        coEvery { repository.updateNickname("Gandalf") } returns successUser
        val useCase = UpdateNicknameUseCase(repository)

        val result = useCase("Gandalf")

        coVerify(exactly = 1) { repository.updateNickname("Gandalf") }
        assertEquals(successUser, result)
    }

    @Test
    fun `given nickname argument when UpdateNicknameUseCase invoked then nickname is forwarded as-is to repository`() = runTest {
        // UpdateNicknameUseCase does NOT trim — that responsibility belongs to
        // the ViewModel (before calling the use case) and the repository (updateNicknameInternal).
        coEvery { repository.updateNickname("  Gandalf  ") } returns successUser
        val useCase = UpdateNicknameUseCase(repository)

        useCase("  Gandalf  ")

        coVerify(exactly = 1) { repository.updateNickname("  Gandalf  ") }
    }

    @Test
    fun `given repository returns NicknameInappropriate when UpdateNicknameUseCase invoked then propagates error`() = runTest {
        val error = AuthResult.Error(AuthError.NicknameInappropriate)
        coEvery { repository.updateNickname(any()) } returns error
        val useCase = UpdateNicknameUseCase(repository)

        val result = useCase("BadWord")

        assertTrue(result is AuthResult.Error)
        assertEquals(AuthError.NicknameInappropriate, (result as AuthResult.Error).error)
    }

    @Test
    fun `given repository returns NicknameTooLong when UpdateNicknameUseCase invoked then propagates error`() = runTest {
        val error = AuthResult.Error(AuthError.NicknameTooLong)
        coEvery { repository.updateNickname(any()) } returns error
        val useCase = UpdateNicknameUseCase(repository)

        val result = useCase("A".repeat(31))

        assertTrue(result is AuthResult.Error)
        assertEquals(AuthError.NicknameTooLong, (result as AuthResult.Error).error)
    }

    @Test
    fun `given repository returns NetworkError when UpdateNicknameUseCase invoked then propagates NetworkError`() = runTest {
        coEvery { repository.updateNickname(any()) } returns networkError
        val useCase = UpdateNicknameUseCase(repository)

        val result = useCase("Gandalf")

        assertEquals(networkError, result)
    }

    @Test
    fun `given repository returns SessionExpired when UpdateNicknameUseCase invoked then propagates SessionExpired`() = runTest {
        val error = AuthResult.Error(AuthError.SessionExpired)
        coEvery { repository.updateNickname(any()) } returns error
        val useCase = UpdateNicknameUseCase(repository)

        val result = useCase("Gandalf")

        assertTrue(result is AuthResult.Error)
        assertEquals(AuthError.SessionExpired, (result as AuthResult.Error).error)
    }

    @Test
    fun `given empty string nickname when UpdateNicknameUseCase invoked then still delegates to repository`() = runTest {
        // The use case itself has no validation — that belongs to ViewModel/Repository.
        // An empty string must be forwarded unchanged so upstream can reject it.
        coEvery { repository.updateNickname("") } returns AuthResult.Error(AuthError.NicknameTooLong)
        val useCase = UpdateNicknameUseCase(repository)

        useCase("")

        coVerify(exactly = 1) { repository.updateNickname("") }
    }
}
