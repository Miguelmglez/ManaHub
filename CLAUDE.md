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

## Project overview

ManaHub is a Magic: The Gathering companion Android app (package `com.mmg.manahub`). Single-module
Gradle project: Kotlin, Jetpack Compose, Clean Architecture, Hilt DI, Room.

## Language rules

**All code, comments, commit messages, and string resources MUST be in English.** Applies to Kotlin,
XML, `strings.xml`, Composables, DAO queries, names, comments, KDoc.

**The app is English-only.** No translation dirs (`res/values-es/`, etc.) — do not add any. No
UI-facing strings in any other language. `TimeAgoFormatter` (`core/util/`) uses only its English
output; do not restore `es`/`de` locale branches. → memory: `feedback_language_rules`

## Build commands

```bash
./gradlew assembleDebug          # Debug build
./gradlew assembleRelease        # Release build (requires signing config)
./gradlew test                   # All unit tests
./gradlew test --tests "com.mmg.manahub.feature.game.GameViewModelTest"  # Single class
./gradlew connectedAndroidTest   # Instrumented Room tests (needs device/emulator)
```

- YouTube API key is optional (Draft Guide video is silently disabled without it). Add
  `YOUTUBE_API_KEY=...` to `local.properties` (git-ignored) → injected into `BuildConfig.YOUTUBE_API_KEY`.
- Room schemas export to `app/schemas/` via `ksp { arg("room.schemaLocation", ...) }`.

## Architecture

**MVVM + Clean Architecture** within a single Gradle module (`:app`).

```
com.mmg.manahub/
├── app/navigation/       — Screen.kt (sealed routes), AppNavGraph.kt
├── core/
│   ├── data/{local,remote,repository}  — Room; Scryfall Retrofit; repo impls + CachePolicy
│   ├── di/               — RepositoryModule, DispatcherModule (Hilt)
│   ├── domain/{model,repository,usecase}
│   ├── network/          — NetworkModule.kt, ScryfallRequestQueue.kt
│   ├── tagging/          — Tag dictionary, analyzers, override repository
│   └── ui/{components,theme}
└── feature/              — One package per screen/flow
```

Most features: a `Screen.kt` Composable, a `ViewModel.kt` (`@HiltViewModel`), optional sub-composables.
Features with their own data layer (Draft, News) add `data/`, `domain/`, `di/` sub-packages.

## Key architectural decisions

### CardDao upsert
**Never use `OnConflictStrategy.REPLACE` on `CardEntity`.** REPLACE = DELETE + INSERT, which cascades
and silently deletes all `UserCardEntity` rows for that card. The DAO uses INSERT OR IGNORE + `@Update`
in a `@Transaction`. Regression test: `CardDao CASCADE regression`.

### Database (Room v38)
- DB file `mtg_collection.db`. `UserCardEntity` FK to `CardEntity` is `ON DELETE RESTRICT` (v38).
- Migration chain 1→38, gaps at 7–10 and 15–17 covered by `fallbackToDestructiveMigration()` (dev-only;
  not safe for production data).
- Schema: `app/schemas/com.mmg.manahub.core.data.local.MtgDatabase/`.

### Scryfall rate-limiting
All Scryfall calls must be wrapped in `ScryfallRequestQueue.execute { }` (≤10 req/s, 100 ms min between
requests, serialised by a `Mutex`).

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

### Navigation
Routes are a sealed class in `Screen.kt`; forward-slash hierarchy (e.g. `"collection/detail/{scryfallId}"`).
Bottom tabs: Collection, Stats, [central FAB = Game], Profile.

### DI
All ViewModels `@HiltViewModel`. `RepositoryModule` binds interfaces→impls (singleton).
`DispatcherModule` provides named dispatchers. Feature modules are separate
`@InstallIn(SingletonComponent::class)` modules.

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

### Trades
Data layer split across five repositories by concern: `TradesRepository` (proposals/thread),
`WishlistRepository`, `OpenForTradeRepository`, `TradeSuggestionsRepository`, `SharedListsRepository`.
Identify which owns a behavior before adding methods.

