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

**MVVM + Clean Architecture.** The tree below is the **current/legacy** single-module (`:app`) layout.
**New code is KMP-first** (Koin DI, `commonMain` by default) per the directive above and the "Kotlin
Multiplatform migration" section below — do not add new Hilt/single-module code; follow the KMP source-set
rules. The legacy structure stays as-is until each feature is migrated.

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

### Database (Room v42)
- DB file `mtg_collection.db`. `UserCardEntity` FK to `CardEntity` is `ON DELETE RESTRICT` (v38).
- Migration chain 1→42, gaps at 7–10 and 15–17 covered by `fallbackToDestructiveMigration()` (dev-only;
  not safe for production data). v39 = 6 gamification tables; v40 = additive `legality_legacy`/
  `legality_vintage`/`legality_pauper` on `cards` (Deck Doctor Phase 4 D2); v41 = Community Decks
  attribution columns on `decks` + `community_deck_cache` table; **v42 = additive `produced_mana`
  (compact WUBRG string, not JSON) on `cards`** (Deck Doctor Community/Archetype plan Phase 0.3, D14).
  Every migration since v39 follows the same pattern: a top-level `val MIGRATION_x_y` in its own file,
  `ADD COLUMN … TEXT NOT NULL DEFAULT '…'` guarded by a `columnExists` check, CardDao upsert untouched
  → no CASCADE risk. → memory: `project_card_model_produced_mana`
- Schema: `app/schemas/com.mmg.manahub.core.data.local.MtgDatabase/` (latest version json gitignored —
  regenerate locally).

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

