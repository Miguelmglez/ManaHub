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
- **2026-06-12 (Phase 1 — Achievements):** absorbed/replaced the old stateless achievement system.
  - `AchievementCatalog` = 40 `AchievementDef`s, indexed `defsByEventType` (event evaluates only its
    registered defs). 7-value `AchievementCategory` (COLLECTION/GAMES/DECKS/SURVEYS/TOURNAMENTS/SOCIAL/
    DEDICATION). The 15 legacy ids kept as STABLE string PKs; new tiered lines added under new ids
    (never collapsed an existing id). 2 secrets.
  - `AchievementEvaluator` (was a stub) persists progress and NEVER overwrites an existing `unlocked_at`
    (fixes the documented NOW-on-recompute bug); `celebrated_at` is the separate celebration gate. Tier
    XP grants once via ledger key `achievement:{id}:tier:{n}`. Family A = local Room aggregate via a new
    read-only `GamificationStatsDao` (wins use `is_local=1`, never name matching), supports retroactive
    unlocks + a one-shot backfill flagged by DataStore `gamificationBackfillDone` (backfill sets
    `celebrated_at = unlocked_at` to suppress celebration). Family B = counters (streaks, still stubbed
    until Phase 2) + remote-backed social/tournament.
  - DELETED `CheckAchievementsUseCase` + `core.domain.model.Achievement`; `ProfileViewModel` rewired to
    `GamificationRepository.observeAchievements()`. `ProgressionOutcome` gained `achievementUnlocks`;
    `engine.outcomes: SharedFlow<ProcessedOutcome>` lets the UI correlate by `GameFinished.sessionId`.
  - UI: Profile tab row (Overview + Achievements only this phase); secret achievements masked "???";
    `Screen.Profile` gained `?tab=`. Unlock celebration overlay hosted GLOBALLY in `MainActivity` root
    Box (Canvas particle burst, no assets), queued off `celebrated_at IS NULL`, skippable + `BackHandler`.
    GameResult progression strip correlated by `lastSessionId` (Room id, not online session id). All
    gamification UI hidden when the master toggle is off.
  - Design review applied: shape/spacing tokens, light-theme (HallowedPrint) contrast fixes
    (`surfaceVariant` track → `textDisabled.copy(0.25f)`; small `goldMtg` label → `textPrimary`).
  - Build: `assembleDebug` green; +35 new unit tests pass; 140 master baseline failures unchanged
    (zero regressions). Security gate PASS.
  - Carried forward: tournament "won" achievement won't unlock until `isLocalWinner` is fixed; streak
    achievements won't advance until Phase 2 fills `StreakTracker`.
- **2026-06-12 (Phase 2 — Quests & streaks):** filled the `QuestEvaluator` + `StreakTracker` stubs; no new
  Room schema (`quest_instances`/`streaks` already shipped in v39). Quests are NOT synced (only claimed XP
  via the ledger).
  - **`QuestCatalog`** (event-indexed `templatesByEventType`): 7 dailies / 6 weeklies; `QuestTemplate.advance:
    (event)->Int`. **All quests are pure monotonic INT counters** — the spec's "play games in 2 different
    modes" weekly was DROPPED (distinct-mode needs side-state the int-progress entity lacks) and replaced with
    `weekly_play_games` (target 7). Template ids are stable PK fragments (`id = "{templateId}:{periodKey}"`) —
    never renamed.
  - **Deterministic seeded generation** (§9 realised): `QuestGenerator` uses explicit `fnv1a64("$stableId|
    $periodKey")` (NEVER `String.hashCode()`) to seed `kotlin.random.Random`; selects exactly 3 with ≥2
    ACCESSIBLE + ≤1 EXPLORATION, de-prioritising the previous period's templates. `stableId` = auth userId
    (anonymous Supabase users count as signed-in) else a persisted random-UUID device id (NOT `ANDROID_ID`).
    Weekly `periodKey` = ISO **week-based-year** (`IsoFields`), not `WeekFields.of(Locale)`.
  - **Idempotent claim** via ledger key `quest_claim:{instanceId}` + `grantXpAtomically` (`XpSourceCategory.
    QUEST`); COMPLETED→CLAIMED; auto-claim on expiry. **`QuestReconciler`** (settle stale + generate missing,
    idempotent) runs from BOTH `ManaHubApp.onCreate` (local-first, any auth) and the periodic
    `QuestRotationWorker` (1-day, initial delay to next local midnight, KEEP). `QuestEvaluator` returns
    `QuestProgressDelta`s folded into `ProgressionOutcome.questProgress` (new field) → `engine.outcomes` for
    the GameResult strip.
  - **`StreakTracker`** (`daily_activity`, reacts to `AppOpenedToday`): pure `advance(existing, today)`; max 2
    freeze tokens; a gap consumes N tokens to preserve the streak (else resets to 1), regen +1 per 7-day
    multiple; never punishes. New event `ProgressionEvent.FeatureExplored(featureKey)` (0 XP) emitted from the
    Deck Doctor analysis path for the exploration daily.
  - UI: Profile **Quests tab** (streak header + claim), Home `PROGRESSION_HUB` + `QUESTS_HUB` (default-added
    for both audiences; gallery "disabled" placeholder when toggle off), `CONTEXT_HERO` top-priority
    "N quests ready to claim", GameResult quest ticks. All gamification UI hidden when the master toggle is
    off. Design review applied (Claim/widget `Role.Button` + labels, level-badge `clearAndSetSemantics`).
  - Build: `assembleDebug` green; **1544 tests / 140 failed = unchanged master baseline (zero regressions)**,
    ~99 new Phase-2 tests pass. Security gate PASS (UUID device-id, PII-free events, no new backend surface).
  - Carried forward: Phase 1's tournament "won" limitation persists. Phase 3 = unlockables/cosmetics; Phase 4
    = Supabase sync (quests excluded by design, §11).
