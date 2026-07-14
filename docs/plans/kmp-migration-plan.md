# ManaHub → KMP — Master Plan, Status & Web Roadmap

**Status date:** 2026-07-04 (supersedes the 2026-06-20 plan, the 2026-07-01 audit,
`kmp-library-and-filesystem-map.md`, and `kmp-next-session-prompt.md` — all folded into this doc).
**Branch:** `feature/kmp-migration` · **Targets:** Android (shipping) + Web (Compose Multiplatform / wasmJs).
iOS/Desktop out of scope; structure must not preclude them.
**Living tracker:** `kmp-migration-progress.md` (STATUS + NEXT STEP always wins over this doc).
**Owners:** Android/shared `.kt` + `.gradle.kts` → `android-kotlin-architect` · web target (`wasmJsMain`,
`:webApp`, web actuals, web-implicating `commonMain`) → `kmp-web-fullstack-dev` · web RPC/RLS/CORS →
`backend-supabase-expert`.

---

## 1. Health verdict (audited 2026-07-04, verified against the live tree)

**Android-on-KMP: DONE and healthy.** The Android app runs on the KMP module structure with a short,
well-understood debt tail (§4).

| Fact | Verified value |
|---|---|
| Shared `commonMain` `.kt` | **357** (core-model 114 · core-common 4 · core-domain 121 · core-data 63 · core-ui 55) |
| `commonTest` | 5 files / 26 real tests (RateLimitedQueue, ManaBaseAnalyzer, TribeDeriver) |
| Retrofit / Gson imports | **0** — all 6 API surfaces on Ktor + kotlinx-serialization |
| DI | Koin owns ALL non-excluded features (20 islands, ~40 VMs) + all 7 non-excluded workers |
| Remaining Hilt | 12 infra/bridge `@Module`s + 4 excluded-feature modules; **3 `@HiltViewModel`, all excluded** |
| Test floor | **1967 tests / 118 failed (all pre-existing, root-caused) / 2 skipped** |
| Release build | Signed `assembleRelease` + R8 verified green post-split (2026-07-01) |
| `commonMain` platform leaks | 0 (KDoc-comment hits only) |
| CI | **None exists** (user decision: revisit when web target exists) |

**Web: greenfield.** Nothing shared has ever executed outside the Kotlin compiler:
- **No `:webApp` module** — `wasmJsBrowserDistribution` cannot even be invoked.
- `core-data` has **0 files** in both `androidMain` and `wasmJsMain` — no web data source exists.
- The web `KeyValueStore` actual is an **in-memory stub** (data lost on reload).
- `SupabaseClient` has only ever been constructed in Android's `SupabaseModule` — Spikes B
  (supabase-kt/Ktor/Coil on wasmJs at runtime) and C (web auth) were deferred and never executed.

"Compiles on wasmJs" is achieved everywhere; "runs in a browser" starts at zero. That is exactly what
the Android-first sequencing intended — the web phase (§5) is now unblocked and well-prepared: every
repo interface the web needs is already pure and shared.

---

## 2. Architecture decisions — reviewed 2026-07-04

Each decision was re-examined against the current ecosystem. Verdicts:

### 2.1 Room stays Android-only; web is Supabase-remote-first — **KEEP (re-confirmed)**
- Room 2.8 has KMP support (Android/iOS/JVM/native) but **still no wasmJs target**, so "Room
  everywhere" remains impossible for our target pair.
- SQLDelight is the only cross-target DB option and its browser story (sql.js/web-worker driver) is
  the weakest part of the library; rewriting the stable 64-file Room layer + the v41 migration chain
  would be weeks of pure risk with **zero Android benefit**.
- **Refinement (new):** the web target is **online-first by design** — it does NOT replicate Android's
  offline-first Room semantics. Web data sources are Supabase-remote-first with a thin cache
  (`localStorage` for prefs/small state; in-memory per-session for query results; IndexedDB only if a
  measured need appears). Do not build an offline web database. The existing collection/deck Supabase
  sync tables already give signed-in users their data on web with no schema work.
- Consequence maintained: repo **interfaces** in `commonMain` (done — 19 shared), Room impls in
  Android source sets, fresh `wasmJsMain` impls per repo in the web phase (§5 W3).

