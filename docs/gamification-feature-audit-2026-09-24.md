# Gamification drift audit — 2026-09-24

> Historical pre-restoration findings. Restoration work began after this snapshot; use the
> restore runbook for current activation requirements and the source tree for current behavior.

Read-only audit of the gamification system against the rest of the app, about 340 commits after
the 2026-07-10 baseline (`docs/gamification-feature-audit-2026-07-10.md`). Branch
`feature/deck-wizard`. No repo file was edited and no Gradle or test task was run. The live Supabase
project `uimogilwuixgkgfcfmyb` was queried read-only (`pg_catalog`/`information_schema` SELECTs,
aggregate counts only, `get_advisors`).

Paths below are relative to `E:\Projects\ManaHub` unless they are absolute. `:app` means
`app/src/main/java/com/mmg/manahub/`.

---

## 1. Executive state table

| Aspect | State (2026-09-24) |
|---|---|
| Feature visibility | Hidden. `gamificationEnabledFlow` returns `prefs[KEY] ?: false` (`:app core/data/local/UserPreferencesDataStore.kt:663-664`). The Settings toggle is still commented out (`feature/settings/presentation/SettingsScreen.kt:408-417`). |
| Backend gate (ADR-005 D1) | Present in `app/ManaHubApp.kt:660-724` (engine, quest worker, one-shot tasks) and `:803-819` (sync worker + reconcile). It collects reactively and calls `cancelUniqueWork`. Both workers also self-abort. **But:** the ON→OFF path cannot stop the engine (G-04), and the flag is a user preference rather than a release gate (G-03). |
| Engine code changed since 07-10 | Only Daily Puzzle wiring (`PuzzleSolved`, puzzle streak, `PUZZLE_SOLVER`) and Crashlytics on stage failures (d4a6900f, 1ceded81, b5eaec1c). **None of the 07-10 findings were fixed.** |
| Event types | 12 (the 11 from the baseline plus `PuzzleSolved`). Emitters are listed in section 4. |
| Catalogs | 39 achievement defs (the baseline said 37; `PUZZLE_SOLVER` was added and the baseline count was off by one), 7+6 quest templates, and 22 unlockables. |
| Sync | Monotonic push/pull. **Violates ADR-008:** the 5 `RETURNS SETOF` RPCs have no pagination, and the pull cursor is the client-supplied `created_at` (G-01). The server stores no server-side timestamp. |
| Local data scoping | **None.** The gamification tables have no user column. Sign-out and account deletion do not wipe them. `reconcileOnSignIn` pushes all local rows into whichever account signs in next (G-02). |
| Live data | `xp_transactions`: 456 rows across 3 users, at most 203 rows per user, so no user has hit the 1000-row cap yet. The latest ledger row is dated **2026-08-09** and the latest `achievement_progress` row **2026-08-11**. Both are after the 07-28 backend gate (see G-03). |
| Supabase invariants | Mostly compliant: `search_path` set, INVOKER functions, `REVOKE` from anon/PUBLIC, `(select auth.uid())` in policies, FK leading PK column, `ON DELETE CASCADE`. Hygiene gaps are listed in G-24. |
| KMP | Domain (models, catalogs, curve, bus, repo interface, claim use case) is in `commonMain` with no Android imports. Engine, Room, sync, workers and all UI are still in `:app`. Details in section 5. |
| Tests | 21 JVM suites plus 2 instrumented suites touching gamification. No test for `GamificationEngineImpl`, `GamificationRepositoryImpl`, the backend gate or sync pagination (section 6). |
| Restore readiness | **Not ready.** 7 findings are BLOCKER-for-restore: G-01, G-02, G-04, G-05 (decision), G-12, G-13 and G-03 (gate model). |

---

## 2. Baseline findings status (2026-07-10 §9)

| ID | Status | Evidence |
|---|---|---|
| 9.1 STREAK_3/7/30 always stay at 0 | **OPEN** | `:app core/gamification/engine/AchievementEvaluator.kt:152-154` has a `def.id.startsWith("STREAK_") -> 0` branch. That branch hard-returns 0, so it was never wired up. The KDoc at `:26-27,140-141` still calls it a "Phase-2 stub". The catalog comment at `shared/core-domain/.../catalog/AchievementCatalog.kt:384-387` is unchanged. `AchievementCatalog.kt:417-419` (the Puzzle work) explicitly deferred this fix again ("deferred together as separate follow-up work"). There is also an ordering trap for the fix: `GamificationEngineImpl.process` runs `achievementEvaluator` (`GamificationEngineImpl.kt:63`) **before** `streakTracker` (`:74`), so a streak-reading resolver would see yesterday's value. See G-12. |
| 9.2 TOURNAMENT_WIN blocked (`isLocalWinner = false`) | **OPEN** | `:app feature/tournament/data/repository/TournamentRepositoryImpl.kt:337-352` still hard-codes `isLocalWinner = false`. Tournaments still have no local seat: `TournamentPlayerEntity.kt:21-27` has id/tournamentId/playerName/playerColor/deckId/seed only. Commit a6d80fb8 changed tournament UI and 5 lines in the repository, none of them a seat concept. `title_tournament_champion` is still transitively unreachable. See G-14. |
| 9.3 WIN_STREAK_5/10 described as "Win 3 games in a row" | **OPEN** | `AchievementCatalog.kt:183,192,201` all read "Win 3 games in a row". Six more copy defects were found; see G-13. |
| 9.4 Stale comments | **OPEN, and more added** | `GamificationEngineImpl.kt:27` ("quest/streak evaluators are still Phase-2 stubs"), `AchievementCatalog.kt:385-387`, `UnlockableCatalog.kt:30` ("~20 items"; the real count is 22). New ones are listed in G-20. |
| 9.5a HIGH_VALUE `%s` | **ESCALATED to a visible bug** | `AchievementCatalog.kt:137` has `"Own a card worth more than %s"`. `GamificationRepositoryImpl.kt:253` passes `description` through raw, and `AchievementsTab.kt:140-143,203` renders it verbatim. No `format()` call exists anywhere in the chain, so the user sees a literal "%s". See G-13. |
| 9.5b Silent XP jump / celebration backlog on restore | **Superseded** | Since ADR-005 nothing accrues while the feature is off, so the jump no longer happens. The opposite problem now exists: most progress is lost (G-05). |

---

## 3. Drift findings

Severity scale: **BLOCKER** = must be fixed before the UI is restored. HIGH, MEDIUM and LOW apply regardless of restore.

### G-01 — Sync violates ADR-008: unpaginated SETOF pulls keyed on a client clock — BLOCKER

**Evidence**
- Live RPC `get_xp_transactions_changes_since(p_since bigint) RETURNS SETOF xp_transactions`, body `WHERE user_id = auth.uid() AND created_at > p_since ORDER BY created_at ASC`. It has no `LIMIT` and no keyset cursor. The same shape applies to `get_achievement_progress_changes_since`, `get_entitlements_changes_since`, `get_streaks_changes_since` and `get_progression_changes_since` (all `SETOF`, verified in `pg_proc`).
- `xp_transactions` has the columns `user_id, idempotency_key, amount, source_category, source_ref, created_at`. **There is no server-assigned column.** `created_at` is the device clock at grant time, taken verbatim from the client payload by `batch_upsert_xp_transactions`.
- The small tables' `updated_at` is also client-supplied: the push DTO is stamped with `syncStartTime` (`:app core/gamification/data/sync/GamificationSyncManager.kt:125-139`). The server keeps `GREATEST(existing, incoming)`.
- Client pull: `SupabaseGamificationDataSource.kt:40-48` makes one `rpc(...).decodeList()`. The watermark is saved as `syncStartTime - 5 min` no matter what was fetched (`GamificationSyncManager.kt:216-217`). That is exactly the "watermark claimed for time never pulled" defect from ADR-008 §Context 2.
- Push: `getLedgerAbove(pushedLedgerId)` is sent as **one** RPC call (`GamificationSyncManager.kt:117-120`). `reconcileOnSignIn` clears the watermarks (`:92-96`), so every sign-in re-sends the entire ledger in a single request body.