### Add Card (`feature/addcard/`)
Search-first entry point (redesigned 2026-07-13). Idle state (no query, no filters) shows a
`LazyVerticalGrid` discovery surface of shuffled cards from one Scryfall set at a time
(`GetSpotlightFeedUseCase`, paginates to the next set on scroll) — mirrors Home's Discover Cards
widget, not a text list. The camera scanner is a `FloatingActionButton` on the `Scaffold`, not an
inline list item or a `SearchSurface` trailing icon. The search field's trailing icon (query empty)
is the flag emoji for the active search language (`CardConstants.getFlag`/`languages`) and opens a
`ModalBottomSheet` (`LanguageSelectorSheet`) — this replaced the old `ManaHubSelector` dropdown row.
"Recent searches" was removed end-to-end (not just hidden): `GetRecentSearchesUseCase`/
`SaveRecentSearchUseCase`/`ClearRecentSearchesUseCase` and `RecentSearchRepository` are gone: only
delete a use case's Koin binding — always grep the feature's OWN `di/` module too (`AddCardKoinModule.kt`
had its own reference the main task list didn't mention). Both the spotlight grid tiles and the
results-list `SearchResultItem` thumbnails participate in the shared-element transition into
`CardDetailScreen` via the shared key `"card-image-${card.scryfallId}"` — any new card-image surface
in this screen must reuse that exact key format to stay connected to the transition.
→ memory: `project_addcard_redesign_2026-07-13`

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
- **SINGLE finish-and-advance write path (audit C1/C2/C3).** Recording a result, advancing the round,
  and finishing the tournament are ONE atomic path: `RecordMatchResultUseCase` → `repository.finishMatch`
  → `TournamentDao.finishMatchAndAdvanceAtomically(@Transaction)`. BOTH the game-played flow
  (`GameViewModel.recordTournamentResultIfNeeded`) and the manual dialog (`TournamentViewModel`) route
  through it — the VM must NEVER generate rounds or call `finishTournament` itself (it only recomputes
  standings + reflects the returned `MatchResultOutcome`). The finish is **first-writer-wins**:
  `finishMatchGuarded` is `UPDATE … WHERE id=:matchId AND status != 'FINISHED'` returning rowcount; 0 rows
  → `NO_OP`, no advancement (a repeated/concurrent finish never double-advances or double-grants XP).
  Round advancement is **round-aware** (H2): `GenerateNextRoundUseCase.plan` (pure, DB-free) advances the
  LOWEST fully-finished round with no successor, never `maxOf { round }`; next-round `scheduledOrder` is
  offset past every existing match (M1, globally monotonic). M4: SINGLE_ELIM draws soft-lock the bracket,
  so the Draw button is hidden when `structure == "SINGLE_ELIM"` (`RecordResultDialog.allowDraw`).
- **`TournamentCompleted` XP is tournament-scoped + device-scoped.** Emitted only on the transition to
  FINISHED (inside the atomic path, post-commit), key `tournament:{id}` with `isDeviceScoped = true`
  (→ `dev:{deviceId}:tournament:{id}`) so two guest devices can't collide on the server PK and a re-finish
  is ledger-deduped. `finishTournament` no-ops when already FINISHED (no double-emit). `isLocalWinner` is
  hard-coded false (no per-seat local flag on tournaments yet) — base completion XP grants, won-bonus does
  not. The hand-rolled match encoding is centralized in `TournamentIdCodec` (M2) — never re-inline
  `json.trim('[',']').split(",")`.
- Phase 2 pending: see `docs/plan-torneos.md`.
- → memory: `feedback_tournament_bugs_2026-05-24`, `feedback_tournament_phase1_2026-06-02`,
  `feedback_tournament_single_write_path_2026-06-16`

### Deck Playtest (`feature/playtest/`, Phase 1 + Phase 2 battlefield complete)
**Hidden for release (2026-07-14) via `DeckFeatureFlags.PLAYTEST_ENABLED = false`** — code intact,
all entry points gated (flip back to `true` to re-enable). Room v35→v36 added `playtest_sessions`,
`playtest_card_stats`, `playtest_survey_answers`. Must-know:
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
**Hidden for release (2026-07-14) via new `feature/online/presentation/OnlineFeatureFlags
.ONLINE_SESSIONS_ENABLED = false`** — code intact, only the online-specific UI (GameSetup's "Play
with friends", TournamentsSheet's online rows, `LobbyHost`/`LobbyJoin`/join-deep-link redirects) is
gated; local/offline same-device play and local tournaments are untouched. Flip back to `true` to
re-enable. **HTTP polling (3 s) is the primary mechanism; Supabase Realtime CDC is an optional fast-path** — never
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
  everything resolves on-device. Cross-product uuids resolve via MTGJSON `sourceSetCodes`; a source set the
  tier list RATES joins the pool as top-level `extraPoolSets: [...]` in booster.json (SOS → `["soa"]`
  Mystical Archive — the app widens the pool query to `(set:x or set:y) lang:en`, entries sanitised against
  `^[a-z0-9]{2,6}$` at BOTH parse and query build), while unrated external sheets (`specialGuest`) are
  dropped and their slot re-homed to `common`.
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

### Card tagging engine (core/tagging)
- **Analysis is English-only**: `StrategyAnalyzer` scans ONLY `card.oracleText` (no `printedText`/
  `lang`); non-English printings are resolved to English upstream first. Labels stay a
  `Map<String,String>` but only "en" is populated.
- Detection uses `DetectionRule(allOf/anyOf/noneOf + typeLineAnyOf/typeLineNoneOf, confidence?)` —
  use `allOf` for compound phrases, NEVER loosely OR'd word fragments. Reminder text (parens) is
  stripped and the card's own name is replaced with `~` before matching.
- **Tag keys are persisted user data — NEVER rename an existing key.** User overrides use the
  rule-line syntax: terms joined by ` + ` are ANDed, `!term` excludes.
- **D12 — system dictionary entries are read-only for users.** The editor shows system rows
  view-only (label + rules, no edit/delete); users can only create/edit/delete their OWN tags, under
  the `custom_` key prefix (`TagDictionaryRepository.CUSTOM_KEY_PREFIX`) — `upsert` rejects any other
  key. `StrategyAnalyzer.analyze()` returns empty on a blank `oracleText` BEFORE evaluating any rule,
  including `typeLineAnyOf`-only ones — a type-line-only detector still needs a non-blank oracle text
  fixture to be exercised in tests.
- → memory: `project_tagging_engine_v2`, `feedback_tag_dictionary_archetype_audit`

### Deck Studio (`feature/decks/presentation/DeckStudio*`)
**Suggestions tab + seed-build hidden for release (2026-07-14)**: `DeckFeatureFlags
.DECK_STUDIO_SUGGESTIONS_TAB_ENABLED` and `.DECK_STUDIO_BUILD_FROM_SEED_ENABLED` flipped back to
`false` (had been `true` since 2026-07-12 for the Community/Archetype plan launch) — code intact,
flip back to `true` to re-enable. Manual editing, Import, and Discoveries are unaffected.
**The SINGLE deck create + edit surface.** Both new decks AND existing decks route here: DeckList FAB +
empty-state, Collection/Stats/Home/CardDetail deck-open, and Home → "Build deck" all navigate to
`Screen.DeckStudio.createRoute(deckId?)` (null ⇒ fresh draft). The old `CreateDeckBottomSheet` + the
`DeckViewModel.createDeck`/`showCreateDialog`/`createdDeckId` state are GONE — DeckStudio creates its own
draft. Fuses manual editing + inline Deck Doctor suggestions + seed-build + Discoveries on ONE **live deck**.
- **Format + Import live INSIDE Studio:** `DeckFormatChipRow` (empty-state + `EditDeckSheet`) → `changeFormat`
  (writes through repo + `invalidateSuggestions()`). Import via `ImportDeckUseCase` (`domain/usecase/`,
  shared extraction — writes into the live draft, renames from a parsed header; `DeckViewModel.importDeck`
  still backs the legacy DeckList import sheet). `importDeck` sets `isImporting`, which **blocks
  `onExitRequested`** (no discard/keep mid-write).
- **Discard-if-empty is gated on `createdFreshDraft`** (set true ONLY on the no-deckId create path). An
  EXISTING deck opened in Studio is NEVER auto-deleted — even if empty + default-named. (Critical data-loss
  guard added when existing decks started routing through Studio; without it, opening a real empty deck and
  backing out deleted it.) Delete completes BEFORE nav.
- Free-text budget: **never build `BudgetConstraints` from raw `TextField` text** — raw String + last-valid +
  error flag, parse-guard in the VM. The Deck Doctor incremental `AnalysisCache`/`GapSignature`/
  `loadAnalysis`/`recomputeIncremental`/`recomputeAdds` machinery lives in `DeckDoctorOrchestrator`
  (`shared/core-domain/.../feature/decks/domain/orchestrator/`, commonMain) — `DeckStudioViewModel`
  constructs one instance per session and delegates to it; VM-internal LOGIC gates must read
  `deckDoctorOrchestrator.state.value` directly (never the merged `_uiState`) to stay race-free.
- Migrated from the legacy editor: inline `CardDetailSheet` (deck-card taps; search-result taps still nav to
  CardDetail), basic-land suggestions (`landDeltas`/`applyLandSuggestions`), stateless `WarningOverlay`
  (over-limit/color-identity/non-legendary-commander + acknowledge), deck game-stats card, playtest button.
  `CardDetailSheet`/`WarningOverlay`/`DeckFormatChipRow` are reusable composables in `presentation/components/`.
- `DeckMagicDetailScreen` (in `DeckBuilderScreen.kt`) + `DeckBuilderViewModel` + `Screen.DeckDetail` route are
  now an UNUSED fallback (kept compiling until parity confirmed in real use — then delete).
  **`DeckImprovementScreen`/`DeckImprovementViewModel`/`Screen.DeckImprovement` were RETIRED (D10)** — the
  Studio Suggestions tab is the sole Deck Doctor UI surface; `DeckMagicDetailScreen.onImproveDeck`
  re-points to `Screen.DeckStudio`.
- **Motor B (community suggestions, Phase 4) + community seed-build (Phase 5)**: the Suggestions tab
  gains a flag-gated (`communityEngineEnabledFlow`, D4) "Popular in similar decks" section +
  "Decks like yours" carousel, entirely additive to Motor A; the seed sheet gains a flag-gated
  "Use community data" toggle that re-orders `BuildDeckFromSeedsUseCase`'s fill priority. Flag off =
  zero community UI, byte-identical to pre-Phase-4.
- → memory: `project_deck_studio`, `feedback_budget_input_free_text_pattern`,
  `project_deck_doctor_orchestrator_extraction`, `project_deck_studio_improvement_retirement`,
  `project_motor_b_community_suggestions`, `project_community_hub_seedbuild_trending`

### Incomplete / quirks
- **SetPickerViewModel**: `clearFilters()` calls `applyFilters()` to respect `restrictedSets` — do not
  assign `filteredSets = allSets`.

### Deck Doctor
Original 8 phases complete; a separate **engine-quality plan** is complete (see below), and the
**archetype-aware Community/Archetype plan** (`docs/claude-code-prompt-deck-doctor-community.md`)
Phases 1-3 are complete: Phase 1 added a new archetype/theme skeleton layer
(`ArchetypeDefinition`/`ThemeDefinition`/`ArchetypeSkeletonResolver`/`ArchetypeEvaluator`/
`ArchetypeRoleClassifier`/`InferDeckArchetypeUseCase`) sitting ADDITIVELY on top of the engine
below, in the SAME `shared/core-domain` commonMain package as the rest of it (not `:app`'s
`feature/decks/domain/engine` — that location is stale, the whole engine was promoted to
`shared/core-domain` during the KMP migration). `EvaluateDeckUseCase` resolves the deck's archetype
(`Deck.archetypeOverride`/`themesOverride` pin, else classifier inference) and only computes
archetype-aware warnings when non-GENERIC/themed — a GENERIC deck's evaluation is byte-identical to
before. Deck Studio's Suggestions header carries a "Deck plan" chip/sheet (still behind the same
`DECK_STUDIO_SUGGESTIONS_TAB_ENABLED` flag). Phase 2 added **Motor A**
(`SuggestAddsFromCollectionUseCase`, same package): the offline, always-on, collection-only add
source, now `DeckDoctorOrchestrator`'s SOLE adds source (it REPLACED, not supplemented,
`SuggestAddsWithBudgetUseCase`). → memory: `project_archetype_engine`, `project_deck_doctor_phase2_motor_a`.
Key invariants:
- `DeckFormat.valueOf()` must NOT be used — use `DeckFormat.entries.firstOrNull { ... } ?: STANDARD`.
- `generateFromSeeds()` captures inputs atomically inside `_uiState.update { }` (double-tap + stale-snapshot guards).
- `DeckDoctorOrchestrator.loadAnalysis()` cancels its own `analysisJob` before relaunching (the
  incremental-analysis machinery lives there now, not inline in the ViewModel — see the Deck Studio
  section above).
