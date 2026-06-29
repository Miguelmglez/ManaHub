# KMP migration — next-session kickoff prompt (Sonnet 4.6, high effort)

Paste the block below as the **first message** of the next session (model = **Sonnet 4.6, high effort**).
It is self-contained: it tells the session to resume the KMP migration from the tracker, follow the
mechanical playbook, and execute the next slice. Everything it references is committed on
`feature/kmp-migration`.

---

## ▶️ COPY FROM HERE

You are resuming the **ManaHub Kotlin Multiplatform migration** (Android + Web / wasmJs). You are the
**orchestrator**; all `.kt` and `.gradle.kts` work is delegated to the **`android-kotlin-architect`**
agent. Do not edit `.kt` yourself.

### Step 1 — Orient (read these, in order)
1. `docs/plans/kmp-migration-progress.md` — the living tracker. Read **STATUS** + **NEXT STEP**.
2. `docs/plans/kmp-migration-plan.md` — **§9 "Execution playbook for Sonnet 4.6"** is your operating
   contract. Follow it verbatim (§9.1 rules, §9.2 verify gauntlet, §9.3 decision tree, §9.4 recipes,
   §9.5 gotchas, §9.6 backlog, §9.7 task template).
3. `docs/plans/kmp-library-and-filesystem-map.md` — library/source-set fates + target module tree.
4. Memory (loaded automatically): `project_kmp_migration_progress`, `project_kmp_spike_findings`,
   `project_modularization_blockers`.

### Step 2 — Verify the tree before doing anything
```bash
git branch --show-current          # must be feature/kmp-migration
git status --short                 # expect clean
git log --oneline -5               # local HEAD
```
- If there is **uncommitted WIP** (a prior agent may have died mid-slice): run the **§9.2 verify
  gauntlet** on it FIRST — it may already be green. If green → commit it as its slice + update the
  tracker. If red → repair via the architect, or `git stash` and report. Do NOT discard work blindly.

### Step 3 — Current state (as of 2026-06-30)

**~330 shared `.kt` files** in `commonMain` across 5 modules:
- `:shared:core-model` ~115 types (domain models, gamification, game, deck, trade, friend, draft,
  playtest; + `EliminationReason`, `GameSessionData`/`PlayerSaveData`/`PlayerResultData`,
  `DeckStats`, `GameModeCount`, `EliminationStats`, `SessionHistoryEntry`, `SessionDetail`,
  `CardConstants`)
- `:shared:core-domain` ~85 files (repo interfaces + use cases + gamification catalogs + deck engine;
  + `GameSessionRepository` [pure, no Room types], + 9 deck use cases + `BudgetOptimizer` +
  `CandidatePoolGenerator`)
- `:shared:core-data` ~65 files (Ktor clients, DTOs, rate-limit queues, trade use cases, repo impls)
- `:shared:core-ui` ~50 files (theme, 33+ composables; + `FloatingDelta`, `GameModeSelector`,
  `SharedComponents`, `AddCardSheet`, `TradeSelectionSheet`, `VariantSelectorSheet`;
  + `coloredShadow` expect/actual; + `GameResultMapper`)
- `:shared:core-common` ~4 files (DispatcherProvider, KeyValueStore, CrashReporter, Page)

**Phase 1 (Hilt→Koin):** COMPLETE.
**Phase 2 (data layer):** SUBSTANTIALLY COMPLETE — Retrofit removed, 6 Ktor clients, 20 repo
interfaces shared (incl. `GameSessionRepository` now pure in core-domain), ~85 use cases shared.
**Phase 3 (UI):** SUBSTANTIALLY COMPLETE — 33+ composables in core-ui, design system fully shared.
**Phase 4 (platform parity):** IN PROGRESS — `java.time` eliminated, `@StringRes`/`R.string`
eliminated from all shared code, Deck Doctor engine shared, `GameSessionRepository` domain projections
extracted and interface moved to core-domain. Remaining: `TournamentRepository`, blocked use cases,
remaining composables.

Test baseline: **1964 tests, 122 failed** (pre-existing), 2 skipped.

