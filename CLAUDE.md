# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

ManaHub is a Magic: The Gathering companion Android app (package `com.mmg.manahub`). It is a single-module Gradle project using Kotlin, Jetpack Compose, Clean Architecture, Hilt DI, and Room.

## Language rules

**All code, comments, commit messages, and string resources MUST be written in English.** This applies to: Kotlin files, XML layouts, `strings.xml`, Compose composables, DAO queries, variable/function/class names, inline comments, and KDoc.

**The app is English-only.** There are no translations (`res/values-es/`, `res/values-de/`, etc.) and none should be added. Do not write UI-facing strings in Spanish, German, or any other language. `TimeAgoFormatter` exists in `core/util/` but only its English output is in use — do not add or restore `es`/`de` locale branches.

## Build commands

```bash
# Debug build
./gradlew assembleDebug

# Release build (requires signing config)
./gradlew assembleRelease

# All unit tests
./gradlew test

# Single test class
./gradlew test --tests "com.mmg.manahub.feature.game.GameViewModelTest"

# Instrumented Room tests (requires connected device/emulator)
./gradlew connectedAndroidTest
```

The YouTube API key is optional. Without it the Draft Guide video feature is silently disabled. To enable it, add `YOUTUBE_API_KEY=your_key_here` to `local.properties` (git-ignored). The key is injected into `BuildConfig.YOUTUBE_API_KEY` via `app/build.gradle.kts`.

Room schema files are exported to `app/schemas/`. `ksp { arg("room.schemaLocation", ...) }` controls this.

## Architecture

**Pattern:** MVVM + Clean Architecture within a single Gradle module (`:app`).

```
com.mmg.manahub/
├── app/
│   └── navigation/       — Screen.kt (sealed routes), AppNavGraph.kt
├── core/
│   ├── data/
│   │   ├── local/        — Room DAOs, entities, converters, mappers
│   │   ├── remote/       — Scryfall Retrofit API, DTOs, remote mappers
│   │   └── repository/   — Repository implementations + CachePolicy.kt
│   ├── di/               — RepositoryModule, DispatcherModule (Hilt)
│   ├── domain/
│   │   ├── model/        — Domain models (card, deck, game, tournament, …)
│   │   ├── repository/   — Repository interfaces
│   │   └── usecase/      — Business logic use cases
│   ├── network/          — NetworkModule.kt, ScryfallRequestQueue.kt
│   ├── tagging/          — Tag dictionary, analyzers, override repository
│   └── ui/
│       ├── components/   — Shared composables (MagicToast, AddToCollectionSheet, …)
│       └── theme/        — AppTheme, MagicColors, MagicTypography, MagicTheme, PlayerTheme
└── feature/              — One package per screen/flow (see below)
```

Most features follow the same internal layout: a `Screen.kt` Composable, a `ViewModel.kt` with `@HiltViewModel`, and optional sub-composables. Features with their own data layer (Draft, News) add `data/`, `domain/`, and `di/` sub-packages inside the feature package.

## Key architectural decisions

### CardDao upsert pattern
**Never use `OnConflictStrategy.REPLACE`** on `CardEntity`. REPLACE internally does DELETE + INSERT which cascades and silently deletes all `UserCardEntity` rows for that card. The DAO uses INSERT OR IGNORE + @Update in a `@Transaction` method.

### Database (Room v21)
- DB file: `mtg_collection.db`
- `UserCardEntity` FK to `CardEntity` is `ON DELETE RESTRICT` (changed from CASCADE in v21 via 12-step SQLite table recreation)
- Migration chain: 1→21 with gaps at 7–10 and 15–17 covered by `fallbackToDestructiveMigration()` (safe for dev builds; do not rely on this for production data)
- Schema: `app/schemas/com.mmg.manahub.core.data.local.MtgDatabase/`

### Scryfall rate-limiting
All Scryfall API calls must be wrapped with `ScryfallRequestQueue.execute { }` to enforce the ≤10 req/s guideline (100 ms minimum between requests, serialised by a `Mutex`).

### Theming
Three themes: `NeonVoid`, `MedievalGrimoire`, `ArcaneCosmos` (sealed class `AppTheme`).
- Access colors via `MaterialTheme.magicColors` and typography via `MaterialTheme.magicTypography` — **never** use `MaterialTheme.colorScheme` or `MaterialTheme.typography` directly in Composables.
- Adding a new theme requires: a `MagicColors` instance, a `MagicTypography` instance, and a branch in `MagicTheme`'s `when(theme)` blocks.

### Notifications / toasts
**Always use `MagicToast`** — never use Material3 `SnackbarHostState`/`SnackbarHost`. Pattern:
```kotlin
val toastState = rememberMagicToastState()
Box {
    Scaffold { ... }
    MagicToastHost(toastState)
}
// Trigger from LaunchedEffect:
toastState.show("message", MagicToastType.ERROR)
```

### Shared UI components (`core/ui/components/`)
Use these before writing inline implementations:
- `EmptyState(icon?, title, subtitle?, actionLabel?, onAction?)` — empty list/screen states
- `InlineErrorState(message, actionLabel?, onAction?)` — error banner inside a screen
- `FullErrorState(message, actionLabel?, onAction?)` — full-screen centered error with retry
- `MagicToast` / `MagicToastHost` / `MagicToastState` — in-app notifications
- `CardGridItem`, `CardListItem` — standard card image tiles (use instead of inline `AsyncImage`)
- `AddToCollectionSheet`, `CardSearchSheet`, `TradeSelectionSheet` — reusable bottom sheets

### Navigation
All routes are defined in `Screen.kt` as a sealed class. Route strings use forward-slash hierarchy (e.g. `"collection/detail/{scryfallId}"`). Bottom tabs: Collection, Stats, [central FAB = Game], Profile.

### DI
- All ViewModels use `@HiltViewModel`.
- `RepositoryModule` binds interfaces to implementations (singleton scope).
- `DispatcherModule` provides named coroutine dispatchers.
- Feature-specific modules (e.g. `feature/draft/di/`, `feature/news/di/`) are separate `@InstallIn(SingletonComponent::class)` modules.

