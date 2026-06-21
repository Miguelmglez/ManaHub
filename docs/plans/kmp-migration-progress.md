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
- 🛑 **Phase 2 · Slice 2 — BLOCKED (investigated 2026-06-22, NO code moved; docs-only).** Goal was to
  extract `Card`/`CardFace`/`CardTag`/`SuggestedTag` + `Deck`/`DeckWithCards` into `:shared:core-model`,
  then move `CardRepository` + `DeckRepository` into `:shared:core-domain`. Investigation found **NONE of
  the required models is pure today** → neither interface can move, and the only cleanly-pure type
  (`CardFace`) unblocks nothing in isolation, so NOTHING was moved (correct per the STOP-and-report rule).
  Exact blockers:
  - **`CardTag` (`core/domain/model/CardTag.kt`)** — its `label` getter calls
    `com.mmg.manahub.core.tagging.TagDictionary.localize(this)` and the file `import`s `TagDictionary`.
    `TagDictionary` (`core/tagging/TagDictionary.kt`) imports `java.util.Locale` AND `core.util.CardTypeTranslator`,
    and `CardTypeTranslator` itself calls `java.util.Locale.getDefault()` (two sites). `java.util.Locale`
    is JVM-only (absent on wasmJs) → **`CardTag` is deeply JVM-coupled via the tag-localization engine.**
    Moving it needs an `expect`/`actual` locale/current-language provider + porting (or decoupling) the
    tagging engine — out of a "small/verifiable" slice; NOT forced.
  - **`SuggestedTag`, `Card`** — BLOCKED transitively (both reference `CardTag`; `Card` also references it
    via `tags`/`userTags`/`suggestedTags`).
  - **`Deck`, `DeckWithCards` (`core/domain/model/Deck.kt`)** — `Deck` uses `System.currentTimeMillis()`
    in two default args (`createdAt`/`updatedAt`); `java.lang.System` is JVM-only (absent on wasmJs) and
    the project has NO `kotlinx-datetime`. `Deck.kt` ALSO co-locates `DeckSlotEntry` (references the blocked
    `Card`) and `AddCardRow` (references the un-moved `WishlistEntry`/`OpenForTradeEntry`) — so the file
    must be SPLIT before any part moves. `DeckWithCards`/`DeckSlot` are themselves pure but cannot move
    while bundled with `Deck` in the same blocked file.
  - **`CardFace`** — the ONLY cleanly-pure target type. NOT moved this slice: moving it alone unblocks
    neither interface and would split it from `Card` (cross-module nullable-prop smart-cast churn per the
    documented gotcha) for zero benefit — it should move TOGETHER with `Card` once `CardTag` is unblocked.
  - Verified: working tree clean (docs-only change); no `:app`/`:shared` source touched, so the GREEN
    Slice-1 baseline (`:app:assembleDebug` SUCCESSFUL, 1964/122/2) is unchanged by definition.
- ⬜ **Phase 2 (remaining)** — `:shared:core-data` (Room stays androidMain; Retrofit→Ktor; Gson→serialization).
- ⬜ **Phase 3** — `:shared:core-ui` + features to Compose Multiplatform (leaf-first).
- ⬜ **Phase 4** — platform parity (Firebase/Work/Camera/Vosk/auth expect-actual) + web responsive + `:webApp`.
- ⬜ **Phase 5** — hardening, CI for both targets, Cloudflare Pages deploy, README/CLAUDE.md update.

## NEXT STEP (resume here)
🛑 **Phase 2 Slice 2 was investigated 2026-06-22 and is BLOCKED — nothing moved (docs-only).** The
models that gate `CardRepository`/`DeckRepository` are NOT pure today (`CardTag`→`TagDictionary`→
`java.util.Locale`; `Deck`→`System.currentTimeMillis()` + bundled with blocked types). Full detail in the
🛑 STATUS bullet above. So Slice 2 must FIRST do the model-purity prework below, THEN the moves.

➡️ **NEXT = Phase 2, Slice 2a — make the gating models KMP-pure, then move them + the 2 interfaces.**
Keep batches small (≤ ~15 files). Recommended order (each its own commit, Android-green at every step):

