# ManaHub → Kotlin Multiplatform Migration Plan

**Status:** Proposed (2026-06-20)
**Target:** Android (existing) + **Web (Compose Multiplatform / wasmJs)**. iOS & Desktop explicitly out of scope for now.
**DI decision:** Migrate **Hilt → Koin** across the whole codebase (KMP-native DI).
**Primary author:** main agent · **execution owner for all `.kt`:** `android-kotlin-architect`

---

## 1. Goal & scope

Ship ManaHub as a **web app** in addition to Android, sharing as much code as
possible (domain, data, ViewModels, and Compose UI via Compose Multiplatform).
No iOS/desktop targets yet, but the module structure must not preclude them.

This migration is **gated by** the **modularization blockers** already documented
(`project_modularization_blockers`). Those 5 circular-dependency blockers are compile
errors that surface the moment any `:shared:*` module is extracted, so they MUST be
resolved FIRST (Phase 0.5) — the migration does not "resolve" them, it depends on them
being resolved (3–5 days pre-work per the memory file).

---

## 2. Current-state facts (measured 2026-06-20)

- 720 main `.kt`, 110 unit-test `.kt`, 7 instrumented. Single `:app` module + `:baseline-profile`.
- Kotlin 2.3.20, AGP 9.1, Compose BOM 2026.03, Hilt 2.59.2, Room 2.8.4, Ktor 3.1.3, Supabase 3.1.4.
- 23 features + `core/`. Room DB **v41**.

**Coupling weight (files touched):**

| Surface | Files | KMP status | Action |
|---|---:|---|---|
| Hilt / DI | **~300** | Android/JVM-only | → Koin (common) — **highest-risk step** |
| Compose (androidx) | 149 | needs CMP | → Compose Multiplatform, UI to commonMain |
| Room | 64 | Android/JVM/Native — **NOT wasm** | keep on androidMain; web alt behind repo iface |
| Context (android) | 48 | Android-only | abstract behind interfaces |
| Firebase | **61** | Android/Web-JS only | expect/actual (Crashlytics no-op on web) + test infra |
| Retrofit | **~21** (incl. ArchidektApi) | not KMP | → Ktor (already present) + kotlinx-serialization |
| `androidx.paging` (PagingData in domain) | — | Android-only | abstract → common pagination model |
| WorkManager | 15 | Android-only | expect/actual scheduling |
| CameraX (scanner) | 3 | Android-only | expect/actual + web getUserMedia |
| Vosk (voice) | 2 | Android-only | expect/actual + web WebSpeech / disable |
| YouTube, Play update/review, credentials+googleid | n | Android-only | platform layer / web OAuth |

**Already KMP-portable:** coroutines, kotlinx-serialization, kotlinx-collections-immutable,
Ktor (js/wasm engine exists), Supabase-kt (verify wasmJs), DataStore (web → localStorage fallback),
Lifecycle ViewModel 2.10, Coil (upgrade 2.7 → 3.x for wasm).

---

## 3. Web-target hard constraints

1. **Room has no wasmJs/js target.** Decision: **keep Room in `androidMain`**, define
   `*Dao`/repository interfaces in `commonMain`, and provide a **web data source**
   (Supabase-remote-first + a thin IndexedDB/in-memory cache) behind the same interface.
   Rationale: avoids rewriting the stable 64-file Room layer + v41 migration chain into
   SQLDelight. (Alternative SQLDelight-everywhere rewrite is recorded as rejected — too costly,
   re-opens migrated data risk.)
2. **DataStore** wasm support is immature → `expect`/`actual` `KeyValueStore`; web actual = `localStorage`.
3. **Crashlytics** has no web product → web actual = no-op (or Sentry-web later). Analytics → GA4 web.
4. **CameraX / Vosk / YouTube / Play services / Google credential sign-in** are Android-only.
   Each becomes an `expect` capability with an Android actual and a web actual (browser API or
   graceful "unavailable on web").
5. **Supabase-kt wasmJs**: must be verified in spike (Phase 0). If unsupported on wasm, fall back
   to a thin Ktor-based Supabase REST/Realtime client in `commonMain`.

---

## 4. Target module structure

```
:shared:core-model        pure Kotlin — domain models only (no platform deps)
:shared:core-common       Result types, dispatchers (expect/actual), Crashlytics iface, KV store iface
:shared:core-domain       use cases + repository interfaces
:shared:core-data         repo impls; expect/actual data sources
                          ├─ commonMain: Ktor + Supabase + repo orchestration + CachePolicy
                          ├─ androidMain: Room DAOs/entities/migrations, DataStore
                          └─ wasmJsMain: IndexedDB/localStorage cache, web Supabase
:shared:core-ui           MagicTheme tokens, shared CMP components (core/ui/components)
:shared:feature-<name>    per feature: domain + data + ViewModel + CMP UI (commonMain)
                          platform-only features (scanner/voice) keep androidMain actuals
:androidApp               Android entrypoint, Koin Android module, Firebase/Camera/Vosk/Work actuals
:webApp                   wasmJs entrypoint (Compose for Web), Koin web module, browser actuals
:baseline-profile         unchanged (Android only)
```

