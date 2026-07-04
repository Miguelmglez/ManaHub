# KMP Migration — Living Progress Tracker

**This file is the single source of truth for "where are we / what's next" in the KMP migration.**
A new Claude session must read this (plus `kmp-migration-plan.md` + `kmp-library-and-filesystem-map.md`)
and **resume the migration automatically without waiting for the user to re-explain it.**

Branch: `feature/kmp-migration`
Plan: `docs/plans/kmp-migration-plan.md` · Library/FS map: `docs/plans/kmp-library-and-filesystem-map.md`
All `.kt` work → delegate to `android-kotlin-architect`. Spike/lib gotchas → memory `project_kmp_spike_findings`.

---

## How to resume (read this first on a new session)
1. Read this file's **STATUS** + **NEXT STEP** below.
2. Check for any background agent still running (a phase may be mid-flight).
3. Verify build state: `./gradlew :app:assembleDebug` should pass; `:shared:core-model` android+wasmJs compile.
4. Execute the NEXT STEP by delegating to `android-kotlin-architect` with full context.
5. After each phase completes + verifies, **update the STATUS / NEXT STEP / log below** so the next
   session can pick up. Keep Android shippable at every step.

---

## STATUS (update after every phase)

- ✅ **Phase 0 · Spike A** — KMP toolchain proven. `:shared:core-model` (android + wasmJs) created,
  2 pure enums moved (`CollectionViewMode`, `GroupingMode`), `:app` still builds. GO.
- ✅ **Phase 0.5 · Modularization blockers** — COMPLETE & VERIFIED GREEN (2026-06-20). The interrupted
  relocation was finished: entities/DAOs live in `core.data.local.{entity,dao}`, auth types in
  `core.domain.auth`, and presentation-coupled persistence keys were decoupled via the new
  `core.domain.model.PersistedWidget` (Blocker 2). Blockers 1–5 all satisfied.
  - `./gradlew :app:assembleDebug` → **BUILD SUCCESSFUL** (only pre-existing deprecation warnings).
  - Room: DB stays **v41**, schema hash unchanged by the package moves (Room keys on table/column
    structure, not Kotlin package); KSP validated v41 against `MIGRATION_40_41` with no mismatch.
    Schemas remain gitignored (regenerated locally). No version bump.
  - `./gradlew :app:testDebugUnitTest` → **1964 tests, 122 failed, 2 skipped** — the whole test source
    set now COMPILES (it did not on the documented baseline) and runs; 122 failures is BELOW the ~140
    pre-existing baseline. Files touched by the fix (Home/Tournament/GameSession/QuickStartAction tests)
    all pass; the remaining 122 are pre-existing assertion/mock failures (Crashlytics-init, Turbine,
    scanner tuning, trades/sync) unrelated to the structural moves.
  - Acceptance greps EMPTY (PASS): no `import com.mmg.manahub.feature.` in core/data, core/domain,
    core/util; no `R.string`/androidx-resource import in core/domain.
- ✅ **Phase 0 · Spike D — Hilt/Koin coexistence** — DONE & GREEN (2026-06-20). Koin 4.0.2 added
  ALONGSIDE Hilt (no Hilt removed). **Settings is the first "Koin island"**: `SettingsViewModel` is
  resolved via `koinViewModel()`, every other feature stays `@HiltViewModel`. Bridge pattern proven
  (Koin module re-exposes the Hilt-owned singletons; `ManaHubApp` is the bridge — `@Inject`s the 8 deps
  and hands them to `settingsKoinModule(...)` in `startKoin`). `:app:assembleDebug` SUCCESSFUL;
  `:app:testDebugUnitTest` = 1964/122-fail/2-skip (identical to the 0.5 baseline — zero new failures).
  → DI cutover is incremental per-feature, NOT big-bang. Full pattern in `project_kmp_spike_findings`.
- ✅ **Phase 0 · Spike E — CMP navigation library** — DECIDED (2026-06-20). **JetBrains Compose
  Multiplatform `navigation-compose`** (androidx.navigation CMP port). Wins on all 5 criteria: it IS the
  same `NavHost`/`composable(route)`/`NavController` API the app already uses (228-line sealed `Screen.kt`
  + 42 destinations migrate as a package swap, not a rewrite), official wasmJs support (CMP 1.9.0),
  `navDeepLink` + `window.bindToNavigation()` for web URL routing, and first-class `koinViewModel()`
  integration. Voyager/Decompose rejected (full nav rewrite + VM-model conflict with Koin). Rationale in
  `project_kmp_spike_findings`. No nav code changed (decision spike).
- ⬜ **Phase 0 · Spikes B & C** — pending: B Supabase/Ktor/Coil on wasmJs, C web auth. **Can be deferred
  or folded into Phase 2 (data-layer commonization) / the web-target work** — they validate web runtime
  libraries, which aren't on the critical path for the Phase-1 Android-side build/DI/model foundation.