### Utilities
- `core/util/TimeAgoFormatter` — relative date formatting (English only). Use this; do not write inline `SimpleDateFormat` or `DateTimeFormatter` for relative times.

## Feature notes

### Trades
The trades data layer is split across five repositories by concern: `TradesRepository` (proposals/thread), `WishlistRepository`, `OpenForTradeRepository`, `TradeSuggestionsRepository`, `SharedListsRepository`. Each has its own DAO, remote data source, and Hilt binding. When adding trade functionality, identify which repository owns it before adding methods.

### Tournament
`TournamentViewModel` intentionally imports `PlayerConfig` from `feature.game.presentation` (not from `feature.tournament.domain.model`) because tournament player configs are passed directly to `GameViewModel` when launching a game. The two `PlayerConfig` classes are different by design.

**Tournament ViewModel / Flow rules (learned 2026-05-24):**
- Never call `repository.startTournament()` (or any DB write) inside a `combine {}` transformer. Room Flow emissions triggered by that write re-enter the transformer, creating an infinite loop. Put one-time side-effects in `init` or `viewModelScope.launch` outside the collector.
- `TournamentViewModel.tournamentId` must be validated > 0L at construction time — `SavedStateHandle.get<Long>` returns `0L` by default, not `null`.
- Manual "Record result" must be restricted to `status == "PENDING"` matches only. ACTIVE matches have a live game session; allowing manual record there races with `GameViewModel.recordTournamentResultIfNeeded`.
- `insertTournamentAtomically` must have `@Transaction` — without it, a crash between the 3 inserts leaves an unrecoverable partial tournament.
- `createTournament` in `TournamentSetupViewModel` must run on `viewModelScope`, not `rememberCoroutineScope`. Screen scope is cancelled on back-press, which can create ghost tournaments in the DB with no navigation.