### Tournament
`TournamentViewModel` imports `PlayerConfig` from `feature.game.presentation` (not
`feature.tournament.domain.model`) by design. Must-know:
- Never do a DB write inside a `combine {}` transformer (Room re-emission → infinite loop).
- Multi-round Swiss/Single-Elim: `generateMatches` does round 1 only; use `GenerateNextRoundUseCase`
  for subsequent rounds — never `isFinished()` from the ViewModel.
- Draw = `finishMatch(winnerId = null, status = "FINISHED")` (1 point). Bye = finished match,
  `playerIds="[id]"`, `winnerId=id`. Tiebreakers: Points → OMW%(floor 33%) → GW% → OGW% (life total is
  display only).
- `insertTournamentAtomically`/`insertTournamentAtomically` writes need `@Transaction`; `tournamentId`
  validated > 0L at construction; create on `viewModelScope` not `rememberCoroutineScope`.
- Phase 2 pending: see `docs/plan-torneos.md`.
- → memory: `feedback_tournament_bugs_2026-05-24`, `feedback_tournament_phase1_2026-06-02`

### Deck Playtest (`feature/playtest/`, Phase 1 + Phase 2 battlefield complete)
Room v35→v36 added `playtest_sessions`, `playtest_card_stats`, `playtest_survey_answers`. Must-know:
- **Explicit-save-only**: redraw/mulligan loops are in-memory; nothing persists until "Save test" via
  `PlaytestDao.saveTestAtomically(@Transaction)` (the only sanctioned write path).
- `deck_id` is plain indexed TEXT, **not** a FK (decks are soft-deleted). Card stats are INT counts,
  not booleans.
- `PlaytestModule` only `@Binds` the repo — `PlaytestDao` already comes from `DatabaseModule`; don't
  add a duplicate `@Provides`.
- **PLAY phase (battlefield) lives in the SAME screen + SAME ViewModel** as MULLIGAN — it is conditional
  content (`PlaytestHandUiState.phase`), NOT a second nav destination or second `pendingX` handoff
  (avoids a second process-death-fragile in-memory handoff). Battlefield composables sit in
  `presentation/battle/` but are driven by `PlaytestHandViewModel`.
- **Battlefield is 100% ephemeral — zero DB writes** (same explicit-save rule). `Keep` no longer opens a
  save sheet; it calls `enterPlayPhase()`. The entire save+survey flow (`PlaytestSaveSheet`,
  `PlaytestSurveySheet`, `SavePlaytest(Survey)UseCase`, `save()`) is **DORMANT but intact** (re-wire when
  stats tracking returns) — do not delete or call it.
- **`End Test` never persists**: confirmation `AlertDialog` (copy must NOT mention saving) → `NavigateBack`.
  System Back in PLAY opens that same dialog via `BackHandler`.
- **`PlayCard.instanceId`** (monotonic Long from the VM, NOT scryfallId) is the stable key for every
  battlefield `LazyRow` — repeated copies of a card would otherwise crash on duplicate keys.
- **Cross-zone drag&drop**: each zone registers root bounds via `Modifier.onGloballyPositioned`; a
  long-press lifts a floating ghost in the root `Box` at `zIndex(Float.MAX_VALUE)`; on drop the pointer
  position is hit-tested against the bounds to resolve the target zone. Highlight the hovered zone with a
  dashed `drawBehind` stroke (NOT `Modifier.border`, which would affect layout). `onDragStart` must
  cancel if the card's `centerInRoot` is still `Offset.Zero` (not laid out yet); `onDragEnd` must cancel
  if `zoneBounds` is empty (first frame after rotation) — otherwise the ghost snaps to (0,0)/mis-drops.
- **Battlefield mutations are atomic**: `drawCard`/`moveCard`/`toggleTap` read AND write `battlefield`
  from the SAME `_uiState.update { state -> ... }` snapshot — never pre-capture `_uiState.value.battlefield`
  outside the lambda (stale capture lets two rapid draws mint a phantom deck+1 card). Conservation
  invariant: `hand+lands+permanents+graveyard+library` size is constant. Returning a card to `HAND`
  untaps it. Emit toasts OUTSIDE the update lambda (it may re-run).