Layering rule (carried from Deck Doctor Phase 8): `:shared:*:domain` must never import
presentation; `core-*` must never import `feature-*`.

---

## 5. Phased plan with agent ownership

Every phase ships compiling + green tests on **Android first**, then adds the web target.
`android-kotlin-architect` writes ALL `.kt`. `android-edge-case-tester` audits each migrated
feature. `android-unit-test-writer` keeps/ports tests. `backend-supabase-expert` owns any web
RPC/RLS/grant changes. `agent-team-orchestrator` validates each phase breakdown before kickoff.

### Phase 0 — Spikes & decisions (de-risk) — 1 sprint
- **Spike A (architect):** create empty `:shared:core-model` KMP module (android + wasmJs), move
  ~5 pure models, prove the build + Compose-Multiplatform "hello web" renders.
- **Spike B (architect):** verify Supabase-kt on wasmJs; verify Ktor wasm engine; verify Coil 3
  image load on web. Record findings; pick web Supabase path.
- **Spike C (architect + backend-supabase-expert):** web auth flow (OAuth redirect vs Android
  credential manager) — confirm Supabase web session handling.
- **Spike D (architect):** Hilt/Koin **coexistence** pattern — can both run in one app during the
  per-feature cutover? Define bridge pattern OR confirm cutover must be single-PR per DI graph.
  Without this, "incremental" is really a hidden big-bang.
- **Spike E (architect):** CMP **navigation** library decision (CMP Navigation vs Voyager vs
  Decompose) replacing the sealed `Screen.kt` + `hiltViewModel()` graph; include **web deep-link**
  (URL routing) handling. `PushDeeplinkRouter`/`JoinByDeepLinkActivity` are Android-only today.
- Output: **ADR-004** (KMP architecture + Room-on-androidMain + DI-coexistence + nav decisions) +
  **`project_kmp_spike_findings`** memory file (wasmJs library compat matrix, web auth approach,
  wasm gotchas) so later phases don't re-derive them. Go/no-go.
- **Gate:** orchestrator reviews ADR-004 + Phase 1 breakdown.

### Phase 0.5 — Resolve modularization blockers (PREREQUISITE) — 3–5 days
- **architect:** fix the 5 documented blockers (per `project_modularization_blockers`) in order:
  (1) `MtgDatabase` importing feature entities, (2) `UserPreferencesDataStore` importing
  `feature/home/presentation` types, (3+4) `core/domain` UseCases importing `R.string` + `core/data`
  repos importing feature domain models, (5) `core/util` layered deps. These are compile errors the
  instant a `:shared:*` module is extracted — must land before Phase 1.
- **Gate:** app compiles + 110 tests green with the dependency violations removed.

### Phase 1 — Foundations: build + DI + model — 1–2 sprints
- Convert build to KMP (`kotlin("multiplatform")`, android + wasmJs targets, hierarchical source sets).
- **DI cutover Hilt → Koin** (architect): define Koin modules per feature; `androidApp` starts Koin;
  delete Hilt plugin/`@Module`/`@HiltViewModel` per the **Spike-D coexistence strategy** (~300 files
  — highest-risk step). ViewModels resolved via `koinViewModel()` (KMP) instead of `hiltViewModel()`.
  Migration order = pure features first (settings/stats), platform-heavy (game/online/scanner) last.
- Extract `:shared:core-model` + `:shared:core-common` (dispatchers, Result, Crashlytics iface,
  KV-store iface with android DataStore actual + web localStorage actual).
- **edge-case-tester + unit-test-writer:** DI graph smoke test; ensure 110 unit tests still green.

### Phase 2 — Domain & data layer commonization — 2–3 sprints
- Move use cases + repository **interfaces** to `:shared:core-domain`. **Abstract `androidx.paging.PagingData`**
  out of `UserCardRepository` (domain) into a common pagination model — AndroidX Paging has no wasm target.
- `:shared:core-data`: repo impls in commonMain; **Room stays androidMain**, web cache actual in
  wasmJsMain; **Gson → kotlinx-serialization**. Replace **Retrofit → Ktor** as **per-API sub-tasks**
  (each differs in serialization/interceptors/rate-limit): ScryfallApi + ArchidektApi (complex —
  custom request queues), CloudflareContentApi, YouTubeApi, FriendshipService, SupabaseUserProfileService.
- `backend-supabase-expert`: confirm all RPCs callable from web (CORS, anon/auth grants already exist).
- Network/queue (`ScryfallRequestQueue`, rate-limit) → commonMain (pure coroutines + Mutex, portable).
- **unit-test-writer:** port repository/usecase tests to commonTest; **edge-case-tester** audits
  cache-policy + offline behavior divergence between Android(Room) and web(remote).

