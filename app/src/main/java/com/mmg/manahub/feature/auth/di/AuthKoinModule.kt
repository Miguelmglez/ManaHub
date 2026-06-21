package com.mmg.manahub.feature.auth.di

import com.mmg.manahub.feature.auth.domain.usecase.DeleteAccountUseCase
import com.mmg.manahub.feature.auth.domain.usecase.GetSessionStateUseCase
import com.mmg.manahub.feature.auth.domain.usecase.LinkGoogleIdentityUseCase
import com.mmg.manahub.feature.auth.domain.usecase.ResetPasswordUseCase
import com.mmg.manahub.feature.auth.domain.usecase.SignInWithEmailUseCase
import com.mmg.manahub.feature.auth.domain.usecase.SignInWithGoogleUseCase
import com.mmg.manahub.feature.auth.domain.usecase.SignOutUseCase
import com.mmg.manahub.feature.auth.domain.usecase.SignUpWithEmailUseCase
import com.mmg.manahub.feature.auth.domain.usecase.SignUpWithGoogleUseCase
import com.mmg.manahub.feature.auth.domain.usecase.UpdateNicknameUseCase
import com.mmg.manahub.feature.auth.presentation.AuthViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * KMP migration — Phase 1 Hilt→Koin cutover. The Auth feature is the latest "Koin island":
 * [AuthViewModel] is resolved by Koin (`koinViewModel()`) while every other still-Hilt feature is
 * unaffected. This continues the incremental, per-feature cutover proven by Spike D (Settings).
 *
 * ## Self-contained module (no bridge args)
 * Unlike the bridge-style island modules, [authKoinModule] takes NO constructor arguments. Every
 * dependency [AuthViewModel] needs is already resolvable from modules loaded earlier in `startKoin`:
 * - The ten auth use cases are stateless thin wrappers over [AuthRepository] (and, for
 *   [DeleteAccountUseCase], `PushTokenRepository`). They are not registered as `single` in any other
 *   loaded module, so they are declared here as `single { }` and constructed directly by Koin. The
 *   Koin-built copies are independent of, but equivalent to, the Hilt-built copies still used by the
 *   Hilt graph — exactly the pattern proven for the Trades use cases.
 * - `AuthRepository` and `AnalyticsHelper` are SHARED with other islands → bridged once in
 *   `coreBridgeKoinModule`, resolved here via `get()` (never re-registered — registering the same type
 *   in two loaded modules would throw `DefinitionOverrideException`).
 * - `PushTokenRepository` is already registered as a `single` by `settingsKoinModule`, resolved here
 *   via `get()`.
 * - The application [android.content.Context] is supplied by Koin via `androidContext()`.
 *
 * ## Cross-cutting consumption (1:1 swap)
 * [AuthViewModel] is not bound to a single screen: it is consumed as a default composable parameter
 * inside several screens — Profile (`ProfileScreen`), Trades (`TradesScreen`) and
 * `CreateTradeProposalScreen` — each obtaining an entry-scoped instance via the former
 * `hiltViewModel()` default. No Activity-scoped or nav-graph-scoped shared instance ever existed, so
 * swapping each default to `koinViewModel()` is an exact 1:1 behavioural equivalent.
 *
 * @return a Koin [Module] that provides the ten auth use cases and the [AuthViewModel] factory.
 */
fun authKoinModule(): Module = module {
    // ── The ten stateless auth use cases. Each wraps AuthRepository (bridged in coreBridgeKoinModule,
    //    resolved via get()); DeleteAccountUseCase also wraps PushTokenRepository (single in
    //    settingsKoinModule). None are registered in any other loaded module → no DefinitionOverride. ──
    single { SignInWithEmailUseCase(get()) }
    single { SignUpWithEmailUseCase(get()) }
    single { SignInWithGoogleUseCase(get()) }
    single { SignUpWithGoogleUseCase(get()) }
    single { LinkGoogleIdentityUseCase(get()) }
    single { SignOutUseCase(get()) }
    single { GetSessionStateUseCase(get()) }
    single { ResetPasswordUseCase(get()) }
    single { UpdateNicknameUseCase(get()) }
    single { DeleteAccountUseCase(get(), get()) }

    // ── The Koin island: AuthViewModel is now resolved by Koin, not Hilt. ──
    viewModel {
        AuthViewModel(
            signInWithEmailUseCase = get(),
            signUpWithEmailUseCase = get(),
            signInWithGoogleUseCase = get(),
            signUpWithGoogleUseCase = get(),
            linkGoogleIdentityUseCase = get(),
            signOutUseCase = get(),
            getSessionState = get(),
            resetPasswordUseCase = get(),
            deleteAccountUseCase = get(),
            updateNicknameUseCase = get(),
            analyticsHelper = get(),
            appContext = androidContext(),
        )
    }
}
