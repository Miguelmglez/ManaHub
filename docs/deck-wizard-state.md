# Deck Wizard — State & Decisions (living document)

**Purpose.** The single durable record of what the Deck Wizard is, why it is built the way it is, and
where it stands. Written for the next campaign (60-card formats) to start from context, not from
archaeology. Keep it concise: decisions, contracts, numbers, open items. Execution logs belong in the
gitignored progress tracker, not here.

**Last updated:** 2026-09-08 (plan authored, implementation not started).
**Owning plan:** `docs/plans/deck-wizard-commander-plan.md` (gitignored; deleted when the campaign
ships — this file survives).

---

## 1. Status

| Item | State |
|---|---|
| Feature flag `FeatureFlags.Decks.DECK_BUILDER_V2_ENABLED` | `false` (D16 — stays off until Phase 8) |
| Formats reachable in this wave | `COMMANDER`, `COMMANDER_CASUAL` |
| Formats deferred | `CASUAL`, `STANDARD`, `PIONEER`, `MODERN`, `LEGACY`, `VINTAGE`, `PAUPER` (§7) |
| Engine used by the wizard | today: legacy Motor A (`DeckScorer.fit`); target: Deck Analysis Engine v3 primitives + `AnalysisEngine.evaluate` verification |
| Campaign phase | P0 done (incl. 0.5); P1 done (contracts only); P2 gate CLOSED (Run 4); P3 DONE (Run 5); P4 DONE (Run 6); P5 DONE (Run 7); P6 DONE (Runs 8-10) |

Phase log (fill one line per gate): `P0 done 2026-09-08 (0.1/0.2/0.3/0.5/E1/E2/E3/E4/E5) · P1 done 2026-09-08 (1.1/1.2/1.3, contracts only) · P2 gate CLOSED 2026-09-09 (Run 4 closed the land-fill/reconstruction/thin/atomicity test items Run 3 deferred — see progress tracker) · P3 DONE 2026-09-09 (Run 4: 3.1 F10 fix + 3.4 search-plumbing; Run 5: 3.2 UI + 3.3 StructuredCardSearch + lockedCriteria + VM wiring + tests + compose-design-reviewer pass — see progress tracker) · P4 DONE 2026-09-09 (Run 6: RecommendCommanderStrategiesUseCase + single-select STRATEGY step UI/VM wiring + DeriveCommanderStrategiesUseCase retirement + tests + compose-design-reviewer pass — see progress tracker) · P5 DONE 2026-09-09 (Run 7: PlanSectionsStepContent replaces MANUAL_ADDS for Commander, DeckAnalysisPipeline-only attribution, selectedPosture F3 gap closed, onAddSeed/onRemoveSeed hardened, ownedAvailabilityBySection, tests + compose-design-reviewer pass — see progress tracker) · P6 DONE 2026-09-09 (Run 8: write-atomicity + nav-arg plumbing; Run 9: BuildCommanderDeckUseCase wired into onGenerate, D12/D13 write path, persistence round-trip proof; Run 10: Result/Generating/Review UI for Commander, Studio "Rebuild with the Wizard" CTA + confirm dialog, pop-back nav, item 8 verified by code trace, compose-design-reviewer + android-edge-case-tester passes with fixes applied — see progress tracker Run 10; one flagged-not-fully-closed item: persist()'s 4-call write is not yet one Room transaction, mitigated via a cancel-blocking guard, low real risk while DECK_BUILDER_V2_ENABLED stays false) · P7 — · P8 —`

---

## 2. Why the wizard was rebuilt (context the 60-card wave must not lose)

Two campaigns preceded this one:

1. **Deck Engine Unification (2026-07)** made the wizard build with the Doctor's own suggestion engine
   (Motor A) so the Doctor could not contradict the wizard. It worked — for the engine of that time.
2. **Deck Analysis Engine v2 → v3 (2026-08)** replaced the Doctor's scoring with a new pipeline
   (`AnalysisEngine`: 5 pillars, `RoleKey` bands, `SynergyGraph` producer→payoff axes, prototype macro
   resolver — `docs/adr/ADR-007-deck-analysis-engine-v3.md`). The suggestion engine (cuts/adds) was
   deleted; the analysis became read-only sections + "Browse for X".

Nothing re-pointed the wizard at v3, so the wizard was again building with one objective and being
graded by another: tag-fingerprint synergy vs. graph coverage, greedy category fill vs. ideal-weighted
bands, `SuggestionCategory` ids vs. `CardSection` ids, a 3-axis picker vs. the curated catalog. The
user-visible result: "the app built me a deck and then told me half of it was off-plan".

**The rule this document exists to protect:** the wizard has no scoring, classification, categorisation
or query logic of its own. Everything it targets, shows or reports comes from the same engine functions
the Studio Analysis tab runs. If a future change needs a "wizard-only" heuristic, that is the smell.