**Failure modes**
1. Once a user has more than 1000 ledger rows (roughly one row per game, card add, deck, daily open, quest claim or tier), a fresh device silently receives 1000. The watermark then moves past the rest and they are never re-fetched. The local `sumAllXp()` recompute (`:150-152`) then shows a lower level than the server.
2. Even below the cap, a row pushed late by another device carries an old `created_at` (an offline device, or a guest merge days later). Its `created_at` is below this device's watermark, so it is **never pulled**. The 5-minute margin only covers clock skew, not late pushes. The small tables have the same problem through the client-supplied `updated_at`.

**Proposed fix (logic)**
- Server (backend-supabase-expert):
  - Add a server-assigned, monotonic change cursor to all 5 tables. For the ledger, use `server_seq bigint GENERATED ALWAYS AS IDENTITY`, which is append-only and tie-free. For the 4 mutable tables, use `changed_at bigint NOT NULL DEFAULT now_ms()`, set by the merge RPCs to server `now()` on every actual change, never from the payload.
  - Replace the `*_changes_since` RPCs with keyset pages. Ledger: `WHERE user_id=(select auth.uid()) AND server_seq > p_after_seq ORDER BY server_seq LIMIT least(coalesce(p_limit,500),500)`. Mutable tables: ADR-008's two-predicate `(changed_at, pk)` form.
  - Return the cursor fields in the rows.
  - Keep the page-size cap on the server.
  - Drop or keep `get_progression_changes_since`: the client never calls it (see G-24).
- Client (android-kotlin-architect):
  - Drain each table with `PagedSync.drainPages` (`:shared:core-data` commonMain).
  - Persist the cursor from the last **applied** row of each page.
  - Apply ADR-008's watermark rule: never advance past a row that was fetched but not applied. For the ledger, the cursor is `server_seq` and advances only after `insertLedgerRowIfAbsent` succeeded for the whole page.
  - Chunk the push into slices of 500 or fewer, and advance the push watermark per confirmed slice.
  - Existing per-user millis watermarks must be migrated. Simplest option: clear them once so the first run does a full paged pull.
- Update ADR-008 "Applied to" and root CLAUDE.md ("Still unpaginated: the five gamification…") once this ships.

### G-02 — Local gamification state is not scoped per account, so it leaks between accounts — BLOCKER

**Evidence**
- The Room entities have no owner column: `XpTransactionEntity` (id, idempotency_key, amount…), `PlayerProgressionEntity` (singleton `id`), `AchievementProgressEntity` (PK `achievement_id`), `EntitlementEntity` (PK `unlockable_id`), `StreakEntity` (PK `type`) and `QuestInstanceEntity`. Equipped cosmetics, `lastCelebratedLevel`, `gamificationBackfillDone` and `gamification_device_id` are device-wide DataStore keys.
- `AuthRepositoryImpl.signOut()` / `signOutLocally()` / `deleteAccount()` (`:app feature/auth/data/repository/AuthRepositoryImpl.kt:756-812`) clear only the privacy flags.
- `ManaHubApp.kt:803-813` runs `reconcileOnSignIn(userId)` for every `(Authenticated, enabled)` pair. That call clears the watermarks and **pushes every local ledger row and the full small-table state into the signed-in account** (`GamificationSyncManager.kt:92-97, 117-139`).
- Contrast with Trades, which already fixed the same class of problem: H4/H8 added `owner_user_id` and `evictForeignAccountRows(userId)` at sign-in (`ManaHubApp.kt:751-757`).

**Failure mode:** user A signs out and user B signs in on the same device. All of A's XP, achievements, cosmetics and streaks merge permanently into B's server account (the merges are monotonic and cannot be undone), and B's history merges into the local store. Deleting A's account leaves A's progress on the device for the next account.

**Context change:** anonymous sign-in was removed in bac195e1 (no `signInAnonymously` call exists in `app/src/main` or `shared`). A "guest" is now a local-only user with no Supabase id. The guest→account merge only makes sense for the **first** account that claims a guest store.

**Proposed fix (logic, android-kotlin-architect):**
- Persist a `gamification_owner_user_id` in DataStore (null means a guest-owned store).
- On `Authenticated(u)`:
  - Owner null → claim: set owner = u and run `reconcileOnSignIn` (the guest merge).
  - Owner == u → normal `sync`, not a reconcile.
  - Owner ≠ u → wipe the 6 gamification tables and the gamification DataStore keys (equipped cosmetics, `lastCelebratedLevel` reset to the -1 sentinel, backfill flag), set owner = u, then do a full paged pull.
- On `deleteAccount` success, and optionally on sign-out, wipe the local store and reset the owner to null.
- Record the rule in the gamification CLAUDE.md and in a memory file.

### G-03 — The master flag is a user preference, not a release gate; there is no compile-time or remote gate — BLOCKER (gate model)

**Evidence**
- `?: false` only applies when the key is **absent**. Any user who flipped the visible Settings switch before 2026-06-16 has `gamification_enabled` persisted in DataStore. If it is `true`, that user sees the full UI (Profile tabs, Home hubs, celebrations) and runs the engine and the hourly 9-RPC sync today.
- Live data is consistent with this: ledger rows were written up to 2026-08-09 and `achievement_progress` rows up to 2026-08-11, after the 07-28 backend gate. **Unverified** which build or device produced them; debug builds are possible.
- `FeatureFlags` (`shared/core-model/.../core/FeatureFlags.kt:7-49`) has no `Gamification` object. `KillSwitch` (`shared/core-domain/.../core/domain/config/KillSwitch.kt`) is an empty enum. Every gate reads only the DataStore flag: ManaHubApp, both workers, `HomeViewModel.kt:654-673`, `ProfileViewModel.kt:183`, `GameResultStripViewModel`, `GamificationCelebrationViewModel.kt:69`, `WidgetGallerySheet.kt:304-305`.

**Proposed fix (logic, android-kotlin-architect):**
1. Add `object Gamification { const val ENABLED = false }` to `FeatureFlags`. This is the release gate and the counterpart of `PUZZLE_ENABLED`.
2. Add `GAMIFICATION("kill_gamification")` to `KillSwitch` (fail-open default `false`). This is the emergency brake after restore.
3. Add a single commonMain `GamificationAvailability`:
   `availableFlow = combine(compile ENABLED, remoteConfig.config.map { !it.isKilled(GAMIFICATION) }, userPrefFlow) { a, b, c -> a && b && c }.distinctUntilChanged()`
4. Every gate listed above consumes `availableFlow` instead of the raw pref.
5. The Settings `GamificationSection` renders only when `ENABLED && !killed`. That replaces commenting code out and removes restore step 2.
6. Keep the user pref as an **opt-out** and default it to `true` once `ENABLED` flips. The compile flag covers the hidden period, so the DataStore default no longer needs to change.

