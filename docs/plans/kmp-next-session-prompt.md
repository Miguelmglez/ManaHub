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
   `feedback_kmp_koin_island_cutover_pattern`, `project_modularization_blockers`.

### Step 2 — Verify the tree before doing anything
```bash
git branch --show-current          # must be feature/kmp-migration
git status --short                 # expect clean
git log --oneline -1               # local HEAD
git log --oneline -1 origin/feature/kmp-migration   # must match local
```
- If there is **uncommitted WIP** (a prior agent may have died mid-slice): run the **§9.2 verify
  gauntlet** on it FIRST — it may already be green. If green → commit it as its slice + update the
  tracker. If red → repair via the architect, or `git stash` and report. Do NOT discard work blindly.

### Step 3 — Execute the next slice
- The authoritative target is the tracker's **NEXT STEP**. As of the last session that is:
  **stand up `:shared:core-data`** — backlog **§9.6 item 1**: create the KMP module
  (`android` + `wasmJs` source sets), wire it into `settings.gradle.kts` + the version catalog,
  make `commonMain` depend on `:shared:core-model` / `:shared:core-domain` / `:shared:core-common`,
  and confirm it builds **empty** (no code moved yet). Keep it a ≤ ~12-file slice.
- Then proceed down §9.6 in order: rate-limit queues → Retrofit→Ktor per API (simplest first,
  `ScryfallApi` last) → repo impls to `commonMain` (Room stays `androidMain`).
- Hand the architect the **§9.7 per-slice task template**, filled in with: branch, last green commit,
  the exact slice, the relevant §9.4 recipe, the §9.3 decision tree, the §9.2 gauntlet, and the
  finish/commit/report instructions.

### Step 4 — Hard rules (non-negotiable, from §9.1)
- Work **only** on `feature/kmp-migration`. **Never** merge or push to `master`.
- **Excluded & untouched** (still Hilt + Android Compose): `feature/online`, `core/voice` + in-game
  voice, `feature/scanner`. If a slice would touch them → stop and report instead.
- **One slice = one logical code commit + one tracker commit. Android GREEN at every commit.**
- Verify gauntlet baseline: **1964 tests, 122 failed, 2 skipped** (`HomeViewModelTest` flaky → 123 is
  fine; the failing-test-CLASS set must not grow; `CollectionUseCasesTest` is a pre-existing failure).
  Leak grep over `shared/*/src/commonMain` must show no `import androidx`/`android.`/`java.` lines.
- After every model/type move, also run the **inline-FQN grep** (§9.4 Recipe 1) — import-only sweeps
  miss FQNs used inline in casts/generics.
- **Small-and-safe beats big-and-broken:** if a slice can't reach green in its batch, leave the last
  green commit, write the exact blocker into the tracker NEXT STEP, and STOP.

### Step 5 — Close the loop after each slice
- Commit (standard ManaHub trailers) + `git push origin feature/kmp-migration`.
- Update `docs/plans/kmp-migration-progress.md`: **STATUS + NEXT STEP + CHANGE LOG**, so the following
  session resumes cleanly. Then continue to the next §9.6 item without waiting to be asked.

## ◀️ COPY TO HERE
