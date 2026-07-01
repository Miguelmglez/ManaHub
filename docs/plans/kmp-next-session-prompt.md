# KMP migration — next-session kickoff prompt (Sonnet 4.6, high effort)

Paste the block below as the **first message** of the next session (model = **Sonnet 4.6, high effort**).
It is self-contained. **Status as of 2026-07-01: Phase 4 (Android-first scope) is CLOSED** — see
`docs/plans/kmp-migration-progress.md` STATUS/NEXT STEP. The next session needs a **human decision**
between two legitimate next moves before it can pick a mechanical backlog again: (A) Phase 5
(hardening/release, Android-only) or (B) hand off to `kmp-web-fullstack-dev` to start the web phase
(`:webApp`, wasmJs actuals behind the now-fully-pure repo interfaces). This prompt orients the session
either way — do not assume which one to start without checking with the user first.

---

## ▶️ COPY FROM HERE

You are resuming the **ManaHub Kotlin Multiplatform migration** (Android + Web / wasmJs). You are the
**orchestrator**; all `.kt` and `.gradle.kts` Android/shared work is delegated to the
**`android-kotlin-architect`** agent; all web-target (`wasmJsMain`) work is delegated to
**`kmp-web-fullstack-dev`**. Do not edit `.kt` yourself.

### Step 1 — Orient (read these, in order)
1. `docs/plans/kmp-migration-progress.md` — the living tracker. Read **STATUS** + **NEXT STEP** —
   Phase 4 (Android-first scope) closed 2026-07-01; NEXT STEP explains the two legitimate next moves
   (Phase 5 vs. web-phase handoff) and why neither is presumed.
2. `docs/plans/kmp-migration-plan.md` — **§9 "Execution playbook for Sonnet 4.6"** is your operating
   contract for any further Android/shared slices. Follow it verbatim (§9.1 rules, §9.2 verify gauntlet,
   §9.3 decision tree, §9.4 recipes, §9.5 gotchas, §9.6 backlog, §9.7 task template). §5/§8 define
   Phase 5 and the web-phase scope if that's the direction chosen.
3. `docs/plans/kmp-library-and-filesystem-map.md` — library/source-set fates + target module tree.
4. Memory (loaded automatically): `project_kmp_migration_progress`, `project_kmp_spike_findings`,
   `project_modularization_blockers`.

### Step 0 — Ask the user first
Before doing any work, confirm with the user which direction to take: **Phase 5 hardening/release**
(Android-only — full regression, CI, README/CLAUDE.md update) or **start the web phase** (delegate to
`kmp-web-fullstack-dev`: `:webApp` entrypoint, wasmJs `actual` data sources, Firebase/WorkManager/
camera/voice web actuals, web responsive layout, web security/telemetry review). Both are valid; this
tracker does not choose for you. If the user has already stated a preference in this conversation, that
overrides this step.

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