### Phase 3 — UI to Compose Multiplatform — 3–4 sprints
- `:shared:core-ui`: port MagicTheme (12 palettes), tokens, and `core/ui/components/*` to CMP.
  Replace androidx.compose imports with CMP; replace `painterResource`/Android resources with CMP
  `Res`. Coil 2→3.
- Migrate features screen-by-screen to commonMain CMP (149 files). Order: leaf/simple first
  (settings, stats) → complex (game, decks Deck Studio) last.
- `compose-design-reviewer` audits each migrated screen on web + Android (12 themes, NeonVoid +
  HallowedPrint contrast). Navigation → CMP-compatible navigation.
- **Platform-only UI** (scanner camera preview, voice mic, YouTube) stays Android; web shows a
  graceful "not available on web" state via expect/actual capability flags.

### Phase 4 — Platform feature parity & web polish — 2 sprints
- expect/actual for: WorkManager (web: skip/service-worker), **Firebase — 3 surfaces enumerated**:
  Crashlytics (web no-op), Analytics (GA4 web or no-op), FCM (out of scope v1); plus **test infra**:
  replace `mockkStatic(FirebaseCrashlytics::class)` (~15 test files) with an injectable interface so
  tests run in commonTest. Camera (web getUserMedia or disabled), voice (WebSpeech or disabled),
  Google sign-in (web OAuth).
- Web responsive layout pass (the app is phone-first; web needs wider breakpoints).
- `android-security-auditor`: web build secret-scan (no keys in wasm bundle), CSP, Supabase anon
  exposure review. `crashlytics-ux-auditor`: web telemetry strategy (GA4 / Sentry).

### Phase 5 — Hardening & release — 1–2 sprints
- Full regression: Android parity unchanged (compare against the 140-test pre-existing baseline),
  web smoke. Performance (wasm bundle size, first paint).
- CI: build both targets; web deploy target (Cloudflare Pages — already using Cloudflare).
- Update README + CLAUDE.md architecture section + ADR-004 final.

---

## 6. Agent assignment matrix

| Work | Owner agent |
|---|---|
| All `.kt` writing **+ `.gradle.kts` / KMP build config** (build, DI, modules, commonization, CMP UI) | `android-kotlin-architect` |
| Plan/phase breakdown review, ownership validation, memory hygiene | `agent-team-orchestrator` |
| Bug/edge-case audit per migrated feature (esp. Android vs web cache divergence) | `android-edge-case-tester` |
| Port/expand unit tests to commonTest | `android-unit-test-writer` |
| Web RPC/RLS/CORS/grants, Supabase web session | `backend-supabase-expert` |
| Migrated UI **code** review (CMP tokens, themes, a11y) — *source only, cannot verify web render* | `compose-design-reviewer` |
| New-screen web design specs (responsive breakpoints) | `mobile-game-ui-designer` |
| Secret scan of web bundle + pre-push gate | `android-security-auditor` |
| **Web visual verification** (rendered wasmJs) | manual QA / `verify` skill (not a code-review agent) |
| **Web telemetry strategy (GA4/Sentry)** | main agent (decision) → architect implements |
| Cloudflare Pages deploy, CI yaml, TOML, docs | main agent directly |

**Roster note:** `.gradle.kts` is an explicit exception to the `.kt`-delegation rule for KMP build
config (build DSL, not production app code) — assigned to the architect since KMP Gradle is the most
error-prone surface. **Capability gap:** no agent has KMP/CMP/wasmJs expertise today — preferred fix
is to **expand `android-kotlin-architect`'s prompt + CLAUDE.md with KMP rules** after Phase 0 and feed
`project_kmp_spike_findings` back into its memory (vs. spinning up a new `kmp-web-architect`, deferred
until volume justifies it). `crashlytics-ux-auditor` is **not** used for web telemetry (Crashlytics-only).

---

## 7. Top risks

1. **Supabase-kt / Ktor on wasmJs** unverified → Phase 0 spike is the gate; fallback = thin Ktor REST client.
2. **Room-not-on-web** means Android and web have *different* persistence → cache-divergence bugs;
   mitigate with shared repo-interface tests + edge-case audit (Phase 2).
3. **Hilt→Koin in 253 files** done big-bang would break everything → strictly per-feature incremental.
4. **CMP UI port (149 files)** is the longest pole; resource handling + Material3-on-CMP differences.
5. **Feature loss on web** (camera/voice/push) must be product-accepted up front.

---

## 8. Sequencing summary

Phase 0 (spikes A–E) → **0.5 (resolve modularization blockers — prerequisite)** → 1 (build+DI+model)
→ 2 (domain+data) → 3 (CMP UI) → 4 (platform parity) → 5 (release).
Android stays shippable at every phase. Web becomes shippable end of Phase 3 (core flows) → polished Phase 4–5.