---

## 3. Architecture contract

```
Commander formats:  COMMANDER_PICK → STRATEGY → PLAN_SECTIONS → REVIEW → GENERATING → RESULT
                    (format + optional deckId arrive by navigation; no format step)

Build target   = CommanderPlanResolver(format, commander, StrategyPick, identity)
                 → ResolvedArchetypeSkeleton (ALWAYS non-null; Custom = generic baseline)
                 → target axes (themes' axes ∪ commander's produced/consumed axes ∪ tribe)
                 → curve targets, land band, mana-fix band
                 == exactly what AnalysisEngine.evaluate resolves for the same pin

Placement      = PlacementScorer.marginalGain: role gain (P3 bands, ideal-weighted, 0 at max,
                 negative for anti-roles) · axis gain (P4 producers/payoffs toward axis ideals)
                 · curve gain (P2 band + shape) · power tie-break · pip feasibility
                 · optional community prior (≤ ×1.15, owned cards, flag-gated)
                 Filler floor: no role gain and no axis gain ⇒ not placed ⇒ gap.

Lands          = LandTargetResolver (shared with Studio) → owned non-basics in identity
                 (LAND_MIX cap, Karsten-safe) → basics (mainboard + commander pips, Phyrexian = 0)
                 → bounded Karsten rebalance via ManaBaseAnalyzer.

Verification   = DeckAnalysisPipeline.analyze (the ONE analysis entry point, shared by
                 DeckDoctorOrchestrator, the wizard and the harness) → refine ≤ 8 swaps while
                 totalScore strictly increases → DeckAnalysis stored in the result.

Persistence    = one atomic write: cards (source WIZARD for engine-placed + commander, USER for
                 manual adds), commanderCardId/coverCardId, name, pin (archetype, posture, themes,
                 tribe) via CuratedStrategy.toPin, strategyLocked (true for curated, false for Custom).

UI reuse       = CardSectionRow / SectionSearchQuery.toAdvancedQuery / CardSearchSheet (sections step),
                 CuratedStrategyPickerSheet rows (strategy step), AdvancedSearchSheet with lockedCriteria
                 (commander pick), PillarTile (result), CardDetailSheet (commander choice).
```

Vocabulary: `RoleKey` / `AxisKey` / `CardSection.id` / `CuratedStrategy.id` — nothing else. The legacy
`DeckRole`, `SuggestionCategory`, `DeckTemplate`, `CategoryFill`, `DeckGap` are Casual-only leftovers
(§7), not part of the Commander contract.

---

## 4. Decisions (stable; revisit only with a new ADR-level reason)

| # | Decision | Rationale (short) | 60-card impact |
|---|---|---|---|
| D1 | Placement objective = the analysis objective (`PlacementScorer` on v3 primitives + `AnalysisEngine` verification) | two engines cannot be kept aligned by tuning; July proved it | reuse as-is; add copy-count (4-of) handling in the loop |
| D2 | One analysis entry point (`DeckAnalysisPipeline`) for orchestrator, wizard, harness | three mirrored copies drifted silently | mandatory |
| D3 | Build target = resolved skeleton, always; community templates are not a target | EDHREC averages are not a plan and rewrote user picks | 60-card: `SixtyFormatProfile` deltas flow through the same resolver |
| D4 | Strategy pick = one `CuratedStrategy` or Custom; pin via `CuratedStrategy.toPin` | the analysis chip only understands catalog entries | `availableIn(format)` already filters per 60-card format |
| D5 | Posture is persisted (`postureOverride`) and honoured in the pin path | Voltron/Ramp picks were graded as plain macros | applies everywhere |
| D6 | Custom = baseline bands + commander axes; no pin; `strategyLocked = false` | Custom must still be a plan; the engine infers afterwards | 60-card Custom has no commander → baseline bands + seed cards' axes |
| D7 | Collection only; no Scryfall backstop; manual adds (owned or not) always kept | honesty; "help me build from what I own" | same; 60-card pools will be thinner → gaps more common (design the UI for it) |
| D8 | Gaps are `CardSection`s with `current < min`; filler never placed | same object as the Analysis; no third vocabulary | same |
| D9 | Community trends = bounded prior (≤ ×1.15), flag-gated | harmless when the Worker is off | Archidekt aggregates for 60-card, same cap |
| D10 | Land fill v2 (owned non-basics, commander pips, Phyrexian = 0, Karsten rebalance) | previous fill ignored owned utility lands and never verified sources | 60-card: `LandTargetResolver`'s dynamic branch + 4-of basics; no commander pips |
| D11 | Refinement pass ≤ 8 swaps, accept only strict score gain, deterministic | closes marginal-vs-composed gap cheaply | same |
| D12 | Wizard writes into the deck it was launched from (`deckId` nav arg), atomically | no orphan drafts, one Studio entry on the back stack | same |
| D13 | Provenance: WIZARD for engine-placed + commander, USER for manual adds | truthful; cuts no longer exist | same |
| D14 | `SearchCriterion.CommanderEligible` + `AdvancedSearchSheet.lockedCriteria` | the sheet itself must carry the non-removable filters | 60-card has no commander lock; reuse `lockedCriteria` for format legality |
| D15 | Self-evaluation in-app (Result = the analysis) and in the harness (`AnalysisEngine` metrics only) | production quality must be observable | same harness, new segments |
| D16 | Flag stays off until the final gate | ship dark | same |

