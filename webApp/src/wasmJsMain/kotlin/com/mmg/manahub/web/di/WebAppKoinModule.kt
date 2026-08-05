package com.mmg.manahub.web.di

import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.common.DispatcherProvider
import com.mmg.manahub.core.common.KeyValueStore
import com.mmg.manahub.core.common.LocalStorageKeyValueStore
import com.mmg.manahub.core.common.provideCrashReporter
import com.mmg.manahub.core.data.cache.CardStrategyTagsCache
import com.mmg.manahub.core.data.cache.WebCardStrategyTagsCache
import com.mmg.manahub.core.data.network.ScryfallCache
import com.mmg.manahub.core.data.network.ScryfallRequestQueue
import com.mmg.manahub.core.data.remote.CardStrategyTagsRemoteDataSource
import com.mmg.manahub.core.data.remote.CardStrategyTagsRemoteDataSourceContract
import com.mmg.manahub.core.data.remote.FriendRemoteDataSource
import com.mmg.manahub.core.data.remote.FriendshipClient
import com.mmg.manahub.core.data.remote.ScryfallClient
import com.mmg.manahub.core.data.remote.ScryfallRemoteDataSource
import com.mmg.manahub.core.data.remote.UserProfileClient
import com.mmg.manahub.core.data.remote.collection.CollectionRemoteDataSource
import com.mmg.manahub.core.data.remote.collection.SupabaseCollectionDataSource
import com.mmg.manahub.core.data.remote.createManaHubSupabaseClient
import com.mmg.manahub.core.data.remote.decks.DeckRemoteDataSource
import com.mmg.manahub.core.data.remote.decks.SupabaseDeckDataSource
import com.mmg.manahub.core.data.remote.installSupabaseAuthHeaders
import com.mmg.manahub.core.data.remote.trades.OpenForTradeRemoteDataSource
import com.mmg.manahub.core.data.remote.trades.TradesRemoteDataSource
import com.mmg.manahub.core.data.remote.trades.WishlistRemoteDataSource
import com.mmg.manahub.core.data.repository.TradesRepository
import com.mmg.manahub.core.data.repository.WebAuthRepository
import com.mmg.manahub.core.data.repository.WebCardRepository
import com.mmg.manahub.core.data.repository.WebDeckRepository
import com.mmg.manahub.core.data.repository.WebFriendRepository
import com.mmg.manahub.core.data.repository.WebOpenForTradeRepository
import com.mmg.manahub.core.data.repository.WebTradesRepository
import com.mmg.manahub.core.data.repository.WebUserCardRepository
import com.mmg.manahub.core.data.repository.WebUserPreferencesRepository
import com.mmg.manahub.core.data.repository.CardStrategyTagsRepositoryImpl
import com.mmg.manahub.core.data.repository.WebWishlistRepository
import com.mmg.manahub.core.domain.auth.AuthRepository
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.CardStrategyTagsRepository
import com.mmg.manahub.core.domain.repository.DeckRepository
import com.mmg.manahub.core.domain.repository.FriendRepository
import com.mmg.manahub.core.domain.repository.OpenForTradeRepository
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.core.domain.repository.UserPreferencesRepository
import com.mmg.manahub.core.domain.repository.WishlistRepository
import com.mmg.manahub.core.domain.usecase.card.GetSpotlightFeedUseCase
import com.mmg.manahub.feature.auth.domain.usecase.SignInWithEmailUseCase
import com.mmg.manahub.feature.auth.domain.usecase.SignUpWithEmailUseCase
import com.mmg.manahub.feature.trades.domain.usecase.AcceptProposalUseCase
import com.mmg.manahub.feature.trades.domain.usecase.CancelProposalUseCase
import com.mmg.manahub.feature.trades.domain.usecase.DeclineProposalUseCase
import com.mmg.manahub.feature.trades.domain.usecase.GetActiveTradesUseCase
import com.mmg.manahub.feature.trades.domain.usecase.GetTradeHistoryUseCase
import com.mmg.manahub.feature.trades.domain.usecase.GetTradeThreadUseCase
import com.mmg.manahub.feature.trades.domain.usecase.RefreshTradeThreadUseCase
import com.mmg.manahub.feature.trades.domain.usecase.RefreshTradesUseCase
import com.mmg.manahub.feature.trades.domain.usecase.RevokeAcceptanceUseCase
import com.mmg.manahub.web.auth.AuthViewModel
import com.mmg.manahub.web.auth.WebSessionManager
import com.mmg.manahub.web.carddetail.CardDetailViewModel
import com.mmg.manahub.web.collection.CollectionViewModel
import com.mmg.manahub.web.config.WebAppConfig
import com.mmg.manahub.web.deckeditor.DeckEditorViewModel
import com.mmg.manahub.web.decks.DeckListViewModel
import com.mmg.manahub.web.friends.FriendsViewModel
import com.mmg.manahub.web.home.HomeViewModel
import com.mmg.manahub.web.profile.ProfileViewModel
import com.mmg.manahub.web.search.CardSearchViewModel
import com.mmg.manahub.web.settings.SettingsViewModel
import com.mmg.manahub.web.theme.ThemeShowcaseViewModel
import com.mmg.manahub.web.trades.TradeThreadViewModel
import com.mmg.manahub.web.trades.TradesViewModel
import io.github.jan.supabase.SupabaseClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Root Koin module for `:webApp`. W1 registered only the [KeyValueStore] wasmJs actual + the
 * showcase screen's ViewModel. Web roadmap W2a adds the real [SupabaseClient] (guest-only auth
 * for now -- Google OAuth is a separate follow-up) built through the same shared
 * `createManaHubSupabaseClient` factory Android uses (`shared/core-data` commonMain), differing
 * only in [WebSessionManager] (localStorage-backed, not Android Keystore), the wasmJs/js Ktor
 * engine, and `oauthScheme = null` (web uses normal redirect URLs, not a custom URI scheme).
 *
 * W2b adds the `@Named("supabaseKtor")` [HttpClient] (`Js` engine) that
 * [UserProfileClient]/[FriendshipClient] are built on -- the SAME shared
 * `installSupabaseAuthHeaders` plugin (`shared/core-data` commonMain,
 * `remote/SupabaseAuthHeaderPlugin.kt`) Android's `AuthKoinModule.kt` installs on its `OkHttp`
 * engine, so header injection and JSON (de)serialization behavior are byte-identical across
 * platforms; only the transport engine differs. The qualifier name matches Android's for
 * consistency, even though this is currently the only [HttpClient] registered here.
 *
 * W3a adds the [UserPreferencesRepository] binding, backed by [WebUserPreferencesRepository]
 * (`shared/core-data` wasmJsMain) — the first web data-layer repository slice. Bound against the
 * INTERFACE type (`single<UserPreferencesRepository> { ... }`), not the bare concrete class, per
 * this project's documented Koin gotcha: [ThemeShowcaseViewModel] declares its constructor
 * parameter as the interface, so a bare `single { WebUserPreferencesRepository(...) }` would
 * register under the concrete type and fail at runtime with `NoDefinitionFoundException` the first
 * time that graph path resolves.
 *
 * W3b adds the [CardRepository] binding, backed by [WebCardRepository] (`shared/core-data`
 * wasmJsMain) — the second web data-layer repository slice, and the first one that backs a REAL
 * product screen ([com.mmg.manahub.web.search.CardSearchScreen]), not a showcase. [WebCardRepository]
 * delegates to [ScryfallRemoteDataSource], the SAME `commonMain` class Android's `CardRepositoryImpl`
 * uses (shared `ScryfallRequestQueue` rate limiter + `ScryfallCache` TTL/dedup cache), so this block
 * assembles that dependency chain fresh for the web target -- mirroring Android's
 * `NetworkModule`/`SharedDomainUseCaseModule` (Hilt) construction shape one-for-one, just via Koin.
 * Bound against the INTERFACE type for the same reason as [UserPreferencesRepository] above:
 * [CardSearchViewModel] declares its constructor parameter as [CardRepository].
 *
 * W3c adds the [DeckRepository] binding, backed by [WebDeckRepository] (`shared/core-data`
 * wasmJsMain) -- the third web data-layer repository slice, backing
 * [com.mmg.manahub.web.decks.DeckListScreen]. [DeckRemoteDataSource] (`shared/core-data` commonMain,
 * moved out of `:app` this same slice) is bound to its Supabase impl, [SupabaseDeckDataSource] --
 * the SAME class Android's `SyncManager` now uses (moved rather than duplicated). Bound against the
 * INTERFACE type for the same reason as [CardRepository]/[UserPreferencesRepository] above.
 *
 * W3d adds the [UserCardRepository] binding, backed by [WebUserCardRepository] (`shared/core-data`
 * wasmJsMain) -- the fourth web data-layer repository slice (collection), backing
 * [com.mmg.manahub.web.collection.CollectionScreen]. [CollectionRemoteDataSource] (`shared/core-data`
 * commonMain, moved out of `:app` this same slice) is bound to its Supabase impl,
 * [SupabaseCollectionDataSource] -- the SAME class Android's `SyncManager` now uses. Also injects
 * the existing [CardRepository] singleton so [WebUserCardRepository] can resolve joined
 * [com.mmg.manahub.core.model.Card] data for its `UserCardWithCard` reads. Bound against the
 * INTERFACE type for the same reason as every other repository above.
 *
 * W4b adds the [CardDetailViewModel] factory, backing
 * [com.mmg.manahub.web.carddetail.CardDetailScreen] -- the fifth REAL web MVP screen. Takes the
 * `scryfallId` nav arg as a Koin runtime parameter (`params.get()`, resolved via
 * `koinViewModel<CardDetailViewModel>(key = scryfallId) { parametersOf(scryfallId) }` from the
 * screen) rather than a `SavedStateHandle` -- `:webApp` has no `koin-androidx-compose` integration
 * (Android-only), and the CMP nav route object already carries the id directly.
 *
 * W4c adds the [DeckEditorViewModel] factory (same `params.get()` runtime-parameter shape as
 * [CardDetailViewModel], for `deckId`), backing
 * [com.mmg.manahub.web.deckeditor.DeckEditorScreen] -- the sixth REAL web MVP screen, a minimal
 * deck editor. No new repository bindings were needed: [DeckEditorViewModel] reuses the existing
 * [DeckRepository] and [CardRepository] singletons already registered above.
 *
 * W4d adds the [HomeViewModel] factory, backing [com.mmg.manahub.web.home.HomeScreen] -- the
 * seventh and last REAL web MVP screen from the master plan's originally-scoped screen list. No
 * new repository bindings needed either: it reuses the existing [DeckRepository]/[CardRepository]
 * (indirectly, via [UserCardRepository]) singletons above -- a client-side sort+cap over two
 * already-real repository reads, not a new data source.
 *
 * The web scope expansion (Settings -> Profile -> Add Card, approved 2026-08-04) adds the
 * [SettingsViewModel] factory, backing [com.mmg.manahub.web.settings.SettingsScreen] -- again no
 * new repository binding, since [UserPreferencesRepository] was already registered above in W3a.
 * [com.mmg.manahub.web.collection.CollectionViewModel]'s existing binding below now also takes
 * [UserPreferencesRepository] so [com.mmg.manahub.web.collection.CollectionScreen] can read/write
 * [CollectionViewMode][com.mmg.manahub.core.model.CollectionViewMode] directly.
 *
 * The Profile slice of that same expansion adds the [ProfileViewModel] factory, backing
 * [com.mmg.manahub.web.profile.ProfileScreen] -- again no new repository/client binding, since
 * [SupabaseClient] and [UserProfileClient] were already registered above in W2a/W2b.
 *
 * The Add Card slice of that same expansion adds the [GetSpotlightFeedUseCase] single, injected
 * into the existing [CardSearchViewModel] binding -- the first (and so far only) domain-layer use
 * case registered on web, rather than a ViewModel calling [CardRepository] methods directly. It
 * was already `commonMain` and depends only on [CardRepository], already registered above in W3b.
 *
 * The Friends slice (web scope expansion, approved 2026-08-04, second wave after Settings/Profile/
 * Add Card) adds the [FriendRepository] binding, backed by [WebFriendRepository] (`shared/core-data`
 * wasmJsMain) -- the fifth web data-layer repository slice, backing
 * [com.mmg.manahub.web.friends.FriendsScreen]. [FriendRemoteDataSource] (`shared/core-data`
 * commonMain, already shared -- confirmed by direct read before this slice, no move needed) is bound
 * fresh here, on top of the [FriendshipClient] already registered in W2b. Also injects the existing
 * [CardRepository] singleton so [WebFriendRepository] can resolve joined card metadata for
 * `getFriendCollection` (same join-through-another-repository pattern as [WebUserCardRepository]).
 * Bound against the INTERFACE type for the same reason as every other repository above.
 *
 * The Trades slice (web scope expansion, approved 2026-08-04, Friends + Trades wave) adds three
 * repository bindings: [TradesRepository] (backed by [WebTradesRepository], the near-Room-free
 * port of Android's `TradesRepositoryImpl`), [WishlistRepository] (backed by
 * [WebWishlistRepository]), and [OpenForTradeRepository] (backed by [WebOpenForTradeRepository]) --
 * all `shared/core-data` wasmJsMain, all bound against their INTERFACE type for the same reason as
 * every repository above. Also registers the negotiation use cases
 * ([GetActiveTradesUseCase]/[GetTradeHistoryUseCase]/[RefreshTradesUseCase]/[GetTradeThreadUseCase]/
 * [RefreshTradeThreadUseCase]/[AcceptProposalUseCase]/[DeclineProposalUseCase]/
 * [CancelProposalUseCase]/[RevokeAcceptanceUseCase]) as thin singles over the shared, already-
 * `commonMain` use-case classes (`shared/core-data/.../feature/trades/domain/usecase/`) -- the
 * SAME classes Android's Trades feature uses, reused directly rather than reimplemented, per this
 * project's KMP migration rule of never duplicating shared business logic. [TradesViewModel] backs
 * [com.mmg.manahub.web.trades.TradesScreen] (list level); [TradeThreadViewModel] backs
 * [com.mmg.manahub.web.trades.TradeThreadScreen] (negotiation detail, takes `rootProposalId` as a
 * Koin runtime parameter -- same `params.get()` shape as [CardDetailViewModel]/[DeckEditorViewModel]
 * above). Creating a brand-new proposal from scratch (friend + item picker) is an explicit, flagged
 * follow-up -- see `TradesScreen`'s KDoc -- so no such ViewModel/screen is registered yet.
 *
 * The Card Detail completion slice (web roadmap W4b follow-up, 2026-08-05) adds the
 * [CardStrategyTagsRepository] binding, backed by the SAME [CardStrategyTagsRepositoryImpl]
 * (`shared/core-data` commonMain) Android's `CardStrategyTagsKoinModule` uses -- only [CardStrategyTagsCache]
 * differs ([WebCardStrategyTagsCache], `shared/core-data` wasmJsMain: a plain session-scoped
 * in-memory map, since Room has no wasmJs target). This powers [CardDetailViewModel]'s read-only
 * tag DISPLAY (`getStrategyTags`) -- tag EDITING (`CardRepository.updateCardTags`/`unionCardTags`/
 * the `custom_` key CRUD system) remains a documented, deliberate non-goal; [WebCardRepository]'s
 * tag-mutation methods stay as their existing loud `UnsupportedOperationException` stubs.
 */
