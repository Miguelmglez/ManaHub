# ADR-002 — Gamification system

- **Status**: Accepted (Phase 0 in progress)
- **Date**: 2026-06-11
- **Supersedes**: the stateless `core/domain/usecase/achievements/CheckAchievementsUseCase`
  (removed in Phase 1)
- **Related**: ADR-001 (per-seat stats model — recorded in memory `project_per_seat_stats_model`;
  this is the first ADR committed as a file). Memory: `feedback_survey_winloss_isLocal`.

> This ADR is the durable recovery point for the multi-phase Gamification feature. At the start
> of each phase, re-read this file, `CLAUDE.md`, and the relevant `MEMORY.md` entries to restore
> state. The full spec lives in the (gitignored, transient) implementation prompt; the **decisions**
> live here.

## Context

ManaHub needs a gamification system (XP, levels, achievements, quests, streaks, unlockable
cosmetics) that:

- Works **100% offline with no account** (local-first). An account only adds sync + social
  visibility — it is never required for progression.
- **Reinforces real value**: rewards actions that make the app better for the user (logging games,
  surveys, building decks, collecting) — not engagement farming.
- Is **opt-out first-class**: a master toggle hides all gamification UI. Streaks use freeze tokens,
  never punishment. No new push notifications in v1.
- **Grandfathers** existing content: all 12 themes stay free; only NEW cosmetics are born locked.

## Decision

### 1. Event-driven engine, not direct calls

A new cross-cutting package `core/gamification/` (peer of `core/sync`, `core/tagging`). **Features
never call the engine to grant XP.** They emit domain events on a `ProgressionEventBus`
(`@Singleton MutableSharedFlow(extraBufferCapacity = 64)`); the engine collects the bus on an
application-scope coroutine (off the main thread, using `@DefaultDispatcher` from `DispatcherModule`)
and reacts.

- Emissions are wired into the **canonical write path** for each action (the repository/use-case
  that already performs the commit), **one `bus.emit(...)` after a successful commit** — never in
  ViewModels or composables. Choke points identified in Phase 0:
  `GameSessionRepositoryImpl` (game save), survey answer save, collection add/scan repository,
  `DeckRepositoryImpl` (deck save), `TournamentRepositoryImpl` (tournament finish),
  `TradesRepositoryImpl` (trade accept success), `FriendRepositoryImpl` (friend accept), plus an
  `AppOpenedToday` emission at app start.

### 2. Engine starts in `ManaHubApp`

`ManaHubApp` already owns `appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)` and collects
`authRepository.sessionState`. The engine's bus collector is launched there. Processing is dispatched
to `@DefaultDispatcher`.

### 3. Idempotency via an XP ledger

Every XP grant writes an `XpTransactionEntity` row keyed by a unique `idempotencyKey` derived from
the event (e.g. `"game:{sessionId}:result"`, `"survey:{surveyId}"`, `"quest_claim:{instanceId}"`).
A **UNIQUE index** on `idempotencyKey` rejects duplicates; re-processing or sync replay is a no-op.
This is the foundation of both crash-safety and (Phase 4) conflict-free sync — XP is a monotonic
**set-union of ledger transactions**, never last-write-wins.

### 4. Two progress families

- **Family A** — derivable from existing Room data (card counts, win totals, deck counts):
  recomputed via queries on relevant events; supports **retroactive unlocks** for new achievements.
  A one-shot backfill runs on first launch after the migration (flagged in DataStore; celebration UI
  suppressed during backfill).
- **Family B** — temporal/streak/windowed (win streaks, daily activity, all quests): **stateful
  counters** persisted in the new entities, advanced only by events.

### 5. Performance: index rules by event type

Achievement defs and quest templates are indexed by
`Map<KClass<out ProgressionEvent>, List<…>>`. An event only evaluates rules registered for its type —
never a full catalog scan.

### 6. XP & levels — single config object