- `CandidatePoolGenerator`/`BudgetOptimizer`/`SuggestAddsWithBudgetUseCase` (D5) are DORMANT — still
  Koin-registered but no longer referenced by any live class since Motor A replaced them in
  `DeckDoctorOrchestrator` (Phase 2); do not delete. `Card.colors`/`colorIdentity`/`producedMana`
  (D14) are persisted as compact WUBRG-subset strings (not JSON); Motor A's pip-intensity multiplier
  and unknown-color-identity fail-closed filter (Commander only) consume them. → memory:
  `project_dormant_budget_pool`, `project_card_model_produced_mana`, `project_deck_doctor_phase2_motor_a`
- **Phase 3** added a new Cloudflare Worker `cloudflare/manahub-community/` (TypeScript, Wrangler,
  vitest+miniflare) aggregating community deck data — EDHREC for Commander, Archidekt for 60-card
  (D16) — behind KV (7-day snapshot TTL) + D1 (anonymous weekly trending counters, no PII), plus a
  full `commonMain` client stack (`CommunityAggregateApi`/`CommunityAggregateRepositoryImpl`/
  `CommunityAggregate` domain model). **Not deployed** — `wrangler.toml` KV/D1 bindings are
  placeholder ids pending human-authorized provisioning. Consumed by nothing yet (Motor B/UI is
  Phase 4); androidMain Room cache + `communityEngineEnabledFlow` DataStore flag (D4) + Koin wiring
  are the remaining plumbing. → memory: `project_community_aggregate_worker`
- `CandidatePoolGenerator.legalityFragment()` returns `String?`; `DRAFT → null` (no legality restriction).
- `BudgetConstraints` has an `init` block validating finite/positive values.
- `SeedStrategy.TOKENS` test requires all 3 primary tags (TOKENS+AGGRO+TRIBAL) to beat AGGRO's tie.
- `stubRankAdds()` in tests must `candidates.take(arg<Int>(4))` to respect the `limit` parameter.
- **Engine-quality plan** (`docs/plans/deck-doctor-engine-quality-plan.md`): Phase 0 (golden + corpus
  harness), **Phase 1 (RoleClassifier v2)**, **Phase 2 (tribal granularity)**, **Phase 3 (scoring
  math)**, **Phase 4 (format correctness + construction validation)**, **Phase 5 (mana-base analysis)**,
  **Phase 6 (external pool v2)**, **Phase 7 (inference + incremental + reason pipeline)** and **Phase 8
  (layering + weight tuning)** are ALL done — the engine-quality plan is COMPLETE. Since Phase 1,
  `RoleClassifier.classify()` returns `Map<DeckRole, Float>` (confidence); use `classifyRoles()` for the
  compat `Set`. The oracle `ROLE_PATTERNS` table is a **fallback safety net only** — enrich the
  TagDictionary for durable role coverage. Never `==`-compare a `classify()` result to a `Set` (it's a Map
  — use `.keys`). Rituals stay out of RAMP. `DeckProfile.roleCounts` is Float (`quantity × confidence`).
- **Phase 3 scoring math:** `synergyScore` denominator counts IDENTITY-category tags ONLY; seed influence is
  a post-normalization fingerprint floor (≥`SEED_FLOOR` 0.6), not a pre-norm `+3f`; `curveScore` scores
  against `DeckSkeleton.targetCurve` per format (a bucket the curve does not want — `target == 0` — scores 0
  even when empty; a wanted-but-full bucket floors at `FULL_BUCKET_FLOOR` 0.1); `healthScore` penalises
  over-coverage past `RoleSlot.max` (healthy band is `[ideal, max]`, symmetric `roleFitRatio`) with a
  continuous `curveBandScore` off the per-format `DeckSkeleton.cmcBand`. **`redundancyScore` triggers only
  when `current > max`, never at `ideal`** — penalising at ideal punishes correctly-stocked staples and is
  fragile under the weighted Float `roleCounts` (oracle cross-credits nudge counts fractionally past ideal).
