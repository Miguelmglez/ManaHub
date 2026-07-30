# KMP Migration — Living Progress Tracker

**Single source of truth for "where are we / what's next".** A new session reads this +
`kmp-migration-plan.md` (master plan: decisions §2, rules §3, Android debt §4, web roadmap §5)
and resumes automatically.

Branch: `feature/kmp-migration` (Android-on-KMP phase, merged) → **`kmp-migration-web`** (current, web
phase, since 2026-07-30) · All Android/shared `.kt` → `android-kotlin-architect` ·
all web-target `.kt` → `kmp-web-fullstack-dev` · wasm/library gotchas → memory
`project_kmp_spike_findings`.

> This file was compacted 2026-07-04 (was 2469 lines). The full slice-by-slice history
> (every commit, verification detail, and closure rationale from 2026-06-20 → 2026-07-04) is in
> the git history of this file — do not re-derive it.

---

## How to resume
1. Read STATUS + NEXT STEP below.
2. `git branch --show-current` = `feature/kmp-migration`; check for uncommitted WIP (a dead agent's
   tree may already be green — run the gauntlet on it before discarding anything).
3. Execute NEXT STEP per the plan's §3 operating rules; verify; commit; update this file.

**Verify gauntlet** (after every slice; details in plan §3):
`:app:assembleDebug` green · touched `:shared:*:compileKotlinWasmJs` green ·
`:app:testDebugUnitTest` vs floor **1967 / 118 failed / 2 skipped** (failing-CLASS set must not
grow; 18 pre-existing failing classes are documented: online×2, voice, scanner, push×2,
collection/sync×5, auth, tagging×2, trades×4) · commonMain leak grep
`grep -rnP "import (androidx\.(?!compose)|android\.|java\.)" shared/*/src/commonMain` empty ·
inline-FQN + same-package-implicit grep after any type move · `--rerun-tasks` after cross-module
moves · `:shared:<m>:clean` if the wasmJs incremental cache corrupts after source deletion.

**Hard exclusions (untouched, still Hilt + Android Compose):** `feature/online`,
`core/voice` + in-game voice, `feature/scanner`, `core/nearby`. Never merge/push to `master`
without the user.

---

## STATUS (2026-07-30)

**Android-on-KMP: COMPLETE with a short debt tail. Web: W0 + W1 DONE.**

- ✅ **Web W1 — DONE** (2026-07-30, branch `kmp-migration-web`). Replaced W0's throwaway `main()`
  with the real entry point: `startKoin` (no `androidContext()`/`androidLogger()`) + `webAppKoinModule`
  + `App.kt` (`MagicTheme { AdaptiveScaffold { ThemeShowcaseScreen() } }`). New shared
  `shared/core-ui/.../layout/` package (commonMain, Android+wasmJs green): `ManaWindowSizeClass`
  (COMPACT/MEDIUM/EXPANDED/LARGE, hand-rolled — CMP has no `androidx.window` equivalent),
  `AdaptiveScaffold` (bottom bar / collapsed rail / expanded rail per breakpoint, built fresh rather
  than adapting `MagicBottomBar`; 1200.dp content clamp+center at LARGE; breakpoint-scaled screen
  padding), `AdaptiveCardGrid` (breakpoint-scaled `GridCells.Adaptive`). `LocalStorageKeyValueStore`
  (core-common wasmJs) now backed by REAL `window.localStorage` (kotlinx-browser), replacing the
  in-memory stub. Added `koin-compose-viewmodel` (4.2.2) for the pure-CMP `koinViewModel()`.
  **Validated live in a real browser** (Playwright/Chromium) at 375px (COMPACT, bottom bar), 768px
  (MEDIUM, collapsed icon-only rail), 1280px (LARGE, expanded icon+label rail) — zero horizontal
  overflow at any width; the 1200dp clamp+gutters confirmed at 1600px (not visible at 1280px, where
  the 200dp rail leaves only ~1080dp of content width — under the clamp threshold, expected). Live
  theme switching confirmed across multiple palettes incl. `HallowedPrint`. Persistence confirmed:
  `localStorage` value `null` → `"true"` after toggling → still `"true"` after a full page reload.
  One notable testing gotcha (memory `project_kmp_spike_findings`): Compose Multiplatform for wasmJs
  renders to a single `<canvas>` with no exposed DOM/ARIA tree, so Playwright's `getByRole()`/
  `getByText()` find nothing — verification needs raw `page.mouse.click(x, y)` coordinates read off
  a screenshot. Android verify: `:app:assembleDebug` BUILD SUCCESSFUL (this slice touches no
  `androidMain`/`:app` source at all). `:app:testDebugUnitTest` run once (no retry loop) — failed at
  the COMPILE step, but the errors are 100% inside two pre-existing, uncommitted community-decks test
  files already on this branch before this session (unrelated in-progress work, left untouched per
  instruction) — identical errors to the W0 run, confirming a standing unrelated blocker, not a
  regression.

