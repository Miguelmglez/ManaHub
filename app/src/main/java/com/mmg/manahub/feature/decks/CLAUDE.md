### Deck Studio (`feature/decks/presentation/DeckStudio*`)
**Suggestions tab (`FeatureFlags.Decks.DECK_STUDIO_SUGGESTIONS_TAB_ENABLED`, in
`core/FeatureFlags.kt` — NOT the stale `feature/decks/presentation/DeckFeatureFlags.kt` path this
doc used to cite) is currently `true`** — it went through a hide/re-enable cycle (hidden
2026-07-14, re-enabled for the Community/Archetype plan, hidden again 2026-07-21, re-enabled during
the Deck Wizard & Engine Rework campaign); flip to `false` to hide it again. Manual editing and
Import are unaffected either way. The tab's user-facing label is **"Analysis"** (Deck Analysis
Engine v2, 2026-08-19 — see below); the flag/enum/class names are still `SUGGESTIONS`-prefixed
(string-only rename). See `docs/hidden-features/deck-studio-suggestions.md`.

**Deck Analysis Engine v2 (2026-08-19).** Score/Role-Coverage/Findings are now ONE unified
pipeline, all deriving from the resolved strategy skeleton (RoleKey vocabulary) — replacing the
old split where `healthScore`/`roleCoverage` came from the legacy `DeckRole`-keyed `DeckScorer`
while warnings alone came from the archetype-aware `ArchetypeEvaluator` (the bug where changing
archetype/themes moved warnings but never the score). Landed ADDITIVELY, not a hard swap:
`DeckHealth.analysis: DeckAnalysis?` (new) sits alongside the untouched legacy
`DeckHealth.evaluation` (dormant — no longer read by any UI, kept compiling for the still-flagged-
off suggestion use cases below). New engine, pure `commonMain`,
`shared/core-domain/.../feature/decks/domain/engine/`:
- `CuratedStrategyCatalog.kt` — 29 curated strategies (6 pure archetypes incl. "balanced"/GENERIC +
  23 themed presets), each resolving to an (ArchetypeId, themes[, tribe]) composition validated
  against `StrategyCatalog.isValidCombination()`; `nearestFor(archetype, themes, format = null)`
  maps an (archetype, themes) pair (e.g. from inference) to the nearest catalog entry, optionally
  filtered to a `DeckFormat`'s availability (Wave 2 B4), `null` = "Custom" (unmatched legacy pin).
  Since Wave 2 B1, `CuratedStrategy.formats: Set<DeckFormat>` (not the coarser `ArchetypeFormat`) —
  see the Wave 2 block below for why.
- `DeckAnalysis.kt` — result models: `DeckAnalysis`, `PillarResult`, `PillarId` (MANA_BASE / CURVE /
  PLAN_ROLES / SYNERGY / LEGALITY), `Finding` (sealed, 18 variants, severity-tiered BLOCKER/
  WARNING/INFO), `RoleCoverageEntry`, `AnalysisWeights` (defaults 0.20/0.15/0.35/0.15/0.15,
  `planRoles` heaviest since that role table IS the score's literal explanation — reasoning as KDoc
  on the class, overridable via the existing `ScoreWeightOverrides` DataStore mechanism, extended
  not duplicated).
- `AnalysisEngine.kt` — the 5 pillar evaluators + score composition + finding budget (top 3 per
  pillar + collapsed count, BLOCKERs never collapse). `ILLEGAL_DECK_SCORE_CAP = 40` (flat cap, no
  severity gradient within P5 — reasoning as KDoc, reviewed during calibration, kept as-is).
  Calibrated 2026-08-19 against 2 realistic-density Commander fixtures (~65 real cards + 35-38 real
  lands, `DeckAnalysisEngineCalibrationTest.kt`) to a documented 65-95 "well-built, on-plan deck"
  band — the original sparse golden fixtures (`DeckAnalysisEngineGoldenTest.kt`, ~17-28 real cards
  padded to 100 with basics) score artificially low (50-64) purely from the land-count skew; that's
  expected and doesn't indicate a miscalibration — those fixtures only assert RELATIVE behavior
  (strategy-switch deltas, anti-role firing, illegal-cap), never absolute score bands.
- `usecase/EvaluateDeckUseCaseV2.kt` — thin wrapper around `AnalysisEngine.evaluate()`, called from
  inside `EvaluateDeckUseCase` (which still also runs the legacy path for the dormant `.evaluation`
  field). The v2 call is wrapped in `runCatching` (optional `crashReporter` param, appended last) —
  it is new, uncalibrated-in-production code, so a pillar-evaluator exception degrades to
  `analysis = null` instead of propagating uncaught through `DeckDoctorOrchestrator.loadAnalysis`'s
  `scope.launch` and crashing the whole app.
- UI: `CuratedStrategyPickerSheet.kt` (replaces the deleted `ArchetypePlanSheet.kt` — flat curated
  list, grouped "Core plans"/"Build-around themes", search, tribe sub-picker, Auto-detect) +
  `HealthComponents.kt`'s `PillarTile`/`RoleCoverageEntryRow`/`FindingRow` (replace the deleted
  legacy `RoleCoverageRow`/`WarningChip`). **Do not name a new component `StrategyPickerSheet`** —
  that name is already taken by an unrelated 3-axis archetype/theme/tribe picker from the Deck
  Wizard Rework plan (WS1.2), still live in `DeckWizardCommanderSteps.kt`.
