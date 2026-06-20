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
  - **Koin islands so far: Settings (Spike D), Stats (Phase 1, 2026-06-20).** Every OTHER feature is
    still on Hilt (`@HiltViewModel` + `hiltViewModel()`). The bridge now has a shared
    `app/di/CoreBridgeKoinModule.kt` holding the cross-island singleton (`UserPreferencesRepository`,
    used by both Settings + Stats) so the two feature modules never double-register it.
  - REMAINING for Phase 1: the per-feature Hilt→Koin cutover (~300 files) — Settings + Stats done; the
    rest NOT started.
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
   (Spike D), **Stats** (2026-06-20). **Next island = `feature/profile`** (`ProfileViewModel`): a pure,
   leaf, Hilt-singleton-only feature like Stats — it observes auth/progression/achievements/cosmetics
   repos (all Hilt `@Singleton`s) and has no platform entanglement. Apply the EXACT same pattern: drop
   `@HiltViewModel`/`@Inject` on the VM, add `feature/profile/di/ProfileKoinModule.kt` with
   `viewModel { ProfileViewModel(...) }`, swap the `ProfileScreen` default param to `koinViewModel()`,
   bridge its deps in `ManaHubApp` (reuse `coreBridgeKoinModule` for any already-shared singleton — e.g.
   `UserPreferencesRepository` — never double-register), and register the module in `startKoin`. Confirm
   the `Screen.Profile` call-site uses the default `viewModel` param (no nav edit needed) before relying on
   the contained-island shortcut. After profile, candidates: `home` (heavier — `combine(8)` VM with many
   repos), `decks`, `collection`. Each feature: its own Koin module + `koinViewModel()` swap, replace the
   bridged `single { hiltInstance }` with real providers + delete the matching Hilt `@Provides/@Binds` only
   once the dep is EXCLUSIVELY Koin-owned. Keep the app compiling at EVERY commit.
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