**Tournament Phase 1 overhaul (2026-06-02) — new invariants:**
- **Multi-round Swiss/Single Elim**: `generateMatches` now generates only round 1. `GenerateNextRoundUseCase` handles subsequent rounds (delegates to `SwissEngine` / `SingleEliminationEngine`). **Never call `isFinished()` from `TournamentViewModel` for multi-round detection** — use `GenerateNextRoundUseCase.invoke()` which returns `TournamentFinished`, `RoundGenerated(n)`, or `RoundNotComplete`.
- **Bye representation**: A bye is a `TournamentMatchEntity` with `playerIds = "[id]"`, `winnerId = id`, `status = "FINISHED"`. Byes are pre-inserted as finished matches. `StandingsCalculator` excludes bye opponents from OMW% (they don't count toward the opponent strength calculation).
- **Draws**: `TournamentRepository.finishMatch(matchId, winnerId = null, ...)` records a draw. `winnerId = null` + `status = "FINISHED"` = draw. `StandingsCalculator` counts draws as 1 point. **No schema change required** — `winnerId` was already nullable in `TournamentMatchEntity`.
- **Tiebreaker order**: Points → OMW% (floor 33%) → GW% → OGW%. Life total is kept in `TournamentStanding.lifeTotal` for display but is **NOT a sort criterion**. Any test that asserts life-total tiebreaker is testing wrong behavior.
- **ACTIVE match soft-lock**: `TournamentViewModel.resetMatch(matchId)` resets an ACTIVE match to PENDING. `TournamentViewModel.resumeMatch(matchId)` navigates directly without calling `startMatch`. `TournamentScreen` exposes Resume and Reset buttons for ACTIVE matches.
- **Odd players**: Swiss and Single Elim now accept odd player counts — no error, byes assigned automatically by the engine. The validation block in `TournamentSetupViewModel` was removed.
- **DI**: `TournamentViewModel` requires `GenerateNextRoundUseCase`, `CalculateStandingsUseCase`, `RecordMatchResultUseCase` via `@Inject`. Tests must mock all three.
- **Crashlytics in unit tests**: Call `mockkStatic(FirebaseCrashlytics::class)` / `unmockkStatic` in `@Before`/`@After` whenever a ViewModel under test calls Crashlytics directly outside a `runCatching` block. Pattern copied from `CollectionSyncTest`.
- **`dao.getMatchById` mock required**: `finishMatch` in `TournamentRepositoryImpl` calls `dao.getMatchById` to validate the winner. Tests that call `finishMatch` must mock `coEvery { dao.getMatchById(any()) } returns <match with valid participantIds>` — relaxed mocks return `null` which triggers `IllegalArgumentException`.
- **Phase 2 pending**: Schema normalization (migration 36→37, `tournament_match_players` table), best-of-N game tracking, edit/undo result, delete tournament, Commander pods. See `docs/plan-torneos.md`.

### Deck Playtest (Phase 1 complete)

**UI + data layer implemented.** Feature package: `feature/playtest/`.

**Architecture decisions:**
- `drawCount` in `PlaytestSessionEntity` = the **configured** draw count from the setup screen (e.g. 7). `finalHandSize = drawCount - mulligansUsed`. Both fields are stored so Phase 2 aggregates can compute both.
- `pendingPlaytestSetup: PlaytestSetup?` is stored as a `remember { mutableStateOf }` variable in `AppNavGraph` (same pattern as `pendingPlayerConfigs`) to pass the setup object from `PlaytestSetupScreen` to `PlaytestHandScreen` without nav-arg serialization. The setup holds hydrated card data that is impractical to encode in a route string.
- `PlaytestSurveyEngine` is a feature-local `object` (not part of `feature.survey.presentation.SurveyQuestionEngine`) but reuses `AnswerOption` and `SurveyChoice` from the survey module. Panels/questions use separate types (`PlaytestSurveyPanel`, `PlaytestSurveyQuestion`) to avoid polluting the game survey engine.
- `PlaytestHandViewModel.buildAndDraw()` calls `deckRepository.observeDeckWithCards(deckId).first()` to get the current deck state once, then builds the library in one batch. The library is rebuilt from scratch on every draw/redraw to guarantee a fresh shuffle.
- `PlaytestModule` only `@Binds` the repository — the `PlaytestDao` is already provided by `DatabaseModule.providePlaytestDao()` (line ~509). Never add a duplicate `@Provides` for `PlaytestDao`.
- Entry points: (1) `PlayArrow` icon in each `DeckItem` row in `DeckListScreen`, (2) `PlayArrow` icon in `DeckMagicDetailScreen` TopBar.
- Eligibility: `CanPlaytestDeckUseCase` returns `Ineligible(reason)` for formats other than standard/draft/commander, or when mainboard size fails the threshold. Reason is surfaced as `MagicToast.INFO`.

**Critical invariants (learned from edge-case audit):**

1. **Hand route must handle null `pendingPlaytestSetup`**: On process death the in-memory setup is lost. The `PlaytestHand` composable route must render `FullErrorState` with a "Back" action when `setup == null` — never render a blank screen.

2. **`sessionStartedAt` is fixed at first draw, never changes on redraw**: Set once in `initWithSetup` via `sessionStartedAt = System.currentTimeMillis()` and referenced from `HandSnapshot.startedAt` on every subsequent draw/redraw. Never call `System.currentTimeMillis()` inside `buildAndDraw` for the start-time field.

3. **Mulligan cannot reduce the kept hand below 1 card**: Disable the Mulligan button (and early-return in `onMulligan`) when `mulligansUsed >= drawCount - 1`. This ensures the minimum keepable hand size is always ≥ 1. Guard applies in both ViewModel and screen.

4. **Commander must be present in the mainboard for commander-format playtests**: In `PlaytestSetupViewModel.loadDeck`, after resolving the commander card, verify that `commanderCardId` is present among the mainboard slots. If the commander is set but missing from the mainboard, override eligibility to `Ineligible` with a descriptive reason. Skipping this check causes `BuildLibraryUseCase` to build a 100-card library while the command zone also shows the commander (double-count).

5. **Survey CardImpact LazyRow must use `itemsIndexed` keyed by index**: Using `key = { it.scryfallId }` crashes with `IllegalArgumentException` when the hand contains 2+ copies of the same card. Key by index; the per-card answer map key must also include the index (`"${questionId}:${scryfallId}:$index"`).

6. **`onDrawHand` double-tap guard**: Use an `isNavigating` boolean in `PlaytestSetupViewModel`. Set it true on the first invocation; reset it in `onEventConsumed()` so Back-then-retry works.

**`PlaytestHandScreen` layout invariants (fixed 2026-05-28):**

- **Adaptive fan in `HandFanRow`**: card width and step are computed inside `BoxWithConstraints`, not hardcoded. The formula is: `step = (availableWidth - cardWidth) / (handSize - 1)`, then clamped into `[cardWidth * 0.18, cardWidth - 24dp]`. If the resulting fan still overflows, card width is reduced by 10 % per iteration (up to 20 iterations). If it still overflows after that, a `LazyRow` fallback is used — nothing is ever clipped. The 0.18 floor is the minimum overlap that keeps cards distinguishable; the 24dp ceiling is the minimum visual overlap between cards.
- **DnD uses `detectDragGesturesAfterLongPress` in a separate `pointerInput` modifier** from the tap gesture in `PlaytestHandCard`. Both can coexist because each runs in its own `pointerInput` scope. The tap block has key `card.scryfallId`; the drag block has keys `(index, snapshotId)` — mismatching these keys causes gestures to not reset on hand change.
- **Drag-and-drop state is keyed on `snapshotId`**: `remember(snapshotId)` ensures `draggingIndex` and `dragOffsetX` reset to zero whenever a new hand is drawn.
- **Landscape layout**: `HandContent` detects `Configuration.ORIENTATION_LANDSCAPE` and switches between `PortraitHandContent` (Column) and `LandscapeHandContent` (Row with left 160dp / center weight 1f / right 110dp). The right column uses `SideActionBar` (buttons stacked vertically). The adaptive fan algorithm inside `HandFanRow` requires no changes for landscape — it reads the actual `BoxWithConstraints` dimensions.

### Deck Playtest — Crashlytics instrumentation (added 2026-05-28)

Four files carry Crashlytics instrumentation. All use the project-standard inline pattern (`FirebaseCrashlytics.getInstance().apply { log(...); setCustomKey(...); recordException(...) }`) — **no new helpers introduced**.

**Custom keys set per session** (in `PlaytestHandViewModel.initWithSetup` and `PlaytestSetupViewModel.onDrawHand`):
`playtest_deck_id`, `playtest_format`, `playtest_draw_count`, `playtest_on_the_play`, `playtest_mulligans_used` (updated on each mulligan), `playtest_library_size` (set once at first draw).

**Non-fatal sites:**
- `PlaytestSetupViewModel.loadDeck`: commander missing from mainboard — `IllegalStateException` records a data-consistency signal without blocking the user.
- `PlaytestHandViewModel.buildAndDraw`: (a) `CardDao.getByIds` under-fetch (cache eviction signal); (b) library size mismatch vs expected mainboard count.
- `PlaytestHandViewModel.save`: `runCatching.onFailure` now **also** calls `recordException` in addition to existing `Log.e`.
- `PlaytestHandViewModel.onSurveyFinished`: survey DB write failure.
- `PlaytestRepositoryImpl.saveTest` / `saveSurveyAnswers`: repository-layer non-fatals that rethrow so the ViewModel error path still fires.
- `AppNavGraph` playtest-hand route `else` branch: process-death null-setup detection.

### Deck Playtest (data layer only — Phase 1)

Three new Room tables added in migration v35→v36:

| Table | Purpose |
|---|---|
| `playtest_sessions` | One row per SAVED test. Written only on "Save test". |
| `playtest_card_stats` | Per-card copy counts for the kept opening hand and mulligan bottoms. FK to `playtest_sessions` with CASCADE. |
| `playtest_survey_answers` | Optional survey answers attached to a saved test. FK with CASCADE. Zero rows = user skipped survey. |

**Explicit-save-only rule:** In-flight redraw/mulligan loops are purely in-memory. Nothing is written to the DB until the user taps "Save test". `PlaytestDao.saveTestAtomically(@Transaction)` is the ONLY sanctioned write path for a session + card-stats pair.

**No hard FK on `deckId`:** `playtest_sessions.deck_id` is a plain TEXT column with an index, not a FK to `decks`. Why: decks are soft-deleted (`is_deleted = true`), not physically removed. A hard FK would either block future hard-delete scenarios or cascade-delete all playtest history for the deck — both are unacceptable for a historical insights feature. Storing `deckId` as a plain indexed TEXT keeps aggregate queries simple (`WHERE deck_id = :deckId`) and history durable.

**Counts, not booleans, for card stats:** `copies_in_opening_hand` and `copies_bottomed_on_mulligan` are INT counts. Standard MTG allows up to 4 copies of a card; a single hand can contain 2-3 of the same scryfallId. A boolean `appeared: yes/no` loses the flood/screw signal and makes average-copies-per-hand impossible to compute correctly.

**Phase 2 columns (reserved, not yet added):** `playtest_card_stats` will gain `copies_drawn_in_game` and `turns_until_played` in a future migration. Do not add them now; their names are reserved in comments on the entity to avoid future migration conflicts.

**DAO (`PlaytestDao`):** abstract class following the `SurveyAnswerDao` pattern. Provides `saveTestAtomically`, `replacePlaytestSurveyAnswers`, and Flow-based aggregate queries for per-deck and per-(deck, card) opening-hand statistics.

### DeckMagic
`DeckMagicScreen` and `DeckMagicViewModel` are an **incomplete feature** — the `SETUP` and `REVIEW` steps render placeholder `Text("...")` stubs. The screen is wired into navigation but not production-ready.

### SetPickerViewModel
`clearFilters()` calls `applyFilters()` internally so it respects the `restrictedSets` constraint. Do not assign `filteredSets = allSets` directly.

### Stats — game stats must key on the local seat, not playerName (Phase 4, 2026-06-02)
Win/loss, win-rate, per-deck performance, session-history W/L badges, and "most frequent loss" are all resolved against the `player_sessions.is_local = 1` seat, **never** a `winnerName == playerName` string match. The stored seat name can diverge from the current UserPreferences name (default `"Wizard"`), which silently zeroed the win-rate. Use `GameSessionDao.observeLocalWins()`, `observeLocalSessionHistory()`, `observeLocalDeckGameStats()`, and `observeMostFrequentElimination()` (which now filters `is_local = 1`). Matchup win-rate (`observeArchetypeMatchups()`) self-joins `player_sessions` (local seat ⋈ opponent seats) grouped by `opp.archetype`. The legacy `observeWins(playerName)` / `observeDeckGameStats(playerName)` are kept only for Profile and `GetDeckGameStatsUseCase` — do not reuse them in Stats. Per-deck stats read `gs.deckId` (survey-set UUID) for backward compat, not the new `ps.deck_id`.

### MagicTypography token set
`MaterialTheme.magicTypography` has **no `titleSmall`** (unlike M3 `Typography`). Available: display{Large,Medium}, title{Large,Medium}, label{Large,Medium,Small}, body{Large,Medium,Small}. Use `titleMedium` for small section headers. `ty.titleSmall` is an unresolved reference that breaks `compileDebugKotlin`.

## Testing conventions

- **Unit tests** use MockK (`io.mockk`) and Turbine for Flow testing (`app.cash.turbine`).
- **Instrumented Room tests** use an in-memory database and run on a device/emulator.
- Test classes mirror source package paths under `src/test/` and `src/androidTest/`.
- The key Room regression test is `CardDao CASCADE regression` — verifies that upsert does not delete user_cards rows.
- **Online join test `SESSION_CODE` constants must be 6 digits** (e.g. `"123456"`). `LobbyJoinViewModel.onCodeChanged()` filters to digits only — an alphanumeric constant like `"XYZ999"` produces `""` after filtering, causing all join mocks to be unreachable.
- **`./gradlew testDebugUnitTest --tests "<pattern>"` compiles the ENTIRE `src/test` source set before running the filtered subset.** A compile error in any unrelated test file fails the whole run (`compileDebugUnitTestKotlin FAILED`) and zero tests execute. As of 2026-06-02 on `feat/push-fase-0-prep`, several test files are already broken from the PR #82 online-sessions merge (stale ViewModel/repository constructor signatures, removed enum values): `GameViewModelOnlineTest`, `GameViewModelNearbyTest`, `feature/game/GameViewModelTest`, `LobbyHostViewModelTest`, `NearbySessionRepositoryImplTest`, `OnlineSessionRepositoryImplTest`. To verify an unrelated suite in isolation, temporarily move the broken files aside, run, then restore — do not commit fixes to them unless that is the task.

## Agent learning protocol

**When an agent (edge-case tester, security auditor, or any other) identifies a bug or required fix in Android/Kotlin code, it MUST delegate writing the fix to the `android-kotlin-architect` agent instead of implementing it directly. The delegating agent should pass: the file path and line number, the exact problem, the proposed solution logic, and any constraints from CLAUDE.md.**

**Every time an agent fixes a bug, resolves a security finding, or makes an architectural/design decision, it MUST:**

1. **Update this file (`CLAUDE.md`)** — add or update the relevant section (Key architectural decisions, Feature notes, Security notes, etc.) with the new constraint, pattern, or anti-pattern. Include *why* the rule exists, not just what it is, so future agents can apply judgment in edge cases.

2. **Write a memory file** at `C:\Users\Miguel\.claude\projects\E--Projects-ManaHub\memory\` and update `MEMORY.md`:
   - Bug fixes → `feedback_<topic>.md` (type: `feedback`) — lead with the rule, then **Why:** and **How to apply:**.
   - Architectural decisions → `project_<topic>.md` (type: `project`) — lead with the decision, then **Why:** and **How to apply:**.
   - Do NOT duplicate what is already documented in CLAUDE.md; memory is for non-obvious context that helps future agents make better judgments.

**The goal:** no agent should encounter the same bug twice or make the same design mistake. Each fix must leave a permanent record that prevents recurrence.

### Online sessions

Lobby phase uses **HTTP polling (3 s interval) as the primary communication mechanism**.
Supabase Realtime CDC is wired as an optional fast-path but must never be a dependency for
lobby correctness — it may not be configured in the Supabase dashboard.

Critical invariants:
- `startLobbyPolling()` must be called immediately after `createSession`/`joinSession`/`resumeSession` regardless of whether `connectAndObserve()` succeeds.
- `connectAndObserve()` failure must be silent (log only, no error toast) — polling covers it.
- Snapshot merge logic and polling loop must live in `startLobbyPolling()` and `refreshParticipants()` — **not** inside `connectAndObserve()`.
- Game-start detection for joiners is done by checking `snapshot.session.status == ACTIVE` in the poll, not only via Realtime `SessionStatusChanged` events.
- The guard `_uiState.value.sessionStatus != ACTIVE` in the poll prevents double-firing `onGameStart` if both Realtime and polling detect the transition.

**Never replace the participant list with a raw HTTP snapshot — always MERGE.**
Add only participants whose `id` is NOT already in `state.participants`. Realtime events may have already delivered a JOIN event that the HTTP snapshot doesn't yet reflect due to propagation lag.

Pattern:
```kotlin
val currentIds = state.participants.map { it.id }.toSet()
val merged = (state.participants + ps.filter { it.id !in currentIds }).sortedBy { it.slotIndex }
```

**In-game event sync (online sessions)** — all game actions that should be visible to the opponent must be broadcast via Supabase Realtime broadcast (not only persisted to DB). The broadcast is the fast path; DB persist is the fallback for reconnects. The current broadcast/persist split:

| Action | Broadcast event | DB persist | Notes |
|--------|----------------|------------|-------|
| Life change | `life_delta` | 500 ms debounce | `updateLifeUseCase.broadcast()` + `schedulePersistLife()` |
| Phase advance | `phase_change` (includes `activePlayerSlot`, `turnNumber`) | 500 ms debounce | `advancePhaseUseCase.broadcast()` + `schedulePhasePersist()` |
| Next turn | `phase_change` (reused) | immediate | `advancePhaseUseCase.broadcast()` + `nextTurnUseCase()` |
| Counter (poison/exp/energy) | `counter_update` | immediate | `updateCounterUseCase.broadcast()` + `updateCounterUseCase()` |
| Commander damage | `commander_damage` | immediate | `updateCommanderDamageUseCase.broadcast()` + `updateCommanderDamageUseCase()` |
| Defeat confirmed | `defeat_confirmed` | immediate | `confirmDefeatUseCase.broadcast()` + `confirmDefeatUseCase()` |
| Land toggled | `land_toggled` | broadcast only | `toggleLandPlayedUseCase.broadcast()` — cleared by next-turn broadcast |

**Session codes are 6-digit numeric strings** (`000000`–`999999`). Backend generates them with `lpad(floor(random()*1000000)::int::text, 6, '0')`. Client regex is `^[0-9]{6}$`. Join screen uses `KeyboardType.Number`. Do NOT use alphanumeric codes.

**`SessionStatusChanged.FINISHED` must NOT set `isOnlineSessionAbandoned`** — that flag is only for `ABANDONED`. When the status is `FINISHED`, let `defeated = true` propagate via broadcast (`DefeatConfirmedReceived`) or CDC (`PlayerStateUpdated`), which call `checkWinner()` and trigger the result screen. Setting `isOnlineSessionAbandoned` on `FINISHED` hides the `GameResultScreen` entirely on the other device. The polling (`startInGameSyncPolling`) must also only early-exit on `ABANDONED`, falling through on `FINISHED` to update player states.

**`SupabaseRealtimeClient.disconnect()` must NOT call `removeChannel()`** — doing so closes the Supabase Realtime WebSocket, and the next session's `subscribe()` throws `IllegalArgumentException: Engine doesn't support WebSocketCapability`. The scope is cancelled and the entry removed from `sessions`, but the SDK channel is intentionally left in its registry so the WebSocket stays alive.

**`PhaseChangedPayload` carries `activePlayerSlot` and `turnNumber`** so that a single broadcast keeps phase, active player, and turn in sync atomically. Handlers for `SessionEvent.PhaseChangedReceived` must update all three fields AND clear `hasPlayedLand` when the active player or turn number changes (`val newTurn = event.activePlayerSlot != s.activePlayerId || event.turnNumber != s.turnNumber`).

**`checkWinner()` must be called after any handler that sets `player.defeated = true`** — `PlayerStateUpdated`, `DefeatConfirmedReceived`, and the in-game polling all call it. Missing this call means the end-game screen never shows on the receiving device.

**Lobby→game channel ownership (fixed 2026-05-28):** Both `LobbyHostViewModel` and `LobbyJoinViewModel` have a `gameLaunched: Boolean` flag. When `onGameStart` is about to be called (either via Realtime event or polling), the flag is set to `true`. In `onCleared()`, `observeSessionUseCase.disconnect(sessionId)` is skipped when `gameLaunched == true`. This prevents the lobby's cleanup from killing the Supabase Realtime channel that `GameViewModel` is already collecting from. `GameViewModel.onCleared()` owns the final disconnect.

**Local player always in BOTTOM slot for 2-player online games:** `GameViewModel.initFromOnlineSession()` and `initFromNearbySession()` both call `buildOnlineGridAssignment(layout, mySlotIndex)` to compute the initial `gridAssignment`. This helper finds the `FULL_WIDTH_BOTTOM` slot in the layout and swaps the assignment so the local player always renders there (normal 0° orientation, readable without rotating the device). If the local player is already at the bottom, the map is empty (identity).

**Online player interaction restriction:** In `GamePlayScreen.GamePlayerGrid`, all interactive `PlayerCard` callbacks (`onLife`, `onCmdPanel`, `onCtrPanel`, `onConfirmDefeat`, `onRevokeDefeat`, `onEndTurn`, `onLandToggle`) are replaced with no-op lambdas when `isOnlineSession && !player.isAppUser`. Players can only mutate their own card's state.

**In-game sync: Realtime + polling dual-path (fixed 2026-05-28, order corrected 2026-05-28):** `GameViewModel.connectAndObserveOnlineSession` executes in this exact order: (1) `disconnect()` stale lobby channel, (2) `connect()` game-owned subscription, (3) `getSnapshot()` HTTP initial state, (4) `collect()` events. The subscribe-before-snapshot order is critical — any broadcast that arrives during the HTTP snapshot call lands in the Realtime replay buffer and is not lost. If Realtime `connect` fails, the game is NOT abandoned — `startInGameSyncPolling(sessionId)` provides a 3-second DB polling fallback. The `collect()` call is wrapped in `runCatching` so a WebSocket drop is logged to Crashlytics (key: `online_session_id`) instead of silently cancelling `onlineObserveJob`.

**`DefeatConfirmedReceived` and `LandToggledReceived` handlers must guard against self-echo:** Supabase broadcasts events back to the sender. Both handlers now check `if (event.slotIndex != mySlotIndex)` before mutating state, consistent with all other per-player broadcast handlers (`LifeDeltaReceived`, `CounterUpdatedReceived`, `CommanderDamageReceived`).

### Push Notifications

End-to-end delivery is an **outbox pattern**: writes enqueue a row, a cron job drains it, an Edge Function sends it via FCM. No persistent server process is required (works on the Supabase free tier).

```
Supabase RPC/write → trigger fn_notify_* → enqueue_notification → notification_outbox
  → pg_cron (every 60s polls pending rows) → Edge Function send-push
  → FCM HTTP v1 (RS256 JWT) → device
  → ManaHubMessagingService.onMessageReceived → NotificationCompat.Builder → system tray
```

Key decisions and invariants future agents must know:

1. **Outbox model is opt-out (missing key = enabled).** `notification_prefs.prefs` is a `jsonb` map of `event_type → boolean`. A **missing key means the type is enabled**; only an explicit `false` silences it. This is deliberately opt-out so new event types are delivered automatically without a migration to backfill preferences.

2. **`enqueue_notification` is NOT callable by clients.** `REVOKE EXECUTE ON FUNCTION public.enqueue_notification FROM PUBLIC, anon, authenticated` was applied in migration `20260602_push_security_fixes`. It is invoked only from the triggers (`fn_notify_trade_proposals`, `fn_notify_friendships`). **Never grant client execute on this function** — it accepts an arbitrary `recipient_id`, so a client grant would allow spamming any user. The per-pair rate limit and anti-self-notify guard are defense-in-depth, not the primary protection.

3. **Payload contains NO PII.** A `notification_outbox` payload holds only: `event_type`, `recipient_id`, `actor_id`, `entity_id`, `thread_id`, `dedupe_key`. No collection, financial, or message-text data. The human-readable title/body is resolved at send time by the Edge Function from `notification_templates`.

4. **Token lifecycle:** `upsert_device_token` on `Authenticated`; `delete_device_token` (this device only) on `Unauthenticated`; `delete_my_device_tokens` (all devices) on account deletion — called from `DeleteAccountUseCase` **before** `deleteAccount()` while the session is still live (after deletion the RPC would be unauthorized).

5. **`PushDeeplinkRouter` — cold-start buffer.** When a notification is tapped with the app killed, `MainActivity.onCreate` calls `PushDeeplinkRouter.enqueue(deeplink)` **before** the NavController is composed. The router buffers the deeplink and flushes it from `AppNavGraph` when `DisposableEffect(navController)` registers the navigator. Do NOT store the NavController as an Activity field — `MainActivity` is `singleTask` and the controller exists only after composition; a field reference leaks and NPEs. Deep links must use scheme `manahub://`; any other scheme is logged and rejected before `navController.navigate()`.

6. **`ForegroundScreenTracker` — foreground suppression.** `TradeNegotiationDetail` registers its deeplink via `ForegroundScreenTracker.setCurrentDeeplink(...)` in a `DisposableEffect`. `ManaHubMessagingService.onMessageReceived` skips building the notification when the incoming deeplink matches the current foreground screen (no point notifying about the screen the user is already viewing).

7. **Feature flag.** `UserPreferencesDataStore.pushNotificationsEnabledFlow` (default `false`) gates notification display in `onMessageReceived`. Set to `true` for rollout. The pg_cron polling is gated implicitly — if no device tokens exist, the Edge Function sends nothing.

8. **Channels (created at app start, immutable after first creation):**
   - `trades_high` (`IMPORTANCE_HIGH`): proposed / countered / accepted
   - `trades_updates` (`IMPORTANCE_LOW`): declined / edited / cancelled / revoked / completed
   - `friends` (`IMPORTANCE_HIGH`): all friend events
   Created in `ManaHubApp.createNotificationChannels()`. Android caches channel importance after first creation — changing the `IMPORTANCE_*` constant in code has no effect on existing installs.

9. **`NotificationPrefsRepositoryImpl` — Mutex-guarded read-merge-upsert.** `setEventEnabled` re-reads server state inside a `Mutex` before merging, so two rapid toggles cannot clobber each other. **Do not refactor this to merge from the local StateFlow cache** — the cache can be stale relative to the server and would reintroduce the lost-update race.

10. **Operator manual steps (required before any push flows):**
    - `supabase secrets set FCM_SERVICE_ACCOUNT='...'` (FCM service-account JSON for the RS256 JWT)
    - Add `SUPABASE_SERVICE_ROLE_KEY` to Supabase Vault so pg_cron can authenticate to the Edge Function
    - `google-services.json` must be present locally (git-ignored)

11. **Supabase free-tier caveat.** A free-tier project pauses after 7 days of inactivity; pg_cron jobs stop while paused. On resume, pending `notification_outbox` rows are processed on the next cron tick (delivery is delayed, not lost). Monitor delivery rate with the SQL query in migration `20260602_push_outbox_retention`.

### Voice recognition (offline, Vosk — multi-language, 2026-06-03)

Offline voice recognition uses the **Vosk** library with grammar-restricted models. One model is loaded per game session (single language, not multi-model).

**Architecture:**
- `core/voice/domain/VoiceLanguage` — `ENGLISH`, `SPANISH`, `GERMAN`, each carries `modelFileName` (zip name) and `modelDirName` (local dir).
- `VoiceModelRepository.modelStates: StateFlow<Map<VoiceLanguage, VoiceModelState>>` — per-language state; missing key = `NotDownloaded`.
- `VoiceModelRepository.download(language)` / `delete(language)` — independent per language.
- Model dirs: `${filesDir}/voice-models/{en|es|de}/`
- Download URL: `${CLOUDFLARE_WORKER_URL}/voice/models/{lang}.zip` → Worker fetches `voice-models/{lang}.zip` from R2 bucket `manahub-assets`.
- `VoiceCommandRecognizer.start(commands, language: VoiceLanguage)` — single language, not `Set`.
- `GameSettings.voiceLanguage: VoiceLanguage` — single selection (not Set); only selectable if that language's model is `Ready`.

**Cloudflare R2 model keys** (bucket: `manahub-assets`):
- `voice-models/en.zip` — Vosk small English (uploaded pre-2026-06)
- `voice-models/es.zip` — Vosk `vosk-model-small-es-0.42` (uploaded 2026-06-03)
- `voice-models/de.zip` — Vosk `vosk-model-small-de-0.15` (uploaded 2026-06-03)

To add a new language: add enum entry to `VoiceLanguage`, add phrases to `CommandGrammar`, upload `{lang}.zip` to R2.

**Key invariants:**
- Never use `Set<VoiceLanguage>` in `VoiceCommandRecognizer.start()` — Vosk loads one `Model` per `SpeechService`; multi-model simultaneous recognition is not supported.
- The selected language is only changeable if its model is `Ready`. Toggling voice features (land reminder, end turn) is gated on `voiceModelStates[gameSettings.voiceLanguage] is Ready`.
- `VoiceLanguagesSheet` (ModalBottomSheet in `GameSetupScreen`) is the download/select UI. Settings screen has a mirror `VoiceRecognitionSection` for delete/download management without navigating to game setup.
- The active language cannot be deleted from `VoiceLanguagesSheet` — the delete button is hidden when `isSelected`. This prevents a state where voice toggles are enabled but no model is available.

## UI / Jetpack Compose (ManaHub)

When working on any UI, screen, Composable, or visual element, follow these non-negotiables. Full
detail (design system, component templates, dos/don'ts, render-to-PNG loop) lives in the **`compose-ui`**
skill; this is the always-loaded floor.

- ManaHub tokens only — access colors via `MaterialTheme.magicColors` and typography via
  `MaterialTheme.magicTypography`; use the named shape tokens (`CardShape`, `ChipShape`, `ButtonShape`,
  `BottomSheetShape`). **Never** read `MaterialTheme.colorScheme` / `MaterialTheme.typography` directly
  (they exist only to bridge inherited M3 components — see the theme rule under *Key architectural
  decisions*). Never hardcode a `Color`, `dp` font size, or shape.
- Spacing comes from `MaterialTheme.spacing` (8dp-grid scale: 2/4/8/12/16/24/32, defined in
  `core/ui/theme/Spacing.kt`, provided by `MagicTheme`). No arbitrary `dp` values.
- Support all themes. There are **12 `AppTheme` palettes**, dark-first, with exactly **one light theme**
  (`HallowedPrint`). Themes are fixed palettes — never branch color on the active theme or on
  `isSystemInDarkTheme()`. Spot-check NeonVoid + HallowedPrint (the contrast extremes).
- Every interactive element ≥ 48dp touch target.
- Stateless Composables with state hoisted; no business logic or ViewModel access inside reusable UI.
- `LazyColumn`/`LazyVerticalGrid` with stable keys for lists — never `Column` + `verticalScroll` for
  unbounded content. Key by a unique id or index, never a value that can repeat (e.g. duplicate cards).
- Handle every state: loading, empty, error, content. Reuse the shared composables in
  `core/ui/components/` (`EmptyState`, `InlineErrorState`, `FullErrorState`, `CardGridItem`, …) before
  writing new ones. In-app notifications use `MagicToast` — never `SnackbarHost`.
- Accessibility: meaningful `contentDescription` (or `null` for decorative), AA contrast, correct
  semantics. Edge-to-edge with proper insets handling.

For building or redesigning UI, use the **`compose-ui`** skill. For auditing or polishing a screen,
delegate to the **`compose-design-reviewer`** subagent. (Note: `mobile-game-ui-designer` is the
complementary *generative* design agent — visual concepts, palettes, animation specs — whereas
`compose-design-reviewer` is a read-only Compose code auditor.)

## Security notes

- HTTP logging is `BODY` level in debug only; `NONE` in release — enforced in `NetworkModule.kt`.
- `network_security_config.xml` blocks cleartext traffic.
- Room DB and DataStore are excluded from Google Drive auto-backup via `backup_rules.xml` / `data_extraction_rules.xml`.
- Scryfall search queries are sanitised with an allowlist before being appended to URLs.
- YouTube API key is injected via an OkHttp interceptor — not visible in Retrofit signatures or Logcat.

### Pre-push security gate (MANDATORY)

**Before creating any PR or pushing any branch to the remote, the `android-security-auditor` agent MUST run a secret-scanning pass on the staged diff.** This is not optional even for "docs-only" changes.

Minimum checks the security agent must perform:
1. Scan every new/modified file for API keys, tokens, private keys, passwords, and connection strings (patterns: `AIzaSy`, `-----BEGIN`, `secret`, `password`, `api_key`, `Authorization`, `Bearer`, Supabase URLs + service-role keys).
2. Confirm `google-services.json` is listed in `.gitignore` and is NOT staged.
3. Confirm no `.md`, `.txt`, or `.json` planning file contains literal key values — referencing a key by name ("rotate key X") is fine; embedding the key value is not.
4. Confirm no new file bypasses `.gitignore` via `git add -f` or `--force`.

If any finding is critical, the agent must block the push and report before proceeding.

### AI planning documents — commit policy

Agents create temporary planning and task-execution `.md` files during complex multi-step tasks (e.g. `docs/PUSH_NOTIFICATIONS_TODO.md`, `PLAN.md`). These files:

- **MUST be added to `.gitignore` before the first `git add`** — patterns are already defined in `.gitignore` (Section 8). If a new pattern is needed, add it to `.gitignore` first.
- **MUST be deleted when the task is finished.** Do not leave orphaned planning docs in the repo.
- **MUST NEVER contain literal secret values** — no API keys, tokens, private keys, or connection strings. Reference them by name only (e.g. "set `FCM_SERVICE_ACCOUNT` to the downloaded JSON" — never paste the JSON).
- Are never a substitute for proper design documentation. Real decisions → `docs/adr/ADR-NNN-*.md` (committed). Task scratch pads → ephemeral, gitignored, deleted.

**Why:** A planning doc containing literal Firebase API keys was committed to git history in 2026-06 (incident `a85a4dd`). The keys were old/rotated so impact was low, but the entire repository history had to be rewritten with `git filter-branch --all` and force-pushed to every remote branch. This is a high-cost, high-risk operation that is entirely avoidable.

### Supabase security invariants (audited 2026-06-02)

These rules apply to every new migration, RPC, trigger, or view written against the Supabase backend:

1. **`SET search_path = public` on every SECURITY DEFINER function / trigger.** Without it, an attacker who creates a shadow function in another schema can hijack execution. Pattern:
   ```sql
   CREATE OR REPLACE FUNCTION public.my_fn()
   RETURNS void LANGUAGE plpgsql
   SECURITY DEFINER SET search_path = public AS $$ ... $$;
   ```

2. **Views must use `security_invoker = true`.** `SECURITY DEFINER` views run as the owner and bypass RLS — users can see rows they have no policy for. Always add `WITH (security_invoker = true)` when creating views.

3. **`(select auth.uid())` in RLS policies, not bare `auth.uid()`.** The bare call re-evaluates per row (O(n) overhead). The subquery form is evaluated once per query by the planner. Same for `auth.jwt()` and `auth.role()`.

4. **Never add both an `ALL` policy and a separate `SELECT` policy on the same table.** PostgreSQL evaluates every permissive policy — two policies for SELECT doubles evaluation. Split `ALL` into explicit `INSERT`/`UPDATE`/`DELETE` when access rules differ; keep one `SELECT` policy.

5. **Materialized views do not inherit RLS.** Always `REVOKE SELECT FROM anon` (and `authenticated` if needed) on any MV that contains multi-user data.

6. **Index every FK column.** After every `REFERENCES` constraint, add `CREATE INDEX IF NOT EXISTS idx_<table>_<col> ON <table>(<col>)` in the same migration.

7. **`DROP INDEX` fails if the index backs a CONSTRAINT.** Use `ALTER TABLE DROP CONSTRAINT` instead; the index disappears automatically.

8. **Every new RPC must `REVOKE EXECUTE FROM PUBLIC` and explicitly `GRANT` to the intended roles.** `REVOKE FROM anon` alone is insufficient when EXECUTE was granted to `PUBLIC` — `anon` inherits from `PUBLIC`. Always use this pattern immediately after `CREATE FUNCTION`:
   ```sql
   REVOKE EXECUTE ON FUNCTION public.my_fn(...) FROM PUBLIC;
   GRANT EXECUTE ON FUNCTION public.my_fn(...) TO authenticated, service_role;
   ```
   For `service_role`-only internal functions (e.g. cron/trigger helpers), omit the `authenticated` grant entirely. Run `get_advisors` after adding RPCs to catch any that remain exposed.

9. **Prefer `SECURITY INVOKER` over `SECURITY DEFINER`.** Use `SECURITY INVOKER` (the default) whenever the function only reads/writes data the caller already has access to via RLS. Only use `SECURITY DEFINER` when one of these genuinely applies:
   - The function is called **inside an RLS policy expression** (`are_mutual_friends`, `is_profile_complete`, `is_session_participant`, `is_tournament_participant`) — these must run as `postgres`, not as the row owner
   - The function writes to a table whose **UPDATE/DELETE policy is intentionally `false`** (trade workflow: `trade_proposals`, `trade_items`, `trade_card_locks`; session state: `session_state`, `session_player_state`)
   - The function performs **bootstrap writes** where the caller is not yet a participant (e.g. `create_online_session`, `join_session` write to `session_participants` before the user exists there)
   - The function **updates rows owned by other users** as part of session cleanup (e.g. `abandon_my_active_session` sets all participants to LEFT)
   - The function reads a **materialized view** (`trade_suggestions_mv`) — PostgreSQL 17 does not support RLS on MVs

   Functions converted to SECURITY INVOKER (2026-06-02): `get_collection_changes_since`, `get_deck_changes_since`, `get_deck_cards_for_deck`, `get_deck_with_cards`, `get_my_active_sessions`, `get_profile_by_user_id`, `upsert_collection_stats`, `delete_current_user`, `delete_device_token`, `delete_my_device_tokens`, `complete_user_profile`, `update_user_avatar`, `update_user_nickname`, `set_participant_ready`, `upsert_device_token`, `get_my_referral_code`, `upsert_deck_cards`, `batch_upsert_collection`, `batch_upsert_decks`, `get_session_snapshot`, `get_friend_collection`

   Trigger/cron-only functions (no client EXECUTE, `service_role` only): `cancel_trades_on_friendship_end`, `handle_new_user`, `update_friend_match_history`, `assign_referral_code`, `expire_stale_sessions`, `refresh_trade_suggestions`, `rls_auto_enable`, `delete_user_app_data`

9. **`enqueue_notification` is permanently REVOKE-protected.** It accepts an arbitrary `recipient_id` — a client grant would allow spamming any user. Do not grant EXECUTE to `public`, `anon`, or `authenticated`.
