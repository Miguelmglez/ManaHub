package com.mmg.manahub.feature.auth.presentation

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
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [AuthViewModel].
 *
 * Strategy:
 * - All UseCases are mocked with MockK — no real business logic is exercised here.
 * - [StandardTestDispatcher] is used (consistent with the rest of the project)
 *   so coroutines launched in viewModelScope advance under test control.
 * - Turbine is used to assert [AuthUiState] transitions for multi-emission flows.
 * - [signInWithGoogle] is NOT tested here: it requires CredentialManager which
 *   depends on the Android framework and is unsuitable for JVM unit tests.
 *
 * DESIGN NOTES:
 * - [AuthViewModel.sessionState] is a stateIn flow initialized from [GetSessionStateUseCase].
 *   We back it with a [MutableStateFlow] in tests so we can control emissions.
 * - [AuthViewModel.uiState] starts as [AuthUiState.Idle]. Every action sets Loading
 *   first, then transitions to the final state.
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

    // Controls sessionState emissions
    private val sessionStateFlow = MutableStateFlow<SessionState>(SessionState.Loading)

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private val dummyAuthUser = AuthUser(
        id          = "user-uuid-001",
        email       = "test@example.com",
        displayName = "Test User",
        avatarUrl   = null,
        provider    = "email",
    )

    // ── SUT ───────────────────────────────────────────────────────────────────

    private lateinit var viewModel: AuthViewModel

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildViewModel(): AuthViewModel = AuthViewModel(
        signInWithEmailUseCase  = signInWithEmailUseCase,
        signUpWithEmailUseCase  = signUpWithEmailUseCase,
        signInWithGoogleUseCase = signInWithGoogleUseCase,
        signOutUseCase          = signOutUseCase,
        getSessionState         = getSessionState,
        resetPasswordUseCase    = resetPasswordUseCase,
        deleteAccountUseCase    = deleteAccountUseCase,
    )

    // ── Setup / Teardown ─────────────────────────────────────────────────────

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { getSessionState() } returns sessionStateFlow
        viewModel = buildViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 1 — signInWithEmail
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given valid credentials when signInWithEmail then uiState transitions Loading then Success`() = runTest {
        // Arrange
        coEvery { signInWithEmailUseCase(any(), any()) } returns AuthResult.Success(dummyAuthUser)

        // Act & Assert
        viewModel.uiState.test {
            assertEquals(AuthUiState.Idle, awaitItem())

            viewModel.signInWithEmail("test@example.com", "password123")

            assertEquals(AuthUiState.Loading, awaitItem())
            assertEquals(AuthUiState.Success, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given invalid credentials when signInWithEmail then uiState transitions Loading then Error with correct message`() = runTest {
        // Arrange
        coEvery { signInWithEmailUseCase(any(), any()) } returns AuthResult.Error(AuthError.InvalidCredentials)

        // Act & Assert
        viewModel.uiState.test {
            assertEquals(AuthUiState.Idle, awaitItem())

            viewModel.signInWithEmail("wrong@example.com", "wrongpass")

            assertEquals(AuthUiState.Loading, awaitItem())
            val errorState = awaitItem()
            assertTrue(errorState is AuthUiState.Error)
            assertEquals("Email o contraseña incorrectos", (errorState as AuthUiState.Error).message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given network error when signInWithEmail then uiState emits Error with network message`() = runTest {
        // Arrange
        coEvery { signInWithEmailUseCase(any(), any()) } returns AuthResult.Error(AuthError.NetworkError)

        // Act & Assert
        viewModel.uiState.test {
            assertEquals(AuthUiState.Idle, awaitItem())
            viewModel.signInWithEmail("test@example.com", "password123")
            assertEquals(AuthUiState.Loading, awaitItem())
            val errorState = awaitItem() as AuthUiState.Error
            assertEquals("Sin conexión. Verifica tu red", errorState.message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given session expired when signInWithEmail then uiState emits Error with expiry message`() = runTest {
        // Arrange
        coEvery { signInWithEmailUseCase(any(), any()) } returns AuthResult.Error(AuthError.SessionExpired)

        // Act & Assert
        viewModel.uiState.test {
            assertEquals(AuthUiState.Idle, awaitItem())
            viewModel.signInWithEmail("test@example.com", "password123")
            assertEquals(AuthUiState.Loading, awaitItem())
            val errorState = awaitItem() as AuthUiState.Error
            assertEquals("Sesión expirada. Inicia sesión de nuevo", errorState.message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 2 — signUpWithEmail
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given new user when signUpWithEmail then uiState transitions Loading then Success`() = runTest {
        // Arrange
        coEvery { signUpWithEmailUseCase(any(), any()) } returns AuthResult.Success(dummyAuthUser)

        // Act & Assert
        viewModel.uiState.test {
            assertEquals(AuthUiState.Idle, awaitItem())
            viewModel.signUpWithEmail("new@example.com", "password123")
            assertEquals(AuthUiState.Loading, awaitItem())
            assertEquals(AuthUiState.Success, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given email confirmation required when signUpWithEmail then uiState emits EmailConfirmationSent`() = runTest {
        // Arrange
        coEvery { signUpWithEmailUseCase(any(), any()) } returns AuthResult.Error(AuthError.EmailConfirmationRequired)

        // Act & Assert
        viewModel.uiState.test {
            assertEquals(AuthUiState.Idle, awaitItem())
            viewModel.signUpWithEmail("confirm@example.com", "password123")
            assertEquals(AuthUiState.Loading, awaitItem())
            assertEquals(AuthUiState.EmailConfirmationSent, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given email already in use when signUpWithEmail then uiState emits Error with registration message`() = runTest {
        // Arrange
        coEvery { signUpWithEmailUseCase(any(), any()) } returns AuthResult.Error(AuthError.EmailAlreadyInUse)

        // Act & Assert
        viewModel.uiState.test {
            assertEquals(AuthUiState.Idle, awaitItem())
            viewModel.signUpWithEmail("existing@example.com", "password123")
            assertEquals(AuthUiState.Loading, awaitItem())
            val errorState = awaitItem() as AuthUiState.Error
            assertEquals("Este email ya está registrado", errorState.message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given network error when signUpWithEmail then uiState emits Error`() = runTest {
        // Arrange
        coEvery { signUpWithEmailUseCase(any(), any()) } returns AuthResult.Error(AuthError.NetworkError)

        // Act & Assert
        viewModel.uiState.test {
            assertEquals(AuthUiState.Idle, awaitItem())
            viewModel.signUpWithEmail("new@example.com", "password123")
            assertEquals(AuthUiState.Loading, awaitItem())
            val errorState = awaitItem() as AuthUiState.Error
            assertEquals("Sin conexión. Verifica tu red", errorState.message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given unknown error when signUpWithEmail then uiState emits Error with custom message`() = runTest {
        // Arrange
        coEvery { signUpWithEmailUseCase(any(), any()) } returns AuthResult.Error(AuthError.Unknown("Custom error message"))

        // Act & Assert
        viewModel.uiState.test {
            assertEquals(AuthUiState.Idle, awaitItem())
            viewModel.signUpWithEmail("new@example.com", "password123")
            assertEquals(AuthUiState.Loading, awaitItem())
            val errorState = awaitItem() as AuthUiState.Error
            assertEquals("Custom error message", errorState.message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 3 — signOut
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `when signOut then uiState becomes Idle and signOutUseCase is called`() = runTest {
        // Arrange: start in a non-idle state to verify the transition
        coEvery { signInWithEmailUseCase(any(), any()) } returns AuthResult.Success(dummyAuthUser)
        coEvery { signOutUseCase() } returns AuthResult.Success(Unit)

        viewModel.signInWithEmail("test@example.com", "password123")
        advanceUntilIdle()

        // Act & Assert
        viewModel.uiState.test {
            // Drain current state (Success from signIn)
            awaitItem()

            viewModel.signOut()
            advanceUntilIdle()

            assertEquals(AuthUiState.Idle, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 1) { signOutUseCase() }
    }

    @Test
    fun `given already Idle state when signOut then uiState stays Idle and signOutUseCase is called`() = runTest {
        // Arrange
        coEvery { signOutUseCase() } returns AuthResult.Success(Unit)

        // Act
        viewModel.signOut()
        advanceUntilIdle()

        // Assert
        assertEquals(AuthUiState.Idle, viewModel.uiState.value)
        coVerify(exactly = 1) { signOutUseCase() }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 4 — deleteAccount
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given successful deletion when deleteAccount then uiState transitions Loading then AccountDeleted`() = runTest {
        // Arrange
        coEvery { deleteAccountUseCase() } returns AuthResult.Success(Unit)

        // Act & Assert
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
        // Arrange
        coEvery { deleteAccountUseCase() } returns AuthResult.Error(AuthError.NetworkError)

        // Act & Assert
        viewModel.uiState.test {
            assertEquals(AuthUiState.Idle, awaitItem())
            viewModel.deleteAccount()
            assertEquals(AuthUiState.Loading, awaitItem())
            val errorState = awaitItem() as AuthUiState.Error
            assertEquals("Sin conexión. Verifica tu red", errorState.message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given unknown error when deleteAccount then uiState emits Error`() = runTest {
        // Arrange
        coEvery { deleteAccountUseCase() } returns AuthResult.Error(AuthError.Unknown("Delete failed"))

        // Act & Assert
        viewModel.uiState.test {
            assertEquals(AuthUiState.Idle, awaitItem())
            viewModel.deleteAccount()
            assertEquals(AuthUiState.Loading, awaitItem())
            val errorState = awaitItem() as AuthUiState.Error
            assertEquals("Delete failed", errorState.message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 5 — resetPassword
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given valid email when resetPassword then uiState transitions Loading then ResetSent`() = runTest {
        // Arrange
        coEvery { resetPasswordUseCase(any()) } returns AuthResult.Success(Unit)

        // Act & Assert
        viewModel.uiState.test {
            assertEquals(AuthUiState.Idle, awaitItem())
            viewModel.resetPassword("user@example.com")
            assertEquals(AuthUiState.Loading, awaitItem())
            assertEquals(AuthUiState.ResetSent, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given error from repository when resetPassword then uiState emits Error`() = runTest {
        // Arrange
        coEvery { resetPasswordUseCase(any()) } returns AuthResult.Error(AuthError.UserNotFound)

        // Act & Assert
        viewModel.uiState.test {
            assertEquals(AuthUiState.Idle, awaitItem())
            viewModel.resetPassword("ghost@example.com")
            assertEquals(AuthUiState.Loading, awaitItem())
            val errorState = awaitItem() as AuthUiState.Error
            assertEquals("Usuario no encontrado", errorState.message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given network failure when resetPassword then uiState emits Error with network message`() = runTest {
        // Arrange
        coEvery { resetPasswordUseCase(any()) } returns AuthResult.Error(AuthError.NetworkError)

        // Act & Assert
        viewModel.uiState.test {
            assertEquals(AuthUiState.Idle, awaitItem())
            viewModel.resetPassword("user@example.com")
            assertEquals(AuthUiState.Loading, awaitItem())
            val errorState = awaitItem() as AuthUiState.Error
            assertEquals("Sin conexión. Verifica tu red", errorState.message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 6 — resetUiState
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given non-idle state when resetUiState then uiState becomes Idle`() = runTest {
        // Arrange: put ViewModel into an Error state
        coEvery { signInWithEmailUseCase(any(), any()) } returns AuthResult.Error(AuthError.NetworkError)
        viewModel.signInWithEmail("test@example.com", "password123")
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is AuthUiState.Error)

        // Act
        viewModel.resetUiState()

        // Assert
        assertEquals(AuthUiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun `given Success state when resetUiState then uiState becomes Idle`() = runTest {
        // Arrange
        coEvery { signInWithEmailUseCase(any(), any()) } returns AuthResult.Success(dummyAuthUser)
        viewModel.signInWithEmail("test@example.com", "password123")
        advanceUntilIdle()
        assertEquals(AuthUiState.Success, viewModel.uiState.value)

        // Act
        viewModel.resetUiState()

        // Assert
        assertEquals(AuthUiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun `given ResetSent state when resetUiState then uiState becomes Idle`() = runTest {
        // Arrange
        coEvery { resetPasswordUseCase(any()) } returns AuthResult.Success(Unit)
        viewModel.resetPassword("user@example.com")
        advanceUntilIdle()
        assertEquals(AuthUiState.ResetSent, viewModel.uiState.value)

        // Act
        viewModel.resetUiState()

        // Assert
        assertEquals(AuthUiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun `given EmailConfirmationSent state when resetUiState then uiState becomes Idle`() = runTest {
        // Arrange
        coEvery { signUpWithEmailUseCase(any(), any()) } returns AuthResult.Error(AuthError.EmailConfirmationRequired)
        viewModel.signUpWithEmail("confirm@example.com", "password123")
        advanceUntilIdle()
        assertEquals(AuthUiState.EmailConfirmationSent, viewModel.uiState.value)

        // Act
        viewModel.resetUiState()

        // Assert
        assertEquals(AuthUiState.Idle, viewModel.uiState.value)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 7 — sessionState (from GetSessionStateUseCase)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given GetSessionStateUseCase emits Loading when sessionState collected then reflects Loading`() = runTest {
        // Arrange: sessionStateFlow starts with Loading (set in setUp)

        // Act & Assert
        viewModel.sessionState.test {
            assertEquals(SessionState.Loading, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given GetSessionStateUseCase emits Unauthenticated when sessionState updates then ViewModel reflects it`() = runTest {
        // Arrange
        viewModel.sessionState.test {
            awaitItem() // consume initial Loading

            // Act: simulate auth session ending
            sessionStateFlow.value = SessionState.Unauthenticated

            // Assert
            assertEquals(SessionState.Unauthenticated, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given GetSessionStateUseCase emits Authenticated when sessionState updates then ViewModel reflects user`() = runTest {
        // Arrange
        val authenticatedState = SessionState.Authenticated(dummyAuthUser)

        viewModel.sessionState.test {
            awaitItem() // consume initial Loading

            // Act
            sessionStateFlow.value = authenticatedState

            // Assert
            val emitted = awaitItem()
            assertTrue(emitted is SessionState.Authenticated)
            assertEquals(dummyAuthUser.id, (emitted as SessionState.Authenticated).user.id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given session transitions Loading to Authenticated when sessionState observed then both states emitted in order`() = runTest {
        // Arrange: start with Loading
        sessionStateFlow.value = SessionState.Loading

        viewModel.sessionState.test {
            assertEquals(SessionState.Loading, awaitItem())

            // Simulate successful sign-in completing in the background
            sessionStateFlow.value = SessionState.Authenticated(dummyAuthUser)
            val nextState = awaitItem()
            assertTrue(nextState is SessionState.Authenticated)

            cancelAndIgnoreRemainingEvents()
        }
    }
}