Design principles carried over from the engine work (do not re-litigate): calibration is normative
(fixtures are a regression harness, never a training set — ADR-007 §4); new params appended last with
defaults; never `.valueOf()` on persisted enum strings; the golden/corpus analysis suites stay
byte-identical during wizard work.

---

## 5. Numbers (filled by the campaign; keep only what a future reader needs)

| Metric | Before (P0 baseline) | After (P7) |
|---|---|---|
| Real collection: cards / unique / eligible commanders | 1344 / 1309 / 93 (P0.2) | 1344 / 1309 / 90 legal-and-non-colorless (a few dropped by the harness's own `isLegal` filter, matching P0's own methodology, plus 1 colorless "Page, Loose Leaf" edge case excluded and diagnosed, §6) |
| Real-collection Commander builds: score distribution (min/p25/median/p75/max) | 40 / 82 / 85 / 88 / 91 (legacy Motor A build, scored by `DeckAnalysisPipeline`, 99 builds) | **78 / 85 / 88 / 91 / 96** (NEW `BuildCommanderDeckUseCase` engine, 180 builds = 90 commanders x {top recommendation, Custom}) |
| Share of builds with 0 BLOCKER | 97.0% (3/99 had ≥1 BLOCKER) | **100% of the 180 in-scope builds** (0 BLOCKER); the 1 excluded colorless commander is a diagnosed pre-existing `BasicLandCalculator` gap, not a scored build |
| Median off-plan share (wizard-placed) | 0.0 (min 0.0, max 0.136) | 0/180 builds exceed the 15% `offplan_share` HARD ceiling (exact per-build shares not separately archived this run — every build passed the ceiling check) |
| Gap sections (`current < min`) | avg 3.17 sections/build, avg 12.96 total missing count | not re-measured as an average this run (harness v2 tracks `gap_section_count`/`gaps_total` per spec in the JSON report, `testdata/wizard-harness/reports/`, gitignored) — all 180 builds satisfied `size_or_gaps` (declared gaps + entries reach the 100-card target) |
| MockCollectionRich reconstruction delta vs fixture score | n/a | Edgar -2, Meren +1, Karlov +4, Urza +5 (tolerance >= -8; 3 of 4 beat their own hand-authored fixture) |
| Final `PlacementScorer` weights | n/a | **NO CHANGE** — `roles 0.45 · axes 0.30 · curve 0.10 · power 0.10 · community <=1.15` (initial weights), confirmed sufficient by the numbers on this row (never fitted to the real collection, ADR-007 §4 — see progress tracker Run 11 §7.2 for the full evidence) |
| Runtime per build (JVM, real collection) | min 40 ms / median 99 ms / max 332 ms (build + analyze) | min 50 ms / median 107 ms / max 231 ms (build + analyze, `BuildCommanderDeckUseCase`) |

**P0.5 baseline reproduction:** `./gradlew :app:testDebugUnitTest --tests
"com.mmg.manahub.feature.decks.harness.P0BaselineTest"` (requires `testdata/wizard-harness/`
checked out — gitignored real user data, Assume-skips otherwise). Full matrix run (99 of
`HarnessSpecs.commanderMatrix`'s 101 specs — 2 dropped by the matrix's own
`HarnessMetricsCalculator.isLegal` filter, nothing to do with sampling); no sampling was needed,
runtime was well inside a single JVM test run (2m36s total build+test). Scored via
`DeckAnalysisPipeline` against the LEGACY Motor A build (`BuildDeckFromTemplateUseCase`) — this is
the two-engine mismatch this whole campaign exists to close (F1/F2): a legacy-built deck already
scores decently (median 85) against the v3 engine on this real collection, which is expected — most
owned commander-eligible pools skew toward generically-good cards Motor A's tag-fingerprint scoring
already favors; the campaign's win condition is closing the LONG TAIL (the min=40/max-offplan-0.136
outliers) and making score/sections/findings the SAME object the wizard shows, not necessarily
raising the median further. The min=40 score is `AnalysisEngine.ILLEGAL_DECK_SCORE_CAP` territory —
at least one of the 99 builds tripped a P5 BLOCKER (color-identity/legality), consistent with the
3% BLOCKER share above.

Acceptance bands in force: see plan §5 until P7 replaces them here.