- ✅ **Web W0 — DONE** (2026-07-30, branch `kmp-migration-web`). `:webApp` module created for real
  (wasmJs-only target, kept permanently — only the throwaway `main()` body gets replaced in W1).
  All 4 target libraries CONFIRMED WORKING at wasmJs RUNTIME in an actual browser (Chromium via
  Playwright, not just compile): `kotlinx.browser.localStorage` (real round-trip, survives reload),
  standalone Ktor `Js`-engine `HttpClient` (Scryfall fetch+parse), Coil3 via `coil-network-ktor3` +
  explicit `KtorNetworkFetcherFactory` wiring (rendered a real card image), and supabase-kt
  (`auth-kt`+`postgrest-kt`, own from-scratch client) — `signInAnonymously()` resolved against the
  real project Supabase instance, session persisted across reload. supabase-kt fallback (hand-rolled
  Ktor client) was **NOT** triggered. One real runtime bug found+fixed: supabase-kt 3.1.4's wasmJs
  klib needs kotlinx-datetime 0.6.x (forced via `resolutionStrategy` in `webApp/build.gradle.kts` —
  Gradle's default resolution otherwise bumps it to 0.7.x via CMP/Coil3, causing an `IrLinkageError`
  at runtime only). Getting `wasmJsBrowserDistribution` to build at all on this Windows dev box also
  required disabling the Kotlin/Wasm toolchain's own Node.js/Yarn/Binaryen repo auto-registration
  (conflicts with `FAIL_ON_PROJECT_REPOS`) in favor of system-installed tools, a KT-58759 DSL fix,
  and a real npm/cli#9133 workaround (kept Yarn as the wasm package manager). Full findings + every
  toolchain gotcha: memory `project_kmp_spike_findings`. Android verify: `:app:assembleDebug` BUILD
  SUCCESSFUL (no cross-module graph breakage); `:app:testDebugUnitTest` could **not** be verified
  cleanly on this machine (Gradle Test Executor crashed after a cascade of OOMs in unrelated test
  classes, following a "1768 tests completed, 67 failed, 3 skipped" summary line — consistent with
  resource contention from the heavy build/browser work in this session, not a regression from this
  change, since it touches no `androidMain`/`:app` source at all). **Re-run
  `:app:testDebugUnitTest` on an idle machine before relying on this branch further** to reconfirm
  the 1967/118/2 floor.

- ✅ **Phase 0 / 0.5 / 1 / 2 / 3 / 4 / 5-Slice-1 — DONE** (2026-06-20 → 2026-07-01). Highlights:
  5 `:shared:core-*` modules, **357 commonMain files**; Retrofit+Gson fully removed (6 Ktor
  clients); all repo interfaces + models + movable use cases + design system + ~54 composables
  shared; `java.time`/`@StringRes`/`R.string` eliminated from shared; regression audit root-caused
  every failing test (floor 1967/118/2); signed release + R8 verified green post-split.
- ✅ **Hilt→Koin cutover Batches 1–6 — DONE** (2026-07-03/04). Koin owns ALL non-excluded feature
  DI (~40 VMs, 20 islands) + the 7 non-excluded workers (`koin-androidx-workmanager`,
  `DelegatingWorkerFactory(Koin + Hilt)` for the excluded scanner worker). 10 feature Hilt modules +
  the `SharedDomainUseCaseModule` hub deleted. Remaining Hilt = 12 infra/bridge modules + 4 excluded
  modules + `KoinToHiltBridgeModule` (reverse bridge for what workers/excluded still consume).
  Batch 7 (infra→Koin) deliberately DEFERRED to the excluded-trio final wave (plan §4 A5).
- ⚠️ **Batch 6 caveat (open):** the Koin WorkerFactory ordering was verified against library source
  but **never on-device** — validate all workers execute next time an emulator is available (A1).
- ✅ **2026-07-01 audit remediation — executed**: P0.1 dead use cases deleted, P0.3 grep fixed,
  P1.1 CrashReporter for the 7 migration-candidate classes, P1.3 orphan VMs → Koin, P1.4 first
  26 commonTest tests, P2.1 `DeckBuilderState` package fix, `GetAccountNudgeUseCase` +
  `ImportCommunityDeckUseCase` unblocked + moved. P1.2 (Room impls move) RESERVED → paired with
  web W3. P0.2 (web KV stub) → web W1. The audit doc is deleted; its verdict + web critical path
  live in the master plan §1/§5.