**~345 shared `.kt` files** in `commonMain` across 5 modules:
- `:shared:core-model` ~122 types (domain models, gamification, game, deck, trade, friend, draft,
  playtest; + `EliminationReason`, `GameSessionData`/`PlayerSaveData`/`PlayerResultData`,
  `DeckStats`, `GameModeCount`, `EliminationStats`, `SessionHistoryEntry`, `SessionDetail`,
  `CardConstants`; + `Tournament`/`TournamentMatch`/`TournamentPlayer`/`TournamentStanding`;
  + `ArchetypeMatchupData`; + `PlayerState` interface [life/poison/commanderDamage — decouples
  EvaluatePlayerEliminationUseCase from core-ui's Player])
- `:shared:core-domain` ~100 files (repo interfaces + use cases + gamification catalogs + deck engine;
  + `GameSessionRepository`; + `TournamentRepository` + `MatchResultOutcome`;
  + 9 deck use cases + `BudgetOptimizer` + `CandidatePoolGenerator`;
  + `CalculateStandingsUseCase` + `RecordMatchResultUseCase` + `GenerateNextRoundUseCase`;
  + `TournamentIdCodec` + `StandingsCalculator` + `SwissEngine` + `SingleEliminationEngine`;
  + `EvaluatePlayerEliminationUseCase` [PlayerState param, @Inject stripped, GameModule @Provides];
  + `ClaimQuestRewardUseCase` [GamificationRepository + Clock, no DAO dep];
  + `QuestClaimData` + `GrantResult` domain models)
- `:shared:core-data` ~68 files (Ktor clients, DTOs, rate-limit queues, trade use cases, repo impls;
  + `ComputeCardTagsUseCase` + `TagJsonMapper` [kotlinx-serialization, Gson fully removed from use case])
- `:shared:core-ui` ~56 files (theme, 35+ composables; + `coloredShadow` expect/actual;
  + `GameResultMapper`; + `ManaCurveChart` [android.graphics.Paint/Typeface removed, CMP TextMeasurer])
- `:shared:core-common` ~4 files (DispatcherProvider, KeyValueStore, CrashReporter, Page)

**Phase 1 (Hilt→Koin):** COMPLETE.
**Phase 2 (data layer):** SUBSTANTIALLY COMPLETE — Retrofit removed, 6 Ktor clients, all repo
interfaces + use cases that can be shared are shared.
**Phase 3 (UI):** SUBSTANTIALLY COMPLETE — 55+ composables in core-ui, design system fully shared.
**Phase 4 (platform parity):** IN PROGRESS — `java.time` eliminated, `@StringRes`/`R.string`
eliminated from all shared code. Tournament engine cluster fully shared. `ComputeCardTagsUseCase`
kotlinx-serialization. All previously-blocked use cases (EvaluatePlayerElimination + ClaimQuestReward)
now resolved.

Test baseline: **1964 tests, 123 failed** (pre-existing), 2 skipped.

Recent commits (most recent first):
- `4042d23` docs(kmp): update progress tracker — Phase 4 session 2026-06-30 (slices B+C)
- `750ce9a` KMP Phase 4: ManaCurveChart → shared core-ui commonMain
- `d46b2d2` KMP Phase 4: ClaimQuestRewardUseCase → shared core-domain
- `0afbad9` KMP Phase 4: EvaluatePlayerEliminationUseCase → shared core-domain
- `b86279c` docs(kmp): update next-session prompt — state as of 2026-06-30
- `17f62a6` KMP Phase 4: GameSessionRepository → shared core-domain

### Step 4 — Remaining work

**Everything in the old Tier-3/4 Android-first punch list is now resolved as of 2026-07-01** —
`GetDeckGameStatsUseCase`, all 6 repo interfaces (Card/Deck/Stats/UserCard/GameSession/Tournament,
zero Room types left in any signature), the CMP Res POC, and the remaining-composables survey are all
DONE or closed. `CardSearchSheet` (Activity dep) and the online/voice/scanner-excluded composables
remain genuine, permanent blockers (not TODOs). See `kmp-migration-progress.md` item 5's 2026-07-01
entry for the survey that closed the last open bullet (Room-backed repo impls / DAO-abstraction).

**Permanently-blocked items (documented, not actionable without disproportionate refactors — do not
re-attempt without new information):** `GetAccountNudgeUseCase` (presentation-layer `NudgeTrigger`
dep), `ImportCommunityDeckUseCase` (Firebase Crashlytics dep), `UpdateTradeCollectionUseCase` (Room DAO
dep), the 3 `core/tagging/` files (`java.util.Locale` / Android `DataStore`), the excluded
online/voice/scanner trio.

**What's actually next is a direction choice, not a mechanical backlog item** — see Step 0 above:
Phase 5 (Android hardening/release) or the web phase (delegate to `kmp-web-fullstack-dev`). Once the
user picks, follow that phase's definition in `kmp-migration-plan.md` §5 (Phase 5) or §3/§4 (web-phase
scope), using this file's Step 5/6 hard rules either way.

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