`LevelCurve.xpToAdvance(level) = round(100 * level^1.5)`, with totals precomputed for levels 1..60
into an immutable list (beyond 60 computed lazily). **No level cap.** ALL XP values and caps live in
ONE `XpConfig` object for tuning. Caps are enforced by `XpGranter` summing the day's ledger per
source-category before granting.

| Action | XP | Cap / rule |
|---|---|---|
| Game logged | 20 | — |
| Local win | +30 | — |
| Survey completed | 25 | — |
| New unique card | 5 | shared daily cap: 100 XP/day from collection adds |
| Additional copy | 2 | same cap |
| Card scanned | 3 | counts toward same cap |
| Deck created | 40 | max 3 rewarded decks/day |
| Tournament completed | 100 | — |
| Tournament won | +75 | — |
| Trade completed | 50 | — |
| Friend added | 30 | max 5 rewarded/week |
| Daily quest claim | 50 | — |
| Weekly quest claim | 200 | — |
| Daily first open | 10 | once per local day |
| Achievement tier unlock | per-tier (catalog) | — |

### 7. Win/loss resolves from `is_local`, never name matching

`GameFinished.isLocalWin` MUST derive from `player_sessions.is_local = 1` seat semantics. Name
matching is forbidden (the stored seat name can diverge from UserPreferences and silently zero
win-rate). See memory `feedback_survey_winloss_isLocal`.

### 8. Data model (Room v38 → v39)

New entities (no FKs to user-data tables — catalog ids are code-side strings; multi-table writes go
through `@Transaction` DAO methods; CASCADE-regression discipline respected):

- `PlayerProgressionEntity` (singleton row id=1): totalXp, level (denormalized), updatedAt.
- `XpTransactionEntity`: id PK autogen, idempotencyKey (UNIQUE INDEX), amount, sourceCategory,
  sourceRef, createdAt.
- `AchievementProgressEntity`: achievementId PK, currentValue, tierReached, unlockedAt?,
  celebratedAt? (separates unlock from shown-celebration — fixes the old `NOW`-on-recompute bug).
- `QuestInstanceEntity` (Phase 2): id = `{templateId}:{periodKey}`, period, periodKey, target,
  progress, status, expiresAt, xpReward, `tokenReward: Int = 0` (reserved-unused).
- `StreakEntity` (Phase 2): type PK, current, longest, lastActiveDate, freezeTokens.
- `EntitlementEntity` (Phase 3): unlockableId PK, unlockedAt, source.

`MIGRATION_38_39` is purely additive (CREATE TABLE for the new tables + their indexes). Follows the
established pattern: a top-level `val MIGRATION_38_39` in its own file (so the instrumented
`MigrationTestHelper` test can reference it), registered in `DatabaseModule.addMigrations`, schema
exported to `app/schemas/`, with an instrumented migration test mirroring `Migration37To38Test`.

### 9. Deterministic seeded quest selection (Phase 2)

The period's quest set is chosen by a weighted deterministic shuffle seeded with a **stable hash**
(explicit FNV-1a over `"$stableId|$periodKey"` — NEVER `String.hashCode()`). `stableId` =
authenticated userId if signed in, else a device id generated once and persisted in DataStore (NOT
`ANDROID_ID`). Two devices on the same account independently generate identical quests for the
period — zero sync coordination. This is why **quests are not synced** (§11).

### 10. Cosmetics are 100% procedural (Phase 3)

Zero image/animation assets in v1. Titles (styled text + optional gradient Brush), badges (Material
glyph in a Canvas-drawn frame), avatar frames (Canvas rings; AGSL foil shader on API 33+ with
gradient fallback — minSdk is below 33), level-ring styles. `renderSpec` references ColorTokens into
the **active theme palette** — cosmetics must look correct in all 12 themes. Ownership in
`EntitlementEntity`; equipped selection in DataStore.

### 11. Backend & sync (Phase 4) — monotonic merge