### G-04 — Turning the flag off mid-session cannot stop the engine — BLOCKER

**Evidence:** `ManaHubApp.kt:714-722` says the off branch only cancels `QuestRotationWorker`. The comment admits the engine has no `stop()` and calls this "not reachable yet because the switch is commented out". `GamificationEngineImpl.start` (`:108-113`) uses a process-lifetime `AtomicBoolean` together with `launchIn(appScope)`. Once the Settings toggle (G-03 step 5) or a kill switch exists, the path is reachable. The collector keeps granting XP and writing Room after the user or the server turns the feature off. Toggling OFF→ON also cannot re-run the one-shot tasks (`gamificationOneShotStartupTasksRun`, `:660, 671-672`).

**Proposed fix:**
- `start(scope)` returns a `Job`, or the engine exposes `stop()`.
- The gate uses `collectLatest { enabled -> if (enabled) coroutineScope { engineJob = …; awaitCancellation() } }` so a false value cancels the collector.
- On each OFF→ON transition, re-run the catch-up tasks (backfill, `reconcileAll`, quest reconcile, `AppOpenedToday`). All of them are idempotent, which is also what G-05 needs.
- Extract the whole gate from `ManaHubApp.onCreate` into a testable `GamificationBackendGate` class (see section 6).

### G-05 — ADR-005's "nothing is lost" premise is false for most progress — BLOCKER (product decision)

**Evidence:** ADR-005 D1 and `ManaHubApp.kt:641-649` claim that enabling later "recomputes the user's true state from scratch". Only these are actually retroactive:
- DERIVED achievements plus their tier XP (`AchievementBackfill`)
- entitlements (`reconcileAll`)

These are **not** retroactive and are permanently lost while the feature is off:
- all per-event XP (games 20/+30, cards, scans, decks 40, trades 50, friends 30, daily open 10, puzzles, quest claims)
- COUNTER achievements (`WIN_STREAK_*`, `TOURNAMENT_*`, `FIRST_FRIEND`/`FRIENDS_5`, `FIRST_TRADE`/`TRADES_10`, `STREAK_*`)
- daily-activity and puzzle streaks: on restore `StreakTracker.advance` sees a months-old `lastActiveDate` and resets to 1
- quests

Two more gaps:
- The backfill is **one-shot per install** (`isGamificationBackfillDone`, `ManaHubApp.kt:681-684`). On installs that ran it before 07-28, DERIVED lines re-evaluate only when a matching event fires. Collection imports and sync pulls fire no event (G-25), so those lines stay stale.
- `ProgressionEventBus.kt:12-14` KDoc still claims the backfill reconciles "on next launch".

**Decision needed (docs owner; then android-kotlin-architect):**
- (a) Accept the loss and correct ADR-005, CLAUDE.md, the restore guide and the ManaHubApp comment. Or:
- (b) Add a restore catch-up:
  - Re-run the backfill on every enable transition, by dropping the one-shot guard or resetting it when the feature is disabled.
  - Optionally add a retroactive XP pass over Room history. The keys are deterministic, so the ledger dedupes it: `game:{sessionId}:result` from `game_sessions`, `deck_created:{id}` from `decks`, `survey:{id}`, `tournament:{id}` and `puzzle:{date}:solved`.
  - The retroactive pass must go through `XpGranter.resolveLedgerKey` so the `dev:{deviceId}:` prefix matches live grants, and it should respect the daily caps or run with an explicit "retro" category.

### G-06 — The startup `AppOpenedToday` can be lost, and there is no midnight rollover — MEDIUM

**Evidence:**
- `appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)` (`ManaHubApp.kt:443`).
- `gamificationEngine.start(appScope)` (`:668`) launches the collector asynchronously, and the emit happens in a *separate* `appScope.launch` (`:695-704`).
- The bus is `MutableSharedFlow(replay = 0, extraBufferCapacity = 64)` (`ProgressionEventBus.kt:21-25`). With zero subscribers, `emit` returns immediately and the event is dropped. On a multi-threaded dispatcher the emit can win the race, which skips that day's check-in XP and streak advance (and would skip STREAK_* once G-12 lands).
- The event is also emitted once per process (`gamificationOneShotStartupTasksRun`), so an app kept alive past midnight never records the next day.

**Proposed fix:**
- Make `start()` subscribe before it returns: `launch(start = CoroutineStart.UNDISPATCHED)`, or await a `subscriptionCount > 0` / `onSubscription` signal. Alternatively call `engine.process(AppOpenedToday)` directly.
- Emit on every `ProcessLifecycleOwner` ON_START whose local date changed. The key `app_open:{date}` makes repeats free.

### G-07 — `TradeCompleted` fires on *accept* by the receiver only; revoked trades keep the reward — HIGH

**Evidence:** `:app feature/trades/data/repository/TradesRepositoryImpl.kt:181-194` emits in `acceptProposal` when the accept succeeds. The lifecycle is `ACCEPTED -> COMPLETED | REVOKED` (`shared/core-model/.../TradeStatus.kt:10`), with explicit `revokeAcceptance`/`markCompleted` steps (`:196-200`, `TradeNegotiationViewModel.kt:445,528`).

**Consequences:**
- The proposer never earns trade XP or the `FIRST_TRADE`/`TRADES_10` counters.
- An accepted-then-revoked trade still pays 50 XP and increments the counter.
- The event name says "completed" but fires on acceptance.
- Web drops the event entirely (`kmp-migration-progress.md:414`).

**Proposed fix:** emit when a proposal the user participates in is **first observed as `COMPLETED`**, for both parties. Do this in the `markCompleted` success path plus the proposal-refresh/sync path that observes server status. Key it `trade:{rootProposalId ?: id}`, which stays non-device-scoped, so the counter-proposal chain grants once. Drop the emission from `acceptProposal`. The `TradesRepositoryImplTest` expectations must flip.

### G-08 — `DeckCreated` fires for throwaway drafts, imports and wizard shells (XP farming) — MEDIUM

**Evidence:**
- The only emitter is `DeckRepositoryImpl.createDeck` (`:app core/data/repository/DeckRepositoryImpl.kt:89-118`), which emits immediately on row insert.
- Deck Studio creates an **empty fresh draft on entry** (`feature/decks/presentation/DeckStudioViewModel.kt:586-606`) and soft-deletes it on exit if still empty (`:1812-1819`). The XP (40), the `daily_build_deck` quest (50 on claim) and the `maxRewardedDecksPerDay = 3` slots are consumed anyway.
- Other creators: `DeckWizardViewModel.kt:1995` (wizard shell "Draft"), `ImportDeckCardsUseCase.kt:315` (text import and Community Deck import via `ImportCommunityDeckUseCase`), `DeckViewModel.kt:69` ("Imported Deck"), and `DraftSimRepositoryImpl.kt:452` (unreachable while `FeatureFlags.Draft.SIMULATOR_ENABLED=false`).
- Imports grant full "deck built" XP, which contradicts the collection "imports grant no XP" invariant (`CommitImportedCardsUseCase.kt:8-10`).
- Live data: `DECK` is 92 of 456 ledger rows (20%). 8 user-days show 4–6 DECK grants within one UTC day. That is consistent with a local-day cap of 3 straddling UTC midnight, so it is **not proof** of a cap bypass.
- DERIVED `DECKS_BUILT` counts `is_deleted = 0` (`GamificationStatsDao.kt:155`), so discarded drafts do not inflate achievements. Only XP and the quest are affected.

