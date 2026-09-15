# Deck Wizard — State & Decisions (living document)

**Purpose.** The single durable record of what the Deck Wizard is, why it is built the way it is, and
where it stands. Written for the next campaign (60-card formats) to start from context, not from
archaeology. Keep it concise: decisions, contracts, numbers, open items. Execution logs belong in the
gitignored progress tracker, not here.

**Last updated:** 2026-09-15 (v4 campaign, Run 11/W6c — real seeded variety (Defect 1) + real
evidence for the W6 score dip (Defect 2); see `docs/plans/deck-wizard-commander-v4-progress.md` for
the full per-item log, gitignored). Commit `b848b9b1` on `feature/deck-wizard`.
**Owning plan (v3, shipped):** `docs/plans/deck-wizard-commander-plan.md` — DELETED per the
AI-planning-doc rule now that the campaign has shipped; this file is the durable record that
survives. **v4 (active):** `docs/plans/deck-wizard-commander-v4-plan.md` (gitignored).

## v4 — Run 8 (2026-09-14): R15 — the two wizard entry points, disjoint by construction

Run 6 gated both "Build from seed" and "Rebuild with the Wizard" to `isCommanderFormat`, leaving
them both visible on the same Commander deck and — after R13 started passing a real `deckId` into
"Build from seed" — able to silently replace a non-empty deck's cards with no confirmation (closed
in Run 7 at the UI level only). **R15 (user, 2026-09-14) made the two entry points mutually
exclusive by render condition, and added a structural guard so a silent overwrite can never happen
again regardless of which entry point reaches the write:**
- **"Build from seed" now renders ONLY on the empty-deck state card** — deleted outright from the
  overflow menu. It never confirms (nothing to lose by construction).
- **"Rebuild with the Wizard" now renders ONLY in the overflow menu on a non-empty deck** — gained
  `&& !isEmptyDeck` on its existing gate. It always confirms.
- `resolveWizardNavDecision` (`DeckStudioScreen.kt`) simplified: took a `WizardEntryPoint` param
  instead of `isEmptyDeck` — each entry point's outcome no longer varies by deck state, since each is
  only ever rendered in the one condition where its rule is correct.
- **Structural guard, independent of the UI:** `Screen.DeckWizard.createRoute` gained a required
  `replaceConfirmed: Boolean` nav arg (no default — every real call site must decide); `DeckWizardViewModel`
  re-checks the launched deck's REAL card count right before persisting (not at launch) and refuses
  an unconfirmed write into a non-empty deck — a `MagicToast` (ERROR) plus a non-fatal, deck left
  byte-identical. This also closes a previously-unflagged gap: the Discoveries/combo hand-offs never
  had a confirm dialog of their own; they are now protected by the same guard.
- **Known incident, recorded and resolved before this run started:** an earlier wizard commit
  (`803fbd9e`) had swept part of the user's own in-progress Multi Add work into itself and did not
  compile from a clean tree; its "green build" had come from a worktree seeded with the dirty main
  tree. The user fixed it by committing their own work directly (`bb7fd0fc`). This run verified
  `bb7fd0fc` compiles clean before starting and built its own verification worktree from that commit
  alone. Full trace: Run 8 in the progress tracker, and `feedback_partial_file_entanglement_surgical_staging`'s
  "Verifying the result" section.
- **Open item (unchanged):** Discoveries/combo hand-offs are safe (refuse rather than overwrite) but
  have no confirm-and-proceed path of their own on a non-empty deck — a future pass may want to give
  them their own dialog instead of a hard refusal.

## v4 — Run 6 (2026-09-14): the format step, deleted for real (R13/R14)

