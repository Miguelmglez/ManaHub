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
    2026-06-21), CommunityDecks (Phase 1, 2026-06-21 — the FIRST multi-ViewModel island).** Every OTHER
    feature is still on Hilt (`@HiltViewModel` + `hiltViewModel()`). The shared
    `app/di/CoreBridgeKoinModule.kt` now holds NINE cross-island
    singletons: `UserPreferencesRepository` (Settings+Stats), `UserPreferencesDataStore`
    (Settings+Profile+Home), `AuthRepository` (Settings+Profile+Home), `GameSessionRepository`
    (Stats+Profile+Home), `StatsRepository` (Profile+Home), `DeckRepository` (Stats+Home+CommunityDecks),
    `ScryfallRemoteDataSource` (Stats+Home), `GamificationRepository` (Profile+Home), `CardRepository`
    (Home+CommunityDecks) — so no feature module double-registers a shared type
    (`DefinitionOverrideException` guard). Adding CommunityDecks promoted `CardRepository` into the bridge
    and SHRUNK Home (it now resolves `CardRepository` via `get()`). CommunityDecks is also the first island
    to **convert+delete its Hilt module**: the feature-private `CommunityDecksModule` (Archidekt
    Retrofit/`ArchidektApi` + the `CommunityDecksRepository` `@Binds`) was ported verbatim into the Koin
    module and deleted, since none of its types is consumed by any Hilt feature. Both VMs resolve a
    Koin-injected `SavedStateHandle` carrying their nav args (`cardName` / `archidektId`).
  - REMAINING for Phase 1: the per-feature Hilt→Koin cutover (~300 files) — Settings + Stats + Profile +
    Home + TagDictionary + AddCard + CommunityDecks done; the rest NOT started.
- ⬜ **Phase 2** — `:shared:core-domain` + `:shared:core-data` (Room stays androidMain; Retrofit→Ktor; Gson→serialization).
- ⬜ **Phase 3** — `:shared:core-ui` + features to Compose Multiplatform (leaf-first).
- ⬜ **Phase 4** — platform parity (Firebase/Work/Camera/Vosk/auth expect-actual) + web responsive + `:webApp`.
- ⬜ **Phase 5** — hardening, CI for both targets, Cloudflare Pages deploy, README/CLAUDE.md update.

## NEXT STEP (resume here)
✅ **The interrupted WIP is REPAIRED and the tree is GREEN** (see Phase 1 STATUS above + the 2026-06-20
CHANGE LOG repair line). `:shared:core-model` (full model set) and `:shared:core-common` both compile for
android + wasmJs; `:app` depends on both and `assembleDebug` is SUCCESSFUL; tests at baseline (122/2).
**`core-common` survived intact** — no re-add needed.

Resume Phase 1 in SMALL batches (≤ ~15 files per run to avoid mid-task session-limit breakage):

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
   **Next island = `feature/carddetail`** (~15 deps). After that, candidates
   in rough order of entanglement: `friends` (~24 deps), then `decks` / `collection`. Apply the EXACT
   same pattern proven by
   Settings/Stats/Profile/Home/TagDictionary/AddCard: drop `@HiltViewModel`/`@Inject` on the VM, add `feature/<x>/di/<X>KoinModule.kt`
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