- **2026-06-13 (Phase 3 — Unlockables & cosmetics):** realised §10 (100% procedural, zero image/animation
  assets). NO new Room schema — `EntitlementEntity` + its DAO methods already shipped in v39 (Phase 0). The 12
  themes stay FREE; no theme unlockable was added (grandfathering held).
  - **`UnlockableCatalog`** = 21 `Unlockable`s (source of truth): 8 titles (4 PlayStyle via `LevelAtLeast`
    since PlayStyle is derived not an achievement; 4 achievement-gated), 4 avatar frames (L5/10/20/35), 3
    level-ring styles, 7 badges (incl. WUBRG mana). Pure data table — color is `CosmeticColorToken` enum refs
    (resolved to `MaterialTheme.magicColors` at draw time, so cosmetics adapt to all 12 themes), never raw
    Color/hex. `UnlockableId.value` strings are persisted PKs + Phase-4 sync keys — never rename. `UnlockRule`
    sealed (`LevelAtLeast` | `AchievementUnlocked`, referencing only real `AchievementCatalog` ids).
    `AchievementDef.unlocks` left empty (unlock modeled one-way in the cosmetic catalog).
  - **`EntitlementGranter`** (engine): on each event (after streakTracker, `runCatching`-isolated) grants
    rules satisfied by the new level + just-unlocked achievements, idempotent via `insertEntitlementIfAbsent`.
    **`reconcileAll()`** (full-state evaluation, idempotent) runs once per launch from `ManaHubApp.onCreate`
    after the achievement backfill → retroactive cosmetics for existing players. Entitlements merge as a union
    in Phase 4 (§11).
  - **Equip flow** = DataStore only (`gamification_equipped_{title,badges,frame,ring}`, badges capped ≤3;
    null/empty clears). `GamificationRepository.equip*` is GUARDED by `hasEntitlement` (equipping an unowned id
    is a silent no-op). `observeRewards()`/`observeEquippedCosmetics()` added for the UI.
  - **Procedural renderers** (Compose Canvas, `CosmeticRenderers.kt`): gradient-Brush titles, badge emblems
    (CIRCLE/SHIELD/HEX + glyph; black-mana badge uses contrast outline not flat fill), avatar rings, level-ring
    restyles. **FOIL = AGSL `RuntimeShader` on API ≥33 with a sweepGradient `Brush` fallback below** (minSdk
    29; the < 33 path never loads the AGSL class); shader is `remember`-ed once with a `uResolution`-normalized
    coord (size/density-independent), only uniforms update per frame.
  - **Rewards tab** (4th Profile tab): single `LazyVerticalGrid` with full-span section headers (no nested
    same-axis scroll), stable keys = unlockable id, owned/locked/equipped states + unlock hints. Profile hero
    overlays equipped title/≤3 badges/avatar-frame/ring — purely additive (hero unchanged when nothing
    equipped or gamification off).
  - **Level-up celebration** reuses the Phase-1 global overlay host. `current` became
    `StateFlow<CelebrationItem?>` (`Achievement` | `LevelUp`); achievements take priority; level-up is driven
    by a DataStore `lastCelebratedLevel` (sentinel -1 suppresses + silent-seeds to current level in VM init,
    so existing players get no spurious burst). New `LevelUpOverlay` (ring-burst variant).
  - Build: `assembleDebug` green; +25 new unit tests pass; **1569 tests / 140 failed = unchanged master
    baseline (zero regressions)**. Design review: 1 P0 (raw `MaterialTheme.typography`) + 6 P1s all fixed.
    Security gate PASS (local-only — no backend/credentials; equip keys are UI prefs; strings.xml benign).
  - Carried forward: tournament "won" still blocked. Phase 4 = Supabase sync (entitlements/progression/
    achievements/streaks; quests excluded by design).