- Cuts/Adds/Community/Similar-decks (Motor A/B, the "make suggestions" half of Deck Doctor) are
  UNCHANGED and stay behind their own new flag,
  `FeatureFlags.Decks.DECK_STUDIO_SUGGESTIONS_ENGINE_ENABLED` (default `false`, independent of the
  tab-level flag above) — gated both in `DeckStudioScreen.kt`'s UI and in
  `DeckDoctorOrchestrator.loadAnalysis`'s stage pipeline (zero Scryfall/Worker calls reachable from
  suggestions while off, per ADR-005). See `docs/hidden-features/deck-studio-suggestions.md`.
- Telemetry: `deck_analysis_strategy_pick`, `deck_analysis_completed` (score bucket + weakest-pillar
  id/score, ONLY on the full `loadAnalysis` pass, never `recomputeIncremental`),
  `deck_analysis_picker_abandoned`, `deck_analysis_v2_engine_failed` (non-fatal). → memory:
  `project_deck_analysis_engine_v2`
- **Wave 2 — Standard (60-card) support (2026-08-20, `docs/plans/deck-analysis-engine-v2-wave2-
  standard-plan.md`).** The engine now analyzes `DeckFormat.STANDARD` decks through the SAME
  pipeline as Commander — Standard is re-enabled in `STUDIO_FORMATS`
  (`DeckEditorComponents.kt`; Modern/Pioneer/Pauper/Legacy/Vintage stay commented out, explicit
  FUTURE DEBT below). **Design contract (B0): only TWO extension points are `DeckFormat`-keyed** —
  `CuratedStrategy.formats` (which strategies a format offers) and `SixtyFormatProfile` (a numeric
  modulation layer). Everything else (`ArchetypeData` bands, `ArchetypeSkeletonResolver`,
  `COLOR_MODULATION`, `LAND_MIX`, Karsten tables) stays keyed by the coarser `ArchetypeFormat`
  (COMMANDER/SIXTY) exactly as before — `ArchetypeData` is NOT forked per `DeckFormat`. Adding a
  future 60-card format therefore needs only: a `SixtyFormatProfile` entry, a strategy-availability
  row, uncommenting its `STUDIO_FORMATS` chip, and calibration fixtures — zero engine-shape changes.
  - **B1 — Standard v1 strategy list (15 of 29 catalog entries):** balanced, aggro, midrange,
    control, tempo, big_mana, tokens, spellslinger, reanimator, landfall, lifegain,
    plus1_counters, tribal, artifacts, vehicles. Landfall and Reanimator were ADDED beyond the
    plan's original proposal after a live Standard-metagame check (mtggoldfish, Aug 2026 snapshot)
    showed both clearly meta-real (curation rationale + full metagame snapshot: Appendix A of the
    wave2 plan doc, gitignored). CASUAL stays permissive (every non-structurally-Commander-only
    entry) — unchanged in effect from Wave 1.
  - **B2 — `SixtyFormatProfile.kt`** (sibling of `ArchetypeData`): `STANDARD =
    SixtyFormatProfile(landsDelta = 2, curveDelta = 0.3)` (Standard runs higher land counts/curves
    than the generic Modern-ish SIXTY baseline — a documented judgment call, not a hard citation).
    `roleBandScale` is reserved, unused (1.0) this wave. Applied by
    `ArchetypeSkeletonResolver.resolveWithColor`'s new `deckFormat: DeckFormat? = null` param
    (appended last, defaulted — Commander is byte-identical) only when
    `ArchetypeFormat.of(deckFormat) == SIXTY`.
  - **B3 — see the Standard-specifics paragraph above this one** (legality dispatch verified,
    `Finding.SideboardOversized`, rotation-staleness caveat).
  - **B4 — format-aware `nearestFor`:** see the `CuratedStrategyCatalog.kt` bullet above.
  - **B5 — UI:** `STUDIO_FORMATS` now offers 4 chips (Commander/Standard/Casual/Draft); the
    strategy picker filters via `availableIn(currentFormat)`.
  - **B6 — calibration:** 2 realistic-density Standard fixtures (mono-Red AGGRO scored 86/100,
    Azorius CONTROL scored 82/100 — both in the documented 65-95 band at UNCHANGED default
    weights, no retuning needed) in `DeckAnalysisEngineCalibrationTest.kt`; 3 new sparse
    SIXTY/STANDARD golden decks in `DeckAnalysisEngineGoldenTest.kt` asserting relative behavior
    only (same discipline as the Commander golden decks — never assert absolute scores on sparse
    fixtures). SYNERGY subscores recorded for the future P4 retune (A4): Standard AGGRO=57,
    CONTROL=19.
  - **Future debt (out of Wave 2, hooks left but nothing built):** Modern/Pioneer/Pauper/Legacy/
    Vintage analysis (each needs its own `SixtyFormatProfile` entry + strategy-availability row +
    `STUDIO_FORMATS` chip + calibration fixtures, blocked on the app supporting deck creation in
    those formats first); sideboard analysis beyond the size check (role coverage of the 15,
    matchup guidance); the SYNERGY (P4) pillar retune; rotation-aware legality refresh UX (respect
    ADR-005 unless real users hit stale verdicts); Deck Analysis "Detected: X" live preview while
    manually pinned (still deferred from Wave 1); the suggestions (Motor A/B) re-enable campaign.