---

## 6. Engine defects found while planning (status)

| Id | Defect | Fix | Status |
|---|---|---|---|
| F3 | Posture dropped on manual pins; Studio applies `archetypes.firstOrNull()` only | D5 (`postureOverride`, Room v54, Supabase mirror, pin path) | **done P0 (2026-09-08)** |
| F4 | `CuratedStrategy.availableIn(COMMANDER_CASUAL)` is always false → no strategies, chip "Custom" | treat `COMMANDER_CASUAL` as `COMMANDER` | **done P0 (2026-09-08)** |
| F5 | 27 `== DeckFormat.COMMANDER` checks; Commander Casual mis-sized | `DeckFormat.isCommanderFormat` sweep | **done P0 (2026-09-08)** — ~15 real sites swept; a few were already correct (see progress tracker); `DeckWizardScreen`'s format picker list deliberately left unwidened (dead behind the flag, replaced in Phase 3-6) |
| F9 | Basic-land pip weights exclude the commander and count Phyrexian pips; owned utility lands unused | D10 | planned P2 |
| F10 | Owned commander candidates capped at 5 | separate `commanderLimit` param on `CollectionProfileUseCase`, default unbounded | **done P3 (2026-09-09)** — the VM's own call site is NOT yet wired to pass it (that's the pick-step UI/VM work, still pending) |
| F11 | Analysis entry mirrored in 3 places | D2 | **done P0 (2026-09-08)** — `BuildDeckFromTemplateUseCase.recomputeProfile` confirmed NOT a mirror (Motor A's own `DeckScorer.profile()`), left untouched per the plan's escape hatch |
| — | `EvaluateDeckUseCase` emits a gamification event on every call | `emitProgression` flag | **done P0 (2026-09-08)** |
| — | `BuildCommanderDeckUseCase.persist()` is 4 sequential non-transactional writes -- a cancellation mid-write (reachable via Studio's "Rebuild with the Wizard" CTA, D12 rebuild-in-place) can leave an existing draft with cards replaced but a stale pin | mitigated: `isWritingCommanderDeck` blocks cancellation once the write starts (Run 10); real fix is one Room `@Transaction` | **mitigated, not closed** — low risk while `DECK_BUILDER_V2_ENABLED=false`; close before Phase 8 flips it |

Known engine debt deliberately NOT touched by this campaign (see ADR-007 "Known debt"): SYNERGY P4
calibration, PRISON never resolving, 60-card CONTROL inevitability ceiling, `DRAFT` has no skeleton,
per-format strategy curation for the 5 non-Standard 60-card formats.

---

## 7. What the 60-card wave inherits (read before planning it)

Things that differ from Commander and are NOT solved by this campaign:

- **No commander ⇒ no anchor.** Entry must be strategy-first or seed-first (the Casual flows exist:
  `WizardEntryFlow.CARDS/COLORS/STRATEGY`, `DIRECTION` step, `SuggestStrategiesForSeedsUseCase`,
  `ColorStrategyAffinity`, `RankOwnedCardsForProfileUseCase`). They still run through the LEGACY build
  (`BuildDeckFromTemplateUseCase` + Motor A + `DeckTemplateResolver` + `SuggestionCategoryResolver` +
  `MainboardTrimmer`). Plan: port them onto `BuildCommanderDeckUseCase`'s shape (rename to a
  format-agnostic builder), delete the legacy path afterwards, and only then retire `DeckScorer`/Motor A
  entirely (check `EvaluateDeckUseCase`'s dormant `evaluation` field and `LandTargetResolver`'s 60-card
  branch, which still needs `DeckProfile`).
- **Copies.** 4-of consistency: `PlacementScorer` must reason about copies (a 4th copy of a core card
  beats a 1st copy of a marginal one); owned quantity clamps placements (`min(want, owned)`).
- **Colour choice is a decision, not a given.** Identity comes from the user/seeds; `D6` Custom needs
  seed-card axes instead of commander axes; `ColorStrategyAffinity` is still a hand-curated table.
- **Legality.** `CASUAL` is permissive; competitive formats need per-format legality (already in
  `Card.legality*`) and Vintage restricted = max 1; Pauper = `r:common` in searches.
- **Skeletons.** Same `ArchetypeData` SIXTY bands + `SixtyFormatProfile` deltas through the same
  resolver — no fork. `LandTargetResolver` uses `ManaBaseAnalyzer.dynamicLandIdeal` for 60-card.
- **Sideboard.** Out of scope for the builder; the analysis only checks size.
- **Harness.** Add 60-card segments to harness v2; the v3 corpus already has Modern fixtures
  (14–22) usable as `MockCollectionRich` seeds for 60-card reconstructions.
- **UI.** `FormatStepContent` (kept as fallback), `COMING_SOON_FORMATS`, Studio CTA gating by
  `isCommanderFormat` — flip per format when ready.

---

## 8. Open items

- User confirmation (non-blocking, defaults in force): preselect the top recommended strategy (yes);
  show Commander-only catalog entries the commander does not signal under "Other plans" (yes).
- Device runtime check of the build loop on a mid-range phone (plan P6).
- Decide, after P7, whether `CandidatePoolGenerator` has any caller left; delete if not.

---

## 9. File map (current → target)

| Concept | Path |
|---|---|
| Wizard UI/VM | `app/.../feature/decks/presentation/wizard/` (`DeckWizardViewModel.kt`, `DeckWizardScreen.kt`, `DeckWizardCommanderSteps.kt`, `DeckWizardGenerationResult.kt`; Casual: `DeckWizardEntryFlows.kt`, `DeckWizardDirectionIdentity.kt`) |
| Commander builder (target) | `shared/core-domain/.../feature/decks/domain/template/BuildCommanderDeckUseCase.kt`, `engine/CommanderPlanResolver.kt`, `engine/PlacementScorer.kt`, `engine/CurveTargets.kt` |
| Shared analysis entry (target) | `shared/core-domain/.../feature/decks/domain/usecase/DeckAnalysisPipeline.kt` |
| Analysis engine | `shared/core-domain/.../feature/decks/domain/engine/` (`AnalysisEngine.kt`, `ArchetypeSkeletonResolver.kt`, `ArchetypeRoleClassifier.kt`, `SynergyGraph.kt`, `CuratedStrategyCatalog.kt`, `SectionSearchQuery.kt`, `LandTargetResolver.kt`, `ManaBaseAnalyzer.kt`) |
| Strategy recommendation | `.../domain/usecase/RecommendCommanderStrategiesUseCase.kt` (Phase 4, done) |
| Search | `shared/core-model/.../AdvancedSearchQuery.kt`, `shared/core-domain/.../core/domain/search/AdvancedSearchCardMatcher.kt`, `.../usecase/search/BuildScryfallQueryUseCase.kt`, `app/.../core/ui/components/search/AdvancedSearchSheet.kt`, `app/.../core/ui/components/CardSearchSheet.kt` |
| Section UI | `app/.../feature/decks/presentation/components/CardSectionComponents.kt`, `CuratedStrategyPickerSheet.kt`, `HealthComponents.kt` |
| Persistence | `shared/core-model/.../Deck.kt`, `DeckRepository`, Room migrations `Migration_x_y.kt`, Supabase `decks` + `batch_upsert_decks` |
| Harness | `app/src/test/java/com/mmg/manahub/feature/decks/harness/`; fixtures in gitignored `testdata/wizard-harness/` (real user data — never commit) |
| Legacy (Casual-only after this campaign) | `template/BuildDeckFromTemplateUseCase.kt`, `template/DeckTemplateResolver.kt`, `template/SuggestionCategoryResolver.kt`, `template/MainboardTrimmer.kt`, `usecase/SuggestAddsFromCollectionUseCase.kt` (Motor A), `engine/DeckScorer.kt`, `components/StrategyPickerSheet.kt` (Phase 4, Run 6: confirmed ZERO remaining callers, Casual included — not deleted, per this campaign's own "don't touch it" scope; a future cleanup pass should find it a real caller or delete it) |

---

## 10. Changelog

- 2026-09-08 — Document created with the analysis findings and decisions of the Commander v3 plan.
- 2026-09-08 — Phase 0 done (0.1, 0.2, 0.3, E1-E5): see the progress tracker's run log for
  per-item notes. Two things future phases should know:
  1. **`CurveTargets.forSkeleton` is new derived logic, not an extraction.**
     `AnalysisEngine.evaluateCurve` never computed a per-MV-bucket numeric target — only a
     whole-curve average-CMC band check and a coarse low/mid/high shape ordering. Phase 2's
     `PlacementScorer.curveGain` will be the FIRST real consumer of `CurveTargets`; its per-shape
     zone ratios (45/35/20 FRONT, 25/50/25 BELL, 20/35/45 BACK) are a documented judgment call,
     not derived from any existing engine number — worth a second look during Phase 2/7
     calibration.
  2. **Posture threading into the skeleton is gated on `isManualOverride`.**
     `EvaluateDeckUseCase.applyArchetypeLayer` now passes a MANUAL posture pin into
     `ArchetypeSkeletonResolver.resolveWithColor`, but NOT an inference-detected one — inference
     already resolved `ArchetypeResolution.posture` before this phase without it ever reaching the
     skeleton, and widening the fix to that path too is a real, not-yet-calibrated behavior change
     (the golden/corpus suites were calibrated without posture-aware inference skeletons). If a
     future phase wants inference-detected postures to also shape the skeleton, that is new work,
     not a bug fix, and needs its own calibration pass.
- 2026-09-08 — Phase 0.5 + Phase 1 done (Run 2): see the progress tracker's Run 2 log for per-item
  notes. Three things future phases should know:
  1. **The P0.5 baseline already scores decently against v3** (median 85/100, 97% zero-BLOCKER,
     median off-plan share 0.0) even though it is the LEGACY Motor A build. This is not evidence the
     rebuild is unnecessary — it means the real collection is broad enough that Motor A's
     tag-fingerprint greedy fill usually lands on decks the v3 engine also likes; the campaign's win
     condition is closing the LONG TAIL (min score 40, max off-plan 13.6%) and making the wizard's
     OWN shown score/sections the same object Studio shows, not chasing a higher median.
  2. **No existing engine table maps a `ThemeId` onto a `SynergyGraph` `AxisKey`.** `CommanderPlanResolver
     .THEME_TARGET_AXES` is Phase 1's own documented judgment-call table (15 of 21 non-tribal themes
     get a clean 1:1 axis; `WHEELS`/`CLONES_THEFT`/`VEHICLES`/`TREASURE` are left unmapped, no static
     axis fits). `CommanderPlan.targetAxes` is a Phase-1 contract nothing reads yet — Phase 2's
     `PlacementScorer` is the first real consumer, and should re-examine this table rather than treat
     it as settled.
  3. **`BuildStage` was NOT extended for Commander.** `CommanderBuildStage` is a separate enum
     because `DeckWizardGenerationResult.kt`'s `BuildStage.label()` is an exhaustive `when` with no
     `else` branch — adding cases to the shared enum would force an unrelated Casual-UI string change.
     Phase 6 (wizard VM wiring) should keep this split when it wires the Commander build loop's
     progress UI.
- 2026-09-09 — Phase 2 (Run 3) done, scoped: see the progress tracker's Run 3 log for full detail.
  Headline items future phases must know:
  1. **`PlacementScorer`'s D8 filler floor is `roleGain > 0 OR axisGain > 0`** — a theme with no
     `THEME_TARGET_AXES` entry never zeroes out placement, `roleGain` alone carries it. The map
     was NOT widened to close the coordinator's mid-run gap concern; see the tracker's note 1 for
     why forcing new axis citations was rejected.
  2. **`CommanderPlan.curveTargets` (Phase 1) has no `targetCount`** (built without a
     `nonLandCount`) — `BuildCommanderDeckUseCase` re-derives its own scaled `CurveTargets` rather
     than trusting the plan's copy. Any future direct reader of `plan.curveTargets` will hit the
     same silent `null`.
  3. **`replaceAllCardsWithSource` is a default-method composition (clearDeck + addCardToDeck
     loop), NOT a real Room transaction** — D12's cancellation-safety guarantee is UNMET today.
     Must be hardened before Phase 6 wires this into a real user-facing flow.
  4. **round_trip_identity, as literally specified, is a structural tautology** (pure-function
     analyze called twice on identical inputs cannot diverge) — verified true for the NEW path,
     and argued (not tested) true for the legacy path via `DeckWizardViewModel
     .writeResultIntoNewDeck`'s direct `result.archetypeOverride`/`themesOverride` write. The real
     F7 risk is a build-intent-vs-persisted-pin divergence inside `BuildDeckFromTemplateUseCase`/
     `DeckTemplateResolver.fromCommanderAggregate`, not an analyze-twice divergence — flagged as a
     concrete follow-up for Phase 7 or a dedicated investigation, not solved this run.
  5. **Land fill v2 test coverage (mono/2/3/5-colour cases) and the write-atomicity test were NOT
     written this run** — the biggest scope cut of Run 3. The implementation exists
     (`BuildCommanderDeckUseCase.fillLandsV2`/`rebalance`) but is exercised only incidentally by
     the 5 `BuildCommanderDeckUseCaseTest` cases.
- 2026-09-09 — Run 4 CLOSED the Phase 2 gate and started Phase 3 (full detail: progress tracker's
  Run 4 log). Two things future phases should know:
  1. **The MockCollectionRich ground-truth test is GREEN with real margin** — all 4 fixtures'
     wizard-vs-fixture score deltas are within [-2, +5] against an 8-point tolerance, so the
     `BuildCommanderDeckUseCase`/`PlacementScorer` pipeline is not merely "passing," it is
     competitive with hand-authored decklists. The ONE real gap found: a curated pin's
     `StrategyPin.archetype` (`toPin()`'s `archetypes.first()`) is not a reliable ground truth for
     "what macro did this build actually resolve to" for entries with multiple listed archetypes
     (e.g. "aristocrats" = `{AGGRO, MIDRANGE, COMBO}`, pin hard-codes AGGRO) — comparing against it
     is a tautology for a manually-pinned build. Any future test wanting "did this build get the
     RIGHT macro" must compare against `AnalysisV3Fixture.expectedMacro` (or another independent
     ground truth), never a curated pin's own forced field.
  2. **A genuine, un-diagnosed Custom-macro gap exists for tribal-AGGRO commanders**: Edgar Markov
     (Vampires) resolves as MIDRANGE under a Custom build, not AGGRO. Left RED per this campaign's
     explicit "never loosen to pass" instruction. Concrete follow-up for Phase 7: Custom's placement
     (D6, generic baseline bands + the commander's own axes) has no explicit curve/creature-density
     bias, so nothing pulls a fast, cheap, tribal-aggressive plan's macro off the generic baseline's
     MIDRANGE-shaped read. Root cause not yet located in `EvaluateDeckUseCase.resolveArchetype`/the
     prototype macro resolver.
  3. **F10 is fixed at the use-case layer, NOT yet wired end-to-end.**
     `CollectionProfileUseCase.commanderLimit` (new, separately defaulted from `limit`) removes the
     cap; `DeckWizardViewModel`'s own call site was NOT touched this run (still implicitly uses the
     old shared-limit shape until the pick-step VM work lands) — the fix is real and tested but
     inert in production until Phase 3's UI/VM phase wires it.
  4. **Phase 3's UI half (grid, search bar, `AdvancedSearchSheet.lockedCriteria`,
     `StructuredCardSearch` extraction, `DeckWizardViewModel` wiring) is UNSTARTED** — only the
     commonMain search-plumbing prerequisite (`SearchCriterion.CommanderEligible` + its query-
     builder/matcher wiring) landed. This was a deliberate scope decision (see progress tracker Run
     4 "Why cut"), not a discovery of a blocker — the next session can start directly on 3.2/3.3
     with the plumbing already in place.
