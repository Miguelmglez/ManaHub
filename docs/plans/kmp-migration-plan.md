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

**Done so far (2026-06-22):** Phase 0 (spikes A/D/E; B/C deferred into Phase 2), Phase 0.5 (all 5
blockers), Phase 1 (build→KMP + DI Hilt→Koin for all 20 non-excluded islands + `:shared:core-model`/
`-common`/`-domain` foundations). Phase 2 IN PROGRESS: models `Card`/`Deck`/`DeckCard`/`UserCard` (+
sub-types) and repo interfaces `Card`/`Deck`/`UserCard`/Slice-1 moved to shared; 9 pure use cases moved.
**Cheap model/use-case extraction is now EXHAUSTED — next productive work is `:shared:core-data`.**

---

## 9. Execution playbook for Sonnet 4.6 (high effort)

This migration is now executed by **Sonnet 4.6 at high effort**, not Opus. Sonnet is fully capable here
**because the hard architectural decisions are already made** (sections 1–8 + the spike findings) — what
remains is disciplined, repetitive, verifiable transformation. This section makes every remaining step
**mechanical**: binary decisions, copy-able recipes, exact verify commands, and a deterministic backlog,
so no step needs Opus-level judgment. **The progress tracker (`kmp-migration-progress.md`) NEXT STEP
always wins** over this backlog if they disagree.

### 9.1 Operating rules (non-negotiable)
- **Roles:** ALL `.kt` + `.gradle.kts` work → delegate to the `android-kotlin-architect` agent (Sonnet).
  The main/orchestrator session does ONLY: read the tracker, delegate one slice, run the verify gauntlet,
  commit/push, update the tracker. The main session NEVER edits `.kt`.
- **Branch:** work only on `feature/kmp-migration`. NEVER merge or push to `master`.
- **Excluded, untouched (still Hilt + Android Compose):** `feature/online`, `core/voice` + in-game voice,
  `feature/scanner`. If a slice would touch them → stop and report instead.
- **Batch size: ≤ ~12 files per slice.** One slice = ONE logical code commit + ONE tracker commit.
  **Android must be GREEN at every commit** (the verify gauntlet, §9.2, passes).
- **Small-and-safe beats big-and-broken.** If a slice can't reach green within its batch, leave the tree
  at the last green commit, write the exact blocker into the tracker NEXT STEP, and STOP. Do not force.
- **No behaviour change** unless the slice explicitly is one. English-only (CLAUDE.md).

### 9.2 The verify gauntlet (run after EVERY slice, before committing)
Authoritative baseline: **1964 tests, 122 failed, 2 skipped.** (The older "110 / 140" figures in §2/§5/§7
predate the test-set compiling as one unit — ignore them; 122 is the live baseline.)

```bash
./gradlew :app:assembleDebug                              # → BUILD SUCCESSFUL
./gradlew :shared:core-model:compileKotlinWasmJs \
          :shared:core-domain:compileKotlinWasmJs          # (+ :shared:core-common / :shared:core-data if touched) → SUCCESSFUL
./gradlew :app:testDebugUnitTest                          # → 1964 tests, 122 failed, 2 skipped
# leak check — must print NOTHING but KDoc comment lines (no `import` lines):
grep -rn -E "import (androidx|android\.|java\.)" shared/*/src/commonMain
```
- **122 vs 123:** `HomeViewModelTest` (Discover `order:random`) is flaky and may flip 122↔123 — NOT a
  regression. The rule is: **the set of failing test CLASSES must not grow.** `CollectionUseCasesTest` is a
  pre-existing failure (data-driven) — already in the baseline.
- After ANY model/type move, also run an **inline-FQN grep** (see Recipe 1) — `import`-only sweeps miss
  fully-qualified refs used inline in casts/generics. This has bitten the migration 3×.

