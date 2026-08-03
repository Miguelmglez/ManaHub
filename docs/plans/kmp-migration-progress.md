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

## STATUS (2026-08-03)

**Android-on-KMP: COMPLETE with a short debt tail. Web: W0 + W1 + W2a + W2b + W3a + W3b DONE.**

- ✅ **Web W3b — DONE** (2026-08-03, branch `kmp-migration-web`, commits `ddcfa689` + `4e23d61a`).
  Second W3 "web data layer, one repo per slice" task, and the FIRST slice that backs a real
  product screen (Card Search), not a showcase. `CardRepository` is a 27-method interface; per
  the scoping decision recorded in the task brief (master plan §2.1: web is online-first, no
  local card database, Collection/Deck Studio/tagging not yet on web), it was split rather than
  faithfully ported.
  1. **`WebCardRepository`** (new file, `shared/core-data` wasmJsMain,
     `repository/WebCardRepository.kt`) implements the 13 real search/lookup methods
     (`searchCardByName`/`searchCards`/`searchCardsPaginated`/`getCardById`/
     `getCardBySetAndNumber`/`getCardPrints`/`getCardArtVariants`/`getLanguagePrints`/
     `getCardByExactName`/`searchWithRawQuery`/`getPlayableSets`/`getCardsByIds`/`observeCard`/
     `warmCacheForIds`) by delegating directly to **`ScryfallRemoteDataSource`** — already
     `commonMain`, already wraps the shared `ScryfallRequestQueue` rate limiter +
     `ScryfallCache` TTL/dedup cache Android uses, so this slice reuses it as-is rather than
     re-implementing rate-limiting/caching against the raw `ScryfallClient` (a simpler shape
     than the task brief anticipated, since it assumed a from-scratch client wrapper). On top,
     a plain in-memory `MutableStateFlow<Map<String, Card>>` session cache backs `observeCard`'s
     `Flow` and `getCardsByIds`'s "local, no network fetch" contract — the web analogue of a
     Room read, cleared on reload, never persisted (a full card cache doesn't belong in
     `localStorage`). The remaining 14 Room-cache-only / tag-write methods
     (`refreshCardById`/`backfillMissingOracleIds`/`backfillMissingStrategyTags`/
     `getCachedEnglishSiblings`/`updatePrices(Batch)`/`evictStaleCache`/`updateCardTags`/
     `unionCardTags`/`updateUserTags`/`updateSuggestedTags`/`confirmSuggestedTag`/
     `dismissSuggestedTag`) throw a loud, documented `UnsupportedOperationException` pointing
     back at the scoping note — never a silent no-op, never faked Room semantics. Compiled clean
     via `:shared:core-data:compileKotlinWasmJs` (hit the documented stale-incremental-klib-cache
     `ArrayIndexOutOfBoundsException` on the first attempt — fixed with `--rerun --no-build-cache`
     per memory, not a real code problem).
  2. **`:webApp` wiring + Card Search MVP screen** — `WebAppKoinModule.kt` assembles the whole
     Scryfall stack fresh for web (Ktor `Js` `HttpClient` + content negotiation, `ScryfallClient`,
     `ScryfallRequestQueue`, `ScryfallCache`, `DispatcherProvider`, `ScryfallRemoteDataSource`),
     mirroring Android's `NetworkModule`/`SharedDomainUseCaseModule` (Hilt) construction shape
     one-for-one, then binds `single<CardRepository> { WebCardRepository(...) }` (interface-typed,
     per the project's Koin gotcha). New `CardSearchScreen`/`CardSearchViewModel` replace the
     "Search" nav placeholder in `App.kt` — the first REAL `:webApp` MVP screen, not a showcase.
     A text field triggers `searchCardsPaginated`; results render in the existing `AdaptiveCardGrid`
     via a new minimal `CardSearchResultTile` (`MagicCard` + `CardName`, NOT
     `CardGridItem` — that component takes a `CollectionCardGroup`, a collection-only shape
     with no fit for a raw search result with no ownership/quantity data; a fresh minimal tile
     was the right call, confirming the plan's contingency). Handles idle/loading/error/content
     states plus a "Load more" pagination footer. **Verified live in Chromium (Playwright)**:
     typing "Lightning Bolt" and pressing Enter resolves two REAL Scryfall results (Emeritus of
     Conflict / SOS, Lightning Bolt / MSC) with real card art rendering (`cards.scryfall.io` image
     requests both 200), confirmed at 375px (COMPACT, bottom bar, 2-col reflow), 768px (MEDIUM,
     collapsed rail, larger tiles/full names), and 1280px (LARGE, expanded rail) — zero horizontal
     overflow at any width (`scrollWidth == clientWidth` at all three). Confirmed no code path in
     the new screen/ViewModel reaches any of the 14 stub methods (only `searchCardsPaginated` is
     called). Android's `CardRepositoryImpl` (`app/src/main/java/.../repository/`) was NOT touched.
     `:app:assembleDebug` stays green. **Note**: the paired A3 move (Android's `CardRepositoryImpl`
     into `shared/core-data/src/androidMain`) was intentionally NOT done in this slice per its own
     brief (explicitly out of scope, Android-side work belongs to `android-kotlin-architect`) —
     still outstanding, same as it was before this slice.
  Full detail + the reusable "delegate to the existing commonMain remote data source instead of
  rebuilding rate-limiting/caching" finding: memory `project_kmp_spike_findings` (W3b addendum,
  pending) and `.claude/agent-memory/kmp-web-fullstack-dev/project_w3_repo_slice_pattern.md`.

- ✅ **Web W3a — DONE** (2026-08-03, branch `kmp-migration-web`, commits `b9aba6ad` + `49772075`).
  First W3 "web data layer, one repo per slice" task — deliberately the narrowest repo in the W3
  order (`UserPreferencesRepository`: 6 flows + 9 setters, 37 lines), chosen to validate the
  "wasmJs impl behind the shared `commonMain` interface" pattern before the bigger repos
  (`CardRepository`, `DeckRepository`, etc. — still NOT started).
  1. **`WebUserPreferencesRepository`** (new file, `shared/core-data` wasmJsMain,
     `repository/WebUserPreferencesRepository.kt`) — a FRESH implementation, deliberately NOT a
     port of Android's 1008-line `UserPreferencesDataStore` god-object (which serves dozens of
     unrelated preference flows well beyond this interface) — that file was correctly left
     completely untouched. Backed by the existing wasmJs `KeyValueStore` actual
     (`LocalStorageKeyValueStore`, real `window.localStorage`, built in W1). **Reactivity
     pattern** (reusable for future repo slices): `KeyValueStore` has no native reactive-flow
     support (no Room/DataStore `Flow`, and `localStorage`'s own `storage` DOM event only fires in
     OTHER tabs) — every exposed `Flow` is backed by an in-memory `MutableStateFlow` cache,
     hydrated once at construction via a `Dispatchers.Unconfined` coroutine (runs to completion
     eagerly/synchronously since `LocalStorageKeyValueStore`'s suspend calls never actually
     suspend) and kept in sync on every write through the same Koin-`single` instance. Encoding
     mirrors Android's conceptual scheme (enum `.code`/`.name`, JSON via `kotlinx.serialization`
     for the `List<UserDefinedTag>` and `Set<NewsLanguage>` collections) without being a
     cross-platform wire-format port — each platform persists to its own local store. Compiled
     clean via `:shared:core-data:compileKotlinWasmJs` with zero `.gradle.kts` changes needed
     (core-data's commonMain already exposed `kotlinx-serialization-json` + `core-domain` as `api`
     dependencies, both inherited by wasmJsMain automatically).
  2. **`:webApp` wiring + live proof** — `WebAppKoinModule.kt` gains
     `single<UserPreferencesRepository> { WebUserPreferencesRepository(...) }` (bound against the
     interface, not the bare concrete class, per the project's documented Koin gotcha).
     `ThemeShowcaseViewModel`/`ThemeShowcaseScreen` extended with a minimal real exercise: a
     `collectionViewModeFlow`/`saveCollectionViewMode` GRID↔LIST toggle button, deliberately
     distinct from W1's raw `KeyValueStore` toggle (this proves the REPOSITORY layer's own
     hydration/serialization logic, not just the underlying KV store). **Verified live in
     Chromium (Playwright)**: clicking Toggle writes `collection_view_mode=LIST` to real
     `window.localStorage` and updates the UI immediately; a full page reload (fresh Koin graph,
     fresh `WebUserPreferencesRepository` instance) still shows `Current: LIST` — proving the
     repository's own read/hydration path works end-to-end, not merely that the raw key
     survived. Zero console errors across both checks. No `.gradle.kts` changes needed here
     either (`:shared:core-domain`'s `UserPreferencesRepository` type was already transitively
     visible to `:webApp` via `core-data`'s `api` dependency on `core-domain`).
  Both checkpoints passed the `android-security-auditor` pre-push gate clean (no secrets/PII —
  only localStorage key names and non-sensitive user preference data). Full detail: memory
  `project_kmp_spike_findings` (W3a addendum).

- ✅ **Web W2b — DONE** (2026-08-03, branch `kmp-migration-web`, commits `f159d441` + `4629a484`).
  Master plan §2.2 Action 2 (Ktor-level auth-header injection in commonMain), in two checkpoints.
  **Google OAuth is explicitly OUT of scope / deliberately deferred** — the user chose to defer
  rather than pick an implementation approach on the spot (memory
  `project_kmp_web_google_oauth_deferred`); do not treat it as blocking.
  1. **Shared Ktor auth-header plugin** — new `installSupabaseAuthHeaders(supabaseClient, anonKey)`
     (`shared/core-data` commonMain, `remote/SupabaseAuthHeaderPlugin.kt`): a plain
     `HttpClientConfig<*>` extension using `createClientPlugin` (same idiom already proven by
     `SupabaseClientFactory.kt`'s call-counter plugin) that injects `apikey`/
     `Authorization: Bearer <token or anonKey>`/`Content-Type`/`Accept` on every request (token read
     fresh per-request via `Auth.currentSessionOrNull()`), plus the shared `ContentNegotiation`
     (kotlinx-json, `ignoreUnknownKeys=true`, `encodeDefaults=true`) and `expectSuccess=true` — one
     mechanism, installed identically on Android (`OkHttp` engine) and Web (`Js` engine).
     `AuthKoinModule.kt`'s `"supabaseKtor"` single now builds on a bare (logging-only) OkHttp
     engine + this plugin. **Important nuance found mid-task, not anticipated by the original
     brief**: the separate `@Named("supabase")` OkHttpClient single was intentionally left
     UNTOUCHED (still has its own interceptor) because `AuthRepositoryImpl` makes two RAW
     (non-Ktor) OkHttp calls to Edge Functions (`delete-current-user`,
     `set-google-account-password`) that bypass Ktor entirely and rely on that interceptor for
     header injection — removing it would have silently broken both. Verified:
     `:app:assembleDebug` green; `AuthRepositoryImplTest`'s one pre-existing failure (session
     mapping, unrelated) reproduces identically on baseline via git-stash A/B, confirming no
     regression from this change.
  2. **Web wiring** — `WebAppKoinModule.kt` gains a `@Named("supabaseKtor")` `HttpClient(Js)` using
     the same plugin, plus `UserProfileClient`/`FriendshipClient` construction (both already
     commonMain, just needed a web construction site). `AuthViewModel` gained a one-time, clearly-
     marked plumbing smoke check: after guest sign-in resolves, it calls
     `UserProfileClient.fetchProfile()` and shows the outcome in `AuthScreen` — not a real feature,
     exists only to prove the Bearer token is read correctly at wasmJs runtime. **Verified live in
     Chromium (Playwright)**: guest sign-in → `POST .../auth/v1/signup` (200) → `GET
     .../rest/v1/user_profiles?id=eq.<that session's own uid>&select=...` (200, 1 row) — getting
     back exactly the caller's own row is strong proof the correct per-session token was injected
     (a wrong/stale token 401/403s; a missing one falls back to the anon key and would RLS-deny).
     **Finding for follow-up, NOT fixed here (out of scope)**: this result contradicts CLAUDE.md's
     documented "guests have no user_profiles row" invariant — confirmed via Supabase MCP that
     `public.handle_new_user()` (the `on_auth_user_created` trigger) inserts a `user_profiles` row
     for EVERY `auth.users` insert unconditionally, anonymous sign-ups included. Needs a dedicated
     `backend-supabase-expert` look. Both checkpoints passed the `android-security-auditor`
     pre-push gate clean. Full detail: memory `project_kmp_spike_findings` (W2b addendum).

- ✅ **Web W2a — DONE** (2026-07-30, branch `kmp-migration-web`, commits `6481a7f6` + `930d56c3`).
  Guest-only auth on web, in two checkpoints:
  1. **Shared `SupabaseClient` factory** — extracted `createManaHubSupabaseClient(...)` to
     `shared/core-data` commonMain (new file `remote/SupabaseClientFactory.kt`), parameterized by
     `sessionManager`/`httpEngine`/`oauthScheme`/`crashReporter`. Installs Auth
     (`alwaysAutoRefresh`/`autoLoadFromStorage`/the passed `SessionManager`, `scheme`/`host` only
     when `oauthScheme != null`), Postgrest, Realtime, and the WS7 call-counter plugin — byte-
     identical to what `SupabaseModule.kt` used to build inline. `app/.../SupabaseModule.kt`
     rewritten to delegate to it (Android behavior unchanged: same `SecureSessionManager`, same
     `Android.create()` engine, same `"manahub"` scheme). Added `libs.supabase.realtime` to
     `shared/core-data/build.gradle.kts` commonMain (was missing). Verified:
     `:app:assembleDebug` BUILD SUCCESSFUL; `AuthRepositoryImplTest` isolated run green (worked
     around the same pre-existing community-decks WIP test-compile break as W0/W1, per project
     convention — moved the two broken files aside, ran, restored).
  2. **Web auth screen** — new `WebSessionManager` (wasmJsMain) implementing supabase-kt's own
     `SessionManager` contract (NOT a hand-rolled token store, per the W0 audit follow-up),
     persisting `UserSession` JSON to `window.localStorage` under `manahub_web_session`, no
     encryption layer (documented: no browser equivalent of Android Keystore — same trust
     boundary as a cookie-based session). New `AuthViewModel`/`AuthScreen` (guest sign-in only —
     `Auth.signInAnonymously()`; Google OAuth deliberately deferred to a follow-up). `AuthUiState`
     exposes only resolved fields (`userId`, `isAnonymous`) — never the raw `SessionStatus`/
     `UserSession`, per the W0 spike's JWT-leak finding. Wired into `App.kt` as a 4th "Account"
     nav tab inside the existing `AdaptiveScaffold`. `:webApp` gained a `:shared:core-data`
     dependency (was missing). **Verified live in Chromium (Playwright)**: guest sign-in resolved
     a real anonymous session against the project's Supabase instance (real JWT, real user id);
     `localStorage` payload was byte-identical before and after a full page reload (proves the new
     `SessionManager` persists, not just the generic KeyValueStore toggle W1 already proved); zero
     horizontal overflow at 375px/768px/1280px, screen renders cleanly inside the reused shell at
     all three. **Notable finding (not fixed, out of scope):** the real anonymous JWT's
     `app_metadata` is empty — `is_anonymous: true` is a top-level JWT/user-object claim, not
     nested under `app_metadata` — so Android's existing `userInfo.appMetadata?.get("is_anonymous")`
     convention (documented in this file's CLAUDE.md-adjacent notes) likely mis-resolves too for a
     freshly-minted anonymous session; the web `AuthViewModel` mirrors that same convention for
     parity rather than silently diverging. Worth a follow-up audit on the Android side — see
     memory `project_kmp_spike_findings` addendum.
  Security gate: both checkpoints passed `android-security-auditor` review (checkpoint 2 had one
  non-blocking LOW finding — raw Supabase error message surfaced to `AuthUiState.Error`, not
  sanitized — deferred, not urgent).

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

1. **Web W3 — next data-layer slice: `DeckRepository`** (owner `kmp-web-fullstack-dev`). W3a
   (`UserPreferencesRepository`) and W3b (`CardRepository`) are both DONE — see STATUS above.
   Per the W3 order in `kmp-migration-plan.md` §5 (`UserPreferences` → `Auth/Profile` →
   `CardRepository` → `DeckRepository` → `UserCardRepository`/collection → `NewsRepository` →
   `CommunityDecks`), Auth/Profile was already substantially covered by W2a/W2b (guest sign-in +
   `UserProfileClient`/`FriendshipClient`), so `DeckRepository` (Supabase-backed) is next in
   sequence — no reason found during W3b to reorder. Reuse the W3a/W3b patterns where they fit:
   the `MutableStateFlow` reactivity cache (memory `project_kmp_spike_findings`) and, if
   `DeckRepository` turns out to have a similar "most methods aren't needed on an online-first web
   client yet" shape, the same real-methods/loud-stub split W3b used (scoping decision recorded in
   `CardRepository.kt`'s class KDoc) rather than a faithful full port. **Still outstanding, not
   part of W3b's brief**: the paired A3 move of `CardRepositoryImpl` (Android) into
   `shared/core-data/src/androidMain` — pick this up (via `android-kotlin-architect`) whenever
   convenient, paired with a future repo slice or standalone, per plan §5's "paired A3 move" note.
   **Google OAuth remains explicitly deferred** (user decision, memory
   `project_kmp_web_google_oauth_deferred`) — do NOT pick it up as a blocking prerequisite to W3.
2. **Follow-up finding to route to `backend-supabase-expert`** (surfaced during W2b, not fixed):
   `public.handle_new_user()` creates a `user_profiles` row for every `auth.users` insert,
   INCLUDING anonymous sign-ups — contradicting CLAUDE.md's documented "guests have no
   user_profiles row" invariant. Needs a decision: is the trigger's behavior correct and the
   CLAUDE.md line stale, or should the trigger skip anonymous users? See memory
   `project_kmp_spike_findings` (W2b addendum) for the exact trigger/function definitions.
3. **Fix (or hand off) the pre-existing community-decks test compile break** — two uncommitted test
   files (`CommunityDecksRepositoryImplTest.kt`, `CommunityDecksSearchViewModelTest.kt`) reference
   `deckFormatId`/`format`/`ALL`/`featuredFormatDecks` symbols that don't exist on the currently
   uncommitted community-decks production WIP on this branch. This is the user's own in-progress
   work (not touched by the web agent, per explicit instruction) and has now blocked
   `:app:testDebugUnitTest` from producing a clean pass/fail/skip count across every web session
   so far (W0 through W2b). Needs resolving (by whoever owns that WIP) before the 1967/118/2 floor
   can be reconfirmed on this branch.
4. **A2 — `android-edge-case-tester` pass** on Tournament finish-and-advance, GameSession/Stats,
   Deck Doctor (last open Android hardening item; fixes → architect).
5. **A1 — on-device WorkerFactory validation** (first time a device/emulator is available).

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
- 2026-07-30: **Web W2a done** (same day, commits `6481a7f6` + `930d56c3`): shared
  `createManaHubSupabaseClient` factory (`shared/core-data` commonMain) consumed by both
  `SupabaseModule.kt` (Android, behavior unchanged) and a new `:webApp` guest-only auth screen
  (`WebSessionManager` implementing supabase-kt's real `SessionManager` contract over
  `localStorage`, `AuthViewModel`/`AuthScreen`, wired as a 4th "Account" tab in `App.kt`). Verified
  live in Chromium: real anonymous session against the Supabase project, session survives a full
  page reload (byte-identical stored payload), zero overflow at 375/768/1280px. Found (not fixed):
  the real anonymous JWT's `is_anonymous` claim lives at the top level, not under `app_metadata` —
  Android's existing `isAnonymous` derivation likely has the same latent miss; flagged for a
  follow-up audit, not touched in this slice.
- 2026-08-03: **Web W2b done** (commits `f159d441` + `4629a484`): shared
  `installSupabaseAuthHeaders` Ktor plugin (`shared/core-data` commonMain) replacing the
  Android-only OkHttp interceptor for `UserProfileClient`/`FriendshipClient` traffic — installed
  identically on Android (`OkHttp`) and Web (`Js`). Kept `AuthRepositoryImpl`'s separate raw-OkHttp
  Edge Function calls (`delete-current-user`/`set-google-account-password`) working unmodified via
  their own untouched interceptor-bearing client. Wired the shared client +
  `UserProfileClient`/`FriendshipClient` into `:webApp`'s `WebAppKoinModule.kt`. Verified live in
  Chromium: guest sign-in → real authenticated `GET user_profiles` returned exactly the caller's
  own row (200, 1 row) — strong proof of correct per-session Bearer-token injection on wasmJs.
  Found (not fixed, routed to `backend-supabase-expert`): `handle_new_user()` creates a
  `user_profiles` row for anonymous sign-ups too, contradicting CLAUDE.md's "guests have no
  user_profiles row" line. Google OAuth remains explicitly deferred per user decision (memory
  `project_kmp_web_google_oauth_deferred`) — W3 (web data layer) is the actual next step, not
  blocked on OAuth.
- 2026-08-03: **Web W3a done** (commits `b9aba6ad` + `49772075`): first W3 data-layer slice,
  `WebUserPreferencesRepository` (`shared/core-data` wasmJsMain) — fresh implementation, NOT a
  port of Android's 1008-line `UserPreferencesDataStore` (left untouched). Reactivity via an
  in-memory `MutableStateFlow` cache hydrated from the wasmJs `KeyValueStore` (real localStorage)
  at construction, kept in sync on every write — a pattern future repo slices should reuse. Wired
  into `:webApp`'s Koin module (bound against the interface type) and exercised via a
  `ThemeShowcaseScreen` GRID/LIST toggle. Verified live in Chromium: toggle click writes to real
  `localStorage` and updates the UI; a full page reload (fresh repository instance) still shows
  the persisted value, proving the repository's own hydration path, not just the raw KV store.
  Zero `.gradle.kts` changes needed (transitive `api` exposure already covered both
  `kotlinx-serialization` and `core-domain`). Next slice: `CardRepository`.
- 2026-08-03: **Web W3b done** (commits `ddcfa689` + `4e23d61a`): second W3 data-layer slice,
  `WebCardRepository` (`shared/core-data` wasmJsMain) — the first slice backing a REAL screen
  (Card Search), not a showcase. Split the 27-method `CardRepository` interface: 13 real
  search/lookup methods delegate to the already-`commonMain` `ScryfallRemoteDataSource` (shared
  rate limiter + TTL cache, reused as-is rather than rebuilt), backed by an in-memory
  `MutableStateFlow<Map<String, Card>>` session cache for `observeCard`/`getCardsByIds`; the
  remaining 14 Room-cache-only/tag-write methods throw a documented `UnsupportedOperationException`
  instead of a silent no-op or faked Room read. Wired into `:webApp`'s Koin module (full Scryfall
  stack assembled fresh, mirroring Android's Hilt shape) and a new `CardSearchScreen`/
  `CardSearchViewModel` — the first real `:webApp` MVP screen. Verified live in Chromium: "Lightning
  Bolt" resolves two real Scryfall results with real card art, reflowing correctly at
  375/768/1280px with zero overflow. `CardGridItem` was NOT reusable (it takes a collection-only
  `CollectionCardGroup`) — built a minimal `CardSearchResultTile` from `MagicCard`+`CardName`
  instead, confirming the plan's documented contingency. Android's `CardRepositoryImpl` untouched;
  `:app:assembleDebug` green. Next slice: `DeckRepository` (Auth/Profile already covered by
  W2a/W2b). Outstanding: the paired A3 Android-impl move to `androidMain` was not part of this
  slice's brief and remains open.