- 2026-09-09 — **Phase 3 DONE (Run 5)**: full detail in the progress tracker's Run 5 log. Headline
  items future phases should know:
  1. **F10 was already fully wired in production before this run** — `CollectionProfileUseCase
     .commanderLimit` defaults to `Int.MAX_VALUE`, and `DeckWizardViewModel`'s call site never
     passed an explicit value, so the cap was already gone since Run 4's 3.1 commit. Run 4's own
     note claiming it still needed wiring was overcautious, not wrong about the fix itself.
  2. **`AdvancedSearchSheet.lockedCriteria` is a REUSABLE mechanism, not commander-specific** — any
     future caller (a 60-card format's own legality lock, per D14's own "60-card impact" column)
     can pass `lockedCriteria` and get the same structural non-removability
     (`AdvancedSearchViewModel.mergeLocked` strips same-subtype UI criteria and re-appends the
     locked ones on every `rebuildQuery`, survives `clearAll`/`seedFrom`) for free.
  3. **`StructuredCardSearch` (commonMain) is now the ONE place "one query → Scryfall fragment +
     local matcher" logic lives** — Deck Studio's Analysis-tab "Browse for X" and the wizard's
     commander pick step both delegate to it. Phase 5 (Plan sections step) should use it too rather
     than re-deriving the pairing a third time.