### 2.2 Supabase integration — **KEEP supabase-kt, with two web-phase actions**
- `supabase-kt` 3.x publishes wasmJs artifacts (auth/postgrest/realtime) — the library choice stands.
  **But it has never been initialized under wasmJs here** → Spike B (§5 W0) is mandatory before any
  web feature work. Fallback (unchanged): thin Ktor REST/Realtime client in `commonMain`.
- **Action 1 — client construction moves to a shared factory** during W1: today
  `createSupabaseClient` lives only in Android's `SupabaseModule`. Extract a common builder
  (url/key/plugins) with per-platform engine wiring so both apps share one config path.
- **Action 2 — auth-header injection must be lifted out of OkHttp** (flagged by the 2026-07-01
  security audit): `UserProfileClient`/`FriendshipClient` rely on an Android OkHttp interceptor to
  attach `apikey`/Bearer. Replace with a Ktor-level mechanism in `commonMain` (`defaultRequest` /
  `Auth` plugin fed by the session) — fixes web AND simplifies Android.
- Backend invariants unchanged (CLAUDE.md Supabase section). Web adds only: CORS check on all RPCs,
  OAuth redirect URLs, and a review of token persistence in `localStorage` (web auth = W2).

### 2.3 DI end-state — **DECIDED: full Koin (user, 2026-07-01)**
Hilt is transitional, not permanent. Current stable end-state: Koin owns all feature DI + workers;
Hilt survives only as the platform base (12 infra/bridge modules) consumed via the
forward/reverse bridges, **until the excluded trio migrates** — then Hilt + `KoinToHiltBridgeModule`
are deleted entirely. Infra modules → Koin ("Batch 7") is deliberately deferred to that final wave;
do not do it piecemeal earlier (churn with no unlock).

### 2.4 Module structure — **REVISED: the `:shared:feature-*` target is DROPPED for now**
The original plan's per-feature shared modules never materialized; instead 11 features' domain lives
inside `core-domain` (a deliberate consequence of the package-preserving, zero-import-edit migration
strategy that kept the whole run regression-free). **Accepted tradeoff, now made explicit:**
- Keep the 5 `:shared:core-*` "god modules" through the web MVP. Compilation-boundary/build-time
  benefits are not worth a re-split before the web target proves the architecture end-to-end.
- Revisit per-feature extraction ONLY if build times or ownership pain appear post-web-MVP
  (Deck Doctor and Tournament are the natural first candidates — largest + most isolated).
- The `:app` → `:androidApp` rename is likewise deferred indefinitely (mechanical, zero value now).
- The layering bar stays non-negotiable: acyclic graph, `core-ui` never imports domain/data,
  domain never imports presentation.

### 2.5 Navigation — **KEEP: JetBrains CMP `navigation-compose`** (Spike E decision stands:
same `NavHost`/route API as today → the 42-destination sealed `Screen.kt` migrates as a package swap;
wasmJs support + `window.bindToNavigation()` for URL routing; first-class `koinViewModel()`.)

### 2.6 Settled and closed (no re-litigation)
Ktor 3 everywhere (done) · Coil 3.3 (done) · kotlinx-datetime / `kotlin.time.Clock` (done, `java.time`
eliminated) · CMP 1.11 + Compose resources `Res` POC (infra exists; bulk string sweep deliberately
skipped — English-only app, no payoff) · MockK stays JVM-only, `commonTest` uses kotlin-test + fakes ·
Excluded trio (`feature/online`, `core/voice` + in-game voice, `feature/scanner`, + `core/nearby`)
stays Hilt + Android Compose until its own final wave.

---

## 3. Operating rules (unchanged, condensed from the old §9 playbook)

- Work only on `feature/kmp-migration`; never push/merge to `master` without the user.
- Never touch the excluded trio; if a slice would, stop and report.
- One slice = one logical commit + one tracker commit; **Android GREEN at every commit**.
- **Verify gauntlet** after every slice:
  ```bash
  ./gradlew :app:assembleDebug
  ./gradlew :shared:core-model:compileKotlinWasmJs :shared:core-domain:compileKotlinWasmJs   # + others if touched
  ./gradlew :app:testDebugUnitTest    # floor: 1967 tests / 118 failed / 2 skipped — failing-CLASS set must not grow
  grep -rnP "import (androidx\.(?!compose)|android\.|java\.)" shared/*/src/commonMain        # → empty
  ```