**2a-i. Unblock `Deck`/`DeckWithCards` (the CHEAPER blocker — do this first):**
   - Split `core/domain/model/Deck.kt`: keep `Deck`, `DeckSlot`, `DeckWithCards`, `BASIC_LAND_NAMES` as the
     movable group; MOVE OUT `DeckSlotEntry` (refs `Card`) and `AddCardRow` (refs `WishlistEntry`/
     `OpenForTradeEntry`) into a separate `:app`-only file (e.g. `AddCardRow.kt`) so they stay in `:app`.
   - Replace `Deck`'s `System.currentTimeMillis()` default args with a KMP-safe source. Kotlin is 2.3.20,
     so `kotlin.time.Clock.System.now().toEpochMilliseconds()` is available (preferred — no new dep). If
     `Clock` is gated behind `@ExperimentalTime` on this version, EITHER opt-in at the model OR drop the
     defaults and have callers/repo impls stamp the time (the repo impls already bump `updatedAt`). Verify
     no behavior change (the timestamps are persisted; keep millis semantics).
   - Then `git mv` `Deck`/`DeckSlot`/`DeckWithCards`/`BASIC_LAND_NAMES` → `:shared:core-model`
     `com.mmg.manahub.core.model` and re-package (these are in `core.domain.model` today, so this DOES change
     consumer imports `core.domain.model.Deck` → `core.model.Deck` — grep + update `:app`). Then `git mv`
     `DeckRepository` → `:shared:core-domain` (its only other ref `DeckSummary` is already in core-model).
**2a-ii. Unblock `Card`/`CardTag`/`SuggestedTag`/`CardFace` (the HARDER blocker):**
   - The real blocker is `CardTag.label` → `TagDictionary.localize()` → `java.util.Locale`. Options:
     (a) **Decouple `CardTag` from `TagDictionary`** — drop the `label` getter from the pure model (move
     label resolution to a `:app`/UI extension `fun CardTag.label()` that calls `TagDictionary`), leaving
     `CardTag`/`TagCategory` as pure `key`+`category` data. This is the smallest change and keeps the
     tagging engine in `:app`. Grep all `cardTag.label` usages and repoint them to the extension. OR
     (b) port `TagDictionary` + `CardTypeTranslator` to `commonMain` behind an `expect`/`actual`
     current-language/locale provider (bigger; only if many call-sites depend on the inline `.label`).
     Prefer (a) unless the call-site sweep proves otherwise.
   - Once `CardTag` is pure: `git mv` `CardTag`/`TagCategory`, `SuggestedTag`, `CardFace`, then `Card`
     (move `Card` ALONE — keep `UserCard`/`UserCardWithCard` in `:app`; they use `System.currentTimeMillis()`
     and gate `UserCardRepository` which is deferred anyway) into `:shared:core-model` `core.model` and
     update `:app` imports (`core.domain.model.Card` → `core.model.Card`, etc.). Then `git mv`
     `CardRepository` → `:shared:core-domain` (`DataResult` already in core-model).
   - The `-keep class com.mmg.manahub.core.model.**` ProGuard wildcard already covers all these moves.
   - **`UserCardRepository` stays in `:app` until the PagingData abstraction lands** —
   its `getCollectionPager(): Flow<PagingData<…>>` + Room `core.data.local.dao.UserCardWithCard` return
   type are platform-bound; replace `PagingData` with the `:shared:core-common` `Page`/pagination model
   and the Room row type with the domain `UserCardWithCard` in a dedicated later slice.
2. **Start moving PURE use cases** into `:shared:core-domain` `commonMain` — pick use cases that depend
   ONLY on the moved repo interfaces + core-model + kotlinx (no `@Inject`-bound platform deps, no
   `@ApplicationContext`, no `R.string`). Strip Hilt `@Inject`/`@Singleton` (Koin builds them). Anything
   platform-bound a use case touches goes behind a `commonMain` interface or `expect`/`actual` (reuse
   `:shared:core-common`'s `DispatcherProvider`/`KeyValueStore`/`CrashReporter` contracts — finally
   consuming that module). Add `implementation(project(":shared:core-common"))` to `:shared:core-domain`
   when the first such use case lands.
3. **Then `:shared:core-data`** — repository IMPLs in `commonMain` against those interfaces; **Room DAOs/
   entities/migrations STAY in `androidMain`** (no wasm target) with the DAO interface in `commonMain` and
   a web data source (Supabase-remote + IndexedDB/localStorage) in `wasmJsMain`. Migrate **Retrofit → Ktor**
   (js/wasm engine) and **Gson → kotlinx-serialization**; port the rate-limit queues
   (`ScryfallRequestQueue`/`ArchidektRequestQueue`) to pure-coroutine `Mutex` impls in `commonMain`.
3. **Fold in the deferred web spikes B & C here** (Supabase/Ktor/Coil on wasmJs; web auth) — they validate
   exactly the web-runtime libraries Phase 2's data layer introduces, so they belong in this phase.
Definition of done per Phase-2 batch: Android stays shippable (compiles + 122/2 baseline), the wasmJs
target compiles, and no Android/AndroidX/browser import leaked into `commonMain`. Keep batches small
(≤ ~15 files) to survive session limits.

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

## CHANGE LOG
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
