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

**~340 shared `.kt` files** in `commonMain` across 5 modules:
- `:shared:core-model` ~120 types (domain models, gamification, game, deck, trade, friend, draft,
  playtest; + `EliminationReason`, `GameSessionData`/`PlayerSaveData`/`PlayerResultData`,
  `DeckStats`, `GameModeCount`, `EliminationStats`, `SessionHistoryEntry`, `SessionDetail`,
  `CardConstants`; + `Tournament`, `TournamentMatch`, `TournamentPlayer`, `TournamentStanding`;
  + `ArchetypeMatchupData`)
- `:shared:core-domain` ~95 files (repo interfaces + use cases + gamification catalogs + deck engine;
  + `GameSessionRepository` [pure, no Room types]; + `TournamentRepository` + `MatchResultOutcome`;
  + 9 deck use cases + `BudgetOptimizer` + `CandidatePoolGenerator`;
  + `CalculateStandingsUseCase` + `RecordMatchResultUseCase`;
  + `TournamentIdCodec` + `StandingsCalculator` + `SwissEngine` + `SingleEliminationEngine`
  + `GenerateNextRoundUseCase` [all pure, domain types only, wasmJs clean])
- `:shared:core-data` ~68 files (Ktor clients, DTOs, rate-limit queues, trade use cases, repo impls;
  + `ComputeCardTagsUseCase` + `TagJsonMapper` [kotlinx-serialization, Gson fully removed from use case])
- `:shared:core-ui` ~54 files (theme, 33+ composables; + `coloredShadow` expect/actual; + `GameResultMapper`)
- `:shared:core-common` ~4 files (DispatcherProvider, KeyValueStore, CrashReporter, Page)

**Phase 1 (Hilt→Koin):** COMPLETE.
**Phase 2 (data layer):** SUBSTANTIALLY COMPLETE — Retrofit removed, 6 Ktor clients, all repo
interfaces + use cases that can be shared are shared.
**Phase 3 (UI):** SUBSTANTIALLY COMPLETE — 50+ composables in core-ui, design system fully shared.
**Phase 4 (platform parity):** IN PROGRESS — `java.time` eliminated, `@StringRes`/`R.string`
eliminated from all shared code. Tournament engine cluster fully shared (5 engine files + use case).
`ComputeCardTagsUseCase` now kotlinx-serialization (no Gson in shared code).

Test baseline: **1964 tests, 123 failed** (pre-existing), 2 skipped.

Recent commits (most recent first):
- `06ac4a0` KMP Phase 4: ComputeCardTagsUseCase Gson→kotlinx-serialization → shared core-data
- `97e665f` KMP Phase 4: tournament engine cluster + GenerateNextRoundUseCase → shared core-domain
- (prior) KMP Phase 4: Stats DAO violation + tournament use cases → core-domain
- `f8db684` KMP Phase 4: TournamentRepository → shared core-domain + CalculateStandingsUseCase fix
- `17f62a6` KMP Phase 4: GameSessionRepository → shared core-domain

### Step 4 — Remaining work (Tier 3/4 — deeper infrastructure)

Work in priority order, delegating each to `android-kotlin-architect`:

1. **Remaining composables in `:app`** — blockers per composable:
   - `ManaCurveChart` — `android.graphics.Paint`/`Typeface` → expect/actual (same pattern as
     `coloredShadow`).
   - `CardSearchSheet` — `android.app.Activity` reference (hardblocked; defer).

2. **Blocked use cases:**
   - ❌ `GetDeckGameStatsUseCase` — injects `GameSessionDao` + `SurveyAnswerDao` + `CardDao`
     directly; needs 3+ new repo methods + domain types first.
   - `EvaluatePlayerEliminationUseCase` — `Player` in core-ui → core-domain can't dep on core-ui.
   - `GetAccountNudgeUseCase` — presentation dep.
   - `ClaimQuestRewardUseCase` — GamificationDao dep.

3. **Repository interfaces still carrying Room types** — `CardRepository`, `DeckRepository`,
   `UserCardRepository`, `StatsRepository` interfaces carry entity/DAO types. Extract domain
   equivalents to core-model and move interfaces to core-domain (same pattern as GameSession +
   Tournament done 2026-06-30).

4. **EXCLUDED features** (online/voice/scanner) — deferred. Do NOT touch.

### Step 5 — Hard rules (non-negotiable)
- Work **only** on `feature/kmp-migration`. **Never** merge or push to `master`.
- **Excluded & untouched** (still Hilt + Android Compose): `feature/online`, `core/voice` + in-game
  voice, `feature/scanner`. If a slice would touch them → stop and report instead.
- **One slice = one logical code commit + one tracker commit. Android GREEN at every commit.**
- Verify gauntlet baseline: **1964 tests, 123 failed** (pre-existing; the failing-test-CLASS set must
  not grow). Leak grep over `shared/*/src/commonMain` must show no `import androidx`/`android.`/`java.`
  lines (except `java.util.UUID` → use `kotlin.uuid.Uuid`).
- **`--rerun-tasks`** on build verification — Gradle stale cache causes false `Unresolved reference`
  errors after cross-module file moves. This has happened 5+ times during this migration.
- **Small-and-safe beats big-and-broken:** if a slice can't reach green, leave the last green commit,
  write the exact blocker into the tracker NEXT STEP, and STOP.
- **Inline FQN gotcha:** after any model/type move, grep for the moved type's name as an INLINE fully-
  qualified reference (not just imports). `HomeViewModel.kt` missed this twice — import sweeps are
  insufficient. Always run: `grep -rn "TournamentEntity\|GameSessionEntity" app/src/main/java` (adapt
  to the moved type) after updating imports.

### Step 6 — Close the loop after each slice
- Commit (standard ManaHub trailers).
- Update `docs/plans/kmp-migration-progress.md`: **STATUS + NEXT STEP + Phase 4 completed section**,
  so the following session resumes cleanly. Then continue to the next item without waiting to be asked.
- Ask for general permissions upfront to avoid pausing on every tool call.

## ◀️ COPY TO HERE