- **One-shot events use a buffered `Channel` (`receiveAsFlow()`), never a nullable `MutableStateFlow`**: a
  StateFlow equality-collapses repeated events (2nd `NavigateBack`/`ShowInfo` lost) and drops them if the
  lifecycle pauses. Collect via `LaunchedEffect(Unit) { vm.events.collect { } }`, not
  `collectAsStateWithLifecycle`; there is no `onEventConsumed()`.
- Edge cases (null setup on process death, fixed `sessionStartedAt`, mulligan ≥1 floor, commander in
  mainboard, LazyRow key-by-index for duplicates, adaptive hand fan + arc rotation) → memory. The
  `PlaytestHandViewModelTest` needs `mockkStatic(FirebaseCrashlytics::class)` (logs outside runCatching).
- → memory: `project_playtest_persistence`, `project_playtest_battlefield_phase2`,
  `feedback_playtest_bugs_2026-05-28`

### Online sessions
**HTTP polling (3 s) is the primary mechanism; Supabase Realtime CDC is an optional fast-path** — never
a correctness dependency. Must-know:
- `startLobbyPolling()` runs unconditionally after create/join/resume; `connectAndObserve()` failure is
  silent (log only). **Never replace the participant list with a raw snapshot — always MERGE by id.**
- In-game actions are **broadcast-first** (Realtime) with DB persist as fallback. Guard every per-player
  broadcast handler against self-echo (`event.slotIndex != mySlotIndex`). Call `checkWinner()` after any
  handler that sets `defeated = true`.
- Session codes are **6-digit numeric** (`^[0-9]{6}$`). `disconnect()` must NOT `removeChannel()`.
  `FINISHED` must NOT set `isOnlineSessionAbandoned` (only `ABANDONED` does). Lobby skips disconnect when
  `gameLaunched`; `GameViewModel` owns the final disconnect. Local player always in BOTTOM slot.
- Connect order in game: disconnect stale → connect → snapshot → collect.
- **Guest access**: `LobbyHostViewModel` and `LobbyJoinViewModel` auto-sign-in anonymously (`authRepository.signInAnonymously()`) if `sessionState.value is Unauthenticated` before the first RPC call. Anonymous users have `AuthUser.isAnonymous = true` (read from Supabase `appMetadata["is_anonymous"]`). `isAuthenticatedFlow` in `HomeViewModel` excludes anonymous users — they never see account-gated features. All 15 session RPCs are GRANT'd to both `authenticated` and `anon` roles. Never call `upsertUserProfile` for anonymous users — they have no `user_profiles` row.
- → memory: `project_online_sessions`, `feedback_online_lobby_snapshot`, `feedback_online_game_sync`,
  `feedback_online_lobby_bugs_2026-05-28`, `feedback_online_ingame_sync_bugs_2026-05-28`,
  `project_gamesetup_hub_refactor`, `project_online_guest_support`

### Push notifications
Outbox pattern: write → trigger `fn_notify_*` → `enqueue_notification` → `notification_outbox` →
pg_cron (60 s) → Edge Function `send-push` → FCM HTTP v1 → device. Must-know:
- Opt-out prefs (missing key = enabled). `enqueue_notification` is **REVOKE-protected** — never grant
  client EXECUTE (accepts arbitrary `recipient_id`). Payload carries no PII.
- `PushDeeplinkRouter` buffers cold-start deeplinks (scheme `manahub://` only); don't store the
  NavController as an Activity field. Feature flag: `pushNotificationsEnabledFlow` (default `false`).
- Channels created once at app start (importance is immutable after first creation).
- → memory: `project_push_notifications`, `feedback_push_enqueue_security`, `feedback_push_deeplink_routing`

### Voice recognition (offline, Vosk)
Grammar-restricted Vosk models, **one language per game session** (never `Set<VoiceLanguage>` in
`start()`). Per-language download/delete from R2 (`voice-models/{en|es|de}.zip` in bucket
`manahub-assets`); a language is selectable only when its model is `Ready`; the active language can't be
deleted. To add a language: enum entry + `CommandGrammar` phrases + upload zip.
- → memory: `project_voice_controls`, `feedback_voice_test_architecture`