Recent commits (most recent first):
- `17f62a6` KMP Phase 4: GameSessionRepository → shared core-domain
- `b49d68d` docs(kmp): update progress tracker
- `1328a6a` KMP Phase 4: 9 deck use cases + BudgetOptimizer + CandidatePoolGenerator → core-domain
- `996b0ff` KMP Phase 4: AddCardSheet/TradeSelectionSheet/VariantSelectorSheet + coloredShadow expect/actual
- `ac787c6` KMP Phase 4: FloatingDelta/GameModeSelector/SharedComponents/CardConstants → shared

### Step 4 — Remaining work (Tier 3/4 — deeper infrastructure)

Work in priority order, delegating each to `android-kotlin-architect`:

1. **`TournamentRepository` domain projection extraction** — same pattern as `GameSessionRepository`
   (just completed). Interface is in `:app` and uses `TournamentEntity`, `TournamentMatchEntity`,
   `TournamentPlayerEntity`, `TournamentStanding` (all Room types). Extract pure domain equivalents
   to `core-model`, move interface to `core-domain`. This unblocks tournament use cases.
   Files to read first:
   - `app/.../feature/tournament/domain/repository/TournamentRepository.kt`
   - `app/.../core/data/local/entity/TournamentEntity.kt` (+ Match, Player, Standing variants)
   - Callers: `TournamentViewModel`, `GenerateNextRoundUseCase`, `CalculateStandingsUseCase`,
     `RecordMatchResultUseCase`

2. **`DeckMagicEngine.kt`** — blocked on `core.tagging.label` extension (not shared) + `@IoDispatcher`.
   Extract `CardTag.label` extension to shared file (maps `TagCategory` → String, both shared), then
   strip `@IoDispatcher` and move the engine.

3. **Remaining composables in `:app`** — blockers per composable:
   - `CircularDistribution` — still has `stringResource(R.string.*)` (inline English, proven pattern).
   - `DeckItem` — `painterResource(R.drawable.mtg_card_back)` (hoist as `Painter?` param) +
     `SimpleDateFormat` (replace with pure Kotlin date arithmetic) + string resources (inline).
   - `ManaCurveChart` — `android.graphics.Paint`/`Typeface` → expect/actual (same pattern as
     `coloredShadow` which was completed this session).
   - `MagicBottomBar` — `Screen` sealed class (`:app`) + `R.drawable` icons (hoist as params).
   - `CardSearchSheet` — `android.app.Activity` reference (hardblocked; defer).

4. **`StatsViewModel` DAO-direct violation** — imports `GameSessionDao` directly (bypasses
   `GameSessionRepository`). Route `observeFavoriteMode()` through the repo interface.
   File: `app/.../feature/stats/presentation/StatsViewModel.kt` line ~133.

5. **~20 blocked use cases** — `GetDeckGameStatsUseCase` (Room DAOs), tournament use cases
   (unblocked by item 1), `ClaimQuestRewardUseCase` (GamificationDao), `ComputeCardTagsUseCase`
   (Gson tag mapper — replace with kotlinx-serialization or manual parser).

6. **EXCLUDED features** (online/voice/scanner) — deferred. Do NOT touch.

### Step 5 — Hard rules (non-negotiable)
- Work **only** on `feature/kmp-migration`. **Never** merge or push to `master`.
- **Excluded & untouched** (still Hilt + Android Compose): `feature/online`, `core/voice` + in-game
  voice, `feature/scanner`. If a slice would touch them → stop and report instead.
- **One slice = one logical code commit + one tracker commit. Android GREEN at every commit.**
- Verify gauntlet baseline: **1964 tests, 122 failed** (pre-existing; the failing-test-CLASS set must
  not grow). Leak grep over `shared/*/src/commonMain` must show no `import androidx`/`android.`/`java.`
  lines (except `java.util.UUID` → use `kotlin.uuid.Uuid`).
- **`--rerun-tasks`** on build verification — Gradle stale cache causes false `Unresolved reference`
  errors after cross-module file moves. This has happened 5+ times during this migration.
- **Small-and-safe beats big-and-broken:** if a slice can't reach green, leave the last green commit,
  write the exact blocker into the tracker NEXT STEP, and STOP.

### Step 6 — Close the loop after each slice
- Commit (standard ManaHub trailers).
- Update `docs/plans/kmp-migration-progress.md`: **STATUS + NEXT STEP + Phase 4 completed section**,
  so the following session resumes cleanly. Then continue to the next item without waiting to be asked.
- Ask for general permissions upfront to avoid pausing on every tool call.

## ◀️ COPY TO HERE