### 9.3 Decision tree — "can this type move to `commonMain`?"
Answer each. **Any YES (that you can't trivially fix) ⇒ it stays in `:app`/`androidMain` (or defer).**
1. Imports `android.*` / `androidx.*` (except libs already on CMP)? → **stays** (or interface-split, Recipe 4).
2. Imports a JVM-only `java.*`: `java.util.Locale`, `java.text.SimpleDateFormat`, `java.time.*`,
   `java.util.UUID`? → `System.currentTimeMillis()` is **fixable** (Recipe 6); the rest ⇒ **stays/defer**.
3. Has a Room annotation, or references a Room DAO/entity/`@Dao`/`@Entity` type? → **stays in `androidMain`**.
4. Has Hilt/`javax.inject` (`@Inject`/`@Singleton`/`@HiltViewModel`)? → **fixable** for use cases (Recipe 2);
   for ViewModels this is the island cutover (Recipe 3, Phase-1 work — mostly done).
5. References a type that itself still lives in `:app` and is NOT yet movable? → move that dependency FIRST,
   or **defer** this one. (Watch **same-package implicit refs** — a sibling in the same file/package using
   `Card` with no `import` line; after the move it needs an explicit `import com.mmg.manahub.core.model.Card`.)
6. Touches `@ApplicationContext`/`Context`, `R.string`/Android resources, Firebase/Crashlytics directly,
   `ProgressionEventBus`, or `core.tagging.*` analyzers? → **stays/defer** (those move in their own phase).

### 9.4 Recipes (the architect copies these — do not re-derive)

**Recipe 1 — move a PURE model to `:shared:core-model`:**
1. Run §9.3 on it. If clean (or fixable via Recipe 6), proceed.
2. `git mv app/.../core/domain/model/Foo.kt shared/core-model/src/commonMain/kotlin/com/mmg/manahub/core/model/Foo.kt`;
   change its `package` to `com.mmg.manahub.core.model`.
3. Update refs: `grep -rn "core.domain.model.Foo"` for **import lines** AND **inline FQNs**; also
   `grep -rn "\bFoo\b" app/.../<same old package dir>` for same-package implicit users now needing an import.
4. Fix any **cross-module smart-cast** breaks: a public nullable `val` from another Gradle module can't be
   smart-cast — replace `if (x.p != null) x.p.use()` with `x.p?.let { it.use() }` / local `val p = x.p` / `!!`.
5. ProGuard already covers it (`-keep class com.mmg.manahub.core.model.** { *; }`). Run §9.2.

**Recipe 2 — move a still-Hilt-owned PURE use case to `:shared:core-domain`:**
1. `git mv` into `shared/core-domain/src/commonMain/kotlin/com/mmg/manahub/core/domain/usecase/<subpkg>/`
   (preserve the sub-package name → zero `:app` import edits).
2. **Strip `@Inject` + `@Singleton`** (`javax.inject` is JVM-only, illegal in `commonMain`).
3. If it was an `object`, nothing else is needed. If it was a `class`, add a `@Provides @Singleton fun`
   for it in `app/src/main/java/com/mmg/manahub/core/di/SharedDomainUseCaseModule.kt` (ctor args resolve
   from existing `RepositoryModule` `@Binds`). Hilt keeps building it; existing Koin bridges + any
   `@HiltViewModel` consumers get the same singleton — zero behaviour change. Run §9.2.

**Recipe 3 — Hilt→Koin island cutover** (Phase-1 pattern; the 20 non-excluded islands are DONE — use only
if a new feature appears): de-Hilt the VM(s) (remove `@HiltViewModel`/`@Inject`/dagger+javax imports →
plain ctor), add `feature/<name>/di/<Name>KoinModule.kt` with `viewModel { }`, resolve at call-sites with
`koinViewModel()` (SavedStateHandle nav-args via `savedStateHandle = get()`; Activity/entry-scoped via
`koinViewModel(viewModelStoreOwner = …)`). Deps stay Hilt singletons, bridged through `ManaHubApp` into the
module as `single { thatInstance }`; promote a dep to `app/di/CoreBridgeKoinModule.kt` when ≥2 islands need
it AND remove the now-duplicate `single {}` from older islands (else runtime `DefinitionOverrideException`).
Full detail: memory `feedback_kmp_koin_island_cutover_pattern`.

**Recipe 4 — interface-split for a platform-bound return type** (the `PagingData` pattern, commit `815f169`):
when a repo interface is pure EXCEPT one method returning an Android-only type (`PagingData`, a Room row),
keep the platform method on a NEW `:app`-only interface (e.g. `CollectionPagerSource`), move the rest of the
interface to `commonMain`, and have the impl implement BOTH. The Android UI consumes the platform interface
directly (no behaviour change); the future web data source implements only the shared interface.

**Recipe 5 — Retrofit→Ktor for ONE api** (Phase-2 data layer, per-API sub-task):
do exactly one API surface per slice. Replace the Retrofit `interface` + Gson DTOs with a Ktor
`HttpClient` call + `@Serializable` kotlinx DTOs in `commonMain`; keep the Android engine
(`ktor-client-okhttp`) wiring in `androidMain`; port any rate-limit queue
(`ScryfallRequestQueue`/`ArchidektRequestQueue`) to a pure-coroutine `Mutex` impl in `commonMain`. Keep the
old Retrofit path until the new one is green, then delete it in the same slice. Verify §9.2 + that the
feature's existing tests pass (add MockEngine tests if the architect has bandwidth).

**Recipe 6 — wasm-safe fixes:** `System.currentTimeMillis()` → `kotlin.time.Clock.System.now()
.toEpochMilliseconds()` with `@OptIn(kotlin.time.ExperimentalTime::class)` (Kotlin 2.3.20 stdlib, NO new
dep, identical epoch millis). `kotlinx-collections-immutable`, `kotlinx.coroutines`,
`kotlinx.serialization` are all already common-safe.

### 9.5 Known-gotchas pre-flight (accumulated — check before each slice)
- **Inline FQN refs** survive an import-only sweep (StatsViewModel cast, batch 2a). Always grep the FQN too.
- **Same-package implicit refs** break silently and the COMPILE ERROR shows up DOWNSTREAM, not at the model
  (`AddCardRow`/`DeckBuilderState`/`OpenForTradeEntry`/`WishlistEntry` after the Card move). Add explicit imports.
- **`.label` / generic-name sed false-positives** — `CardTag.label()` vs `magicTypography.labelLarge` /
  `TagItem.label` (String field) / `link.label`. Verify the receiver type before any blanket rename.
- **Cross-module smart-cast** on public nullable `val`s is forbidden — fix per-site (Recipe 1.4).
- **`DefinitionOverrideException`** is a RUNTIME error (not compile) when two Koin modules register the same
  `single<T>` — promote-and-shrink (Recipe 3).
- A WIP-but-uncommitted tree from a dead agent **may already be green** — run §9.2 before assuming it's broken
  (batch #3 was complete-but-uncommitted; the main agent verified and committed it).

### 9.6 Remaining backlog (deterministic order — do the lowest-numbered unblocked item)
**Phase 2 — `:shared:core-data` (current phase):**
1. **Create `:shared:core-data`** KMP module (android + wasmJs source sets) wired into `settings.gradle.kts`
   + version catalog; `commonMain` depends on `:shared:core-model`/`-domain`/`-common`. Empty but building.
2. **Port the rate-limit queues** (`ScryfallRequestQueue`, `ArchidektRequestQueue`) to pure-coroutine
   `Mutex` impls in `:shared:core-data` `commonMain` (no Android deps — should be a clean lift).
3. **Retrofit→Ktor per API** (one slice each, Recipe 5), simplest first:
   `CloudflareContentApi` → `YouTubeApi` → `FriendshipService` → `SupabaseUserProfileService` →
   `ArchidektApi` → **`ScryfallApi`** (most complex: queue + interceptors + large DTO surface, last).
4. **Move repo IMPLs to `commonMain`** behind their already-shared interfaces, one repo per slice; **Room
   DAOs/entities/migrations STAY in `androidMain`** with the DAO interface exposed to `commonMain` and a
   `wasmJsMain` web data source (Supabase-remote + IndexedDB/localStorage) added later (web phase).
5. **Then the deferred use cases** unblock as their deps land (`RefreshCollectionPricesUseCase` after
   Scryfall data source; `GetDeckGameStatsUseCase` after the deck-stats DAO interface; `AutoTagCardUseCase`/
   `ComputeCardTagsUseCase` after the `core.tagging.*` engine moves; gamification use cases after
   `ProgressionEventBus` moves).
6. **Fold in web spikes B & C** here (Supabase-kt/Ktor/Coil3 on wasmJs; web auth) — they validate exactly
   the libraries this phase introduces.

**Phase 3 — `:shared:core-ui` + features to CMP** (after data layer): port MagicTheme + tokens + `core/ui/
components/*` to CMP first, then features leaf-first (settings/stats → game/decks last); androidx.compose →
CMP, `painterResource`/`R` → CMP `Res`, Coil 2→3. **Then `:webApp`** entrypoint once core-ui + one feature
compile on wasmJs. **Phase 4** platform parity (Firebase/WorkManager/camera/voice expect-actual + web
fallbacks + responsive). **Phase 5** hardening + Cloudflare Pages deploy. Eventually migrate the excluded
online/voice/scanner trio in a final wave.

### 9.7 Per-slice task template (what the orchestrator hands the architect)
> Branch `feature/kmp-migration`, last green commit `<sha>`, tree CLEAN. Do NOT touch online/voice/scanner.
> **Slice:** `<one item from §9.6, ≤ ~12 files>`. **Recipe:** `<§9.4 recipe #>`. **Decision tree:** apply
> §9.3 first; if a type fails it, defer + report instead of forcing. **Verify (must pass):** the §9.2
> gauntlet vs the 1964/122/2 baseline + leak grep + inline-FQN grep. **Finish:** commit (logical steps,
> standard ManaHub trailers), push, then update `kmp-migration-progress.md` (STATUS + NEXT STEP + CHANGE
> LOG). **Report:** what moved/deferred and why, build+test results vs baseline, commit SHAs. If you hit a
> hard blocker, STOP at the last green commit and report the exact refactor needed.