### Stats — key on the local seat, not playerName
Win/loss, win-rate, per-deck performance, session-history badges, and "most frequent loss" all resolve
against `player_sessions.is_local = 1`, **never** a `winnerName == playerName` match (the stored seat
name can diverge from UserPreferences, silently zeroing win-rate). Use `observeLocalWins()`,
`observeLocalSessionHistory()`, `observeLocalDeckGameStats()`, `observeMostFrequentElimination()`,
`observeArchetypeMatchups()`. Legacy `observeWins(playerName)` kept only for Profile/
`GetDeckGameStatsUseCase`.
- → memory: `feedback_survey_winloss_isLocal`, `project_per_seat_stats_model`

### Draft Simulator (`feature/draft/`, NOT live — rebuild in progress)
Content (tier list, guide, booster, engine) is generated offline and served by the Cloudflare
`manahub-draft-api` Worker from R2. Must-know:
- **Content pipeline is Python** at `scripts/draftsim_py/` (replaces the old Node `.mjs`). Generate one
  set at a time; **always ask the user for the Draftsim URLs per set** (guide + pick-order) — old sets
  break the canonical URL pattern, so never auto-derive them.
- **Booster = MTGJSON `play` collation.** Keep MTGJSON sheet names verbatim (the generic
  `WeightedBoosterGenerator` matches `variant.contents` keys to `sheets[name]`). Never collapse names —
  matching `"foil"` before `"land"` misfiles `foilLand`/`nonFoilLand` as foil and **deletes the land slot**.
  Booster sheet ids are oracle-collapse-remapped onto the app pool (`set:<code> lang:en unique:cards`) so
  everything resolves on-device with no app change; external sheets (`specialGuest`) are dropped and their
  slot re-homed to `common`.