- **Standard specifics (Wave 2 B3):** P5 already dispatched `DeckFormat.STANDARD` to
  `Card.legalityStandard` correctly since Phase 2 — verified, not fixed. New `Finding
  .SideboardOversized(count)` (WARNING) fires when a 60-card constructed deck
  (`DeckFormat.isSixtyCardConstructed`) carries more than 15 sideboard cards; `AnalysisEngine
  .evaluate()`/`EvaluateDeckUseCaseV2`/`EvaluateDeckUseCase` all gained a `sideboardCount: Int = 0`
  param (appended last) to carry it in, and `DeckDoctorOrchestrator` computes it once from
  `deckWithCards.sideboard` per `loadAnalysis` pass (cached on `AnalysisCache`, reused unchanged by
  `recomputeIncremental`). Unlike every other P5 finding, a `SideboardOversized`-only pillar result
  stays at subscore 100 (the binary 0/100 P5 subscore is now gated on BLOCKER severity specifically,
  not "any finding") — it is advisory, not construction-breaking. **Legality findings carry no
  "verified current as of right now" guarantee** — `legalityStandard` et al. are read from the
  already-cached `Card`, never a live Scryfall call; after a real Standard rotation a stale cache
  entry can report a wrong verdict until it refreshes through one of the EXISTING paths (Backend
  call budget / ADR-005 above) — per ADR-005 this pillar deliberately does NOT add a new on-demand
  refresh path to compensate. Sideboard role-coverage/matchup analysis remains FUTURE DEBT.

**Deck Builder v2 wizard (`Screen.DeckWizard`) and Discoveries v2 (`DiscoverSynergiesV2UseCase`,
identity-only clustering) are the SOLE "Build from seed" / "Browse inspirations" entry points** —
gated by `DeckFeatureFlags.DECK_BUILDER_V2_ENABLED`/`DISCOVERIES_V2_ENABLED` (currently `true`).
Their LEGACY siblings (`DECK_STUDIO_BUILD_FROM_SEED_ENABLED`/`DECK_STUDIO_BROWSE_INSPIRATIONS_ENABLED`,
and the `SeedsContent`/`BuildDeckFromSeedsUseCase`/`DeckMagicEngine.discoverSynergies` content they
gated) went through a hide-then-retire cycle: hidden 2026-07-17 when v2 landed, both entry points
temporarily hidden end-to-end 2026-07-21 (all four flags briefly `false`), then the legacy code was
DELETED OUTRIGHT (not just re-hidden) in the Deck Wizard & Engine Rework plan, Workstream 7.2
(2026-07-28) after a parity audit confirmed the current wizard fully supersedes it — see
`docs/plans/deck-wizard-rework-plan.md` §WS7 and `feature_deck_wizard_rework_ws7_retirement` memory.
`docs/hidden-features/deck-studio-build-from-seed.md`/`-inspirations.md` were deleted alongside it;
only `docs/hidden-features/deck-studio-suggestions.md` remains (still-real hide/show toggle, no
legacy sibling to retire). → memory: `project_deck_builder_v2` (consolidated; per-run detail in
`.claude/agent-memory/android-kotlin-architect/`), `project_deck_studio_temporary_hide_2026-07-21`

**The SINGLE deck create + edit surface.** Both new decks AND existing decks route here: DeckList FAB +
empty-state, Collection/Stats/Home/CardDetail deck-open, and Home → "Build deck" all navigate to
`Screen.DeckStudio.createRoute(deckId?)` (null ⇒ fresh draft). The old `CreateDeckBottomSheet` + the
`DeckViewModel.createDeck`/`showCreateDialog`/`createdDeckId` state are GONE — DeckStudio creates its own
draft. Fuses manual editing + inline Deck Doctor suggestions + seed-build + Discoveries on ONE **live deck**.
- **Format + Import live INSIDE Studio:** `DeckFormatChipRow` (empty-state + `EditDeckSheet`) → `changeFormat`
  (writes through repo + `invalidateSuggestions()`). Import via `ImportDeckUseCase` (`domain/usecase/`,
  shared extraction — writes into the live draft, renames from a parsed header; `DeckViewModel.importDeck`
  still backs the legacy DeckList import sheet). `importDeck` sets `isImporting`, which **blocks
  `onExitRequested`** (no discard/keep mid-write).
- **Discard-if-empty is gated on `createdFreshDraft`** (set true ONLY on the no-deckId create path). An
  EXISTING deck opened in Studio is NEVER auto-deleted — even if empty + default-named. (Critical data-loss
  guard added when existing decks started routing through Studio; without it, opening a real empty deck and
  backing out deleted it.) Delete completes BEFORE nav.