- **Phase 2 tribal granularity (B1):** tribe identity is PER-TRIBE and 100% RUNTIME-derived — `TribeDeriver`
  (in `RoleClassifier.kt`) builds `tribe:<subtype>` keys from the type-line subtypes after the dash (Creature/
  Tribal/Kindred only) ∪ tribes named as oracle PAYOFFS ("Elves you control", "other Elves", lords, "choose a
  creature type"). **These `tribe:` keys are NEVER persisted** (not written to `card.tags`/`userTags`/Room/the
  tagging store; the `tribe:` prefix guarantees no collision) — the commented-out per-tribe `CardTag`s stay
  commented out, no DB change. `DeckScorer.profile` adds a `tribe:<x>` fingerprint key when it clears
  `TRIBE_ABS_THRESHOLD` 8 copies **OR** `TRIBE_SHARE_THRESHOLD` 0.15 of creature copies; **when ANY tribe
  clears, the generic `CardTag.TRIBAL.key` ("tribal") is dropped from the fingerprint** (and a generic `tribal`
  SEED is not floored) so an off-tribe card carrying only the coarse `tribal` tag (a Dragon in an Elf deck) no
  longer matches. `synergyScore` injects the candidate's own `TribeDeriver.tribeKeys` as `TRIBAL`-category
  signal into BOTH numerator and denominator (consistent with the B2 identity-only denominator); `synergyDensity`
  alignment checks derived tribe keys too. Type-line subtype parsing is structural/language-neutral — it does
  NOT violate the English-only ORACLE rule.
- **Phase 7 inference + flow (B4/E4/E5/E6/E7):** the Deck Doctor seeds the profile — `EvaluateDeckUseCase`
  takes a `seedTags` param fed by `InferDeckIdentityUseCase` over the commander + the deck's top-8
  highest-identity-tag mainboard cards (the fingerprint is no longer self-referential; do NOT reintroduce
  `seedTags = emptyList()`). **Reasons are structured everywhere**: `MagicSuggestion.reasons` /
  `CardSuggestion.reasons` are `List<ScoreReason>` (NOT `List<String>`); engines emit `fit.reasons` and the
  UI renders `ScoreReason.label()` — never `fit.roles.map { it.name }` (raw-enum-name leak). Unresolvable
  mainboard slots surface `DeckWarning.UnresolvedCards(n)` from the **ViewModel** (the engine never sees
  unresolved slots and stays string-free); add its `label()`+`key` when touching `DeckWarning`.
  `DeckImprovementViewModel` does **incremental re-analysis**: it holds an in-memory `AnalysisCache`
  (workingMainboard / seedTags / resolvedById / gapSignature / externalPool); `onCut`/`onAdd` mutate the
  list in memory and `recomputeIncremental()` rebuilds profile/eval/cuts purely, re-fetching the external
  Scryfall pool ONLY when the `GapSignature` (queryable gap roles ∩ `queryFragment()` + colorIdentity +
  format) changes — else it reuses the cached pool via `SuggestAddsWithBudgetUseCase(externalCardsOverride=…)`
  / `BudgetSelection.externalPool`. A 99-card cut→add round-trip with an unchanged gap set must issue ZERO
  Scryfall calls. Full `loadAnalysis()` stays the fallback (missing cache / unknown re-add source).
- **Phase 8 layering (F1) — the engine now lives in `feature/decks/domain/engine/`, NOT
  `presentation/engine/`.** `DeckScorer`, `RoleClassifier`/`TribeDeriver`, `ManaBaseAnalyzer`,
  `DeckScoreModel` (DeckProfile/DeckRole/DeckSkeleton/`ScoreWeights`/`ScoreReason`/`ScoreFit`/`DeckWarning`/
  `Magic*`), `DeckImportExportHelper`, `DeckMagicEngine`, and the pure model types (`GameFormat`,
  `ManaColor`, `SeedStrategy`, `DeckEntry`, `CardSuggestion`, `PathDecision` in `DeckEngineModels.kt`) are
  in **`...decks.domain.engine`**. (The old `presentation/engine/` builder — `DeckBuilderEngine`
  + `DeckBuilderState.kt` — was **retired** with the legacy Deck Magic creator when the unified
  **Deck Studio** screen landed; see `→ memory: project_deck_studio_*`.) **Layering rule:
  `feature/decks/domain/**` must never import `...presentation...`**
  (domain→presentation is the bug Phase 8 fixed). **Phase 8 weight tuning (F2):** `ScoreWeights` is
  debug-tunable via core `ScoreWeightOverrides` (7 nullable Floats + `NONE`) persisted in
  `UserPreferencesDataStore` (primitive floats — core never imports the feature-layer `ScoreWeights`); the
  feature-layer `ScoreWeightOverrides.toScoreWeights()` maps null→default so `NONE` == `ScoreWeights()`
  (zero behavior change at defaults). `DeckImprovementViewModel` reads it and threads `weights` into
  `evaluateDeck`/`suggestAddsWithBudget`/`suggestCuts` (`EvaluateDeckUseCase` gained an optional `weights`
  param forwarded to `DeckScorer.evaluate`). The setter is the debug entry point — there is NO settings UI.