**Proposed fix:**
- Move the emission from `createDeck` to the first moment the deck becomes "real": the first mainboard card persisted, or an explicit save with at least 1 card.
- Add a `source` field (BUILT, WIZARD, IMPORT, COMMUNITY, DRAFT) to `DeckCreated`.
- Grant XP only for BUILT/WIZARD. IMPORT and COMMUNITY advance DERIVED achievements (they are real decks) but earn 0 XP.
- Keep key `deck_created:{id}`.

### G-09 — The AddCard queue (manual adds) is rewarded as *scans*; `CardsAdded` is nearly dead — MEDIUM

**Evidence:**
- The AddCard "Select multiple" flow and the queue shared with the Scanner commit through `CardQueueActions.forScannedCards(... CommitScannedCardsUseCase)` (`shared/core-domain/.../usecase/queue/CardQueueActions.kt:236-253`, wired in `:app core/di/SharedDomainKoinModule.kt:205`). Every batch becomes `CardScanned` (`CommitScannedCardsUseCase.kt:110-119`), whether or not a camera was involved.
- `AddCardToCollectionUseCase` (the only `CardsAdded` emitter, `:64-72`) is now consumed **only** by `CardDetailViewModel.kt:59`.
- Result: manual adds advance "Scan Patrol" (`daily_scan_cards`), while "Stock Up" (`daily_add_cards`) and "Collector's Week" (`weekly_add_cards`, `QuestCatalog.kt:55-59,110-114`) almost never move.
- The queue KDoc at `CardQueueActions.kt:238-242` documents this as intended ("commits count as scans"). That conflicts with the quest design.

**Proposed fix:** `CardCommit` carries an `origin` field (SCANNED or MANUAL). The committer emits one `CardScanned(count)` for the scanned subset and one `CardsAdded(addedUnique, addedCopies)` for the manual subset per batch. It needs `AddOutcome` aggregation, the same way the scan path aggregates. The shared daily cap applies to both. Deck-source mode and the scanner's deck merge write to decks, not the collection, and correctly emit nothing.

### G-10 — `CardsAdded` double-counts the first copy — LOW

**Evidence:** `AddCardToCollectionUseCase.kt:67-70` sends `addedCopies = quantity` and `addedUnique = 1` for a new row.
- `XpGranter.kt:124` computes `5·unique + 2·copies`, which pays 7 XP per new card although `XpConfig` says `additionalCopy` is for extra copies.
- The quests advance by `addedUnique + addedCopies` (`QuestCatalog.kt:59,114`), so one new card counts 2 toward "add 3 cards".

**Fix:** `addedCopies = quantity - (if CREATED_NEW 1 else 0)`, or change the quest advance to `addedCopies` only.

### G-11 — COUNTER achievements and quests are not gated on event dedupe — MEDIUM

**Evidence:**
- `GamificationEngineImpl.process` runs the achievement and quest stages even when `XpGranter` returned `none` because the key already existed (`GamificationEngineImpl.kt:58-71`; `XpGranter.kt:54`).
- COUNTER defs do `else -> current + 1` (`AchievementEvaluator.kt:156`) and quests add `advance(event)` (`QuestEvaluator.kt:50-59`), with no event-seen check.
- Farming vectors:
  - Remove and re-add the same friend: `removeFriend` then a new request produces a new `friendshipId` (`FriendRepositoryImpl.kt:99-107,121-124`). The XP is capped at 5 per week, but the `FRIEND` counter is not.
  - Revoked trades (G-07).
- Only the accepting side of a friendship emits (`acceptRequest`, `:79-108`). `acceptInvite` (`:144`) emits nothing, so friends made via invite links never count. **Unverified** whether `accept_invite` already creates an ACCEPTED friendship.

**Proposed fix:**
- `XpGranter.grant` returns a tri-state (Applied, Duplicate, NoXp). The engine skips the COUNTER and quest stages on Duplicate. Non-ledgered events (DeckSaved, FeatureExplored) keep their current behaviour.
- Longer term, make `FIRST_FRIEND`/`FRIENDS_5` and the trade lines DERIVED from server truth (the friend count in the Room friends cache, and the count of completed trades), which removes farming and double-count by construction.
- Emit `FriendAdded` for the requester too, when the refresh observes the new ACCEPTED friendship, and on invite acceptance.

### G-12 — The DEDICATION category is dead (9.1) and has an ordering trap — BLOCKER

**Evidence:** see 9.1. In addition, `GamificationEngineImpl.kt:63,74` evaluates achievements before `streakTracker.process`.

**Proposed fix:**
1. Reorder the stages to XP → **streak** → achievements → quests → entitlements.
2. In `counterNextValue`, `STREAK_*` returns `max(current, dao.getStreak(TYPE_DAILY_ACTIVITY)?.current ?: 0)`. This never lowers the value, and unlocks are permanent.
3. Fix the stale KDoc and catalog comments.
4. Decide whether the separate puzzle streak (`StreakTracker.TYPE_PUZZLE`, `StreakTracker.kt:134-136`) gets an achievement. ADR-006 D5 deferred `puzzle_streak`. It also syncs as a second `streaks` row type, and `observeDailyActivityStreak` shows only daily activity.

### G-13 — Catalog copy is wrong in 8 places — BLOCKER (user-facing)

All in `shared/core-domain/.../catalog/AchievementCatalog.kt`:

| Def | Line | Problem |
|---|---|---|
| `WIN_STREAK_5`, `WIN_STREAK_10` | 192, 201 | "Win 3 games in a row" (baseline 9.3). |
| `HIGH_VALUE_COLLECTION` | 137 | The literal `%s` reaches the UI (9.5a). Embed "$60", matching the tier at `:140`. |
| `COLLECTOR_50`, `COLLECTOR_500` | 71, 81 | "Add 50/500 cards to your collection", but the resolver is `UNIQUE_CARDS` (distinct printings). |
| `UNIQUE_CARDS_2000` | 91 | "Own 50 / 500 / 2,000 unique cards", but there is a single 2,000 tier. |
| `MYTHIC_OWNER` | 124 | "Own a mythic rare card", but the tiers are 1 and 10. |
| `COMMANDER_KILLER` | 230 | "Eliminate a player with commander damage", but the tier is 10 and the resolver counts **local wins in COMMANDER mode** (`GamificationStatsDao.kt:139-146`), not commander-damage eliminations. |
| `RAINBOW_COLLECTOR` vs `SECRET_PERFECT_RAINBOW` | 114 / 452-460 | The public one reads "Have cards of all 5 colors" but both use `COLORS_WITH_20_PLUS >= 5`. They always unlock together, so the secret is redundant. Lower the public one to `>= 1` card per color with a new resolver, or raise the secret. |
| `GAMES_PLAYED_*`, `STREAK_*` | 485, 391-409 | Every single-tier id repeats the multi-tier description ("Play 10 / 50 / 100 / 500 games"). Each should state its own threshold. |

Ids are stable PKs, so change only the text and thresholds. Changing a threshold on an existing id needs a note, because `tier_reached` is persisted and never lowered. Owner: android-kotlin-architect (commonMain).

### G-14 — `TOURNAMENT_WIN` is still blocked (9.2) — MEDIUM