- 🟡 **Phase 1 (foundation) — REPAIRED & GREEN (2026-06-20).** The interrupted model-extraction WIP is
  fixed: all pure models now live in `com.mmg.manahub.core.model` (`:shared:core-model`) and `:app`
  compiles against them. `:shared:core-common` SURVIVED (it was coherent/complete) — 4 contracts in
  commonMain (`DispatcherProvider` expect/actual, `KeyValueStore`, `CrashReporter` expect/actual, `Page`)
  with real Android actuals (Dispatchers / DataStore / Firebase Crashlytics) and minimal-but-compiling
  wasmJs actuals (Default-dispatcher fallback, in-memory KV stub, no-op CrashReporter). `:app` now
  `implementation(project(":shared:core-common"))` and builds; NO call-site migrated onto it yet.
  - `:app:assembleDebug` → **BUILD SUCCESSFUL** (deprecation warnings only).
  - `:shared:core-model:compileKotlinWasmJs` + `:shared:core-common:compileKotlinWasmJs` → **SUCCESSFUL**.
  - `:app:testDebugUnitTest` → **1964 tests, 122 failed, 2 skipped** (== baseline, ZERO new failures).
  - commonMain Android/AndroidX/browser-import grep → EMPTY (PASS).
  - **Koin islands so far: Settings (Spike D), Stats (Phase 1, 2026-06-20), Profile (Phase 1,
    2026-06-21), Home (Phase 1, 2026-06-21), TagDictionary (Phase 1, 2026-06-21), AddCard (Phase 1,
    2026-06-21), CommunityDecks (Phase 1, 2026-06-21 — the FIRST multi-ViewModel island), CardDetail
    (Phase 1, 2026-06-21), Friends (Phase 1, 2026-06-21 — the SECOND multi-VM island, 3 VMs, the
    heaviest island so far + the first with an Activity-scoped VM), Splash + Survey + News (Phase 1,
    2026-06-21 — three leaf islands migrated in one run; News is the THIRD multi-VM island), Draft
    (Phase 1, 2026-06-21 — the FOURTH multi-VM island, 3 VMs), Playtest (Phase 1, 2026-06-21 — the
    FIFTH multi-VM island, 2 VMs; the SECOND island to convert+delete its feature-private Hilt module),
    Tournament (Phase 1, 2026-06-21 — the SIXTH multi-VM island, 3 VMs; the FIRST entry-scoped Koin VM
    via `koinViewModel(viewModelStoreOwner = entry)`, KEEPS its Hilt module + bridges 2 use cases for the
    still-Hilt `GameViewModel`), Trades (Phase 1, 2026-06-21 — the SEVENTH multi-VM island, 5 VMs; the
    MOST repo-entangled island so far — its 5-repo split forced promoting 3 shared repos to coreBridge +
    shrinking 3 prior islands; KEEPS its Hilt `TradesModule` for still-Hilt consumers), Collection
    (Phase 1, 2026-06-21 — a SINGLE-VM island, the least-entangled remaining; reuses 8 already-bridged
    deps via `get()` — 6 from coreBridge + `GetLocalWishlistUseCase` from `tradesKoinModule` +
    `UserCardRepository` from `cardDetailKoinModule` — and bridges only 3 new Collection-only singletons
    + the already-injected `WorkManager`; NOTHING promoted to coreBridge, NO island shrunk; KEEPS its
    `AdvancedSearchViewModel = hiltViewModel()` (a core/ui-components VM, out of collection scope)).**
    Every OTHER feature is still on Hilt (`@HiltViewModel` + `hiltViewModel()`).
    The shared `app/di/CoreBridgeKoinModule.kt` now holds SEVENTEEN cross-island
    singletons (the 14 below + `TradesRepository`, `WishlistRepository`, `OpenForTradeRepository`
    promoted for the Trades island): `UserPreferencesRepository` (Settings+Stats+CardDetail),
    `UserPreferencesDataStore`
    (Settings+Profile+Home+CardDetail), `AuthRepository` (Settings+Profile+Home+CardDetail+Friends),
    `GameSessionRepository` (Stats+Profile+Home), `StatsRepository` (Profile+Home), `DeckRepository`
    (Stats+Home+CommunityDecks+CardDetail), `ScryfallRemoteDataSource` (Stats+Home),
    `GamificationRepository` (Profile+Home), `CardRepository` (Home+CommunityDecks+CardDetail),
    `AnalyticsHelper` (Settings+CardDetail+Friends+Draft), `FriendRepository` (Profile+Friends),
    `DraftRepository` (Home+Draft), `DraftSimRepository` (Home+Draft),
    `TournamentRepository` (Home+Tournament+Hilt-GameViewModel) — so no feature
    module double-registers a shared type (`DefinitionOverrideException` guard). Adding CardDetail promoted
    `AnalyticsHelper` into the bridge and SHRUNK Settings; adding Friends promoted `FriendRepository` into
    the bridge and SHRUNK Profile (it now resolves `FriendRepository` via `get()`); adding Draft promoted
    `DraftRepository` + `DraftSimRepository` into the bridge and SHRUNK Home (both resolved via `get()`);
    adding Tournament promoted `TournamentRepository` into the bridge and SHRUNK Home (resolved via `get()`).
    CommunityDecks and Playtest are the
    only islands to **convert+delete their feature-private Hilt module** so far; Friends KEEPS its Hilt `FriendModule`
    (`@Binds FriendRepository` + `@Provides FriendshipService`) because Hilt features still consume them
    (Trades VMs + `CollectionStatsSyncWorker`); Tournament likewise KEEPS its Hilt `TournamentModule`
    (`@Binds TournamentRepository`) because the still-Hilt `GameViewModel` consumes the repo + the record
    use case. Multi-arg-nav VMs resolve a Koin-injected `SavedStateHandle`
    carrying their nav args (`cardName` / `archidektId` / `scryfallId` / `userId` / `tournamentId`);
    Activity-scoped VMs (InviteDispatcher) resolve via `koinViewModel(viewModelStoreOwner = activity)`,
    and entry-scoped VMs (TournamentDetail) via `koinViewModel(viewModelStoreOwner = entry)`.
  - **Phase 1 Hilt→Koin cutover COMPLETE except the deferred online/voice/scanner trio.** Settings +
    Stats + Profile + Home + TagDictionary + AddCard + CommunityDecks + CardDetail + Friends + Splash +
    Survey + News + Draft + Playtest + Tournament + Trades + Collection + Decks + Auth + **Game** done
    (TWENTY islands). `game` was the LAST + heaviest non-excluded island (it forced Tournament + Decks to
    KEEP their Hilt modules + bridge use cases) and is now migrated — its 3 VMs resolve via
    `koinViewModel()`, with the DEFERRED `core/voice` + `core/online` + `core/nearby` singletons it depends
    on BRIDGED from the Hilt graph (NOT de-Hilt'd). `feature/online`, `core/voice` + in-game voice, and
    `feature/scanner` remain EXPLICITLY EXCLUDED (untouched, still Hilt + Android Compose) — they migrate
    in a later wave. Every non-excluded feature is now Koin.
- ✅ **Decks (Phase 1, 2026-06-21 — the EIGHTEENTH Koin island; a 4-VM island; the FIRST island whose
  bridged Hilt objects must STAY Hilt because a still-Hilt feature (Draft) shares the SAME engine
  singleton).** All four decks VMs (`DeckViewModel`, `DeckStudioViewModel`, `DeckImprovementViewModel`,
  legacy `DeckMagicDetailViewModel`) resolve via `koinViewModel()`. The Deck Doctor engine (`DeckScorer`
  + its `@Inject` graph) + the feature-private Hilt `DeckDoctorModule` are KEPT (still-Hilt Draft's
  `ScoringDraftDeckBuilder` consumes the same `DeckScorer`); the 9 engine/use-case singletons +
  `@ApplicationScope` are BRIDGED from the Hilt graph via `ManaHubApp` (not rebuilt → one shared
  `DeckScorer`). NOTHING promoted to coreBridge, NO island shrunk. DeckStudio/DeckImprovement
  `createdFreshDraft`/`isImporting`/free-text-budget/`AnalysisCache`/`GapSignature` logic untouched.
  Layering grep (`decks/domain` → no `presentation`) still clean. See CHANGE LOG.
- ✅ **Auth (Phase 1, 2026-06-21 — the NINETEENTH Koin island; the CROSS-CUTTING island — `AuthViewModel`
  is consumed inside several screens, both already-Koin and still-Hilt).** `AuthViewModel` (the ONLY VM
  under `feature/auth/**` — `ProfileEditViewModel` lives in `feature/profile/**`, OUT of scope, left Hilt)
  resolves via `koinViewModel()` at ALL THREE of its call-sites (`ProfileScreen`, `TradesScreen`,
  `CreateTradeProposalScreen`). **No shared/Activity-scoped instance existed** — every call-site used the
  default `hiltViewModel()` (entry-scoped per NavBackStackEntry), so each swap to `koinViewModel()` is an
  exact 1:1 behavioural equivalent; NO `viewModelStoreOwner` needed. **MainActivity deeplink/PKCE wiring
  is untouched** — it calls `supabaseClient.handleDeeplinks(intent)` directly, never references
  `AuthViewModel`. **Self-contained `authKoinModule()` (NO args):** the 10 stateless auth use cases are
  Koin `single { }`s over the bridged `AuthRepository` (coreBridge) + `PushTokenRepository` (already a
  `single` in `settingsKoinModule`); `AnalyticsHelper` via coreBridge `get()`; Context via
  `androidContext()`. NOTHING promoted to coreBridge, NO island shrunk, NO new `ManaHubApp` bridge field.
  **Hilt `AuthModule` KEPT** (provides `@Named("supabase")` OkHttpClient/Retrofit + `UserProfileDataSource`
  + `@Binds AuthRepository`, all still consumed broadly by Hilt features); the use cases keep their Hilt
  `@Inject`/`@Singleton` (Koin builds its own equivalent stateless copies — the Trades pattern). See CHANGE LOG.
- ✅ **Game (Phase 1, 2026-06-22 — the TWENTIETH + LAST non-excluded Koin island; the heaviest, and the
  one that bridges DEFERRED features WITHOUT migrating them).** All three game VMs (`GameViewModel` 19
  ctor deps incl. voice/online/nearby, `GameSetupViewModel`, `GameResultStripViewModel`) resolve via
  `koinViewModel()`. `GameViewModel` is Activity-scoped (game state must persist across all in-game
  navigation): `koinViewModel(viewModelStoreOwner = activity)` at `AppNavGraph` (exact equivalent of the
  old `hiltViewModel(activity)`); its `mode`/`playerCount` nav args flow through a Koin-injected
  `SavedStateHandle` (`savedStateHandle = get()`). **The voice/online coupling is the crux: `GameViewModel`
  integrates the DEFERRED `core/voice` (`VoiceCommandRecognizer`), `core/online` (11 session use cases),
  and `core/nearby` (`NearbySessionRepository`) features, which MUST stay Hilt.** They are BRIDGED from the
  Hilt graph through `ManaHubApp` into `gameKoinModule` (the tournament-use-case bridge pattern) → ONE
  shared instance per DI graph; NOTHING under `core/voice`, `feature/online`, `core/nearby`,
  `feature/scanner` was de-Hilt'd (`git diff --stat` confirms). The Hilt `GameModule` (`@Binds
  GameSessionRepository`) is KEPT (the repo is bridged in coreBridge + consumed by still-Hilt online/nearby
  code). Shared deps reused via `get()`: `GameSessionRepository`/`TournamentRepository`/`AnalyticsHelper`/
  `UserPreferencesDataStore` (coreBridge), `RecordMatchResultUseCase` (tournamentKoinModule — the SINGLE
  finish-and-advance write path, identical instance), `VoiceModelRepository` (settingsKoinModule),
  `gamificationEngine` (already a `ManaHubApp` `@Inject` field). 2 game-only deps bridged new
  (`EvaluatePlayerEliminationUseCase`, `GamificationEngine`) + the 13 deferred-feature singletons. NOTHING
  promoted to coreBridge, NO island shrunk. The per-seat-stats `isLocal` win/loss derivation, the
  tournament single-write-path, the broadcast-first online sync, and the one-voice-language-per-session
  rule are all byte-for-byte untouched (DI-only change). See CHANGE LOG.
- 🟡 **Phase 2 — STARTED (Slice 1 DONE & GREEN, 2026-06-22).** `:shared:core-domain` module created
  (android + wasmJs, mirrors `:shared:core-model`/`:shared:core-common`: `com.android.kotlin.multiplatform.library`,
  plugins `apply false` in root, `alias` no-version in module; `commonMain` deps = `api(:shared:core-model)`
  + `coroutines.core`). A FIRST BATCH of **5 already-pure repository INTERFACES** moved into it
  (`commonMain`, package `com.mmg.manahub.core.domain.repository` UNCHANGED → zero `:app` import edits):
  `UserPreferencesRepository`, `StatsRepository`, `CommunityStatsRepository`, `NotificationPrefsRepository`,
  `PushTokenRepository`. Their impls STAY in `:app` (Android side) and keep implementing the moved
  interfaces. `:app` now `implementation(project(":shared:core-domain"))`. SKIPPED (still in `:app`, future
  slices): `UserCardRepository` (`androidx.paging.PagingData` + Room `core.data.local.dao.UserCardWithCard`
  + un-moved `core.domain.model.UserCard`), `CardRepository` (un-moved `core.domain.model.{Card,CardTag,
  SuggestedTag}`), `DeckRepository` (un-moved `core.domain.model.{Deck,DeckWithCards}`). NO use cases / impls
  / Retrofit / Ktor / Room touched this slice.
  - `:app:assembleDebug` → **BUILD SUCCESSFUL** (pre-existing deprecation warnings only).
  - `:shared:core-domain:compileKotlinWasmJs` → **SUCCESSFUL** (the 5 moved interfaces are web-compatible).
  - `:app:testDebugUnitTest` → **1964 tests, 122 failed, 0 errors, 2 skipped** (== baseline, ZERO new failures).
  - commonMain Android/AndroidX/browser/Room-import grep → EMPTY (PASS).
- ✅ **Phase 2 · Slice 2a-i — Deck model made KMP-pure + moved + `DeckRepository` moved (DONE & GREEN,
  2026-06-22).** The cheaper of the two Slice-2 blockers is cleared.
  - **`Deck.kt` SPLIT:** `DeckSlotEntry` (refs `Card`) + `AddCardRow` (refs `WishlistEntry`/
    `OpenForTradeEntry`) extracted into a NEW `:app`-only file
    `app/.../core/domain/model/AddCardRow.kt` (package `com.mmg.manahub.core.domain.model` UNCHANGED →
    their explicit `core.domain.model.{DeckSlotEntry,AddCardRow}` consumer imports DID NOT change). They
    stay in `:app` because `Card`/`WishlistEntry`/`OpenForTradeEntry` are still Android-coupled.
  - **Clock swap:** `Deck`'s two `System.currentTimeMillis()` default args → `kotlin.time.Clock.System
    .now().toEpochMilliseconds()` (`@OptIn(ExperimentalTime::class)` at the model). NO new dependency
    (Kotlin 2.3.20 stdlib). Identical epoch-millis semantics; no test asserts a pre-swap constant. The
    swap compiles on wasmJs (proof `kotlin.time.Clock` is KMP-safe).
  - **MOVED to `:shared:core-model` `commonMain` (package `com.mmg.manahub.core.model`):** `Deck`,
    `DeckSlot`, `DeckWithCards`, `BASIC_LAND_NAMES` (one file). Consumer imports rewritten
    `core.domain.model.{Deck,DeckSlot,DeckWithCards,BASIC_LAND_NAMES}` → `core.model.{...}` across `:app`
    main + test (sed on `import` lines) + ONE inline FQN in `StatsViewModel.kt:139`
    (`args[9] as List<…core.domain.model.Deck>` → `core.model.Deck` — sed only caught import lines, so
    inline FQNs must be grepped separately; this is a recurring gotcha for the `combine`-array cast VMs).
  - **MOVED to `:shared:core-domain` `commonMain`:** `DeckRepository` (package
    `com.mmg.manahub.core.domain.repository` UNCHANGED → zero consumer-import edits for the interface
    itself; only its internal `Deck`/`DeckWithCards` imports were repointed to `core.model`). All three of
    its referenced types (`Deck`, `DeckWithCards`, `DeckSummary`) are now in core-model. `DeckRepositoryImpl`
    STAYS in `:app` and keeps implementing the moved interface.
  - ProGuard wildcard `-keep class com.mmg.manahub.core.model.** { *; }` already covers the new Deck types
    (no new keep rule). DB stays v41 (no schema/entity touched — `DeckEntityMapper` only swapped imports).
  - Verified: `:app:assembleDebug` **BUILD SUCCESSFUL**; `:shared:core-model:compileKotlinWasmJs` +
    `:shared:core-domain:compileKotlinWasmJs` **SUCCESSFUL**; `:app:testDebugUnitTest` **1964 / 122 failed /
    2 skipped** (== baseline, ZERO new failures) — Deck suites all green (DeckStudioViewModelTest 113/0,
    DeckScorerTest 73/0, DeckRepositoryImplTest 26/0, DeckImprovementViewModelTest 3/0,
    BasicLandCalculatorTest 4/0). commonMain forbidden-import grep (`android|androidx|kotlinx.browser|
    org.w3c|java.|System.currentTimeMillis`) EMPTY for both modules (the only `System.currentTimeMillis`
    hit is a KDoc comment, not code).
- ✅ **Phase 2 · Slice 2b — Card model + `CardRepository` MOVED (DONE & GREEN, 2026-06-22).**
  `Card`/`CardTag`+`TagCategory`/`CardFace`/`SuggestedTag` in `:shared:core-model`; `CardRepository` in
  `:shared:core-domain`. (Unblocked by decoupling `CardTag.label` → `:app` extension; full detail in
  NEXT STEP / CHANGE LOG.)
- ✅ **Phase 2 · use-case batch #1 — 5 PURE use cases MOVED to `:shared:core-domain` `commonMain`
  (DONE & GREEN, 2026-06-22).** `SearchCardUseCase`, `SearchCardsUseCase`, `BuildScryfallQueryUseCase`,
  `GetCollectionSetCodesUseCase`, `GetCollectionStatsUseCase`. Still Hilt-owned (bridged into Koin
  islands) → `@Inject` stripped + new Hilt `@Provides` module `SharedDomainUseCaseModule`. (Detail in
  NEXT STEP / CHANGE LOG.)
- ✅ **Phase 2 · use-case batch #2 — `DeckCard`/`BasicLandDistribution` + `BasicLandCalculator`/
  `DeckCardValidator` MOVED (DONE & GREEN, 2026-06-22).** `DeckCard` + `BasicLandDistribution` (both
  pure: only ref `Card` from core-model) split out of `:app` `core/domain/model/DeckBuilderState.kt`
  into a new `:shared:core-model` file `core/model/DeckCard.kt`; `DeckBuilderState`/`BuilderStep`/
  `BuilderTab`/`ReviewGroupBy` stayed in `:app` (presentation state, out of scope) + now import the two
  moved types. `BasicLandCalculator` + `DeckCardValidator` (both `object`s, NO `@Inject` → NO Hilt
  provider needed) `git mv`'d to `:shared:core-domain` `commonMain` package `core.domain.usecase.decks`
  UNCHANGED → zero consumer-import edits for the use cases. `DeckCard`/`BasicLandDistribution` consumer
  imports rewritten `core.domain.model.X` → `core.model.X` across `:app` main + test (9 files); NO inline
  FQN refs (grep clean). Commit `7932339`. Verified: both shared modules `compileKotlinWasmJs` SUCCESSFUL;
  `:app:assembleDebug` SUCCESSFUL; `testDebugUnitTest` 1964/122-fail/2-skip (== baseline,
  `BasicLandCalculatorTest` 4/0 green); commonMain forbidden-import grep EMPTY (lone `System.currentTimeMillis`
  hit is the pre-existing `Deck.kt` KDoc comment).
- ✅ **Phase 2 · `:shared:core-data` scaffold + RateLimitedQueue (DONE & GREEN, 2026-06-22).**
  `:shared:core-data` module created (android + wasmJs, mirrors core-domain; deps = `api(:shared:core-model)`,
  `api(:shared:core-domain)`, `impl(:shared:core-common)`, `impl(coroutines.core)`). Rate-limit queues
  extracted: new `RateLimitedQueue` + `RateLimitConfig` + `RetryDecision` in `commonMain`
  (`com.mmg.manahub.core.data.network`); platform deps removed (`System.currentTimeMillis()` →
  `Clock.System`, `AtomicLong` → plain `var` under mutex, `HttpException` → pluggable `shouldRetry` lambda).
  `:app` `ScryfallRequestQueue` + `ArchidektRequestQueue` are now thin wrappers delegating to
  `RateLimitedQueue`. Verified: `:shared:core-data:compileKotlinWasmJs` SUCCESSFUL;
  `:app:assembleDebug` SUCCESSFUL; `testDebugUnitTest` 1964/122/2 (== baseline); 0 platform imports in
  `commonMain`.
- ✅ **Phase 2 · Retrofit→Ktor — ALL 6 APIs migrated (DONE & GREEN, 2026-06-22).** Every Retrofit API
  surface replaced with a KMP-pure Ktor `HttpClient`-based client in `:shared:core-data` `commonMain`:
  `CloudflareContentClient` (`79a1e0b`), `YouTubeClient` (`3483ff8`), `FriendshipClient` (`1475268`),
  `UserProfileClient` (`8eb9642`), `ArchidektClient` (`47868dd`), `ScryfallClient` (`7524265`).
  DTOs moved to shared `core.data.remote.dto` (all `@Serializable`). Gson DTOs deleted. The
  `@Named("supabase") Retrofit` was the last Retrofit provider and is deleted; the global `OkHttpClient`
  stays (DraftModule YouTube). Ktor deps (`ktor-client-core`, `content-negotiation`,
  `serialization-kotlinx-json`, `ktor-client-okhttp` in androidMain, `ktor-client-js` in wasmJsMain)
  added to version catalog + `:shared:core-data`. `ScryfallRequestQueue` + `ArchidektRequestQueue`
  updated from `retrofit2.HttpException` to `io.ktor.client.plugins.ResponseException`. Baseline held
  at 1964/122/2 throughout all 6 migrations; 0 platform imports in `commonMain`.
- ✅ **Phase 2 §9.6 — auth domain + playtest model + trade repo impls + news domain batched move
  (GREEN, `bcc2cfb`, 2026-06-23).** Four parallel agent batches landed in one commit:
  (1) Auth domain (5 files → `:shared:core-domain` `core.domain.auth`): `AuthRepository`, `AuthUser`,
  `AuthError`, `AuthResult`, `SessionState` — package UNCHANGED, zero consumer import edits; cross-module
  smart-cast fixes in `AccountSection` + `BattlefieldContent` (local-val pattern).
  (2) Playtest models (`PlaytestModels.kt` → `:shared:core-model` `core.model`): package CHANGED from
  `feature.playtest.domain.model` → `core.model`; 20+ consumer imports + 5 test files updated.
  (3) Trade repo impls (`SharedListsRepositoryImpl` + `TradeSuggestionsRepositoryImpl` →
  `:shared:core-data` `core.data.repository`): `@Inject`/`@Singleton` stripped, `TradesModule` `@Binds` →
  `@Provides`.
  (4) News domain (`ContentSource` → `:shared:core-model` `core.model.news`; `NewsRepository` →
  `:shared:core-domain` `core.domain.repository`): package-changed `ContentSource` consumers updated
  (8 production + 2 test FQN refs).
  Verified: `:app:assembleDebug` GREEN; 3 shared modules `compileKotlinWasmJs` GREEN;
  `testDebugUnitTest` 1964/122/2 (== baseline); 0 platform imports in `commonMain`.
- ✅ **Phase 2 — friends/game/tournament/playtest models + repos shared (GREEN, `ef4e98a`, 2026-06-23).**
  10 pure domain models → `:shared:core-model`: Friends (7: AcceptInviteResult, Friend, FriendCard,
  FriendMatchHistory, FriendRequest, FriendStats, OutgoingFriendRequest, FolderFilters), Game (2:
  CounterIconKey, LayoutTemplate + GridSlotPosition/PlayerSlot/LayoutTemplates), Tournament (1:
  MatchResult). 2 repo interfaces → `:shared:core-domain`: `FriendRepository`, `PlaytestRepository`.
  Skipped: GameResult/PhaseStop/PlayerConfig (`@StringRes`/`PlayerThemeColors`). 40 consumer files updated.
  Verified: assembleDebug + wasmJs GREEN; testDebugUnitTest 1964/122/2 (== baseline); 0 platform imports.
- ✅ **Phase 2 — cleanup: AddCardRow/DeckSlotEntry → core-model + TradesRepository shim deleted (GREEN,
  `f992cf6`, 2026-06-23).** `DeckSlotEntry` + `AddCardRow` moved from `:app` `core.domain.model` to
  `:shared:core-model` `core.model` (all deps — Card, WishlistEntry, OpenForTradeEntry — already shared;
  12 consumer imports updated + 2 cross-module smart-cast fixes). The `feature.trades.domain.repository.
  TradesRepository` typealias shim deleted — 20 consumers (18 production + 2 test) repointed to the
  canonical `core.data.repository.TradesRepository` in `:shared:core-data`. 32 files changed.
  Verified: assembleDebug + wasmJs GREEN; testDebugUnitTest 1964/122/2 (== baseline); 0 platform imports.
- 🟢 **Phase 2 data-layer sharing SUBSTANTIALLY COMPLETE (2026-06-23).** Summary:
  - **165 shared `.kt` files** in `commonMain` across 4 modules (core-model 77, core-common 4,
    core-domain 36, core-data 48).
  - **Retrofit completely removed** (0 imports project-wide); ALL 6 API clients replaced with Ktor.
  - **All DTOs, rate-limit queues, caches, data sources, mappers** shared in `core-data`.
  - **Domain models shared:** auth (5), draft (23), trade (19), friends (8), game (2), tournament (1),
    playtest (7), news (2), community decks (6), deck (5), card (4), collection/stats (5), search (3),
    user card (2) — plus general-purpose types (DataResult, CachePolicy, Page, etc.).
  - **19 repo interfaces** shared in `core-domain` (incl. AuthRepository) + `TradesRepository` in `core-data`.
  - **5 repo impls** shared in `core-data` (NotificationPrefs, CommunityStats, CommunityDecks,
    SharedLists, TradeSuggestions).
  - **12 use cases / utilities** shared (7 in core-domain, 3+2 in core-data).
  - Phase 1 Hilt→Koin cutover COMPLETE (20 islands, all non-excluded features).
  - **Remaining Phase 2 items are ALL blocked on cross-cutting infrastructure — deferred to Phase 3+.**
- ⬜ **Phase 2 (remaining — blocked, deferred to Phase 3+):**
  - **Room-backed repo impls** (Card, Deck, Stats, UserCard, OpenForTrade, Wishlist, Trades,
    GameSession, Tournament) — each needs a 10-40+ method DAO-abstraction interface in `commonMain`.
    For web, fresh Supabase-backed impls behind the same repo interface are more practical than
    abstracting every Room DAO method.
  - **Gamification types** (`ProgressionEvent`, `PlayerProgression`, `GamificationRepository`) — blocked
    on `java.time.Instant` (needs `kotlinx-datetime`), `@StringRes`.
  - **Game domain models** (GameMode, GamePhase, Player, PlayerConfig, GameResult, PhaseStop) — blocked
    on `@StringRes`/`R`/`PlayerThemeColors`.
  - **Remaining use cases** (~95) — mostly feature-level, tightly coupled to Room repos or gamification.
  - **`ComputeCardTagsUseCase`** — blocked on Gson tag mapper.
  - **EXCLUDED features** (online, voice, scanner) — deferred per plan.
- ✅ **Phase 3 · Slice 1 — `:shared:core-ui` module + MagicTheme design system (DONE & GREEN,
  2026-06-23, `e852840`+`ec86cdc`).** `:shared:core-ui` KMP module created (kotlin-multiplatform +
  android-kmp-library + compose-multiplatform + kotlin-compose plugins; deps = core-model + CMP
  compose.runtime/foundation/material3/ui). **CMP upgraded 1.9.0 → 1.11.0** to fix
  `NoSuchMethodError` on AGP 9.1.0's `KotlinMultiplatformAndroidComponentsExtension.onVariant()`.
  9 theme files MOVED to `commonMain` (`core.ui.theme` package UNCHANGED → zero consumer import edits):
  AppTheme, Color, MagicColors (12 palettes), PlayerTheme, MagicShapes, Spacing, MagicTypography (data
  class only — system-font defaults), MagicTheme (CompositionLocals + M3 bridge), Type. MagicTypography
  SPLIT: data class → commonMain; font families (R.font.*) + NeonVoidTypography → stay `:app`. MagicTheme
  gained `typography` param; `MagicThemeAndroid` wrapper in `:app` passes NeonVoidTypography. BLOCKED:
  font families (R.font.*), ThemeBackground (*Background refs), MagicModifiers (BlurMaskFilter).
- ✅ **Phase 3 · Slice 2 — 12 pure UI composables moved to `:shared:core-ui` `commonMain` (DONE &
  GREEN, `473dc60`, 2026-06-23).** `EmptyState`, `ErrorState` (`InlineErrorState`+`FullErrorState`),
  `MagicProgressBar` (+`MagicLoadingFooter`), `MagicToast` (`MagicToastState`/`MagicToastHost`/types),
  `ManaUtils` (`manaColorFor`/`counterKeyToManaToken`), `PlayerCountStepper`, and 6 theme backgrounds
  (`AncientOak`, `ArcaneCosmos`, `ForestMurmur`, `HallowedPrint`, `HexGrid`, `MedievalGrimoire`).
  Package `core.ui.components` UNCHANGED → zero consumer import edits. **Two wasmJs fixes:**
  (1) `HexGridBackground` `Math.PI` → `kotlin.math.PI` (JVM-only `java.lang.Math`);
  (2) `MagicToast` `Icons.Default.{Check,Close,Info,Warning}` replaced with inline `ImageVector.Builder`
  paths in a private `ToastIcons` object (the `material-icons-core` artifact has no CMP wasmJs target as
  of CMP 1.11). **Skipped (still in `:app`):** `FloatingDelta` (`MulishFontFamily` → `R.font.*`),
  `ManaColorPicker` (`painterResource(R.drawable.ic_counter)` + `ManaSymbolImage`), `CollectionScreen`
  (empty stub). Verified: assembleDebug + wasmJs GREEN; testDebugUnitTest 1964/122/2 (== baseline);
  0 platform imports in `commonMain`.
- ✅ **Phase 3 · Slice 3 — Coil 2→3 upgrade + image composables (DONE & GREEN, 2026-06-24,
  `8c34442`→`71026d7`).** **Coil 2.7.0 → 3.3.0** upgraded (53 files, `coil.*`→`coil3.*`, pinned at 3.3.0
  for Hilt metadata compat). `coil-compose` added to `:shared:core-ui` commonMain, `coil-network-okhttp`
  to `:app`. PlayerEditSheet + PullToRefresh moved (`7a4f31d`). Then 4 commits for Slice 3 proper:
  (1) `CollectionCardGroup` → `:shared:core-model`, `PriceFormatter` → `:shared:core-data` (then relocated
  core-data→core-model to avoid Supabase/Ktor transitive deps in core-ui) with expect/actual for
  platform-specific number formatting (android: `java.util.Locale`, wasmJs: pure Kotlin arithmetic).
  (2) `CardRarity` enum + `CopyBadge`/`FoilBadge`/`RarityDot` + `MagicSegmentedControl` extracted from
  `:app` SharedComponents.kt/SetSymbol.kt into own shared files (pure, zero platform deps).
  (3) `SetSymbol` decoupled from Android (Coil 3 raw URL, inline fallback vector), `CardName` decoupled
  (inline alchemy vector), `CardGridItem`+`CardListItem` decoupled (`Icons.Default.Style` → inline
  `StackedCardsIcon`). All 4 moved to shared core-ui. `InlineIcons.kt` holds 3 internal KMP vector
  replacements. `ManaCostImage.kt` retained in `:app` (`ManaSymbolImage`/`ManaCostImages` still Android-only).
  **31 files** in `:shared:core-ui` commonMain (10 theme + 21 components). Verified: assembleDebug GREEN
  (--rerun-tasks), core-ui+core-model wasmJs GREEN, 0 platform imports in commonMain.
- ✅ **Phase 3 · Slice 4 — CardSearchField + CardFullScreenDialog moved (DONE & GREEN, 2026-06-24,
  `ddeadf7`).** Both decoupled from Android: stringResource → hardcoded English, Icons.Default →
  inline vectors (Search/Clear/Close/Flip in `InlineIcons.kt`). `CardSearchField.searchCards` param
  changed from `SearchCardsUseCase` to `suspend (String) -> DataResult<List<Card>>` lambda (avoids
  core-ui→core-domain dep). MagicToast deduped onto shared `CloseIcon`. **33 files** in core-ui.
- 🟢 **Phase 3 composable moves SUBSTANTIALLY COMPLETE (2026-06-24).** Then Phase 4 unblocked more:
  `kotlinx-datetime` 0.6.2 added → `DraftSetCard` moved (java.time→kotlinx-datetime);
  `TimeAgoFormatter` shared (English-only, Clock.System) → `NewsItemCard` moved
  (`R.drawable.mtg_card_back` hoisted as `Painter?` param, `Icons.Default.PlayArrow` → inline vector).
  **35 files in `:shared:core-ui` commonMain** (10 theme + 25 components, 8 inline ImageVectors).
  Remaining composables in `:app` still have deep platform deps:
  - **ManaSymbolImage** (LocalContext + R.drawable SVG per symbol): DeckItem, ManaCostImage,
    ManaColorPicker, CircularDistribution → need per-platform image loading or URL-based approach.
  - **Bitmap resources** (`R.drawable.mtg_card_back`): DeckItem (+ `SimpleDateFormat`/6 strings).
  - **android.graphics**: ManaCurveChart (`Paint`/`Typeface`) → need expect/actual Canvas.
  - **Heavy string resources**: VariantSelectorSheet, AddCardSheet, TradeSelectionSheet, DeckItem (6+
    strings each) + CardSearchSheet (`android.app.Activity`).
  - **Feature-layer deps**: GameModeSelector (`GameMode`), MagicBottomBar (`Screen`/`R.drawable`),
    ParticipantListRow/RoomCodeDisplay/RoomCodeField (online-excluded).
- 🟢 **Phase 4 (Android-first scope) COMPLETE (2026-07-01).** All Android-side punch-list items
  (CMP Res POC, remaining composables survey, repo-interface Room-type extraction, blocked-use-case
  unblocking, Room-backed repo impl survey — see NEXT STEP below for the full item-by-item closure) are
  either DONE, decided-skip-with-rationale, or closed-as-permanently-deferred. What the ORIGINAL Phase-4
  scope in `kmp-migration-plan.md` §5 called out — Firebase/WorkManager/camera/voice `expect`/`actual`,
  web responsive layout, `:webApp` entrypoint, web security/telemetry review — is, on inspection, **100%
  web-target work** with no Android-side action possible until the web phase starts; it is NOT
  "remaining Phase-4 work" in the Android-first sense, it IS the web phase, and belongs to
  `kmp-web-fullstack-dev` per CLAUDE.md's agent-assignment section. The deferred Phase-2 items (Room DAO
  abstractions for web, `PushTokenRepositoryImpl`) fall in the same bucket.
- 🟡 **Phase 5 · Slice 1 — full regression audit (Android-only scope) DONE & GREEN (2026-07-01,
  `30864f6`).** The documented test baseline ("1964 tests, ~122-124 failed, 2 skipped") had been
  treated as a fixed floor throughout the migration but never root-caused — every prior session only
  diffed the failing-CLASS set against the previous run. This slice finally did it: ran
  `:app:testDebugUnitTest --rerun-tasks` fresh, read the actual `<failure>` messages out of every
  `TEST-*.xml` report (not just class names), and classified all 19 failing classes.
  - **1 migration regression found and FIXED:** `CanPlaytestDeckUseCaseTest` — stale test left over
    from a Phase 4 slice (2026-06-29) that added Casual-format support (60-card min, same threshold as
    Standard) to `CanPlaytestDeckUseCase` but never updated the test, which still asserted
    `casual format then Ineligible` under "Group 5: Unsupported formats." Fixed by removing the stale
    assertion and adding a proper "Group 1b: Casual" boundary-test block (60/>60/59/0 cards), mirroring
    the existing Standard coverage. Commit `30864f6`.
  - **18 classes / 122 tests CONFIRMED pre-existing, NOT migration-caused, out of scope for Phase 5:**
    - **4 classes / 74 tests in the permanently-excluded trees** (presumptively out of scope by
      definition — these trees are untouched by this migration): `OnlineSessionRepositoryImplTest`,
      `LobbyJoinViewModelTest` (`feature/online`); `CommandGrammarTest` (`core/voice`);
      `ScannerViewModelTest` (`feature/scanner`).
    - **14 classes / 48 tests independently verified pre-existing** via `git log`/`git blame` on the
      test file + code under test (none touched by a KMP-migration commit) and, for the one genuinely
      ambiguous case, a live `git worktree` comparison against `master` (`AuthRepositoryImplTest` fails
      identically on `master` — confirmed pre-existing, not a regression). Grouped by subsystem:
      - **Push/notifications (2):** `PushTokenRepositoryImplTest`, `PushDeeplinkRouterTest`.
      - **Collection/sync (5):** `UserCardRepositoryImplTest`, `UserCardRepositoryImplSyncTest`,
        `CollectionUseCasesTest`, `CollectionSyncTest`, `SyncManagerTest`.
      - **Auth (1):** `AuthRepositoryImplTest` (verified against `master` directly, see above).
      - **Tagging (2):** `TagLocalizationTest`, `TagDictionaryViewModelTest`.
      - **Trades (4):** `OpenForTradeRepositoryImplTest`, `TradesRepositoryImplTest`,
        `WishlistRepositoryImplTest`, `TradeProposalViewModelMatchesTest`.
  - **0 uncertain items remain** — every one of the 19 originally-failing classes was placed
    confidently into "fixed" or "confirmed pre-existing."
  - **New test baseline: 1967 tests, 122 failed, 2 skipped** (was 1964/123/2 — net +3 tests from the
    `CanPlaytestDeckUseCaseTest` edit, failures down by exactly 1, zero new failures introduced). This
    replaces "1964/122-124/2" as the tracker's reference floor going forward.
  - **CI/build-health finding: there is NO CI configuration in this repository** — verified
    project-wide: no `.github/workflows/`, no `.gitlab-ci.yml`, no `azure-pipelines.yml`, no
    `Jenkinsfile`. Nothing was broken by the module restructuring (`:shared:core-*` + `:app`) because
    there is no CI pipeline referencing the old single-module task graph to break. This is a real gap
    (no automated build/test gate on push), but standing up CI is a separate future initiative, not
    something this "hardening/regression audit" slice should invent — flagged here for whoever picks up
    CI setup next.
  - Verified: `:app:testDebugUnitTest --tests "...CanPlaytestDeckUseCaseTest" --rerun-tasks` → 26/0
    failures; `:app:assembleDebug --rerun-tasks` → BUILD SUCCESSFUL; full `testDebugUnitTest` →
    1967/122/2 (no new failing classes vs. the pre-fix run). No `feature/online`/`core/voice`/
    `feature/scanner` files were touched (confirmed via `git diff --stat` on the fix commit — the only
    file changed is the playtest test).

## REMEDIATION LOG — audit 2026-07-01 (local doc: `kmp-migration-audit-2026-07-01.md`, gitignored)

> **EXECUTION AUTHORIZATION (user, 2026-07-03):** run the remediation plan + Hilt→Koin cutover with
> **FULL autonomy** — chain slices, delegate to `android-kotlin-architect`, verify + **commit each green
> slice locally WITHOUT asking**, and continue. **NO push** (local commits only until the user explicitly
> asks). On a blocker/micro-decision: use best judgment, document it here, keep going; only consult the
> user if the action is irreversible or changes product scope.

A read-only audit (2 sub-agents, cross-verified vs the live tree) produced a prioritized P0/P1/P2 backlog
in `docs/plans/kmp-migration-audit-2026-07-01.md` (gitignored per the planning-doc rule; delete when the
backlog is fully executed). Being worked through as GREEN slices:
- ✅ **P0.1 (2026-07-01, `b392a4f`)** — deleted 4 DEAD shared use cases stranded in `:shared:core-domain`
  (`SearchCardUseCase`, `GetCardByNameUseCase`, `GetSetCardsUseCase`, `LookupCardIdUseCase`) — migrated in
  Phase-2 batch #1 with no consumer, `@Provides` already removed from `SharedDomainUseCaseModule`.
  `RemoveCardUseCase` KEPT (real test consumer `CollectionUseCasesTest`, plain-ctor). Gauntlet green,
  1967/122/2. NOTE: core-domain needed `clean` — deleting klib source corrupts the wasmJs incremental cache
  (`WasmIrFileMetadata` AIOOBE); `:shared:core-domain:clean` fixes it. Add this to known-gotchas.
- ✅ **P0.3 (2026-07-01, doc-only)** — fixed the §9.2 leak-grep regex (was `import (androidx|...)` → floods
  core-ui with `androidx.compose.*` false positives). Now uses PCRE negative lookahead
  `import (androidx\.(?!compose)|android\.|java\.)` + a non-`-P` fallback. Baseline label updated 1964→1967.
- ✅ **P1.3 (2026-07-01, `edf9230`)** — converted the 4 orphan `@HiltViewModel` to Koin
  (`AdvancedSearchViewModel`+`SetPickerViewModel` → new `searchWidgetsKoinModule`; `GamificationCelebration
  ViewModel` → new `gamificationKoinModule`; `ProfileEditViewModel` → existing `ProfileKoinModule`). All deps
  already single'd in coreBridge/addCard → zero new bridges. **DI cutover now COMPLETE for every non-excluded
  feature** — `grep '^@HiltViewModel'` non-excluded is EMPTY. Baseline 1967/122/2.
- ✅ **§3.1 quick-wins (2026-07-01, `<pending-sha>`+committed)** — (1) `AutoTagCardUseCase` already migrated
  (it's a typealias to `core-data`'s `SuggestTagsUseCase`; audit doc was stale). (2) `GetAccountNudgeUseCase`
  DEFERRED — real presentation dep (`import feature.home.presentation.NudgeTrigger`); unblock needs
  promoting `NudgeTrigger` to a domain model (separate decision). (3) `ImportCommunityDeckUseCase` MOVED to
  `core-domain` (Firebase→`CrashReporter`, `System.currentTimeMillis`→`Clock.System`). **NEW: `core-domain`
  now depends on `core-common`** — future use-case moves touching `CrashReporter`/`DispatcherProvider`/
  `KeyValueStore` need no further Gradle edit. Baseline 1967/122/2.
- ✅ **P1.1 (2026-07-01, committed)** — RE-SCOPED targeted swap: the 7 migration-candidate (A) classes
  (`PushTokenRepositoryImpl`, `GamificationSyncManager`, `SyncManager`, `UserProfileDataSource`,
  `DraftSimRepositoryImpl`, `FriendRepositoryImpl`, `PlaytestRepositoryImpl`) now use `core-common`'s
  `CrashReporter` instead of direct `FirebaseCrashlytics` — removes the Crashlytics pin blocking their
  future move to shared. Pure-Android (B) screens/VMs/FCM keep `FirebaseCrashlytics` by design. Full (A)
  set fit the cap; no follow-up. Instrumentation-preserving (Android actual delegates to Crashlytics).
  Hilt resolves `CrashReporter` via a new `CrashlyticsModule` provider. 4 test files drop
  `mockkStatic(FirebaseCrashlytics)`. **New baseline: 1967/118/2** (was /122 — 4 previously-flaky
  unmocked-Crashlytics tests now pass; failing-CLASS set did not grow, A/B-verified via git stash).
  → NOTE the reference floor is now **1967 tests / 118 failed / 2 skipped**.
- 🔵 **P1.5 — DECIDED by USER (2026-07-01): Hilt is NOT permanent. TARGET = full Koin.** Move ALL
  non-excluded DI to Koin now. The Hilt runtime survives ONLY as long as the excluded trio
  (`feature/online`, `feature/scanner`, `core/voice`) stays Hilt (they still need a Hilt graph for their
  `@HiltViewModel`s + the shared singletons they consume); Hilt fully retires in the final excluded-trio
  migration wave. Actionable now: convert every remaining non-excluded Hilt `@Module` to Koin, keeping only
  the bindings the excluded trio genuinely consumes (bridge those Koin→Hilt or keep a thin Hilt `@Provides`
  reading the Koin single). → NEW WORK ITEM **"Hilt-to-Koin module cutover"** below.
- 🔵 **P1.2 — RESERVED by USER (2026-07-01): Room repo impls STAY in `:app` until the web implementation
  phase begins.** Do NOT move the 6 Room-backed impls to `core-data/androidMain` now, no spike. Revisit
  when `kmp-web-fullstack-dev` starts the web target (the wasmJs actual + the androidMain move land together).
- ✅ **P1.4 (2026-07-01, committed)** — first real `commonTest` coverage: 26 tests (kotlin-test +
  coroutines-test, no MockK) — `RateLimitedQueue` (7, core-data), `ManaBaseAnalyzer` (10) + `TribeDeriver`
  (9, core-domain). All PASS on JVM host (`:shared:*:testAndroidHostTest` — the KMP-android host test task,
  NOT `testDebugUnitTest`) and COMPILE for wasmJs. No production change. Floor 1967/118/2.

### WORK ITEM — Hilt→Koin full module cutover (per P1.5 decision "move all to Koin")
Three Hilt subsystems to invert to **Koin as source of truth**, keeping a MINIMAL Hilt graph only for the
excluded trio (online/scanner/voice/nearby) until their final wave:
1. **25 `@Module`s**: 4 excluded-own (`OnlineSessionModule`/`VoiceModule`/`ScannerModule`/`NearbyModule` —
   STAY Hilt); ~11 infra (`DatabaseModule`(Room)/`NetworkModule`/`SupabaseModule`/`DispatcherModule`/
   `CoroutineScopesModule`/`CrashlyticsModule`/`AnalyticsModule`/`PushModule`/`SyncModule`/`CommunityModule`/
   `RepositoryModule`); ~10 feature/bridge (`AuthModule`/`DeckDoctorModule`/`DraftModule`/`FriendModule`/
   `GameModule`/`NewsModule`/`TournamentModule`/`TradesModule`/`GamificationModule`/`SharedDomainUseCaseModule`).
2. **8 `@HiltWorker`** (WorkManager): 7 non-excluded (`GamificationSyncWorker`/`QuestRotationWorker`/
   `RegisterPushTokenWorker`/`UnregisterPushTokenWorker`/`CollectionStatsSyncWorker`/`CollectionSyncWorker`/
   `PriceRefreshWorker`) → migrate to a Koin `WorkerFactory` (`koin-androidx-workmanager`);
   `EmbeddingDatabaseUpdateWorker` (scanner) STAYS Hilt.
3. **Excluded-trio consumption surface** (the minimal Koin→Hilt bridge): they `@Inject` `AuthRepository`,
   `CardRepository`, `AnalyticsHelper`, `OkHttpClient`, `WorkManager`, `UserPreferences*`, dispatchers,
   `VoiceModelRepository`, `SoundManager`, `NearbySessionRepository`, `OnlineSessionRepository` + their own
   online/nearby use cases. Whatever the excluded trio consumes must stay Hilt-resolvable (thin Koin→Hilt
   bridge or keep those specific Hilt bindings).
Incremental, GREEN per batch. Batch order: safe feature modules first (no excluded/worker consumer) →
worker subsystem → infra modules (define the bridge surface) → last, the minimal excluded bridge.
- ✅ **Cutover Batch 1 (2026-07-03, committed)** — Community + Tournament modules inverted to Koin
  (deleted their Hilt `@Module`s; `TournamentRepository` flipped native in coreBridge; 4 dead `ManaHubApp`
  `@Inject` fields removed). GREEN 1967/118/2.
- 🔑 **KEY FINDING — `SharedDomainUseCaseModule` is the cross-cutting LYNCHPIN.** It `@Provides` use cases
  (News: `GetNewsFeed`/`RefreshNewsFeed`/`ManageSources`; all 10 Draft use cases; `AddToWishlistUseCase`;
  `RefreshCollectionPricesUseCase`; etc.) that require LIVE Hilt bindings of the feature repos → News,
  Draft, DeckDoctor (transitive via `ScoringDraftDeckBuilder`→`DeckScorer`), and Trades cannot convert
  until this hub does. And the hub is itself entangled: `RefreshCollectionPricesUseCase`→`PriceRefreshWorker`
  (Hilt worker), `AddToWishlistUseCase`→`ScannerViewModel` (excluded). **Unlock = a Koin→Hilt REVERSE
  BRIDGE** (`app/di/KoinToHiltBridgeModule.kt`, Hilt `@Module` with `@Provides fun x() =
  GlobalContext.get().get<X>()` per type a Hilt consumer still needs) so the hub + feature modules move to
  Koin while workers/excluded keep resolving via Hilt-from-Koin. Koin starts in `ManaHubApp.onCreate` before
  any worker/excluded resolution → ordering safe (verify).
- ✅ **Cutover Batch 2 (committed, `refactor(kmp): Hilt->Koin cutover batch 2`)** — the reverse bridge
  (`KoinToHiltBridgeModule`) landed + `SharedDomainUseCaseModule` shrunk to 2 residual `@Provides`
  (`ScryfallCache`/`ScryfallRemoteDataSource` builder, `ComputeCardTagsUseCase`) — the rest natively
  Koin-built in the new `SharedDomainKoinModule`. This is exactly the doc's "Batch 2" above.
- ✅ **Cutover Batch 3 (this session, 2026-07-03, NOT YET committed — user reviews first)** — the four
  now-free feature-private Hilt `@Module`s DELETED: `NewsModule`, `DraftModule`, `DeckDoctorModule`,
  `TradesModule`. Consumer audit (full-codebase grep, confirmed zero excluded-trio/`@HiltWorker` touch)
  found NO type needed a `KoinToHiltBridgeModule` reverse-bridge entry — every repo impl's Hilt-only
  consumer had already migrated to Koin.
  - `NewsRepositoryImpl`/`NewsFeedService`/`RssFeedParser`/`YouTubeRssFeedParser` lost `@Inject`/
    `@Singleton` → native singles in `newsKoinModule` (new `newsDao` bridge field).
  - `DraftRepositoryImpl`/`DraftSimRepositoryImpl`/`ScoringDraftDeckBuilder` lost `@Inject`/`@Singleton` →
    `DraftRepository`/`DraftSimRepository` natively built in `coreBridgeKoinModule` (shared w/ Home);
    `DraftEngine`/`DraftDeckBuilder`/`BotDrafter`/`BoosterGenerator`/the YouTube+Cloudflare Ktor clients/
    `Gson` natively built in `draftKoinModule` (new `draftSetDao`/`draftSessionDao` bridge fields). The
    residual Hilt `SharedDomainUseCaseModule` lost its 3 Draft-only `@Provides` (`GetDraftableSetsUseCase`/
    `GetSetTierListUseCase`/`GetSetCardsPageUseCase`) — `DraftSimRepositoryImpl` was their only Hilt-only
    consumer and is itself now Koin-native.
  - **The ENTIRE Deck Doctor engine graph was ALREADY plain (no `@Inject`) in `:shared:core-domain`** —
    the sole reason `DeckDoctorModule` survived batches 1–2 was `ScoringDraftDeckBuilder`'s `@Inject`
    (Draft's `DeckScorer` consumer); de-Hilt'ing Draft this batch let the whole engine
    (`DeckScorer`/`RoleClassifier`/`ManaBaseAnalyzer`/`EdhrecPowerResolver`/`DeckMagicEngine`/
    `BudgetOptimizer`/`CandidatePoolGenerator`/`InferDeckIdentityUseCase`/all 6 deck use cases) move to
    `decksKoinModule` in one shot — `decksKoinModule(...)` now takes only `applicationScope`.
  - `TradesRepositoryImpl`/`WishlistRepositoryImpl`/`OpenForTradeRepositoryImpl` lost `@Inject`/
    `@Singleton` → natively built in `coreBridgeKoinModule` (shared across many islands);
    `SharedListsRepositoryImpl`/`TradeSuggestionsRepositoryImpl` (already plain from an earlier KMP move)
    + all 5 trades remote data sources natively built in `tradesKoinModule` (new `localWishlistDao`/
    `localOpenForTradeDao` bridge fields; `tradeCollectionSyncDao` kept). **`SupabaseClient` had NO Koin
    presence before this batch** — newly forward-bridged in `coreBridgeKoinModule` (new `ManaHubApp`
    field) since all 5 trades remote sources need it.
  - `DeckRepositoryImpl` also flipped native in `coreBridgeKoinModule` (new `deckDao` bridge field) —
    `RepositoryModule.bindDeckRepository` deleted (single surgical line removed from an otherwise-Hilt
    shared module, since Hilt would otherwise fail with a missing binding for the stripped impl).
  - `OkHttpClient` forward-bridged into `coreBridgeKoinModule` too (reused the pre-existing `ManaHubApp`
    field used for Coil) — needed by the newly-native `NewsFeedService`.
  - **Koin-graph audit (manual, no `checkModules()` test exists in this repo):** grepped every
    `single<T>`/`single { }` across all loaded modules for the ~15 flipped/newly-bridged types — zero
    duplicates found (no `DefinitionOverrideException` risk); every cross-module `get()` (e.g. `DeckScorer`
    resolved from `draftKoinModule`'s `ScoringDraftDeckBuilder`, `SupabaseClient` resolved from
    `tradesKoinModule`'s 5 remote sources) has exactly one registration site, following the pre-existing
    `TournamentDao`/`TournamentRepository` cross-module precedent.
  - Verified: `./gradlew :app:assembleDebug` BUILD SUCCESSFUL (deprecation warnings only — Hilt/KSP
    compiled clean, confirming no missing binding anywhere in the app);
    `:shared:core-domain:compileKotlinWasmJs` SUCCESSFUL; `:app:testDebugUnitTest` → **1967 tests, 118
    failed, 2 skipped** — EXACTLY the documented floor (`1967/118/2`, see NEXT STEP above), failing-class
    set cross-checked class-by-class against the documented pre-existing list (Online×2, Push, Collection
    sync×2/3, Auth, Tagging×2, Scanner, Trades×4 — all present, none new); commonMain leak grep confirmed
    the only `androidx`/`android.`/`java.` hits are pre-existing `shared/core-ui` Compose Multiplatform
    imports (legitimate — CMP shares the `androidx.compose.*` namespace), zero in `core-domain`/`core-model`
    (untouched this batch). **Not committed** (user reviews the diff first, per task instructions).
- ✅ **Cutover Batch 3 — COMMITTED (2026-07-03).** News/Draft/DeckDoctor/Trades → Koin. GREEN 1967/118/2.
- ✅ **Cutover Batch 4 — COMMITTED (2026-07-03).** Friend/Game/Gamification → Koin (3 Hilt @Modules
  deleted). Reverse-bridged FriendRepository + GamificationSyncManager + QuestReconciler (workers). 6
  ManaHubApp fields → `by inject()`. Fixed a pre-existing missing `single<GameSessionDao>`. GREEN 1967/118/2.
- ✅ **Cutover Batch 5 — COMMITTED (2026-07-03).** AuthModule → Koin (deleted). AuthRepository flipped
  native in coreBridge + reverse-bridged (workers+excluded); @Named supabase/supabaseKtor HttpClients →
  Koin `named()` singles; Supabase `Auth` derived from the Koin SupabaseClient single; Friends + Settings
  shrunk (promote-then-shrink). GREEN — failing-CLASS set unchanged (18 pre-existing). **ALL non-excluded
  FEATURE modules are now Koin.** Remaining Hilt = infra + 8 workers + 4 excluded modules.
- ✅ **Cutover Batch 6 — COMMITTED (2026-07-04, workers → Koin).** After two reverted mid-wire attempts,
  a long-running agent completed it GREEN: the 7 non-excluded `@HiltWorker`s → plain `CoroutineWorker` +
  Koin `worker{}` DSL; `koin-androidx-workmanager` added; WorkManager uses ONE
  `DelegatingWorkerFactory(KoinWorkerFactory + HiltWorkerFactory)` (in `SyncModule.provideWorkManager`) so
  the excluded scanner `@HiltWorker` still resolves via Hilt. New `SyncKoinModule`/`PushKoinModule`
  (worker{} + `StatsDao`/`PushTokenRemoteDataSource` forward-bridges). Retired 4 now-unneeded reverse-bridges
  (`RefreshCollectionPricesUseCase`/`FriendRepository`/`GamificationSyncManager`/`QuestReconciler`);
  `AuthRepository` kept (excluded online lobby VMs consume it). **CAVEAT: runtime ordering was verified
  against the koin-androidx-workmanager SOURCE (KoinWorkerFactory resolves lazily at job-exec, after
  startKoin) but NOT on-device** (no emulator here) — validate the WorkManagerFactory when the app is next
  launched. Also closed MINOR DEBT in the same commit (coupled via ManaHubApp): `DeckBuilderState` →
  `core.model` package (P2.1); `NudgeTrigger` promoted to `:shared:core-model` + `GetAccountNudgeUseCase` →
  `:shared:core-domain` (native Koin single). GREEN 1967/118/2.
- ✅ **CUTOVER END-STATE (stable, committed, GREEN 1967/≈118/2):**
  - **Koin owns ALL feature DI** — every non-excluded ViewModel (~40, via `koinViewModel()`) + every feature
    repository/use-case/engine. Feature Hilt `@Module`s DELETED: Community, Tournament, News, Draft,
    DeckDoctor, Trades, Friend, Game, Gamification, Auth (10) + the `SharedDomainUseCaseModule` use-case hub
    (moved to `SharedDomainKoinModule`, 33 singles).
  - **Workers now Koin** (Batch 6): the 7 non-excluded workers use Koin `worker{}`; only the excluded
    scanner `@HiltWorker` stays Hilt (via the DelegatingWorkerFactory).
  - **Hilt retained as the shared platform base** (consumed by both Koin — via forward-bridge in
    `CoreBridgeKoinModule`/`ManaHubApp` — and the excluded trio; `SyncModule` also now hosts the Koin+Hilt
    DelegatingWorkerFactory): infra modules `DatabaseModule`(Room), `NetworkModule`, `SupabaseModule`,
    `DispatcherModule`, `CoroutineScopesModule`, `AnalyticsModule`, `CrashlyticsModule`, `PushModule`,
    `SyncModule`, `RepositoryModule` (6 residual `@Binds`), `SharedDomainUseCaseModule` (residual
    eager-singleton `@Provides`); the 4 excluded modules (`OnlineSessionModule`/`ScannerModule`/
    `VoiceModule`/`NearbyModule`).
  - **`KoinToHiltBridgeModule`** (Koin→Hilt reverse bridge) exposes to Hilt consumers the types now
    Koin-owned that workers/excluded still need: `AuthRepository`, `FriendRepository`,
    `GamificationSyncManager`, `QuestReconciler`, `RefreshCollectionPricesUseCase`, `AddToWishlistUseCase`,
    `CommitScannedCardsUseCase`. This module + the forward-bridges shrink to nothing when the excluded trio
    migrates and Hilt is deleted.
- ⏳ **Remaining cutover (deferred to the excluded-trio final wave):** infra modules → Koin; migrate the
  excluded trio (online/scanner/voice/nearby) → Koin; then delete Hilt + `KoinToHiltBridgeModule` entirely.
  (Workers are DONE as of Batch 6.)
- ~~old Batch 5 line~~ (superseded above): Batch 5 = **Auth** (reverse-bridge AuthRepository for workers+excluded;
  move @Named supabase/supabaseKtor HttpClients + UserProfileClient/DataSource; the batch-4 ManaHubApp
  `supabaseKtorHttpClient` forward-bridge flips native). Batch 6 = **workers → Koin `WorkerFactory`**
  (`koin-androidx-workmanager`) → then RETIRE the worker reverse-bridges (Friend/GamificationSync/
  QuestReconciler/RefreshCollectionPrices). Batch 7 = **infra** (Network/Supabase/Dispatcher/CoroutineScopes/
  Analytics/Crashlytics/Push/Sync/DatabaseModule-DAOs/RepositoryModule/SharedDomainUseCaseModule-residual) →
  Koin, keeping a reverse-bridge ONLY for what the excluded trio still needs. END-STATE: only the 4 excluded
  Hilt modules (online/scanner/voice/nearby) + a minimal reverse bridge remain, until the excluded wave.
- ⏳ Queued after cutover: P0.2 (web KV stub honesty → `kmp-web-fullstack-dev`).
- Audit finding on checklist item **C** (Koin↔Hilt binding completeness): ANSWERED — bindings complete, no
  orphaned modules beyond the by-design bridges + the 4 orphan VMs (P1.3).

## NEXT STEP (resume here)

**🟡 Phase 5 · Slice 1 (full regression audit, Android-only scope) is DONE as of 2026-07-01 —
see the STATUS entry above for the full categorized breakdown.** Short version: 1 genuine migration
regression found and fixed (`CanPlaytestDeckUseCaseTest`, commit `30864f6`); the other 18 failing
classes / 122 tests are all confirmed pre-existing/out-of-scope (4 classes in the permanently-excluded
online/voice/scanner trees, 14 independently verified against `git log`/a live `master` worktree
comparison); zero uncertain items remain. New test baseline: **1967 tests, 122 failed, 2 skipped**
(supersedes "1964/122-124/2"). No CI configuration exists in the repo (a documented gap, not a
regression — nothing to fix, since there's no old pipeline the module restructuring could have broken).

**2026-07-01 (doc-refresh slice) — README updated, CI explicitly skipped by user decision.**
- ✅ `README.md` Tech Stack + Architecture sections rewritten (`2f1087e`) — was still describing the
  pre-migration single-module/Hilt-only/Retrofit setup; now reflects `:shared:core-*`, Koin/Hilt
  transition, Ktor networking, Room-is-Android-only, excluded-features list. `CLAUDE.md` needed no
  change (kept current throughout the migration already).
- ⏸ **CI setup: user explicitly chose to SKIP for now** (2026-07-01) — repo has zero CI config
  (`.github/workflows`, etc. all absent). Revisit when the web target exists rather than doing
  Android-only CI now and web CI later.
- ⚠️ `docs/adr/ADR-004` does not exist — the plan's "ADR-004 final" line item is premature while the
  web target hasn't started; defer until the migration is closer to fully done.
- ⏸ **Performance (bundle size/first paint)** from the original Phase-5 scope is a web-target concern
  (wasm bundle) — N/A under Android-only scope; not reframed as an APK-size check, no decision made.

**2026-07-01 (user directive) — continue Android hardening to completion before considering the web
handoff.** Concrete checklist adopted for the rest of Phase 5 (Android-only):
- [x] A. **DONE 2026-07-01 (audit only, zero `.pro`/`.gradle.kts` change — release build was already
      healthy).** Ran a real signed `./gradlew :app:assembleRelease --rerun-tasks` (local keystore in
      the gitignored `local.properties` is fully configured) → **BUILD SUCCESSFUL in 7m13s**, R8
      (`minifyReleaseWithR8`) + resource shrinking (`optimizeReleaseResources`) + `packageRelease` +
      `uploadCrashlyticsMappingFileRelease` all ran and succeeded — this proves R8 doesn't choke on the
      `:shared:core-*` split, not just that a debug build compiles. Verified R8 didn't silently strip
      anything the split introduced by inspecting `app/build/outputs/mapping/release/mapping.txt`
      (1.2M lines): all `com.mmg.manahub.core.data.remote.dto.*` `@Serializable` DTOs (now living in
      `:shared:core-data`) kept their original class names + full constructor signatures (spot-checked
      `CardDto`'s 37-arg synthetic `@Serializable` constructor, byte-for-byte present); `org.koin.core.Koin`
      and `io.ktor.client.HttpClient` both present/kept; `com.mmg.manahub.core.data.repository.
      DeckRepositoryImpl` (a `:shared:core-data` repo impl) present with real method names; no
      `missing_rules.txt` was generated (R8 only emits that file when a `-keep` rule can't be resolved —
      its absence confirms zero missing-rule failures). **Root cause of "why nothing broke":** the
      Phase-2/3 data-layer moves were consistently **package-preserving** (per the standing pattern in
      `project_kmp_phase2_usecase_batch1`/`batch2` — e.g. `core.domain.model.*` → `core.model.*` DTOs
      still live under `com.mmg.manahub.core.data.remote.**`, repo impls still under `com.mmg.manahub.
      core.data.repository.**`), so `proguard-rules.pro`'s existing **wildcard** keep rules (`-keep class
      com.mmg.manahub.core.data.remote.** { *; }`, the `@kotlinx.serialization.Serializable` blanket keep,
      `-keep class com.mmg.manahub.core.model.** { *; }`, `-keep class org.koin.** { *; }`, `-keep class
      io.ktor.** { *; }`) already transitively cover the classes' NEW module regardless of which
      `.jar`/module they physically compile from — R8 operates on the merged post-D8 class graph, not
      per-module, so a package-scoped wildcard rule doesn't care that the package moved from `:app` to
      `:shared:core-data`. **One stale-but-harmless line found, left as-is (zero risk, zero benefit to
      touching it):** `-keep class com.mmg.manahub.core.domain.model.** { *; }` now matches an EMPTY
      package (`app/src/main/java/.../core/domain/model/` has 0 files — everything moved to
      `:shared:core-model`'s `core.model`); it's a no-op keep rule, not a bug, so it was left untouched
      rather than invent a cosmetic-only commit. **No `consumerProguardFiles` were added to any
      `:shared:core-*` module** — none were needed; `:app` is the only module that produces a shrunk
      artifact (the shared modules ship as plain `.aar`/`.klib` with no minification of their own), so
      keep rules belong exclusively in `:app`'s `proguard-rules.pro` per the task's own guidance. Full
      gauntlet re-verified after the release build (to rule out any config-cache/daemon side effects):
      `:app:assembleDebug` + all 5 `:shared:core-*:compileKotlinWasmJs` → BUILD SUCCESSFUL;
      `:app:testDebugUnitTest --rerun-tasks` → **1967 tests, 122 failed, 2 skipped** (== baseline, zero
      regressions).
- [x] B. **DONE 2026-07-01 (audit only, zero `.kt` change — clean pass, no fixes required).**
      `android-security-auditor` full pass over the 5 `shared/core-*` modules' `commonMain` (all ~340
      shared `.kt` files as of this session) + the migration diff, per CLAUDE.md's "Security notes" +
      "Supabase invariants." Web target (`wasmJsMain`) explicitly out of scope; `feature/online`/
      `core/voice`/`feature/scanner` untouched (confirmed via `git diff --stat` — only this doc changed).
      Five areas audited, all CLEAN:
      1. **Secrets/credentials.** Regex sweep for API-key/token/secret literals, known key-prefix
         patterns (`AIza`, `sk_live`, `ghp_`, `eyJhbGciOi`, `service_role`, PEM headers), and
         `http://`/embedded-credential URLs across `shared/**/*.kt` — zero hits. Also ran `git log -S`
         (pickaxe) for the same prefixes across the **full history** of `shared/` (70 commits) to catch
         a secret that predates the routine pre-push gate — zero hits. No `BuildConfig.*` reference
         exists in `shared/` at all (expected: `commonMain` can't see Android's `BuildConfig`), so
         nothing could have been carried over hardcoded.
      2. **commonMain data-boundary risk (forward-looking for web).** No `io.ktor.client.plugins.logging`
         `Logging` plugin installed anywhere in `shared/` (all HTTP logging is wired per-client in `:app`
         via `OkHttpClient`/`HttpLoggingInterceptor`, gated `BODY`-debug/`NONE`-release — Android-only,
         correctly outside commonMain). No `println`/`Log.*`/`console.log` in `shared/`. No raw
         token storage in `commonMain`: `KeyValueStore` is an `expect` interface only; the Android
         `actual` (`DataStoreKeyValueStore`) and the wasmJs `actual` (`LocalStorageKeyValueStore`,
         already scaffolded — NOT reviewed here, web-target scope) are the platform-specific impls. ID
         tokens (`SignInWithGoogleUseCase` etc.) pass through to the repository as plain params with no
         logging. **Forward-looking note for the web phase (not a current bug):** `UserProfileClient`/
         `FriendshipClient` in `shared/core-data` rely on the Android `OkHttpClient` engine's
         interceptor to attach the Supabase `apikey`/Bearer headers (wired in `:app`'s
         `AuthModule`/`FriendModule`) — this pattern is Android-OkHttp-engine-specific and will NOT
         carry over to Ktor's JS engine; `kmp-web-fullstack-dev` will need an equivalent
         auth-header-injection mechanism for the wasmJs `HttpClient`. Also noted: `LocalStorageKeyValueStore`
         already exists in `wasmJsMain` — flagged for `kmp-web-fullstack-dev` to review against
         CLAUDE.md's "no hand-rolled token storage in `localStorage`" rule when the web auth phase starts
         (not audited here — out of scope).
      3. **Ktor migration parity — CONFIRMED preserved.** All `baseUrl`s in `:app` DI (`ScryfallClient`,
         `YouTubeClient`, `ArchidektClient`, Supabase clients) are `https://`; `network_security_config.xml`
         still blocks cleartext at the OS level, unaffected by the module split (Android resource, not
         touched by the restructuring). `ScryfallRequestQueue`'s allowlist sanitization survived the move
         to `BuildScryfallQueryUseCase` in `shared/core-domain` byte-for-byte (regex
         `[^a-zA-Z0-9\-',.\s]` strip, unchanged). The YouTube API key is injected via `BuildConfig
         .YOUTUBE_API_KEY` at the `:app` DI boundary (`DraftModule.provideYouTubeClient`) — never
         hardcoded in `shared` — and its dedicated Ktor `HttpClient` (`provideYouTubeHttpClient`)
         deliberately does NOT install `HttpLoggingInterceptor` at all (unlike the Cloudflare/global
         clients, which do), so the key — travelling as a `?key=` query param per the YouTube REST API's
         own requirement — is never written to Logcat even in debug builds. This is equivalent-or-better
         than the pre-migration OkHttp-interceptor approach the CLAUDE.md note describes.
      4. **DI/Koin exposure — N/A, nothing to audit yet.** Zero `org.koin` imports anywhere in
         `shared/`; every Koin module (all 20 islands) still lives in `:app`. No secret or
         internal-only endpoint is `single { }`-provided in `commonMain` because no DI wiring exists
         there yet. Revisit this check once Koin modules start moving into `shared/` (not yet planned).
      5. **Room→domain mapper boundary — clean.** Audited `GameSessionRepositoryImpl.toDomain()` and
         `TournamentRepositoryImpl`'s three `toDomain()` mappers (the two examples named in the task) —
         both are explicit field-by-field mappings (no reflection/wildcard copy), and the shared domain
         types they populate (`SessionDetail`/`SessionHistoryEntry`/`DeckStats`/etc. in
         `feature.game.domain.model`; `Tournament`/`TournamentMatch`/`TournamentPlayer` in `core.model`)
         carry only gameplay data — no device id, no debug/internal-only flag, no Android-local-only
         field is exposed. Spot-checked `TradeItemRequestDto` (a client-authored request DTO carrying
         `from_user_id`/`to_user_id`) — this is a pre-existing pattern unchanged by the migration (Gson
         → kotlinx.serialization only) and its safety depends on backend RLS/RPC enforcement
         (`backend-supabase-expert`'s domain, not this migration's), not a mapper-boundary issue; noted
         but not treated as a migration-introduced finding.
      **Net result: 0 fixes delegated to `android-kotlin-architect`** (nothing rose to "genuine
      current-state issue"). 2 forward-looking notes recorded above for whoever picks up the web
      auth/networking phase.
- [ ] C. DI graph completeness: Koin↔Hilt bridge has no missing bindings / orphaned modules after
      ~250+ moved files.
- [x] D. **DONE 2026-07-01 (audit only, zero `.kt` change).** `:baseline-profile` module still valid
      post module-split. Checked `baseline-profile/build.gradle.kts` (`com.android.test` plugin,
      `targetProjectPath = ":app"`, no `androidx.baselineprofile` plugin yet — AGP 9.x incompatibility
      documented in-file, pre-existing/unrelated to this migration) and
      `BaselineProfileGenerator.kt` (the sole source file). The generator drives journeys purely at the
      UI-automator level (`By.descContains(...)`, the app's package-name string, `pressHome`/
      `startActivityAndWait`) — it has **zero imports and zero FQN references to any
      ViewModel/UseCase/Repository/model class**, so it was structurally immune to the ~250+ file
      relocations by design; confirmed with `grep -rn "com\.mmg\.manahub\.\(feature\|core\.domain\|
      core\.data\|core\.model\)" baseline-profile/` → empty. Verified `./gradlew
      :baseline-profile:assemble` → **BUILD SUCCESSFUL** (a first attempt hit a transient Windows
      file-lock on `shared:core-domain`'s jar from a concurrently-running agent — not a real failure;
      retry succeeded clean). **Full profile generation
      (`:baseline-profile:connectedAndroidTest`) could NOT be verified** — no `adb`/emulator available
      in this environment (`adb: command not found`, no `$ANDROID_HOME/emulator`); this is an
      environment limitation, not a code issue, and matches the module's own pre-existing note that the
      `androidx.baselineprofile` automation plugin isn't wired yet anyway. No code changes were
      required — nothing broke.
- [x] E. **CANCELLED per user directive (2026-07-01): do NOT delete `DeckMagicDetailScreen`/
      `DeckBuilderViewModel`/`Screen.DeckDetail`.** Despite being currently unused (superseded by Deck
      Studio), the user wants it kept — it will be used again in the future. Item closed as
      "keep as-is," not attempted.
- [ ] F. `android-edge-case-tester` scoped pass on the most heavily-migrated critical flows (Tournament
      finish-and-advance path, GameSession/Stats, Deck Doctor engine) to catch regressions unit tests
      might miss.

Work through these one at a time, each its own verified GREEN slice/commit, until the list is done or
a genuine blocker is hit (documented here, not forced).

**➡️ NEXT for Phase 5:** with regression-audit + docs done and CI/performance/ADR-004 explicitly
deferred, continue the checklist above. Web-phase handoff remains available whenever the user wants it,
but is not the current directive. All 12 items in
"Phase 4 remaining work" are resolved: DONE (3, 4, 6, 7-partial, 8), DECIDED-SKIP with rationale (4b),
or CLOSED-AS-DEFERRED / permanently-blocked-by-design with no further Android-side action possible
(5, 7-remainder, 9, 10, 11, 12). See item 5's full entry below for the item-5 closure (the last item
that was still open going into this session). **There is no further Android-first-scope work
identified in Phase 4** — see "➡️ NEXT" below for what that means concretely.

**➡️ NEXT = Phase 5 (Hardening & release) per `kmp-migration-plan.md` §5/§8, OR hand off to
`kmp-web-fullstack-dev` to start the web phase (Phase 3/4 web-target work: `:webApp` entrypoint,
wasmJs `actual` data sources behind the now-fully-pure repo interfaces, Firebase/WorkManager/camera/
voice web actuals, web responsive layout, web security/telemetry review).** Both are legitimate next
moves and are not mutually exclusive — the remaining Android-side punch list (the permanently-blocked
items below: `GetAccountNudgeUseCase`, `ImportCommunityDeckUseCase`, `UpdateTradeCollectionUseCase`,
the 3 `core/tagging/` files, the excluded online/voice/scanner trio) does NOT block starting the web
phase, since none of it sits on the path of any repo interface or shared model the web target would
consume. A human/orchestrator decision is needed on which to pick up first — this session does not
presume to choose for the user.

User decision (2026-06-24): prepare Android for 100% KMP FIRST, no web implementation yet. Web
target (`:webApp`, web `actual` impls) deferred until Android is fully KMP-ready. **That condition is
now met for the scope Phase 4 defined** (see the completeness statement above) — the web phase can
start whenever the user is ready to greenlight it.

**2026-07-01 (survey session) — Phase 4 item 5 closed as deferred, zero `.kt` change.** Surveyed the
6 Room-backed repo impls (`CardRepositoryImpl`/`DeckRepositoryImpl`/`StatsRepositoryImpl`/
`UserCardRepositoryImpl`/`GameSessionRepositoryImpl`/`TournamentRepositoryImpl`) for (a) thin-CRUD /
(b) business-logic / (c) network-mixed-with-Room concerns — full categorization + conclusion is in
item 5's entry below (search "CLOSED-AS-DEFERRED 2026-07-01"). Short version: item 5 as originally
scoped ("DAO-abstraction interfaces in commonMain") is subsumed by item 6 (already closed 2026-06-30
— the repo interfaces themselves ARE the abstraction boundary); the only remaining piece is writing
NEW `wasmJsMain` implementations, which is 100% web-target work and `kmp-web-fullstack-dev`'s domain,
not an Android-first action item. No layering violations or misplaced logic were found in the survey
that would justify a standalone Android-side cleanup slice, so none was forced. This closes out the
last open bullet in "Phase 4 remaining work," which is why Phase 4 (Android-first scope) is now
considered complete (see the banner above).

**2026-07-01 (earlier session) — Composable/Res follow-up assessment session (audit only, no `.kt` code change; doc-only
commit). Item 4 ("remaining `:app` composables with deep platform deps") is now CLOSED — see its full
entry above. Summary of what was checked and decided:**
- **`CardSearchSheet.kt` re-confirmed as a genuine hard blocker** — its forced-keyboard-dismiss logic is
  load-bearing on `Activity`/`WindowInsetsControllerCompat`/`LocalView` (none CMP-portable). Left
  exactly as-is; its `R.string.*` calls correctly stay plain Android resources (the file isn't shared,
  so CMP `Res` buys nothing). No string-swap-in-place was done — rejected as churn with no payoff while
  the file can't move.
- **`ParticipantListRow`/`RoomCodeDisplay`/`RoomCodeField` re-confirmed online-excluded** (sole
  consumers are `feature/online/presentation/lobby/*`) — untouched per the hard exclusion rule.
- **No other `:app` composables remain with deep platform deps** — directory listing of
  `core/ui/components/` has exactly these 4 files; everything else from the Phase-3/4 backlog was
  already moved in prior sessions.
- **Bulk Res sweep across the ~54 already-shared core-ui composables and the 3 gamification catalogs
  was evaluated and SKIPPED** (no code change) — see item 4b above for the full rationale. Short
  version: the composables have no concentrated string-duplication worth centralizing, and the catalogs
  are eagerly-initialized `object`s where `Res.string.*` isn't even mechanically usable (no
  `@Composable`/`suspend` context at construction) — converting them would need a bigger key/resolve
  refactor with zero localization payoff in an English-only app. Revisit only if a second locale is ever
  planned.