- **Sideboard/mainboard movement (`MovementRow`, the Sideboard section) is format-agnostic and
  ALWAYS rendered** (fixed 2026-07-22 — it used to be hidden behind `!isCommanderFormat` in
  `DeckStudioScreen.kt`'s `BuildTab` with no domain-layer backing: `moveQuantityToSideboard`/
  `moveQuantityToMainboard`/`totalCards` were never format-restricted). A Commander import can
  legitimately populate the sideboard (see the Community Deck import bullet below), so never
  reintroduce a format gate here — before gating ANY Deck Studio UI section on a format/mode flag,
  verify the ViewModel/repository underneath actually enforces that restriction; if it doesn't, the
  UI gate is stale.
- Free-text budget: **never build `BudgetConstraints` from raw `TextField` text** — raw String + last-valid +
  error flag, parse-guard in the VM. The Deck Doctor incremental `AnalysisCache`/`GapSignature`/
  `loadAnalysis`/`recomputeIncremental`/`recomputeAdds` machinery lives in `DeckDoctorOrchestrator`
  (`shared/core-domain/.../feature/decks/domain/orchestrator/`, commonMain) — `DeckStudioViewModel`
  constructs one instance per session and delegates to it; VM-internal LOGIC gates must read
  `deckDoctorOrchestrator.state.value` directly (never the merged `_uiState`) to stay race-free.
- Migrated from the legacy editor: inline `CardDetailSheet` (deck-card taps; search-result taps still nav to
  CardDetail), basic-land suggestions (`landDeltas`/`applyLandSuggestions`), stateless `WarningOverlay`
  (over-limit/color-identity/non-legendary-commander + acknowledge), deck game-stats card, playtest button.
  `CardDetailSheet`/`WarningOverlay`/`DeckFormatChipRow` are reusable composables in `presentation/components/`.
- `DeckMagicDetailScreen`/`DeckBuilderScreen.kt`/`DeckMagicDetailViewModel`/`DeckBuilderViewModel.kt`/
  `Screen.DeckDetail` — the legacy unused-fallback editor — were DELETED (not just hidden) in the
  Deck Wizard & Engine Rework plan, Workstream 7.1 (2026-07-28) after confirming zero navigation
  call sites and full feature parity with Deck Studio (commander flow, land suggestions, grouping,
  sideboard movement, playtest button, share/export all live in `DeckStudioScreen`/
  `DeckStudioViewModel`). Do not re-add this route or screen.
  **`DeckImprovementScreen`/`DeckImprovementViewModel`/`Screen.DeckImprovement` were RETIRED (D10)** — the
  Studio Suggestions tab is the sole Deck Doctor UI surface.
- **Motor B (community suggestions, Phase 4) + community seed-build (Phase 5)**: the Suggestions tab
  gains a flag-gated (`communityEngineEnabledFlow`, D4) "Popular in similar decks" section +
  "Decks like yours" carousel, entirely additive to Motor A; the seed sheet gains a flag-gated
  "Use community data" toggle that re-orders `BuildDeckFromSeedsUseCase`'s fill priority. Flag off =
  zero community UI, byte-identical to pre-Phase-4.
- → memory: `project_deck_studio`, `feedback_budget_input_free_text_pattern`,
  `project_deck_doctor_orchestrator_extraction`, `project_deck_studio_improvement_retirement`,
  `project_motor_b_community_suggestions`, `project_community_hub_seedbuild_trending`

### Incomplete / quirks
- **SetPickerViewModel**: `clearFilters()` calls `applyFilters()` to respect `restrictedSets` — do not
  assign `filteredSets = allSets`.

### Deck Doctor
Original 8 phases complete; a separate **engine-quality plan** is complete (see below), and the
**archetype-aware Community/Archetype plan** (`docs/claude-code-prompt-deck-doctor-community.md`)
Phases 1-3 are complete: Phase 1 added a new archetype/theme skeleton layer
(`ArchetypeDefinition`/`ThemeDefinition`/`ArchetypeSkeletonResolver`/`ArchetypeEvaluator`/
`ArchetypeRoleClassifier`/`InferDeckArchetypeUseCase`) sitting ADDITIVELY on top of the engine
below, in the SAME `shared/core-domain` commonMain package as the rest of it (not `:app`'s
`feature/decks/domain/engine` — that location is stale, the whole engine was promoted to
`shared/core-domain` during the KMP migration). `EvaluateDeckUseCase` resolves the deck's archetype
(`Deck.archetypeOverride`/`themesOverride` pin, else classifier inference) and only computes
archetype-aware warnings when non-GENERIC/themed — a GENERIC deck's evaluation is byte-identical to
before. Deck Studio's Suggestions header carries a "Deck plan" chip/sheet (still behind the same
`DECK_STUDIO_SUGGESTIONS_TAB_ENABLED` flag). Phase 2 added **Motor A**
(`SuggestAddsFromCollectionUseCase`, same package): the offline, always-on, collection-only add
source, now `DeckDoctorOrchestrator`'s SOLE adds source (it REPLACED, not supplemented, the since-
retired `SuggestAddsWithBudgetUseCase`). → memory: `project_archetype_engine`,
`project_deck_doctor_phase2_motor_a`.
Key invariants:
- `DeckFormat.valueOf()` must NOT be used — use `DeckFormat.entries.firstOrNull { ... } ?: STANDARD`.
- The wizard's `DeckWizardViewModel.onGenerate()` re-entrancy-guards a double-tap via a synchronous
  phase check (`state.phase != WizardPhase.REVIEW` returns early) before launching `generateJob`.
  (The legacy `DeckStudioViewModel.generateFromSeeds()` used an analogous atomic `_uiState.update {}`
  capture — that whole seed-build path was DELETED in the Deck Wizard & Engine Rework plan, WS7.2,
  2026-07-28; the wizard is now the only build entry point.)