- **Phase 5 mana-base analysis (C3):** pure injectable `ManaBaseAnalyzer` (engine pkg, sibling of
  `RoleClassifier`; `DeckScorer`'s 3rd ctor param, defaulted to `ManaBaseAnalyzer()` so old 2-arg
  construction stays green). NO Room/schema/CardTag change — everything is derived from the resolved
  mainboard, no simulation/network. Pip/devotion from `Card.manaCost` (TRUE hybrid `{W/U}` counts both
  halves; **Phyrexian `{W/P}` counts as ZERO coloured demand** — payable with life, no source pressure);
  land production via priority chain (basic subtypes → oracle `Add {X}` clause → rainbow "any color" →
  fallback to the land's own `colorIdentity`) — it UNDER-counts fetch/conditional lands on purpose so the
  fixing check never HIDES a shortage. **Oracle production parsing must stay conservative (never invent an
  off-colour source):** the Add-clause regex is `\bAdd\b…` (word-anchored — `[Aa]dd` matched inside
  "Additional"), and "any color" → rainbow is recognised ONLY INSIDE an `Add` clause (matching it across
  the whole oracle invented rainbow sources from flavour like "protection from any color"). Static
  **Karsten** source table (`KARSTEN_60`/`KARSTEN_99`, 99-row at ≥33 lands) keyed by **per-colour MAX
  SINGLE-CARD pip intensity** (1/2/3+ — `maxSinglePipIntensity()`), NOT the whole-deck pip sum (the sum
  bucketed every 2-colour deck to triple-pip → impossible source need → false shortage on healthy decks)
  → `DeckWarning.ColorSourceShortage` (have<need) / `UnfixedSplash` (have==0 or <50% of need). **`dynamicLandIdeal` can only RELAX the land count** (clamped `[LAND.min,
  skeletonBase]` — never recommends MORE lands than the flat ideal); it feeds the *displayed* target of
  `TooFewLands`/`TooManyLands` while the band gate stays skeleton min/max. `evaluate(…, fullMainboard?)`
  appends the shortages only when `fullMainboard != null` (same gate as construction warnings). New
  warnings need `DeckDoctorStrings.label()` (uses `ManaColor.displayName`) + `.key` + a `strings.xml` row.
- **Phase 6 external pool v2 (E2/E3/E8):** `CandidatePoolGenerator` role queries lead with curated oracle
  tags — `DeckRole.queryFragment()` returns `otag:` (board-wipe/removal/card-advantage/ramp/counterspell/
  tutor); the legacy oracle substrings are DEMOTED to `DeckRole.fallbackQueryFragment()` and issued ONLY when
  the primary `otag:` query ERRORS or returns EMPTY (otags are not in Scryfall's formal grammar — be
  defensive; never delete the substring net). PAYOFF/SYNERGY/THREAT have no role query, so the pool adds up to
  two profile-derived queries: the top non-tribe STRATEGY fingerprint key → `otag:<x>` via the CONSTANT
  `STRATEGY_OTAGS` allowlist (no allowlist hit ⇒ no query — never guess an otag from an arbitrary key), and the
  dominant Phase-2 `tribe:<x>` key → `t:<tribe>` with the token sanitised to letters-only. `MAX_QUERIES` is 8.
  **All query fragments stay constants/allowlist — no user free text, the tribe token is `[a-z]`-sanitised.**
  E8: `AddSuggestion.priceUnknown` (priceEur == null); `BudgetOptimizer` EXCLUDES a non-free unknown-price card
  under an ACTIVE `maxTotalEur` cap (it cannot be costed — never silently 0€), keeps owned/free unknown-price
  ones, and is inert with no cap. `runningPaid`/`cardsToBuy` are charged only on a known `cost > 0`. When the
  otag-vs-substring stub returns empty in a test, the fallback fires → a single role issues 2 queries.
- **Phase 4 format correctness + construction validation (D1-D4/C5):** `DeckFormat` now covers PIONEER/
  MODERN/LEGACY/VINTAGE/PAUPER/CASUAL (+`isSixtyCardConstructed`); `GameFormat→DeckFormat` is **1:1** via
  `GameFormat.toEngineDeckFormat()` (engine pkg) — never collapse non-Commander to STANDARD again.
  `DeckScorer.isLegal` reads the per-format legality field (Card now persists `legalityLegacy/Vintage/Pauper`,
  DB v40); CASUAL & DRAFT are permissive. `DeckSkeletons.forFormat` uses the shared `sixtyCardSkeleton` with
  ETERNAL/PAUPER curves. **C5 construction validation gates on a NEW optional `evaluate(profile, nonLand,
  fullMainboard? = null)` param** — null SKIPS the checks (keeps every old `evaluate(profile, nonLand)` call
  +DeckScorerTest green); `EvaluateDeckUseCase` passes the full mainboard. New `DeckWarning`s: `DeckTooSmall`,
  `TooManyCopies`, `SingletonViolation`, `OffColorIdentity` — quantity-aware, copy limit is **by card name**,
  basics exempt, CASUAL/DRAFT skip min-size. D3 multi-copy: `AddSuggestion.suggestedCopies` (≤`maxCopies −
  owned-by-name`; Commander/Draft = 1) and `BudgetOptimizer` charges `copies × price`; pass
  `mainboardCopiesByName` to `SuggestAddsWithBudgetUseCase`. D4: `CandidatePoolGenerator` edhrec-pre-sorts
  ONLY for Commander; constructed pools rank by fit. When touching `DeckWarning`, add the new cases'
  `label()`+`key` in `DeckDoctorStrings` + `strings.xml`.
- **Re-run the golden ORDERING invariants after ANY scoring-component change** — a re-tune of one component
  (curve) can unmask coupling in another (redundancy) by removing slack.
- → memory: `project_deck_doctor_phase6`, `project_deck_doctor_phase6_v2`, `project_deck_doctor_general`,
  `feedback_deck_doctor_audit_2026-06-08`,
  `project_deck_doctor_phase0_harness`, `project_deck_doctor_phase1_classifier`,
  `feedback_deck_doctor_phase1_signature_ripple`, `project_deck_doctor_phase3_scoring`,
  `feedback_deck_doctor_phase3_curve_redundancy_coupling`, `project_deck_doctor_phase2_tribal`,
  `project_deck_doctor_phase7_inference_flow`, `project_deck_doctor_phase4`, `project_deck_doctor_phase5`,
  `project_deck_doctor_phase8`

**Deck Doctor Community/Archetype plan** (`docs/claude-code-prompt-deck-doctor-community.md`, gitignored/
pending deletion once this branch ships — durable record lives in memory + `docs/adr/ADR-004-*`): archetype-
aware skeletons (Phase 1) + Motor A collection suggestions (Phase 2) + the `manahub-community` Cloudflare
Worker (Phase 3, not deployed) + Motor B community suggestions (Phase 4) + Community Hub Discover/seed-build
priority/Home trending widget (Phase 5) + unified import pipeline (Phase 6) are ALL COMPLETE, entirely
flag-gated behind `communityEngineEnabledFlow` (D4, default OFF). → memory: `project_archetype_engine`,
`project_deck_doctor_phase2_motor_a`, `project_community_aggregate_worker`, `project_motor_b_community_suggestions`,
`project_community_hub_seedbuild_trending`, `project_import_unification`

### Home dashboard (`feature/home/`)
Free-first, account-enhanced start screen. Fully implemented (2026-06-08). Must-know:
- **Start destination is `Screen.Home`** (not `Screen.Collection`). BottomBar is 3-slot: [Home] [⚔ FAB] [Library].
- `HomeViewModel` uses `combine(8 flows).stateIn(WhileSubscribed(5_000))` — no new DB tables. `avatarUrlFlow` MUST be subscribed/mocked in tests (blocks `accountFlow` combine if missing).
- Quick Start: 4 shortcuts persisted in DataStore via `UserPreferencesDataStore.observeQuickStartActions()`; partial-restore pads with defaults rather than discarding valid entries.
- Account nudge: 5-priority system (ACTION_REQUIRED > COLLECTION_MILESTONE ≥10 > DECK_MILESTONE ≥2 > GAME_MILESTONE ≥3 > SYNC_PENDING); 48h cooldown; ACTION_REQUIRED bypasses cooldown.
- `onBackHome`: `popUpTo(0) { inclusive = true }` (not `popUpTo(Screen.Collection.route)`) to avoid back-stack corruption on fresh install.
- → memory: `project_home_dashboard_redesign`, `feedback_home_stateIn_test_pattern`

### Home widget board (`feature/home/`, overhauled 2026-07-13 — no-stub rule now in force)
The dashboard is a **fully customizable widget board** (`LazyVerticalGrid` of 2 cols). 17 widget
types in `HomeWidgetType` (each carries `persistedId`, `defaultTitleRes`, `supportedSizes`,
`category`, `audience`, `isAlwaysPresent`). Layout = ordered `List<WidgetInstance>(type,size)`.
**THE NO-STUB RULE (Home invariant, added 2026-07-13): a widget/slide may only ship if its data
path is real end-to-end (upstream source → repo flow → ViewModel → UI); anything that can't be
wired must be deleted (composable + model + copy + DI binding), never left as a placeholder.**
Every widget/slide is real as of the 2026-07-13 overhaul except `RULES_TIP` (intentionally static
content). Must-know:
- **Layout persists in DataStore only** (`home_widget_layout` = ordered `"persistedId:SIZE"` tokens;
  unknown id/size tokens are silently skipped on decode; empty → auth-appropriate default). No new
  Room tables. `homeLayoutFlow(default)` takes the default as a param so the DataStore stays unaware
  of auth; the VM picks `defaultLayoutSignedIn/Out` via `isAuthenticatedFlow.flatMapLatest` — the
  2026-07-13 overhaul replaced both default lists (Phase 2.3); `TRENDING_COMMANDERS` is intentionally
  NOT in either default (gallery-only, board-length discipline).
- **Sizes are vestigial but only PARTLY cleaned up**: every widget renders at a single MEDIUM width
  (`CARD_OF_THE_DAY` now only declares `WidgetSize.MEDIUM`, matching the rest); the board's old
  bounds-registry/drag-hit-testing API (`HomeWidgetContainer.onRegisterBounds`, module-level
  `findTargetIndex()`, `HomeScreen`'s `itemBounds` map) was DELETED as dead code — the gallery sheet
  owns add/remove/reorder via its own local drag state, the board itself is static. Removing
  `WidgetSize` from `WidgetInstance`/`PersistedWidget` entirely (and making the decoder legacy-token
  tolerant) is DEFERRED — do this as its own tested pass, not bundled into a larger change.
- **ViewModel combine arity**: slices are bundled (`CoreSnapshot` 7 flows, `DataBundle{layout,stats,
  discover,social,gamification,recentlyAdded}` 6 flows) via the vararg-destructure `combine(vararg){
  args -> @Suppress("UNCHECKED_CAST") ... }` pattern once a bundle exceeds 5 flows (Kotlin's typed
  combine overloads cap at 5); inner sub-bundles (`socialSnapshotFlow`'s `TradesSnapshot`/
  `SocialExtras`) stay on the typed non-vararg overloads. `performanceFlow` MUST be declared before
  `statsSnapshotFlow` (property init order). Every data flow is `.catch{emit(empty/null)}`-isolated
  so one source failing never collapses the board.
- **Every data source is real** (2026-07-13 overhaul, Phase 1): `CommunityStatsRepositoryImpl`
  (Supabase RPC `get_community_stats()`, cache-first via `CommunityAggregateCache`, replaces the
  deleted `CommunityStatsRepositoryStub`); `ArchidektTrendingRepository` (reuses the existing
  `ArchidektClient`); Trades Hub wired to `TradesRepository`/`OpenForTradeRepository`/
  `TradeSuggestionsRepository`; `friendCount` wired to `FriendRepository.observeFriendCount()`;
  tournament round wired to a new read-only `TournamentRepository.observeCurrentRound()`. Account-gated
  widgets render `AccountGatedPlaceholder` (→ `CreateAccount`) when `!isAuthenticated`.
- `HomeWidgetHost` dispatches type→composable; `HomeWidgetContainer` just wraps it (no bounds
  registry, see above); all widgets share `WidgetShell` (flat Column, no card surface). No
  `success`/`error` tokens exist — win=`lifePositive`, loss=`lifeNegative`.
- **`RECENTLY_ADDED`** (new 2026-07-13): newest local collection additions,
  `UserCardRepository.observeRecentlyAdded(10)` — no Room migration (`created_at`/`updated_at`
  already existed on `UserCardCollectionEntity`). Keyed by the `user_card_collection` row id (NOT
  scryfallId — duplicate copies of the same card would collide on scryfallId).
- **`TRENDING_COMMANDERS` (Deck Doctor Community/Archetype plan Phase 5)**: its `TrendingSnapshot?`
  data is kept OUTSIDE the `HomeUiState` combine chain (a separate `HomeViewModel.trendingFlow`
  `stateIn`, threaded as its own param through `HomeScreen`→`HomeWidgetContainer`→`HomeWidgetHost`) —
  see `project_community_hub_seedbuild_trending` memory for why. Silently hidden (never an error
  state) on any failure/flag-off. Distinct from the new SOCIAL_HUB "Popular on Archidekt" slides —
  different backend (Cloudflare Worker vs. direct Archidekt API), never conflate the two.
- **First Steps carousel**: tap = CTA only (2026-07-13 — no longer also dismisses); a dedicated
  top-right dismiss affordance (`Icons.Default.Close`, ≥48dp) calls the same `SkipFirstStep`/
  `observeSkippedFirstSteps` DataStore mechanism as before. Most step conditions are data-driven
  (see `ALL_FIRST_STEPS` in `FirstStepItem.kt` for the per-step DATA-DRIVEN/DISMISS-ONLY doc).
- Top bar = time-of-day greeting (`Calendar.HOUR_OF_DAY`) + avatar (→ `OpenProfile`).
- → memory: `project_home_widget_board`, `project_home_feature_overhaul_2026-07-13`,
  `feedback_home_dashboard_audit_fixes_2026-07-13`, `feedback_archidekt_trending_stale_cache_bug`

### Gamification (`core/gamification/`, multi-phase — Phase 0 + Phase 1 complete)
Cross-cutting XP/levels/achievements/quests/streaks/cosmetics engine. **Local-first** (works 100%
offline; account only adds Phase-4 sync). The durable design doc is `docs/adr/ADR-002-gamification.md`
— **read it + the memory files before any gamification work.** Must-know:
- **Features never call the engine.** They emit a `ProgressionEvent` on `ProgressionEventBus` at the
  canonical write path (repository/use-case, after a successful commit — never a ViewModel/composable);
  the engine collects the bus in `ManaHubApp.onCreate` and processes on `@DefaultDispatcher`.
- **Every XP grant is idempotent via an `xp_transactions` ledger** keyed by a UNIQUE `idempotency_key`;
  `grantXpAtomically` updates `player_progression` ONLY when the ledger insert succeeded (rowId != -1).
  Caps are enforced by querying the ledger for the current local day/week, never an in-memory counter.
- **`grantXpAtomically` takes a DELTA, never a precomputed total.** It reads the current total INSIDE its
  `@Transaction`, computes `new = current + amount`, recomputes the level via the injected
  `levelForTotalXp` (`LevelCurve.levelForTotalXp`), and returns a `GrantResult(applied, previous/new
  totalXp, previous/new level)`. Callers (XpGranter / AchievementEvaluator / AchievementBackfill /
  ClaimQuestRewardUseCase) MUST NOT pre-read `getProgression()` and pass a total — two concurrent
  app-start grants with distinct keys would both read the same stale total and the 2nd clobbers the 1st
  (`total_xp < SUM(ledger)`). The friend weekly-cap window is ISO Monday (`TemporalAdjusters
  .previousOrSame(MONDAY)`), never `WeekFields.of(Locale)`. → memory: `feedback_gamification_xp_idempotency`
- **Win/loss in `GameFinished.isLocalWin` derives from `player_sessions.is_local = 1`**, never name
  matching (see `feedback_survey_winloss_isLocal`). ALL XP values/caps live in one `XpConfig`.
- Room **v39** added 6 tables (additive `MIGRATION_38_39`). `app/schemas/` is gitignored (39.json not
  committed). Master carries 140 pre-existing test failures — compare PRs against that baseline.
- Out of scope for v1: token shop, leaderboards, Lottie, push, seasonal track (extension points reserved).
- **Achievements (Phase 1):** the catalog (`domain/catalog/AchievementCatalog`, ~40 `AchievementDef`)
  is the source of truth, indexed by event class (`defsByEventType`) so an event evaluates only its
  registered defs. `AchievementEvaluator` NEVER overwrites an existing `unlocked_at`; `celebrated_at`
  is the separate celebration gate (backfill sets it = `unlocked_at` to suppress; live unlocks leave it
  null). Tier XP grants through the ledger key `achievement:{id}:tier:{n}` (idempotent). 15 migrated ids
  are STABLE PKs — never rename. Family A = local Room aggregate (retroactive + one-shot backfill flagged
  by DataStore `gamificationBackfillDone`); Family B = COUNTER for streaks + remote-backed social/
  tournament (no backfill). `GamificationStatsDao` is a read-only snapshot DAO (no entities → no schema
  change). `GamificationRepository.observeAchievements()` feeds the UI; the old
  `CheckAchievementsUseCase` + `core.domain.model.Achievement` were DELETED and `ProfileViewModel`
  rewired. `engine.outcomes: SharedFlow<ProcessedOutcome>` lets the UI correlate by `GameFinished.sessionId`.
- **Achievements UI (Phase 1):** Profile gained a tab row (Overview + Achievements only — Quests/Rewards
  are Phase 2/3); secret achievements render masked "???" until unlocked; `Screen.Profile` gained an
  optional `?tab=` arg for deep-linking. The unlock **celebration** overlay is hosted GLOBALLY in
  `MainActivity`'s root `Box` (Canvas particle burst, NO assets), queued sequentially off the DB
  (`celebrated_at IS NULL`) — toggle-off leaves unlocks pending (not marked), never shows. `GameResultScreen`
  shows a progression strip correlated by `lastSessionId` (the Room id, NOT the online session id). All
  gamification UI renders nothing when the master toggle is off.
- **Quests & streaks (Phase 2, complete):** no new Room schema (`quest_instances`/`streaks` shipped in v39).
  **Quests are NOT synced** (deterministically regenerable; only claimed XP flows through the ledger). The
  `QuestCatalog` is the source of truth (event-indexed `templatesByEventType`); **template ids are stable PK
  fragments** (`QuestInstanceEntity.id = "{templateId}:{periodKey}"`) — never rename. **All quests are pure
  monotonic INT counters** (no distinct/derived quests). Selection is **deterministic**: `fnv1a64("$stableId|
  $periodKey")` seeds `Random` (**NEVER `String.hashCode()`**) → pick 3 with ≥2 ACCESSIBLE + ≤1 EXPLORATION,
  no-repeat-yesterday; `stableId` = auth userId (anonymous = signed-in) else a persisted random-UUID device id
  (**not `ANDROID_ID`**); weekly key = ISO **week-based-year** (`IsoFields`, not `WeekFields.of(Locale)`).
  **Claim is idempotent** via ledger key `quest_claim:{instanceId}` + `grantXpAtomically`; auto-claims on
  expiry. `QuestReconciler` (settle stale + generate missing, idempotent) runs from BOTH `ManaHubApp.onCreate`
  (local-first, any auth) and the periodic `QuestRotationWorker`. `StreakTracker` (`daily_activity`,
  `AppOpenedToday`): max 2 freeze tokens, a gap consumes tokens to preserve the streak (never punishes), regen
  +1 per 7-day multiple via the single `regenTokens()` helper applied in BOTH the consecutive-day AND
  freeze-covered-gap branches of `advance` (symmetry by construction — never skip a milestone token just
  because the milestone day was covered by a freeze). `advance` counts CALENDAR DAYS: `dayDelta <= 0` (today
  == lastActiveDate) is a no-op, so repeated app-opens in one day never inflate the streak. Home
  `PROGRESSION_HUB`/`QUESTS_HUB` widgets + `CONTEXT_HERO` "N ready to claim".
- **Unlockables & cosmetics (Phase 3, complete):** 100% procedural (ZERO image/animation assets), local-only,
  no new Room schema (`EntitlementEntity` shipped in v39). `UnlockableCatalog` (21 items) is the source of
  truth — a **pure data table**; color is `CosmeticColorToken` enum refs resolved to `MaterialTheme.magicColors`
  at draw time (never raw Color → adapts to all 12 themes). `UnlockableId.value` = stable persisted PK +
  Phase-4 sync key, **never rename**. `UnlockRule` (`LevelAtLeast`|`AchievementUnlocked`) references only real
  `AchievementCatalog` ids. **The 12 themes stay FREE — never add a theme unlockable** (grandfathering).
  `EntitlementGranter` grants on level-up + achievement unlock (idempotent via `insertEntitlementIfAbsent`),
  hooked after streakTracker; `reconcileAll()` (full-state catch-up) runs once per launch from
  `ManaHubApp.onCreate` for retroactive cosmetics. Equip = DataStore only; `equip*` repo methods are **guarded
  by `hasEntitlement`** (unowned id → silent no-op; badges ≤3). Renderers are Compose Canvas; **FOIL = AGSL
  `RuntimeShader` API ≥33 with a sweepGradient fallback below** (minSdk 29; < 33 path never loads the AGSL
  class; shader `remember`-ed once + `uResolution`-normalized). Rewards tab = single `LazyVerticalGrid`
  (full-span headers, no nested scroll, keys = unlockable id). Hero overlays equipped cosmetics **purely
  additively**. Level-up celebration reuses the global host: `current` is `StateFlow<CelebrationItem?>`
  (`Achievement`>`LevelUp`); level-up driven by DataStore `lastCelebratedLevel` (sentinel -1 suppresses +
  silent-seeds in VM init → no spurious burst for existing players). Glyph/text use `magicTypography` —
  **never `MaterialTheme.typography`** (banned).
- **Backend & sync (Phase 4, complete):** bidirectional Room↔Supabase sync that is **MONOTONIC — never
  last-write-wins** (unlike the collection/deck LWW `SyncManager`). **Quests are NOT synced.** 5 Supabase
  mirror tables (`player_progression`/`xp_transactions`/`achievement_progress`/`entitlements`/`streaks`,
  owner-only RLS, `xp_transactions` PK `(user_id, idempotency_key)`) + 9 SECURITY-INVOKER RPCs (set-union
  ledger + recompute progression; GREATEST counters; earliest-non-null unlock; union entitlements; latest-date
  streak). **REVOKE EXECUTE FROM PUBLIC *and* anon** then GRANT authenticated+service_role (this project grants
  anon separately). The **SQL `level_for_total_xp` hardcodes the L1..100 reach-thresholds from the client
  formula** (zero float divergence) and **MUST stay in sync with `LevelCurve.kt`** (parity in
  `LevelCurveParityTest`). Android: **NO Room schema change** — ledger push uses a DataStore id-watermark
  (`getMaxLedgerId()`), the 3 small tables are pushed in FULL each cycle (safe under monotonic merge); pull
  recomputes local progression from the ledger sum (direct SET, NOT `grantXpAtomically`) + client-side
  monotonic merges; `reconcileOnSignIn` merges guest/anonymous progress INTO the account. Upload DTOs **omit
  `user_id`** (the RPC sets it from `auth.uid()` — cross-user write vector closed). **L3 per-device key
  scoping:** a `ProgressionEvent` whose `idempotencyKey` is LOCAL-ID-DERIVED (local Room id / local UUID /
  local timestamp — `isDeviceScoped = true`: game/survey/tournament/scan/deck_created/cards_added) is
  prefixed `dev:{deviceId}:{rawKey}` via `IdempotencyKeyScoper.scope(...)`, applied ONLY in
  `XpGranter.resolveLedgerKey` (pre-check + insert + dedup gate all use the resolved key) so two guest
  devices don't collide on the server PK. GLOBALLY-STABLE-per-user keys MUST stay un-prefixed
  (`isDeviceScoped = false`): server-side `trade`/`friend`, per-user `app_open`, and the catalog/period keys
  produced OUTSIDE XpGranter (`achievement:{id}:tier:{n}`, `quest_claim:{instanceId}`) — prefixing those
  would double-grant once per device after sign-in merge. Device id = the existing
  `UserPreferencesDataStore.getOrCreateGamificationDeviceId()` (per-install random UUID, NOT `ANDROID_ID`,
  NOT `QuestStableIdProvider.stableId()` which collapses to the user id when signed in). When adding a new
  ledgered `ProgressionEvent`, set `isDeviceScoped` per this rule.
- → memory: `project_gamification_phase0`, `project_gamification_phase1`,
  `project_gamification_phase1_chunkB_ui`, `project_gamification_phase2`, `project_gamification_phase3`,
  `project_gamification_phase4`, `feedback_gamification_xp_idempotency`,
  `feedback_achievement_unlockedat_persistence`, `feedback_gamification_celebration_ui`

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
Non-Kotlin work (Python `scripts/draftsim_py/`, Worker JS, Gradle, docs, memory) is handled directly.
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