- After any type move: grep inline FQNs (not just imports) + same-package implicit refs; watch
  cross-module smart-cast breaks; `--rerun-tasks` after cross-module moves (stale-cache false errors);
  deleting klib sources can corrupt the wasmJs incremental cache → `:shared:<module>:clean`.
- Koin: promote-and-shrink into `CoreBridgeKoinModule` when ≥2 islands share a dep (runtime
  `DefinitionOverrideException` otherwise). Package-preserving moves remain the default.
- Pre-push security gate (`pre-push-security-gate` skill) before any push, even docs-only.

---

## 4. Android debt plan (short tail — none of it blocks the web phase)

Ordered; each item its own GREEN slice with owner and trigger:

| # | Item | Owner | When / trigger |
|---|---|---|---|
| A1 | **Validate the Koin `DelegatingWorkerFactory` on-device** (Batch 6 shipped verified against library source only — never run on a device; all 7 migrated workers + the Hilt scanner worker must be seen executing) | user + architect | next time an emulator/device is available — **first thing** |
| A2 | **Checklist F: `android-edge-case-tester` pass** on the most heavily migrated critical flows (Tournament finish-and-advance path, GameSession/Stats, Deck Doctor engine) | edge-case-tester → architect for fixes | now — the only remaining open hardening item |
| A3 | **Move Room-backed repo impls → `shared/core-data/src/androidMain`** (package-preserving; ~15 impls). RESERVED by user decision: lands **together with** each repo's wasmJs impl in W3, one repo per slice — one move instead of two | architect (paired with W3 slices) | web phase W3 |
| A4 | **Grow `commonTest`** alongside web work (JS/wasm divergence risk: Double scoring math, Mutex under wasm dispatcher, serialization) — extend the 26-test base to `DeckScorer`/`RoleClassifier` | unit-test-writer | opportunistic, during W1–W4 |
| A5 | **Batch 7: infra modules → Koin + excluded-trio migration + delete Hilt** (`OnlineSessionModule`/`VoiceModule`/`ScannerModule`/`NearbyModule` + the 12 infra/bridge modules + `KoinToHiltBridgeModule`) | architect | final wave, AFTER web MVP ships |
| A6 | **CI** (build both targets + test gate; Cloudflare Pages deploy for web) | main agent | W6 (user deferred until web exists) |
| A7 | Permanently blocked, keep as-is: `CardSearchSheet` (Activity/IME), `UpdateTradeCollectionUseCase` (Room DAO), 3 `core/tagging` files (`java.util.Locale`/DataStore), `DeckMagicDetailScreen` fallback (user: keep) | — | only with new information |

---

## 5. Web implementation plan (gradual, phased W0–W6)

Principles: **web is online-first** (§2.1); Android must stay green through every W-slice; each phase
has a hard gate; `kmp-web-fullstack-dev` owns all web `.kt`. Feature scope for the **web MVP
(proposal, confirm with user before W4):** Auth + profile, Card search, Collection (browse/add via
Supabase), Decks incl. Deck Studio, News. Explicitly NOT in web v1: game/life-counter, online
sessions, voice, scanner, playtest, push, gamification UI.

### W0 — Runtime smoke spike (the deferred Spikes B, de-risk before scaffolding)
Throwaway wasmJs `main()`: initialize supabase-kt (auth+postgrest), issue a Ktor request
(Scryfall via `ScryfallClient`), load one image with Coil 3, write/read `window.localStorage`.
**Gate:** all four libraries proven at RUNTIME under wasmJs, findings recorded in
`project_kmp_spike_findings`. If supabase-kt fails → fall back to the thin-Ktor-client decision
BEFORE any other web work.