- `DeckDoctorOrchestrator.loadAnalysis()` cancels its own `analysisJob` before relaunching (the
  incremental-analysis machinery lives there now, not inline in the ViewModel — see the Deck Studio
  section above).
- `BudgetOptimizer`/`SuggestAddsWithBudgetUseCase` (D5 dormant pipeline) were DELETED in the Deck
  Wizard & Engine Rework plan, Workstream 7.3 (2026-07-28, D-H: the budget feature is not coming
  back). **`CandidatePoolGenerator` SURVIVED** — it gained a live caller (the wizard's Scryfall
  backstop fill, WS4) before the D5 pipeline was deleted, and a second live caller since (the
  Suggestions tab's own "include outside collection" toggle, WS8.2); do not delete it. `BudgetConstraints`
  also survived (moved to its own file, `.../domain/usecase/BudgetConstraints.kt`) — still threaded
  through `DeckDoctorOrchestrator`'s API and `DeckStudioViewModel`'s free-text budget state (U7)
  even though Motor A ignores its values entirely. `Card.colors`/`colorIdentity`/`producedMana`
  (D14) are persisted as compact WUBRG-subset strings (not JSON); Motor A's pip-intensity multiplier
  and unknown-color-identity fail-closed filter (Commander only) consume them. → memory:
  `project_dormant_budget_pool` (retirement recorded), `project_card_model_produced_mana`,
  `project_deck_doctor_phase2_motor_a`, `feedback_candidatepoolgenerator_no_longer_dormant`
- **Phase 3** added a new Cloudflare Worker `cloudflare/manahub-community/` (TypeScript, Wrangler,
  vitest+miniflare) aggregating community deck data — EDHREC for Commander, Archidekt for 60-card
  (D16) — behind KV (7-day snapshot TTL) + D1 (anonymous weekly trending counters, no PII), plus a
  full `commonMain` client stack (`CommunityAggregateApi`/`CommunityAggregateRepositoryImpl`/
  `CommunityAggregate` domain model). **Not deployed** — `wrangler.toml` KV/D1 bindings are
  placeholder ids pending human-authorized provisioning. Consumed by nothing yet (Motor B/UI is
  Phase 4); androidMain Room cache + `communityEngineEnabledFlow` DataStore flag (D4) + Koin wiring
  are the remaining plumbing. → memory: `project_community_aggregate_worker`
- `CandidatePoolGenerator.legalityFragment()` returns `String?`; `DRAFT → null` (no legality restriction).
- `BudgetConstraints` has an `init` block validating finite/positive values.
- `SeedStrategy.TOKENS` test requires all 3 primary tags (TOKENS+AGGRO+TRIBAL) to beat AGGRO's tie.
- `stubRankAdds()` in tests must `candidates.take(arg<Int>(4))` to respect the `limit` parameter.
- **Engine-quality plan** (`docs/plans/deck-doctor-engine-quality-plan.md`): Phase 0 (golden + corpus
  harness), **Phase 1 (RoleClassifier v2)**, **Phase 2 (tribal granularity)**, **Phase 3 (scoring
  math)**, **Phase 4 (format correctness + construction validation)**, **Phase 5 (mana-base analysis)**,
  **Phase 6 (external pool v2)**, **Phase 7 (inference + incremental + reason pipeline)** and **Phase 8
  (layering + weight tuning)** are ALL done — the engine-quality plan is COMPLETE. Since Phase 1,
  `RoleClassifier.classify()` returns `Map<DeckRole, Float>` (confidence); use `classifyRoles()` for the
  compat `Set`. The oracle `ROLE_PATTERNS` table is a **fallback safety net only** — enrich the
  TagDictionary for durable role coverage. Never `==`-compare a `classify()` result to a `Set` (it's a Map
  — use `.keys`). Rituals stay out of RAMP. `DeckProfile.roleCounts` is Float (`quantity × confidence`).
- **Phase 3 scoring math:** `synergyScore` denominator counts IDENTITY-category tags ONLY; seed influence is
  a post-normalization fingerprint floor (≥`SEED_FLOOR` 0.6), not a pre-norm `+3f`; `curveScore` scores
  against `DeckSkeleton.targetCurve` per format (a bucket the curve does not want — `target == 0` — scores 0
  even when empty; a wanted-but-full bucket floors at `FULL_BUCKET_FLOOR` 0.1); `healthScore` penalises
  over-coverage past `RoleSlot.max` (healthy band is `[ideal, max]`, symmetric `roleFitRatio`) with a
  continuous `curveBandScore` off the per-format `DeckSkeleton.cmcBand`. **`redundancyScore` triggers only
  when `current > max`, never at `ideal`** — penalising at ideal punishes correctly-stocked staples and is
  fragile under the weighted Float `roleCounts` (oracle cross-credits nudge counts fractionally past ideal).
- **Phase 2 tribal granularity (B1):** tribe identity is PER-TRIBE and 100% RUNTIME-derived — `TribeDeriver`
  (in `RoleClassifier.kt`) builds `tribe:<subtype>` keys from the type-line subtypes after the dash (Creature/
  Tribal/Kindred only) ∪ tribes named as oracle PAYOFFS ("Elves you control", "other Elves", lords, "choose a
  creature type"). **These `tribe:` keys are NEVER persisted** (not written to `card.tags`/`userTags`/Room/the
  tagging store; the `tribe:` prefix guarantees no collision) — the commented-out per-tribe `CardTag`s stay
  commented out, no DB change. `DeckScorer.profile` adds a `tribe:<x>` fingerprint key when it clears
  `TRIBE_ABS_THRESHOLD` 8 copies **OR** `TRIBE_SHARE_THRESHOLD` 0.15 of creature copies; **when ANY tribe
  clears, the generic `CardTag.TRIBAL.key` ("tribal") is dropped from the fingerprint** (and a generic `tribal`
  SEED is not floored) so an off-tribe card carrying only the coarse `tribal` tag (a Dragon in an Elf deck) no
  longer matches. `synergyScore` injects the candidate's own `TribeDeriver.tribeKeys` as `TRIBAL`-category
  signal into BOTH numerator and denominator (consistent with the B2 identity-only denominator); `synergyDensity`
  alignment checks derived tribe keys too. Type-line subtype parsing is structural/language-neutral — it does
  NOT violate the English-only ORACLE rule.
- **Phase 7 inference + flow (B4/E4/E5/E6/E7):** the Deck Doctor seeds the profile — `EvaluateDeckUseCase`
  takes a `seedTags` param fed by `InferDeckIdentityUseCase` over the commander + the deck's top-8
  highest-identity-tag mainboard cards (the fingerprint is no longer self-referential; do NOT reintroduce
  `seedTags = emptyList()`). **A deck's persisted `archetypeOverride`/`themesOverride` pin is ALSO folded
  into `seedTags`** (`DeckDoctorOrchestrator.pinSeedTags`, via the single shared `DeckIdentitySeedTags`
  table in `feature/decks/domain/engine/` — Wizard Quality Campaign Wave 4) — every wizard-built deck is
  pinned from creation, so this is the common case, not an edge case; any future re-evaluation pass over a
  deck's identity must fold in the SAME pin the SAME way, never inference-only. → memory:
  `feedback_deck_doctor_honors_pinned_identity`. **Reasons are structured everywhere**: `MagicSuggestion.reasons` /
  `CardSuggestion.reasons` are `List<ScoreReason>` (NOT `List<String>`); engines emit `fit.reasons` and the
  UI renders `ScoreReason.label()` — never `fit.roles.map { it.name }` (raw-enum-name leak). Unresolvable
  mainboard slots surface `DeckWarning.UnresolvedCards(n)` from the **ViewModel** (the engine never sees
  unresolved slots and stays string-free); add its `label()`+`key` when touching `DeckWarning`.
  `DeckImprovementViewModel` does **incremental re-analysis**: it holds an in-memory `AnalysisCache`
  (workingMainboard / seedTags / resolvedById / gapSignature / externalPool); `onCut`/`onAdd` mutate the
  list in memory and `recomputeIncremental()` rebuilds profile/eval/cuts purely, re-fetching the external
  Scryfall pool ONLY when the `GapSignature` (queryable gap roles ∩ `queryFragment()` + colorIdentity +
  format) changes — else it reuses the cached pool via `SuggestAddsWithBudgetUseCase(externalCardsOverride=…)`
  / `BudgetSelection.externalPool`. A 99-card cut→add round-trip with an unchanged gap set must issue ZERO
  Scryfall calls. Full `loadAnalysis()` stays the fallback (missing cache / unknown re-add source).
- **Phase 8 layering (F1) — the engine now lives in `feature/decks/domain/engine/`, NOT
  `presentation/engine/`.** `DeckScorer`, `RoleClassifier`/`TribeDeriver`, `ManaBaseAnalyzer`,
  `DeckScoreModel` (DeckProfile/DeckRole/DeckSkeleton/`ScoreWeights`/`ScoreReason`/`ScoreFit`/`DeckWarning`/
  `Magic*`), `DeckImportExportHelper`, `DeckMagicEngine`, and the pure model types (`GameFormat`,
  `ManaColor`, `SeedStrategy`, `DeckEntry`, `CardSuggestion`, `PathDecision` in `DeckEngineModels.kt`) are
  in **`...decks.domain.engine`**. (The old `presentation/engine/` builder — `DeckBuilderEngine`
  + `DeckBuilderState.kt` — was **retired** with the legacy Deck Magic creator when the unified
  **Deck Studio** screen landed; see `→ memory: project_deck_studio_*`.) **Layering rule:
  `feature/decks/domain/**` must never import `...presentation...`**
  (domain→presentation is the bug Phase 8 fixed). **Phase 8 weight tuning (F2):** `ScoreWeights` is
  debug-tunable via core `ScoreWeightOverrides` (7 nullable Floats + `NONE`) persisted in
  `UserPreferencesDataStore` (primitive floats — core never imports the feature-layer `ScoreWeights`); the
  feature-layer `ScoreWeightOverrides.toScoreWeights()` maps null→default so `NONE` == `ScoreWeights()`
  (zero behavior change at defaults). `DeckImprovementViewModel` reads it and threads `weights` into
  `evaluateDeck`/`suggestAddsWithBudget`/`suggestCuts` (`EvaluateDeckUseCase` gained an optional `weights`
  param forwarded to `DeckScorer.evaluate`). The setter is the debug entry point — there is NO settings UI.
- **Phase 5 mana-base analysis (C3):** pure injectable `ManaBaseAnalyzer` (engine pkg, sibling of
  `RoleClassifier`; `DeckScorer`'s 3rd ctor param, defaulted to `ManaBaseAnalyzer()` so old 2-arg
  construction stays green). NO Room/schema/CardTag change — everything is derived from the resolved
  mainboard, no simulation/network. Pip/devotion from `Card.manaCost` (TRUE hybrid `{W/U}` counts both
  halves; **Phyrexian `{W/P}` counts as ZERO coloured demand** — payable with life, no source pressure);
  land production via priority chain (basic subtypes → oracle `Add {X}` clause → rainbow "any color" →
  fallback to the land's own `colorIdentity`) — it UNDER-counts fetch/conditional lands on purpose so the
  fixing check never HIDES a shortage. **Oracle production parsing must stay conservative (never invent an
  off-colour source):** the Add-clause regex is `\bAdd\b…` (word-anchored — `[Aa]dd` matched inside
  "Additional"), and "any color" → rainbow is recognised ONLY INSIDE an `Add` clause (matching it across
  the whole oracle invented rainbow sources from flavour like "protection from any color"). Static
  **Karsten** source table (`KARSTEN_60`/`KARSTEN_99`, 99-row at ≥33 lands) keyed by **per-colour MAX
  SINGLE-CARD pip intensity** (1/2/3+ — `maxSinglePipIntensity()`), NOT the whole-deck pip sum (the sum
  bucketed every 2-colour deck to triple-pip → impossible source need → false shortage on healthy decks)
  → `DeckWarning.ColorSourceShortage` (have<need) / `UnfixedSplash` (have==0 or <50% of need). **`dynamicLandIdeal` can only RELAX the land count** (clamped `[LAND.min,
  skeletonBase]` — never recommends MORE lands than the flat ideal); it feeds the *displayed* target of
  `TooFewLands`/`TooManyLands` while the band gate stays skeleton min/max. `evaluate(…, fullMainboard?)`
  appends the shortages only when `fullMainboard != null` (same gate as construction warnings). New
  warnings need `DeckDoctorStrings.label()` (uses `ManaColor.displayName`) + `.key` + a `strings.xml` row.
- **Phase 6 external pool v2 (E2/E3/E8):** `CandidatePoolGenerator` role queries lead with curated oracle
  tags — `DeckRole.queryFragment()` returns `otag:` (board-wipe/removal/card-advantage/ramp/counterspell/
  tutor); the legacy oracle substrings are DEMOTED to `DeckRole.fallbackQueryFragment()` and issued ONLY when
  the primary `otag:` query ERRORS or returns EMPTY (otags are not in Scryfall's formal grammar — be
  defensive; never delete the substring net). PAYOFF/SYNERGY/THREAT have no role query, so the pool adds up to
  two profile-derived queries: the top non-tribe STRATEGY fingerprint key → `otag:<x>` via the CONSTANT
  `STRATEGY_OTAGS` allowlist (no allowlist hit ⇒ no query — never guess an otag from an arbitrary key), and the
  dominant Phase-2 `tribe:<x>` key → `t:<tribe>` with the token sanitised to letters-only. `MAX_QUERIES` is 8.
  **All query fragments stay constants/allowlist — no user free text, the tribe token is `[a-z]`-sanitised.**
  E8: `AddSuggestion.priceUnknown` (priceEur == null) — the retired `BudgetOptimizer` used to EXCLUDE
  a non-free unknown-price card under an ACTIVE `maxTotalEur` cap (it cannot be costed — never
  silently 0€), keeping owned/free unknown-price ones, inert with no cap; `runningPaid`/`cardsToBuy`
  were charged only on a known `cost > 0`. That whole budget-optimizer pipeline was DELETED in the
  Deck Wizard & Engine Rework plan, WS7.3 (2026-07-28) — `priceUnknown` itself survives (still set
  by `SuggestAddsUseCase`), just with no remaining consumer of the flag. When the otag-vs-substring
  stub returns empty in a test, the fallback fires → a single role issues 2 queries.
- **Phase 4 format correctness + construction validation (D1-D4/C5):** `DeckFormat` now covers PIONEER/
  MODERN/LEGACY/VINTAGE/PAUPER/CASUAL (+`isSixtyCardConstructed`); `GameFormat→DeckFormat` is **1:1** via
  `GameFormat.toEngineDeckFormat()` (engine pkg) — never collapse non-Commander to STANDARD again.
  `DeckScorer.isLegal` reads the per-format legality field (Card now persists `legalityLegacy/Vintage/Pauper`,
  DB v40); CASUAL & DRAFT are permissive. `DeckSkeletons.forFormat` uses the shared `sixtyCardSkeleton` with
  ETERNAL/PAUPER curves. **C5 construction validation gates on a NEW optional `evaluate(profile, nonLand,
  fullMainboard? = null)` param** — null SKIPS the checks (keeps every old `evaluate(profile, nonLand)` call
  +DeckScorerTest green); `EvaluateDeckUseCase` passes the full mainboard. New `DeckWarning`s: `DeckTooSmall`,
  `TooManyCopies`, `SingletonViolation`, `OffColorIdentity` — quantity-aware, copy limit is **by card name**,
  basics exempt, CASUAL/DRAFT skip min-size. D3 multi-copy: `AddSuggestion.suggestedCopies` (≤`maxCopies −
  owned-by-name`; Commander/Draft = 1) — consumed today by the wizard build
  (`BuildDeckFromTemplateUseCase`) to write the right per-card quantity; the retired `BudgetOptimizer`
  used to charge `copies × price` against it. D4: `CandidatePoolGenerator` edhrec-pre-sorts
  ONLY for Commander; constructed pools rank by fit. When touching `DeckWarning`, add the new cases'
  `label()`+`key` in `DeckDoctorStrings` + `strings.xml`.
- **Re-run the golden ORDERING invariants after ANY scoring-component change** — a re-tune of one component
  (curve) can unmask coupling in another (redundancy) by removing slack.
- → memory: `project_deck_doctor_phase6`, `project_deck_doctor_phase6_v2`, `project_deck_doctor_general`,
  `feedback_deck_doctor_audit_2026-06-08`,
  `project_deck_doctor_phase0_harness`, `project_deck_doctor_phase1_classifier`,
  `feedback_deck_doctor_phase1_signature_ripple`, `project_deck_doctor_phase3_scoring`,
  `feedback_deck_doctor_phase3_curve_redundancy_coupling`, `project_deck_doctor_phase2_tribal`,
  `project_deck_doctor_phase7_inference_flow`, `project_deck_doctor_phase4`, `project_deck_doctor_phase5`,
  `project_deck_doctor_phase8`

**Deck Doctor Community/Archetype plan** (`docs/claude-code-prompt-deck-doctor-community.md`, gitignored/
pending deletion once this branch ships — durable record lives in memory + `docs/adr/ADR-004-*`): archetype-
aware skeletons (Phase 1) + Motor A collection suggestions (Phase 2) + the `manahub-community` Cloudflare
Worker (Phase 3, not deployed) + Motor B community suggestions (Phase 4) + Community Hub Discover/seed-build
priority/Home trending widget (Phase 5) + unified import pipeline (Phase 6) are ALL COMPLETE, entirely
flag-gated behind `communityEngineEnabledFlow` (D4, default OFF). → memory: `project_archetype_engine`,
`project_deck_doctor_phase2_motor_a`, `project_community_aggregate_worker`, `project_motor_b_community_suggestions`,
`project_community_hub_seedbuild_trending`, `project_import_unification`

**`ImportDeckCardsUseCase` card resolution (bug fix, 2026-07-22): always prefer known-id batch
resolution over fuzzy name search whenever the source already supplies an exact Scryfall id.** A
card entry carrying a pre-resolved `scryfallId` (currently only `ImportSource.FromCommunityDeck`,
via `CommunityDeckCard.scryfallId`) is resolved through ONE batched `CardRepository.warmCacheForIds`
+ `getCardsByIds` pre-warm over every known id, never a fresh per-card
`CardRepository.searchCardByName` — fuzzy name search is slower (one network round-trip per card)
and less reliable (fails/mis-resolves on split/adventure/DFC names, punctuation, ambiguous short
names) than an id the source already gave us. A known id the batch fetch can't resolve (a
retired/dead printing id) falls back to `searchCardByName` as a second attempt before the card
counts as failed — never straight to failed. Apply this rule to any FUTURE import source that
supplies an id (Moxfield, a future deckstats shape, etc.). Also: **the deck write is
resolve-then-write, never incremental-while-resolving** — a brand-new deck's ENTIRE resolved card
list is flushed in one atomic `DeckRepository.replaceAllCards` call, never N sequential
`addCardToDeck` calls, so a deck is never observable half-populated (a cancellation during
resolution, e.g. from screen navigation, leaves nothing written). An import into an EXISTING deck
keeps the original incremental per-card `addCardToDeck` merge (there IS a pre-existing card list to
preserve there). **Archidekt's excluded-from-deck zones (Maybeboard, or ANY user-named custom
category flagged `includedInDeck = false`) are recategorized as sideboard-like
(`CommunityDeckCard.excludedFromDeckCount` → `isSideboard`), never dropped** (refined 2026-07-22 —
an earlier pass dropped them to fix an over-count bug, which silently discarded real cards the user
expected to see). Archidekt lets users name a category anything, so **key every decision off the
`includedInDeck` FLAG, never a category name string/allowlist**. → memory:
`feedback_community_deck_import_reliability_and_atomicity`,
`project_community_deck_import_coordinator`

