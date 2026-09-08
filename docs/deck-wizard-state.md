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
| Campaign phase | P0 done (incl. 0.5); P1 done (contracts only — P2 placement engine not started) |

Phase log (fill one line per gate): `P0 done 2026-09-08 (0.1/0.2/0.3/0.5/E1/E2/E3/E4/E5) · P1 done 2026-09-08 (1.1/1.2/1.3, contracts only) · P2 — · P3 — · P4 — · P5 — · P6 — · P7 — · P8 —`

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
| Real collection: cards / unique / eligible commanders | 1344 / 1309 / 93 (P0.2) | — |
| Real-collection Commander builds: score distribution (min/p25/median/p75/max) | 40 / 82 / 85 / 88 / 91 (legacy Motor A build, scored by `DeckAnalysisPipeline`, 99 builds) | — |
| Share of builds with 0 BLOCKER | 97.0% (3/99 had ≥1 BLOCKER) | — |
| Median off-plan share (wizard-placed) | 0.0 (min 0.0, max 0.136) | — |
| Gap sections (`current < min`) | avg 3.17 sections/build, avg 12.96 total missing count | — |
| MockCollectionRich reconstruction delta vs fixture score | n/a | — |
| Final `PlacementScorer` weights | n/a | roles · axes · curve · power · community |
| Runtime per build (JVM, real collection) | min 40 ms / median 99 ms / max 332 ms (build + analyze) | — |

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
| F10 | Owned commander candidates capped at 5 | compute all eligible in the VM | planned P3 |
| F11 | Analysis entry mirrored in 3 places | D2 | **done P0 (2026-09-08)** — `BuildDeckFromTemplateUseCase.recomputeProfile` confirmed NOT a mirror (Motor A's own `DeckScorer.profile()`), left untouched per the plan's escape hatch |
| — | `EvaluateDeckUseCase` emits a gamification event on every call | `emitProgression` flag | **done P0 (2026-09-08)** |

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
| Strategy recommendation (target) | `.../domain/usecase/RecommendCommanderStrategiesUseCase.kt` |
| Search | `shared/core-model/.../AdvancedSearchQuery.kt`, `shared/core-domain/.../core/domain/search/AdvancedSearchCardMatcher.kt`, `.../usecase/search/BuildScryfallQueryUseCase.kt`, `app/.../core/ui/components/search/AdvancedSearchSheet.kt`, `app/.../core/ui/components/CardSearchSheet.kt` |
| Section UI | `app/.../feature/decks/presentation/components/CardSectionComponents.kt`, `CuratedStrategyPickerSheet.kt`, `HealthComponents.kt` |
| Persistence | `shared/core-model/.../Deck.kt`, `DeckRepository`, Room migrations `Migration_x_y.kt`, Supabase `decks` + `batch_upsert_decks` |
| Harness | `app/src/test/java/com/mmg/manahub/feature/decks/harness/`; fixtures in gitignored `testdata/wizard-harness/` (real user data — never commit) |
| Legacy (Casual-only after this campaign) | `template/BuildDeckFromTemplateUseCase.kt`, `template/DeckTemplateResolver.kt`, `template/SuggestionCategoryResolver.kt`, `template/MainboardTrimmer.kt`, `usecase/SuggestAddsFromCollectionUseCase.kt` (Motor A), `engine/DeckScorer.kt`, `components/StrategyPickerSheet.kt` |

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