- **Bot/suggested-pick engine must be archetype-based, not 2-color commitment** (the old
  `HeuristicBotDrafter` forces a 2-color pool and breaks 3+ color sets like TDM's wedges). Target: a
  data-driven `engine.json` per set + a generic `ArchetypeAwareBotDrafter`, with `HeuristicBotDrafter` as
  fallback when a set has no engine.json. The suggested pick uses the SAME engine as the bots.
- The draft engine (rotation/packs/rounds) is already generic on `DraftConfig.seatCount` (selector 2–10,
  default 8); SEALED forces 1 seat / 6 packs.
- **Robustness invariants (audit 2026-06-11):** `BotDrafter.pick` must `require(pack.cards.isNotEmpty())`
  (never crash on empty); `DefaultDraftEngine.autoPick` guards the empty human pack → returns state
  unchanged. `parseEngineConfig` SANITISES all engine.json floats (the Worker JSON is untrusted):
  non-finite weight → default, negative → `coerceAtLeast(0f)`, `rating` only trusted when finite & in
  `0f..1f`, negative/NaN `archetypeWeights` filtered out — a NaN breaks `maxWithOrNull`'s comparator and a
  negative weight anti-picks the committed lane. `getEngineConfig` must NOT hold `engineCacheMutex` across
  the network fetch (lock→check→release→fetch→re-lock→store). `DraftRatingNormalizer` guards `rank > 0`
  (rank 0 must not score 1.0f). Suggested-pick scoring runs on `@DefaultDispatcher`, not Main.
- → memory: `project_draftsim_redesign`, `feedback_draftsim_audit_2026-06-11`, `feedback_draft_booster_land_slot`

### Incomplete / quirks
- **DeckMagic**: `SETUP`/`REVIEW` steps are placeholder stubs — wired into nav but not production-ready.
- **SetPickerViewModel**: `clearFilters()` calls `applyFilters()` to respect `restrictedSets` — do not
  assign `filteredSets = allSets`.

### Deck Doctor
All 8 phases complete (Phases 1–8 verified, build green, 209 unit tests passing). Key invariants:
- `DeckFormat.valueOf()` must NOT be used — use `DeckFormat.entries.firstOrNull { ... } ?: STANDARD`.
- `generateFromSeeds()` captures inputs atomically inside `_uiState.update { }` (double-tap + stale-snapshot guards).
- `loadAnalysis()` cancels via `analysisJob?.cancel()` before relaunching; sets `isLoading` only when `health == null`.
- `CandidatePoolGenerator.legalityFragment()` returns `String?`; `DRAFT → null` (no legality restriction).
- `BudgetConstraints` has an `init` block validating finite/positive values.
- `SeedStrategy.TOKENS` test requires all 3 primary tags (TOKENS+AGGRO+TRIBAL) to beat AGGRO's tie.
- `stubRankAdds()` in tests must `candidates.take(arg<Int>(4))` to respect the `limit` parameter.
- → memory: `project_deck_doctor_phase6`, `project_deck_doctor_general`, `feedback_deck_doctor_audit_2026-06-08`

### Home dashboard (`feature/home/`)
Free-first, account-enhanced start screen. Fully implemented (2026-06-08). Must-know:
- **Start destination is `Screen.Home`** (not `Screen.Collection`). BottomBar is 3-slot: [Home] [⚔ FAB] [Library].
- `HomeViewModel` uses `combine(8 flows).stateIn(WhileSubscribed(5_000))` — no new DB tables. `avatarUrlFlow` MUST be subscribed/mocked in tests (blocks `accountFlow` combine if missing).
- Quick Start: 4 shortcuts persisted in DataStore via `UserPreferencesDataStore.observeQuickStartActions()`; partial-restore pads with defaults rather than discarding valid entries.
- Account nudge: 5-priority system (ACTION_REQUIRED > COLLECTION_MILESTONE ≥10 > DECK_MILESTONE ≥2 > GAME_MILESTONE ≥3 > SYNC_PENDING); 48h cooldown; ACTION_REQUIRED bypasses cooldown.
- `onBackHome`: `popUpTo(0) { inclusive = true }` (not `popUpTo(Screen.Collection.route)`) to avoid back-stack corruption on fresh install.
- → memory: `project_home_dashboard_redesign`, `feedback_home_stateIn_test_pattern`

### Home widget board (`feature/home/`, 3 phases complete 2026-06-09)
The dashboard is a **fully customizable widget board** (`LazyVerticalGrid` of 2 cols). ~40 widget
types in `HomeWidgetType` (each carries `persistedId`, `defaultTitleRes`, `supportedSizes`,
`category`, `audience`, `isAlwaysPresent`). Layout = ordered `List<WidgetInstance>(type,size)`. Must-know:
- **Layout persists in DataStore only** (`home_widget_layout` = ordered `"persistedId:SIZE"` tokens;
  unknown id/size tokens are silently skipped on decode; empty → auth-appropriate default). No new
  Room tables. `homeLayoutFlow(default)` takes the default as a param so the DataStore stays unaware
  of auth; the VM picks `defaultLayoutSignedIn/Out` via `isAuthenticatedFlow.flatMapLatest`.
- **Edit mode is transient** (`editModeFlow`, never persisted; resets each session). All layout
  reducers (`add/remove/move/resize/reset`) read the latest layout from `uiState.value.layout`,
  produce a new list, and `saveHomeLayout` immediately — DataStore re-emits as the single source of
  truth (no separate in-memory copy to drift). `CONTEXT_HERO.isAlwaysPresent` → cannot be removed.
  `ResizeWidget` rejects sizes not in `type.supportedSizes`.
- **Drag-to-reorder ghost lifts from the item's registered top-left + finger delta** (NOT the
  Playtest `centerInRoot` pattern, which snaps to center and jumps on corner-press). Container
  registers bounds via `onGloballyPositioned{boundsInRoot()}`; the dragged item is `alpha(0)`; a
  floating ghost at `zIndex(Float.MAX_VALUE)` is positioned at `bounds.topLeft + dragDelta`. On drag,
  `findTargetIndex(ghostCenter,...)` hit-tests and emits `MoveWidget`, then resets `dragDelta` to 0.
- **ViewModel combine arity**: slices are bundled (`CoreSnapshot`, `DataBundle{board,stats,discover,
  social}`) and folded with typed (non-vararg) `combine` overloads to avoid `Array<Any?>` erasure.
  `performanceFlow` MUST be declared before `statsSnapshotFlow` (property init order). Every data flow
  is `.catch{emit(empty/null)}`-isolated so one source failing never collapses the board.