### W1 — `:webApp` scaffold + real persistence primitive
`:webApp` module (`ComposeViewport`, `index.html`, Koin web startup) wired into
`settings.gradle.kts`; render one static screen with full MagicTheme (12 palettes) + shared
components; replace the in-memory `LocalStorageKeyValueStore` with a real `window.localStorage`
impl (kotlinx-browser). **Gate:** `./gradlew :webApp:wasmJsBrowserDistribution` builds; theme screen
renders in a browser; a pref survives reload.

### W2 — Auth on web (the deferred Spike C)
Shared `SupabaseClient` factory (§2.2 action 1); Ktor-level auth-header injection in `commonMain`
(§2.2 action 2 — replaces the Android-only OkHttp interceptor); Supabase session persistence on web;
OAuth redirect/PKCE flow + anonymous guest sign-in; `backend-supabase-expert` verifies CORS +
redirect URLs on all RPCs; review token storage against the "no hand-rolled tokens in localStorage"
rule. **Gate:** sign-in (Google OAuth + anon guest) works in the browser; session survives reload;
Android auth regression-free.

### W3 — Web data layer, one repo per slice (the single largest chunk)
For each repo the MVP needs, in this order: `UserPreferences` (KV-backed) → `Auth/Profile` →
`CardRepository` (Scryfall remote + in-memory cache) → `DeckRepository` (Supabase) →
`UserCardRepository`/collection (Supabase sync tables; needs the common-pagination path, no
`androidx.paging`) → `NewsRepository` → `CommunityDecks`. Each slice = wasmJs impl behind the
already-shared interface **+ the paired A3 move** of the Android impl into `core-data/androidMain`,
+ `commonTest` where the logic is shared. `android-edge-case-tester` audits Android↔web cache/behavior
divergence per repo. **Gate per slice:** both platforms green; repo demonstrably works in the browser.

### W4 — Navigation + screens
CMP `navigation-compose` swap (androidx → JetBrains artifact — near-mechanical for the 42-route
sealed `Screen.kt`), `window.bindToNavigation()` URL routing; port MVP screens leaf-first (Auth →
Search/CardDetail → Collection → Decks/Deck Studio → News → reduced Home). ViewModels are already
Koin + constructor-clean; screens move to `commonMain` as they port. Non-MVP destinations show a
graceful "not available on web" state behind a `PlatformCapabilities` flag. `compose-design-reviewer`
audits each ported screen (12 themes, NeonVoid + HallowedPrint).
**Gate:** MVP flows fully navigable in the browser; Android pixel-identical (same shared composables).

### W5 — Parity, polish, security, telemetry
Responsive/breakpoint pass (phone-first → desktop web); expect/actual no-ops for
Firebase (Crashlytics no-op, decide GA4 vs Sentry vs none for web telemetry — user decision),
WorkManager (skip on web); `android-security-auditor` web pass: wasm bundle secret scan, CSP,
Supabase anon exposure; Crashlytics dashboards reviewed for events web can never emit.
**Gate:** security pass clean; telemetry decision recorded.

### W6 — Release
Cloudflare Pages deploy (already on Cloudflare); CI for both targets (A6); README/CLAUDE.md/ADR
final update (write **ADR-004** — KMP architecture + Room-androidMain + full-Koin + web-online-first —
only now, when it documents reality). Then schedule A5 (excluded-trio wave + Hilt deletion) as the
migration's true completion.

---

## 6. Documentation map (post-cleanup, 2026-07-04)

| Doc | Role |
|---|---|
| `kmp-migration-plan.md` (this file) | Single source for strategy, decisions, debt, web roadmap |
| `kmp-migration-progress.md` | Living tracker: STATUS + NEXT STEP + brief log (always wins on "what's next") |
| Deleted 2026-07-04 | `kmp-migration-audit-2026-07-01.md` (executed/absorbed), `kmp-library-and-filesystem-map.md` (stale; surviving content = §2/§5 here), `kmp-next-session-prompt.md` (obsolete decision point, stale baselines) |
| Memory | `project_kmp_migration_progress` (auto-resume), `project_kmp_spike_findings` (wasm/library gotchas), `project_modularization_blockers` (historical) |

Full historical detail of every landed slice (2026-06-20 → 2026-07-04) lives in git history of
`kmp-migration-progress.md` (pre-compaction) — not re-summarized here.