- 2026-09-09 — **Phase 4 DONE (Run 6)**: full detail in the progress tracker's Run 6 log. Headline
  items future phases should know:
  1. **`RecommendCommanderStrategiesUseCase` (new, commonMain) replaces
     `DeriveCommanderStrategiesUseCase`** (deleted) as the STRATEGY step's ranking source — reuses
     `CommanderPlanResolver`'s `THEME_TARGET_AXES`/`tribeAxisKey` (promoted `internal`) for its
     PRIMARY commander-axis/role signal, plus `card_strategy_tags`, EDHREC theme names,
     `ColorStrategyAffinity`, and an owned-role-band-coverage signal computed once per call over the
     owned pool (NOT a public extraction of `BuildCommanderDeckUseCase`'s own candidate-pool
     internals — those carry pip/curve/power machinery this use case has no use for; see the
     tracker's own note on why that specific reuse was rejected).
  2. **A real `TribeDeriver` false-positive was found and worked around, not fixed upstream.**
     `payoffTribeKeys`'s generic `"<word> you control"` regex manufactures a spurious tribe from
     non-tribal oracle text (Urza's "for each artifact you control" → a fake "artifact" tribe) —
     the recommender only trusts a tribe that is BOTH a subtype AND a named payoff (the
     intersection), never payoff-only. `TribeDeriver` itself was NOT touched (out of this run's
     scope) — any other caller of `payoffTribeKeys` alone should be aware this false-positive class
     exists.
  3. **Two fixture commanders (Urza, Brago) classify to an EMPTY role/axis map** under
     `ArchetypeRoleClassifier`/`SynergyGraph` — their hand-authored fixture oracle text is too
     minimal to trip any matcher, so the PRIMARY signal contributes zero for them specifically (a
     real Scryfall card's fuller oracle text would very likely not have this gap). Their own
     recommendation tests simulate the `card_strategy_tags` signal instead. Worth revisiting if
     Phase 7 calibration finds similarly-thin-oracle-text commanders under-recommended in
     production.
  4. **The old 3-axis `StrategyPickerSheet` component now has ZERO remaining callers** (its only
     call site, the old `StrategyStepContent`, was replaced this run) — left untouched per this
     run's explicit instruction, but the file map's "Casual-only" framing for it (§9) was already
     stale before this run (no live Casual flow ever called it either). A future pass should find it
     a real caller or delete it.
  5. **The #1 recommendation is preselected on every commander pick (product default, plan §8)** —
     this changed two pre-existing `DeckWizardViewModelTest` assertions that encoded the OLD
     "nothing selected until the user picks" behavior (a stale-archetype-leak check and the
     GENERIC-escape-hatch check); both were rewritten to select Custom explicitly where the test's
     real intent needed an unpinned state, not loosened.
- 2026-09-09 — **Phase 2's deferred write-atomicity gate item CLOSED + Phase 6 SLICE landed (Run 8,
  low reasoning-effort budget — see the progress tracker's Run 8 log for the full breakdown).**
  `DeckRepository.replaceAllCardsWithSource` is now a genuine single-transaction Room write on
  Android (`DeckDao.replaceAllCardsWithSource`, `@Transaction`); `writeResultIntoNewDeck` uses it for
  both Casual and Commander. `Screen.DeckWizard.createRoute` gained `format`/`deckId` args and the VM
  preselects a passed format, skipping the FORMAT step. The Run 7 `onRemoveSeed` open finding (the
  Casual-only "clear stale strategy pick" rule wrongly shared with Commander) is fixed. **Headline
  finding for the next run: `BuildCommanderDeckUseCase` (the whole Phase 2 placement engine) is still
  never called by `DeckWizardViewModel.onGenerate()`** — every format, Commander included, still
  builds via the legacy `BuildDeckFromTemplateUseCase`/`TemplateBuildResult` pipeline. Rewiring
  `onGenerate()` to dispatch Commander through `BuildCommanderDeckUseCase` is a PREREQUISITE for
  6.2/6.3/6.4 (Review/Generating/Result content), not parallel work — the next run should start
  there. NOT done this run: the write path targeting an existing launched-from deck, the pop-back-to-
  Studio nav branch (deliberately reverted rather than shipped half-wired — see the tracker's note
  1), the Studio wizard CTA + confirmation dialog, all of 6.2/6.3/6.4's UI content, the
  Result-vs-Studio round-trip test, and the `compose-design-reviewer`/`android-edge-case-tester`
  passes.
- 2026-09-09 — **Phase 6 headline wire (Run 9)**: `BuildCommanderDeckUseCase` is now actually called
  by `DeckWizardViewModel.onGenerate()` for Commander formats (`generateCommanderDeck`, D12/D13
  write path), and a new `BuildCommanderDeckPersistenceRoundTripTest` proves the build's own
  `DeckAnalysis` survives a real persist-then-re-analyze round trip byte-for-byte. The Result/
  Generating/Review UI content, the Studio CTA, and the pop-back nav branch were still NOT done —
  `commanderBuildResult` was populated in state with no screen rendering it yet.
- 2026-09-09 — **Phase 6 DONE (Run 10)**: full detail in the progress tracker's Run 10 log. Headline
  items future phases should know:
  1. **`BuildCommanderDeckUseCase.persist()` is still 4 sequential, non-transactional writes** —
     the adversarial pass flagged this as a real data-corruption risk for the NEW "Rebuild with the
     Wizard" (rebuild-in-place) path specifically, since that path mutates a real pre-existing
     draft rather than an orphan-safe fresh deck. Mitigated this run with a cancel-blocking guard
     (`isWritingCommanderDeck`) so a cancellation can no longer land mid-write, but the write itself
     is still not one Room `@Transaction` — a genuine follow-up before `DECK_BUILDER_V2_ENABLED`
     ever flips (the CTA is unreachable in production today, so real-world risk is currently zero).
  2. **Item 8 (posture pin -> Studio chip) needed no new code** — Phase 0/E3 already wired
     `postureOverride` through the ONE shared `DeckAnalysisPipeline.analyze` both the wizard and
     `DeckDoctorOrchestrator` call; this run's contribution was confirming that chain end to end,
     not fixing a gap. No `DeckDoctorOrchestrator`-level test proves it directly yet (only the
     lower-level `DeckAnalysisPipeline` round trip does) — a reasonable Phase 7 addition if
     stronger proof is wanted.
  3. **`CommanderBuildStage` progress is real, not simulated** — `BuildCommanderDeckUseCase` gained
     an `onStage` callback (defaulted, zero call-site breakage beyond a mock-matcher arg count) that
     fires at the SAME stage boundaries the placement loop already passes through.