Supabase tables mirror the local entities (RLS owner-only `(select auth.uid()) = user_id`, FKs
indexed). **Progression data is monotonic; NEVER last-write-wins on state.** XP merges as a set-union
of ledger transactions (`ON CONFLICT (user_id, idempotency_key) DO NOTHING`); achievements and
entitlements merge as unions; counters merge as `GREATEST`; `unlocked_at` merges as `LEAST` (earliest
real unlock wins). The level curve formula is **duplicated server-side** in
`batch_upsert_xp_transactions` (SQL) — this coupling is intentional and documented here so both sides
stay in sync if the curve constants change. **Quests are NOT synced in v1** (deterministically
regenerable per §9; claimed XP syncs through the ledger).

## Out of scope for v1 (do NOT build)

Token/currency shop, seasonal mastery track, Lottie animations, leaderboards, push notifications, the
Puzzle feature. Leave clean extension points (e.g. `QuestInstanceEntity.tokenReward` reserved, unused).

## Phase plan (one phase = one session = one PR)

- **Phase 0 — Engine foundations.** This ADR; `core/gamification` skeleton (events, bus, engine,
  XpGranter + caps, LevelCurve + tests, idempotency); Room v39 entities/DAOs/migration + test; event
  emissions wired into canonical use cases; master toggle in Settings; Profile hero shows level + XP
  ring (read-only). No celebrations yet.
- **Phase 1 — Achievements.** Catalog (~40 defs incl. 15 migrated with stable ids), evaluator,
  Family-A backfill, delete old `CheckAchievementsUseCase` + rewire `ProfileViewModel`, Achievements
  tab, unlock celebration overlay + queue, GameResult progression strip.
- **Phase 2 — Quests & streaks.** `QuestCatalog`, seeded deterministic generation (property test),
  `QuestRotationWorker` + app-start reconciliation, evaluator, claim/auto-claim, `StreakTracker` +
  freeze tokens, Quests tab, Home `QUESTS_HUB` + `PROGRESSION_HUB` widgets, CONTEXT_HERO claim
  suggestion.
- **Phase 3 — Unlockables.** `UnlockableCatalog` (~20 procedural items), entitlement granting on
  level-ups/achievements, equip flow (DataStore), Rewards tab, procedural renderers, level-up
  celebration.
- **Phase 4 — Backend & sync.** Supabase migrations + RPCs (10-invariant checklist + clean
  `get_advisors`), `GamificationSyncWorker`, sign-in reconciliation (anonymous progress merges INTO
  the account via the monotonic rules).

## Decision log (appended each phase)

- **2026-06-11 (Phase 0):** ADR created. Architecture, idempotency-ledger, two-families, level curve,
  v39 schema, and emission choke points fixed as above.
- **2026-06-11 (Phase 0, implementation outcomes):**
  - `XpGranter` injects `java.time.Clock`+`ZoneId` (not in the original spec) so daily/weekly cap windows
    are testable. `grantXpAtomically` gained an `updatedAt` param to keep the DAO clock-free.
  - Survey & Scanner had no repository layer; emissions live in new `CompleteSurveyUseCase` /
    `CommitScannedCardsUseCase` (domain), not the ViewModels. `AddCardToCollectionUseCase` gained a
    `source` param; `UserCardRepository.addOrIncrement` now returns `AddOutcome` to distinguish
    new-unique vs additional-copy without a second read.
  - **Known limitation:** `TournamentCompleted.isLocalWinner` is hard-coded `false` — tournaments have no
    local-seat concept (unlike `player_sessions.is_local`), so the +75 "tournament won" bonus is never
    granted (base 100 still is). Resolving requires adding a local-player flag to the tournament
    schema + setup UI. Deferred to a later phase.
  - `.gitignore` had a broad `/docs/*` rule that silenced `docs/adr/`; added `!/docs/adr/` +
    `!/docs/adr/**` negations so ADRs are actually committed (caught by the security gate).
  - Build: `assembleDebug` green; +26 new unit tests pass; 140 pre-existing master failures unchanged
    (zero regressions, verified against the master baseline).