val webAppKoinModule = module {
    single<KeyValueStore> { LocalStorageKeyValueStore() }

    single<CrashReporter> { provideCrashReporter() }

    single<UserPreferencesRepository> { WebUserPreferencesRepository(keyValueStore = get()) }

    single<SupabaseClient> {
        createManaHubSupabaseClient(
            supabaseUrl = WebAppConfig.SUPABASE_URL,
            supabaseKey = WebAppConfig.SUPABASE_ANON_KEY,
            sessionManager = WebSessionManager(),
            httpEngine = Js.create(),
            oauthScheme = null,
            crashReporter = get(),
        )
    }

    single<HttpClient>(named("supabaseKtor")) {
        HttpClient(Js) {
            installSupabaseAuthHeaders(
                supabaseClient = get(),
                anonKey = WebAppConfig.SUPABASE_ANON_KEY,
            )
        }
    }

    single {
        UserProfileClient(
            httpClient = get(named("supabaseKtor")),
            baseUrl = "${WebAppConfig.SUPABASE_URL}/rest/v1/",
        )
    }

    // ── Auth stack (email/password sign-in, sign-up, password reset -- 2026-08-05) ──────────────
    single<AuthRepository> { WebAuthRepository(supabaseClient = get(), userProfileClient = get()) }
    single { SignInWithEmailUseCase(repository = get()) }
    single { SignUpWithEmailUseCase(repository = get()) }

    single {
        FriendshipClient(
            httpClient = get(named("supabaseKtor")),
            baseUrl = "${WebAppConfig.SUPABASE_URL}/rest/v1/",
        )
    }

    // ── Scryfall stack (W3b) — mirrors Android's NetworkModule/SharedDomainUseCaseModule shape ──
    single<HttpClient>(named("scryfall")) {
        HttpClient(Js) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; coerceInputValues = true })
            }
            expectSuccess = true
        }
    }
    single {
        ScryfallClient(httpClient = get(named("scryfall")), baseUrl = "https://api.scryfall.com/")
    }
    single { ScryfallRequestQueue(crashReporter = get()) }
    single { ScryfallCache() }
    single { DispatcherProvider() }
    single {
        ScryfallRemoteDataSource(
            api = get(),
            requestQueue = get(),
            cache = get(),
            dispatcherProvider = get(),
            crashReporter = get(),
        )
    }
    single<CardRepository> { WebCardRepository(remote = get()) }
    single { GetSpotlightFeedUseCase(cardRepository = get()) }

    // ── Decks stack (W3c) ─────────────────────────────────────────────────────────────────────
    single<DeckRemoteDataSource> { SupabaseDeckDataSource(supabaseClient = get()) }
    single<DeckRepository> { WebDeckRepository(remote = get(), supabaseClient = get()) }

    // ── Collection stack (W3d) ────────────────────────────────────────────────────────────────
    single<CollectionRemoteDataSource> { SupabaseCollectionDataSource(supabaseClient = get()) }
    single<UserCardRepository> {
        WebUserCardRepository(remote = get(), cardRepository = get(), supabaseClient = get())
    }

    // ── Friends stack (web scope expansion, Friends slice) ───────────────────────────────────────
    single { FriendRemoteDataSource(client = get()) }
    single<FriendRepository> {
        WebFriendRepository(remote = get(), cardRepository = get(), supabaseClient = get(), crashReporter = get())
    }

    // ── Trades stack (web scope expansion, Trades slice) ─────────────────────────────────────────
    single { TradesRemoteDataSource(supabaseClient = get()) }
    single<TradesRepository> { WebTradesRepository(remote = get(), cardRepository = get(), supabaseClient = get()) }
    single { WishlistRemoteDataSource(supabaseClient = get()) }
    single<WishlistRepository> { WebWishlistRepository(remote = get(), cardRepository = get(), supabaseClient = get()) }
    single { OpenForTradeRemoteDataSource(supabaseClient = get()) }
    single<OpenForTradeRepository> {
        WebOpenForTradeRepository(remote = get(), cardRepository = get(), supabaseClient = get())
    }
    // ── Card strategy tags stack (Card Detail tag DISPLAY, web roadmap W4b follow-up) ──────────
    single<CardStrategyTagsCache> { WebCardStrategyTagsCache() }
    single<CardStrategyTagsRemoteDataSourceContract> {
        CardStrategyTagsRemoteDataSource(supabaseClient = get(), dispatcherProvider = get())
    }
    single<CardStrategyTagsRepository> {
        CardStrategyTagsRepositoryImpl(
            remote = get(),
            cache = get(),
            crashReporter = get(),
            dispatcherProvider = get(),
            now = { Clock.System.now().toEpochMilliseconds() },
        )
    }

    single { GetActiveTradesUseCase(repo = get()) }
    single { GetTradeHistoryUseCase(repo = get()) }
    single { RefreshTradesUseCase(repo = get()) }
    single { GetTradeThreadUseCase(repo = get()) }
    single { RefreshTradeThreadUseCase(repo = get()) }
    single { AcceptProposalUseCase(repo = get()) }
    single { DeclineProposalUseCase(repo = get()) }
    single { CancelProposalUseCase(repo = get()) }
    single { RevokeAcceptanceUseCase(repo = get()) }

    viewModel { ThemeShowcaseViewModel(keyValueStore = get(), userPreferencesRepository = get()) }
    viewModel {
        AuthViewModel(
            authRepository = get(),
            signInWithEmailUseCase = get(),
            signUpWithEmailUseCase = get(),
        )
    }
    viewModel {
        CardSearchViewModel(
            cardRepository = get(),
            userCardRepository = get(),
            getSpotlightFeed = get(),
            crashReporter = get(),
        )
    }
    viewModel { DeckListViewModel(deckRepository = get(), crashReporter = get()) }
    viewModel { CollectionViewModel(userCardRepository = get(), userPreferencesRepository = get()) }
    viewModel { params ->
        CardDetailViewModel(
            scryfallId = params.get(),
            cardRepository = get(),
            cardStrategyTagsRepository = get(),
        )
    }
    viewModel { params ->
        DeckEditorViewModel(
            deckId = params.get(),
            deckRepository = get(),
            cardRepository = get(),
            crashReporter = get(),
        )
    }
    viewModel { HomeViewModel(deckRepository = get(), userCardRepository = get()) }
    viewModel { SettingsViewModel(userPreferencesRepository = get()) }
    viewModel {
        ProfileViewModel(supabaseClient = get(), userProfileClient = get(), crashReporter = get())
    }
    viewModel {
        FriendsViewModel(friendRepository = get(), supabaseClient = get(), crashReporter = get())
    }
    viewModel {
        TradesViewModel(
            supabaseClient = get(),
            getActiveTrades = get(),
            getTradeHistory = get(),
            refreshTrades = get(),
            tradesRepository = get(),
            wishlistRepository = get(),
            openForTradeRepository = get(),
            friendshipClient = get(),
            crashReporter = get(),
        )
    }
    viewModel { params ->
        TradeThreadViewModel(
            rootProposalId = params.get(),
            supabaseClient = get(),
            getThread = get(),
            refreshTradeThread = get(),
            acceptProposal = get(),
            declineProposal = get(),
            cancelProposal = get(),
            revokeAcceptance = get(),
            friendshipClient = get(),
            crashReporter = get(),
        )
    }
}
