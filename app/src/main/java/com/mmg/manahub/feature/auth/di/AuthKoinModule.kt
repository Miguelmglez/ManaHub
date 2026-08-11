package com.mmg.manahub.feature.auth.di

import com.mmg.manahub.BuildConfig
import com.mmg.manahub.core.data.remote.UserProfileClient
import com.mmg.manahub.core.data.remote.installSupabaseAuthHeaders
import com.mmg.manahub.feature.auth.data.remote.UserProfileDataSource
import com.mmg.manahub.feature.auth.domain.usecase.ConfirmEmailUpdateUseCase
import com.mmg.manahub.feature.auth.domain.usecase.ConfirmPasswordResetUseCase
import com.mmg.manahub.feature.auth.domain.usecase.DeleteAccountUseCase
import com.mmg.manahub.feature.auth.domain.usecase.GetSessionStateUseCase
import com.mmg.manahub.feature.auth.domain.usecase.LinkGoogleIdentityNativeUseCase
import com.mmg.manahub.feature.auth.domain.usecase.LinkGoogleIdentityUseCase
import com.mmg.manahub.feature.auth.domain.usecase.RequestReauthenticationUseCase
import com.mmg.manahub.feature.auth.domain.usecase.ResendConfirmationEmailUseCase
import com.mmg.manahub.feature.auth.domain.usecase.ResetPasswordUseCase
import com.mmg.manahub.feature.auth.domain.usecase.SignInWithEmailUseCase
import com.mmg.manahub.feature.auth.domain.usecase.SignInWithGoogleUseCase
import com.mmg.manahub.feature.auth.domain.usecase.SignOutUseCase
import com.mmg.manahub.feature.auth.domain.usecase.SignUpWithEmailUseCase
import com.mmg.manahub.feature.auth.domain.usecase.SignUpWithGoogleUseCase
import com.mmg.manahub.feature.auth.domain.usecase.UnlinkIdentityUseCase
import com.mmg.manahub.feature.auth.domain.usecase.UpdateEmailUseCase
import com.mmg.manahub.feature.auth.domain.usecase.UpdateNicknameUseCase
import com.mmg.manahub.feature.auth.domain.usecase.UpdatePasswordUseCase
import com.mmg.manahub.feature.auth.presentation.AccountManagementViewModel
import com.mmg.manahub.feature.auth.presentation.AuthViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * KMP migration — Phase 1 Hilt→Koin cutover. The Auth feature is the latest "Koin island":
 * [AuthViewModel] is resolved by Koin (`koinViewModel()`) while every other still-Hilt feature is
 * unaffected. This continues the incremental, per-feature cutover proven by Spike D (Settings).
 *
 * ## Self-contained module (no bridge args)
 * Unlike the bridge-style island modules, [authKoinModule] takes NO constructor arguments. Every
 * dependency [AuthViewModel] needs is already resolvable from modules loaded earlier in `startKoin`:
 * - The eighteen auth use cases (ten original + six added by the account-management data-layer
 *   slice — [ResendConfirmationEmailUseCase]/[RequestReauthenticationUseCase]/[UpdateEmailUseCase]/
 *   [UpdatePasswordUseCase]/[UnlinkIdentityUseCase]/[LinkGoogleIdentityNativeUseCase] — plus
 *   [ConfirmPasswordResetUseCase] added by the Phase 4b UI slice and [ConfirmEmailUpdateUseCase]
 *   added by the "Change email" reauth-gate simplification — see its KDoc) are stateless
 *   thin wrappers over [AuthRepository] (and, for [DeleteAccountUseCase], `PushTokenRepository`). They
 *   are not registered as `single` in any other loaded module, so they are declared here as
 *   `single { }` and constructed directly by Koin. The Koin-built copies are independent of, but
 *   equivalent to, the Hilt-built copies still used by the Hilt graph — exactly the pattern proven
 *   for the Trades use cases.
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
 * ## KMP migration — Hilt→Koin cutover batch 5
 * The Hilt `feature.auth.di.AuthModule` (which used to `@Binds AuthRepository` and `@Provides` the
 * `@Named("supabase")`/`@Named("supabaseKtor")` HTTP clients + [UserProfileClient]/
 * [UserProfileDataSource]) was DELETED. `AuthRepository` is now natively Koin-built in
 * `app.di.coreBridgeKoinModule` (shared across nearly every island); the two qualified HTTP clients
 * and [UserProfileClient]/[UserProfileDataSource] are natively Koin-built HERE:
 * - `@Named("supabase")` [OkHttpClient] — the auth interceptor derives its [io.github.jan.supabase.auth.Auth]
 *   directly from the already-bridged [SupabaseClient] single (`get<SupabaseClient>().auth`) rather than
 *   registering a separate `Auth` single — no other consumer needs one.
 * - `@Named("supabaseKtor")` [HttpClient] — built on the OkHttp client above. `friendsKoinModule`'s
 *   `FriendshipClient` used to receive this as a `ManaHubApp` forward-bridge field
 *   (`supabaseKtorHttpClient`); it now resolves it cross-module via `get(named("supabaseKtor"))`
 *   instead, and the dead `ManaHubApp` field was removed.
 * - [UserProfileClient] / [UserProfileDataSource] — both were ALREADY plain Kotlin classes (no
 *   `@Inject`, KMP-ready). [UserProfileDataSource] is ALSO consumed by `settingsKoinModule`
 *   (`SettingsViewModel`) — it used to receive it as a `ManaHubApp` forward-bridge field; that field
 *   and `settingsKoinModule`'s own `single { userProfileDataSource }` registration were removed
 *   (the promote-then-shrink ritual) so it resolves the ONE native single here via `get()` instead of
 *   throwing `DefinitionOverrideException` from a duplicate registration.
 *
 * ## KMP web roadmap W2b — shared Ktor auth-header plugin
 * `@Named("supabaseKtor")` used to build its OWN OkHttp interceptor to inject
 * `apikey`/`Authorization`/`Content-Type`/`Accept` headers, duplicating logic that had to be
 * hand-copied for the web (`Js`) engine. That header-injection logic is now
 * [installSupabaseAuthHeaders] (`shared/core-data` commonMain,
 * `remote/SupabaseAuthHeaderPlugin.kt`) — a plain Ktor `HttpClientConfig` extension installed
 * identically on Android (`OkHttp` engine, this file) and Web (`Js` engine,
 * `webApp`'s `WebAppKoinModule.kt`). **The `@Named("supabase")` [OkHttpClient] single below is
 * UNCHANGED and keeps its OWN auth interceptor** — `AuthRepositoryImpl` makes two RAW OkHttp calls
 * (`delete-current-user`/`set-google-account-password` Edge Functions) that bypass Ktor entirely
 * and rely on that interceptor for header injection; removing it would break those calls silently.
 * `@Named("supabaseKtor")`'s own OkHttp engine is now a SEPARATE, bare `OkHttpClient` (logging
 * only, no auth interceptor) so header injection happens exactly once, at the Ktor layer, matching
 * web — not duplicated across both an OkHttp interceptor AND the new Ktor plugin.
 *
 * @return a Koin [Module] that provides the ten auth use cases, the two qualified HTTP clients,
 *   [UserProfileClient], [UserProfileDataSource] and the [AuthViewModel] factory.
 */
