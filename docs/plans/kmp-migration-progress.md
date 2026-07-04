# KMP Migration — Living Progress Tracker

**Single source of truth for "where are we / what's next".** A new session reads this +
`kmp-migration-plan.md` (master plan: decisions §2, rules §3, Android debt §4, web roadmap §5)
and resumes automatically.

Branch: `feature/kmp-migration` · All Android/shared `.kt` → `android-kotlin-architect` ·
all web-target `.kt` → `kmp-web-fullstack-dev` · wasm/library gotchas → memory
`project_kmp_spike_findings`.

> This file was compacted 2026-07-04 (was 2469 lines). The full slice-by-slice history
> (every commit, verification detail, and closure rationale from 2026-06-20 → 2026-07-04) is in
> the git history of this file — do not re-derive it.

---

## How to resume
1. Read STATUS + NEXT STEP below.
2. `git branch --show-current` = `feature/kmp-migration`; check for uncommitted WIP (a dead agent's
   tree may already be green — run the gauntlet on it before discarding anything).
3. Execute NEXT STEP per the plan's §3 operating rules; verify; commit; update this file.

**Verify gauntlet** (after every slice; details in plan §3):
`:app:assembleDebug` green · touched `:shared:*:compileKotlinWasmJs` green ·
`:app:testDebugUnitTest` vs floor **1967 / 118 failed / 2 skipped** (failing-CLASS set must not
grow; 18 pre-existing failing classes are documented: online×2, voice, scanner, push×2,
collection/sync×5, auth, tagging×2, trades×4) · commonMain leak grep
`grep -rnP "import (androidx\.(?!compose)|android\.|java\.)" shared/*/src/commonMain` empty ·
inline-FQN + same-package-implicit grep after any type move · `--rerun-tasks` after cross-module
moves · `:shared:<m>:clean` if the wasmJs incremental cache corrupts after source deletion.

**Hard exclusions (untouched, still Hilt + Android Compose):** `feature/online`,
`core/voice` + in-game voice, `feature/scanner`, `core/nearby`. Never merge/push to `master`
without the user.

---

## STATUS (2026-07-04)

**Android-on-KMP: COMPLETE with a short debt tail. Web: not started (greenfield by design).**

- ✅ **Phase 0 / 0.5 / 1 / 2 / 3 / 4 / 5-Slice-1 — DONE** (2026-06-20 → 2026-07-01). Highlights:
  5 `:shared:core-*` modules, **357 commonMain files**; Retrofit+Gson fully removed (6 Ktor
  clients); all repo interfaces + models + movable use cases + design system + ~54 composables
  shared; `java.time`/`@StringRes`/`R.string` eliminated from shared; regression audit root-caused
  every failing test (floor 1967/118/2); signed release + R8 verified green post-split.
- ✅ **Hilt→Koin cutover Batches 1–6 — DONE** (2026-07-03/04). Koin owns ALL non-excluded feature
  DI (~40 VMs, 20 islands) + the 7 non-excluded workers (`koin-androidx-workmanager`,
  `DelegatingWorkerFactory(Koin + Hilt)` for the excluded scanner worker). 10 feature Hilt modules +
  the `SharedDomainUseCaseModule` hub deleted. Remaining Hilt = 12 infra/bridge modules + 4 excluded
  modules + `KoinToHiltBridgeModule` (reverse bridge for what workers/excluded still consume).
  Batch 7 (infra→Koin) deliberately DEFERRED to the excluded-trio final wave (plan §4 A5).
- ⚠️ **Batch 6 caveat (open):** the Koin WorkerFactory ordering was verified against library source
  but **never on-device** — validate all workers execute next time an emulator is available (A1).
- ✅ **2026-07-01 audit remediation — executed**: P0.1 dead use cases deleted, P0.3 grep fixed,
  P1.1 CrashReporter for the 7 migration-candidate classes, P1.3 orphan VMs → Koin, P1.4 first
  26 commonTest tests, P2.1 `DeckBuilderState` package fix, `GetAccountNudgeUseCase` +
  `ImportCommunityDeckUseCase` unblocked + moved. P1.2 (Room impls move) RESERVED → paired with
  web W3. P0.2 (web KV stub) → web W1. The audit doc is deleted; its verdict + web critical path
  live in the master plan §1/§5.
- 📋 **User decisions on record:** full-Koin end-state (Hilt dies with the excluded wave) ·
  Room stays Android, web is Supabase-remote-first/online-first · `:shared:feature-*` modules
  dropped for now · CI deferred until web exists · `DeckMagicDetailScreen` kept · Android-first
  sequencing satisfied — web phase unblocked.

## NEXT STEP

1. **A2 — `android-edge-case-tester` pass** on Tournament finish-and-advance, GameSession/Stats,
   Deck Doctor (last open Android hardening item; fixes → architect).
2. **A1 — on-device WorkerFactory validation** (first time a device/emulator is available).
3. **Web phase start (user greenlight):** hand to `kmp-web-fullstack-dev` at plan §5 **W0**
   (wasmJs runtime smoke spike: supabase-kt + Ktor + Coil3 + localStorage) → W1 `:webApp` scaffold.
   Confirm the web-MVP feature set (plan §5 proposal) with the user before W4.

## LOG (compact; full detail in git history of this file)

- 2026-06-20: Phase 0 spikes A/D/E; Phase 0.5 blockers; Phase 1 foundation repaired.
- 2026-06-21/22: 20 Koin islands; `:shared:core-{model,common,domain,data}`; Retrofit→Ktor ×6.
- 2026-06-23/24: Phase 2 data sharing substantially complete; `:shared:core-ui` + CMP 1.11 + Coil 3.
- 2026-06-25 → 07-01: Phase 4 (55+ use cases, gamification/game/tournament domain, catalogs,
  composable sweep, kotlinx-datetime, CMP Res POC); Phase 5 Slice 1 regression audit; release/R8 +
  security + baseline-profile audits clean; README refresh.
- 2026-07-01: external audit → remediation P0.1/P0.3/P1.1/P1.3/P1.4 + quick wins; floor 1967/118/2.
- 2026-07-03/04: Hilt→Koin cutover batches 1–6 (features, use-case hub, workers); reverse bridge
  `KoinToHiltBridgeModule`; minor debt closed (P2.1, NudgeTrigger/GetAccountNudge).
- 2026-07-04: docs consolidated — master plan rewritten, audit/map/next-session-prompt deleted,
  this tracker compacted.