**Proposed fix, option (a):**
- Add a `tournament_players.is_local` column: Room v57, additive, following the migration pattern since v39.
- Add a "This is me" seat in tournament setup, mirroring `isAppUser` in game setup.
- Derive `isLocalWinner` from the final standings of the local seat.

**Option (b):** hide `TOURNAMENT_WIN` and `title_tournament_champion` until that exists (G-15). Recommend (b) for the restore and (a) later.

### G-15 — Catalogs advertise rewards for hidden or unreachable features — MEDIUM

**Evidence:**
- `PUZZLE_SOLVER` (`AchievementCatalog.kt:423-435`) is visible in the Achievements tab, but `FeatureFlags.Puzzle.PUZZLE_ENABLED = false`. The route `Screen.DailyPuzzle` is registered unconditionally (`AppNavGraph.kt:703`); only the Home widget entry is gated.
- `TOURNAMENT_WIN` and the `title_tournament_champion` cosmetic are also unreachable (G-14).

**Proposed fix:** add an optional `availability: () -> Boolean` / `FeatureGate` to `AchievementDef`, `QuestTemplate` and `Unlockable`. Filter unavailable entries in `GamificationRepositoryImpl.observeAchievements/observeRewards`, in quest generation, and in the backfill.

### G-16 — Home drift: dead `QuestsReady` hero, and gamification widgets default to visible — MEDIUM

**Evidence:**
- `HomeHeroState.QuestsReady` (`HomeUiState.kt:368`) is rendered (`HomeWidgets.kt:1111-1112, 1428`, strings at `:3840,3878`) but **never produced**. `HomeViewModel.kt:1694` only builds `Welcome`. The producer disappeared in 4f780a7e (2026-07-22). The CONTEXT_HERO "N quests ready to claim" integration from the baseline is gone.
- `HomeUiState.kt:127` defaults `gamificationEnabled = true`, and `HomeViewModel.kt:1578` uses `gamification?.enabled ?: true`. Until the snapshot resolves, persisted `PROGRESSION_HUB`/`QUESTS_HUB` entries survive the board filter (`HomeBoard.kt:112`) and hold skeleton slots (`HomeWidgetReadiness.kt:37-38`), then vanish. That is the flash 47ba9560 set out to remove, and it defaults to showing gamification widgets instead of hiding them.

**Proposed fix:** restore the `QuestsReady` producer, gated on `gamification.data.claimableCount > 0` with its priority placed relative to Welcome, or delete the state, its renderer and its strings. Make `gamificationEnabled` default to false, or use a tri-state, and filter `isGamification` widgets while `gamificationLoaded` is false.

### G-17 — `ProfileViewModel` ignores ADR-005 D4, defaults to showing gamification, and has no telemetry — MEDIUM

**Evidence:**
- `ProfileViewModel.kt:109` sets `gamificationEnabled: Boolean = true`, so the tab row and XP ring show until the flow emits.
- Six gamification Room observers are collected unconditionally in `init` (`:168-208`), whatever the flag or selected tab.
- Every failure is swallowed with `.catch { /* ignore */ }` and no Crashlytics.
- `claimQuest` wraps the use case in `runCatching` (`:397-409`), which also swallows `CancellationException` and records nothing.
- The equip and unequip handlers use bare `runCatching` (`:422-460`).

**Proposed fix:**
- Use a tri-state or `false` default.
- Wrap the gamification flows in `availableFlow.flatMapLatest { if (!it) flowOf(empty) else … }` and scope them to the selected tab where possible.
- Add `recordNonFatal` breadcrumbs (`profile_quest_claim_failed`, `profile_gamification_flow_failed`) through the crashlytics-ux-auditor spec.
- Rethrow cancellation.

### G-18 — Shared-component and token violations in the gamification UI — MEDIUM

**Evidence (all `:app feature/...`):**
- `profile/presentation/ProfileScreen.kt:420-460`: `ProfileTabRow` uses Material3 `TabRow`/`Tab`; the inventory requires `ManaTabRow`.
- `profile/presentation/QuestsTab.kt:366-397`: `ClaimButton` is a hand-rolled `Surface + clickable`; use `MagicCtaButton(Filled, Primary)`.
- `ProfileScreen.kt` (the hero hosts the cosmetics):
  - `Color.Black` at `:749-750` and `Color.White` at `:804`
  - raw `fontSize` sp at `:732` (72.sp), `:803` (26.sp) and `:854` (11.sp)
  - `RoundedCornerShape(n.dp)` literals at `:594, 694, 847, 977, 1008, 1046, 1142, 1186, 1229` instead of `CardShape`/`ChipShape`
- `gamification/presentation/GamificationCelebrationHost.kt:152,286`: `Color.Black.copy(0.72f)` scrim. No scrim token exists; add one to `MagicColors` or reuse the sheet/dialog scrim.

**Used correctly:** `EmptyState` (Achievements/Quests/Rewards), `MagicProgressBar`, `MagicToastHost`, `LazyColumn`/`LazyVerticalGrid` with keys, `magicTypography`, and `≥48dp` targets (the tabs and ClaimButton set `heightIn(48.dp)`).

Owner: android-kotlin-architect; android-edge-case-tester audits NeonVoid and HallowedPrint.

### G-19 — Telemetry — MEDIUM

**Evidence:**
- `GamificationSyncManager.kt:221`: `log("gamification_sync_failed: userId=$userId")` puts the raw account id in a breadcrumb. That breaks the "event name only, no PII" rule; `setCustomKey` already carries the error type.
- `GamificationSyncWorker.kt:116-120`: `onFailure = { Result.retry() }` is unbounded. `MAX_ATTEMPTS` applies only to thrown exceptions, and `sync` never throws except on cancellation, so a persistently failing sync retries with backoff indefinitely.
- There is no breadcrumb or custom key for the effective gate state (`gamification_available`) or for the reason it is off (compile, killed or user) once G-03 lands.
- Profile and the celebration host have silent catches (G-17).

**Fix:**
- Use the log event `gamification_sync_failed` with no interpolation.
- Apply `runAttemptCount >= MAX_ATTEMPTS -> Result.failure()` in both branches.
- Add the gate keys.

The crashlytics-ux-auditor produces the spec and android-kotlin-architect applies it.

### G-20 — Stale comments and KDoc — LOW

In addition to 9.4:
- `ManaHubApp.kt:649` cites "ADR-002 §12" (ADR-002 has no §12) and memory `project_gamification_backend_gate_2026-07-28`, which **does not exist**.
- `SettingsScreen.kt:408-410` points to `docs/gamification-hidden-for-release.md` (the real path is `docs/hidden-features/...`) and claims "The engine keeps recording progress silently", which is false since ADR-005.
- `SettingsViewModel.kt:106` says "Default: enabled", and `initialValue = true` at `:113`.
- Anonymous-account references remain after anonymous sign-in was removed: `GamificationSyncWorker.kt:25,109`, `QuestStableIdProvider.kt:13`, `GamificationSyncManager.kt:85`.
- `ProgressionEventBus.kt:12-14` ("backfill reconcile … on next launch").

### G-21 — Web (commonMain) emitters publish into an unconsumed bus — LOW (by design)