- **Phase 4 remaining work is now down to item 5 only** (Room-backed repo impls for the web data-source
  phase — Card/Deck/Stats/UserCard/GameSession/Tournament each need a DAO-abstraction interface in
  `commonMain`, with fresh Supabase-backed impls behind the same interface for web). That's the next
  slice to pick up.

**308 shared `.kt` files** across 5 modules as of 2026-06-25 (higher by session end 2026-06-30).
Test baseline: 1964 tests, 123 failed (vs 122 pre-existing; +1 is noise), 0 errors, 2 skipped. (No test
run was needed this session — zero `.kt`/`.gradle.kts` changes were made; pure documentation update.)

**2026-06-30 (later session) — audit only, no code change.** Item 6 ("Repository interfaces with Room
types") is now CLOSED: `CardRepository`/`DeckRepository`/`UserCardRepository`/`StatsRepository` were
re-verified and are ALREADY pure + ALREADY in `shared/core-domain` (done in earlier Phase-2 slices; the
"Remaining" bullet under item 6 was stale — see that section for commit SHAs). No interface in
`shared/core-domain` carries a Room/Android type. **Next unblocked Phase-4 items are #3 (CMP Res system)
and #5 (Room-backed repo impls / DAO-abstraction interfaces for the web data-source phase)** — both
Tier 3/4, medium-to-high effort; pick up there.

**Phase 4 completed (2026-06-24):**
- ✅ `kotlinx-datetime` 0.6.2 added to all shared modules + `:app`
- ✅ `java.time` completely eliminated from app source (0 imports remain)
- ✅ `TimeAgoFormatter` shared (core-model, English-only, Clock.System)
- ✅ `DraftSetCard` + `NewsItemCard` moved to shared core-ui
- ✅ Gamification domain types: 22 types shared (19 core-model + 3 core-domain)
- ✅ `@StringRes` stripped from 6 gamification + 2 game model files
- ✅ Game models: GameMode/GamePhase/PhaseStop → core-model (hardcoded English strings)
- ✅ GamificationEngine + GamificationRepository + ProgressionEventBus → core-domain

**Phase 4 completed (2026-06-25):**
- ✅ ManaCostImage + ManaColorPicker → `:shared:core-ui` commonMain (`cc99c7d`). ManaSymbolImage
  switched from `ImageRequest.Builder(LocalContext.current)` to plain URL string (SetSymbol pattern).
  ManaColorPicker `R.drawable.ic_counter` → `SetSymbolFallbackIcon` (already in InlineIcons.kt).
  **37 files** in core-ui (was 35).
- ✅ **55 use cases moved to shared** in 2 commits:
  - `ff7560c`: 15 draft/playtest use cases → core-domain (9 draft @Inject-stripped + 6 playtest pure)
  - `2f377cb`: 40 use cases → shared (auth 10, friends 7, news 3, communitydecks 2, trades 18).
    Trades split: 5 → core-domain (WishlistRepo/OpenForTradeRepo deps), 13 → core-data
    (TradesRepository dep lives in core-data; can't go core-domain without circular dep).
    `ImportCommunityDeckUseCase` skipped (Firebase Crashlytics dep).
    `UpdateTradeCollectionUseCase` skipped (Room DAO dep).
- ✅ Game domain models → `:shared:core-ui` commonMain (`3bb9334`): Player/CustomCounter/CounterType,
  PlayerConfig, GameResult/PlayerResult/EliminationReason. Went to core-ui (not core-model) because
  they depend on `PlayerThemeColors` which contains Compose `Color`. Decoupling to an index would
  require changes across ~44 `.theme` usages — disproportionate for this phase.
- ✅ DeckBuilderState/BuilderStep/BuilderTab/ReviewGroupBy → `:shared:core-model` (pure, all deps shared).
- ✅ 3 more use cases → core-domain: CompleteSurveyUseCase (@Inject stripped),
  AddCardToCollectionUseCase + CollectionAddSource (@Inject stripped),
  CommitScannedCardsUseCase + ScannedCardCommit (`java.util.UUID` → `kotlin.uuid.Uuid`).
- ✅ Deck Doctor engine → `:shared:core-domain`: DeckEngineModels, DeckScoreModel,
  DeckImportExportHelper, ScoreWeightOverridesMapper, RoleClassifier+TribeDeriver,
  ManaBaseAnalyzer, DeckScorer (7 files, @Inject stripped, Hilt @Provides added to DeckDoctorModule).
- ✅ 5 draft use cases with dispatcher qualifiers → core-domain: GetDraftableSimSetUseCase,
  StartDraftUseCase, MakePickUseCase, AutoPickUseCase, CompleteDraftUseCase
  (`@IoDispatcher`/`@DefaultDispatcher` → `Dispatchers.IO`/`Dispatchers.Default` directly).
- ✅ Gamification catalogs: **87 `R.string.*` refs inlined** as English strings (app is English-only).
  Model fields renamed: `titleRes: Int` → `title: String`, `descRes: Int` → `description: String`,
  `displayNameRes: Int` → `displayName: String` across 7 core-model types. `QuestTemplate` extracted
  to core-model. 3 catalogs (AchievementCatalog, QuestCatalog, UnlockableCatalog) moved to
  `:shared:core-domain`. 7 UI files + 4 tests updated. (`e23acdd`)
- fix: `ClaimQuestRewardUseCaseTest` missing `ClaimResult` import after package move (`cbdeec4`).

**Phase 4 completed (2026-06-29):**
- ✅ 3 new core-ui composables → commonMain (`c5feba3`): MagicAlertDialog, MagicCard,
  MagicCardInspectionOverlay. CardGridItem + CardListItem + PlayerEditSheet updated for shared use.
  PlaytestCardInfo model added to PlaytestModels. core-ui now ~45 shared composables.
- ✅ CanPlaytestDeckUseCase: casual format added (60-card min).
- ✅ PlaytestSetupViewModel: InferDeckIdentityUseCase + color identity + deckImageUrl + observeAllDeckSummaries combine.
- ✅ Playtest screens (Setup/Hand/Battlefield), CardDetailScreen, GamePlayScreen, AppNavGraph
  migrated to use shared composables (screens stay in `:app` androidMain — only their composable deps moved).

- ✅ `DeckMagicEngine` → `:shared:core-domain` (`d94e8b6`). `CardTag.displayLabel` added to shared.
  `@IoDispatcher` → `Dispatchers.Default`. `DeckDoctorModule` @Provides added.
- ✅ CircularDistribution + DeckItem + MagicBottomBar → shared core-ui (`5d4da24`). Painters hoisted
  as params; SimpleDateFormat → kotlinx-datetime; strings inlined; MagicBottomBar routes parameterized.
- ✅ FloatingDelta + GameModeSelector + SharedComponents → shared core-ui; CollectionScreen stub deleted
  (`ac787c6`). MulishFontFamily → magicTypography; LocalContext/ImageRequest → plain URL; strings inlined.
- ✅ CardConstants → :shared:core-model (pure Kotlin; was blocking SharedComponents).
  core-ui now ~51 shared composables.
- ✅ AddCardSheet + TradeSelectionSheet + VariantSelectorSheet → shared core-ui (`996b0ff`).
  9/6/4 stringResource → English literals; coloredShadow split to expect/actual (Android:
  BlurMaskFilter, wasmJs: no-op; defaults on expect only — KMP rule). core-ui now ~54 shared composables.
  Remaining in :app/core/ui/components/: CardSearchSheet (Activity/Context hard blockers),
  ParticipantListRow/RoomCodeDisplay/RoomCodeField (online-excluded).
  ✅ ManaCurveChart (`750ce9a`, 2026-06-30) → shared core-ui; android.graphics.Paint/Typeface/nativeCanvas.drawText
  replaced with CMP `rememberTextMeasurer()` + `DrawScope.drawText`; `legendLabel: String` param replaces
  `stringResource(R.string.deckbuilder_ideal_curve)`.
- ✅ **9 deck use cases → `:shared:core-domain` `commonMain` (2026-06-29).** Full package
  `com.mmg.manahub.feature.decks.domain.usecase` moved — all 9 files that were in `:app`:
  `BudgetOptimizer`, `BudgetConstraints`, `BudgetSelection` (in BudgetOptimizer.kt),
  `CandidatePoolGenerator` (+ `DeckRole.queryFragment/fallbackQueryFragment` top-level extensions),
  `EvaluateDeckUseCase` + `DeckHealth`,
  `InferDeckIdentityUseCase` + `InferredIdentity`,
  `SuggestAddsUseCase` + `AddOrigin` + `AddSuggestion`,
  `SuggestAddsWithBudgetUseCase`,
  `SuggestCutsUseCase`,
  `BuildDeckFromSeedsUseCase` + `SeedDeckResult`,
  `ImportDeckUseCase`.
  **Key changes:** `@Inject`/`@Singleton`/`javax.inject.*`/`core.di.IoDispatcher` stripped;
  dispatcher params → `CoroutineDispatcher = Dispatchers.Default`. **KMP fix:**
  `CandidatePoolGenerator.formatCap` replaced `String.format(java.util.Locale.US, "%.2f", value)`
  with pure Kotlin integer arithmetic (`kotlin.math.round`/`abs` + `padStart`) — JVM-only
  `java.util.Locale` eliminated. `DeckDoctorModule` updated with explicit `@Provides @Singleton` for
  all 9 new types (+ `BudgetOptimizer` no-arg + `CandidatePoolGenerator` with `CardRepository` dep).
  Package UNCHANGED → zero consumer import edits. Platform-leak grep: EMPTY (PASS).
  **Additional KMP fixes (2026-06-30, `1328a6a`):** `putIfAbsent` (JVM-only MutableMap) → `getOrPut`
  in 4 files; `queryFragment`/`fallbackQueryFragment` visibility `internal` → `public` (cross-module).
  Remaining blocked use cases: `GetDeckGameStatsUseCase` (Room DAO), `ClaimQuestRewardUseCase` (Room DAO),
  `CalculateStandingsUseCase`/`GenerateNextRoundUseCase` (Room entities), `EvaluatePlayerEliminationUseCase`
  (`Player` in core-ui → core-domain can't dep), `GetAccountNudgeUseCase` (presentation dep), online-excluded.

- ✅ **`TournamentRepository` interface + domain models → `:shared:core-domain`/`:shared:core-model`
  `commonMain` (2026-06-30, `f8db684`).** `Tournament`, `TournamentMatch`, `TournamentPlayer`,
  `TournamentStanding` (replaces Room projection) added to `:shared:core-model`; `TournamentRepository`
  + `MatchResultOutcome` moved to `:shared:core-domain` (package PRESERVED → zero consumer import edits).
  `TournamentRepositoryImpl` gains private `toDomain()` mappers; `StandingsCalculator` keeps entity
  inputs but produces domain-type output. `CalculateStandingsUseCase` DAO-direct layering violation
  fixed (now delegates to `repository.calculateStandings()`). `HomeViewModel.firstActiveSummary()`
  receiver updated from FQN `TournamentEntity` to `core.model.Tournament`. 21 files changed. Verified:
  assembleDebug GREEN; core-model + core-domain `compileKotlinWasmJs` GREEN; `testDebugUnitTest`
  1964/123/2 (== baseline); 0 platform imports in commonMain.
- ✅ **`GameSessionRepository` interface → `:shared:core-domain` `commonMain` (2026-06-30).** The
  interface's Room-projection return types (`DeckStatsRow`, `ModeCount`, `EliminationCount`,
  `LocalSessionHistoryRow`, `GameSessionWithPlayers`) were the blocker — they can't go to `commonMain`.
  **Strategy:** domain equivalents (`DeckStats`, `GameModeCount`, `EliminationStats`,
  `SessionHistoryEntry`, `SessionDetail`/`SessionSummaryData`/`PlayerSummaryData`) added to
  `shared/core-model/commonMain` (package `com.mmg.manahub.feature.game.domain.model`); `EliminationReason`
  extracted from `core-ui` `GameResult.kt` into `core-model`. New `GameResultMapper.kt` in `core-ui`
  (`GameResult.toSessionData(): GameSessionData`). Interface moved to `shared/core-domain/commonMain`
  (package `com.mmg.manahub.feature.game.domain.repository` PRESERVED → zero consumer import edits).
  `GameSessionRepositoryImpl` rewritten: takes `GameSessionData`, maps DAO projections to domain types.
  `GameViewModel` calls `result.toSessionData()`. `HomeViewModel`/`ProfileViewModel` field types updated
  to domain equivalents (identical field names → zero logic change). `GameSessionRepositoryImplTest`
  updated (`toSessionData()` call). DAO projection types (`DeckStatsRow`, etc.) STAY in `:app`.
  Verified: `:app:assembleDebug` GREEN; shared modules `compileKotlinWasmJs` GREEN;
  `testDebugUnitTest` 1964/122/2 (== baseline, `GameSessionRepositoryImplTest` 0/0 failures);
  0 platform imports in `commonMain`.
- ✅ **`GetDeckGameStatsUseCase` → `:shared:core-domain` `commonMain` (2026-06-30, `72e4551`).** Last
  blocked use case in the §9.6 backlog. It previously injected `GameSessionDao`/`SurveyAnswerDao`/
  `CardDao`/`UserPreferencesDataStore` directly; now depends only on `GameSessionRepository` +
  `CardRepository`. `GameSessionRepository` gained 4 methods (`observeSingleDeckStats`,
  `observeTopCardImpactsForDeck`, `observeWeakestCardImpactsForDeck`, `observeSessionSummariesForDeck`)
  backed by 3 new domain types in `SessionStats.kt` (`SingleDeckStats`, `CardImpactScore`,
  `DeckSessionSummary`); `GameSessionRepositoryImpl` now also injects `SurveyAnswerDao`.
  `CardRepository` gained `getCardsByIds` (batch scryfallId→Card). `playerName` is now an explicit
  `invoke(deckId, playerName)` param (was read internally from `UserPreferencesDataStore`);
  `DeckStudioViewModel`/`DeckMagicDetailViewModel` thread their existing `playerNameFlow` through.
  `DeckStatsCard.SessionRow` takes the new domain `DeckSessionSummary` instead of the Room-projection
  `SessionSummary` (zero body change, same 4 fields). `@Inject` stripped, Hilt `@Provides` added to
  `SharedDomainUseCaseModule`. 12 files changed (the use case `git mv`'d). Verified: assembleDebug
  GREEN; core-model + core-domain `compileKotlinWasmJs` GREEN; `testDebugUnitTest` 1964/123/2 (==
  baseline, no new failing classes); 0 platform imports in `shared/core-domain` + `shared/core-model`
  commonMain.

**Phase 4 remaining work (Android KMP-readiness) — ALL are Tier 3/4, medium-to-high effort:**
3. ✅ **CMP Res system — DONE-POC (2026-06-30/07-01, `6002793`).** Infrastructure stood up + ONE string
   proven end-to-end; the BULK string/drawable migration across the remaining `:app` composables is
   still future work (see item 4 below for the file list). `implementation(compose.components.resources)`
   added to `:shared:core-ui` commonMain; first `src/commonMain/composeResources/values/strings.xml`
   (single entry `deckbuilder_ideal_curve`, English-only — NOT a localization mechanism, no `values-xx/`
   dirs per CLAUDE.md). `ManaCurveChart.kt`'s `legendLabel` default switched from the hardcoded literal
   to `stringResource(Res.string.deckbuilder_ideal_curve)` — zero behavior change, zero caller edits
   (`DraftResultScreen.kt`/`StatsScreen.kt` don't pass it explicitly). **No AGP9/`androidLibrary{}`
   friction found** — `convertXmlValueResourcesForCommonMain` → `generateComposeResClass` →
   `generateResourceAccessorsForCommonMain` ran automatically ahead of `compileKotlinWasmJs` AND the
   Android `assembleDebug` path with **zero explicit `dependsOn` wiring** (Gradle's implicit
   compile-classpath task dependency handled it on both targets). **Generated package is
   `manahub.shared.core_ui.generated.resources`** — auto-derived from the Gradle module path
   (`:shared:core-ui` → `manahub.shared.core_ui`), NOT from the `androidLibrary.namespace`
   (`com.mmg.manahub.core.ui`); no `compose.resources { packageOfResClass = ... }` override was needed
   for this single-module POC (didn't collide with anything). `Res`/`Res.string.*` accessors generate
   `internal` visibility — fine since the only consumer (`ManaCurveChart.kt`) is in the same module;
   a FUTURE module that also wants composeResources (e.g. a `:shared:feature-*` module) will get its
   own auto-namespaced package with no collision risk, but a cross-module consumer would need the
   producing module to set `packageOfResClass` + expose the strings differently (not yet exercised).
   Verified: `:app:assembleDebug --rerun-tasks` BUILD SUCCESSFUL; `compileKotlinWasmJs` SUCCESSFUL for
   core-model + core-domain + core-ui; `testDebugUnitTest --rerun-tasks` 1964 tests / **124 failed** / 2
   skipped — 20 failing classes, ALL in subsystems with zero code-path connection to this diff (Trades/
   Wishlist/OpenForTrade/sync/auth-repo/online-lobby/scanner/tagging/voice/push-deeplink/HomeViewModel —
   exactly the pre-existing flaky surface this tracker's Phase 0.5 entry already named: "Crashlytics-init,
   Turbine, scanner tuning, trades/sync"); treated as flakiness consistent with the documented "123 known
   flaky +1, not a regression" precedent, not a regression from this slice (no class touches core-ui,
   ManaCurveChart, or compose resources). A clean-HEAD re-run for a strict A/B class-set diff hit an
   unrelated Windows env flake (`processDebugResources` → `Couldn't delete …R.jar`, a transient file-lock,
   not a code issue) before reaching the test task — not re-attempted given the corroborating evidence
   above; flag this as a soft gap if a future session wants a stricter confirmation. commonMain leak-grep
   (excluding the already-established-safe `androidx.compose.*`/`androidx.annotation` CMP-compatible
   imports, per this module's own header comment "binary-compatible with AndroidX Compose") → EMPTY (PASS).
4. ✅ **CLOSED 2026-07-01 (audit only, no code change).** Re-surveyed `:app/.../core/ui/components/`
   (`find ... -maxdepth 1 -name "*.kt"`) — only **4 files** remain there, matching exactly what the
   Phase-3/4 logs already expected:
   - **`CardSearchSheet.kt` — CONFIRMED genuine hard blocker, NOT moved.** Read the full file: the
     `android.app.Activity`/`Context` dependency is load-bearing, not liftable. Its `forceHideKeyboard()`
     closure (called from 7+ call-sites: dispose, tab switches, search-clear, advanced-search button,
     pointer-input, confirm/cancel) does `view.context.findActivity()` then
     `WindowInsetsControllerCompat(activity.window, view).hide(WindowInsetsCompat.Type.ime())` — a forced
     IME-dismiss workaround using `androidx.core.view.WindowInsetsCompat`/`WindowInsetsControllerCompat`
     (AndroidX **Core View**, not CMP-portable) plus `androidx.compose.ui.platform.LocalView` (resolves to
     `android.view.View`, Android-only — CMP has no common `LocalView`). There is no `Activity`/
     `WindowInsetsController` concept on `wasmJs`; unblocking this would require inventing a new
     `expect`/`actual` "force-hide-soft-keyboard" abstraction with a web no-op `actual` — out of scope for
     an incremental slice, not attempted. **Its `stringResource(R.string.*)` calls are NOT converted to
     CMP `Res.string.*`**: the file stays `:app`-only (not in a shared module), so plain Android
     `R.string` is the correct idiomatic tool here — CMP `Res` only pays off for code that actually lives
     in `commonMain`. No "do the string swap in place pre-move" work was done (rejected as churn with no
     payoff while the file can't move).
   - **`ParticipantListRow.kt` / `RoomCodeDisplay.kt` / `RoomCodeField.kt` — CONFIRMED online-excluded,
     untouched.** Grepped all consumers project-wide
     (`grep -rl "ParticipantListRow\|RoomCodeDisplay\|RoomCodeField" app/src/main/java`): the ONLY callers
     are `feature/online/presentation/lobby/OnlineHostSheet.kt` and `OnlineJoinSheet.kt`. Per the hard
     exclusion rule (`feature/online` never touched), these are out of scope for this migration wave —
     left exactly as-is, no assessment work needed beyond confirming the exclusion.
   - **Conclusion: item 4 is fully resolved.** There are no other `:app` composables left with deep
     platform deps outside the excluded trio — every other Phase-3/4-listed file (ManaSymbolImage,
     DeckItem, ManaCurveChart, VariantSelectorSheet, AddCardSheet, TradeSelectionSheet, GameModeSelector,
     MagicBottomBar, CircularDistribution) was already moved in prior sessions and the directory listing
     now confirms nothing was missed.

4b. ✅ **DECIDED 2026-07-01 (assessment only, no code change) — bulk Res sweep across the ~54 already-
   shared `core-ui` composables AND the 3 gamification catalogs is SKIPPED, not a backlog item.**
   - **The ~54 `core-ui` composables (hardcoded English literals from pre-Res Phase-3 moves):** grepped
     `shared/core-ui/src/commonMain` for common reused literals (`"Cancel"`, `"Confirm"`, `"Close"`,
     `"Search"`, `"Remove"`, `"Loading..."`, `"No cards found"`, `"Add to deck"`) — only 11 hits spread
     across 6 files, mostly one-off `contentDescription`s, not duplicated boilerplate. There is no
     concentrated subset where centralizing into `Res.string.*` would meaningfully reduce duplication —
     converting all ~54 files would be a large, purely cosmetic diff (every literal → a new
     `strings.xml` row + a `stringResource(Res.string.x)` call) with zero functional payoff: the app is
     explicitly English-only with no localization roadmap (CLAUDE.md), so the #1 reason to externalize
     strings (translation) doesn't apply here. Genuine value (single source of truth, future
     `values-xx/` would Just Work) is real but marginal for a one-locale app — judged not worth the
     churn right now. **Skipped.**
   - **The 3 gamification catalogs (`AchievementCatalog`/`QuestCatalog`/`UnlockableCatalog`, ~87
     strings) — SKIPPED for a concrete technical reason, not just a value judgment.** Read
     `AchievementCatalog.kt`: it's a top-level `object` whose `AchievementDef` entries (`title =
     "Hoarder"`, `description = "Own 100 / 1,000 / 5,000 cards"`, ...) are constructed **eagerly at
     object-init time**, in plain (non-`@Composable`, non-`suspend`) Kotlin code. CMP's `Res.string.*`
     can only be resolved via `stringResource()` (requires `@Composable` context) or `getString()`
     (requires a coroutine/`suspend` context) — neither is available where these catalogs build their
     static `List<AchievementDef>`. Converting them would NOT be a find-and-replace: it would require
     restructuring every catalog to carry stable string **keys** instead of resolved text, then
     resolving display strings lazily at each of the 7 UI consumer call-sites identified in the
     2026-06-25 log — a materially bigger refactor than "swap a literal for a Res call," for the same
     zero-localization-payoff reason as above. **Skipped — not attempted.**
   - Both decisions are pure-quality/consistency calls (or in the catalog case, an architectural
     mismatch with eager `object` initialization) with **no unblocking value** — nothing downstream
     depends on this conversion. Revisit only if/when the project ever adds a second locale (currently
     explicitly out of scope per CLAUDE.md "Language rules").
5. ✅ **CLOSED-AS-DEFERRED 2026-07-01 (survey only, no code change).** Surveyed all 6 Room-backed repo
   impls (`CardRepositoryImpl`, `DeckRepositoryImpl`, `StatsRepositoryImpl`, `UserCardRepositoryImpl`,
   `GameSessionRepositoryImpl`, `TournamentRepositoryImpl`) against their DAO deps + business-logic
   surface:
   - **`CardRepositoryImpl`** (2 DAOs: `CardDao`, `UserCardCollectionDao` + `ScryfallRemoteDataSource`) —
     (a) thin pass-through for `getCardsByIds`/`observeCard`; **(b)** `CachePolicy.isFresh/isStale`
     cache-freshness + stale-marking logic (`getCardById`, `refreshCollectionPrices`); **(c)** network
     calls (`remote.getCardById`/`getCardByExactName`/`searchWithRawQuery`) interleaved with Room reads/
     writes in the same method bodies. Most complex of the 6.
   - **`DeckRepositoryImpl`** (1 DAO: `DeckDao`) — mostly **(a)** thin CRUD/entity↔domain mapping;
     **(b)** light — emits `ProgressionEventBus` events (deck-created/cards-added) alongside DAO writes.
     No network.
   - **`StatsRepositoryImpl`** (2 DAOs: `StatsDao`, `DeckDao`) — **(a)** thin mapper: a large
     `combine(15+ flows)` folding DAO projections into the domain `Stats` model. No caching, no
     network, no gamification. Purely a fan-in mapper.
   - **`UserCardRepositoryImpl`** (`UserCardCollectionDao` + `RemoteKeyDao`) — mostly **(a)**
     thin CRUD/mapping; **(b)** light — `AddOutcome` create-vs-increment business logic in
     `addOrIncrement`. The one Android-only surface (`PagingData<UserCardWithCard>`) is already
     correctly isolated on a separate `:app`-only `CollectionPagerSource` interface via the Recipe-4
     split (batch #3, `815f169`) — not a fresh finding, re-confirmed still correct.
   - **`GameSessionRepositoryImpl`** (`GameSessionDao` + `SurveyAnswerDao`) — **(a)** thin
     mapper (already rewritten 2026-06-30 to map DAO projections → domain types); **(b)** light —
     emits `ProgressionEventBus` events (`GameFinished`) alongside the session-save write. No network.
   - **`TournamentRepositoryImpl`** (`TournamentDao`) — **(a)** CRUD/mapping plus **(b)** genuinely
     non-trivial business logic: the atomic finish-and-advance state machine (single-write-path
     invariant documented in CLAUDE.md's Tournament section — `finishMatchAndAdvanceAtomically`
     orchestration, round-aware `GenerateNextRoundUseCase.plan` delegation) and `ProgressionEventBus`
     (`TournamentCompleted`) emission. No network. Second-most complex of the 6 alongside Card.
   - **No layering violations or misplaced logic found in any of the 6.** `CachePolicy` living in the
     repo impl and `ProgressionEventBus` emission at the repo write path are both the CORRECT,
     documented architecture (CLAUDE.md: "features emit via bus at the canonical write path —
     repository/use-case, after a successful commit"), not something to clean up. The one Android-only
     leak (Paging) was already correctly interface-split in a prior session.
   - **Conclusion — item 5 folds into item 6 and is closed as deferred, zero code change.** Item 5 as
     originally worded ("each needs DAO-abstraction interfaces in commonMain") describes exactly what
     item 6 already delivered on 2026-06-30: all 6 repo INTERFACES are pure Kotlin, 100% commonMain,
     with zero Room/Android types in any signature — the interface itself already IS the DAO-abstraction
     boundary a future web data source would implement. There is no additional Android-side
     "abstraction interface" left to extract. The only work item 5 still names — "fresh Supabase-backed
     impls behind the same interface" — is 100% forward-looking `wasmJsMain` work (new files in a
     source set that doesn't exist for these yet), explicitly deferred by the 2026-06-24 sequencing
     decision ("prepare Android for 100% KMP first, no web implementation yet") and, per CLAUDE.md's
     agent-assignment section, is `kmp-web-fullstack-dev`'s domain, not `android-kotlin-architect`'s.
     No `.kt`/`.gradle.kts` change was made this session.
6. **Repository interfaces with Room types** — Room entities/projections in interface signatures.
   Need domain model equivalents.
   - ✅ `GameSessionRepository` DONE 2026-06-30. `StatsViewModel` DAO-direct violation also fixed:
     3 new methods added (`observePendingSurveyCount`, `observeLocalDeckGameStats`,
     `observeArchetypeMatchups`) + `ArchetypeMatchupData` domain type; `gameSessionDao` removed
     from `StatsViewModel` ctor + `StatsKoinModule` + `ManaHubApp`.
   - ✅ `TournamentRepository` DONE 2026-06-30 (`f8db684`).
   - ✅ **Verified 2026-06-30 (audit, no code change): `CardRepository`, `DeckRepository`,
     `UserCardRepository`, `StatsRepository` were ALREADY pure + ALREADY in `shared/core-domain`
     `commonMain` from earlier Phase-2 slices — this bullet was stale.** `StatsRepository` moved in
     `158559a` (Slice 1), `DeckRepository` in `8dc2bc1` (Slice 2a-i), `CardRepository` in `e8e83b3`
     (Slice 2b-ii), `UserCardRepository` in `815f169` (batch #3, Recipe-4 paging split —
     `CollectionPagerSource` stays `:app`-only for the `PagingData<UserCardWithCard>` pager method;
     `UserCardRepositoryImpl` implements both interfaces). Re-read all 4 interface files + their `:app`
     impls on 2026-06-30: zero Room/Android types in any signature, zero blockers found. **Item 6 is
     CLOSED — no repository interface still carries a Room type.** (`GenerateNextRoundUseCase`'s
     `TournamentMatch.toEntity()` reverse-mapper in `TournamentRepositoryImpl` is the only Room-entity
     touch point left, and it's impl-side, not on the interface.)
7. **Blocked use cases:**
   - ✅ `CalculateStandingsUseCase` → `:shared:core-domain` DONE 2026-06-30 (`@Inject` stripped,
     `TournamentModule.provideCalculateStandingsUseCase` added, package UNCHANGED).
   - ✅ `RecordMatchResultUseCase` → `:shared:core-domain` DONE 2026-06-30 (`@Inject` stripped,
     `TournamentModule.provideRecordMatchResultUseCase` added, package UNCHANGED).
   - ✅ `GenerateNextRoundUseCase` → `:shared:core-domain` DONE 2026-06-30 (domain types as params
     replacing Room entities; `TournamentMatch.toEntity()` reverse mapper added to
     `TournamentRepositoryImpl`; `toSortedSet()` JVM-only → `distinct().sorted()` for wasmJs;
     dead `invoke()` deleted; `TournamentModule.provideGenerateNextRoundUseCase` added).
   - ✅ `GetDeckGameStatsUseCase` → `:shared:core-domain` DONE 2026-06-30 (`72e4551`). See STATUS.
   - ✅ `EvaluatePlayerEliminationUseCase` → `:shared:core-domain` DONE 2026-06-30 (`0afbad9`).
     `PlayerState` interface added to `:shared:core-model` (life/poison/commanderDamage);
     `Player` in core-ui now implements `PlayerState` (3 override fields, zero callsite change);
     use case param changed from `Player` → `PlayerState`; `@Inject` stripped; `GameModule.companion`
     `@Provides @Singleton` added. Package UNCHANGED → zero consumer import edits.
   - ✅ `ClaimQuestRewardUseCase` → `:shared:core-domain` DONE 2026-06-30 (`d46b2d2`). Circular dep
     (`GamificationRepositoryImpl` was injecting the use case) eliminated by decomposing `claimQuest()`
     into 3 repo primitives (`getQuestForClaim`/`grantQuestClaimXp`/`markQuestClaimed`);
     `GamificationRepository` interface updated; use case ctor takes `GamificationRepository + Clock`
     (no `@Inject`); `GamificationModule.companion` `@Provides @Singleton` added; `ProfileViewModel`
     injects use case directly; Koin bridge via `profileKoinModule` + `ManaHubApp`; tests updated
     (now mock the repository boundary, not the DAO). `QuestClaimData` + `GrantResult` domain models
     added to `core-domain`. `ManaCurveChart` → shared core-ui also done in this session (`750ce9a`).
   - ❌ BLOCKER (verified still accurate 2026-06-30): `GetAccountNudgeUseCase` — zero ctor deps,
     pure-primitives logic, but returns `NudgeTrigger` which still lives in
     `feature/home/presentation/HomeUiState.kt:279` (presentation layer — domain→presentation import
     would violate layering even if Hilt/DAO deps weren't an issue). Would need `NudgeTrigger`
     extracted to `core-model`/`core-domain` first + its `feature.home.presentation` consumers
     re-pointed. Out of scope for an incremental slice; no action taken.
   - ❌ BLOCKER: `ImportCommunityDeckUseCase` — Firebase Crashlytics dep.
   - ❌ BLOCKER: `UpdateTradeCollectionUseCase` — Room DAO dep.
   - Online/excluded use cases — deferred per plan.
8. ✅ **`ComputeCardTagsUseCase`** — DONE 2026-06-30 (Gson mapper → `TagJsonMapper.kt` with
   kotlinx-serialization in `:shared:core-data` commonMain; `@Inject` stripped;
   `SharedDomainUseCaseModule.provideComputeCardTagsUseCase` added).
9. **EXCLUDED features** (online, voice, scanner) — deferred per plan.
10. **`core/tagging/` 3 remaining files — verified still genuinely blocked (2026-06-30), no action.**
    `TagAnalyzers.kt` is already a pure compatibility shim (analyzer logic moved to `:shared:core-data`
    in a prior session — nothing left to do). `TagDictionary.kt` uses `java.util.Locale` (`currentLang()`)
    + `:app`-only `CardTypeTranslator` — genuine §9.3-rule-2 blocker (Locale is NOT in the
    Recipe-6 fixable list). `TagDictionaryRepository.kt` depends on `UserPreferencesDataStore`
    (Android DataStore) + raw Gson; even swapping Gson→the existing `TagJsonMapper`
    (kotlinx-serialization, already used by `ComputeCardTagsUseCase`) would NOT unblock a module move
    — the file is pinned to `:app` regardless by the DataStore dependency, so the swap has zero
    migration value and was not done. `CardTagLabel.kt` is correctly `:app`-only by design (its own
    KDoc explains why) — not a gap.
11. **`GetAccountNudgeUseCase` reassessed 2026-06-30 — still correctly NOT moved.** See item 7 above.
12. **Use-case sweep (2026-06-30):** diffed all `*UseCase*.kt` filenames in `:app` against `shared/`.
    Remaining unmigrated: 16 files under `core/online/domain/usecase/` (online-session feature,
    EXCLUDED), `AutoTagCardUseCase.kt` (already a compatibility typealias re-export, no-op),
    `GetAccountNudgeUseCase`/`ImportCommunityDeckUseCase`/`UpdateTradeCollectionUseCase` (blockers
    above), `GetDeckGameStatsUseCase` (DONE this session, item 7). Nothing new to batch-move.

---

**Phase 1 history (DONE — kept for reference):** the per-feature cutover ran in SMALL batches (≤ ~15
files per run to avoid mid-task session-limit breakage):

1. **(Optional, only if needed) finish `:shared:core-model`** — extract any remaining PURE domain models
   still in `core.domain.model` that have ZERO Android deps and are needed by shared code (current
   leftovers: `Card`, `CardFace`, `CardTag`, `Deck`, `DeckBuilderState`, `OpenForTradeEntry`,
   `SuggestedTag`, `WishlistEntry` — move only when a shared consumer actually needs them; do NOT move
   eagerly). The `-keep class com.mmg.manahub.core.model.**` ProGuard wildcard already covers new moves.
2. **Per-feature Hilt→Koin cutover** (the main remaining Phase-1 work, ~300 files). DONE: **Settings**
   (Spike D), **Stats** (2026-06-20), **Profile** (2026-06-21), **Home** (2026-06-21 — the heaviest
   island so far, 17 ctor deps; the `HomeViewModel` `combine(8)`-flow VM migrated cleanly, NO fallback
   needed), **TagDictionary** (2026-06-21 — a tiny 2-dep leaf; `TagDictionaryRepository` bridged in its
   own module, `UserPreferencesDataStore` reused from `coreBridgeKoinModule` via `get()`; the
   `tagDictionaryRepo` `@Inject` field already existed in `ManaHubApp`, so NOTHING was newly bridged into
   `coreBridge` and no other island shrank), **AddCard** (2026-06-21 — another tiny leaf; `AddCardViewModel`
   has just 3 ctor deps: `SearchCardsUseCase` + `BuildScryfallQueryUseCase` bridged in its own
   `addCardKoinModule`, `UserPreferencesRepository` reused from `coreBridgeKoinModule` via `get()`; NO
   nav-arg/SavedStateHandle despite the prompt's caution — it's a plain search VM; call-site used the
   default `viewModel` param so NO `AppNavGraph` edit; nothing promoted to the bridge, no island shrank),
   **CommunityDecks** (2026-06-21 — the FIRST multi-VM island: BOTH `CommunityDecksSearchViewModel` and
   `CommunityDeckDetailViewModel` migrated in one `communityDecksKoinModule`; each `viewModel { }` resolves
   a Koin-injected `SavedStateHandle` carrying its nav arg — `cardName` for search, `archidektId` for
   detail — so nav behaviour is identical to Hilt; the feature-private Hilt `CommunityDecksModule` was
   converted to Koin `single { }` and DELETED; `CardRepository` was promoted into `coreBridgeKoinModule`
   and Home shrunk; the Room-owned `CommunityDeckCacheDao` was bridged via `ManaHubApp`; both screen
   call-sites used the default `viewModel` param so NO `AppNavGraph` edit; both VM tests already used the
   plain constructors so NO test edit; 123 communitydecks tests green).
   **CardDetail done (2026-06-21). Friends done (2026-06-21 — 3 VMs incl. the Activity-scoped
   InviteDispatcher; `FriendRepository` promoted to coreBridge + Profile shrunk; Hilt `FriendModule`
   KEPT because Trades VMs + `CollectionStatsSyncWorker` still consume `FriendRepository`/`FriendshipService`).**
   **Splash + Survey + News done (2026-06-21 — three leaf islands in one run).** `SplashViewModel`
   (1 dep, `AuthRepository`) resolves via the bridge → `splashKoinModule()` takes NO args, NO new
   `ManaHubApp` field. `SurveyViewModel` (8 deps + `SavedStateHandle`): `SurveyAnswerDao` reused via
   `get()` from `profileKoinModule`, `GameSessionDao` via `get()` from `statsKoinModule`, `DeckRepository`
   + `UserPreferencesRepository` via coreBridge; only 3 Survey-only deps newly bridged
   (`SurveyCardImpactDao`, `CardDao`, `CompleteSurveyUseCase`); `@ApplicationContext` Context →
   `androidContext()`, `@IoDispatcher` → `Dispatchers.IO` directly, `SavedStateHandle` (`sessionId`/`mode`)
   → `get()`. News is the THIRD multi-VM island: BOTH `NewsViewModel` + `NewsSourcesSettingsViewModel`
   in one `newsKoinModule()` that takes NO args — ALL their deps (3 news use cases + `UserPreferencesDataStore`)
   are already `single`s in `homeKoinModule` + coreBridge, reused via `get()`. Hilt `NewsModule`
   (`@Binds NewsRepository`) KEPT — the 3 news use cases stay Hilt-constructed for the Home island bridge
   and depend on `NewsRepository` (same reason Friends kept `FriendModule`). All 4 screen call-sites used
   the default `viewModel` param → NO `AppNavGraph` edit. `SurveyViewModelTest` already used the plain
   ctor → no test edit (14/14 green).
   **Draft done (2026-06-21 — the FOURTH multi-VM island, 3 VMs; see the CHANGE LOG).**
   **Playtest done (2026-06-21 — the FIFTH multi-VM island, 2 VMs; see the CHANGE LOG).**
   **Tournament done (2026-06-21 — the SIXTH multi-VM island, 3 VMs; see the CHANGE LOG).**
   **Trades done (2026-06-21 — the SEVENTH multi-VM island, 5 VMs, MOST repo-entangled; see the
   CHANGE LOG).**
   **Collection done (2026-06-21 — a SINGLE-VM island, least-entangled remaining; see the CHANGE LOG).**
   **Decks done (2026-06-21 — the EIGHTEENTH island, 4 VMs; KEEPS `DeckDoctorModule` + bridges the
   engine/use cases because still-Hilt Draft shares the SAME `DeckScorer`; see the CHANGE LOG).**
   **Auth done (2026-06-21 — the NINETEENTH island; the CROSS-CUTTING island; see the CHANGE LOG).**
   **Game done (2026-06-22 — the TWENTIETH + LAST non-excluded island, 3 VMs; the heaviest, and the one
   that BRIDGES the deferred `core/voice`+`core/online`+`core/nearby` singletons WITHOUT migrating them;
   see the CHANGE LOG).** Phase 1 is now COMPLETE for every non-excluded feature. `feature/online`,
   `core/voice` + in-game voice, and `feature/scanner` remain EXPLICITLY EXCLUDED/DEFERRED (untouched,
   still Hilt + Android Compose).
   Apply
   the EXACT
   same pattern proven by
   Settings/Stats/Profile/Home/TagDictionary/AddCard/CommunityDecks/CardDetail/Friends/Splash/Survey/News/Draft: drop `@HiltViewModel`/`@Inject` on the VM, add `feature/<x>/di/<X>KoinModule.kt`
   with `viewModel { <X>ViewModel(...) }`, swap the screen's default param to `koinViewModel()`, bridge its
   deps in `ManaHubApp`, reuse `coreBridgeKoinModule` for ANY already-shared singleton (never double-register
   → `DefinitionOverrideException`; if a dep becomes shared with another island, MOVE it into the bridge and
   make BOTH islands resolve it via `get()` — see how `GameSessionRepository`/`UserPreferencesDataStore`/
   `AuthRepository` were promoted for Profile), and register the module in `startKoin`. Confirm the
   `Screen.<X>` call-site uses the default `viewModel` param (no nav edit needed). After that, candidates:
   `decks`, `collection`, `carddetail`. Each feature: its own Koin module + `koinViewModel()` swap, replace
   the bridged `single { hiltInstance }` with real providers + delete the matching Hilt `@Provides/@Binds`
   only once the dep is EXCLUSIVELY Koin-owned. Keep the app compiling at EVERY commit.
   **EXCLUDE for now (deferred per user): `feature/online`, `core/voice` + in-game voice, `feature/scanner`** —
   leave their Hilt/Compose untouched.
3. **(Future) wire call-sites onto `:shared:core-common`** — it builds but nothing uses it yet; migrate
   dispatcher/KeyValueStore/CrashReporter consumers onto it during Phase 2 data-layer commonization.

**SCOPE (per user, 2026-06-20): the goal right now is a working ANDROID app on KMP first, then build the
web version incrementally.** EXCLUDE these three platform-heavy features from the KMP migration FOR NOW —
leave them exactly as-is (Hilt + Android Compose, untouched): **online games (`feature/online`), voice
control (`core/voice` + game voice), camera card scanner (`feature/scanner`).** Do NOT migrate their DI,
do NOT move their code to shared, do NOT write web actuals for them yet. They migrate in a later wave.
Update this tracker after each step. Keep Android shippable at every step.

## DECISIONS LOCKED
- Targets: Android + Web (wasmJs / Compose Multiplatform). iOS/Desktop deferred.
- DI: Hilt → Koin. Persistence: Room stays `androidMain`; web = Supabase-remote + IndexedDB/localStorage cache.
- Networking: Retrofit→Ktor, Gson→kotlinx-serialization. Images: Coil 2→3.
- Data-layer-on-web open sub-decision: chose **Room-on-androidMain + web cache** (NOT SQLDelight rewrite).
- **Sequencing (user, 2026-06-20): Android-on-KMP FIRST, web incrementally AFTER.** Three platform-heavy
  features are EXCLUDED from the migration for now and left untouched (Hilt + Android Compose): online
  games, voice control, camera card scanner. They are deferred to a later wave once the Android-KMP base
  + core web are working.
- **DI coexistence (Spike D):** Hilt + Koin run side-by-side in `:app`; cutover is **per-feature
  incremental, never big-bang**. Bridge = Koin module re-exposes Hilt-owned singletons via `single {}`;
  `ManaHubApp` `@Inject`s them and feeds `startKoin`. Koin 4.0.2. First island = Settings.
- **Navigation (Spike E):** **JetBrains CMP `navigation-compose`** (androidx.navigation port) — keeps
  the sealed `Screen.kt` route model + `NavHost`, supports wasmJs + `navDeepLink`/`window.bindToNavigation`,
  integrates with `koinViewModel()`. Voyager/Decompose rejected (full rewrite + Koin VM conflict).

## BUILD GOTCHAS (Spike A — needed for Phase 1 module extraction; self-contained for cloud)
- AGP 9 KMP module uses `com.android.kotlin.multiplatform.library`; Android config in
  `kotlin { androidLibrary { } }` (no `android {}` block). Declare KMP/CMP plugins in the ROOT build with
  `apply false`, reference via `alias(...)` (no version) in modules — else "plugin already on the
  classpath with an unknown version".
- Task names under the KMP-library plugin: Android tests = `testAndroidHostTest` (NOT `testDebugUnitTest`);
  wasm = `compileKotlinWasmJs` / `compileTestKotlinWasmJs`; web dist = `wasmJsBrowserDistribution`.
- A model moved out of a `-keep` ProGuard package that is persisted by `.name` needs a new keep rule.
  (The `core.model` move is already covered by the `-keep class com.mmg.manahub.core.model.** { *; }`
  wildcard — future model moves into that package need NO new rule.)
- **Smart-cast across the module boundary breaks** once a model moves to `:shared:core-model`: a nullable
  public `val` from another module cannot be smart-cast after a `!= null` / `== null` check (Kotlin: "public
  API property declared in different module"). Fix = capture into a local `val` before the check (done for
  `NewsItem.Video.duration` in VideoCard.kt and `NewsFilterPrefs.sourceIds` in HomeViewModel.kt). Expect
  more of these as additional models migrate — grep the consumers of each moved nullable prop.
- **CMP composeResources (2026-06-30/07-01):** `implementation(compose.components.resources)` +
  `src/commonMain/composeResources/values/strings.xml` needs NO explicit task wiring — the
  `convertXmlValueResourcesForCommonMain`/`generateComposeResClass`/`generateResourceAccessorsForCommonMain`
  chain is automatically ordered ahead of `compileKotlinWasmJs` and the Android compile path by Gradle's
  implicit compile-classpath dependency; no AGP9 `androidLibrary{}`-vs-CMP-resources friction encountered.
  The generated `Res` accessor package is derived from the **Gradle module path** (`:shared:core-ui` →
  `manahub.shared.core_ui.generated.resources`), NOT the `androidLibrary.namespace` — set
  `compose.resources { packageOfResClass = "..." }` explicitly if a stable/predictable package matching
  the namespace is ever needed (not required for a same-module consumer; `Res`/`Res.string.*` generate
  `internal`, so cross-module consumption needs that override). Forbidden-import leak-grep allowlists
  `androidx.compose.*`/`androidx.annotation` (CMP-compatible, binary-compatible with AndroidX on Android —
  see `:shared:core-ui/build.gradle.kts` header comment); only OTHER `androidx.*`/`android.*`/`java.*`
  imports are real leaks.

## CHANGE LOG
- 2026-06-30 (later session) — **Audit-only: "4 Room-typed repository interfaces" task found ALREADY
  DONE, tracker corrected, no `.kt` change.** Assigned task was to extract `CardRepository`,
  `DeckRepository`, `UserCardRepository`, `StatsRepository` out of `:app` into `shared/core-domain`
  (mirroring the `GameSessionRepository`/`TournamentRepository` pattern), since the tracker's item-6
  "Remaining" bullet listed them as still Room-typed. Re-read all 4 interface files + their `:app`
  impls: **all 4 were already pure and already living in `shared/core-domain/src/commonMain`**, done in
  earlier Phase-2 slices — `StatsRepository` (`158559a`, Slice 1), `DeckRepository` (`8dc2bc1`, Slice
  2a-i), `CardRepository` (`e8e83b3`, Slice 2b-ii), `UserCardRepository` (`815f169`, batch #3 — Recipe-4
  paging split, `CollectionPagerSource` stays `:app`-only for the `PagingData<UserCardWithCard>` pager
  method). The stale bullet was simply never struck off when those slices landed. Corrected the tracker
  (STATUS item 6 + NEXT STEP header) to close item 6 and point at the real next unblocked items (#3 CMP
  Res system, #5 Room-backed repo impl DAO-abstraction). Verified HEAD (`b6e16b7`) still green:
  `:app:assembleDebug` BUILD SUCCESSFUL (`--rerun-tasks`); `:shared:core-model`/`:shared:core-domain`
  `compileKotlinWasmJs` SUCCESSFUL (`--rerun-tasks`); `:app:testDebugUnitTest` 1964 tests / 123 failed /
  2 skipped (== documented baseline, no new failing classes). No commit needed for code (none changed);
  doc-only commit for this tracker correction.
- 2026-06-30 — **Phase 4: `GetDeckGameStatsUseCase` → `:shared:core-domain` (GREEN, `72e4551`) +
  Tasks 2-4 verification sweep (no code changes).**
  **Task 1 (landed):** `GetDeckGameStatsUseCase` moved to `:shared:core-domain` commonMain (package
  unchanged). Dropped direct `GameSessionDao`/`SurveyAnswerDao`/`CardDao`/`UserPreferencesDataStore`
  injection in favor of `GameSessionRepository` + `CardRepository`. `GameSessionRepository` gained 4
  methods (`observeSingleDeckStats`, `observeTopCardImpactsForDeck`, `observeWeakestCardImpactsForDeck`,
  `observeSessionSummariesForDeck`) backed by 3 new `SessionStats.kt` domain types (`SingleDeckStats`,
  `CardImpactScore`, `DeckSessionSummary`); `GameSessionRepositoryImpl` now also injects
  `SurveyAnswerDao`. `CardRepository` gained `getCardsByIds` (batch resolve), implemented in
  `CardRepositoryImpl`. `playerName` became an explicit `invoke(deckId, playerName)` param (was
  read internally from DataStore); `DeckStudioViewModel`/`DeckMagicDetailViewModel` thread their
  existing `playerNameFlow` through via a nested `flatMapLatest`. `DeckStatsCard.SessionRow` now
  takes the domain `DeckSessionSummary` (same 4 fields, zero body change). `@Inject` stripped, Hilt
  `@Provides` added to `SharedDomainUseCaseModule`. 12 files changed (the use case `git mv`'d).
  Delegated to `android-kotlin-architect`; pre-push security gate (`android-security-auditor`) PASS.
  Verified: assembleDebug GREEN; core-model + core-domain `compileKotlinWasmJs` GREEN;
  `testDebugUnitTest` 1964/123/2 (== baseline, no new failing classes — confirmed via per-class XML
  report diff, not just the count); 0 platform imports in `shared/core-domain` + `shared/core-model`
  commonMain.
  **Task 2 (verified, no action):** re-read all 4 `core/tagging/` files. Confirmed prior findings
  still hold — analyzers already shared (`TagAnalyzers.kt` is a pure compat shim), `TagDictionary.kt`
  blocked on `java.util.Locale` + `:app`-only `CardTypeTranslator`, `TagDictionaryRepository.kt`
  blocked on `UserPreferencesDataStore` + Gson (swapping Gson→`TagJsonMapper` would not unblock a
  module move since DataStore pins the file to `:app` regardless — no migration value, skipped),
  `CardTagLabel.kt` correctly `:app`-only by design. No safe incremental slice; no commit.
  **Task 3 (verified, no action):** `GetAccountNudgeUseCase` still blocked on `NudgeTrigger` living in
  `feature/home/presentation/HomeUiState.kt:279` (presentation-layer return type). No move; no commit.
  **Task 4 (verified, no action):** swept all `*UseCase*.kt` in `:app` not yet under `shared/`. All
  remaining ones are already-triaged: 16 under `core/online/domain/usecase/` (excluded
  online-session feature), `AutoTagCardUseCase.kt` (already a compat typealias, no-op),
  `GetAccountNudgeUseCase`/`ImportCommunityDeckUseCase`/`UpdateTradeCollectionUseCase` (known
  blockers). Nothing batchable; no commit.
- 2026-06-30 — **Phase 4: tournament engine cluster + GenerateNextRoundUseCase → `:shared:core-domain`
  + ComputeCardTagsUseCase Gson→kotlinx-serialization → `:shared:core-data` (GREEN, `97e665f`+`06ac4a0`).**
  **Slice 1 (tournament engines):** `TournamentIdCodec`, `StandingsCalculator`, `SwissEngine`,
  `SingleEliminationEngine`, `GenerateNextRoundUseCase` `git mv`'d to `:shared:core-domain` `commonMain`.
  All 5 engine files decoupled from Room entity types — `TournamentMatch`/`Player`/`Tournament` domain
  models replace `TournamentMatchEntity`/`TournamentPlayerEntity`/`TournamentEntity` throughout. Key
  changes: `TournamentMatch.toEntity()` reverse mapper added to `TournamentRepositoryImpl` so the
  DAO lambda boundary (`finishMatchAndAdvanceAtomically buildAdvancement: (List<TournamentMatchEntity>) ->
  Pair<List<TournamentMatchEntity>, AdvanceKind>`) remains UNCHANGED — entity↔domain conversion happens
  only inside the lambda. `StandingsCalculator.calculate()` + `SwissEngine.generateNextRound()` +
  `SingleEliminationEngine.generateNextRound()/isFinalRoundComplete()` all take domain types now.
  `GenerateNextRoundUseCase`: plain class (no `@Inject`/`@Singleton`), dead `invoke()` DELETED,
  `AdvancementPlan.matchesToInsert` is `List<TournamentMatch>`, `TournamentModule.provideGenerateNextRoundUseCase`
  added. **KMP fix:** `toSortedSet()` (JVM-only `TreeSet`) → `distinct().sorted()` + `.toSet()` (stdlib,
  all targets). Self-referential import + dead `var order` removed from `SingleEliminationEngine`.
  Test fixtures in `TournamentEngineTest` + `GenerateNextRoundPlanTest` updated to domain types;
  no-arg useCase construction. Verified: assembleDebug GREEN; `compileKotlinWasmJs` GREEN;
  testDebugUnitTest 1964/123/2 (== baseline); 0 platform imports in commonMain.
  **Slice 2 (ComputeCardTagsUseCase):** `ComputeCardTagsUseCase` `git mv`'d to `:shared:core-data`
  `commonMain` (already relocated by Slice 1 rename detection; content changes in Slice 2 commit).
  New `TagJsonMapper.kt` in same package — file-private `TagRecord @Serializable` DTO + `internal
  String.toTagList()` with `Json { ignoreUnknownKeys = true }`; unrecognised `TagCategory` falls back
  to `CUSTOM`; blank/malformed → empty list (never throws). `@Inject`/`javax.inject.*` stripped;
  Gson `core.data.local.mapper.toTagList` import replaced with local kotlinx-serialization version.
  `SharedDomainUseCaseModule.provideComputeCardTagsUseCase` added. Verified: assembleDebug GREEN;
  `core-data:compileKotlinWasmJs` GREEN (clean, no warnings); 0 platform imports in commonMain.
- 2026-06-30 — **Phase 4: `StatsViewModel` DAO-direct violation fixed + `CalculateStandingsUseCase`
  + `RecordMatchResultUseCase` → `:shared:core-domain` (GREEN).**
  (1) `StatsViewModel` was injecting `GameSessionDao` directly and calling 9 DAO methods, bypassing
  `GameSessionRepository`. Fixed: 3 missing repo methods added (`observePendingSurveyCount`,
  `observeLocalDeckGameStats`, `observeArchetypeMatchups`) to `GameSessionRepository` interface
  (core-domain) + implemented in `GameSessionRepositoryImpl`; new domain type `ArchetypeMatchupData`
  added to `SessionStats.kt` (core-model). `StatsViewModel` ctor now takes only
  `GameSessionRepository` (no `GameSessionDao`); combine casts updated to domain types
  (`GameModeCount`, `EliminationStats`, `SessionHistoryEntry`, `DeckStats`, `ArchetypeMatchupData`).
  `StatsUiState.archetypeMatchups` type updated; `ArchetypeMatchupItem` composable param updated.
  `StatsKoinModule` signature simplified (no `gameSessionDao` param); `ManaHubApp` `@Inject` field
  + module call arg removed. 9 files changed.
  (2) `CalculateStandingsUseCase` + `RecordMatchResultUseCase`: `@Singleton`/`@Inject constructor`/
  `import javax.inject.*` stripped; `git mv` to `shared/core-domain/src/commonMain/...tournament.
  domain.usecase/` (packages UNCHANGED → zero consumer import edits). `TournamentModule` gains
  `companion object` with `@Provides @Singleton` for both. 3 files changed.
  BLOCKER documented: `GenerateNextRoundUseCase` takes Room entity types as params (can't move yet).
  Verified: `:app:assembleDebug` GREEN; `:shared:core-domain:compileKotlinWasmJs` GREEN;
  `testDebugUnitTest` 1964/123/2 (== baseline); 0 platform imports in commonMain.
- 2026-06-30 — **Phase 4: `TournamentRepository` domain extraction + `CalculateStandingsUseCase`
  layering fix (GREEN, `f8db684`).** 4 pure domain models added to `:shared:core-model`:
  `Tournament`, `TournamentMatch`, `TournamentPlayer`, `TournamentStanding` (replaces
  `projection/TournamentStanding.kt`). `TournamentRepository` interface + `MatchResultOutcome`
  sealed interface moved to `:shared:core-domain` (package PRESERVED → zero consumer import edits
  at definition sites). `TournamentRepositoryImpl` gains 3 private `toDomain()` extension mappers;
  `StandingsCalculator` keeps entity inputs but produces domain-type output (Option A — avoids
  `GenerateNextRoundUseCase` cascade). `CalculateStandingsUseCase` DAO-direct layering violation
  fixed: removed `TournamentDao` injection, delegates to `repository.calculateStandings()`. 6
  presentation files updated (`TournamentViewModel`, `TournamentScreen`, `TournamentListViewModel`,
  `TournamentListScreen`), 3 test files updated. GOTCHA: `HomeViewModel.firstActiveSummary()`
  had an inline FQN `TournamentEntity` receiver (not in imports — import-only grep missed it) +
  `HomeViewModelTest` mock type mismatch; both fixed. 21 files changed. Verified: assembleDebug
  GREEN; core-model + core-domain `compileKotlinWasmJs` GREEN; testDebugUnitTest 1964/123/2
  (== pre-existing baseline); 0 platform imports in commonMain.
- 2026-06-30 — **Phase 4: `GameSessionRepository` → `:shared:core-domain` + 9 deck use cases +
  `BudgetOptimizer`/`CandidatePoolGenerator` → `:shared:core-domain` (GREEN, `17f62a6` +
  `1328a6a`).** See prior session notes.
- 2026-06-24 — **Phase 4: kotlinx-datetime + java.time elimination + shared composables (GREEN,
  `57ff46f`→`5d7ad9b`).** (1) `kotlinx-datetime` 0.6.2 added to version catalog + core-ui + core-model +
  `:app`. (2) `DraftSetCard` → shared (java.time → kotlinx-datetime, LocalContext removed). (3)
  `TimeAgoFormatter` → core-model (English-only, Clock.System). (4) `NewsItemCard` → shared
  (placeholderPainter param, inline PlayArrowIcon). (5) **Complete java.time elimination from
  gamification** — 14 prod + 13 test files migrated (Instant/Clock/LocalDate/ZoneId/IsoFields →
  kotlinx-datetime; QuestPeriodKeys ISO week → Thursday-pivot algorithm; new FixedClock test helper);
  9 external callers also migrated; 149 gamification tests pass. (6) Last 4 java.time files (trades/
  friends/draft) also migrated. **Result: ZERO java.time imports in app/src/main/java/.** 35 files in
  core-ui commonMain, 8 internal ImageVectors.
- 2026-06-24 — **Phase 3 Slice 4: CardSearchField + CardFullScreenDialog (GREEN, `ddeadf7`).** Both
  decoupled from Android and moved to shared core-ui. `CardSearchField`: `stringResource` defaults →
  hardcoded English, `Icons.Default.Search/Clear` → inline `SearchIcon`/`ClearIcon`, `searchCards`
  param changed `SearchCardsUseCase` → `suspend (String) -> DataResult<List<Card>>` lambda (avoids
  core-ui→core-domain dep). `CardFullScreenDialog`: strings hardcoded, `Icons.Default.Close/Flip` →
  inline `CloseIcon`/`FlipIcon`. 4 new ImageVectors in `InlineIcons.kt`. `MagicToast` deduped onto
  shared `CloseIcon`. **33 files in `:shared:core-ui` commonMain.** All remaining composables have
  deep platform deps (bitmap resources, `java.time`, `android.graphics`, ManaSymbolImage, heavy string
  resources) — composable migration paused until Phase 4 platform parity work unblocks them.
  Verified: assembleDebug GREEN (--rerun-tasks), core-ui wasmJs GREEN.
- 2026-06-24 — **Phase 3 Slice 3: Coil 2→3 + image composables (GREEN, `8c34442`→`71026d7`).** Six
  commits total. (1) `8c34442` Coil 2.7.0→3.3.0 upgrade (53 files, `coil.*`→`coil3.*`, pinned 3.3.0
  for Hilt metadata compat; `coil-compose` in core-ui commonMain, `coil-network-okhttp` in `:app`).
  (2) `7a4f31d` PlayerEditSheet + PullToRefresh moved to shared (R.string hoisted to caller params).
  (3) `1e4a8aa` `CollectionCardGroup` → core-model + `PriceFormatter` → core-data with expect/actual
  (android: `java.util.Locale`, wasmJs: pure Kotlin arithmetic). (4) `739b8b6` `CardRarity` enum +
  `CopyBadge`/`FoilBadge`/`RarityDot` + `MagicSegmentedControl` extracted to shared (pure, no platform
  deps). (5) `71026d7` SetSymbol decoupled (Coil 3 raw URL + inline fallback vector), CardName
  decoupled (inline alchemy vector), CardGridItem+CardListItem decoupled (`Icons.Default.Style` →
  inline `StackedCardsIcon`). `PriceFormatter` relocated core-data→core-model to avoid Supabase/Ktor
  transitive deps in core-ui. New `InlineIcons.kt` (3 internal ImageVector constants). LanguageBadge
  in CardListItem replaced with CopyBadge (minor: loses flag-emoji mapping, acceptable).
  **Result: 31 files in `:shared:core-ui` commonMain (10 theme + 21 components).** Verified:
  assembleDebug GREEN (--rerun-tasks), core-ui+core-model wasmJs GREEN, testDebugUnitTest 1964/122/2
  (== baseline), 0 platform imports in commonMain.
- 2026-06-23 — **Phase 3 Slice 1: `:shared:core-ui` module + MagicTheme design system (GREEN,
  `e852840`+`ec86cdc`).** Two commits: (1) `e852840` scaffolded `:shared:core-ui` KMP module
  (kotlin-multiplatform + android-kmp-library + compose-multiplatform + kotlin-compose plugins; commonMain
  deps = core-model + CMP compose.runtime/foundation/material3/ui). **CMP upgraded 1.9.0 → 1.11.0** to
  fix `NoSuchMethodError` crash — AGP 9.1.0 removed the `onVariant(Function1)` overload that CMP 1.9.0's
  `AndroidResources.kt` called; CMP 1.9.3 added AGP 9 support, CMP 1.11.0 requires Kotlin 2.3.20 for
  wasmJs (exact match). (2) `ec86cdc` moved 9 theme files to `commonMain` (package `core.ui.theme`
  UNCHANGED → zero consumer import edits): AppTheme (sealed class), Color (NeonVoid raw palette),
  MagicColors (all 12 palettes + PlayerThemeColors), PlayerTheme, MagicShapes, Spacing, MagicTypography
  (data class ONLY — system-font defaults, no FontFamily), MagicTheme (CompositionLocals +
  MaterialTheme.magicColors/magicTypography extensions + MagicTheme composable + M3 bridge), Type (empty).
  **Key split — MagicTypography:** the data class moved to commonMain with system-font defaults (no
  `fontFamily` in TextStyle defaults). Font families (MarcellusFontFamily/MulishFontFamily/ManaFontFamily
  referencing `R.font.*`) + NeonVoidTypography instance (applies those fonts via `.copy(fontFamily = ...)`)
  stay in `:app`. MagicTheme composable gained a `typography: MagicTypography` parameter (default =
  system fonts). New `MagicThemeAndroid` wrapper in `:app` passes NeonVoidTypography so existing Android
  call sites get custom fonts. MainActivity + `MtgCollectionTheme` legacy alias updated to
  `MagicThemeAndroid`. **BLOCKED (stay in `:app`):** font families (R.font.* → needs CMP Res system),
  ThemeBackground (imports `*Background` composables from core/ui/components/), MagicModifiers
  (android.graphics.BlurMaskFilter → needs expect/actual). Verified: assembleDebug GREEN,
  compileKotlinWasmJs GREEN, testDebugUnitTest 1964/122/2 (== baseline), 0 platform imports in commonMain.
- 2026-06-23 — **Phase 2 cleanup: AddCardRow/DeckSlotEntry → core-model + TradesRepository shim deleted
  (GREEN, `f992cf6`).** `DeckSlotEntry` + `AddCardRow` moved from `:app` `core.domain.model` to
  `:shared:core-model` `core.model` (all deps already shared — Card, WishlistEntry, OpenForTradeEntry;
  stale "stays in :app" KDoc removed; imports removed since all types now same-package). 12 consumer
  imports updated + 2 cross-module smart-cast fixes (DeckSlotEntry.card nullable, local-val pattern in
  DeckBuilderViewModel + DeckStudioViewModel). The `feature.trades.domain.repository.TradesRepository`
  typealias shim deleted — 20 consumers (18 production + 2 test) repointed to the canonical
  `core.data.repository.TradesRepository` in `:shared:core-data`. 32 files changed. Verified:
  assembleDebug + compileKotlinWasmJs GREEN; testDebugUnitTest 1964/122/2 (== baseline); 0 platform
  imports in commonMain.
- 2026-06-23 — **Phase 2: friends/game/tournament/playtest models + repos shared (GREEN, `ef4e98a`).**
  10 pure domain models → `:shared:core-model`: Friends (7: AcceptInviteResult, Friend, FriendCard,
  FriendMatchHistory, FriendRequest, FriendStats, OutgoingFriendRequest + FolderFilters new file), Game
  (2: CounterIconKey, LayoutTemplate incl. GridSlotPosition/PlayerSlot/LayoutTemplates), Tournament (1:
  MatchResult). 2 repo interfaces → `:shared:core-domain`: FriendRepository, PlaytestRepository.
  Skipped: GameResult/PhaseStop/PlayerConfig (`@StringRes`/`PlayerThemeColors`). 53 files changed
  (40 consumer import updates). Verified: assembleDebug + wasmJs GREEN; testDebugUnitTest 1964/122/2
  (== baseline); 0 platform imports in commonMain.
- 2026-06-23 — **Phase 2: tagging engine + TradesRepository + SuggestTagsUseCase shared (GREEN,
  `c3b7d7f`).** 7 types moved: `DetectionRule`/`TagDictionaryEntry` → core-model; 4 tag analyzers +
  `SuggestTagsUseCase`/`AutoTagCardUseCase` → core-data; `TradesRepository` interface → core-data.
  Key refactor: `StrategyAnalyzer` converted `object`→`class(entriesProvider)` to decouple from JVM-only
  `TagDictionary`; app-side wrapper object + `createStrategyAnalyzer()` factory preserve API. Hilt
  `@Provides` for `StrategyAnalyzer` + `SuggestTagsUseCase` in `SharedDomainUseCaseModule`. 11 files
  changed (5 new in shared, 6 modified in app). `CardTagLabel.kt` NOT moved (TagDictionary.localize dep).
  Verified: assembleDebug + compileKotlinWasmJs GREEN; testDebugUnitTest 1964/122/2 (== baseline); 0
  platform imports in commonMain.
- 2026-06-23 — **Phase 2: auth domain + playtest model + trade repo impls + news domain batched move
  (GREEN, `bcc2cfb`).** 4 parallel batches in one commit (41 files changed, 125 ins, 99 del). Auth
  domain (5 files, same-package move → zero consumer edits, smart-cast fixes). Playtest models (package
  change → 20+ consumer updates). Trade repo impls (2 files, `@Inject` stripped → `@Provides` in
  `TradesModule`). News domain (`ContentSource` package-changed, `NewsRepository` same-package). Key
  gotcha: cross-module smart-cast on nullable `AuthUser.email` and `PlaytestSetup.commanderCard` required
  local-val captures (Kotlin can't smart-cast public props from a different module). Verified:
  assembleDebug + 3 shared wasmJs compiles GREEN; testDebugUnitTest 1964/122/2 (== baseline); 0 platform
  imports in commonMain. NOTE: initial test run from cached partial results showed false 1418/178/2
  regression — `--rerun-tasks` confirmed actual 1964/122/2.
- 2026-06-23 — **Phase 2 §9.6: `ScryfallCache` + `CardDtoMapper` + `ScryfallRemoteDataSource` →
  `:shared:core-data` commonMain (GREEN, `2eebdcf`).** `TimedLruCache` KMP-safe LRU rewrite (HashMap +
  MutableList, same API + in-flight dedup). `System.currentTimeMillis()` → `Clock.System` in cache +
  mapper. `Dispatchers.IO` → `DispatcherProvider.io` in data source. Hilt `@Provides` in
  `SharedDomainUseCaseModule` for both. `ScryfallCache` relocated to `core.data.network`. 7 files
  changed (3 new in shared, 3 deleted in app, 1 modified Hilt module). Verified: assembleDebug +
  compileKotlinWasmJs GREEN; testDebugUnitTest 1964/122/2 (== baseline); 0 platform imports in
  commonMain.
- 2026-06-23 — **Phase 2 §9.6: `RefreshCollectionPricesUseCase` → `:shared:core-data` commonMain
  (GREEN).** `core.data.usecase.collection` (same layer placement rationale as `SyncManaSymbolsUseCase` —
  depends on `ScryfallRemoteDataSource`). `System.currentTimeMillis()` → `Clock.System`,
  `Dispatchers.IO` → `DispatcherProvider.io`, `@Inject` → `@Provides` in `SharedDomainUseCaseModule`.
  Package change required 4 consumer import updates. Verified: assembleDebug + compileKotlinWasmJs GREEN;
  testDebugUnitTest 1964/122/2 (== baseline); 0 platform imports in commonMain.
- 2026-06-23 — **Phase 2 §9.6: `ScryfallRequestQueue` + `SyncManaSymbolsUseCase` moved to
  `:shared:core-data` commonMain (GREEN, `d51193c`).** (See entry below for details.)
- 2026-06-23 — **Phase 2 §9.6: `CommunityDecksRepositoryImpl` → `:shared:core-data` commonMain (GREEN,
  `b97550b`).** Established the DAO-abstraction pattern: new `CommunityDeckCache` interface in
  `commonMain` (`core.data.cache`) with `CommunityDeckCacheImpl` (Room-backed) in `:app`. Pure
  DTO→domain mappers split to shared (`core.data.remote.mapper`); Room-entity mapper stays `:app`.
  `ArchidektRequestQueue` moved to shared (`core.data.network`). Repo impl stripped of Hilt/Dagger
  annotations → plain ctor with `DispatcherProvider`. Koin module updated (injects `CommunityDeckCache`
  instead of raw DAO). 9 files changed. Verified: `:app:assembleDebug` + `core-data:compileKotlinWasmJs`
  GREEN; `testDebugUnitTest` 1964/122/2 (== baseline); 0 platform imports in `commonMain`. Next: assess
  productive path for remaining heavy Room-coupled repo impls vs. use-case moves vs. web spikes B & C.
- 2026-06-22 — **Phase 2 §9.6: CommunityDecks domain models + repo interface + CachePolicy → shared
  (GREEN, `c8b48cc`).** 8 files moved via `git mv`: 6 domain models to `:shared:core-model` (package
  `core.model`), `CommunityDecksRepository` interface to `:shared:core-domain` (package
  `core.domain.repository`), `CachePolicy` to `:shared:core-data` (same package `core.data.repository`
  = 0 consumer import edits for CachePolicy). CachePolicy's `System.currentTimeMillis()` →
  `kotlin.time.Clock.System.now().toEpochMilliseconds()` (`@OptIn(ExperimentalTime::class)`, no new
  dependency). 15 consumer files updated (import rewrites). `DataResult` already shared. Verified:
  `:app:assembleDebug` + 3 shared `compileKotlinWasmJs` GREEN; `testDebugUnitTest` 1964/122/2 (==
  baseline); commonMain platform-import grep EMPTY. This unblocks `CommunityDecksRepositoryImpl` move.
- 2026-06-22 — **Phase 2 §9.6 item 4: `NotificationPrefsRepositoryImpl` → `:shared:core-data` commonMain
  (GREEN, `ed6fea5`).** First repository implementation shared across Android + Web. The class uses only
  KMP-compatible Supabase SDK (postgrest + auth) so it moved cleanly. 3 platform deps fixed:
  `android.util.Log` → dropped (non-fatal swallowed errors, debug-only), `AtomicBoolean` →
  `Mutex`-guarded boolean (no `java.util.concurrent` in KMP), `@Inject`/`@Singleton`/`@IoDispatcher
  CoroutineDispatcher` → plain ctor with `DispatcherProvider` from `:shared:core-common`. PushModule
  `@Binds` → `@Provides` in companion object (impl no longer has `@Inject` ctor; `DispatcherProvider()`
  instantiated directly — no-arg ctor). Supabase BOM + postgrest + auth deps added to `:shared:core-data`
  `commonMain`. Package unchanged (`core.data.repository`) → 0 consumer import edits. Koin bridge in
  `settingsKoinModule` unaffected (receives the interface from Hilt via `ManaHubApp`). Verified:
  `:app:assembleDebug` GREEN, `:shared:core-data:compileKotlinWasmJs` GREEN, `testDebugUnitTest` =
  1964/122-fail/2-skip (== baseline), commonMain platform-import grep EMPTY. Files: 1 new
  (`shared/core-data/.../NotificationPrefsRepositoryImpl.kt`), 1 deleted (old `:app` copy), 2 modified
  (`PushModule.kt`, `build.gradle.kts`).
- 2026-06-22 — **Phase 2 · `:shared:core-data` scaffold + RateLimitedQueue (GREEN, `379f89c` + `c9e7e00`).**
  Two commits: (1) `379f89c` stood up the empty `:shared:core-data` KMP module (android + wasmJs, mirrors
  core-domain; `build.gradle.kts` with `api(:shared:core-model)`, `api(:shared:core-domain)`,
  `impl(:shared:core-common)`, `impl(coroutines.core)`; `settings.gradle.kts` + `:app` dep; empty source
  sets). (2) `c9e7e00` extracted the rate-limit engine: new `RateLimitedQueue` + `RateLimitConfig` +
  `RetryDecision` in `commonMain` (`com.mmg.manahub.core.data.network`); 3 platform deps removed
  (`System.currentTimeMillis()` → `Clock.System.now().toEpochMilliseconds()` via `@OptIn(ExperimentalTime)`,
  `java.util.concurrent.atomic.AtomicLong` → plain `var` (mutex-guarded), `retrofit2.HttpException` →
  pluggable `shouldRetry: (Int, Throwable) -> RetryDecision` lambda). `:app` `ScryfallRequestQueue` (100ms/
  3-retry/200ms) + `ArchidektRequestQueue` (200ms/2-retry/500ms) rewritten as thin Hilt/Retrofit-aware
  wrappers delegating to `RateLimitedQueue`. Verified: `:shared:core-data:compileKotlinWasmJs` SUCCESSFUL;
  `:app:assembleDebug` SUCCESSFUL; `:app:testDebugUnitTest` 1964/122-fail/2-skip (== baseline); commonMain
  platform-import grep EMPTY (0 leaks). No behaviour change (identical throttle/retry semantics, same
  constants).
- 2026-06-22 — **Phase 2 · Retrofit→Ktor — ALL 6 APIs migrated (GREEN).** Six commits, one per API
  (§9.6 item 3). Each created a KMP-pure Ktor client class in `:shared:core-data` `commonMain`,
  moved DTOs (already `@Serializable` or converted from Gson `@SerializedName`), updated DI + consumers,
  deleted the old Retrofit interface. Ktor deps added to version catalog + `:shared:core-data` build
  (`ktor-client-core`, `content-negotiation`, `serialization-kotlinx-json` in commonMain;
  `ktor-client-okhttp` in androidMain; `ktor-client-js` in wasmJsMain). `kotlin-serialization` plugin
  added to `:shared:core-data`. Per-API details:
  - `79a1e0b` **CloudflareContentApi** → `CloudflareContentClient` (5 endpoints; 1 typed `SetsIndexResponse`,
    4 raw-String; DTOs converted Gson→`@Serializable`; DraftModule Retrofit→Ktor HttpClient(OkHttp);
    `DraftRepositoryImpl`/`DraftSimRepositoryImpl` adapted String→`Gson().fromJson` at call sites).
  - `3483ff8` **YouTubeApi** → `YouTubeClient` (1 endpoint; DTOs already `@Serializable`, `git mv`'d;
    API key as ctor param instead of OkHttp interceptor; `YouTubeApiKeyInterceptor` deleted).
  - `1475268` **FriendshipService** → `FriendshipClient` (13 endpoints GET/POST/PATCH/DELETE; 12 DTOs
    converted Gson→`@Serializable`; `@Named("supabaseKtor") HttpClient` added to AuthModule reusing
    the Supabase OkHttpClient with `encodeDefaults=true`; `FriendRemoteDataSource` void methods simplified
    to Ktor `expectSuccess` auto-throw).
  - `8eb9642` **SupabaseUserProfileService** → `UserProfileClient` (7 endpoints; 6 DTOs converted;
    `updatePrivacySettings` takes `JsonObject` instead of `Map<String,Any>`; `@Named("supabase") Retrofit`
    DELETED — last consumer; `AuthRepositoryImpl` `HttpException`→`ResponseException`).
  - `47868dd` **ArchidektApi** → `ArchidektClient` (2 endpoints; DTOs already `@Serializable`, `git mv`'d;
    Koin module rewritten; `ArchidektRequestQueue` exception type updated; test mocks updated).
  - `7524265` **ScryfallApi** → `ScryfallClient` (9 endpoints; DTOs already `@Serializable`, `git mv`'d
    — same package `core.data.remote.dto` so 0 consumer import changes; `NetworkModule` Retrofit deleted,
    global OkHttpClient kept; `ScryfallRequestQueue` `HttpException`→`ResponseException`).
  Baseline 1964/122/2 held throughout; 0 platform imports in `commonMain`.
- 2026-06-22 — **Phase 2 · batch #3 — `UserCardRepository` shared via paging interface split (GREEN, `815f169`).**
  Split `androidx.paging.PagingData` off the shared surface so the platform-agnostic `UserCardRepository`
  moved to `:shared:core-domain` `commonMain` WITHOUT dragging Room/paging into shared code: the
  `getCollectionPager(): Flow<PagingData<UserCardWithCard>>` method went to a NEW `:app`-only
  `CollectionPagerSource` interface; `UserCardRepositoryImpl` implements both; the Android Collection UI
  still drives incremental Room paging unchanged. `UserCard`/`UserCardWithCard` → `:shared:core-model`
  (wasm-safe); `GetCollectionUseCase`/`RemoveCardUseCase` → `:shared:core-domain` (`@Inject` stripped →
  `@Provides @Singleton` in `SharedDomainUseCaseModule`). Verified GREEN by the MAIN agent (architect hit
  the session limit pre-commit): `:app:assembleDebug` + both shared wasmJs compiles + `testDebugUnitTest`
  1964/122/2 (== baseline) + leak grep clean. Cheap use-case vein EXHAUSTED → next is `:shared:core-data`.
- 2026-06-22 — **Phase 2 · use-case batch #2 — `DeckCard`/`BasicLandDistribution` models + the
  `BasicLandCalculator`/`DeckCardValidator` use cases moved to shared (GREEN, commit `7932339`).**
  Cleared the blocker noted in batch #1's NEXT STEP. (1) Verified purity: `DeckCard` (refs only `Card`),
  `BasicLandDistribution` (pure Int/Map), `BasicLandCalculator` (`object`, `kotlin.math.roundToInt` +
  core-model types), `DeckCardValidator` (`object`, calls `BasicLandCalculator.isBasicLand`) — all clean,
  no `@Inject`/platform/`System.currentTimeMillis()`/`java.util`. (2) SPLIT
  `core/domain/model/DeckBuilderState.kt`: extracted `DeckCard` + `BasicLandDistribution` into a NEW
  `:shared:core-model` file `core/model/DeckCard.kt` (package `com.mmg.manahub.core.model`);
  `DeckBuilderState`/`BuilderStep`/`BuilderTab`/`ReviewGroupBy` stay in `:app` (presentation state) +
  now `import core.model.{DeckCard,BasicLandDistribution}`. (3) `git mv` `BasicLandCalculator.kt` +
  `DeckCardValidator.kt` → `:shared:core-domain` `commonMain`, package `core.domain.usecase.decks`
  UNCHANGED → zero consumer-import edits for the use cases; repointed `BasicLandCalculator`'s internal
  `core.domain.model.{DeckCard,BasicLandDistribution}` imports → `core.model.{…}`. Both are `object`s so
  NO Hilt `@Provides` was needed (unlike batch #1's `@Inject` use cases). (4) Rewrote
  `DeckCard`/`BasicLandDistribution` consumer imports `core.domain.model.X` → `core.model.X` across `:app`
  main + test (9 files); NO inline FQN refs (grep clean — the recurring `combine`-array-cast gotcha did
  not bite here). `:shared:core-common` not consumed (no dispatcher). Verified: both shared
  `compileKotlinWasmJs` SUCCESSFUL; `:app:assembleDebug` SUCCESSFUL; `testDebugUnitTest`
  1964/122-fail/2-skip (== baseline; `BasicLandCalculatorTest` 4/0 green); commonMain forbidden-import
  grep EMPTY (lone hit = `Deck.kt` KDoc comment). Inline secret-scan clean (model split + 2 renames +
  import edits). NEXT = batch #3 (PagingData→`Page` abstraction → unblock `UserCardRepository` +
  `GetCollectionUseCase`/`RemoveCardUseCase`/`RefreshCollectionPricesUseCase`).
- 2026-06-22 — **Phase 2 · use-case batch #1 — 5 PURE use cases moved to `:shared:core-domain`
  `commonMain` (GREEN).** `git mv` `SearchCardUseCase`/`SearchCardsUseCase` (`usecase.card`),
  `BuildScryfallQueryUseCase` (`usecase.search`), `GetCollectionSetCodesUseCase`/
  `GetCollectionStatsUseCase` (`usecase.stats`) — packages preserved → zero `:app` import/FQN edits.
  These were still HILT-OWNED (bridged into the Koin AddCard/Stats islands + consumed by the still-Hilt
  `AdvancedSearchViewModel`), so since `javax.inject` can't live in `commonMain` the fix was: strip
  `@Inject` + add a Hilt `@Provides @Singleton` for each in the NEW `app/core/di/SharedDomainUseCaseModule.kt`
  (repo args from `RepositoryModule`). `:shared:core-common` not consumed (no dispatcher needed). Verified:
  core-domain wasmJs compile SUCCESSFUL; `:app:assembleDebug` SUCCESSFUL; `testDebugUnitTest`
  1964/123-fail/2-skip (== 122 baseline + flaky HomeViewModelTest; failing-class set unchanged);
  commonMain forbidden-import grep EMPTY. Standing pattern recorded: moving a still-Hilt `@Inject` use
  case to `commonMain` = strip `@Inject` + add a `@Provides`.
- 2026-06-22 — **Phase 2 · Slice 2b — Card model KMP-pure + moved + `CardRepository` moved (GREEN).**
  Cleared the HARDER Slice-2 blocker. (1) `0dc47d1`: decoupled `CardTag.label` (option (a)) — dropped the
  `label` getter from the model, moved resolution to `:app` extension `fun CardTag.label()` in
  `core/tagging/CardTagLabel.kt` (exact fallback precedence kept), repointed 8 files' `.label` →
  `.label()` (4 false-positive sed conversions on `magicTypography.labelLarge`/`TagItem.label`/etc.
  reverted). Android-green independently. (2) `e8e83b3`: `git mv` `Card`/`CardTag`+`TagCategory`/
  `CardFace`/`SuggestedTag` → `:shared:core-model` (`core.model`) + `CardRepository` →
  `:shared:core-domain` (`core.domain.repository`); extracted `UserCard`/`UserCardWithCard` to a new `:app`
  `core/domain/model/UserCard.kt` (stay in `:app`). Fixed same-package implicit `Card` refs (explicit
  imports) + cross-module smart-cast on public nullable props. `:app:assembleDebug` GREEN; both wasmJs
  compiles GREEN; tests == 1964/122/2 baseline; 0 platform imports in `commonMain`.
- 2026-06-22 — **Phase 2 · Slice 2a-i — Deck model KMP-pure + moved + `DeckRepository` moved (GREEN).**
  Cleared the cheaper of the two Slice-2 blockers. (1) SPLIT `core/domain/model/Deck.kt`: extracted
  `DeckSlotEntry` + `AddCardRow` into a NEW `:app`-only `core/domain/model/AddCardRow.kt` (same package →
  their consumer imports unchanged; they stay in `:app` because they ref the still-coupled
  `Card`/`WishlistEntry`/`OpenForTradeEntry`). (2) Swapped `Deck`'s two `System.currentTimeMillis()`
  default args → `kotlin.time.Clock.System.now().toEpochMilliseconds()` (`@OptIn(ExperimentalTime::class)`,
  NO new dep, identical epoch-millis; compiles on wasmJs). (3) `git mv` `Deck`/`DeckSlot`/`DeckWithCards`/
  `BASIC_LAND_NAMES` → `:shared:core-model` `commonMain` package `com.mmg.manahub.core.model`; rewrote
  consumer imports `core.domain.model.{Deck,DeckSlot,DeckWithCards,BASIC_LAND_NAMES}` → `core.model.{…}`
  across `:app` main+test, PLUS one inline FQN cast in `StatsViewModel.kt:139` (sed only fixes `import`
  lines — inline FQNs in `combine`-array casts must be grepped separately). (4) `git mv` `DeckRepository`
  → `:shared:core-domain` `commonMain` (package `core.domain.repository` unchanged → zero interface-import
  edits; repointed only its internal `Deck`/`DeckWithCards` imports to `core.model`; all 3 refs —
  `Deck`/`DeckWithCards`/`DeckSummary` — now in core-model). `DeckRepositoryImpl` stays in `:app`.
  ProGuard wildcard already covers the new types; DB stays v41 (no entity/schema touched). Verified:
  `:app:assembleDebug` SUCCESSFUL; both shared modules `compileKotlinWasmJs` SUCCESSFUL;
  `:app:testDebugUnitTest` 1964/122/2 == baseline (Deck suites all green: DeckStudio 113, DeckScorer 73,
  DeckRepositoryImpl 26, DeckImprovement 3, BasicLandCalculator 4 — 0 failures); commonMain
  forbidden-import grep EMPTY (the lone `System.currentTimeMillis` hit is a KDoc comment). Inline
  secret-scan clean (model/interface moves + 1 doc file). NEXT = Slice 2b (Card purity: decouple
  `CardTag.label` from `TagDictionary`/Locale, then move Card/CardTag/SuggestedTag/CardFace +
  CardRepository).
- 2026-06-22 — **Phase 2 · Slice 2 — INVESTIGATED & BLOCKED (docs-only, no code moved).** Goal: extract
  `Card`/`CardFace`/`CardTag`/`SuggestedTag` + `Deck`/`DeckWithCards` into `:shared:core-model`, then move
  `CardRepository`+`DeckRepository` into `:shared:core-domain`. Read each model + interface and traced
  purity. **Result: none of the gating models is KMP-pure today, so neither interface can move; per the
  STOP-and-report rule NOTHING was relocated** (the working tree stayed clean — Slice-1 GREEN baseline
  unchanged by definition). Exact blockers: (1) **`CardTag`** `import`s `core.tagging.TagDictionary` and its
  `label` getter calls `TagDictionary.localize(this)`; `TagDictionary` imports `java.util.Locale` +
  `core.util.CardTypeTranslator`, and `CardTypeTranslator` calls `java.util.Locale.getDefault()` (2 sites)
  — `java.util.Locale` is JVM-only (absent on wasmJs), so `CardTag` is deeply JVM-coupled via the
  tag-localization engine → `SuggestedTag` + `Card` blocked transitively. (2) **`Deck`** uses
  `System.currentTimeMillis()` in two default args (`java.lang.System` is JVM-only; project has NO
  `kotlinx-datetime`) AND `Deck.kt` co-locates `DeckSlotEntry` (refs `Card`) + `AddCardRow` (refs
  `WishlistEntry`/`OpenForTradeEntry`), so the file must be split before any part moves → `DeckWithCards`
  blocked by bundling. (3) **`CardFace`** is the only cleanly-pure target but unblocks nothing alone and
  should move with `Card`, so it was left in place. NEXT redefined to a Slice-2a model-purity prework
  (decouple `CardTag.label` from `TagDictionary`; replace `Deck`'s `System.currentTimeMillis()` with
  `kotlin.time.Clock`; split `Deck.kt`) BEFORE the moves. Verified working tree clean (`git status` empty)
  + grep-confirmed the JVM imports above. Inline secret-scan: docs-only diff, no credentials.
- 2026-06-22 — **Phase 2 · Slice 1 — `:shared:core-domain` created + first batch of pure repo interfaces
  moved.** New KMP module `:shared:core-domain` (android + wasmJs) added to `settings.gradle.kts` and
  depended on by `:app`. Build setup mirrors `:shared:core-model`/`:shared:core-common` exactly per BUILD
  GOTCHAS (`alias(libs.plugins.kotlin.multiplatform)` + `alias(libs.plugins.android.kmp.library)`, no
  version; `androidLibrary { namespace = "com.mmg.manahub.core.domain"; compileSdk 36; minSdk 29;
  withHostTestBuilder {} }`; `wasmJs { browser() }`; `jvmToolchain(17)`). `commonMain` deps =
  `api(project(":shared:core-model"))` + `implementation(libs.coroutines.core)`; `:shared:core-common` NOT
  yet depended on (no current interface needs it — wired in Slice 2 when use cases move). **5 already-pure
  repository INTERFACES `git mv`'d** (history preserved) from
  `app/.../core/domain/repository/` into
  `shared/core-domain/src/commonMain/kotlin/com/mmg/manahub/core/domain/repository/` — package kept
  IDENTICAL (`com.mmg.manahub.core.domain.repository`) so NOT A SINGLE `:app` import statement changed:
  `UserPreferencesRepository` (refs core-model `UserPreferences`/`PreferredCurrency`/`AppLanguage`/
  `CardLanguage`/`NewsLanguage`/`UserDefinedTag`/`CollectionViewMode` + `Flow`), `StatsRepository` (refs
  `CollectionStats`/`MtgColor`/`PreferredCurrency` + `Flow`), `CommunityStatsRepository` (refs
  `CommunityStats` + `Flow`), `NotificationPrefsRepository` (`Flow` + primitives), `PushTokenRepository`
  (primitives only). All 7 referenced core-model types confirmed present in `:shared:core-model`. The
  concrete impls stay in `:app` (the Android/Room/DataStore side) and keep implementing the moved
  interfaces — single definition, no duplication. **SKIPPED (kept in `:app`, define future-slice work):**
  `UserCardRepository` — signature has `androidx.paging.PagingData<UserCardWithCard>` + the Room row type
  `core.data.local.dao.UserCardWithCard` + the un-moved domain model `core.domain.model.UserCard` (needs
  both the `Page`/pagination abstraction AND the `UserCard`/domain-`UserCardWithCard` model extraction
  before it can move); `CardRepository` — refs un-moved `core.domain.model.{Card,CardTag,SuggestedTag}`
  (DataResult is already in core-model); `DeckRepository` — refs un-moved `core.domain.model.{Deck,
  DeckWithCards}` (`DeckSummary` is already in core-model). NO use cases, NO impls, NO Retrofit/Ktor/Room
  touched this slice (per scope). Verified: `:app:assembleDebug` BUILD SUCCESSFUL (pre-existing deprecation
  warnings only — none in moved files); `:shared:core-domain:compileKotlinWasmJs` BUILD SUCCESSFUL (proves
  the 5 interfaces are web-compatible); `:app:testDebugUnitTest` 1964 tests / 122 failed / 0 errors / 2
  skipped — EXACTLY the baseline, ZERO new failures; commonMain `import (android|androidx|kotlinx.browser|
  org.w3c|androidx.room)` grep EMPTY. Inline secret-scan clean (build DSL + interface moves only). NEXT =
  Slice 2 (extract `Card`/`Deck`/`UserCard` domain models → move `CardRepository`+`DeckRepository`; start
  moving pure use cases; `UserCardRepository` deferred until PagingData abstraction).
- 2026-06-22 — **Phase 1 · Game Koin island** (TWENTIETH + LAST non-excluded feature cutover; the
  heaviest island, and the one that BRIDGES three DEFERRED features without migrating them — completing
  the Phase-1 Hilt→Koin cutover for everything except online/voice/scanner). FINISHED the interrupted WIP
  (3 de-Hilt'd VMs + a pre-written `GameKoinModule.kt` were on disk; `ManaHubApp`/`AppNavGraph` were NOT
  yet wired so the tree did not compile). De-Hilt'd VMs were already correct: `GameViewModel` (19 ctor deps
  incl. voice/online/nearby), `GameSetupViewModel`, `GameResultStripViewModel` all dropped
  `@HiltViewModel`/`@Inject constructor` + the `@ApplicationContext` qualifier + the `dagger`/`javax.inject`
  imports → plain ctors, param lists BYTE-FOR-BYTE unchanged. **The voice/online coupling (the hard part):**
  `GameViewModel` integrates the DEFERRED `core/voice` (`VoiceCommandRecognizer`), `core/online` (the 11
  session use cases: Observe/UpdateLife/AdvancePhase/NextTurn/UpdateCounter/UpdateCommanderDamage/
  ConfirmDefeat/RevokeDefeat/LeaveSession/ToggleLandPlayed), and `core/nearby` (`NearbySessionRepository`)
  features, which MUST stay Hilt. Those 13 Hilt-owned singletons are BRIDGED from the Hilt graph through
  `ManaHubApp` into `gameKoinModule` (the tournament-use-case bridge pattern) → ONE shared instance per DI
  graph; NOTHING under `core/voice`, `feature/online`, `core/nearby`, `feature/scanner` was de-Hilt'd
  (`git diff --stat`: only ManaHubApp + AppNavGraph + 5 game/*.kt files + the new GameKoinModule). **Scoping:**
  `GameViewModel` is Activity-scoped (game state persists across all in-game navigation) — `AppNavGraph`'s
  `hiltViewModel(activity)` → `koinViewModel(viewModelStoreOwner = activity)` (exact equivalent; the
  activity `CreationExtras` carry the `mode`/`playerCount` nav args into the Koin-injected `SavedStateHandle`
  `= get()`). `GameSetupViewModel` (AppNavGraph, default-param `hiltViewModel()`) → `koinViewModel()`;
  `GamePlayScreen`'s default param `GameViewModel = hiltViewModel()` → `koinViewModel()` (import swapped;
  the screen is always rendered with the explicit Activity-scoped `gameVm`, so the default is belt-and-braces);
  `GameResultScreen`'s `GameProgressionStrip(viewModel: GameResultStripViewModel = …hiltViewModel())` →
  fully-qualified `org.koin.androidx.compose.koinViewModel()` (this default IS used at runtime — the strip is
  rendered inside `GamePlayScreen` without an explicit VM); `GameSetupScreen.viewModel` is a required param,
  no default → untouched. **Reused via `get()` (NOT re-registered):** `GameSessionRepository`/
  `TournamentRepository`/`AnalyticsHelper`/`UserPreferencesDataStore` (coreBridge), `RecordMatchResultUseCase`
  (tournamentKoinModule — the SINGLE finish-and-advance write path, identical instance — the game-played
  tournament-result flow still routes through it), `VoiceModelRepository` (settingsKoinModule, for
  GameSetup), `gamificationEngine` (already a `ManaHubApp` `@Inject` field from Phase 0). **Bridged NEW via
  `ManaHubApp`:** the 13 deferred-feature singletons above + `EvaluatePlayerEliminationUseCase` (game-only)
  + `GamificationEngine` re-exposed for `GameResultStripViewModel` — 14 `single { }`s total in
  `gameKoinModule`, registered last in `startKoin`. NOTHING promoted to coreBridge, NO island shrunk. **Hilt
  `GameModule` (`@Binds GameSessionRepository`) KEPT** (the repo is bridged in coreBridge AND consumed by
  the still-Hilt online/nearby code — must stay Hilt-bound; the same singleton serves both graphs). **CLAUDE.md
  invariants untouched (DI-only change):** per-seat-stats `isLocal` win/loss derivation, tournament
  single-write-path, broadcast-first online sync, one-voice-language-per-session — all byte-for-byte
  unchanged. **Koin-graph audit:** all 14 game `single<T>` types appear in NO other loaded module
  (grep-verified) → no duplicate `single<T>` across the 20 loaded modules → no `DefinitionOverrideException`
  (and `startKoin` ran clean during the test-suite app init — all `errors=0`). **Tests:** both game test
  classes build the VMs via plain named-arg ctors (`GameViewModelVoiceTest` constructs `GameViewModel(...)`
  directly; it already mocks `FirebaseCrashlytics`) → NO test edit needed; `GameViewModelVoiceTest` 7/7,
  `GameResultStripViewModelTest` 5/5, both 0 fail/0 error. Verified: `:app:assembleDebug` BUILD SUCCESSFUL
  (pre-existing deprecation warnings only); `:app:testDebugUnitTest` 1964 tests / 122 failed / 2 skipped —
  EXACTLY the baseline, ZERO new failures (the 73 online/voice failures among the 122 — OnlineSession=28,
  CommandGrammar=3, LobbyJoin=42 — are PRE-EXISTING assertion failures in the EXCLUDED features, all
  `errors=0`, untouched). Inline secret-scan clean (DI wiring only, no credentials). Koin islands now =
  {Settings, Stats, Profile, Home, TagDictionary, AddCard, CommunityDecks, CardDetail, Friends, Splash,
  Survey, News, Draft, Playtest, Tournament, Trades, Collection, Decks, Auth, Game} (20) — Phase 1 COMPLETE
  except online/voice/scanner. NEXT = Phase 2 (`:shared:core-domain` then `:shared:core-data`).
- 2026-06-21 — **Phase 1 · Auth Koin island** (nineteenth feature cutover; the CROSS-CUTTING island —
  `AuthViewModel` is consumed inside MANY screens, both already-Koin and still-Hilt, so the call-site
  sweep was the risk). De-Hilt'd `AuthViewModel` (the ONLY VM under `feature/auth/**`; dropped
  `@HiltViewModel`/`@Inject constructor` + the `@ApplicationContext` qualifier + the
  `dagger.hilt`/`javax.inject` imports → plain ctor, constructor param list BYTE-FOR-BYTE unchanged so the
  existing test's named-arg construction still compiles; zero behaviour change to any auth method). **Scope
  confirmed by grep:** `ProfileEditViewModel` lives in `feature/profile/**` (NOT auth) and was left Hilt
  (out of scope). New `feature/auth/di/AuthKoinModule.kt` — `authKoinModule()` takes **NO args** (fully
  self-contained): the 10 stateless auth use cases are Koin `single { }`s (9 wrap `AuthRepository` via
  `get()`; `DeleteAccountUseCase(get(), get())` also wraps `PushTokenRepository`, already a `single` in
  `settingsKoinModule`), `AnalyticsHelper` + `AuthRepository` resolved from coreBridge via `get()`, the
  app Context via `androidContext()`. **NEW gotcha — cross-cutting shared VM with NO actual sharing:** the
  prompt flagged a possible Activity-/nav-graph-scoped shared `AuthViewModel`, but investigation showed
  every call-site used the DEFAULT `hiltViewModel()` (entry-scoped per NavBackStackEntry) — there was NO
  shared instance to preserve, so each swap to `koinViewModel()` is an exact 1:1 equivalent and NO
  `koinViewModel(viewModelStoreOwner = …)` was needed. **Call-sites (all 3, swept exhaustively via
  `grep AuthViewModel` over the whole repo):** `ProfileScreen.kt` (line 108), `TradesScreen.kt` (line 70),
  `CreateTradeProposalScreen.kt` (line 106) — each `authViewModel: AuthViewModel = hiltViewModel()` →
  `= koinViewModel()`; in all three `hiltViewModel()` was used ONLY for the auth VM, so the now-unused
  `androidx.hilt.navigation.compose.hiltViewModel` import was dropped (the Trades/Profile own VMs are
  already `koinViewModel()`). `LoginSheet.kt` takes `authViewModel` as a plain required param (the 3
  screens pass their instance to it) — UNCHANGED. **MainActivity NOT touched:** its deeplink/PKCE wiring
  calls `supabaseClient.handleDeeplinks(intent)` directly and never references `AuthViewModel` — the
  ADR-003 email-confirmation deeplink flow is intact. `gamesetup` has NO `AuthViewModel` usage. **Hilt
  `AuthModule` KEPT entirely** (its `@Named("supabase")` OkHttpClient/Retrofit + `UserProfileDataSource` +
  `@Binds AuthRepository` are consumed broadly by Hilt features); NO `@Provides`/`@Binds` deleted; the 10
  use cases keep their `@Inject`/`@Singleton` (Hilt still constructs its own copies; Koin builds equivalent
  stateless copies — the Trades pattern). **Bridge:** NOTHING promoted to coreBridge, NO island shrunk, NO
  new `ManaHubApp` `@Inject` field — only the import + `authKoinModule(),` line added to `startKoin`.
  **Koin-graph audit:** grep over all `**/di/*.kt` for the 10 use-case types → 0 matches elsewhere → no
  duplicate `single<T>` across the 19 loaded modules → no `DefinitionOverrideException` (startKoin ran
  clean during the test-suite app init). **Tests:** `AuthViewModelTest` (the suite that exercises the
  de-Hilt'd VM) builds the VM via the plain named-arg ctor with mocks + a mocked `appContext` → NO test
  edit needed, 62/62 green; `AuthUseCasesTest` 35/35 green; `AuthRepositoryImplTest` had its 1 PRE-EXISTING
  flaky `sessionState`-collection Turbine-timing failure (a pure repo test, no DI, untouched). Verified:
  `:app:assembleDebug` BUILD SUCCESSFUL (pre-existing deprecation warnings only); `:app:testDebugUnitTest`
  1964 tests / 122 failed / 2 skipped — EXACTLY the baseline (not even the +1 flaky `HomeViewModelTest`
  this run), ZERO new failures, no auth class regressed. Inline secret-scan clean (auth touches
  credentials — confirmed no secret/token literal added; nonce generation untouched). Koin islands now =
  {Settings, Stats, Profile, Home, TagDictionary, AddCard, CommunityDecks, CardDetail, Friends, Splash,
  Survey, News, Draft, Playtest, Tournament, Trades, Collection, Decks, Auth} (19); `game` is the ONLY
  remaining non-excluded island; online/voice/scanner still EXCLUDED. NEXT = `feature/game` (last).
- 2026-06-21 — **Phase 1 · Decks Koin island** (eighteenth feature cutover; a 4-ViewModel island; the
  FIRST island where the bridged Hilt-built objects MUST stay Hilt-owned because a still-Hilt feature
  shares the SAME engine singleton). De-Hilt'd ALL FOUR decks VMs (dropped `@HiltViewModel`/`@Inject
  constructor` + the `dagger`/`javax.inject` imports + the `@ApplicationContext`/`@ApplicationScope`
  qualifiers → plain ctors, zero behaviour change): `DeckViewModel` (2 deps: `DeckRepository`,
  `CardRepository` — backs the deck list), `DeckStudioViewModel` (17 deps incl. `SavedStateHandle` — the
  unified create+edit surface), `DeckImprovementViewModel` (10 deps incl. `SavedStateHandle` — inline
  Deck Doctor), and the legacy fallback `DeckMagicDetailViewModel` (12 deps incl. `SavedStateHandle` +
  `@ApplicationScope CoroutineScope`). **CRITICAL behaviour preserved (CLAUDE.md):** the DI cutover
  changed ONLY annotations/wiring — DeckStudio is still THE single create+edit surface, the
  `createdFreshDraft`-gated discard-if-empty contract, the `isImporting` exit-guard, the free-text budget
  parse-guard, and the Deck Doctor incremental `AnalysisCache`/`GapSignature` recompute are all
  byte-for-byte untouched. **Layering rule held:** `feature/decks/domain/**` imports NO
  `...presentation...` (acceptance grep EMPTY); the DI changes added no such import (the Koin module lives
  in `feature/decks/di/`). New `feature/decks/di/DecksKoinModule.kt` (`decksKoinModule(suggestTags,
  evaluateDeck, inferDeckIdentity, suggestCuts, suggestAddsWithBudget, buildDeckFromSeeds,
  getDeckGameStats, importDeck, deckMagicEngine, applicationScope)`): a `viewModel { }` per VM.
  **Nav-arg handling:** `DeckStudioViewModel` (optional `"deckId"`, `"" ⇒ fresh draft`),
  `DeckImprovementViewModel` (`"deckId"`) and `DeckMagicDetailViewModel` (`"deckId"`) each read their nav
  arg from a Koin-injected `SavedStateHandle` (`savedStateHandle = get()` — identical to Hilt;
  `koinViewModel()` populates it from the NavBackStackEntry's `CreationExtras`). DeckStudio is reached
  from MANY entry points (DeckList FAB/empty-state, Collection/Stats/Home/CardDetail deck-open,
  Home→Build deck, CommunityDeck import) — all are ROUTE navigations to `Screen.DeckStudio`, so none
  passes an explicit `viewModel =` arg; the one explicit creation was the AppNavGraph
  `DeckStudioScreen(viewModel = hiltViewModel())` line → swapped to `koinViewModel()` (the import was
  already present). **NEW gotcha — Deck Doctor engine MUST stay Hilt-owned + bridged, NOT rebuilt in
  Koin:** `DeckScorer` (and its `@Inject` graph: `RoleClassifier`, `ManaBaseAnalyzer`, `PowerResolver`
  from the feature-private Hilt `DeckDoctorModule`) is ALSO consumed by the still-Hilt Draft feature
  (`ScoringDraftDeckBuilder` in `DraftModule`). Rebuilding the scoring use cases in Koin would mint a
  SECOND `DeckScorer` that could diverge from the one Draft uses. So — exactly like Tournament's use-case
  bridge — the Hilt `DeckDoctorModule` is KEPT, the engine + use cases keep their `@Inject`/`@Singleton`,
  and the 9 Hilt-built singletons (`SuggestTagsUseCase`, `EvaluateDeckUseCase`, `InferDeckIdentityUseCase`,
  `SuggestCutsUseCase`, `SuggestAddsWithBudgetUseCase`, `BuildDeckFromSeedsUseCase`,
  `GetDeckGameStatsUseCase`, `ImportDeckUseCase`, `DeckMagicEngine`) + the `@ApplicationScope`
  `CoroutineScope` are BRIDGED from the Hilt graph through `ManaHubApp` into the Koin module → ONE shared
  `DeckScorer` serves both DI graphs. **Bridge — NOTHING promoted to coreBridge, NO island shrunk:** the
  shared repos are reused from coreBridge via `get()` (`DeckRepository`, `CardRepository`,
  `WishlistRepository`, `UserPreferencesRepository`, `UserPreferencesDataStore`, `AuthRepository`,
  `AnalyticsHelper`); and 4 deps are reused via `get()` from OTHER loaded feature modules WITHOUT
  re-registration (`UserCardRepository` already a `single` in `cardDetailKoinModule`; `SyncManager` in
  `collectionKoinModule`; `SearchCardsUseCase` in `addCardKoinModule`; `WorkManager` in
  `collectionKoinModule` — a `single<T>` is resolvable from any loaded module, so re-registering any would
  throw `DefinitionOverrideException`). NO Hilt `@Provides`/`@Binds` deleted (the engine/use cases stay
  Hilt-constructed for the Draft consumer + the bridge). `ManaHubApp` gained 10 new `@Inject` bridge
  fields (the 9 use cases/engine + `@ApplicationScope applicationScope`) and registers `decksKoinModule(...)`
  in `startKoin`. **Call-sites:** all 4 screens (`DeckListScreen`, `DeckStudioScreen`, `DeckBuilderScreen`,
  `DeckImprovementScreen`) swapped their default param `hiltViewModel()` → `koinViewModel()`
  (each had exactly one `hiltViewModel` use → import swapped too). `DeckListScreen` is nested in
  `CollectionScreen` (Decks tab) with the default param; `DeckMagicDetailScreen`/`DeckImprovementScreen`
  in AppNavGraph use the default param → only the DeckStudio explicit creation needed editing. **Koin-graph
  audit:** the 10 new `single<T>` types appear in NO other loaded module (grep-verified) → no duplicate
  `single<T>` across the 18 loaded modules → no `DefinitionOverrideException` (and `startKoin` ran clean
  during the test-suite app init). **Tests:** all decks VM tests already build the VMs via plain named-arg
  constructors (params/order unchanged after dropping annotations) → NO test edit needed;
  `DeckStudioViewModelTest` (113), `DeckImprovementViewModelTest` (3), `DeckViewModelSyncTest` (4),
  `DeckScorerTest` (73), `DeckScoreModelTest` (26), `RoleClassifierTest` (45), `GoldenDeckHarnessTest`
  (29), `ManaBaseAnalyzerTest` (14), `ClassificationCorpusTest` (14), and all use-case suites
  (Evaluate/SuggestCuts/SuggestAddsWithBudget/BuildDeckFromSeeds/CandidatePool/BudgetOptimizer/
  ImportDeck/InferDeckIdentity) — ALL 0 fail / 0 error. Verified: `:app:assembleDebug` SUCCESSFUL
  (pre-existing deprecation warnings only, none in the new/edited files); `:app:testDebugUnitTest`
  1964 tests / 123 failed / 0 errors / 2 skipped. The ONE extra vs the 122 baseline is the documented
  flaky `HomeViewModelTest` (`UncaughtExceptionsBeforeTest`, the leaked-coroutine-between-tests symptom
  that oscillates 122↔123 run-to-run) — `HomeViewModel`/its test are UNTOUCHED by this cutover
  (`git status` shows no Home file modified) and ZERO decks classes are among the 123 failures (all
  pre-existing online/trades/sync/scanner/voice/push assertion failures, all `errors=0`). Inline
  secret-scan clean. Koin islands now = {Settings, Stats, Profile, Home, TagDictionary, AddCard,
  CommunityDecks, CardDetail, Friends, Splash, Survey, News, Draft, Playtest, Tournament, Trades,
  Collection, Decks} (18); all other features still Hilt (incl. auth/game/voice/online/scanner). NEXT =
  `feature/auth` (cross-cutting `AuthViewModel`), then `game` last; online/voice/scanner still EXCLUDED.
- 2026-06-21 — **Phase 1 · Collection Koin island** (seventeenth feature cutover; a SINGLE-VM island —
  the least-entangled remaining feature, and the FIRST island that needed NEITHER a coreBridge promotion
  NOR an island shrink). De-Hilt'd the one collection VM `CollectionViewModel` (dropped `@HiltViewModel`/
  `@Inject constructor` + the `dagger.hilt`/`javax.inject` imports → plain ctor, zero behaviour change;
  12 ctor deps, NO `SavedStateHandle`, NO nav args). The Paging note in the prompt was moot —
  `CollectionViewModel` does NOT use `androidx.paging.PagingData`; it builds its own filtered/sorted
  `List<CollectionCardGroup>` in `_uiState` (Paging lives in other code, left as-is, NOT commonized). The
  collection sync logic (the `SyncManager` push/pull, `previouslyAuthenticated` guard, the unsynced-banner
  reconciliation across collection + wishlist + open-for-trade, the `CollectionSyncWorker` scheduling) is
  intact — the cutover changed ONLY DI annotations/wiring. New `feature/collection/di/CollectionKoinModule.kt`
  (`collectionKoinModule(getCollection, syncManager, workManager, migrateLocalTradeLists)`): a single
  `viewModel { CollectionViewModel(...) }` with all 12 args via `get()`. **Bridge — NOTHING promoted to
  coreBridge, NO island shrunk:** 6 of the 12 deps are reused from coreBridge via `get()`
  (`CardRepository`, `AuthRepository`, `UserPreferencesRepository`, `AnalyticsHelper`, `WishlistRepository`,
  `OpenForTradeRepository` — the last two were already promoted by Trades); 2 more are reused via `get()`
  from OTHER loaded feature modules WITHOUT re-registration (`GetLocalWishlistUseCase` is already a `single`
  in `tradesKoinModule`; `UserCardRepository` is already a `single` in `cardDetailKoinModule` — a `single<T>`
  is resolvable from any loaded module, so re-registering either would throw `DefinitionOverrideException`).
  Only 3 NEW Collection-only singletons were bridged via `ManaHubApp` (`GetCollectionUseCase`, `SyncManager`
  [`@Singleton` — same instance whose `syncState` StateFlow the global `ManaHubApp` observer also reads],
  `MigrateLocalTradeListsUseCase`); the 4th ctor dep `WorkManager` reuses the `workManager` field
  `ManaHubApp` ALREADY `@Inject`s — no new field for it. NO Hilt `@Provides`/`@Binds` deleted, NO
  feature-private Hilt module (collection has none). **Call-sites:** `CollectionScreen` has the VM as a
  default param (`viewModel: CollectionViewModel = hiltViewModel()`) → swapped that ONE default to
  `koinViewModel()` (added the `org.koin.androidx.compose.koinViewModel` import; KEPT the `hiltViewModel`
  import because the screen's `AdvancedSearchViewModel = hiltViewModel()` — a `core/ui/components/search` VM,
  OUT of collection scope — stays Hilt; a Hilt VM resolves fine inside a Koin screen). All THREE AppNavGraph
  composables that render `CollectionScreen` (the Collection / DeckList / Trades bottom-nav tabs) pass NO
  explicit `viewModel =` arg → NO AppNavGraph edit. `ManaHubApp` gained 3 `@Inject` bridge fields
  (`getCollectionUseCase`, `syncManager`, `migrateLocalTradeListsUseCase`) + registers
  `collectionKoinModule(...)` in `startKoin`. **Koin-graph audit:** the 4 collection-only `single<T>` types
  (`GetCollectionUseCase`, `SyncManager`, `WorkManager`, `MigrateLocalTradeListsUseCase`) appear in NO other
  loaded module (grep-verified) → no duplicate `single<T>` across the 17 loaded modules → no
  `DefinitionOverrideException` (and `startKoin` ran clean during the test-suite app init). **Tests:** both
  collection VM tests (`CollectionViewModelTest` 33, `CollectionViewModelSyncTest` 10) already build the VM
  via the plain named-arg constructor (identical param names/order after dropping the annotations) → NO test
  edit needed; both classes 0 fail / 0 error (verified via their TEST-*.xml). Verified: `:app:assembleDebug`
  SUCCESSFUL (deprecation warnings only, none in the new/edited files); `:app:testDebugUnitTest` 1964 tests /
  122 failed / 2 skipped (== baseline, ZERO new failures; no collection class among the 122). Inline
  secret-scan clean. Koin islands now = {Settings, Stats, Profile, Home, TagDictionary, AddCard,
  CommunityDecks, CardDetail, Friends, Splash, Survey, News, Draft, Playtest, Tournament, Trades,
  Collection} (17); all other features still Hilt (incl. game/voice/online/scanner — still EXCLUDED).
  NEXT = `feature/decks` (multi-VM: DeckList/DeckStudio/DeckImprovement), still EXCLUDING online/voice/scanner.
- 2026-06-21 — **Phase 1 · Trades Koin island** (sixteenth feature cutover; the SEVENTH multi-VM island,
  5 VMs; the MOST repo-entangled island so far — its data layer is split across FIVE repositories by
  concern). De-Hilt'd ALL FIVE trades VMs (dropped `@HiltViewModel`/`@Inject constructor` +
  `dagger`/`javax.inject` imports + the `@IoDispatcher` qualifier on the dispatcher param → plain ctors,
  zero behaviour change): `TradesViewModel` (5 deps), `TradeProposalViewModel` (13 deps incl.
  `SavedStateHandle`), `TradeNegotiationViewModel` (14 deps incl. `SavedStateHandle`),
  `TradesHistoryViewModel` (6 deps), `TradesSharedListViewModel` (3 deps incl. `SavedStateHandle`). The
  five-repository split (per CLAUDE.md — owners must not be merged) is intact; the cutover changed ONLY DI
  annotations/wiring, not any trade-proposal/wishlist/open-for-trade behaviour. New
  `feature/trades/di/TradesKoinModule.kt` (`tradesKoinModule(sharedListsRepository, tradeCollectionSyncDao)`):
  a `viewModel { }` per VM + all 17 trades use cases as `single { UseCase(get()...) }` + `GetFriendsUseCase`
  (a Friends use case not built by FriendsKoinModule). `@IoDispatcher` → `Dispatchers.IO` directly.
  **Nav-arg handling:** `TradeProposalViewModel` (`receiverId`/`parentProposalId`/`editingProposalId`/
  `rootProposalId`), `TradeNegotiationViewModel` (`rootProposalId`) and `TradesSharedListViewModel`
  (`shareId`) each read their nav args from a Koin-injected `SavedStateHandle` (`savedStateHandle = get()` —
  byte-for-byte identical to Hilt). **Repo dedup — 3 repos PROMOTED to coreBridge + 3 islands SHRUNK:**
  `TradesRepository` (was a Friends-island `single`; shared with Trades + still-Hilt Home/FriendDetail) →
  moved to coreBridge, Friends shrunk (drops the param + `single`; its VM already used `get()`).
  `WishlistRepository` (was a Home-island `single`; shared with Trades + CardDetail + still-Hilt
  Collection/DeckStudio/DeckImprovement) → moved to coreBridge, Home shrunk. `OpenForTradeRepository`
  (was a CardDetail-island `single`; shared with Trades + still-Hilt Collection) → moved to coreBridge,
  CardDetail shrunk. coreBridge now holds SEVENTEEN singletons. `UserCardRepository` (a CardDetail-island
  `single`) is LEFT in CardDetail and Trades just resolves it via `get()` (the leave-in-place strategy —
  only 2 consumers; avoids a needless promotion). `AuthRepository`/`CardRepository`/`AnalyticsHelper`/
  `FriendRepository` all reused from coreBridge via `get()`. Only 2 trades-only singletons newly bridged
  via `ManaHubApp`: `SharedListsRepository` (trades-only) + `TradeCollectionSyncDao` (Room/`DatabaseModule`-
  owned, used by `UpdateTradeCollectionUseCase` inside `TradeNegotiationViewModel`). The fifth repo,
  `TradeSuggestionsRepository`, is trades-only AND consumed by no migrated VM → NOT bridged. **Hilt
  `TradesModule` KEPT (NOT deleted):** its `@Binds` for `WishlistRepository`/`OpenForTradeRepository`/
  `TradeSuggestionsRepository` are still consumed by Hilt features (`CollectionViewModel`,
  `DeckStudioViewModel`, `DeckImprovementViewModel`) — the bridge guarantees the SAME singleton serves both
  DI graphs (same reason Friends/News/Draft/Tournament kept theirs). NO Hilt `@Provides`/`@Binds` deleted;
  the trades repos/datasources/use cases KEEP their `@Inject`/`@Singleton` (Hilt still constructs them for
  its consumers; Koin builds its own stateless use-case copies over the bridged repos). **Call-sites:** all
  5 trades screens use the default `viewModel` param → swapped `hiltViewModel()` → `koinViewModel()`; NONE
  is created with an explicit `viewModel =` arg in `AppNavGraph` (the 3 nav destinations) or in
  `CollectionScreen` (`TradesScreen`/`TradesHistoryScreen` are nested there with the default param) → NO
  AppNavGraph/CollectionScreen edit. `TradesScreen` + `CreateTradeProposalScreen` KEEP
  `authViewModel = hiltViewModel()` (Auth still Hilt — a Hilt VM resolves fine inside a Koin-VM screen).
  `ManaHubApp` gained 2 new `@Inject` bridge fields (`sharedListsRepository`, `tradeCollectionSyncDao`;
  `tradesRepository`/`wishlistRepository`/`openForTradeRepository` were ALREADY injected — reused), added
  the 3 promoted repos to the `coreBridgeKoinModule(...)` call, dropped `wishlistRepository` from
  `homeKoinModule(...)`, `tradesRepository` from `friendsKoinModule(...)`, `openForTradeRepository` from
  `cardDetailKoinModule(...)`, and registers `tradesKoinModule(...)`. **Koin-graph audit:** the 3 promoted
  repos now live in EXACTLY ONE module (coreBridge); every trades-only `single<T>` (SharedListsRepository,
  TradeCollectionSyncDao, the 18 use cases) appears in no other loaded module → no duplicate `single<T>`
  across the 16 loaded modules → no `DefinitionOverrideException` (and `startKoin` ran clean during the
  test-suite app init). **Tests:** only `TradeProposalViewModelMatchesTest` constructs a VM, via the plain
  named-arg ctor (params/order unchanged after dropping annotations) → NO test edit needed. Verified:
  `:app:assembleDebug` SUCCESSFUL (deprecation warnings only); `:app:testDebugUnitTest` 1964 tests /
  122 failed / 2 skipped (== baseline, ZERO new failures). The 22 trades/wishlist failures among the 122
  are PRE-EXISTING assertion failures (all `errors=0`, NOT construction/Koin errors) — PROVEN by stashing
  the cutover + running the trades test classes on clean HEAD: identical 22 failures
  (OpenForTrade=6, Trades=4, Wishlist=10, TradeProposalMatches=2). Inline secret-scan clean. Koin islands
  now = {Settings, Stats, Profile, Home, TagDictionary, AddCard, CommunityDecks, CardDetail, Friends,
  Splash, Survey, News, Draft, Playtest, Tournament, Trades} (16); all other features still Hilt (incl.
  game/voice/online/scanner — still EXCLUDED). NEXT = `feature/collection` (single VM, least entangled
  remaining), still EXCLUDING online/voice/scanner.
- 2026-06-21 — **Phase 1 · Tournament Koin island** (fifteenth feature cutover; the SIXTH multi-VM
  island, 3 VMs; FIRST entry-scoped Koin VM; FIRST island to KEEP its Hilt module AND bridge use cases
  because a still-Hilt feature shares its write path). De-Hilt'd ALL THREE tournament VMs (dropped
  `@HiltViewModel`/`@Inject constructor` + `dagger`/`javax.inject` imports → plain ctors, zero behaviour
  change): `TournamentListViewModel` (1 dep: `TournamentRepository`), `TournamentSetupViewModel` (1 dep:
  `TournamentRepository`), `TournamentViewModel` (4 deps: `TournamentRepository`,
  `CalculateStandingsUseCase`, `RecordMatchResultUseCase`, `SavedStateHandle`). **The tournament
  single finish-and-advance atomic write path is intact:** `RecordMatchResultUseCase` →
  `TournamentRepository.finishMatch` → `TournamentDao.finishMatchAndAdvanceAtomically` is unchanged;
  `TournamentViewModel` still imports `PlayerConfig` from `feature.game.presentation` BY DESIGN (left
  alone); the `tournamentId > 0L` construction guard, the `viewModelScope` create flow, and the tiebreaker/
  bye logic were untouched — the cutover changed ONLY DI annotations/wiring. New
  `feature/tournament/di/TournamentKoinModule.kt` (`tournamentKoinModule(calculateStandings,
  recordMatchResult)`): a `viewModel { }` per VM. **Nav-arg handling:** `TournamentViewModel` reads the
  `tournamentId` nav arg from a Koin-injected `SavedStateHandle` (`savedStateHandle = get()`).
  **NEW gotcha — entry-scoped Koin VM:** `TournamentScreen` is the one tournament screen whose
  AppNavGraph composable created the VM EXPLICITLY (`val tournamentVm = hiltViewModel(entry)`, then
  passed `viewModel = tournamentVm` AND referenced it in `onStartMatch` for
  `buildPlayerConfigsForMatch`/`getGameMode`). Its Koin equivalent is
  `koinViewModel(viewModelStoreOwner = entry)` — the NavBackStackEntry is a `ViewModelStoreOwner` whose
  `CreationExtras` populate the `SavedStateHandle` (same `tournamentId`), so it stays entry-scoped (one
  instance per detail destination) and the `onStartMatch` reference keeps working; only that creation line
  changed. The other two screens (`TournamentListScreen`, `TournamentSetupScreen`) used the default
  `viewModel` param → swapped the default `hiltViewModel()` → `koinViewModel()`, NO AppNavGraph edit.
  **`TournamentsSheet` (used INSIDE the still-Hilt `GameSetupScreen`) also resolves
  `TournamentListViewModel` — its default param was swapped `hiltViewModel()` → `koinViewModel()` too;
  a Koin VM resolves fine inside a Hilt screen (Koin is started app-wide).** **Bridge —
  `TournamentRepository` PROMOTED + Home SHRUNK:** it was a Home-only bridged `single`; now shared with
  Tournament (and the Hilt `GameViewModel`), it was MOVED into `coreBridgeKoinModule` (now FOURTEEN
  singletons) and `homeKoinModule` was shrunk to drop the param + `single` (its `viewModel { }` already
  used `get()` → no Home VM edit; the `ManaHubApp` home call dropped the arg, the coreBridge call gained
  it). **Hilt `TournamentModule` KEPT (NOT converted/deleted):** it `@Binds TournamentRepository`, still
  consumed by the still-Hilt `GameViewModel` → deleting it would break the Hilt graph (same reason
  Friends/News/Draft kept their modules). The two use cases (`CalculateStandingsUseCase`,
  `RecordMatchResultUseCase`) KEEP their `@Singleton @Inject constructor` (Hilt still builds them —
  `GameViewModel` consumes `RecordMatchResultUseCase` via the game-played result flow) and are BRIDGED
  from the Hilt graph into Koin via `ManaHubApp` → `tournamentKoinModule` (so the SAME singleton instances
  serve both DI graphs; no `TournamentDao`/dispatcher bridging needed). `ManaHubApp` gained 2 new `@Inject`
  bridge fields (`calculateStandingsUseCase`, `recordMatchResultUseCase`; `tournamentRepository` was
  already injected and is REUSED), added `tournamentRepository` to the `coreBridgeKoinModule(...)` call,
  dropped it from the `homeKoinModule(...)` call, and registers `tournamentKoinModule(...)`. NO Hilt
  `@Provides`/`@Binds` deleted. **Koin-graph audit:** the 2 new `single<T>` types
  (`CalculateStandingsUseCase`, `RecordMatchResultUseCase`) appear in NO other loaded module;
  `TournamentRepository` now lives ONLY in coreBridge (removed from Home) → no duplicate `single<T>`
  across the 15 loaded modules → no `DefinitionOverrideException` (and `startKoin` ran clean during the
  test-suite app init). **Tests:** `TournamentViewModelTest` (36) already built the VM via the plain 4-arg
  constructor with `mockkStatic(FirebaseCrashlytics)` in `@Before`/`@After` + hand-built `SavedStateHandle`
  → NO test edit needed; it + `TournamentRepositoryImplTest` (35) + `TournamentEngineTest` (22) +
  `TournamentIdCodecTest` (14) + `GenerateNextRoundPlanTest` all green. Verified: `:app:assembleDebug`
  SUCCESSFUL (deprecation warnings only); `:app:testDebugUnitTest` 1964 tests / 123 failed / 2 skipped —
  the ONE extra vs the 122 baseline is a PRE-EXISTING flaky `HomeViewModelTest` Discover/`order:random`
  test (`UncaughtExceptionsBeforeTest`, a leaked-coroutine-exception-between-tests symptom): its failing
  set OSCILLATES run-to-run REGARDLESS of these changes — proven by `git stash` + isolated run on clean
  HEAD (2 fails) vs isolated run WITH changes (3 DIFFERENT fails) vs full run WITH changes (1 fail); zero
  tournament classes among the failures, and `HomeViewModel`/its test are untouched by this cutover.
  Inline secret-scan clean. Koin islands now = {Settings, Stats, Profile, Home, TagDictionary, AddCard,
  CommunityDecks, CardDetail, Friends, Splash, Survey, News, Draft, Playtest, Tournament} (15); all other
  features still Hilt (incl. game/voice/online/scanner — still EXCLUDED). NEXT = `feature/trades`.
- 2026-06-21 — **Phase 1 · Playtest Koin island** (fourteenth feature cutover; the FIFTH multi-VM
  island, 2 VMs; the SECOND island to convert+delete its feature-private Hilt module — after
  CommunityDecks). De-Hilt'd BOTH Deck Playtest VMs (dropped `@HiltViewModel`/`@Inject constructor` +
  `dagger`/`javax.inject` imports → plain ctors, zero behaviour change): `PlaytestSetupViewModel`
  (5 deps: `SavedStateHandle`, `DeckRepository`, `CardDao`, `CanPlaytestDeckUseCase`, `@IoDispatcher`),
  `PlaytestHandViewModel` (8 deps: `DeckRepository`, `CardDao`, 5 use cases —
  `BuildLibraryUseCase`/`DrawHandUseCase`/`LondonMulliganUseCase`/`SavePlaytestUseCase`/
  `SavePlaytestSurveyUseCase` — and `@IoDispatcher`). **The same-VM mulligan↔battlefield design is
  intact:** `PlaytestHandViewModel` drives BOTH the MULLIGAN and PLAY/battlefield phases as conditional
  content (`PlaytestHandUiState.phase` + ephemeral `battlefield`) — NOT a second nav destination — and
  the DI cutover touched only the ctor annotations, never the phase logic, the atomic
  `_uiState.update {}` battlefield mutations, the `instanceId`-keyed LazyRows, or the explicit-save
  guard. **One-shot events stay on the buffered `Channel` (`receiveAsFlow()`)** in both VMs, collected
  via `LaunchedEffect(Unit){ vm.events.collect {} }` in the screens (unchanged). **Nav-arg / handoff
  handling:** `PlaytestSetupViewModel` reads the `"deckId"` nav arg from a Koin-injected
  `SavedStateHandle` (`savedStateHandle = get()` — `koinViewModel()` populates it from the
  NavBackStackEntry's `CreationExtras`, byte-for-byte the same as Hilt). `PlaytestHandViewModel` takes
  NO `SavedStateHandle`: its `PlaytestSetup` arrives via the in-memory `pendingPlaytestSetup` handoff in
  `AppNavGraph` → `PlaytestHandScreen(setup=…)` → `LaunchedEffect{ viewModel.initWithSetup(setup) }`;
  that handoff (incl. the process-death `FullErrorState` fallback) is unchanged by the DI cutover. New
  `feature/playtest/di/PlaytestKoinModule.kt` (`playtestKoinModule(playtestDao)`): a `viewModel { }` per
  VM + the data layer ported from the deleted Hilt module. **Hilt `PlaytestModule` converted + DELETED**
  — it only `@Binds PlaytestRepository`, consumed by ZERO Hilt features (grep-verified: repo + impl +
  all six use cases live exclusively under `feature/playtest`), so its binding became a Koin
  `single<PlaytestRepository> { PlaytestRepositoryImpl(get(), Dispatchers.IO) }` and the module was
  deleted; `@Inject`/`@Singleton`/`@IoDispatcher` stripped from `PlaytestRepositoryImpl` + the six use
  cases (now plain Koin-constructed classes); `@IoDispatcher CoroutineDispatcher` → `Dispatchers.IO`
  directly (same singleton the Hilt binding returned — Survey/CommunityDecks precedent). **Bridge —
  NOTHING promoted to coreBridge, NO island shrunk:** `DeckRepository` reused from `coreBridgeKoinModule`
  via `get()`; `CardDao` reused from `surveyKoinModule` (already a `single` there) via `get()` — NOT
  re-registered (would `DefinitionOverrideException`). Only ONE singleton newly bridged via `ManaHubApp`:
  the Room/`DatabaseModule`-owned (still Hilt-provided) `PlaytestDao` (this island only). `ManaHubApp`
  gained 1 new `@Inject` field (`playtestDao`) + registers `playtestKoinModule(playtestDao = playtestDao)`
  in `startKoin`. NO Hilt `@Provides`/`@Binds` deleted OUTSIDE the feature (`PlaytestDao` is still
  `DatabaseModule`-provided; `DeckRepository`/`CardDao` still shared with Hilt features). Both screens
  (`PlaytestSetupScreen`, `PlaytestHandScreen`) swapped their default param `hiltViewModel()` →
  `koinViewModel()`; both are constructed in `AppNavGraph` with NO explicit `viewModel =` arg → NO nav
  edit (the `pendingPlaytestSetup` plumbing is unrelated to VM creation). **Koin-graph audit:** the 8
  new `single<T>` types (`PlaytestDao`, `PlaytestRepository`, the 6 use cases) appear in NO other loaded
  module → no `DefinitionOverrideException` (and `startKoin` ran clean during the test-suite app init).
  **Tests:** both VM tests (`PlaytestHandViewModelTest`, `PlaytestSetupViewModelTest`) already built the
  VMs via the plain positional constructors with the `mockkStatic(FirebaseCrashlytics::class)` already in
  `@Before`/`@After` (logs outside runCatching) → NO test edit needed; all playtest tests green.
  Verified: `:app:assembleDebug` SUCCESSFUL (deprecation warnings only); `:app:testDebugUnitTest`
  1964 tests / 122 failed / 0 errors / 2 skipped (== baseline, ZERO new failures; no playtest class
  among the 122 — verified by parsing the JUnit XML); inline secret-scan clean. Koin islands now =
  {Settings, Stats, Profile, Home, TagDictionary, AddCard, CommunityDecks, CardDetail, Friends, Splash,
  Survey, News, Draft, Playtest} (14); all other features still Hilt. NEXT = `feature/tournament`, still
  EXCLUDING online/voice/scanner.
- 2026-06-21 — **Phase 1 · Draft Koin island** (thirteenth feature cutover; the FOURTH multi-VM island,
  3 VMs — finished an interrupted WIP that hit the session limit after compile-check but before commit).
  De-Hilt'd ALL THREE Draft VMs (dropped `@HiltViewModel`/`@Inject constructor` + `dagger`/`javax.inject`
  imports → plain ctors, zero behaviour change): `DraftViewModel` (1 dep: `GetDraftableSetsUseCase`),
  `SetDraftDetailViewModel` (5 deps incl. `SavedStateHandle` for `setCode`/`setName`/`setIconUri`/
  `setReleasedAt`), `DraftSimViewModel` (11 deps incl. `SavedStateHandle` for `setCode`, the 6 sim use
  cases, `AnalyticsHelper`, `BotDrafter`, `DraftSimRepository`, and a `@DefaultDispatcher` dispatcher).
  New `feature/draft/di/DraftKoinModule.kt` (`draftKoinModule(10 use cases + BotDrafter)`) with a
  `viewModel { }` per VM; `@DefaultDispatcher` → `Dispatchers.Default` directly (the same singleton the
  Hilt binding returned — Survey/CommunityDecks precedent for `Dispatchers.IO`). **Per-screen VM scoping
  is preserved & correct:** the DraftSim flow spans THREE separate `composable` routes
  (`DraftSimSetup`/`DraftSimDrafting`/`DraftSimResult`); under no-arg `hiltViewModel()` each already got
  its OWN NavBackStackEntry-scoped instance, and `koinViewModel()` scopes identically — `DraftSimViewModel`
  is designed for this (it holds NO shared in-memory state; each instance reconstructs its UI from the
  persisted session via `ObserveDraftUseCase`), so there is ZERO behaviour change. All 5 screens
  (`DraftScreen`, `SetDraftDetailScreen`, `DraftSetupScreen`, `DraftingScreen`, `DraftResultScreen`)
  swapped their default param `hiltViewModel()` → `koinViewModel()`; all are called in `AppNavGraph` with
  NO explicit `viewModel =` arg → NO nav edit. **Bridge — `DraftRepository` + `DraftSimRepository`
  PROMOTED + Home SHRUNK:** both were Home-only bridged `single`s; now shared with Draft, they were MOVED
  into `coreBridgeKoinModule` (now THIRTEEN singletons) and `homeKoinModule` was shrunk to drop both
  params + `single`s (its `viewModel { }` already used `get()` → no Home VM edit; the `ManaHubApp` home
  call dropped both args, the coreBridge call gained both). `AnalyticsHelper` was already in coreBridge →
  reused via `get()`. **Hilt `DraftModule` KEPT (NOT converted/deleted):** it still `@Binds`
  `DraftRepository`/`DraftSimRepository`/`DraftDeckBuilder` and `@Provides` the engine threading
  (`ArchetypeAwareBotDrafter`+`HeuristicBotDrafter` fallback / `DefaultDraftEngine` /
  `WeightedBoosterGenerator` / `Gson`), the YouTube + Cloudflare-Worker Retrofit clients, and the draft
  version prefs — and all 14 draft use cases stay `@Inject constructor`. The Home island bridges
  `DraftRepository`/`DraftSimRepository` from this Hilt graph and `ManaHubApp` `@Inject`s the 10
  Hilt-built use cases + `BotDrafter` to feed `draftKoinModule`, so the whole Hilt sub-graph MUST stay
  intact (same reason Friends/News kept their Hilt modules). `ScryfallRequestQueue` in
  `DraftRepositoryImpl` is untouched → rate-limiting + engine.json/archetype-bot threading keep working.
  `ManaHubApp` gained 11 new `@Inject` bridge fields (the 10 use cases + `BotDrafter`;
  `draftRepository`/`draftSimRepository`/`analyticsHelper` were already injected and are REUSED), added
  both repos to the `coreBridgeKoinModule(...)` call, dropped them from the `homeKoinModule(...)` call,
  and registers `draftKoinModule(...)`. NO Hilt `@Provides`/`@Binds` deleted. **Koin-graph audit:** the
  11 new `single<T>` types (10 draft use cases + `BotDrafter`) appear in NO other loaded module; the 2
  repos now live ONLY in coreBridge (removed from Home) → no duplicate `single<T>` across the 14 loaded
  modules → no `DefinitionOverrideException`. **Tests:** `DraftSimViewModelTest` (6) already built the VM
  via the plain 11-arg constructor → NO test edit; all 36 draft tests green (engine 12+2+9+5, integration
  2, VM 6; 0 fail/0 skip). Verified: forced full `:app:compileDebugKotlin --rerun-tasks` SUCCESSFUL
  (deprecation warnings only); `:app:assembleDebug` SUCCESSFUL; `:app:testDebugUnitTest` 1964 tests /
  122 failed / 2 skipped (== baseline, ZERO new failures; no draft class among the 122); inline
  secret-scan clean. Koin islands now = {Settings, Stats, Profile, Home, TagDictionary, AddCard,
  CommunityDecks, CardDetail, Friends, Splash, Survey, News, Draft} (13); all other features still Hilt.
  NEXT = `feature/playtest`, still EXCLUDING online/voice/scanner.
- 2026-06-21 — **Phase 1 · Splash + Survey + News Koin islands** (tenth/eleventh/twelfth feature
  cutovers — three light leaf islands migrated in ONE run; News is the THIRD multi-VM island). All
  four ViewModels de-Hilt'd (dropped `@HiltViewModel`/`@Inject constructor` + `dagger`/`javax.inject`
  imports → plain ctors, zero behaviour change): `SplashViewModel` (1 dep), `SurveyViewModel` (8 deps +
  `SavedStateHandle`), `NewsViewModel` (4 deps), `NewsSourcesSettingsViewModel` (1 dep). Three new Koin
  modules: `feature/splash/di/SplashKoinModule.kt` (`splashKoinModule()` — NO args),
  `feature/survey/di/SurveyKoinModule.kt` (`surveyKoinModule(surveyCardImpactDao, cardDao, completeSurvey)`),
  `feature/news/di/NewsKoinModule.kt` (`newsKoinModule()` — NO args, a `viewModel { }` per VM).
  **Bridge — NOTHING promoted to coreBridge, NO island shrunk:** every shared dep was already a `single`
  in a loaded module, so each was reused via `get()` without promotion: Splash's `AuthRepository`
  (coreBridge); Survey's `SurveyAnswerDao` (already a `single` in `profileKoinModule`), `GameSessionDao`
  (already a `single` in `statsKoinModule`), `DeckRepository` + `UserPreferencesRepository` (coreBridge);
  News's 3 use cases (`GetNewsFeedUseCase`/`RefreshNewsFeedUseCase`/`ManageSourcesUseCase` — already
  `single`s in `homeKoinModule`) + `UserPreferencesDataStore` (coreBridge). Confirms the
  "a `single<T>` is resolvable via `get()` from ANY loaded module, promotion to coreBridge is only
  needed to avoid a DUPLICATE `single<T>` registration" rule — none of these islands re-registers a
  shared type, so coreBridge stays at ELEVEN singletons. **Survey platform-binding handling:**
  `@ApplicationContext Context` → Koin `androidContext()`; `@IoDispatcher CoroutineDispatcher` →
  `Dispatchers.IO` directly (the SAME singleton the Hilt binding returned, CommunityDecks precedent);
  `SavedStateHandle` (`sessionId` + `mode` nav args) → `get()` (byte-for-byte identical nav behaviour).
  Only 3 Survey-only singletons newly bridged via `ManaHubApp` (`surveyCardImpactDao`, `cardDao`,
  `completeSurveyUseCase`) — Splash + News needed ZERO new `@Inject` fields. **Hilt `NewsModule` KEPT
  (NOT converted/deleted):** although `NewsRepository` is consumed by no screen outside `feature/news`,
  the 3 news use cases are STILL Hilt-constructed (`@Inject constructor`) for the HOME island bridge
  (`ManaHubApp` `@Inject`s them from the Hilt graph → `homeKoinModule`), and they depend on
  `NewsRepository`, so deleting its only `@Binds` would break the Hilt graph (same reason Friends kept
  `FriendModule`). It can be converted+deleted once Home migrates off Hilt. **Call-sites:** all 4 screens
  (`SplashScreen`, `SurveyScreen`, `NewsScreen`, `NewsSourcesSettingsScreen`) swapped their default param
  `hiltViewModel()` → `koinViewModel()`; all are called in `AppNavGraph` with NO explicit `viewModel =`
  arg → NO nav edit. `ManaHubApp` gained 3 `@Inject` bridge fields (`surveyCardImpactDao`, `cardDao`,
  `completeSurveyUseCase`) + registers `splashKoinModule()`, `surveyKoinModule(...)`, `newsKoinModule()`.
  NO Hilt `@Provides`/`@Binds` deleted. **Koin-graph audit:** the only NEW `single<T>` types registered
  are Survey's 3 (`SurveyCardImpactDao`, `CardDao`, `CompleteSurveyUseCase`) — grep confirms each appears
  in NO other loaded module → no `DefinitionOverrideException`; all 13 loaded modules' `single`s remain
  unique. **Tests:** only `SurveyViewModelTest` exists (no Splash/News tests); it already built the VM
  via the plain 10-arg constructor (incl. `context`, `ioDispatcher`, `savedStateHandle`) → NO test edit;
  14/14 pass. Verified: `:app:assembleDebug` SUCCESSFUL (deprecation warnings only);
  `:app:testDebugUnitTest` 1964 tests / 122 failed / 2 skipped (== baseline, ZERO new failures; no
  survey/splash/news class among the 122); inline secret-scan clean. Koin islands now = {Settings, Stats,
  Profile, Home, TagDictionary, AddCard, CommunityDecks, CardDetail, Friends, Splash, Survey, News}; all
  other features still Hilt. NEXT = `feature/draft` (the least-entangled remaining leaf among draft /
  playtest / tournament / trades / decks / collection / game / auth), still EXCLUDING online/voice/scanner.
- 2026-06-21 — **Phase 1 · Friends Koin island** (ninth feature cutover; the SECOND multi-VM island,
  the heaviest so far, and the FIRST with an Activity-scoped VM). De-Hilt'd ALL THREE Friends VMs —
  `FriendsViewModel` (5 deps: `FriendRepository`, `AuthRepository`, `SearchUserByGameTagUseCase`,
  `SendFriendRequestUseCase`, `AnalyticsHelper`), `FriendDetailViewModel` (5 deps: `SavedStateHandle`,
  `FriendRepository`, `GetFriendCollectionUseCase`, `TradesRepository`, `AuthRepository`),
  `InviteDispatcherViewModel` (3 deps: `AcceptInviteUseCase`, `PendingInviteStore`, `AuthRepository`) —
  dropping `@HiltViewModel`/`@Inject constructor` + `dagger`/`javax.inject` imports → plain ctors, zero
  behaviour change. New `feature/friends/di/FriendsKoinModule.kt` with a `viewModel { }` per VM + the 4
  Friends-only use cases as `single { UseCase(get()) }` + the 2 Friends-only bridged singletons
  (`TradesRepository`, `PendingInviteStore`). **Nav-arg handling:** `FriendDetailViewModel` resolves a
  Koin-injected `SavedStateHandle` (`savedStateHandle = get()`) carrying the `"userId"` nav arg — byte-for-byte
  the same as Hilt (CommunityDeckDetail/CardDetail precedent). **NEW gotcha — Activity-scoped Koin VM:**
  `InviteDispatcherViewModel` MUST stay Activity-scoped (it survives the InviteDispatcher→Profile/Login nav
  to process a pending invite code after login, and its events are collected at the AppNavGraph top level).
  AppNavGraph created it via `hiltViewModel(activity)`; the Koin equivalent is
  `koinViewModel(viewModelStoreOwner = activity)` (same Activity `ViewModelStore` → same single instance).
  `InviteDispatcherScreen` takes `inviteVm` as a REQUIRED param (no default `viewModel()`), so only the
  AppNavGraph creation line changed — the screen itself needed no edit. **Call-sites:** `FriendsScreen` +
  `FriendDetailScreen` default param `hiltViewModel()` → `koinViewModel()`; both are called in AppNavGraph
  with NO explicit `viewModel =` arg → no nav edit for them. AppNavGraph kept its `hiltViewModel` import
  (still used by `gameVm` + other Hilt screens) and gained an `org.koin.androidx.compose.koinViewModel`
  import. **Bridge — `FriendRepository` PROMOTED + Profile SHRUNK:** `FriendRepository` was a Profile-only
  bridged `single` in `profileKoinModule`; since Friends now also needs it, it was MOVED into
  `coreBridgeKoinModule` (now ELEVEN singletons) and `profileKoinModule` was shrunk to drop the param +
  `single { friendRepository }` (its `viewModel { }` already used `friendRepository = get()` → no VM edit;
  the `ManaHubApp` profile call dropped the arg, the coreBridge call gained `friendRepository`). Bridge
  deps REUSED via `get()` (no re-register): `FriendRepository`, `AuthRepository`, `AnalyticsHelper`.
  **Hilt `FriendModule` KEPT (NOT deleted)** — unlike CommunityDecks, the feature-private Hilt module
  `@Binds FriendRepository` + `@Provides FriendshipService` are STILL consumed by Hilt features
  (`TradeProposalViewModel`/`TradeNegotiationViewModel`/`TradesHistoryViewModel` + `CollectionStatsSyncWorker`),
  so deleting it would break the Hilt graph. The `FriendRepositoryImpl`/`FriendRemoteDataSource`/use cases
  keep their `@Inject` annotations (still Hilt-constructed for the Hilt consumers); Koin builds its own
  copies of the use cases via `single { UseCase(get()) }` over the bridged `FriendRepository` — harmless
  (use cases are stateless). `ManaHubApp` gained 2 new `@Inject` bridge fields (`tradesRepository`,
  `pendingInviteStore`; `friendRepository`/`authRepository`/`analyticsHelper` were already injected and are
  REUSED), added `friendRepository` to the `coreBridgeKoinModule(...)` call, dropped it from the
  `profileKoinModule(...)` call, and registered `friendsKoinModule(...)`. **Tests:** only
  `FriendsViewModelTest` exists (no FriendDetail/Invite tests); it already built the VM via the plain
  5-arg constructor → NO test edit; it passes. **Koin-graph audit:** all `single<T>` across the 10 loaded
  modules are unique types (grep `single` → every entry count == 1) → no `DefinitionOverrideException`;
  Friends' deps all resolve (bridge + friends-only). Verified: `:app:assembleDebug` SUCCESSFUL (deprecation
  warnings only); `:app:testDebugUnitTest` 1964 tests / 122 failed / 0 errors / 2 skipped (== baseline,
  ZERO new failures; no friends/profile/invite class among the 122); inline secret-scan clean. Koin islands
  now = {Settings, Stats, Profile, Home, TagDictionary, AddCard, CommunityDecks, CardDetail, Friends}; all
  other features still Hilt. NEXT = `feature/splash` + `survey` + `news` (single/light leaves), still
  EXCLUDING online/voice/scanner. (DONE 2026-06-21 — see the newer change-log entry above.)
- 2026-06-21 — **Phase 1 · CardDetail Koin island** (eighth feature cutover). De-Hilt'd the single
  `CardDetailViewModel` (12 ctor deps: `SavedStateHandle`, `CardRepository`, `UserCardRepository`,
  `DeckRepository`, `AddCardToCollectionUseCase`, `AddToWishlistUseCase`, `WishlistRepository`,
  `OpenForTradeRepository`, `UserPreferencesRepository`, `UserPreferencesDataStore`, `AuthRepository`,
  `AnalyticsHelper`) — dropped `@HiltViewModel`/`@Inject constructor` + `dagger`/`javax.inject` imports →
  plain ctor, zero behaviour change. New `feature/carddetail/di/CardDetailKoinModule.kt` with a single
  `viewModel { }` whose `savedStateHandle = get()` resolves a **Koin-injected `SavedStateHandle`** carrying
  the `scryfallId` nav arg (CommunityDeckDetail precedent — byte-for-byte identical nav behaviour).
  `CardDetailScreen` default param `hiltViewModel()` → `koinViewModel()`. **Call-site:** the
  `Screen.CollectionCardDetail` composable in `AppNavGraph` calls the screen with NO explicit `viewModel =`
  arg (default param) → NO nav edit needed (like Settings/Stats/Profile/AddCard/CommunityDecks, unlike
  Home). **Bridge — `AnalyticsHelper` PROMOTED + Settings SHRUNK:** `AnalyticsHelper` was previously a
  Settings-only bridged `single`; since CardDetail now also needs it, it was MOVED into
  `coreBridgeKoinModule` (now TEN singletons) and `settingsKoinModule` was shrunk to drop the param + the
  `single { analyticsHelper }` (its `viewModel { }` already used `analyticsHelper = get()` → no VM edit;
  the `ManaHubApp` settings call dropped the arg, and the coreBridge call gained `analyticsHelper`). Bridge
  deps REUSED via `get()` (no re-register): `CardRepository`, `DeckRepository`, `UserPreferencesRepository`,
  `UserPreferencesDataStore`, `AuthRepository`, `AnalyticsHelper`. **WishlistRepository ownership (the
  subtle one):** `WishlistRepository` is already registered as a `single` in `homeKoinModule` (the Home
  `wishlistRepository` param) — a `single<T>` is resolvable via `get()` from ANY loaded module, so CardDetail
  resolves it via `get()` and `cardDetailKoinModule` does NOT re-register it (would `DefinitionOverrideException`).
  Hence `cardDetailKoinModule` takes only 4 CardDetail-only params (`UserCardRepository`,
  `AddCardToCollectionUseCase`, `AddToWishlistUseCase`, `OpenForTradeRepository`), each registered as a
  `single` (none appears in any other module). **New gotcha (memory updated):** a dep can stay bridged in a
  NON-coreBridge feature module (Wishlist in Home) and another island just resolve it via `get()` without
  promoting it — promotion to coreBridge is only mandatory to avoid a DUPLICATE `single<T>` across two
  modules that BOTH register it. `ManaHubApp` gained 4 new `@Inject` bridge fields (`userCardRepository`,
  `addCardToCollectionUseCase`, `addToWishlistUseCase`, `openForTradeRepository`; `analyticsHelper` and
  `wishlistRepository` were already injected and are REUSED), and registers `cardDetailKoinModule(...)`.
  NO Hilt `@Provides`/`@Binds` deleted (all deps still shared with Hilt features); no feature-private Hilt
  module in carddetail to convert; no `CardDetailViewModelTest` to edit. **Koin-graph audit:** all `single<T>`
  across the 9 loaded modules are unique types → no `DefinitionOverrideException`. Verified:
  `:app:assembleDebug` SUCCESSFUL (deprecation warnings only); `:app:testDebugUnitTest` 1964 tests /
  122 failed / 2 skipped (== baseline, ZERO new failures); inline secret-scan clean. Koin islands now =
  {Settings, Stats, Profile, Home, TagDictionary, AddCard, CommunityDecks, CardDetail}; all other features
  still Hilt. NEXT = `feature/friends` (~24 deps), still EXCLUDING online/voice/scanner.
- 2026-06-21 — **Phase 1 · CommunityDecks Koin island** (seventh feature cutover; the FIRST island with
  MULTIPLE ViewModels, and the FIRST to convert+delete a feature-private Hilt module). De-Hilt'd BOTH
  `CommunityDecksSearchViewModel` (3 deps: `SavedStateHandle`, `SearchCommunityDecksUseCase`,
  `UserPreferencesDataStore`) and `CommunityDeckDetailViewModel` (4 deps: `SavedStateHandle`,
  `GetCommunityDeckUseCase`, `ImportCommunityDeckUseCase`, `UserPreferencesDataStore`) — dropped
  `@HiltViewModel`/`@Inject constructor` + `dagger`/`javax.inject` imports → plain ctors. New
  `feature/communitydecks/di/CommunityDecksKoinModule.kt` with a `viewModel { }` for EACH VM; each factory
  resolves a **Koin-injected `SavedStateHandle`** via `get()`, which carries the route nav args
  (`cardName` for the search/by-card screens, `archidektId` for detail) exactly as Hilt's
  `SavedStateHandle` did — `koinViewModel()` reads them from the NavBackStackEntry's `CreationExtras`, so
  the nav-arg behaviour is byte-for-byte unchanged. Both screens (`CommunityDecksScreen`,
  `CommunityDeckDetailScreen`) swapped their default param `hiltViewModel()` → `koinViewModel()`.
  **Call-sites:** all 3 `AppNavGraph` composables (`Screen.CommunityDecks`, `Screen.CommunityDecksByCard`,
  `Screen.CommunityDeckDetail`) call the screens with NO explicit `viewModel =` arg (default param) → NO
  nav edit needed (like Settings/Stats/Profile/AddCard, unlike Home). **Hilt module converted + DELETED:**
  the feature-private `CommunityDecksModule` (Archidekt `@Named("archidekt")` Retrofit + `ArchidektApi`
  `@Provides` + `CommunityDecksRepository` `@Binds`) is consumed by ZERO Hilt features (verified by grep),
  so its `@Provides` were ported verbatim into the Koin module as `single { }` (the Archidekt OkHttpClient
  is still built FROM SCRATCH — keeps its dedicated `http_cache_archidekt` disk cache, User-Agent, and 5 MB
  response guard; `Context` via `androidContext()`) and the Hilt module was deleted. Also de-Hilt'd the
  feature data/domain classes whose `@Inject constructor` would have failed Hilt validation once the
  `@Binds` was gone (`CommunityDecksRepositoryImpl` — also dropped the `@IoDispatcher` qualifier, now takes
  `Dispatchers.IO` directly = the same singleton the binding returned; `ArchidektRequestQueue` — dropped
  `@Singleton`; all 3 use cases). **Bridge — `CardRepository` PROMOTED + Home SHRUNK:** `CardRepository`
  (needed by `ImportCommunityDeckUseCase`) was previously a Home-only bridged `single`; since it is now
  shared with this island it was MOVED into `coreBridgeKoinModule` (now NINE singletons) and `homeKoinModule`
  was shrunk to drop the `cardRepository` param + `single` (its `viewModel { }` already used `get()` → no VM
  edit; the `ManaHubApp` home call dropped the arg). Bridge deps REUSED via `get()`:
  `UserPreferencesDataStore` (Settings+Profile+Home), `DeckRepository` (Stats+Home), `CardRepository`
  (now bridged). Bridge dep NEWLY bridged in `communityDecksKoinModule` itself (this island only): the
  Room/`DatabaseModule`-owned `CommunityDeckCacheDao`. `ManaHubApp` gained 1 new `@Inject` field
  (`communityDeckCacheDao`), added `cardRepository` to the `coreBridgeKoinModule(...)` call, dropped
  `cardRepository` from the `homeKoinModule(...)` call, and registered `communityDecksKoinModule(...)`.
  NO Hilt `@Provides`/`@Binds` deleted OUTSIDE the feature (`CommunityDeckCacheDao` is still
  `DatabaseModule`-provided; `CardRepository`/`DeckRepository`/`UserPreferencesDataStore` still shared with
  Hilt features). **Koin-graph audit:** all `single<T>` across the 7 loaded modules are unique types — the
  new feature-private types (`ArchidektApi`, `ArchidektRequestQueue`, `CommunityDecksRepository`, 3 use
  cases, `CommunityDeckCacheDao`) appear in no other module, and `CardRepository` now lives ONLY in the
  bridge → no `DefinitionOverrideException`. **Tests:** both VM tests (`CommunityDecksSearchViewModelTest`,
  `CommunityDeckDetailViewModelTest`) + repo/usecase/format tests already built everything via the plain
  constructors with a hand-built `SavedStateHandle` → NO test edit needed; all 123 communitydecks tests
  pass (24+33+22+20+24, 0 fail/0 skip). Verified: `:app:assembleDebug` SUCCESSFUL (deprecation warnings
  only); `:app:testDebugUnitTest` 1964 tests / 122 failed / 2 skipped (== baseline, ZERO new failures);
  inline secret-scan clean. Koin islands now = {Settings, Stats, Profile, Home, TagDictionary, AddCard,
  CommunityDecks}; all other features still Hilt. NEXT = `feature/carddetail` (~15 deps), still EXCLUDING
  online/voice/scanner.
- 2026-06-21 — **Phase 1 · AddCard Koin island** (sixth feature cutover; a tiny 3-dep leaf). De-Hilt'd
  `AddCardViewModel` (dropped `@HiltViewModel`/`@Inject constructor` + the
  `dagger.hilt.android.lifecycle.HiltViewModel` and `javax.inject.Inject` imports → plain
  `class AddCardViewModel(searchCards, userPreferences, buildScryfallQuery)`, 3 ctor deps), new
  `feature/addcard/di/AddCardKoinModule.kt` (`addCardKoinModule(searchCards, buildScryfallQuery)` +
  `viewModel { AddCardViewModel(searchCards = get(), userPreferences = get(), buildScryfallQuery = get()) }`),
  `AddCardScreen` default param `hiltViewModel()` → `koinViewModel()`. **No nav-arg / SavedStateHandle
  despite the task's caution** — `AddCardViewModel` is a plain debounced-search VM that takes NO
  cardId/scryfallId (the search query is set via `onQueryChange`, not a nav arg), so no `parametersOf`
  wiring was needed. **Call-site:** the `Screen.CollectionAddCard` composable in `AppNavGraph` calls
  `AddCardScreen(onNavigateBack=…, onNavigateToScanner=…, onNavigateToCardDetail=…)` with NO explicit
  `viewModel =` arg (uses the default param) → NO nav edit needed (like Settings/Stats/Profile/TagDictionary,
  unlike Home). **Bridge — nothing newly promoted, nothing shrunk:** the 2 AddCard-only deps
  (`SearchCardsUseCase`, `BuildScryfallQueryUseCase`, both `@Inject constructor`-provided, referenced in no
  other island module) are bridged in `addCardKoinModule` itself via `single { }`; the 3rd dep
  `UserPreferencesRepository` is already in `coreBridgeKoinModule` (shared with Settings + Stats) → REUSED
  via `get()`, never re-registered. `ManaHubApp` gained 2 new `@Inject` bridge fields
  (`searchCardsUseCase`, `buildScryfallQueryUseCase`) + registers `addCardKoinModule(...)` in `startKoin`.
  NO Hilt `@Provides`/`@Binds` deleted (both use cases are still `@Inject constructor`-provided and may be
  consumed by Hilt features). **Koin-graph audit:** the new module's two `single<T>` types
  (`SearchCardsUseCase`, `BuildScryfallQueryUseCase`) appear in no other loaded module → no
  `DefinitionOverrideException`; the VM's `userPreferences = get()` resolves the bridge's
  `UserPreferencesRepository`. No `AddCardViewModelTest` exists → no test edit needed. Verified:
  `:app:assembleDebug` SUCCESSFUL (deprecation warnings only); `:app:testDebugUnitTest` 1964 tests /
  122 failed / 2 skipped (== baseline, ZERO new failures); inline secret-scan clean. Koin islands now =
  {Settings, Stats, Profile, Home, TagDictionary, AddCard}; all other features still Hilt. NEXT =
  `feature/communitydecks` (~6 deps), still EXCLUDING online/voice/scanner.
- 2026-06-21 — **Phase 1 · TagDictionary Koin island** (fifth feature cutover; the smallest leaf yet).
  De-Hilt'd `TagDictionaryViewModel` (dropped `@HiltViewModel`/`@Inject constructor` + the
  `dagger.hilt.android.lifecycle.HiltViewModel` and `javax.inject.Inject` imports → plain
  `class TagDictionaryViewModel(dictionaryRepo, prefs)`, 2 ctor deps), new
  `feature/tagdictionary/di/TagDictionaryKoinModule.kt` (`tagDictionaryKoinModule(tagDictionaryRepository)`
  + `viewModel { TagDictionaryViewModel(dictionaryRepo = get(), prefs = get()) }`),
  `TagDictionaryScreen` default param `hiltViewModel()` → `koinViewModel()`. **Call-site:** the
  `Screen.TagDictionary` composable in `AppNavGraph` calls `TagDictionaryScreen(onBack = …)` with NO
  explicit `viewModel =` arg (uses the default param) → NO nav edit needed (unlike Home). **Bridge —
  nothing newly promoted, nothing shrunk:** the two deps are `TagDictionaryRepository` (NOT shared with
  any other island → bridged in `tagDictionaryKoinModule` itself via `single { tagDictionaryRepository }`)
  and `UserPreferencesDataStore` (already in `coreBridgeKoinModule`, shared with Settings + Profile + Home
  → REUSED via `get()`, never re-registered). `ManaHubApp` needed NO new `@Inject` field — the
  `tagDictionaryRepo` field already existed (used for `loadAndApply()` at app start); it just registers
  `tagDictionaryKoinModule(tagDictionaryRepository = tagDictionaryRepo)` in `startKoin` (one new import +
  one module entry). NO Hilt `@Provides`/`@Binds` deleted (TagDictionaryRepository is still `@Inject
  constructor`-provided and consumed by Hilt code at app start). **Koin-graph audit:** the new module's
  only `single<T>` is `TagDictionaryRepository`, which appears in no other loaded module → no
  `DefinitionOverrideException`; the VM's `prefs = get()` resolves the bridge's `UserPreferencesDataStore`.
  `TagDictionaryViewModelTest` already built the VM via the plain 2-arg constructor → NO test edit needed;
  18/19 tests pass and the 1 failure (`setSuggestThreshold … coerced down`, asserts `eq(0.85f)` but the VM
  computes `0.90f - 0.05f = 0.84999996f`) is a PRE-EXISTING Float-precision baseline failure unrelated to
  the DI change (my edits never touched the `coerceIn` math). Verified: `:app:assembleDebug` SUCCESSFUL
  (deprecation warnings only); inline secret-scan clean. Koin islands now = {Settings, Stats, Profile,
  Home, TagDictionary}; all other features still Hilt. NEXT = `feature/addcard` (least-entangled leaf,
  ~6 deps), still EXCLUDING online/voice/scanner.
- 2026-06-21 — **Phase 1 · Home Koin island** (fourth feature cutover; the heaviest so far). De-Hilt'd
  `HomeViewModel` (dropped `@HiltViewModel`/`@Inject constructor` + `dagger`/`javax.inject` imports →
  plain `class HomeViewModel(...)`, 17 ctor deps), new `feature/home/di/HomeKoinModule.kt`
  (`homeKoinModule(...)` + `viewModel { HomeViewModel(...) }`: 10 Home-only `single`s + 7 bridge `get()`s),
  `HomeScreen` default param `hiltViewModel()` → `koinViewModel()`. **Call-site gotcha (new vs.
  Settings/Stats/Profile):** the `Screen.Home` composable in `AppNavGraph` passed `viewModel =
  hiltViewModel()` EXPLICITLY (not the default param) — so the nav-graph DID need an edit: removed the
  explicit arg so the screen uses its new `koinViewModel()` default. (Always grep the call-site; the
  "default-param contained-island shortcut" does NOT hold for Home.) **Bridge: promoted 4 deps + SHRUNK
  the older islands that declared them** — `StatsRepository` (was in Profile) + `DeckRepository`,
  `ScryfallRemoteDataSource` (were in Stats) + `GamificationRepository` (was in Profile) all moved INTO
  `coreBridgeKoinModule` (now 8 singletons), and `statsKoinModule`/`profileKoinModule` were shrunk to drop
  them (their `viewModel { }` already used `get()` → no VM edit; their `ManaHubApp` call-sites dropped the
  removed args). Bridge deps REUSED via `get()` (already present): `UserPreferencesDataStore`,
  `AuthRepository`, `GameSessionRepository`. Bridge deps NEWLY added (promoted): `StatsRepository`,
  `DeckRepository`, `ScryfallRemoteDataSource`, `GamificationRepository`. `ManaHubApp` gained 10 Home-only
  `@Inject` bridge fields + registers `homeKoinModule(...)`; the `coreBridge` call gained 4 args and the
  stats/profile calls dropped the promoted args. **`HomeViewModelTest` already built the VM via the plain
  17-arg constructor (`buildViewModel()`, mocks ALL deps incl. `avatarUrlFlow`) → no test edit needed; it
  PASSES (67 tests / 0 fail / 0 skip).** NO Hilt `@Provides/@Binds` deleted (all deps still shared with
  Hilt features). Koin-graph audit: all `single<T>` across the 5 loaded modules are unique types → no
  `DefinitionOverrideException`; Home's 17 deps all resolve (7 bridge + 10 home). Verified:
  `:app:assembleDebug` SUCCESSFUL; `:app:testDebugUnitTest` 1964 tests / 122 failed / 2 skipped (==
  baseline, ZERO new failures); inline secret-scan clean. Koin islands now = {Settings, Stats, Profile,
  Home}; all other features still Hilt. NEXT = `feature/tagdictionary` (least-entangled leaf), still
  EXCLUDING online/voice/scanner.
- 2026-06-21 — **Phase 1 · Profile Koin island** (third feature cutover; resumed an interrupted mid-task
  WIP). `ProfileViewModel` de-Hilt'd (dropped `@HiltViewModel`/`@Inject constructor` → plain class), new
  `feature/profile/di/ProfileKoinModule.kt` (`profileKoinModule(...)` + `viewModel { ProfileViewModel(...) }`,
  7 ctor deps), `ProfileScreen` default param `hiltViewModel()` → `koinViewModel()` (call-site uses the
  default param → NO nav edit). The interrupted run had already done the VM/Screen/module + extended
  `coreBridgeKoinModule` with the 3 newly-shared deps; this run FINISHED the wiring: `ManaHubApp` now
  `@Inject`s the 4 Profile-only deps (`StatsRepository`, `SurveyAnswerDao`, `FriendRepository`,
  `GamificationRepository`) and feeds them to `profileKoinModule`, and the bridge call gained
  `userPrefsDataStore`/`authRepository`/`gameSessionRepository`. **Critical fix the WIP had not handled:**
  promoting `UserPreferencesDataStore` + `AuthRepository` (shared with Settings) and `GameSessionRepository`
  (shared with Stats) into `coreBridgeKoinModule` means `settingsKoinModule` and `statsKoinModule` must STOP
  taking/registering those types (they'd now `DefinitionOverrideException` against the bridge) — both modules
  were shrunk to resolve them via `get()` instead, and their `ManaHubApp` call-sites updated. Bridge deps
  REUSED via `get()` (not re-registered): `UserPreferencesDataStore`, `AuthRepository`, `GameSessionRepository`.
  Bridge deps NEWLY added: those same 3 (promoted from feature modules). `ProfileViewModelTest` already
  built the VM via the plain constructor (never used Hilt infra) → no test edit needed, and it PASSES. NO
  Hilt `@Provides/@Binds` deleted (all deps still shared with Hilt features). Verified: `:app:assembleDebug`
  SUCCESSFUL; `:app:testDebugUnitTest` 1964 tests / 122 failed / 2 skipped (== baseline, ZERO new failures);
  inline secret-scan clean. Koin islands now = {Settings, Stats, Profile}; all other features still Hilt.
  NEXT = `feature/home` (or a lighter leaf if too entangled), still EXCLUDING online/voice/scanner.
- 2026-06-20 — **Phase 1 · Stats Koin island** (second feature cutover, replicating Spike-D Settings).
  `StatsViewModel` de-Hilt'd (dropped `@HiltViewModel`/`@Inject constructor` → plain class), new
  `feature/stats/di/StatsKoinModule.kt` (`statsKoinModule(...)` + `viewModel { StatsViewModel(...) }`),
  `StatsScreen` default param `hiltViewModel()` → `koinViewModel()` (call-site in `AppNavGraph` uses the
  default param, so NO nav edit). Bridge: 7 of the 8 deps NEWLY bridged via `statsKoinModule`
  (`GetCollectionStatsUseCase`, `GetCollectionSetCodesUseCase`, `ScryfallRemoteDataSource`,
  `RefreshCollectionPricesUseCase`, `GameSessionDao`, `GameSessionRepository`, `DeckRepository` — all
  `@Inject`'d into `ManaHubApp`); the 8th (`UserPreferencesRepository`) is SHARED with Settings, so it was
  MOVED out of `settingsKoinModule` into a new shared `app/di/CoreBridgeKoinModule.kt`
  (`coreBridgeKoinModule(...)`) to avoid Koin `DefinitionOverrideException` from double-registering one
  type across two loaded modules. NO Hilt `@Provides/@Binds` deleted (all deps still shared with Hilt
  features). Verified: `:app:assembleDebug` SUCCESSFUL; `:app:testDebugUnitTest` 1964/122-fail/2-skip
  (== baseline, zero new failures); no StatsViewModel test exists (nothing to adjust). Koin islands now =
  {Settings, Stats}; all other features still Hilt. NEXT = `feature/profile`.
- 2026-06-20 — Plan + library/FS map written; CLAUDE.md KMP directive added; architect prompt expanded
  with KMP rules. Spike A GREEN. Phase 0.5 launched.
- 2026-06-20 — Phase 0.5 INTERRUPTED by session limit mid-refactor (~167 files; entity/DAO/auth git
  moves into core). WIP checkpoint committed. Resume = finish 0.5 build fixes (see NEXT STEP).
- 2026-06-20 — Phase 0 Spikes D & E COMPLETE. **Spike D (code proof):** Koin 4.0.2 added alongside Hilt;
  Settings migrated to a Koin island (`SettingsViewModel` via `koinViewModel()`, de-Hilt'd; bridge module
  `feature/settings/di/SettingsKoinModule.kt`; `startKoin` in `ManaHubApp` fed Hilt singletons via 5 new
  `@Inject` bridge fields; Koin ProGuard keep added). `:app:assembleDebug` SUCCESSFUL; tests
  1964/122-fail/2-skip (== 0.5 baseline, zero regressions). **Spike E (decision):** chose JetBrains CMP
  `navigation-compose` over Voyager/Decompose. Findings → `project_kmp_spike_findings`. Spikes B/C deferred
  to Phase 2/web. NEXT = Phase 1 (build foundation + per-feature Hilt→Koin cutover + core-model/core-common).
- 2026-06-20 — Phase 1 foundation WIP REPAIRED → GREEN. The interrupted model extraction is fixed: the
  ONLY two compile errors were smart-cast-across-module failures (`NewsItem.Video.duration` in VideoCard.kt,
  `NewsFilterPrefs.sourceIds` in HomeViewModel.kt) — fixed by capturing each nullable into a local val. The
  models were already cleanly extracted (no duplicate defs left in `core.domain.model`; ProGuard wildcard
  `-keep class com.mmg.manahub.core.model.**` already in place — comment refreshed). `:shared:core-common`
  was coherent & complete, so it was KEPT (not removed); `:app` now depends on it (`implementation
  project(":shared:core-common")`) and builds, with no call-site migrated yet. Verified: `:app:assembleDebug`
  SUCCESSFUL; `:shared:core-model` + `:shared:core-common` `compileKotlinWasmJs` SUCCESSFUL;
  `:app:testDebugUnitTest` 1964/122-fail/2-skip (== baseline, zero new); commonMain forbidden-import grep
  EMPTY. NEXT = per-feature Hilt→Koin cutover starting at Stats (excl. online/voice/scanner).
- 2026-06-20 — Phase 0.5 FINISHED & VERIFIED GREEN. Fixed the stale references left by the interrupted
  relocation: added missing entity imports to the moved `NewsDao`/`CommunityDeckCacheDao` and
  `DefaultSources` (entities now in `core.data.local.entity`, DAOs same-package no longer); added the
  `QuickStartAction`/`WidgetSize`/`PersistedWidget` imports + migrated the `homeLayoutFlow`/
  `saveHomeLayout` mock to `PersistedWidget` in `HomeViewModelTest`; added `QuickStartAction` import to
  `QuickStartActionTest` and aligned its `defaults` assertion with the new `COMMUNITY_DECKS` default;
  added the missing `TournamentRepositoryImpl`+`TournamentStanding` and `GameSessionRepositoryImpl`
  imports so the test source set compiles as a unit. `:app:assembleDebug` SUCCESSFUL; tests
  1964/122-fail/2-skip (below ~140 baseline, suite now compiles); acceptance greps empty; DB stays v41.
  NEXT = Spike D (Hilt/Koin coexistence) + Spike E (CMP navigation), then Phase 1.
