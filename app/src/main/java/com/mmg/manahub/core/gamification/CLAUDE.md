### Gamification (`core/gamification/`, multi-phase — Phase 0 + Phase 1 complete)
Cross-cutting XP/levels/achievements/quests/streaks/cosmetics engine. **Local-first** (works 100%
offline; account only adds Phase-4 sync). The durable design doc is `docs/adr/ADR-002-gamification.md`
— **read it + the memory files before any gamification work.** Must-know:
- **One gate: `GamificationAvailability` (`:shared:core-domain`) = `FeatureFlags.Gamification.ENABLED`
  (release gate, false while hidden) && !`KillSwitch.GAMIFICATION` (`kill_gamification`, fail-open) &&
  the user opt-out pref (default opted in). Fails closed on read errors.** Every UI/VM/worker consumes it,
  never the raw pref. `GamificationBackendGate` (commonMain, started from `ManaHubApp`) owns the backend
  (ADR-005 D1): while AVAILABLE the engine collects, quest rotation is scheduled, the idempotent catch-up
  (DERIVED backfill incl. tier XP → `reconcileAll` → quest reconcile) runs on EVERY OFF→ON transition, and
  `AppOpenedToday` is emitted once per local date (at enable + each app foreground); any other state cancels
  the engine job and every work name. There is no one-shot backfill flag any more. Progress that is not
  retroactive (per-event XP, counters, streaks, quests) is lost while off — accepted (D2). **Do not
  "restore" silent recording.**
- **The local store is owned by one account** (`gamification_owner_user_id`): null → claim + guest merge
  (`reconcileOnSignIn`); same → `sync`; other → `GamificationLocalStore.wipe()` (6 tables in one
  transaction + account-scoped DataStore keys) then re-own + full pull. Account deletion wipes and clears
  the owner; sign-out never wipes. The sync worker skips unless the signed-in user owns the store.
- **Features never call the engine.** They emit a `ProgressionEvent` on `ProgressionEventBus` at the
  canonical write path (repository/use-case, after a successful commit — never a ViewModel/composable);
  the engine collects the bus while the gate keeps it started and processes on the default dispatcher.
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
  are STABLE PKs — never rename. Family A = local Room aggregate (retroactive; backfill re-runs on every
  enable); Family B = COUNTER for streaks + remote-backed social/
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
  no-repeat-yesterday; `stableId` = auth userId else a persisted random-UUID device id
  (**not `ANDROID_ID`**); weekly key = ISO **week-based-year** (`IsoFields`, not `WeekFields.of(Locale)`).
  **Claim is idempotent** via ledger key `quest_claim:{instanceId}` + `grantXpAtomically`; auto-claims on
  expiry. `QuestReconciler` (settle stale + generate missing, idempotent) runs from BOTH the gate's catch-up
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
  hooked after streakTracker; `reconcileAll()` (full-state catch-up) runs in the gate's catch-up for
  retroactive cosmetics. Equip = DataStore only; `equip*` repo methods are **guarded
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
  `LevelCurveParityTest`). Android: **NO Room schema change**. **Sync is keyset-paged (G-01, ADR-008):**
  every pull drains a `get_*_page` RPC (cap 500) from a SERVER cursor (`server_seq` for the ledger,
  `(changed_at, pk)` for achievements/entitlements/streaks) via `drainFromCursor` (`:shared:core-data`
  `CursorSync.kt`), persisting the cursor (`gam_cursor_*` DataStore keys) after each APPLIED page. Pushes go
  in slices ≤500 (`pushInSlices`); the ledger id-watermark advances per confirmed slice, then only over the
  contiguous ids this cycle PULLED (`advancePastPulledRows`) — never `MAX(id)`, which skipped a local grant
  landing mid-pull. The legacy `gam_sync_ms_*` watermark is reset once (full paged pull). The 3 small tables
  are pushed in FULL each cycle (monotonic merge); progression is recomputed from the ledger sum (direct
  SET, NOT `grantXpAtomically`); `get_player_progression_page` is intentionally not called.
  `reconcileOnSignIn` merges a guest store INTO the account that claims it. Upload DTOs **omit
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
- **Engine (restore P4):** stages run XP → streak → achievements → quests → entitlements. `XpGranter.grant`
  is tri-state (`Applied`/`Duplicate`/`NoXp`); a `Duplicate` (replayed ledger key) skips COUNTER achievements
  and quests. `STREAK_*` = max(stored, daily-activity streak). DERIVED values come from ONE
  `DerivedAchievementResolver`. Catalog entries carry `CatalogAvailability` (`TOURNAMENT_WIN` +
  `title_tournament_champion` unavailable until a local tournament seat exists; `PUZZLE_SOLVER` follows
  `FeatureFlags.Puzzle`): unavailable entries are hidden, never evaluated, generated, backfilled or granted.
  Catalog copy/threshold invariants live in `shared/core-domain` commonTest.
- → memory: `project_gamification_phase0`, `project_gamification_phase1`,
  `project_gamification_phase1_chunkB_ui`, `project_gamification_phase2`, `project_gamification_phase3`,
  `project_gamification_phase4`, `feedback_gamification_xp_idempotency`,
  `feedback_achievement_unlockedat_persistence`, `feedback_gamification_celebration_ui`