**Evidence:** these shared use cases emit on web as well: `CompleteSurveyUseCase`, `AddCardToCollectionUseCase`, `CommitScannedCardsUseCase` and `EvaluateDeckUseCase`. Web has no engine. The bus drops the events (`DROP_OLDEST`, no subscriber), and the web repositories deliberately skip emitting (`kmp-migration-progress.md:249,414`). This is harmless, but a signed-in web user's activity never counts, while the server state that user syncs is shared with their Android devices. Document it as a known cross-platform gap before any web gamification.

### G-22 — Uncapped and client-trusted XP sources — LOW (revisit before any social or leaderboard surface)

**Evidence:**
- `GameFinished` pays 20 XP, +30 on a local win, with no cap. Games are local-only and deletable (`GameSessionRepositoryImpl.kt:210`), so trivially short games farm XP.
- `TournamentCompleted` pays 100 XP, uncapped.
- On the server, `batch_upsert_xp_transactions` inserts the client `amount`, `source_category` and `created_at` verbatim, and there are no CHECK constraints.

This is acceptable while cosmetics are self-facing only (baseline §7.4.5). **Proposed:** daily caps for GAME and TOURNAMENT in `XpConfig`, a minimum duration or turn count for game XP, and a server-side per-category amount ceiling.

### G-23 — The derived-value resolver is duplicated — LOW

**Evidence:** `AchievementEvaluator.kt:44-52,160-183` and `AchievementBackfill.kt:26-33` (plus its resolver) duplicate `QUICK_WIN_MAX_TURNS`, `COMEBACK_MAX_LIFE` and the other thresholds, and the resolver `when`.

**Fix:** extract a single `DerivedAchievementResolver(statsDao)`. The engine side stays in `:app` until the DAO is abstracted.

### G-24 — Supabase hygiene — LOW

The invariants are mostly met (section 3.1). Gaps:
- The owner policies `*_owner` are `FOR ALL` with `roles = PUBLIC` (`polroles` null), and `anon` holds table-level SELECT on all 5 tables (`has_table_privilege('anon', …, 'SELECT') = true`). RLS makes the rows invisible to anon, but invariant-style least privilege says `TO authenticated` plus `REVOKE ALL … FROM anon`.
- `get_progression_changes_since` has no client caller. `getProgressionChangesSince` in `SupabaseGamificationDataSource.kt:110-118` is unused.
- **The gamification schema and RPC migrations are not in the repo.** `app/supabase/migrations/` holds only 8 files. The DDL exists only in the live DB, so it cannot be reviewed or reproduced. backend-supabase-expert should export them.

**Invariant compliance (live DB):**

| # | Invariant | 5 tables / 11 functions |
|---|---|---|
| 1 | `search_path` on SECDEF | All 11 functions have `search_path=public` (none is SECDEF) ✓ |
| 2 | Views `security_invoker` | No views ✓ |
| 3 | `(select auth.uid())` in RLS | ✓ all 5 policies. Function bodies use bare `auth.uid()`; that is allowed, since the rule is for policies. |
| 4 | Not ALL + SELECT on one table | ✓ one ALL policy each |
| 5 | Materialized views | n/a |
| 6 | Index FK columns | ✓ `user_id` is the leading PK column on every table |
| 7 | Constraint-backed index drop | n/a |
| 8 | REVOKE PUBLIC + explicit GRANT | ✓ ACL is `postgres, authenticated, service_role` only; anon cannot execute |
| 9 | Prefer INVOKER | ✓ all INVOKER |
| 10 | `enqueue_notification` | n/a |

Additional checks:
- `get_advisors` (security) reports no lints on the gamification objects.
- The FKs to `auth.users` are `ON DELETE CASCADE`, so account deletion removes the server rows. Local rows are covered by G-02.
- ADR-008 is violated (G-01).

### G-25 — Collection imports and sync pulls never re-evaluate collection achievements — LOW

**Evidence:** `CommitImportedCardsUseCase.kt:8-10` emits nothing by design, and neither do `SyncManager` pulls. Collection DERIVED lines therefore lag until the next scan or manual add. After a restore, if the backfill already ran, they stay stale (G-05).

**Fix:** emit a zero-XP `CollectionChanged` event registered only in `COLLECTION_EVENTS`, with no ledger row and no quests. Alternatively run the DERIVED collection resolvers after an import commit.

### G-26 — The time zone is captured once at DI time — LOW

**Evidence:** `GamificationEngineKoinModule.kt:104` has `single { TimeZone.currentSystemDefault() }`. Day and week caps, quest period keys and streak days use the zone from process start. Travelling users get the wrong day boundaries until the process restarts.

**Fix:** inject a `() -> TimeZone` provider.

### G-27 — A destructive Room reset leaves gamification DataStore state behind — LOW

**Evidence:** `SyncPreferencesStore.clearAllWatermarks` clears the sync watermarks (`SyncPreferences.kt:66-78`) but not `gamificationBackfillDone`, `lastCelebratedLevel` or the equipped cosmetics. After the dev-only destructive fallback wipes Room, the backfill never re-runs.

**Fix:** use the same wipe routine as G-02.

---

## 4. New-feature integration matrix

| Feature / flow (since 07-10) | Should emit? | Currently emits | Action |
|---|---|---|---|
| Collection import/export (review queue → commit) | No XP. Should re-evaluate DERIVED | Nothing (`CommitImportedCardsUseCase`) ✓ | G-25 (zero-XP re-eval trigger) |
| Export | No | Nothing ✓ | none |
| AddCard single and "Select multiple" (shared queue) | `CardsAdded` | `CardScanned` (queue → `CommitScannedCardsUseCase`) ✗ | G-09 |
| Scanner commit | `CardScanned` | `CardScanned` ✓ | keep; split by origin (G-09) |
| AddCard deck-source mode / scanner deck atomic merge | No XP (optionally `DeckSaved` for the quest) | Nothing | optional: `DeckSaved` on the first card-list change per day |
| CardDetail add | `CardsAdded` | `CardsAdded` ✓ (double-counted first copy) | G-10 |
| Deck Studio fresh draft | Only when the deck becomes real | `DeckCreated` at blank creation ✗ | G-08 |
| Deck Wizard / Deck Builder v2 | `DeckCreated` (BUILT/WIZARD) | `DeckCreated` at shell creation (`DeckWizardViewModel.kt:1995`). Internal evaluations correctly pass `emitProgression=false` (`:1391`, `BuildWizardDeckUseCase.kt:958`, `DeckStudioViewModel.kt:1975`) ✓ | move to "real deck" (G-08) |
| Community Deck import coordinator | 0 XP; DERIVED counts only | `DeckCreated` (40 XP, 3/day) ✗ | G-08 source=COMMUNITY |
| Text deck import (DeckList/Studio) | 0 XP | `DeckCreated` ✗ | G-08 source=IMPORT |
| Deck posture pin (`posture_override`) | No | Nothing ✓ (`DeckRepositoryImpl.kt:291-388`, no emit) | none |
| Deck metadata save | `DeckSaved` (quest only) | `DeckSaved` from `updateDeck` ✓ | none |
| Deck Doctor / Analysis tab | `FeatureExplored("deck_doctor")` | ✓ via `EvaluateDeckUseCase.kt:191-198`, including recompute after add/cut; the per-day key and target 1 make repeats harmless | none |
| Daily Puzzle (`PUZZLE_ENABLED=false`) | `PuzzleSolved` | ✓ `PuzzleRepositoryImpl.kt:74-87`; only reachable via the gated Home widget; no XP while gamification is off | G-15 (hide `PUZZLE_SOLVER`) |
| Playtest | No | Nothing ✓ (no `game_sessions` write) | none |
| Draft simulator (flag off) / draft guides | Draft deck: `DeckCreated` source=DRAFT; guides: no | Deck path emits `DeckCreated` (unreachable); guides nothing ✓ | G-08 source tag |
| Competitive | No | Nothing ✓ | none |
| Friend card search | No | Nothing ✓ | none |
| Friends (accept / invite) | `FriendAdded` for both sides | Accepter only; the invite path emits nothing ✗ | G-11 |
| Trades (H4/H8, keyset drain) | `TradeCompleted` once per completed trade, both sides | On accept, receiver only ✗; trade collection apply (`UserCardRepository.applyTradeCollectionChanges`) correctly emits no `CardsAdded` ✓ | G-07 |
| Tournament overhaul | `TournamentCompleted` (+ won) | Completed ✓ exactly once (finish guard `TournamentRepositoryImpl.kt:318-329`); won always false ✗ | G-14 |
| Stats Hall of Fame | No | Nothing ✓ | none |
| Game (`player_sessions.is_local`) | `GameFinished.isLocalWin` from `is_local` | ✓ `GameSessionRepositoryImpl.kt:64-80` (isLocal = isAppUser) | G-22 caps (optional) |
| Home widget board (47ba9560) | Hubs gated and ready-certain | Gated through `flatMapLatest` ✓; `gamificationEnabled` defaults to true ✗; `QuestsReady` hero dead ✗ | G-16 |
| Account management overhaul / anonymous accounts removed | Per-account store, first-account guest merge | Device-global store, merge on every sign-in ✗ | G-02 |
| Sign-out / account deletion | Wipe or re-scope local gamification | Nothing ✗ (server cascades ✓) | G-02 |
| Remote Config kill switches / `FeatureFlags` | `kill_gamification` + compile flag | Not integrated ✗ | G-03 |

