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

### Step 3 — Current state (as of 2026-06-25)

**308 shared `.kt` files** in `commonMain` across 5 modules:
- `:shared:core-model` ~105 types (domain models, gamification, game, deck, trade, friend, draft, playtest)
- `:shared:core-domain` ~70 files (repo interfaces, use cases, gamification catalogs, deck engine)
- `:shared:core-data` ~65 files (Ktor clients, DTOs, rate-limit queues, trade use cases, repo impls)
- `:shared:core-ui` ~42 files (theme, 27+ composables incl. ManaCostImage/ManaColorPicker, InlineIcons)
- `:shared:core-common` ~4 files (DispatcherProvider, KeyValueStore, CrashReporter, Page)

**Phase 1 (Hilt→Koin):** COMPLETE — 20 Koin islands, all non-excluded features.
**Phase 2 (data layer):** SUBSTANTIALLY COMPLETE — Retrofit fully removed, 6 Ktor clients, 19 repo
interfaces shared, ~75 use cases shared.
**Phase 3 (UI):** SUBSTANTIALLY COMPLETE — 42 composables in core-ui, design system fully shared.
**Phase 4 (platform parity):** LARGELY COMPLETE — `java.time` eliminated, `@StringRes`/`R.string`
eliminated from gamification + game models, Deck Doctor engine shared, all low-hanging fruit picked.

Test baseline: **1964 tests, 123 failed** (122 pre-existing + 1 noise), 0 skipped.

### Step 4 — Remaining work (Tier 3/4 — deeper infrastructure)

These are the items left. Work them in priority order, delegating each to `android-kotlin-architect`:

1. **`DeckMagicEngine.kt`** — blocked on `core.tagging.label` extension (not shared) + `@IoDispatcher`.
   Approach: extract the `CardTag.label` extension to a shared file (it maps `TagCategory` → String,
   both already shared), then strip `@IoDispatcher` and move the engine. ~2 files to share first.

2. **Remaining composables in `:app`** — each has specific blockers:
   - `CircularDistribution` — `ManaSymbolImage` (DONE), but still has `stringResource(R.string.*)`.
   - `DeckItem` — `painterResource(R.drawable.mtg_card_back)` + `SimpleDateFormat` + 6 string resources.
   - `VariantSelectorSheet`, `AddCardSheet`, `TradeSelectionSheet` — heavy string resources (6+ each).
   - `ManaCurveChart` — `android.graphics.Paint`/`Typeface` → need expect/actual Canvas.
   - `MagicBottomBar` — `Screen` sealed class + `R.drawable` icons.
   - `CardSearchSheet` — `android.app.Activity` reference.
   Approach: for string-heavy composables, inline English strings (proven pattern). For drawables,
   hoist as `Painter?` params (proven with NewsItemCard). For android.graphics, use expect/actual.

3. **Room-backed repository impls** — Card, Deck, Stats, UserCard, GameSession, Tournament repos
   have Android-only Room implementations. For KMP: the repo INTERFACES are already shared; the Room
   impls stay in `androidMain`. For web, fresh Supabase-backed impls behind the same interface go in
   `wasmJsMain`. This is a Phase 4/5 task — define the DAO-abstraction approach first.

4. **Repository interfaces with Room types** — `GameSessionRepository` and `TournamentRepository`
   use Room entities/projections in their interface signatures. Need domain model equivalents extracted
   to replace the Room types in the interface, then the interface can move to core-domain.

5. **~25 blocked use cases** — depend on Room DAOs, Firebase Crashlytics, tournament engine types,
   or `DeckMagicEngine`. Unblocked incrementally as their deps move.

6. **`ComputeCardTagsUseCase`** — blocked on Gson-based tag mapper. Replace with
   kotlinx-serialization or a manual parser.

7. **EXCLUDED features** (online/voice/scanner) — deferred per user directive. Do NOT touch.

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

### Step 6 — Close the loop after each slice
- Commit (standard ManaHub trailers).
- Update `docs/plans/kmp-migration-progress.md`: **STATUS + NEXT STEP + Phase 4 completed section**,
  so the following session resumes cleanly. Then continue to the next item without waiting to be asked.
- Ask for general permissions upfront to avoid pausing on every tool call.

## ◀️ COPY TO HERE