- 📋 **User decisions on record:** full-Koin end-state (Hilt dies with the excluded wave) ·
  Room stays Android, web is Supabase-remote-first/online-first · `:shared:feature-*` modules
  dropped for now · CI deferred until web exists · `DeckMagicDetailScreen` kept · Android-first
  sequencing satisfied — web phase unblocked.

## NEXT STEP

1. **Fix (or hand off) the pre-existing community-decks test compile break** — two uncommitted test
   files (`CommunityDecksRepositoryImplTest.kt`, `CommunityDecksSearchViewModelTest.kt`) reference
   `deckFormatId`/`format`/`ALL`/`featuredFormatDecks` symbols that don't exist on the currently
   uncommitted community-decks production WIP on this branch. This is the user's own in-progress
   work (not touched by the web agent, per explicit instruction) and has now blocked
   `:app:testDebugUnitTest` from producing a clean pass/fail/skip count across BOTH the W0 and W1
   sessions. Needs resolving (by whoever owns that WIP) before the 1967/118/2 floor can be
   reconfirmed on this branch.
2. **Web W2 — Auth on web** (owner `kmp-web-fullstack-dev`): shared `SupabaseClient` factory,
   Ktor-level auth-header injection in `commonMain` (replacing the Android-only OkHttp interceptor),
   Supabase session persistence on web, OAuth redirect/PKCE + anonymous guest sign-in;
   `backend-supabase-expert` verifies CORS + redirect URLs. Same 375/768/1280px responsive gate as
   W1. Full detail: `C:\Users\Miguel\.claude\plans\abundant-giggling-comet.md` §6 (W2 row), and
   `kmp-migration-plan.md` §5.
3. **A2 — `android-edge-case-tester` pass** on Tournament finish-and-advance, GameSession/Stats,
   Deck Doctor (last open Android hardening item; fixes → architect).
4. **A1 — on-device WorkerFactory validation** (first time a device/emulator is available).

## LOG (compact; full detail in git history of this file)

- 2026-06-20: Phase 0 spikes A/D/E; Phase 0.5 blockers; Phase 1 foundation repaired.
- 2026-06-21/22: 20 Koin islands; `:shared:core-{model,common,domain,data}`; Retrofit→Ktor ×6.
- 2026-06-23/24: Phase 2 data sharing substantially complete; `:shared:core-ui` + CMP 1.11 + Coil 3.
- 2026-06-25 → 07-01: Phase 4 (55+ use cases, gamification/game/tournament domain, catalogs,
  composable sweep, kotlinx-datetime, CMP Res POC); Phase 5 Slice 1 regression audit; release/R8 +
  security + baseline-profile audits clean; README refresh.
- 2026-07-01: external audit → remediation P0.1/P0.3/P1.1/P1.3/P1.4 + quick wins; floor 1967/118/2.
- 2026-07-03/04: Hilt→Koin cutover batches 1–6 (features, use-case hub, workers); reverse bridge
  `KoinToHiltBridgeModule`; minor debt closed (P2.1, NudgeTrigger/GetAccountNudge).
- 2026-07-04: docs consolidated — master plan rewritten, audit/map/next-session-prompt deleted,
  this tracker compacted.
- 2026-07-30: branch `kmp-migration-web` created for the web phase. **Web W0 done**: `:webApp`
  module created for real; supabase-kt/Ktor-Js/Coil3/kotlinx-browser localStorage all confirmed
  working at wasmJs runtime in an actual browser; kotlinx-datetime forced to 0.6.2 (supabase-kt klib
  compat); wasmJs toolchain (Node/Yarn/Binaryen repo conflicts with FAIL_ON_PROJECT_REPOS) fixed for
  this dev machine. Full findings: memory `project_kmp_spike_findings`.
- 2026-07-30: **Web W1 done** (same day): real Koin-started `main()`/`App.kt`/`ThemeShowcaseScreen`
  replacing W0's throwaway entry point; new `shared/core-ui/.../layout/` responsive package
  (`ManaWindowSizeClass`/`AdaptiveScaffold`/`AdaptiveCardGrid`); real `localStorage`-backed
  `LocalStorageKeyValueStore`. Validated live at 375/768/1280(+1600 for the clamp)px — zero
  horizontal overflow, correct nav-chrome switching, persisted toggle survives reload. Found: CMP
  wasmJs has no DOM/ARIA tree (Playwright locators need raw coordinate clicks). Discovered (not
  caused): the pre-existing uncommitted community-decks test files are still compile-broken,
  blocking a clean `testDebugUnitTest` floor reading on this branch (now NEXT STEP #1).
