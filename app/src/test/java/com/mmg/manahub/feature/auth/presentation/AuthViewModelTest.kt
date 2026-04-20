package com.mmg.manahub.feature.auth.presentation

import android.content.Context
import app.cash.turbine.test
import com.mmg.manahub.feature.auth.domain.model.AuthError
import com.mmg.manahub.feature.auth.domain.model.AuthResult
import com.mmg.manahub.feature.auth.domain.model.AuthUser
import com.mmg.manahub.feature.auth.domain.model.SessionState
import com.mmg.manahub.feature.auth.domain.usecase.DeleteAccountUseCase
import com.mmg.manahub.feature.auth.domain.usecase.GetSessionStateUseCase
import com.mmg.manahub.feature.auth.domain.usecase.ResetPasswordUseCase
import com.mmg.manahub.feature.auth.domain.usecase.SignInWithEmailUseCase
import com.mmg.manahub.feature.auth.domain.usecase.SignInWithGoogleUseCase
import com.mmg.manahub.feature.auth.domain.usecase.SignOutUseCase
import com.mmg.manahub.feature.auth.domain.usecase.SignUpWithEmailUseCase
import com.mmg.manahub.feature.auth.domain.usecase.UpdateNicknameUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.anyNullable
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

/**
 * Unit tests for [AuthViewModel].
 *
 * Strategy:
 * - All UseCases are mocked with MockK — no real business logic is exercised here.
 * - [Context] is mocked so getString() returns the actual string values from strings.xml.
 *   All expected message strings are in English (matching strings.xml as of DB v22).
 * - [StandardTestDispatcher] is used so coroutines launched in viewModelScope advance
 *   under test control via [advanceUntilIdle].
 * - Turbine is used to assert full [AuthUiState] emission sequences.
 * - [signInWithGoogle] is NOT tested here: it requires CredentialManager, which depends
 *   on the Android framework and is unsuitable for JVM unit tests.
 *
 * BUG FIXED (vs previous version): [buildViewModel] now passes the required [appContext]
 * parameter, and error-message assertions use English strings (matching strings.xml).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    // ── Test dispatcher ───────────────────────────────────────────────────────

    private val testDispatcher = StandardTestDispatcher()

    // ── Mocks ─────────────────────────────────────────────────────────────────

    private val signInWithEmailUseCase  = mockk<SignInWithEmailUseCase>()
    private val signUpWithEmailUseCase  = mockk<SignUpWithEmailUseCase>()
    private val signInWithGoogleUseCase = mockk<SignInWithGoogleUseCase>()
    private val signOutUseCase          = mockk<SignOutUseCase>()
    private val getSessionState         = mockk<GetSessionStateUseCase>()
    private val resetPasswordUseCase    = mockk<ResetPasswordUseCase>()
    private val deleteAccountUseCase    = mockk<DeleteAccountUseCase>()
    private val updateNicknameUseCase   = mockk<UpdateNicknameUseCase>()

    /**
     * Context mock that returns the actual English string values from strings.xml.
     * Each getString stub maps a resource ID (via the symbolic constant) to its
     * English string value. This avoids Robolectric while still allowing assertion
     * on exact error messages.
     *
     * NOTE: R.string constants are resolved at compile time to integer IDs.
     * We use `any()` matchers here so that each call to getString(anyInt) returns
     * a deterministic string based on which auth error is under test.
     * For cases where multiple different IDs must be distinguished, we use
     * separate mock stubs per test.
     */
    private val appContext = mockk<Context>(relaxed = true)

    // Controls sessionState emissions
    private val sessionStateFlow = MutableStateFlow<SessionState>(SessionState.Loading)

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private val dummyAuthUser = AuthUser(
        id        = "user-uuid-001",
        email     = "test@example.com",
        nickname  = "TestUser",
        gameTag   = null,
        avatarUrl = null,
        provider  = "email",
    )

    // ── SUT ───────────────────────────────────────────────────────────────────

    private lateinit var viewModel: AuthViewModel

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Stubs Context.getString for all resource IDs used by AuthViewModel's
     * toUiMessage() and validation helpers. Returns the English string values
     * exactly as defined in strings.xml.
     */
    private fun stubContextStrings() {
        // Error messages (from AuthError.toUiMessage())
        every { appContext.getString(com.mmg.manahub.R.string.auth_error_invalid_credentials) } returns
                "Incorrect email or password"
        every { appContext.getString(com.mmg.manahub.R.string.auth_error_email_in_use) } returns
                "This email is already registered"
        every { appContext.getString(com.mmg.manahub.R.string.auth_error_network) } returns
                "No connection. Check your network"
        every { appContext.getString(com.mmg.manahub.R.string.auth_error_session_expired) } returns
                "Session expired. Sign in again"
        every { appContext.getString(com.mmg.manahub.R.string.auth_error_user_not_found) } returns
                "User not found"
        every { appContext.getString(com.mmg.manahub.R.string.auth_email_confirmation_sent) } returns
                "Check your inbox to confirm your email!"
        every { appContext.getString(com.mmg.manahub.R.string.auth_error_nickname_inappropriate) } returns
                "This nickname contains inappropriate content. Please choose another."
        every { appContext.getString(com.mmg.manahub.R.string.auth_error_nickname_too_long) } returns
                "Nickname must be 30 characters or less."
        every { appContext.getString(com.mmg.manahub.R.string.auth_error_unknown) } returns
                "An unexpected error occurred. Try again"

        // Validation messages
        every { appContext.getString(com.mmg.manahub.R.string.auth_validation_email_required) } returns
                "Enter an email address"
        every { appContext.getString(com.mmg.manahub.R.string.auth_validation_password_required) } returns
                "Enter a password"
        every { appContext.getString(com.mmg.manahub.R.string.auth_error_invalid_email) } returns
                "Invalid email format"
        every { appContext.getString(com.mmg.manahub.R.string.auth_error_password_requirements) } returns
                "Password must include uppercase, lowercase, a number and a symbol"
        every { appContext.getString(com.mmg.manahub.R.string.auth_error_nickname_required) } returns
                "Please enter a nickname."
        every { appContext.getString(com.mmg.manahub.R.string.auth_error_nickname_invalid) } returns
                "Nickname can only contain letters, numbers, spaces and basic punctuation."

        // Google sign-in errors
        every { appContext.getString(com.mmg.manahub.R.string.auth_error_credential_unsupported) } returns
                "Credential type not supported"
        every { appContext.getString(com.mmg.manahub.R.string.auth_error_google_cancelled) } returns
                "Google Sign-In cancelled"
        every { appContext.getString(com.mmg.manahub.R.string.auth_error_google_failed) } returns
                "Error signing in with Google"
    }

    private fun buildViewModel(): AuthViewModel = AuthViewModel(
        signInWithEmailUseCase  = signInWithEmailUseCase,
        signUpWithEmailUseCase  = signUpWithEmailUseCase,
        signInWithGoogleUseCase = signInWithGoogleUseCase,
        signOutUseCase          = signOutUseCase,
        getSessionState         = getSessionState,
        resetPasswordUseCase    = resetPasswordUseCase,
        deleteAccountUseCase    = deleteAccountUseCase,
        updateNicknameUseCase   = updateNicknameUseCase,
        appContext               = appContext,
    )

    // ── Setup / Teardown ─────────────────────────────────────────────────────

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { getSessionState() } returns sessionStateFlow
        stubContextStrings()
        viewModel = buildViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 1 — signInWithEmail (happy + error paths)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given valid credentials when signInWithEmail then uiState transitions Loading then Success`() = runTest {
        coEvery { signInWithEmailUseCase(any(), any()) } returns AuthResult.Success(dummyAuthUser)

        viewModel.uiState.test {
            assertEquals(AuthUiState.Idle, awaitItem())
            viewModel.signInWithEmail("test@example.com", "password123")
            assertEquals(AuthUiState.Loading, awaitItem())
            assertEquals(AuthUiState.Success, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given invalid credentials when signInWithEmail then uiState emits Error with correct English message`() = runTest {
        coEvery { signInWithEmailUseCase(any(), any()) } returns AuthResult.Error(AuthError.InvalidCredentials)

        viewModel.uiState.test {
            assertEquals(AuthUiState.Idle, awaitItem())
            viewModel.signInWithEmail("wrong@example.com", "wrongpass")
            assertEquals(AuthUiState.Loading, awaitItem())
            val errorState = awaitItem() as AuthUiState.Error
            assertEquals("Incorrect email or password", errorState.message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given network error when signInWithEmail then uiState emits Error with network message`() = runTest {
        coEvery { signInWithEmailUseCase(any(), any()) } returns AuthResult.Error(AuthError.NetworkError)

        viewModel.uiState.test {
            assertEquals(AuthUiState.Idle, awaitItem())
            viewModel.signInWithEmail("test@example.com", "password123")
            assertEquals(AuthUiState.Loading, awaitItem())
            val errorState = awaitItem() as AuthUiState.Error
            assertEquals("No connection. Check your network", errorState.message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given session expired when signInWithEmail then uiState emits Error with session expiry message`() = runTest {
        coEvery { signInWithEmailUseCase(any(), any()) } returns AuthResult.Error(AuthError.SessionExpired)

        viewModel.uiState.test {
            assertEquals(AuthUiState.Idle, awaitItem())
            viewModel.signInWithEmail("test@example.com", "password123")
            assertEquals(AuthUiState.Loading, awaitItem())
            val errorState = awaitItem() as AuthUiState.Error
            assertEquals("Session expired. Sign in again", errorState.message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given user not found when signInWithEmail then uiState emits Error with user not found message`() = runTest {
        coEvery { signInWithEmailUseCase(any(), any()) } returns AuthResult.Error(AuthError.UserNotFound)

        viewModel.uiState.test {
            assertEquals(AuthUiState.Idle, awaitItem())
            viewModel.signInWithEmail("ghost@example.com", "password123")
            assertEquals(AuthUiState.Loading, awaitItem())
            val errorState = awaitItem() as AuthUiState.Error
            assertEquals("User not found", errorState.message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given unknown server error when signInWithEmail then uiState emits generic error message`() = runTest {
        // Unknown errors must NOT expose raw server messages to avoid leaking Supabase internals
        coEvery { signInWithEmailUseCase(any(), any()) } returns AuthResult.Error(AuthError.Unknown("constraint violation on auth.users"))

        viewModel.uiState.test {
            assertEquals(AuthUiState.Idle, awaitItem())
            viewModel.signInWithEmail("test@example.com", "password123")
            assertEquals(AuthUiState.Loading, awaitItem())
            val errorState = awaitItem() as AuthUiState.Error
            // Must return the generic string, NOT the raw server message
            assertEquals("An unexpected error occurred. Try again", errorState.message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 2 — signInWithEmail input validation (pre-flight, no network call)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given blank email when signInWithEmail then uiState emits Error immediately without Loading`() = runTest {
        viewModel.uiState.test {
            assertEquals(AuthUiState.Idle, awaitItem())
            viewModel.signInWithEmail("", "password123")
            // Validation fires synchronously — no Loading state emitted
            val errorState = awaitItem() as AuthUiState.Error
            assertEquals("Enter an email address", errorState.message)
            cancelAndIgnoreRemainingEvents()
        }
        // Use case must never be invoked when validation fails
        coVerify(exactly = 0) { signInWithEmailUseCase(any(), any()) }
    }

    @Test
    fun `given whitespace-only email when signInWithEmail then uiState emits email required error`() = runTest {
        viewModel.uiState.test {
            assertEquals(AuthUiState.Idle, awaitItem())
            viewModel.signInWithEmail("   ", "password123")
            val errorState = awaitItem() as AuthUiState.Error
            assertEquals("Enter an email address", errorState.message)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 0) { signInWithEmailUseCase(any(), any()) }
    }

    @Test
    fun `given malformed email when signInWithEmail then uiState emits invalid email error`() = runTest {
        viewModel.uiState.test {
            assertEquals(AuthUiState.Idle, awaitItem())
            viewModel.signInWithEmail("not-an-email", "password123")
            val errorState = awaitItem() as AuthUiState.Error
            assertEquals("Invalid email format", errorState.message)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 0) { signInWithEmailUseCase(any(), any()) }
    }

    @Test
    fun `given blank password when signInWithEmail then uiState emits password required error`() = runTest {
        viewModel.uiState.test {
            assertEquals(AuthUiState.Idle, awaitItem())
            viewModel.signInWithEmail("test@example.com", "")
            val errorState = awaitItem() as AuthUiState.Error
            assertEquals("Enter a password", errorState.message)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 0) { signInWithEmailUseCase(any(), any()) }
    }

    @Test
    fun `given email with leading and trailing spaces when signInWithEmail then validation passes and use case is called with trimmed email`() = runTest {
        coEvery { signInWithEmailUseCase("test@example.com", any()) } returns AuthResult.Success(dummyAuthUser)

        viewModel.uiState.test {
            assertEquals(AuthUiState.Idle, awaitItem())
            viewModel.signInWithEmail("  test@example.com  ", "password123")
            assertEquals(AuthUiState.Loading, awaitItem())
            assertEquals(AuthUiState.Success, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        // Verify the email was trimmed before reaching the use case
        coVerify(exactly = 1) { signInWithEmailUseCase("test@example.com", "password123") }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 3 — signUpWithEmail (happy + error + validation paths)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given new user when signUpWithEmail then uiState transitions Loading then Success`() = runTest {
        coEvery { signUpWithEmailUseCase(any(), any(), any(), anyNullable()) } returns AuthResult.Success(dummyAuthUser)

        viewModel.uiState.test {
            assertEquals(AuthUiState.Idle, awaitItem())
            viewModel.signUpWithEmail("new@example.com", "Password1!", "Hero")
            assertEquals(AuthUiState.Loading, awaitItem())
            assertEquals(AuthUiState.Success, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given email confirmation required when signUpWithEmail then uiState emits EmailConfirmationSent`() = runTest {
        coEvery { signUpWithEmailUseCase(any(), any(), any(), anyNullable()) } returns AuthResult.Error(AuthError.EmailConfirmationRequired)

        viewModel.uiState.test {
            assertEquals(AuthUiState.Idle, awaitItem())
            viewModel.signUpWithEmail("confirm@example.com", "Password1!", "Hero")
            assertEquals(AuthUiState.Loading, awaitItem())
            assertEquals(AuthUiState.EmailConfirmationSent, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given email already in use when signUpWithEmail then uiState emits Error with registration message`() = runTest {
        coEvery { signUpWithEmailUseCase(any(), any(), any(), anyNullable()) } returns AuthResult.Error(AuthError.EmailAlreadyInUse)

        viewModel.uiState.test {
            assertEquals(AuthUiState.Idle, awaitItem())
            viewModel.signUpWithEmail("existing@example.com", "Password1!", "Hero")
            assertEquals(AuthUiState.Loading, awaitItem())
            val errorState = awaitItem() as AuthUiState.Error
            assertEquals("This email is already registered", errorState.message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given network error when signUpWithEmail then uiState emits Error with network message`() = runTest {
        coEvery { signUpWithEmailUseCase(any(), any(), any(), anyNullable()) } returns AuthResult.Error(AuthError.NetworkError)

        viewModel.uiState.test {
            assertEquals(AuthUiState.Idle, awaitItem())
            viewModel.signUpWithEmail("new@example.com", "Password1!", "Hero")
            assertEquals(AuthUiState.Loading, awaitItem())
            val errorState = awaitItem() as AuthUiState.Error
            assertEquals("No connection. Check your network", errorState.message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given unknown error when signUpWithEmail then uiState emits generic error message not raw server text`() = runTest {
        coEvery { signUpWithEmailUseCase(any(), any(), any(), anyNullable()) } returns AuthResult.Error(AuthError.Unknown("pg: duplicate key"))

        viewModel.uiState.test {
            assertEquals(AuthUiState.Idle, awaitItem())
            viewModel.signUpWithEmail("new@example.com", "Password1!", "Hero")
            assertEquals(AuthUiState.Loading, awaitItem())
            val errorState = awaitItem() as AuthUiState.Error
            // Raw Postgres error must NOT be surfaced — generic message is expected
            assertEquals("An unexpected error occurred. Try again", errorState.message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given blank nickname when signUpWithEmail then uiState emits Error without Loading`() = runTest {
        viewModel.uiState.test {
            assertEquals(AuthUiState.Idle, awaitItem())
            viewModel.signUpWithEmail("new@example.com", "Password1!", "")
            val errorState = awaitItem() as AuthUiState.Error
            assertEquals("Please enter a nickname.", errorState.message)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 0) { signUpWithEmailUseCase(any(), any(), any(), anyNullable()) }
    }

    @Test
    fun `given nickname exceeding 30 chars when signUpWithEmail then uiState emits Error without Loading`() = runTest {
        val tooLong = "A".repeat(31)
        viewModel.uiState.test {
            assertEquals(AuthUiState.Idle, awaitItem())
            viewModel.signUpWithEmail("new@example.com", "Password1!", tooLong)
            val errorState = awaitItem() as AuthUiState.Error
            assertEquals("Nickname must be 30 characters or less.", errorState.message)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 0) { signUpWithEmailUseCase(any(), any(), any(), anyNullable()) }
    }

    @Test
    fun `given nickname with exactly 30 chars when signUpWithEmail then validation passes`() = runTest {
        val exactly30 = "A".repeat(30)
        coEvery { signUpWithEmailUseCase(any(), any(), exactly30, anyNullable()) } returns AuthResult.Success(dummyAuthUser)

        viewModel.uiState.test {
            assertEquals(AuthUiState.Idle, awaitItem())
            viewModel.signUpWithEmail("new@example.com", "Password1!", exactly30)
            assertEquals(AuthUiState.Loading, awaitItem())
            assertEquals(AuthUiState.Success, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given nickname with illegal characters when signUpWithEmail then uiState emits invalid nickname error`() = runTest {
        viewModel.uiState.test {
            assertEquals(AuthUiState.Idle, awaitItem())
            // Exclamation marks are not in the allowed character set
            viewModel.signUpWithEmail("new@example.com", "Password1!", "Hero!")
            val errorState = awaitItem() as AuthUiState.Error
            assertEquals("Nickname can only contain letters, numbers, spaces and basic punctuation.", errorState.message)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 0) { signUpWithEmailUseCase(any(), any(), any(), anyNullable()) }
    }

    @Test
    fun `given weak password during sign-up when signUpWithEmail then uiState emits password requirements error`() = runTest {
        viewModel.uiState.test {
            assertEquals(AuthUiState.Idle, awaitItem())
            // Fails: no uppercase, no digit, no symbol
            viewModel.signUpWithEmail("new@example.com", "weakpassword", "Hero")
            val errorState = awaitItem() as AuthUiState.Error
            assertEquals("Password must include uppercase, lowercase, a number and a symbol", errorState.message)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 0) { signUpWithEmailUseCase(any(), any(), any(), anyNullable()) }
    }

    @Test
    fun `given email validation checked before nickname when signUpWithEmail then email error is returned first`() = runTest {
        viewModel.uiState.test {
            assertEquals(AuthUiState.Idle, awaitItem())
            // Both email and nickname are invalid; email is validated first in the ViewModel
            viewModel.signUpWithEmail("not-an-email", "Password1!", "")
            val errorState = awaitItem() as AuthUiState.Error
            assertEquals("Invalid email format", errorState.message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 4 — updateNickname
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given valid nickname when updateNickname then uiState transitions Loading then NicknameUpdated`() = runTest {
        coEvery { updateNicknameUseCase("Gandalf") } returns AuthResult.Success(dummyAuthUser)

        viewModel.uiState.test {
            assertEquals(AuthUiState.Idle, awaitItem())
            viewModel.updateNickname("Gandalf")
            assertEquals(AuthUiState.Loading, awaitItem())
            assertEquals(AuthUiState.NicknameUpdated, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given nickname with leading spaces when updateNickname then use case is called with trimmed value`() = runTest {
        coEvery { updateNicknameUseCase("Gandalf") } returns AuthResult.Success(dummyAuthUser)

        viewModel.uiState.test {
            assertEquals(AuthUiState.Idle, awaitItem())
            viewModel.updateNickname("  Gandalf  ")
            assertEquals(AuthUiState.Loading, awaitItem())
            assertEquals(AuthUiState.NicknameUpdated, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 1) { updateNicknameUseCase("Gandalf") }
    }

    @Test
    fun `given blank nickname when updateNickname then uiState emits Error without Loading`() = runTest {
        viewModel.uiState.test {
            assertEquals(AuthUiState.Idle, awaitItem())
            viewModel.updateNickname("")
            val errorState = awaitItem() as AuthUiState.Error
            assertEquals("Please enter a nickname.", errorState.message)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 0) { updateNicknameUseCase(any()) }
    }

    @Test
    fun `given nickname exceeding 30 chars when updateNickname then uiState emits Error without Loading`() = runTest {
        viewModel.uiState.test {
            assertEquals(AuthUiState.Idle, awaitItem())
            viewModel.updateNickname("A".repeat(31))
            val errorState = awaitItem() as AuthUiState.Error
            assertEquals("Nickname must be 30 characters or less.", errorState.message)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 0) { updateNicknameUseCase(any()) }
    }

    @Test
    fun `given inappropriate nickname rejected by server when updateNickname then uiState emits Error with inappropriate message`() = runTest {
        coEvery { updateNicknameUseCase(any()) } returns AuthResult.Error(AuthError.NicknameInappropriate)

        viewModel.uiState.test {
            assertEquals(AuthUiState.Idle, awaitItem())
            viewModel.updateNickname("BadWord")
            assertEquals(AuthUiState.Loading, awaitItem())
            val errorState = awaitItem() as AuthUiState.Error
            assertEquals("This nickname contains inappropriate content. Please choose another.", errorState.message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given network error when updateNickname then uiState emits Error with network message`() = runTest {
        coEvery { updateNicknameUseCase(any()) } returns AuthResult.Error(AuthError.NetworkError)

        viewModel.uiState.test {
            assertEquals(AuthUiState.Idle, awaitItem())
            viewModel.updateNickname("ValidName")
            assertEquals(AuthUiState.Loading, awaitItem())
            val errorState = awaitItem() as AuthUiState.Error
            assertEquals("No connection. Check your network", errorState.message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given session expired when updateNickname then uiState emits Error with session message`() = runTest {
        coEvery { updateNicknameUseCase(any()) } returns AuthResult.Error(AuthError.SessionExpired)

        viewModel.uiState.test {
            assertEquals(AuthUiState.Idle, awaitItem())
            viewModel.updateNickname("ValidName")
            assertEquals(AuthUiState.Loading, awaitItem())
            val errorState = awaitItem() as AuthUiState.Error
            assertEquals("Session expired. Sign in again", errorState.message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given nickname with exactly 30 chars when updateNickname then validation passes and use case is called`() = runTest {
        val exactly30 = "A".repeat(30)
        coEvery { updateNicknameUseCase(exactly30) } returns AuthResult.Success(dummyAuthUser)

        viewModel.uiState.test {
            assertEquals(AuthUiState.Idle, awaitItem())
            viewModel.updateNickname(exactly30)
            assertEquals(AuthUiState.Loading, awaitItem())
            assertEquals(AuthUiState.NicknameUpdated, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 5 — signOut
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `when signOut then uiState becomes Idle and signOutUseCase is called`() = runTest {
        coEvery { signInWithEmailUseCase(any(), any()) } returns AuthResult.Success(dummyAuthUser)
        coEvery { signOutUseCase() } returns AuthResult.Success(Unit)

        viewModel.signInWithEmail("test@example.com", "password123")
        advanceUntilIdle()

        viewModel.uiState.test {
            awaitItem() // drain current Success state
            viewModel.signOut()
            advanceUntilIdle()
            assertEquals(AuthUiState.Idle, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 1) { signOutUseCase() }
    }

    @Test
    fun `given already Idle state when signOut then uiState stays Idle and signOutUseCase is called`() = runTest {
        coEvery { signOutUseCase() } returns AuthResult.Success(Unit)

        viewModel.signOut()
        advanceUntilIdle()

        assertEquals(AuthUiState.Idle, viewModel.uiState.value)
        coVerify(exactly = 1) { signOutUseCase() }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 6 — deleteAccount
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given successful deletion when deleteAccount then uiState transitions Loading then AccountDeleted`() = runTest {
        coEvery { deleteAccountUseCase() } returns AuthResult.Success(Unit)

        viewModel.uiState.test {
            assertEquals(AuthUiState.Idle, awaitItem())
            viewModel.deleteAccount()
            assertEquals(AuthUiState.Loading, awaitItem())
            assertEquals(AuthUiState.AccountDeleted, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given network error when deleteAccount then uiState emits Error with message`() = runTest {
        coEvery { deleteAccountUseCase() } returns AuthResult.Error(AuthError.NetworkError)

        viewModel.uiState.test {
            assertEquals(AuthUiState.Idle, awaitItem())
            viewModel.deleteAccount()
            assertEquals(AuthUiState.Loading, awaitItem())
            val errorState = awaitItem() as AuthUiState.Error
            assertEquals("No connection. Check your network", errorState.message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given session expired when deleteAccount then uiState emits Error with session message`() = runTest {
        coEvery { deleteAccountUseCase() } returns AuthResult.Error(AuthError.SessionExpired)

        viewModel.uiState.test {
            assertEquals(AuthUiState.Idle, awaitItem())
            viewModel.deleteAccount()
            assertEquals(AuthUiState.Loading, awaitItem())
            val errorState = awaitItem() as AuthUiState.Error
            assertEquals("Session expired. Sign in again", errorState.message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given unknown error when deleteAccount then uiState emits generic error not raw message`() = runTest {
        coEvery { deleteAccountUseCase() } returns AuthResult.Error(AuthError.Unknown("delete_current_user: permission denied"))

        viewModel.uiState.test {
            assertEquals(AuthUiState.Idle, awaitItem())
            viewModel.deleteAccount()
            assertEquals(AuthUiState.Loading, awaitItem())
            val errorState = awaitItem() as AuthUiState.Error
            assertEquals("An unexpected error occurred. Try again", errorState.message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 7 — resetPassword (happy + validation + errors)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given valid email when resetPassword then uiState transitions Loading then ResetSent`() = runTest {
        coEvery { resetPasswordUseCase(any()) } returns AuthResult.Success(Unit)

        viewModel.uiState.test {
            assertEquals(AuthUiState.Idle, awaitItem())
            viewModel.resetPassword("user@example.com")
            assertEquals(AuthUiState.Loading, awaitItem())
            assertEquals(AuthUiState.ResetSent, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given blank email when resetPassword then uiState emits Error without Loading`() = runTest {
        viewModel.uiState.test {
            assertEquals(AuthUiState.Idle, awaitItem())
            viewModel.resetPassword("")
            val errorState = awaitItem() as AuthUiState.Error
            assertEquals("Invalid email format", errorState.message)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 0) { resetPasswordUseCase(any()) }
    }

    @Test
    fun `given malformed email when resetPassword then uiState emits Error without Loading`() = runTest {
        viewModel.uiState.test {
            assertEquals(AuthUiState.Idle, awaitItem())
            viewModel.resetPassword("not-valid")
            val errorState = awaitItem() as AuthUiState.Error
            assertEquals("Invalid email format", errorState.message)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 0) { resetPasswordUseCase(any()) }
    }

    @Test
    fun `given user not found error when resetPassword then uiState emits Error with user not found message`() = runTest {
        coEvery { resetPasswordUseCase(any()) } returns AuthResult.Error(AuthError.UserNotFound)

        viewModel.uiState.test {
            assertEquals(AuthUiState.Idle, awaitItem())
            viewModel.resetPassword("ghost@example.com")
            assertEquals(AuthUiState.Loading, awaitItem())
            val errorState = awaitItem() as AuthUiState.Error
            assertEquals("User not found", errorState.message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given network failure when resetPassword then uiState emits Error with network message`() = runTest {
        coEvery { resetPasswordUseCase(any()) } returns AuthResult.Error(AuthError.NetworkError)

        viewModel.uiState.test {
            assertEquals(AuthUiState.Idle, awaitItem())
            viewModel.resetPassword("user@example.com")
            assertEquals(AuthUiState.Loading, awaitItem())
            val errorState = awaitItem() as AuthUiState.Error
            assertEquals("No connection. Check your network", errorState.message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given email with leading spaces when resetPassword then use case is called with trimmed email`() = runTest {
        coEvery { resetPasswordUseCase("user@example.com") } returns AuthResult.Success(Unit)

        viewModel.uiState.test {
            assertEquals(AuthUiState.Idle, awaitItem())
            viewModel.resetPassword("  user@example.com  ")
            assertEquals(AuthUiState.Loading, awaitItem())
            assertEquals(AuthUiState.ResetSent, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 1) { resetPasswordUseCase("user@example.com") }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 8 — resetUiState
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given Error state when resetUiState then uiState becomes Idle`() = runTest {
        coEvery { signInWithEmailUseCase(any(), any()) } returns AuthResult.Error(AuthError.NetworkError)
        viewModel.signInWithEmail("test@example.com", "password123")
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is AuthUiState.Error)

        viewModel.resetUiState()

        assertEquals(AuthUiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun `given Success state when resetUiState then uiState becomes Idle`() = runTest {
        coEvery { signInWithEmailUseCase(any(), any()) } returns AuthResult.Success(dummyAuthUser)
        viewModel.signInWithEmail("test@example.com", "password123")
        advanceUntilIdle()
        assertEquals(AuthUiState.Success, viewModel.uiState.value)

        viewModel.resetUiState()

        assertEquals(AuthUiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun `given ResetSent state when resetUiState then uiState becomes Idle`() = runTest {
        coEvery { resetPasswordUseCase(any()) } returns AuthResult.Success(Unit)
        viewModel.resetPassword("user@example.com")
        advanceUntilIdle()
        assertEquals(AuthUiState.ResetSent, viewModel.uiState.value)

        viewModel.resetUiState()

        assertEquals(AuthUiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun `given EmailConfirmationSent state when resetUiState then uiState becomes Idle`() = runTest {
        coEvery { signUpWithEmailUseCase(any(), any(), any(), anyNullable()) } returns AuthResult.Error(AuthError.EmailConfirmationRequired)
        viewModel.signUpWithEmail("confirm@example.com", "Password1!", "Hero")
        advanceUntilIdle()
        assertEquals(AuthUiState.EmailConfirmationSent, viewModel.uiState.value)

        viewModel.resetUiState()

        assertEquals(AuthUiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun `given NicknameUpdated state when resetUiState then uiState becomes Idle`() = runTest {
        coEvery { updateNicknameUseCase(any()) } returns AuthResult.Success(dummyAuthUser)
        viewModel.updateNickname("Gandalf")
        advanceUntilIdle()
        assertEquals(AuthUiState.NicknameUpdated, viewModel.uiState.value)

        viewModel.resetUiState()

        assertEquals(AuthUiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun `given AccountDeleted state when resetUiState then uiState becomes Idle`() = runTest {
        coEvery { deleteAccountUseCase() } returns AuthResult.Success(Unit)
        viewModel.deleteAccount()
        advanceUntilIdle()
        assertEquals(AuthUiState.AccountDeleted, viewModel.uiState.value)

        viewModel.resetUiState()

        assertEquals(AuthUiState.Idle, viewModel.uiState.value)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 9 — sessionState (from GetSessionStateUseCase)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given GetSessionStateUseCase emits Loading when sessionState collected then reflects Loading`() = runTest {
        viewModel.sessionState.test {
            assertEquals(SessionState.Loading, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given GetSessionStateUseCase emits Unauthenticated when sessionState updates then ViewModel reflects it`() = runTest {
        viewModel.sessionState.test {
            awaitItem() // consume initial Loading
            sessionStateFlow.value = SessionState.Unauthenticated
            assertEquals(SessionState.Unauthenticated, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given GetSessionStateUseCase emits Authenticated when sessionState updates then ViewModel reflects user`() = runTest {
        val authenticatedState = SessionState.Authenticated(dummyAuthUser)

        viewModel.sessionState.test {
            awaitItem() // consume initial Loading
            sessionStateFlow.value = authenticatedState
            val emitted = awaitItem()
            assertTrue(emitted is SessionState.Authenticated)
            assertEquals(dummyAuthUser.id, (emitted as SessionState.Authenticated).user.id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given session transitions from Loading to Authenticated when sessionState observed then both states emitted in order`() = runTest {
        sessionStateFlow.value = SessionState.Loading

        viewModel.sessionState.test {
            assertEquals(SessionState.Loading, awaitItem())
            sessionStateFlow.value = SessionState.Authenticated(dummyAuthUser)
            val nextState = awaitItem()
            assertTrue(nextState is SessionState.Authenticated)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 10 — isPasswordStrong (companion object, pure logic — no mocks)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given password meeting all requirements when isPasswordStrong then returns true`() {
        assertTrue(AuthViewModel.isPasswordStrong("Password1!"))
    }

    @Test
    fun `given password shorter than 8 chars when isPasswordStrong then returns false`() {
        assertFalse(AuthViewModel.isPasswordStrong("Pw1!"))
    }

    @Test
    fun `given password with no uppercase when isPasswordStrong then returns false`() {
        assertFalse(AuthViewModel.isPasswordStrong("password1!"))
    }

    @Test
    fun `given password with no lowercase when isPasswordStrong then returns false`() {
        assertFalse(AuthViewModel.isPasswordStrong("PASSWORD1!"))
    }

    @Test
    fun `given password with no digit when isPasswordStrong then returns false`() {
        assertFalse(AuthViewModel.isPasswordStrong("Password!!"))
    }

    @Test
    fun `given password with no symbol when isPasswordStrong then returns false`() {
        assertFalse(AuthViewModel.isPasswordStrong("Password1"))
    }

    @Test
    fun `given empty password when isPasswordStrong then returns false`() {
        assertFalse(AuthViewModel.isPasswordStrong(""))
    }

    @Test
    fun `given exactly 8 char password meeting all rules when isPasswordStrong then returns true`() {
        // Boundary: exactly 8 chars with all required character classes
        assertTrue(AuthViewModel.isPasswordStrong("Abcdef1!"))
    }

    @Test
    fun `given password with only symbols when isPasswordStrong then returns false`() {
        // No letters at all — fails uppercase AND lowercase checks
        assertFalse(AuthViewModel.isPasswordStrong("!!!!!!!!!!"))
    }
}