fun authKoinModule(): Module = module {
    /**
     * Provides a dedicated [OkHttpClient] for Supabase PostgREST calls.
     *
     * Each request is automatically decorated with:
     * - `apikey` header (Supabase anon key)
     * - `Authorization: Bearer <accessToken>` header (current session token,
     *   or anon key as fallback when unauthenticated)
     * - `Content-Type: application/json` and `Accept: application/json`
     *
     * [io.github.jan.supabase.auth.Auth.currentSessionOrNull] reads in-memory state from
     * `Auth.sessionStatus` — it is a plain (non-suspend) function, so no blocking or coroutine
     * bridge is needed here.
     *
     * **Kept separate from `@Named("supabaseKtor")`'s own engine (see class KDoc above)**: this
     * client is consumed directly (not through Ktor) by `AuthRepositoryImpl` for two raw Edge
     * Function calls, so its interceptor-based header injection must stay exactly as-is.
     */
    single(named("supabase")) {
        val supabaseAuth = get<SupabaseClient>().auth
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val accessToken = supabaseAuth.currentSessionOrNull()?.accessToken
                chain.proceed(
                    chain.request().newBuilder()
                        .header("apikey", BuildConfig.SUPABASE_ANON_KEY)
                        .header(
                            "Authorization",
                            "Bearer ${accessToken ?: BuildConfig.SUPABASE_ANON_KEY}",
                        )
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json")
                        .build()
                )
            }
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = if (BuildConfig.DEBUG) {
                        HttpLoggingInterceptor.Level.BODY
                    } else {
                        HttpLoggingInterceptor.Level.NONE
                    }
                }
            )
            .build()
    }

    /**
     * Provides a Ktor [HttpClient] for [UserProfileClient]/[FriendshipClient] PostgREST calls.
     *
     * The OkHttp engine backing this client is bare (logging only) — auth-header injection
     * (`apikey`/`Authorization`/`Content-Type`/`Accept`) happens via the shared
     * [installSupabaseAuthHeaders] Ktor plugin instead, the SAME plugin the web target installs on
     * its `Js` engine, so header behavior is byte-identical across platforms. `encodeDefaults =
     * true` (set inside the shared plugin) mirrors the old Gson `serializeNulls()` behavior so
     * that fields with default values (e.g. `pLimit = 50`) are always serialized.
     */
    single(named("supabaseKtor")) {
        val loggingOnlyOkHttpClient = OkHttpClient.Builder()
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = if (BuildConfig.DEBUG) {
                        HttpLoggingInterceptor.Level.BODY
                    } else {
                        HttpLoggingInterceptor.Level.NONE
                    }
                }
            )
            .build()
        HttpClient(OkHttp) {
            engine {
                preconfigured = loggingOnlyOkHttpClient
            }
            installSupabaseAuthHeaders(
                supabaseClient = get<SupabaseClient>(),
                anonKey = BuildConfig.SUPABASE_ANON_KEY,
            )
        }
    }

    // ── UserProfileClient / UserProfileDataSource: natively Koin-constructed (KMP migration batch 5;
    //    Hilt `AuthModule` deleted). Shared with settingsKoinModule (SettingsViewModel) — registered
    //    exactly once here, resolved elsewhere via get(). CrashReporter comes from
    //    coreBridgeKoinModule. ──
    single {
        UserProfileClient(
            httpClient = get(named("supabaseKtor")),
            baseUrl = "${BuildConfig.SUPABASE_URL}/rest/v1/",
        )
    }
    single {
        UserProfileDataSource(
            client = get(),
            ioDispatcher = Dispatchers.IO,
            crashReporter = get(),
        )
    }

    // ── The sixteen stateless auth use cases. Each wraps AuthRepository (bridged in
    //    coreBridgeKoinModule, resolved via get()); DeleteAccountUseCase also wraps
    //    PushTokenRepository (single in settingsKoinModule). None are registered in any other
    //    loaded module → no DefinitionOverride. ──
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
    // Account-management data-layer slice (Phase 1): the six new use cases.
    single { ResendConfirmationEmailUseCase(get()) }
    single { RequestReauthenticationUseCase(get()) }
    single { UpdateEmailUseCase(get()) }
    single { UpdatePasswordUseCase(get()) }
    single { UnlinkIdentityUseCase(get()) }
    single { LinkGoogleIdentityNativeUseCase(get()) }
    // Phase 4b (account management): "forgot password" recovery-link completion, no reauth code.
    single { ConfirmPasswordResetUseCase(get()) }
    // "Change email" reauth-gate simplification: Secure Email Change already double-confirms, so
    // this use case skips the reauthentication-code gate — see its KDoc.
    single { ConfirmEmailUpdateUseCase(get()) }

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
            resendConfirmationEmailUseCase = get(),
            requestReauthenticationUseCase = get(),
            updateEmailUseCase = get(),
            updatePasswordUseCase = get(),
            unlinkIdentityUseCase = get(),
            linkGoogleIdentityNativeUseCase = get(),
            confirmPasswordResetUseCase = get(),
            confirmEmailUpdateUseCase = get(),
            analyticsHelper = get(),
            appContext = androidContext(),
        )
    }

    // ── AccountManagementScreen's screen-local ViewModel (Phase 4b). Owns the "share my profile"
    //    link lookup (mirrors ProfileViewModel.fetchShareLink — same ShareInviteUseCase, cross-module
    //    from friendsKoinModule) and the resend-confirmation-email cooldown timer. Everything else on
    //    that screen (resend/unlink/link/sign-out/delete) goes straight through the entry-scoped
    //    AuthViewModel, matching the existing Profile/LoginSheet pattern. ──
    viewModel {
        AccountManagementViewModel(
            authRepository = get(),
            shareInviteUseCase = get(),
        )
    }
}