Prior runs removed `WizardPhase.FORMAT` from the COMMANDER phase list only, but left the enum
value, `FormatStepContent`, and every Casual/no-arg entry point still able to render it —
`Screen.DeckWizard.createRoute`'s `format`/`deckId` args were optional, and "Build from seed"
(`DeckStudioScreen.kt`'s `handleBuildFromSeed`) called `onNavigateToWizard(null, null, null, null,
null)` with no format/deckId at all, so the format step still reached the user on every "Build from
seed" tap. **R13/R14 (user, 2026-09-14) made this unrepresentable at the type level, not just
unreachable by convention:**
- `WizardPhase.FORMAT` no longer exists (enum value deleted; the compiler forced every exhaustive
  `when` to be fixed). `FormatStepContent`/`FormatCard`/`V1_FORMATS`/`COMING_SOON_FORMATS` deleted.
- `Screen.DeckWizard.createRoute(format: String, deckId: String, ...)` — both required, no default,
  moved to the front of the param list. A wizard route without a format is now a compile error.
- `DeckWizardViewModel`'s `init` always resolves a format (falls back to `CASUAL` for a
  missing/unsupported arg) and picks the correct starting phase (`COMMANDER_PICK` for Commander
  formats, `ENTRY` for Casual) before the UI ever collects the first state. Back from either first
  step now exits the wizard.
- `DeckStudioScreen`'s `onNavigateToWizard` callback gained required `deckId`/`format` params; all 3
  internal call sites (Build from seed, Discoveries hand-off, combo hand-off) now pass the currently
  open draft's own id/format.
- **"Build from seed" is now gated on `isCommanderFormat`** (R14) — hidden for every 60-card format
  until that wave ships. Reuses the existing `isCommanderFormat` val (already computed for "Rebuild
  with the Wizard"); no second format predicate introduced.
- `onSelectFormat`/`onNextFromFormat` KEPT as VM functions (unreachable from the UI now, used only by
  `init` and ~150 existing test call sites as a test-setup idiom) — a deliberate scope call to avoid
  rewriting a large swath of `DeckWizardViewModelTest.kt` for zero behavioral gain; documented in the
  functions' own KDoc.
- **Known redundancy, reported not merged (user's call, per the brief):** "Build from seed" and
  "Rebuild with the Wizard" are both visible on the same Commander decks now and, on an EMPTY draft,
  do the exact same thing. On a NON-EMPTY draft they diverge: "Rebuild" confirms before replacing
  every card and reuses the deck's existing commander/strategy; "Build from seed" has no confirmation
  and starts cold. Full findings tracker entry: Run 6.
- **Known gap, reported not fixed:** the empty-deck landing panel's own "Build from seed" option card
  is still visible regardless of format (only gated on the feature flag) — it no-ops safely on tap for
  a non-Commander draft, but is visually inconsistent with the overflow menu's format gate.

## v4 — Run 2 (2026-09-10): W2 (Commander pick step)

Closes G2/G3 (R2/R3) — the COMMANDER_PICK step, now the wizard's FIRST screen (Run 1 removed FORMAT).
Two commits: `c6fa39fe` (W2.1/W2.2/W2.3 implementation) + `28693576` (compose-design-reviewer fixes).
- **W2.1 (G2/R2) — no tabs.** Deleted the Collection/All-cards `TabRow` and `CommanderResultTab`
  entirely. Idle (`commanderPickIsIdle`: blank query AND zero non-locked filter criteria) shows the
  owned commander-eligible grid (`commanderPickLocalCandidates`, unchanged); any search text or
  user-added advanced-search filter routes to a live Scryfall search
  (`triggerCommanderPickSearch` → `StructuredCardSearch.scryfallFragment` → `searchCardsUseCase`,
  already `ScryfallRequestQueue`-wrapped at the data-source layer), debounced at the SAME
  `SEARCH_DEBOUNCE_MS` (400ms) used elsewhere in this VM. `commanderLockedCriteria` (commander
  eligibility + Commander-format legality) stays structurally non-removable — a structured query
  containing ONLY the locked criteria (sheet opened, Search tapped, nothing added) still reads as
  idle, by design. Scryfall results are marked owned/not via the same ownership-matching logic the
  old Collection tab used. A distinct `commanderSearchError` state (new) now renders `InlineErrorState`
  on a failed search, ranked loading > error > empty so a failure never reads as "no results" (G7).
- **W2.2 (R2) — filters mirror `CollectionScreen`.** Active-filter count line + `MagicCtaButton(Ghost,
  Error)` "clear filters" + `BadgedBox`/`Tune` icon with the active (non-locked) filter count as a
  badge, replacing the old per-criterion `InputChip` row. One shared `commanderActiveFilterCount`
  helper feeds both the badge and the idle check (W2.1) — never computed two different ways.
- **W2.3 (G3/R3) — selected commander mirrors `CardDetailSheet`.** Extracted `CardFlipPortrait`
  (`presentation/components/CardDetailSheet.kt`) — the large-image + DFC-flip block, previously
  private/inline in `CardDetailSheet`'s `ModalBottomSheet` — into a standalone `internal` composable
  both `CardDetailSheet` and the wizard's inline selected-commander view call. Below the image:
  `CardTagGroup`, then "Change commander" (`onClearCommander`); "Continue" is owned solely by the
  existing `WizardStickyButton` (a design-review pass caught and fixed a duplicate inline "Next"
  button that shipped in the first commit — see below).
- **compose-design-reviewer pass (2nd commit):** 1 P0 (duplicate "Next" CTA when a commander was
  selected — fixed, `WizardStickyButton` is now the ONLY "Next" for this step, matching every sibling
  step), 3 P1 (idle-empty-collection dead-end message added; the new `commanderSearchError` field
  above came FROM this finding, not W2.1's original design; Tune icon's active tint unified to
  `primaryAccent`, was inconsistently `goldMtg`), 3 P2 (shared `CardPortraitFrame` helper to stop the
  loading-placeholder frame drifting from `CardFlipPortrait`'s; the flip hint chip is now itself
  tappable, not just the image; `CommanderCtaHeight` 52dp→48dp, back on the touch-target minimum and
  the 8dp grid). One P2 finding (spinner replaces prior results on every keystroke) was left as-is —
  confirmed the debounce is the same 400ms used everywhere else in this VM, not a short window.
- **Tests:** rewrote the old "name filter is a pure local filter, no Scryfall call" test (now FALSE
  by design — a non-blank query must fetch) into idle/search/filter-transition coverage; deleted the
  tab-switch tests along with `CommanderResultTab`; added owned-marking-on-Scryfall-results and
  search-error-sets/clears coverage. 111 `DeckWizardCommanderStepsTest`+`DeckWizardViewModelTest`
  cases, 1 failure — the same pre-existing Casual taxonomy-toggle case documented since Run 1,
  confirmed unchanged via `git stash` before/after.
- **Gate:** `:app:assembleDebug` and `:shared:core-domain:compileKotlinWasmJs` green both commits;
  `pre-push-security-gate` PASS before each commit; golden/corpus/calibration/skeleton suites
  untouched (no `shared/` scoring code touched this run).

## v4 — Run 3 (2026-09-10): W3 (strategy step) + W4.1/W4.4 (visual half of plan sections)

Two commits: `40adaccc` (W3 engine — `RecommendCommanderStrategiesUseCase.splitRecommended`) +
`57a6d61f` (W3 UI + W4.1 category nav + W4.4 ring/label fix + design-review fixes).
- **W3 (G4/E3, R4):** Recommended is now score-threshold-gated (absolute noise floor `1.0`,
  calibrated from the real fixture score distribution — a zero-signal commander tops out at `0.9`
  from color affinity alone; relative top-of-range fraction `0.2`) plus a hard cap of 5, replacing
  the old `take(6)` cosmetic split. "Other plans" renamed "Partial fit". A commander with no real
  signal now yields a SHORT (possibly empty) Recommended list instead of 5 padded color-affinity
  ties — verified empty for a from-scratch Urza (no owned collection, no tags, no EDHREC).
- **W4.1 (G5/R5):** the plan step's category navigation reuses the Analysis tab's own `PillarTile`
  single-select row verbatim — one overview row, one active category's sections below, replacing
  the old always-all-expanded flat list.
- **W4.4 (G8b/E2):** the section ring now shows the SAME metric as its label (progress toward
  ideal, clamped at 1.0, never shrinking); over-ideal reads as an explicit `goldMtg` tone, an
  anti-role over max stays an immediate `lifeNegative` alert — both render a FULL ring now, never a
  shrinking one. Math extracted to a plain, unit-tested `sectionRingState()`.
- W4.2 (Browse coverage for every `role:*` section, the 3 long-red `SectionSearchQueryTest` cases),
  W4.2b (G14 Collection/Scryfall parity audit) and W4.3 (Browse loading state) are the SEPARATE
  "search half of W4" — not started, not this run's scope.
- Full detail (threshold derivation, actual per-fixture Recommended lists, design-review findings +
  fixes): `docs/plans/deck-wizard-commander-v4-progress.md` Run 3 (gitignored).

Remaining v4 workstreams (W4.2/W4.2b/W4.3, W5-W8) are NOT started.

## v4 — Run 1 (2026-09-10): W0 + W1

Fixes 14 user-reported defects from hands-on testing of the v3 wizard (G1-G14). Run 1 closed:
- **W0.1 (G13/E9/R7):** ONE `isLegalForFormat(card, format)` predicate
  (`shared/core-domain/.../engine/DeckLegality.kt`), replacing `AnalysisEngine`'s private `isLegal`
  and `BuildCommanderDeckUseCase.isLegalForCommanderFormat`. `SectionSearchQuery`'s legality clause
  now also drops `legal:commander` for `COMMANDER_CASUAL` (was wrongly enforcing it before).
- **W0.2 (G10/E10):** the 94-card bug's real cause — `materializeBasics` can't place a basic land the
  user owns zero copies of (its `Card` object doesn't exist in `ownedCollection`). Fixed via
  `DeckWizardViewModel.ensureBasicsAvailable` pre-warming missing basics through `CardRepository
  .searchCardByName`, mirroring `DeckStudioViewModel.applyLandSuggestions`'s own fetch-if-missing
  pattern. `DeckWizardViewModel` gained a new required `cardRepository` constructor param.
- **W0.3 (G8a/E1):** `CardSection.realCount` (computed, `Σ contributions.quantity`) is now what every
  UI consumer renders; `current` (confidence-weighted) stays score-only, never displayed.
- **W0.4 (F18):** traced the principled fix and confirmed it requires touching
  `CommanderPlanResolverTest`'s D6 assertion — escalated per the campaign brief rather than forced.
  See the progress tracker for the exact trade-off and recommendation. **Still RED, unchanged.**
- **W1 (G1/R1):** Commander's wizard steps are now `COMMANDER_PICK → STRATEGY → MANUAL_ADDS → REVIEW`
  (FORMAT removed from the indicator; `onBackPressed` from COMMANDER_PICK exits the wizard). R11
  (finish → Build tab) already held with zero code change (`DeckStudioUiState.selectedTab` defaults
  to BUILD) for the primary fresh-draft path; the "Rebuild with the Wizard" pop-back-to-existing-entry
  path has a known, unfixed residual gap — see the progress tracker.

Remaining v4 workstreams (W2-W8) are NOT started.

---

## 1. Status

| Item | State |
|---|---|
| Feature flag `FeatureFlags.Decks.DECK_BUILDER_V2_ENABLED` | **`true`** (flipped Run 12, Phase 8 — full verify gauntlet green first) |
| Formats reachable in this wave | `COMMANDER`, `COMMANDER_CASUAL` |
| Formats deferred | `CASUAL`, `STANDARD`, `PIONEER`, `MODERN`, `LEGACY`, `VINTAGE`, `PAUPER` (§7) |
| Engine used by the wizard | Commander/Commander Casual: `BuildCommanderDeckUseCase` (`PlacementScorer` + `DeckAnalysisPipeline` verify/refine), wired into production since Run 9, LIVE since Run 12. Casual: still legacy Motor A (`DeckScorer.fit`), unchanged (non-goal). |
| Campaign phase | **CLOSED** — P0 done (incl. 0.5); P1 done (contracts only); P2 gate CLOSED (Run 4); P3 DONE (Run 5); P4 DONE (Run 6); P5 DONE (Run 7); P6 DONE (Runs 8-10); P7 DONE (Run 11 — harness v2 green, calibration verdict NO CHANGE, telemetry added); P8 DONE (Run 12 — F17 fixed, persist() atomicity closed, cleanup, flag flip) |

Phase log (fill one line per gate): `P0 done 2026-09-08 (0.1/0.2/0.3/0.5/E1/E2/E3/E4/E5) · P1 done 2026-09-08 (1.1/1.2/1.3, contracts only) · P2 gate CLOSED 2026-09-09 (Run 4 closed the land-fill/reconstruction/thin/atomicity test items Run 3 deferred — see progress tracker) · P3 DONE 2026-09-09 (Run 4: 3.1 F10 fix + 3.4 search-plumbing; Run 5: 3.2 UI + 3.3 StructuredCardSearch + lockedCriteria + VM wiring + tests + compose-design-reviewer pass — see progress tracker) · P4 DONE 2026-09-09 (Run 6: RecommendCommanderStrategiesUseCase + single-select STRATEGY step UI/VM wiring + DeriveCommanderStrategiesUseCase retirement + tests + compose-design-reviewer pass — see progress tracker) · P5 DONE 2026-09-09 (Run 7: PlanSectionsStepContent replaces MANUAL_ADDS for Commander, DeckAnalysisPipeline-only attribution, selectedPosture F3 gap closed, onAddSeed/onRemoveSeed hardened, ownedAvailabilityBySection, tests + compose-design-reviewer pass — see progress tracker) · P6 DONE 2026-09-09 (Run 8: write-atomicity + nav-arg plumbing; Run 9: BuildCommanderDeckUseCase wired into onGenerate, D12/D13 write path, persistence round-trip proof; Run 10: Result/Generating/Review UI for Commander, Studio "Rebuild with the Wizard" CTA + confirm dialog, pop-back nav, item 8 verified by code trace, compose-design-reviewer + android-edge-case-tester passes with fixes applied — see progress tracker Run 10; one flagged-not-fully-closed item: persist()'s 4-call write is not yet one Room transaction, mitigated via a cancel-blocking guard, low real risk while DECK_BUILDER_V2_ENABLED stays false) · P7 DONE 2026-09-09 (Run 11: harness v2
real-collection segment 180/180 HARD-pass + MockCollectionRich/Thin segments green (1 diagnosed
exclusion each, logged not masked); calibration verdict NO CHANGE to PlacementScorer's initial
weights (evidence, not a default); Edgar Markov MIDRANGE-vs-AGGRO gap diagnosed to
CommanderPlanResolver's Custom branch, principled fix identified but deferred (would need to rewrite
a stable, deliberately-tested D6 contract — see progress tracker Run 11 §7.2 for the concrete next
step); 8 new telemetry keys added, 0 stale keys needed removal (already unreachable for Commander by
Phase 6's dispatch split) — see progress tracker Run 11) · P8 DONE 2026-09-09 (Run 12: F17 fixed —
`BasicLandDistribution.wastes` + `BasicLandCalculator.allocate`'s colourless-identity branch +
`BuildCommanderDeckUseCase.materializeBasics`/`nameToColor`, coloured behaviour byte-identical,
harness's colourless-commander exclusion re-diagnosed to a real collection-sparsity DeckTooSmall
(59 owned colourless nonland cards, not the land-fill bug) and correctly re-excluded with an
accurate comment; `persist()` closed to ONE Room `@Transaction`
(`DeckRepository.persistCommanderBuild` / `DeckDao.persistCommanderBuild`), `isWritingCommanderDeck`
kept as VM-state defense-in-depth only; cleanup deleted the zero-caller 3-axis `StrategyPickerSheet`
+ `StrategyPickerState` (every other candidate had real live callers, left untouched, see the
changelog entry below); `DECK_BUILDER_V2_ENABLED` flipped `true` after the full verify gauntlet
(harness v2 all 3 segments, golden/calibration/corpus/skeleton-differentiation suites
byte-identical, `:app:assembleDebug`, `:shared:core-domain:compileKotlinWasmJs`) — see the
changelog for the full breakdown)`

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
| Real-collection Commander builds: score distribution (min/p25/median/p75/max) | 40 / 82 / 85 / 88 / 91 (legacy Motor A build, scored by `DeckAnalysisPipeline`, 99 builds) | **78 / 85 / 88 / 91 / 96** (NEW `BuildCommanderDeckUseCase` engine, 180 builds = 90 commanders x {top recommendation, Custom}); **77 / 84 / 87 / 90 / 95 after W6/W6b/W6c** (versatility objective + real seeded variety — see W6c below for the evidenced trade-off) |
| Share of builds with 0 BLOCKER | 97.0% (3/99 had ≥1 BLOCKER) | **100% of the 180 in-scope builds** (0 BLOCKER); the 1 excluded colorless commander is a diagnosed pre-existing `BasicLandCalculator` gap, not a scored build |
| Median off-plan share (wizard-placed) | 0.0 (min 0.0, max 0.136) | 0/180 builds exceed the 15% `offplan_share` HARD ceiling (exact per-build shares not separately archived this run — every build passed the ceiling check) |
| Gap sections (`current < min`) | avg 3.17 sections/build, avg 12.96 total missing count | not re-measured as an average this run (harness v2 tracks `gap_section_count`/`gaps_total` per spec in the JSON report, `testdata/wizard-harness/reports/`, gitignored) — all 180 builds satisfied `size_or_gaps` (declared gaps + entries reach the 100-card target) |
| MockCollectionRich reconstruction delta vs fixture score | n/a | Edgar -2, Meren +1, Karlov +4, Urza +5 (tolerance >= -8; 3 of 4 beat their own hand-authored fixture) |
| Final `PlacementScorer` weights | n/a | **NO CHANGE** — `roles 0.45 · axes 0.30 · curve 0.10 · power 0.10 · community <=1.15` (initial weights), confirmed sufficient by the numbers on this row (never fitted to the real collection, ADR-007 §4 — see progress tracker Run 11 §7.2 for the full evidence) |
| Runtime per build (JVM, real collection) | min 40 ms / median 99 ms / max 332 ms (build + analyze) | min 50 ms / median 107 ms / max 231 ms (build + analyze, `BuildCommanderDeckUseCase`) |

**W6b (2026-09-15) — real-collection evidence W6's own report omitted, per the campaign's own
demand for numbers, not claims:**
- **Score distribution after W6b's F18 fix, all 3 baselines side by side:** v3 baseline 78/88/96
  (min/median/max) → post-W6 76/87/95 → post-W6b 76/87/95 (byte-identical to post-W6; F18's fix is
  scoped to Custom-mode internal tribe/tag targeting and does not move the aggregate distribution —
  re-run via `WizardCommanderHarnessV2Test`, 180/180 HARD metrics pass).
- **Score-dip diagnosis (v3 78/88/96 → post-W6 76/87/95):** diagnosed as an ACCEPTED TRADE-OFF of
  W6's E5 "versatility" placement objective, not a regression re-introduced or fixable by W6b.
  Pre-W6, placement was "effectively-first-match" (a card's marginal gain came from ~one role/axis
  it filled); W6 replaced this with a gain that sums EVERY live band/axis a candidate advances
  (diminishing returns) — a deliberate, documented design change (plan §0 E5: "a card serving three
  live needs outranks a card serving one"). Evidence for "accepted trade-off" over "bug": the shift
  is small and UNIFORM across the whole distribution (min -2, median -1, max -1 — not a handful of
  builds cratering), consistent with a systemic re-optimization toward versatile-but-not-always-
  highest-scoring cards rather than a targeted defect. No specific miscalibration (double-counted
  axis, wrong weight) was found on inspection of `PlacementScorer.axisGain`/`roleGain`; closing the
  gap further would mean re-tuning the placement objective against the SCORING engine's own weights
  — a calibration change, out of W6b's scope (golden/corpus/calibration must stay byte-identical).
- **Ambiguity volume** (questions per build = `WizardBuildResult.ambiguityGroups.size`, full
  180-spec real matrix): min 0, p25 0, median 1, p75 2, max 4; 51/180 (28%) builds ask zero
  questions. Epsilon = 0.15 (`BuildCommanderDeckUseCase.AMBIGUITY_EPSILON`, W6 Task 4/E6) — chosen
  as a relative-gain-closeness band around the strongest remaining candidate; this distribution
  (median 1, max 4, more than a quarter asking none) shows it is NOT over-firing into a "thirty
  questions" flood, nor silently mute — it triggers on genuine near-ties only. No fixture asked more
  than 4 questions in this pass.
- **Variety** (card-overlap % between two independent builds — different `deckId` seeds — for the
  same commander+strategy, W6 Task 3/E4's seeded tie-break): a deterministic 12-spec sample (every
  15th of the 180) measured 0.96–1.00 Jaccard overlap on scryfallId sets, 11/12 samples IDENTICAL
  (1.00). **Finding: in practice, variety across two different deckIds is close to zero on this real
  collection.** This is not a bug in the tie-break itself — E4 was designed to make an otherwise-
  arbitrary tie DETERMINISTIC and per-deck, not to manufacture variety — but it does mean two users
  building the same commander+strategy from the same collection will almost always get literally the
  same deck today, because exact marginal-gain ties are rare with real (non-integer, non-duplicated)
  card data. Flagged as a real product-facing limitation, not fixed (no instruction to add
  intentional randomization, and doing so would need a product decision, not an engineering one).

**W6c (2026-09-15) — real seeded variety, and real evidence for the score dip (closes both open
items W6b left above):**
- **Variety fix.** `BuildCommanderDeckUseCase`'s tie-break only fired on an EXACT float gain match,
  which almost never happens against W6's normalized, continuous scoring — E4's seeded variety was
  live in the code but dead in practice (W6b's 11/12-identical finding). Replaced with a RELATIVE
  near-tie band (`NEAR_TIE_BAND = 0.12`, distinct from and narrower than `AMBIGUITY_EPSILON = 0.15`
  per the plan's own rule): a candidate within the band of the running best gain is decided by
  `stableSeed(deckId, cardId)` instead of gain order. Applied to both the main non-land placement
  loop and `fillLandsV2`'s Stage A land ordering (`nearTieOrdered()`, a new shared helper).
- **Band chosen from a measured trade-off curve** (0.02 / 0.06 / 0.12, all < 0.15) against the full
  180-spec real-collection matrix: variety improves monotonically (median Jaccard card-overlap
  between two different `deckId` builds of the same commander+strategy: 0.88 → 0.76 → 0.65;
  identical-build count 7 → 0 → 0 of 180) while the score distribution stays flat at every band
  (median 87 unchanged in all three; min/p75/max move by at most 1 point) — 0.12 was the widest
  band tested and bought real variety at zero measured score cost, so it was kept. Two different
  `deckId`s building the same commander+strategy now typically share ~65% of their cards instead of
  being byte-identical; rebuilding the SAME `deckId` stays byte-identical (determinism HARD metric
  unaffected). Full curve + methodology: progress tracker Run 11.
- **Score-dip evidence (Defect 2).** Measured placed-card power directly (same
  `EdhrecPowerResolver` normalization the scorer uses, over every wizard-placed non-land card,
  full 180-spec matrix) in an isolated worktree at pre-W6 commit `3d5c8e04` vs. current:
  mean power **0.135 → 0.157 (+16.6% relative)**, UNIFORMLY across the whole distribution
  (every percentile +0.013 to +0.029), while score dropped by exactly 1 point at every percentile
  (min 78→77, median 88→87, max 96→95). **Verdict: EVIDENCED TRADE-OFF, not a regression** — this
  is the concrete confirmation of W6's E5 claim (card quality now genuinely decides more
  placements), at a score cost within noise. No weight retuning performed; `PlacementScorer`
  weights unchanged from the Run 12 "NO CHANGE" calibration verdict.
- Both W6b open items (`docs`'s §8, "variety ~0% in practice") are now RESOLVED: variety is real
  and measured, and the score dip has a metric behind it instead of "on inspection." Verify
  gauntlet: harness v2 180/180 HARD, `feature.decks.*` 549 tests/1 pre-existing failure (Edgar,
  unchanged), `compileKotlinWasmJs`/`compileKotlinJvm`/`assembleDebug` green, golden/corpus/
  calibration untouched (no scoring-engine file touched this run). Commit `b848b9b1`.

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
| — | `BuildCommanderDeckUseCase.persist()` is 4 sequential non-transactional writes -- a cancellation mid-write (reachable via Studio's "Rebuild with the Wizard" CTA, D12 rebuild-in-place) can leave an existing draft with cards replaced but a stale pin | `DeckRepository.persistCommanderBuild` (new) is ONE Room `@Transaction` via `DeckDao.persistCommanderBuild` on Android; commonMain default keeps the 4-call fallback for `WebDeckRepository` | **closed P8 (Run 12)** — instrumented Room tests (happy path + genuine FK-violation rollback) added mirroring the existing `replaceAllCardsWithSource` proof; `isWritingCommanderDeck` kept as defense-in-depth for the ViewModel's own in-memory state, not because the DB can land half-written any more |
| F17 | Colorless-identity commander (e.g. "Page, Loose Leaf") builds a `DeckTooSmall` BLOCKER — `BasicLandCalculator`/`BasicLandDistribution` has no colourless "Wastes" slot at all, so Stage B of `fillLandsV2` allocates zero basics for an empty identity | `BasicLandDistribution.wastes` (new field, folded into `total`/`toMap()`'s `"C"` key) + `BasicLandCalculator.allocate` returns an all-Wastes distribution when `commanderIdentity` is explicitly empty (distinct from `null` = no constraint) + `BuildCommanderDeckUseCase.materializeBasics`/`nameToColor` place Wastes like any other basic. Coloured-identity behaviour byte-identical (verified by the full pre-existing `LandFillV2Test` suite + a new colourless-identity case). | **CLOSED P8 (Run 12)** — land fill itself is fixed (`land_target`/`mana_sources` harness metrics green for "Page, Loose Leaf"). The harness's colourless-commander exclusion STAYS, re-diagnosed to a DIFFERENT, genuine cause: this real collection owns only 59 colourless nonland cards total, well under Commander's ~62-card nonland target once lands are correctly filled — a real MTG color-identity constraint (colourless commander ⇒ colourless cards + Wastes only) colliding with real collection sparsity, not an engine defect, and unfixable without a Scryfall backstop D7 forbids. → memory: `feedback_colourless_commander_deck_size_vs_land_fill`. |
| F18 | `CommanderPlanResolver.resolve`'s `StrategyPick.Custom` branch always targets the archetype-null generic BELL-shaped baseline skeleton regardless of the commander's own aggression signal, so a fast/cheap/tribal-aggressive Custom build (Edgar Markov) reads MIDRANGE post-build instead of AGGRO | `CommanderArchetypeBias.commanderTagArchetype` (new, tag-tier ONLY — the color-identity tier was tried and reverted, it regressed a real fixture) gives Custom's INTERNAL skeleton a bounded build hint; persisted `StrategyPin` stays Custom/null (E12). `CommanderPlanResolverTest`'s assertion rewritten per the plan's own instruction | **W6b (2026-09-15) — audited and made production-honest, residual CONFIRMED genuine, not closed.** W6's own "build-hint CLOSED" claim rested on a FABRICATED test input: `Fixture01EdgarMarkov`'s commander carried a hand-added `CardTag.AGGRO`, but `"aggro"` is a `TagDictionary` MANUAL-PICK-ONLY entry (`plain(...)`, no detection rule) — production never assigns it (ADR-007 §4 violation). Replaced with `CardTag.TOKENS`, which a real Edgar Markov DOES earn in production (`TagDictionary`'s `"tokens"` rule: `allOf("create","token")`, confidence 0.95 clears `SuggestTagsUseCase.DEFAULT_AUTO_THRESHOLD` 0.90 against Edgar's own oracle text) and which reverse-maps to `ArchetypeId.AGGRO` the same as the fabricated tag did — the tag-tier bias itself was legitimate, just proven on the wrong input. Also: (1) `CommanderArchetypeBias.commanderTagArchetype` was order-dependent (`firstNotNullOfOrNull`) — rewritten as a majority vote over all commander tags, alphabetical tie-break; (2) a Custom build's INTERNAL tribe target was `null` even for a tribal-lord commander (`pin.tribe` only exists for Curated) — added `CommanderPlan.internalTribe` (`TribeDeriver.derivedLordTribe`, the SAME derivation `RecommendCommanderStrategiesUseCase` already used, now extracted and shared) and wired it into `BuildCommanderDeckUseCase`'s placement-time `dominantTribeAxis`/`dominantTribeKey` + `SynergyGraph.axisIdeals`'s new optional tribe-ideal param — before this wiring the plan's tribe axis was a no-op at placement time. **Net result:** `MockCollectionRichReconstructionTest`'s Custom-macro case is GREEN for Meren/Karlov/Urza; Edgar is a genuine near-miss, margin 0.035 vs the 0.08 `MACRO_AMBIGUITY_MARGIN` needed (was already red pre-W6b for the SAME reason under the fabricated tag — confirmed by re-running at HEAD `460db325` before any W6b change). Closing it further requires touching Deck Analysis Engine v3 calibration (prototype weights/anchors), explicitly out of scope for this campaign (ADR-007 §4, golden/corpus/calibration must stay byte-identical) — reported honestly per the campaign's own "never tune a fixture/threshold to reach green" rule rather than forced. Harness v2 (180 specs) confirms 180/180 HARD metrics still pass and the score distribution is UNCHANGED by this work (min 76/p25 85/median 87/p75 89/max 95, same as post-W6). |

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
- `CandidatePoolGenerator` (checked P7, re-confirmed P8): still has a live caller —
  `BuildDeckFromTemplateUseCase`'s Scryfall backstop fill, which only Casual builds ever reach now
  that Commander dispatches to `BuildCommanderDeckUseCase`. Not dead code; do not delete without
  first retiring Casual's own build path (60-card wave, §7).
- **F17 CLOSED P8** (land fill fixed; see §6 for the re-diagnosed residual real-collection-sparsity
  case, which is not this defect and not fixable within this campaign's D7 scope).
- **F18 — real fix landed, honest residual remains OPEN (W6b, 2026-09-15)** — see §6. W6's
  "build-hint CLOSED" was proven on a fabricated tag; W6b fixed the fixture + the underlying
  mechanism (majority-vote tag bias + a real internal tribe target wired through placement). 3 of 4
  reconstruction fixtures now genuinely pass; Edgar remains a real 0.035-vs-0.08 margin near-miss
  that needs a calibration-layer change to close — explicitly out of this campaign's scope. Do not
  re-fabricate a test input to force this green; either accept the residual (update
  `MockCollectionRichReconstructionTest`'s own docstring to say "3 of 4" instead of implying all 4
  pass) or open a calibration-scoped follow-up campaign.
- **Variety CLOSED W6c (2026-09-15)** — was ~0% in practice (11/12 sampled real-collection pairs
  built byte-identical decks) because the tie-break fired only on an exact float match. Fixed with
  a relative `NEAR_TIE_BAND` (0.12) in the main placement loop and `fillLandsV2`'s Stage A ordering;
  two different `deckId`s now typically share ~65% of their cards (median Jaccard 0.65) at zero
  measured score cost. See §5's W6c entry for the full trade-off curve. No open item here any more.
- **Score-dip evidence CLOSED W6c (2026-09-15)** — W6b's "accepted trade-off, on inspection" now has
  a metric: placed-card power rose +16.6% relative (mean 0.135→0.157, uniformly across the
  distribution) for a uniform 1-point score cost, measured pre-W6 (`3d5c8e04`) vs. current. No
  weight retuning performed or needed. No open item here any more.
- **`persist()` atomicity CLOSED P8** — `DeckRepository.persistCommanderBuild` / `DeckDao
  .persistCommanderBuild` is one real Room `@Transaction`. No open item here any more.

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
| Legacy (Casual-only after this campaign) | `template/BuildDeckFromTemplateUseCase.kt`, `template/DeckTemplateResolver.kt`, `template/SuggestionCategoryResolver.kt`, `template/MainboardTrimmer.kt`, `usecase/SuggestAddsFromCollectionUseCase.kt` (Motor A), `engine/DeckScorer.kt` — all confirmed to still have real Casual callers (Phase 8, Run 12), left untouched. `components/StrategyPickerSheet.kt` was DELETED in Phase 8 (Run 12) after confirming zero remaining callers; `TribeOption` moved to `CuratedStrategyPickerSheet.kt`. |

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
- 2026-09-09 — **Phase 7 DONE (Run 11)**: full detail in the progress tracker's Run 11 log. Headline
  items future phases (especially the 60-card wave, §7) should know:
  1. **The new engine measurably beats the legacy one on the SAME real collection, without any
     calibration.** Real-collection score distribution moved from 40/82/85/88/91 (legacy Motor A,
     P0 baseline) to 78/85/88/91/96 (`BuildCommanderDeckUseCase`, 180 builds) — the long tail is
     gone (min 40->78) and every percentile improved. 0 BLOCKER across all 180 in-scope builds
     (vs. 3% before).
  2. **`PlacementScorer`'s initial weights need no retune** — verdict is evidence-backed (real
     collection reads well inside/above the healthy band without fitting; `MockCollectionRich`
     reconstruction beats 3 of 4 hand-authored fixtures), not an unexamined default. Do not
     re-litigate this without new evidence the current numbers don't already cover.
  3. **Two genuine, narrow defects were found by harness v2 and left OPEN, not force-fixed** — F17
     (colorless commanders get zero land fill, a `BasicLandCalculator` gap) and F18 (Custom builds
     never bias toward the commander's own aggression signal, so a fast tribal-aggro commander like
     Edgar Markov reads MIDRANGE post-build). Both have a concrete next step recorded (§6, progress
     tracker Run 11 §7.2) — F18 in particular needs a CONSCIOUS decision to touch D6's stable
     "Custom == null archetype" contract, not a quick patch.
  4. **The legacy `WizardQualityMatrixTest`/`HarnessMetricsCalculator`/`HarnessDoctorPipeline`
     stack was NOT deleted**, despite the plan's literal 7.1 text — it is the only automated
     coverage the Casual wizard path (still Motor A, non-goal) has. The new Commander-only harness
     v2 (`WizardCommanderHarnessV2Test` in `app/src/test`, `WizardHarnessMock{Rich,Thin}
     SegmentTest` in `shared/core-domain` commonTest) is ADDITIVE. A future cleanup (60-card wave or
     Phase 8) should rename the legacy class to make its now-Casual-only scope explicit.
  5. **Telemetry additions were smaller than the plan implied** — of the "remove stale keys" list,
     EVERY item was already structurally unreachable from the Commander path (Phase 6's
     `generateCommanderDeck`/`generateCasualDeck` split already isolated them), and one
     (`deck_wizard_include_outside_collection`) never existed as a literal telemetry key to begin
     with (only as a UI-state/string-resource name). Only the 8 ADD items + the already-existing
     `deck_wizard_blocker_after_build` (Phase 2) needed real work.
- 2026-09-09 — **Phase 8 CLOSED (Run 12) — campaign done.** Full detail in the (now-deleted) plan's
  final run; headline items future work must know:
  1. **F17 is fixed, not merely narrowed.** `BasicLandDistribution` gained a `wastes` field;
     `BasicLandCalculator.allocate` returns an all-Wastes distribution when `commanderIdentity` is
     an EXPLICITLY EMPTY set (distinct from `null` = no constraint, which keeps its pre-existing
     empty-distribution behaviour); `BuildCommanderDeckUseCase.materializeBasics`/`nameToColor`
     place Wastes exactly like any other basic. Coloured-identity behaviour is byte-identical
     (proven by the full pre-existing `LandFillV2Test` suite passing unchanged plus a new
     colourless-identity test). **The harness's colourless-commander exclusion was NOT removed** —
     re-diagnosing it after the fix found a DIFFERENT, genuine cause: this real collection owns only
     59 colourless nonland cards total (confirmed by direct inspection, not assumed), well under
     Commander's ~62-card nonland target once lands are correctly filled — a real MTG
     color-identity constraint (a colourless commander deck can only run colourless cards +
     Wastes), not an engine defect, and not fixable within D7's collection-only scope. Any future
     session that sees this exclusion should not assume it means F17 regressed.
  2. **`persist()` is now genuinely one Room transaction, not a mitigation.**
     `DeckRepository.persistCommanderBuild` (new interface method, default 4-call fallback for
     `WebDeckRepository`) is overridden on Android by `DeckDao.persistCommanderBuild`, a `suspend
     fun` DEFAULT method annotated `@Transaction` that calls the existing blocking
     (`clearDeckCards`/`upsertDeckCards`) AND suspend (`updateArchetypeOverride`/
     `updateTribeOverride`/`updateStrategyLocked`) abstract DAO methods directly — Room supports
     mixing suspend-ness inside one suspend `@Transaction` default method without `runBlocking`.
     `isWritingCommanderDeck` was kept (not removed) as defense-in-depth for the ViewModel's OWN
     in-memory state, since a coroutine cancellation can still race `withContext(ioDispatcher)`
     even though the DB itself can no longer land half-written.
  3. **Cleanup found far less genuinely-dead code than the plan assumed.** Grepping for callers
     before deleting anything found that `DeckTemplateResolver`'s Commander branch,
     `runScryfallBackstopLoop`, `MainboardTrimmer`, `ManualAddsStepContent
     .buildRoleSections`/`OutsideCollectionToggleRow`, `includeOutsideCollection`, and
     `CandidatePoolGenerator` ALL have real, live callers — Casual's own build path (explicitly
     out of scope, "Casual-only code stays") or existing test coverage
     (`DeckTemplateResolverTest`, `P0BaselineTest`). None of it was touched. The ONE genuine
     zero-caller deletion was the old 3-axis `StrategyPickerSheet.kt` + its commonMain backing
     `StrategyPickerState.kt`/test (superseded by `CuratedStrategyPickerSheet` since Phase 3;
     only their own `@Preview` referenced them). `TribeOption` — a plain (key, label) shape that
     data class also happened to define — moved to `CuratedStrategyPickerSheet.kt`, its real
     surviving consumer group.
  4. **`DECK_BUILDER_V2_ENABLED` is `true` in production as of this run**, gated on the FULL verify
     gauntlet passing first: harness v2 (real-collection 90/90 + both mock segments),
     `DeckAnalysisEngineGoldenTest`/`DeckAnalysisEngineCalibrationTest`/`DeckAnalysisV3CorpusTest`/
     `SkeletonDifferentiationTest` byte-identical (0 failures each), `:app:assembleDebug`,
     `:shared:core-domain:compileKotlinWasmJs`. The only red tests anywhere in the affected surface
     are the 4 pre-existing ones documented at the top of this campaign (3 `SectionSearchQueryTest`
     + 1 `DeckWizardViewModelTest` taxonomy-toggle case) — confirmed still red at the pre-campaign
     base commit, not a regression.
