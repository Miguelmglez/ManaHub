# CLAUDE.md

Guidance for Claude Code when working in this repository.

## How to use this file + memory

This file is the **always-loaded floor**: broadly-applicable rules that apply across the whole
codebase. Keep it lean. Deep, feature-specific, non-obvious learnings live in **memory files**
(indexed in `MEMORY.md` at `C:\Users\Miguel\.claude\projects\E--Projects-ManaHub\memory\`).

**Before working on any feature, consult the relevant memory file(s).** Each feature section below
ends with a `→ memory:` pointer. The inline notes here are only the must-know invariants; the full
context, edge cases, and rationale are in memory.

**When recording a new learning** (after a bug fix, security finding, or design decision):
- Default to writing a **memory file** + a one-line entry in `MEMORY.md`. This keeps CLAUDE.md small.
- Only add to CLAUDE.md when the rule is **broadly applicable** (a new architectural pattern, a
  cross-cutting constraint). A single feature's edge case belongs in memory, not here.
- **Never duplicate**: if it's in CLAUDE.md, don't repeat it in memory, and vice-versa. If a CLAUDE.md
  feature section grows past a few lines, move the detail to its memory file and leave a pointer.

Full file-type / frontmatter / index conventions live in the **`memory-protocol` skill**.

## Project overview

ManaHub is a Magic: The Gathering companion app (package `com.mmg.manahub`), currently single-module
Gradle (Kotlin, Jetpack Compose, Clean Architecture, Hilt DI, Room) **migrating to Kotlin
Multiplatform**.

**DIRECTIVE — KMP-oriented Clean Architecture (effective 2026-06-20).** From now on the app targets
**Android + Web** and ALL new code MUST be written in **Clean Architecture oriented to KMP**:
platform-agnostic domain/data/ViewModel/UI in `commonMain` by default, with anything Android- or
web-specific isolated behind `expect`/`actual` or interfaces in `androidMain`/`wasmJsMain`. Do not
write new code that hard-couples to Android (`Context`, AndroidX-only APIs, Room/DataStore directly,
Hilt) unless it genuinely belongs in `androidMain`. DI is **Koin** (not Hilt) for all new/migrated
code. See the **Kotlin Multiplatform migration** section below + `docs/plans/kmp-migration-plan.md`.

## Language rules

**All code, comments, commit messages, and string resources MUST be in English.** Applies to Kotlin,
XML, `strings.xml`, Composables, DAO queries, names, comments, KDoc.

**The app is English-only.** No translation dirs (`res/values-es/`, etc.) — do not add any. No
UI-facing strings in any other language. `TimeAgoFormatter` (`core/util/`) uses only its English
output; do not restore `es`/`de` locale branches. → memory: `feedback_language_rules`

## Comments & Code Style

**Comments: precise, concise, or none.** Default to NO comments — well-named identifiers already show WHAT the code does. Add a comment ONLY when WHY is non-obvious: a hidden constraint, a subtle invariant, a workaround for a specific bug, or behavior that would surprise a reader. Never comment WHAT; never reference the feature/task/issue that prompted the code (belongs in commit messages, not code).

**One-line comments max** (never multi-line comment blocks or docstrings for implementation code). Write as a sentence fragment:
```kotlin
// DAO uses INSERT OR IGNORE + @Update, never REPLACE (which cascades)
// StateFlow self-assignment is a no-op on cold flow, safe to call
```

**Removing stale comments is mandatory:** `android-kotlin-architect` + `kmp-web-fullstack-dev` agents MUST, on first touch of a file, delete all outdated/obvious/multi-line comments. **Mark the file with a gitignored `.comments-reviewed` tag** (add `// COMMENTS_REVIEWED: 2026-09-06` as the first line after the package declaration) so agents skip it on future edits — never re-review a marked file unless the comment itself changes.

→ memory: `feedback_comment_precision_2026-09-06`

## Build commands

- YouTube API key is optional (Draft Guide video is silently disabled without it). Add
  `YOUTUBE_API_KEY=...` to `local.properties` (git-ignored) → injected into `BuildConfig.YOUTUBE_API_KEY`.
- Room schemas export to `app/schemas/` via `ksp { arg("room.schemaLocation", ...) }`.

## Architecture

**MVVM + Clean Architecture.** The tree below is the **current/legacy** single-module (`:app`) layout.
**New code is KMP-first** (Koin DI, `commonMain` by default) per the directive above and the "Kotlin
Multiplatform migration" section below — do not add new Hilt/single-module code; follow the KMP source-set
rules. The legacy structure stays as-is until each feature is migrated.

Most features: a `Screen.kt` Composable, a `ViewModel.kt` (`@HiltViewModel`), optional sub-composables.
Features with their own data layer (Draft, News) add `data/`, `domain/`, `di/` sub-packages.

## Kotlin Multiplatform migration (IN PROGRESS — Android + Web)

The project is migrating to **KMP, targeting Android + Web (Compose Multiplatform / `wasmJs`)**.
iOS/Desktop are out of scope for now but the structure must not preclude them. **DI is moving Hilt →
Koin.** Master plan (status, decisions, Android debt, web roadmap): `docs/plans/kmp-migration-plan.md`;
living tracker: `docs/plans/kmp-migration-progress.md`; spike findings + wasm/library gotchas live in memory
`project_kmp_spike_findings`. **Read these before any KMP-tagged work.** Until a feature is
migrated, existing Hilt/Room/androidx-Compose code stays as-is — do not pre-emptively KMP-ify
unrelated code.

**Sequencing (2026-06-20): get the ANDROID app working on KMP FIRST, then build the web version
incrementally.** Three platform-heavy features are EXCLUDED from the migration for now and left
untouched (Hilt + Android Compose, no shared move, no web actuals yet): **online games
(`feature/online`), voice control (`core/voice` + in-game voice), and the camera card scanner
(`feature/scanner`).** They migrate in a later wave once the Android-KMP base + core web are working.

Broadly-applicable rules (feature-specific detail → memory):
- **Source sets:** `commonMain` (shared) / `androidMain` / `wasmJsMain` (+ `commonTest`). `commonMain`
  **never** imports an Android/AndroidX or browser API — those go behind `expect`/`actual` or an
  interface. Target modules: `:shared:{core-model,core-common,core-domain,core-data,core-ui,feature-*}`,
  `:androidApp`, `:webApp`.
- **Room has no wasm target** → Room DAOs/entities/migrations stay in `androidMain`; DAO/repository
  **interfaces in `commonMain`**; web data source (Supabase-remote-first + IndexedDB/`localStorage`
  cache) in `wasmJsMain` behind the same interface.
- **No `androidx.paging.PagingData` and no `R.string`/Android resources in shared code** — use a common
  pagination model and the CMP `Res` resource system.
- **Networking:** Retrofit → Ktor (js/wasm engine), Gson → kotlinx-serialization. Rate-limit queues
  (`ScryfallRequestQueue`, `ArchidektRequestQueue`) become pure-coroutine `Mutex` impls in `commonMain`.
- **DI:** Koin modules per feature; `koinViewModel()` not `hiltViewModel()`. Migrate per-feature (pure
  features first, platform-heavy last) — never a big-bang swap that breaks compilation between commits.
- **Definition of done:** Android stays shippable (compiles + tests green vs. the documented baseline),
  the web target builds (`./gradlew wasmJsBrowserDistribution`), and no Android/browser import leaked
  into `commonMain`. `.gradle.kts`/KMP build config is owned by `android-kotlin-architect` (build DSL
  exception to the `.kt`-only delegation rule).
- → memory: `project_modularization_blockers` (5 blockers are a PREREQUISITE, resolve first),
  `project_kmp_spike_findings`

## Key architectural decisions

### CardDao upsert
**Never use `OnConflictStrategy.REPLACE` on `CardEntity`.** REPLACE = DELETE + INSERT, which cascades
and silently deletes all `UserCardEntity` rows for that card. The DAO uses INSERT OR IGNORE + `@Update`
in a `@Transaction`. Regression test: `CardDao CASCADE regression`.

### Collection sync — data-loss invariants (ADR-008)
Three rules, all cross-cutting. Full rationale: `docs/adr/ADR-008-collection-sync-safe-watermark.md`.
- **A sync watermark must never advance past a row that was not both fetched AND applied.**
  `min(syncStartTime, minUnappliedUpdatedAt - 1).coerceAtLeast(lastSync)`. Never
  `max(updatedAt seen)` — "seen" is not "applied", and that is precisely the bug that permanently
  stranded 377 of one user's 1377 collection rows.
- **Every `RETURNS SETOF` RPC must be keyset-paginated below `db-max-rows`.** PostgREST silently
  truncates at 1000 with no signal to the client. Paginate on `(updated_at, id)` — tie-safe — keeping
  the window filter and the cursor as separate predicates, and cap the page size **server-side**.
  Drain via `PagedSync.drainPages` (`:shared:core-data` commonMain). Still unpaginated: the five
  gamification `*_changes_since` RPCs.
- **Ownership data never depends on cache metadata.** A `user_card_collection` row inserts whether or
  not its `CardEntity` is cached; unresolved ids get a `stale_reason = "pending_hydration"`
  placeholder so the row stays visible and counted. Consumers that aggregate card fields (stats,
  price refresh, deck analysis) must exclude placeholders.
- → memory: `feedback_sync_watermark_never_past_unapplied`, `feedback_setof_rpc_must_be_paginated`,
  `feedback_idempotency_gate_tests_own_completion`, `project_collection_sync_data_loss_2026-09`

### Database (Room v53)
- DB file `mtg_collection.db`. The `UserCardEntity` → `CardEntity` FK was **removed in v53** (ADR-008);
  do not reintroduce it. It was `ON DELETE RESTRICT` from v38 to v52.
- Migration chain 1→53, gaps at 7–10 and 15–17 covered by `fallbackToDestructiveMigration()` (dev-only;
  not safe for production data). v39 = 6 gamification tables; v40 = additive `legality_legacy`/
  `legality_vintage`/`legality_pauper` on `cards` (Deck Doctor Phase 4 D2); v41 = Community Decks
  attribution columns on `decks` + `community_deck_cache` table; v42 = additive `produced_mana`
  (compact WUBRG string, not JSON) on `cards` (Deck Doctor Community/Archetype plan Phase 0.3, D14);
  v43–v49 = additive per-feature tables/columns (see each `Migration_x_y.kt`'s KDoc for what it
  added); v50 = `puzzle_results` table (Daily Puzzle, Batch B1); v51 = `competitive_meta_cache` +
  `competitive_limited_ratings_cache` tables (Competitive feature, Worker-backed JSON-blob caches);
  v52 = Deck Analysis Engine v3 taxonomy migration (`ArchetypeId`/`ThemeId` renames, defensively
  parsed so stale persisted strings degrade rather than crash);
  **v53 = table recreate of `user_card_collection` to DROP its FK to `cards`** — the one migration
  that is not additive. SQLite has no `DROP CONSTRAINT`, so it recreates + copies + recreates all five
  indices; those index names must match Room's generated names byte for byte or
  `runMigrationsAndValidate` crash-loops every user at launch (destructive fallback covers only
  v1–24). Guarded by `Migration52To53Test`.
  Every migration since v39 follows the same pattern: a top-level `val MIGRATION_x_y` in its own file,
  `CREATE TABLE IF NOT EXISTS …` / `ADD COLUMN … TEXT NOT NULL DEFAULT '…'` guarded by a
  `columnExists` check where applicable, CardDao upsert untouched → no CASCADE risk.
  → memory: `project_card_model_produced_mana`
- Schema: `app/schemas/com.mmg.manahub.core.data.local.MtgDatabase/` (latest version json gitignored —
  regenerate locally).

### Scryfall / Archidekt rate-limiting
All Scryfall calls must be wrapped in `ScryfallRequestQueue.execute { }` (≤10 req/s, 100 ms min between
requests); all Archidekt calls go through `ArchidektRequestQueue.execute { }` (≤5 req/s). Both delegate
to the shared `RateLimitedQueue` (`shared/core-data/.../network/`), which is a SINGLE app-wide instance
per API (never construct a second one, never add a bypass path around either queue). Since WS2 of the
backend-performance-optimization plan (2026-07-28), `RateLimitedQueue` also enforces: a SHARED cooldown
gate (every caller, not just the one that got 429'd, waits out an active cooldown before dispatching —
this is what makes the app recoverable from a 429 instead of digging deeper), bounded concurrency
(`Semaphore(maxConcurrent)`, default 2), and escalating back-off across consecutive 429/503 hits that
decays after a clean window. When retries are exhausted on a retryable failure, callers get a typed
`RateLimitExhaustedException` (never the raw HTTP exception) so a UI retry CTA can be disabled with a
countdown instead of re-triggering the storm. → memory: `project_scryfall_rate_limit_resilience_ws2`

### Backend call budget (ADR-005, 2026-07-28)
Three cross-cutting rules. Full rationale + the open findings list: `docs/adr/ADR-005-backend-call-budget.md`.
- **Gate a hidden feature's BACKEND work, not just its UI.** A flag that hides a feature must also stop
  its engine collectors, WorkManager scheduling, sync and startup reconciles — and must `cancelUniqueWork`
  already-enqueued work, not merely skip future scheduling. Gate reactively (`collect`), never a one-shot
  read. Precedent + the ADR-002 override: the Gamification section below.
- **Card prices refresh on exactly ONE automatic path — never ask the user.** `PriceRefreshWorker` →
  `RefreshCollectionPricesUseCase` (daily, 23 h watermark, stale ids only, ~500-id slices, ONE
  `updatePricesBatch` per slice, capped per run with an auto follow-up; watermark claimed only on a full
  pass). There is **no** manual refresh button and no screen-entry trigger — both were deleted, along with
  the duplicate `CardRepositoryImpl.refreshCollectionPrices()`. Do not reintroduce either. Never write one
  batch per 75-card chunk (invalidation storm → production OOM). Resumability is cursor-free by design:
  the stale-id list is recomputed each run, so a numeric offset would point at the wrong slice.
- **A write path that mutates cached fields MUST invalidate the matching cache entries.** `ScryfallCache`
  holds full `Card` objects (prices included) in `cards`/`cardNames`/`artVariants`; a stale cache serves
  old data and callers re-fetch to "fix" it — producing exactly the duplicate calls the cache exists to
  prevent.

### Theming
12 `AppTheme` palettes (dark-first; exactly one light theme: `HallowedPrint`). Fixed palettes — never
branch color on the active theme or `isSystemInDarkTheme()`.
- Colors via `MaterialTheme.magicColors`, typography via `MaterialTheme.magicTypography`, spacing via
  `MaterialTheme.spacing`, shapes via named tokens (`CardShape`, `ChipShape`, `ButtonShape`,
  `BottomSheetShape`). **Never** use `MaterialTheme.colorScheme`/`typography` directly, and never
  hardcode a `Color`, `dp` font size, or shape.
- `magicTypography` has **no `titleSmall`** (use `titleMedium` for small headers). Available:
  display{Large,Medium}, title{Large,Medium}, label{Large,Medium,Small}, body{Large,Medium,Small}.
- New theme = a `MagicColors` + `MagicTypography` instance + a branch in `MagicTheme`'s `when(theme)`.

### Notifications / toasts
**Always use `MagicToast`** — never Material3 `SnackbarHost`/`SnackbarHostState`.
```kotlin
val toastState = rememberMagicToastState()
Box { Scaffold { ... }; MagicToastHost(toastState) }
// toastState.show("message", MagicToastType.ERROR)
```

### Shared UI components (`core/ui/components/`)
Reuse before writing inline: `EmptyState`, `InlineErrorState`, `FullErrorState`, `MagicToast(Host/State)`,
`CardGridItem`, `CardListItem`, `AddToCollectionSheet`, `CardSearchSheet`, `TradeSelectionSheet`.

**Mandatory component usage:**
- **Card names:** always use `CardName` (substitutes "A-" prefix + post-"//" with Alchemy icon, supports `showFrontOnly`/`fontWeight`)
- **Alerts/dialogs:** use `MagicAlertDialog` (never Material3 `AlertDialog`); provides title/text/confirm/dismiss buttons with MagicTheme styling
- **CTA buttons:** use `MagicCtaButton` (style: `Filled`/`Outlined`/`Ghost`; color: `Primary`/`Accent`/`Error`/`Success`/`Warning`/`Info`/`Neutral`/`Gold`/`Surface` + Solid variants; supports icon/loading state)
- **Toasts:** use `MagicToast(Host/State)` (never `SnackbarHost`); type: `SUCCESS`/`INFO`/`WARNING`/`ERROR`
- **Card inspection overlay:** use `MagicCardInspectionOverlay` for enlarged card views with animation, supports paging + custom actions below card
- **Card image placeholder:** use `mtg_card_back.jpg` as fallback while image loads or on error (located `shared/core-ui/src/commonMain/composeResources/drawable/`)
- **Tag chips:** always use `CardTagChip(label, category, ...)` (never an inline `Surface`/`InputChip`/
  `SuggestionChip` for a `CardTag`) — color-codes by `TagCategory` (tonal fill + full-opacity accent
  text/border, with a runtime WCAG contrast guard that falls back to `textPrimary` whenever the raw
  accent can't clear 4.5:1 against its own tonal fill — computed per-render, not a theme branch, so it
  self-corrects on any current or future palette). Never localizes the label itself — pass the
  already-resolved text (`CardTag.label()` on Android / `CardTag.displayLabel` in commonMain).

### Navigation
Routes are a sealed class in `Screen.kt`; forward-slash hierarchy (e.g. `"collection/detail/{scryfallId}"`).
**Start destination is `Screen.Home`** (not Collection). BottomBar is 3-slot: **[Home] [⚔ FAB = Game]
[Library]** (Home dashboard redesign, 2026-06-08 — superseded the old 4-tab Collection/Stats/Game/Profile
bar). → memory: `project_home_dashboard_redesign`

### DI
**New/migrated code uses Koin** (`koinViewModel()`, Koin modules per feature) — this is the default for
all new work (see the KMP migration section). The Hilt setup below is **legacy/unmigrated only**: existing
ViewModels are `@HiltViewModel`, `RepositoryModule` binds interfaces→impls (singleton), `DispatcherModule`
provides named dispatchers, feature modules are separate `@InstallIn(SingletonComponent::class)` modules.
Do not add new Hilt modules for new code; bridge Koin↔Hilt during the transition rather than expanding Hilt.

**Koin `single`/`factory` type must match the consumer's declared param type.** If any consumer's
constructor parameter is typed as an interface, the binding producing it MUST be
`single<Interface> { Impl(...) }`, not a bare `single { Impl(...) }` (which registers under the
concrete class). This mismatch compiles cleanly — Koin resolves by type only at runtime — and
surfaces as `NoDefinitionFoundException` the first time that graph path is exercised (e.g. on app
launch, for a start-destination ViewModel's dependency chain). Bare `single { }` is only safe when
every consumer's param type is the exact concrete return type. → memory:
`feedback_koin_single_concrete_type_mismatch`

**`koinViewModel { parametersOf(x) }` needs an explicit `key` when the call site is a fixed-position
overlay (sheet-scoped conditional content), not a real nav destination.** Compose's
`ViewModelProvider` caches by key on the enclosing `ViewModelStoreOwner`; a real nav route gets a
fresh `NavBackStackEntry`/`ViewModelStore` per call so this never surfaces there, but an overlay
composable mounted at a fixed call site (e.g. `CardDetailScreen` opened from inside another screen's
`AnimatedVisibility`, not `navController.navigate(...)`) keeps the same owner across recompositions —
so `parametersOf(...)` is honored only on the FIRST construction and silently ignored on every
later re-open with a different id. Always pass `koinViewModel(key = idString) { parametersOf(idString) }`
in that pattern (precedent: `SetPickerViewModel`/`SetPickerSheet.kt`). → memory:
`feedback_koin_viewmodel_overlay_key_trap`

### Utilities
`core/util/TimeAgoFormatter` for relative dates (English only) — don't write inline `SimpleDateFormat`.

## UI / Jetpack Compose

When working on any Composable/screen/visual element, follow these non-negotiables (full detail in the
**`compose-ui`** skill — consult it before writing UI code):

- ManaHub tokens only (see Theming above). No hardcoded color/dp/shape; spacing from
  `MaterialTheme.spacing` (8dp grid: 2/4/8/12/16/24/32).
- Support all 12 themes; spot-check NeonVoid + HallowedPrint (contrast extremes).
- Every interactive element ≥ 48dp touch target.
- Stateless Composables, state hoisted; no business logic / ViewModel access in reusable UI.
- `LazyColumn`/`LazyVerticalGrid` with stable keys for unbounded lists — never `Column` +
  `verticalScroll`. Key by a unique id or index, never a value that can repeat (e.g. duplicate cards).
- Handle every state (loading/empty/error/content) with the shared `core/ui/components/`.
- Accessibility: meaningful `contentDescription` (or `null` if decorative), AA contrast, correct
  semantics, edge-to-edge insets.

To build/redesign UI use the **`compose-ui`** skill; to audit/polish, delegate to the
**`compose-design-reviewer`** subagent (`mobile-game-ui-designer` is the generative counterpart).

## Feature notes

Each section is a minimal pointer — read the linked memory before changing the feature.

### Auth & Account Management (`feature/auth/`)
See `app/src/main/java/com/mmg/manahub/feature/auth/CLAUDE.md`.

### Add Card (`feature/addcard/`)
See `app/src/main/java/com/mmg/manahub/feature/addcard/CLAUDE.md`.

### Card versions & languages (CardDetail / Collection / AddCardSheet)
See `app/src/main/java/com/mmg/manahub/feature/carddetail/CLAUDE.md`.

### Trades (`feature/trades/`)
See `app/src/main/java/com/mmg/manahub/feature/trades/CLAUDE.md`.

### Tournament (`feature/tournament/`)
See `app/src/main/java/com/mmg/manahub/feature/tournament/CLAUDE.md`.

### Deck Playtest (`feature/playtest/`)
See `app/src/main/java/com/mmg/manahub/feature/playtest/CLAUDE.md`.

### Online sessions (`feature/online/`)
See `app/src/main/java/com/mmg/manahub/feature/online/CLAUDE.md`.

### Push notifications (`core/push/`)
See `app/src/main/java/com/mmg/manahub/core/push/CLAUDE.md`.

### Voice recognition (`core/voice/`)
See `app/src/main/java/com/mmg/manahub/core/voice/CLAUDE.md`.

### Stats (`feature/stats/`)
See `app/src/main/java/com/mmg/manahub/feature/stats/CLAUDE.md`.

### Draft Simulator (`feature/draft/`)
See `app/src/main/java/com/mmg/manahub/feature/draft/CLAUDE.md`.

### Card tagging engine (`core/tagging/`)
See `app/src/main/java/com/mmg/manahub/core/tagging/CLAUDE.md`.

### Deck Studio & Deck Doctor (`feature/decks/`)
Unified deck editor + Deck Doctor suggestion engine — see `app/src/main/java/com/mmg/manahub/feature/decks/CLAUDE.md`.

### Home dashboard & widget board (`feature/home/`)
See `app/src/main/java/com/mmg/manahub/feature/home/CLAUDE.md`.

### Gamification (`core/gamification/`)
Cross-cutting XP/levels/achievements/quests/streaks/cosmetics engine — see `app/src/main/java/com/mmg/manahub/core/gamification/CLAUDE.md`.

### Competitive (`feature/competitive/`)
Static curated deep-link catalog (tournament decklists, power rankings, trending decks, Limited
ratings, deck-building tools, live streams) + event locator + Pro Tour news filter — zero live
third-party API calls by design. See `app/src/main/java/com/mmg/manahub/feature/competitive/CLAUDE.md`.
## Testing conventions

- **Targeted testing — NEVER run the full test suite by default.** Run ONLY the test classes/packages
  affected by the change: the suites mirroring the files you touched + direct consumers of changed
  public APIs. Use `--tests "com.mmg.manahub.<package>.*"` / specific class patterns. The full
  `./gradlew test` run is reserved for: pre-PR final gate, changes to cross-cutting core code
  (`core/data`, DB migrations, DI graph roots), or when the affected surface is genuinely unclear.
  Same for builds: `assembleDebug` once per implementation batch, not after every file. Rationale:
  full runs waste most of their time on untouched code. → memory: `feedback_targeted_testing`
- **Batch implementation runs.** When executing a multi-phase plan, group phases into larger
  implementation runs and defer the test-write + build + verify gate to the END of each run — do not
  stop to write tests and do a full build after every small phase unless a phase is explicitly risky
  (schema migration, data-loss surface).
- Unit tests: MockK (`io.mockk`) + Turbine (`app.cash.turbine`). Instrumented Room tests: in-memory DB
  on device/emulator. Test classes mirror source package paths.
- **`testDebugUnitTest --tests "<pattern>"` compiles the ENTIRE `src/test` source set first** — a compile
  error in any unrelated test file fails the whole run and zero tests execute. To verify one suite in
  isolation when others are broken, temporarily move the broken files aside, run, then restore.
- ViewModels that call Crashlytics outside a `runCatching` block need `mockkStatic(FirebaseCrashlytics::class)`
  / `unmockkStatic` in `@Before`/`@After`.
- Online join test `SESSION_CODE` constants must be 6 digits (the join VM filters to digits only).
- → memory: `feedback_test_suite_compiles_as_unit`, `project_unit_test_fixes`

## Telemetry (Crashlytics / Analytics)

**Every new feature MUST be reviewed for telemetry before it is considered done.** When you implement,
significantly change, or remove a feature (screen / ViewModel / repository / use case), you MUST review
whether Crashlytics instrumentation needs to be **added, removed, or modified** so we keep getting
valuable user metrics:
- **Add** action breadcrumbs (`log("action_context_result")`), session/context custom keys, and
  Non-Fatals (`recordSafeNonFatal`/`recordNonFatal`) at the new friction points — silent error
  swallowing (`runCatching{}.getOrNull()`, cold-flow `.catch{emit(...)}`), network/Room failures,
  abandonable multi-step flows, and critical user actions.
- **Remove / modify** instrumentation that a refactor made stale (renamed flows, deleted screens,
  changed keys) so dashboards don't rot — never leave a dangling event/key referencing dead code.
- Delegate the audit to the **`crashlytics-ux-auditor` agent** (it proposes telemetry; it does NOT write
  fixes), then delegate the resulting `.kt` edits to `android-kotlin-architect`.
- **Rules**: helpers in `core/util/CrashlyticsHelper.kt` (`recordSafeNonFatal(tag,e)` for external/user
  input — strips PII; `recordNonFatal(message,e)` for dev-controlled). `log`/`setCustomKey` via
  `FirebaseCrashlytics.getInstance()`. Events/keys are `snake_case` (`action_context_result`); ≤3-4
  custom keys per operation; instrumentation is **ADDITIVE**, never a substitute for existing error
  handling; **NEVER log PII** (emails, real names, tokens, raw free-text queries — log length/enum-id
  only). The auditor's running spec lives in `.claude/agent-memory/crashlytics-ux-auditor/` (keys/events
  already defined — consult it to avoid duplicates).
- → memory: `feedback_telemetry_review_on_every_feature`

## Agent learning protocol

**All Android/Kotlin (`.kt`) work goes through the `android-kotlin-architect` agent** — net-new feature
code included, not only bug fixes. The main agent must not edit `.kt` files directly; delegate (passing
file path + line, the exact problem/feature, the proposed solution logic, and any CLAUDE.md constraints).
Non-Kotlin work (the Python draft-content pipeline, Worker JS, Gradle, docs, memory) is handled directly.
→ memory: `feedback_delegate_kotlin_to_architect`

**All WEB-target work (implement / translate / fix) goes through the `kmp-web-fullstack-dev` agent** —
this is the web-side counterpart to `android-kotlin-architect`. Any agent or process that is going to
write, port, or fix web code MUST delegate to it. Its domain: the `wasmJsMain` source set, web `actual`
implementations behind `commonMain` interfaces, Compose Multiplatform / `wasmJs` rendering + bundle, the
`:webApp` target, Ktor js/wasm engine wiring, and any `commonMain` change with web implications.
**Boundary:** `android-kotlin-architect` owns Android (`androidMain`) + pure shared `commonMain`;
`kmp-web-fullstack-dev` owns the web target + web-implicating shared code. When a `commonMain` change is
driven by a web need (a new web actual, Ktor/wasm, a web-side interface), route it to
`kmp-web-fullstack-dev`. The main agent must not edit web `.kt` directly — delegate (passing file path +
line, the exact problem/feature, the proposed solution logic, and any CLAUDE.md constraints).

When an agent identifies a bug or required fix in Android/Kotlin code, it MUST likewise **delegate the
fix to the `android-kotlin-architect` agent** (or `kmp-web-fullstack-dev` for web-target code) rather
than implementing it directly.

After any bug fix, security finding, or architectural/design decision, the agent MUST record the
learning per the **`memory-protocol` skill** (memory-file-first; update CLAUDE.md only when the rule is
broadly applicable; never duplicate between the two). → skill: `memory-protocol`

The goal: no agent should hit the same bug or repeat the same design mistake twice.

## Security notes

- HTTP logging `BODY` in debug only, `NONE` in release (`NetworkModule.kt`).
  `network_security_config.xml` blocks cleartext. Room DB + DataStore excluded from Drive auto-backup
  (`backup_rules.xml` / `data_extraction_rules.xml`). Scryfall queries sanitised with an allowlist.
  YouTube key injected via OkHttp interceptor (not in Retrofit signatures or Logcat).

### Pre-push security gate (MANDATORY)
Before any PR or push (even "docs-only"), run the **`pre-push-security-gate` skill**: it delegates a
secret-scan of the **staged** diff to the `android-security-auditor` agent and blocks on any critical
finding. → skill: `pre-push-security-gate` · memory: `feedback_secret_leak_prevention`

### AI planning documents
Temporary planning `.md` files: **must be gitignored before the first `git add`** (patterns in
`.gitignore` Section 8), **deleted when the task finishes**, and **never contain literal secrets** (an
incident in 2026-06 required a full `git filter-branch --all` history rewrite). Real decisions go to
`docs/adr/ADR-NNN-*.md` (committed).

### Supabase invariants
Apply to every new migration/RPC/trigger/view:
1. `SET search_path = public` on every SECURITY DEFINER function/trigger.
2. Views use `WITH (security_invoker = true)`.
3. `(select auth.uid())` in RLS policies, not bare `auth.uid()` (also `auth.jwt()`/`auth.role()`).
4. Never put both an `ALL` and a separate `SELECT` policy on one table.
5. Materialized views don't inherit RLS — `REVOKE SELECT FROM anon` (and `authenticated` if needed).
6. Index every FK column in the same migration.
7. To drop a constraint-backing index, use `ALTER TABLE DROP CONSTRAINT`, not `DROP INDEX`.
8. Every new RPC: `REVOKE EXECUTE ... FROM PUBLIC` then explicit `GRANT` to intended roles (run
   `get_advisors` after).
9. Prefer `SECURITY INVOKER`; use `SECURITY DEFINER` only when genuinely required (called inside an RLS
   policy expression; writes to a table whose UPDATE/DELETE policy is intentionally `false`; bootstrap
   writes before the caller is a participant; cross-user session cleanup; reading a materialized view).
10. `enqueue_notification` is permanently REVOKE-protected (accepts arbitrary `recipient_id`).

→ memory: `feedback_supabase_security_audit_2026-06-02`

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

Rules:
- **Mandatory for ALL agents and subagents (architect, edge-case-tester, orchestrator, explore, etc.):** before grepping or reading source files broadly to find or understand code, **orient via the graph first** — `graphify query "<question>"` / `graphify explain "<concept>"` / `graphify path "<A>" "<B>"`, or the wiki at `graphify-out/wiki/index.md`. The graph returns a scoped subgraph at a fraction of the token cost of raw search; only fall back to direct `Grep`/`Read` once the graph has pointed you at the relevant files (or when modifying/debugging specific code, where the graph lacks the detail). This keeps token consumption low across the whole agent team. **Do not revert or treat this rule as out-of-scope cleanup** — it is a standing project rule.
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- A wiki exists at graphify-out/wiki/index.md — use it as the entry point for broad codebase navigation: read the index, then follow its `[[wiki-links]]` into community/god-node articles BEFORE falling back to raw source browsing or GRAPH_REPORT.md (this is the lower-token path).
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).