---

## 5. KMP classification

The rules come from `docs/plans/kmp-migration-plan.md` and `kmp-migration-progress.md`. Gamification UI is explicitly out of web v1 (`kmp-migration-plan.md:151`).

**Already `commonMain`, clean.** An import scan found no `android.*`, `androidx.*` or `java.*` in either module:
- `shared/core-model`: `ProgressionEvent`, `LevelCurve`, `XpConfig`, `QuestPeriod(Keys)`, all UI and domain models, `AchievementDef`, `QuestTemplate`, `Unlockable(Id)`.
- `shared/core-domain`: `AchievementCatalog`, `QuestCatalog`, `UnlockableCatalog`, the `GamificationEngine` interface, `ProgressionEventBus`, the `GamificationRepository` interface, `ClaimQuestRewardUseCase`, `GrantResult`, `QuestClaimData`.

**Still `:app`, but pure and movable as-is (no Android and no DAO):**
- `core/gamification/domain/QuestGenerator.kt` (explicitly documented as pure)
- `engine/IdempotencyKeyScoper.kt`
- `StreakTracker.advance()`, the pure transition; its `process()` uses the DAO
- `AchievementBackfill.computeBackfillRows()`

**Still `:app`, needs an abstraction first:**
- Engine: `XpGranter`, `AchievementEvaluator`, `QuestEvaluator`, `StreakTracker`, `EntitlementGranter`, `QuestReconciler`, `AchievementBackfill`, `GamificationEngineImpl`. They depend on the Room `GamificationDao`/`GamificationStatsDao`/entities, on `UserPreferencesDataStore`, and in `GamificationEngineImpl` on `java.util.concurrent.atomic.AtomicBoolean`.
- To move them: define a commonMain `GamificationStore` (the ledger/progress/quest/streak/entitlement ops) and a `GamificationStatsSource` (the DERIVED aggregates) with Room actuals, and replace `AtomicBoolean` with an `atomicfu`/`Mutex` guard.
- Web would need an IndexedDB or remote-first actual. Per the "web is online-first" rule, that would be a server-authoritative engine, which is a larger design question.

**Android-only by nature (androidMain or excluded):**
- `GamificationSyncWorker`, `QuestRotationWorker` (WorkManager)
- `SyncPreferencesStore` (DataStore; behind an interface)
- The Room entities and DAOs, `Migration38To39`
- The DI wiring in `GamificationEngineKoinModule` (uses `androidContext()`, `org.koin.androidx.workmanager`)
- `SupabaseGamificationDataSource` could move to commonMain `shared/core-data`, because supabase-kt is multiplatform.

**Presentation, for a future CMP move:**
- `ProfileScreen` tabs, `AchievementsTab`, `QuestsTab`, `RewardsTab`: use `com.mmg.manahub.R` strings, so they need migration to CMP `Res`. Otherwise they are Compose Material3 plus tokens and portable.
- `ProfileLevelRing`: no Android imports, so portable today.
- `GamificationCelebrationHost`: `androidx.activity.compose.BackHandler`, `collectAsStateWithLifecycle` and `org.koin.androidx.compose.koinViewModel`. Use the CMP back handler, `lifecycle-runtime-compose` (multiplatform) and `koin-compose-viewmodel`.
- `CosmeticRenderers`: `android.graphics.RuntimeShader` and `android.os.Build` (AGSL foil). This needs an `expect fun rememberFoilBrush(...)`: the Android actual keeps AGSL on API 33+ with the sweep fallback, and wasm uses the sweep-gradient fallback (or Skia `RuntimeEffect`, which is not verified on wasm).
- `GamificationCelebrationViewModel`, `ProfileViewModel`: `androidx.lifecycle.ViewModel` (multiplatform-capable). `ProfileViewModel` also depends on the Android `UserPreferencesDataStore` and `statsRepo`.

**Leak check:** nothing in the gamification commonMain packages imports Android or browser APIs. There is one semantic divergence (G-21).

---

## 6. Tests status

**Existing gamification suites.** JVM, `app/src/test/java/com/mmg/manahub/`:

| Suite | @Test count |
|---|---|
| `core/gamification/domain/LevelCurveTest` | 9 |
| `LevelCurveParityTest` | 2 |
| `QuestGeneratorTest` | 9 |
| `QuestPeriodKeysTest` | 8 |
| `catalog/AchievementCatalogTest` | 9 |
| `catalog/UnlockableCatalogTest` | 11 |
| `usecase/ClaimQuestRewardUseCaseTest` | 8 |
| `engine/AchievementBackfillTest` | 5 |
| `AchievementEvaluatorTest` | 14, includes `PUZZLE_SOLVER` |
| `EntitlementGranterTest` | 8 |
| `IdempotencyKeyScoperTest` | 4 |
| `QuestEvaluatorTest` | 12 |
| `QuestReconcilerTest` | 7 |
| `StreakTrackerTest` | 20 |
| `XpGranterTest` | 23 |
| `data/sync/GamificationSyncManagerTest` | 12 |
| `GamificationSyncWorkerTest` | 3 |
| `QuestRotationWorkerTest` | 2 |
| `feature/game/GameResultStripViewModelTest` | 7 |
| `feature/gamification/GamificationCelebrationViewModelTest` | 12 |
| `feature/profile/ProfileViewModelTest` | 38 |

Emitter coverage exists in `DeckRepositoryImplTest`, `GameSessionRepositoryImplTest`, `PuzzleRepositoryImplTest`, `TournamentRepositoryImplTest`, `TradesRepositoryImplTest`, `FriendRepositoryImplTest`, `AddCardToCollectionUseCaseTest` and `EvaluateDeckUseCaseTest`.