- **Phase 2 data** derives from existing repos (win rate/best deck/nemesis/heatmap/matchups from
  `GameSessionRepository`, colors/rarity from `CollectionStats`, sets from `DraftRepository`). **Phase
  3 is stubbed**: `CommunityStatsRepository` (interface + `CommunityStatsRepositoryStub`→`flowOf(null)`
  bound in `CommunityModule`); trade/wishlist flows are `flowOf(null)` with TODOs. Account-gated
  widgets render `AccountGatedPlaceholder` (→ `CreateAccount`) when `!isAuthenticated`.
- `HomeWidgetHost` dispatches type→composable; `HomeWidgetContainer` adds bounds/edit overlay; all
  widgets share `WidgetShell` (surface+CardShape+min height S=96/M=132/L=220dp). No `success`/`error`
  tokens exist — win=`lifePositive`, loss=`lifeNegative`. Community null→spinner, empty→empty body.
- Top bar = time-of-day greeting (`Calendar.HOUR_OF_DAY`) + edit pencil (Edit↔Done) + avatar.
- → memory: `project_home_widget_board`

## Testing conventions

- Unit tests: MockK (`io.mockk`) + Turbine (`app.cash.turbine`). Instrumented Room tests: in-memory DB
  on device/emulator. Test classes mirror source package paths.
- **`testDebugUnitTest --tests "<pattern>"` compiles the ENTIRE `src/test` source set first** — a compile
  error in any unrelated test file fails the whole run and zero tests execute. To verify one suite in
  isolation when others are broken, temporarily move the broken files aside, run, then restore.
- ViewModels that call Crashlytics outside a `runCatching` block need `mockkStatic(FirebaseCrashlytics::class)`
  / `unmockkStatic` in `@Before`/`@After`.
- Online join test `SESSION_CODE` constants must be 6 digits (the join VM filters to digits only).
- → memory: `feedback_test_suite_compiles_as_unit`, `project_unit_test_fixes`

## Agent learning protocol

**All Android/Kotlin (`.kt`) work goes through the `android-kotlin-architect` agent** — net-new feature
code included, not only bug fixes. The main agent must not edit `.kt` files directly; delegate (passing
file path + line, the exact problem/feature, the proposed solution logic, and any CLAUDE.md constraints).
Non-Kotlin work (Python `scripts/draftsim_py/`, Worker JS, Gradle, docs, memory) is handled directly.
→ memory: `feedback_delegate_kotlin_to_architect`

When an agent identifies a bug or required fix in Android/Kotlin code, it MUST likewise **delegate the
fix to the `android-kotlin-architect` agent** rather than implementing it directly.

After any bug fix, security finding, or architectural/design decision, the agent MUST:
1. **Record the learning in memory first** — write/update a memory file (`feedback_<topic>.md` for fixes,
   `project_<topic>.md` for decisions; lead with the rule, then **Why:** and **How to apply:**) and update
   `MEMORY.md`.
2. **Update CLAUDE.md only if the rule is broadly applicable** — add it to the right section (or, for a
   feature, tighten the inline note and ensure the `→ memory:` pointer exists). Do not duplicate memory
   content here; keep this file small.

The goal: no agent should hit the same bug or repeat the same design mistake twice.

## Security notes

- HTTP logging `BODY` in debug only, `NONE` in release (`NetworkModule.kt`).
  `network_security_config.xml` blocks cleartext. Room DB + DataStore excluded from Drive auto-backup
  (`backup_rules.xml` / `data_extraction_rules.xml`). Scryfall queries sanitised with an allowlist.
  YouTube key injected via OkHttp interceptor (not in Retrofit signatures or Logcat).

### Pre-push security gate (MANDATORY)
Before any PR or push (even "docs-only"), the **`android-security-auditor` agent MUST secret-scan the
staged diff**:
1. Scan for keys/tokens/private keys/passwords/connection strings (`AIzaSy`, `-----BEGIN`, `secret`,
   `password`, `api_key`, `Authorization`, `Bearer`, Supabase URLs + service-role keys).
2. Confirm `google-services.json` is git-ignored and not staged.
3. Confirm no `.md`/`.txt`/`.json` file contains literal key **values** (referencing by name is fine).
4. Confirm nothing bypassed `.gitignore` via `git add -f`.

Block the push on any critical finding. → memory: `feedback_secret_leak_prevention`

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
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).