Instrumented: `Migration38To39Test` and `GamificationDaoConcurrencyTest`.

**Static staleness check.** Tests were not run, per the brief.
- Constructor signatures match current code: `GamificationSyncWorker` with 5 params including `userPreferencesDataStore`; `XpGranter(dao, clock, timeZone, dataStore)`.
- No test references deleted symbols such as `CheckAchievementsUseCase` or `QuestsReady` producers.
- No `@Ignore` in the gamification suites.
- **No compile breakage was detected by inspection; this is unverified at runtime.**

**Weak or one-sided tests:**
- `LevelCurveParityTest` only pins client constants; the SQL is not in the repo. **Verified live:** `level_for_total_xp` returns 1, 1, 2, 4, 5, 10, 20, 57, 362 for 0, 99, 100, 1702, 1703, 11106, 67135, 1e6 and 1e8. A Python replica of `LevelCurve` gives 57 and 362 for the last two, so the curves match, including beyond L100.
- `AchievementCatalogTest` asserts `size >= 36` ("~40"). It will not catch copy regressions (G-13) or duplicate rules (the RAINBOW pair). Add assertions that single-tier descriptions contain their own threshold, and that no two defs share a (resolver, threshold) pair.
- The catalog tests live in `app/src/test` although the catalogs are in `shared/core-domain` commonMain. Move them to `shared/core-domain/src/commonTest` (kotlin.test) so they also run for web.

**Missing coverage, needed before restore:**
- `GamificationEngineImpl`: stage order, per-stage isolation, `outcomes` publishing, duplicate short-circuit (G-11, G-12).
- The backend gate, which currently sits inline in `ManaHubApp.onCreate` and cannot be tested. Extract `GamificationBackendGate` and test OFF→ON→OFF with engine stop, `cancelUniqueWork` and catch-up (G-04).
- Sync pagination and watermark tests (more than 500 rows, a late-pushed old row, a partial page failure) mirroring the collection sync tests (G-01).
- Owner-scoping tests (G-02).
- `GamificationRepositoryImpl`: description mapping and filtering (G-13, G-15).
- A `GamificationStatsDao` instrumented test, especially for `commanderLocalWins` semantics and placeholder rows.
- Trade and friend emission-semantics tests (G-07, G-11).
- Home `QuestsReady` (G-16).

---

## 7. Doc rot list

| Doc | Problem | Fix |
|---|---|---|
| `docs/hidden-features/gamification-hidden-for-release.md` | Says "the engine is intentionally left running… nothing is lost" (header and "How to restore" tail). Written before ADR-005: omits the backend gate, both workers' self-abort, `cancelUniqueWork`, the ON→OFF engine gap, and the loss of non-retroactive progress. Step 4 names `defaultLayoutSignedOut/SignedIn` (they are now `DEFAULT_LAYOUT_SIGNED_OUT/IN`, `HomeViewModel.kt:1937,1949`). No mention of `FeatureFlags`/`KillSwitch`, the G-01/G-02/G-12/G-13 prerequisites, `ProfileViewModel`/`HomeUiState` defaulting to visible, or the `QuestsReady` hero. | Rewrite as a restore runbook: prerequisites (the BLOCKERs), gate model (G-03), then verification. |
| `:app core/gamification/CLAUDE.md` | Header says "Phase 0 + Phase 1 complete" (all 4 are). "~40 AchievementDef" (39). "UnlockableCatalog (21 items)" (22). "Room v39 … 140 pre-existing test failures" baseline is stale (DB is v56). `anonymous = signed-in` wording is obsolete. The sync bullet does not mention the ADR-008 non-compliance or the missing owner scoping. The events list does not mention `PuzzleSolved` or the puzzle streak type. | Update facts; add G-01 and G-02 invariants once fixed. |
| `docs/adr/ADR-002-gamification.md` | `Status: Accepted (Phase 0 in progress)`. No "amended by ADR-005" pointer next to "opt-out first-class" (`:24`). §11 describes watermarks that violate ADR-008. Anonymous-merge text (`:175, 234, 310`). No §12 exists although `ManaHubApp.kt:649` cites one. | Status → Accepted (Phases 0–4 shipped; D1 amended by ADR-005; sync amended by ADR-008 follow-up). |
| `docs/adr/ADR-005-backend-call-budget.md` D1 | "enabling later reconstructs the user's real state anyway" is inaccurate (G-05). | Correct the Consequences, or record the catch-up design. |
| `docs/adr/ADR-008-…md` Decision 2 | The "gamification scheduled behind" note has no owner, and does not mention the client-clock cursor (a worse defect than truncation). | Add G-01 details. |
| Root `CLAUDE.md` | Line "Still unpaginated: the five gamification `*_changes_since` RPCs" is accurate. Add that the cursor is client-clock based and that local state is not per-account. The telemetry section should reference the G-19 PII breadcrumb fix. | Update after the fixes. |
| `docs/gamification-feature-audit-2026-07-10.md` | §2 "engine running silently" and §4 CONTEXT_HERO claim are both stale. Count of 37 defs (39 now). | Link to this audit, or mark superseded. |
| `SettingsScreen.kt:408-410`, `SettingsViewModel.kt:106,113`, `ManaHubApp.kt:649`, `ProgressionEventBus.kt:12-14`, `GamificationSyncWorker.kt:25,109`, `QuestStableIdProvider.kt:13` | See G-20. | Code comments; android-kotlin-architect. |
| Memory `project_gamification_hidden_for_release.md` | Says "The engine was NOT disabled — it keeps recording…". Restore path points to `docs/gamification-hidden-for-release.md` (wrong path). | Update to the ADR-005 reality and link this audit. |
| Memory `project_gamification_audit_2026-07-10.md` | Premise "engine runs silently" is stale; its fix list is still open. | Point to this audit. |
| Memory `project_gamification_backend_gate_2026-07-28` | **Missing**, but referenced by `ManaHubApp.kt:649` (`project_backend_call_budget_adr005.md` exists instead). | Create it, or fix the reference. |
| Memory index `MEMORY.md` | Gamification lines ("Gamification hidden… master flag default-OFF", "Gamification audit… STREAK_* dead") omit the ADR-005 gate and the new drift. | One-line update after the fixes. |
| Supabase migrations | The gamification DDL and RPCs are not in the repo (`app/supabase/migrations/` holds 8 unrelated files). | backend-supabase-expert exports them (G-24). |

---

## Suggested restore order (owners)

1. **backend-supabase-expert:** G-01 server side (cursor columns, paged RPCs, server-set `changed_at`), G-24, and exporting the gamification migrations into the repo.
2. **android-kotlin-architect**, as one batch:
   - G-03 gate model (`FeatureFlags.Gamification`, `KillSwitch.GAMIFICATION`, `GamificationAvailability`)
   - G-04 engine stop and the `GamificationBackendGate` extraction
   - G-02 owner scoping
   - G-01 client drain
   - G-12, G-13, G-15
   - G-06
   - G-07, G-08, G-09, G-11
   - G-16, G-17, G-18
3. **Product decision on G-05**, then the catch-up implementation if (b) is chosen.
4. **crashlytics-ux-auditor** handles the G-19 spec; **android-unit-test-writer** covers the section 6 gaps; **android-edge-case-tester** does the UI pass on NeonVoid and HallowedPrint.
5. **Docs:** section 7.
