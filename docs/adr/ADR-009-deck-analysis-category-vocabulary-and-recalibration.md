# ADR-009 — Deck Analysis Category Vocabulary and F18 Recalibration Attempt

**Status:** Accepted (vocabulary unification) / `inevitability` axis re-derived and kept (R2b amendment,
Edgar still open, honestly reported) · **Date:** 2026-09-16 · **Related:** ADR-007 (Deck Analysis
Engine v3), ADR-008 (collection sync)

## Context

Users reported that some deck-analysis categories (e.g. "Counters Payoff") showed no cards in the
Analysis tab's section, yet "Browse for Counters Payoff" against the Collection tab found cards
already in the deck carrying that category. The root cause: three independent key spaces claimed to
describe "the same category" —

1. `RoleKey` (`ArchetypeRoleClassifier.tagMatcher`, the analysis's own attribution)
2. `CardTag` keys (the tagging engine's actual detected tags on a card)
3. `CardFunctionOption.collectionTagKeys` (the Advanced Search "Card function" facet's own,
   independently curated mapping from a Scryfall `function:` value to CardTag keys)

Browse-from-a-section routed its Collection filter through (3) via a `SearchCriterion.CardFunction`
criterion, while the analysis attributed roles via (1)/(2) directly. Where (3)'s hand-curated set was
wider than the analysis's own matcher, Browse found cards the analysis ignored.

Separately, F18 (carried from the v4 campaign, `docs/deck-wizard-state.md` §6) remained open: a
Custom build for Edgar Markov infers a macro whose margin over the runner-up falls below
`CommanderArchetypeBias.MACRO_AMBIGUITY_MARGIN` (0.08), so `analysis.strategy.archetype` resolves
`null` instead of `AGGRO`. The user's decision (2026-09-15) was to recalibrate the engine in this
same run, "never tuned until Edgar passes."

## Decision 1 — `CategoryVocabulary`, one authoritative table

`shared/core-domain/.../feature/decks/domain/engine/CategoryVocabulary.kt` maps each `RoleKey` to the
exact `CardTag` key set that means membership. Both `ArchetypeRoleClassifier.tagMatcher` (analysis
attribution) and `SectionSearchQuery.collectionTagKeysFor` (Browse's Collection-tab tag filter) read
this same table, so the two can never independently drift for a role it covers.

`StructuredCardSearch.matchesForCategoryBrowse` (new) is the second half of the fix: when a section's
Browse query carries a `SearchCriterion.CardFunction` criterion (the ~21 roles that route through
`SectionSearchQuery.DIRECT_ORACLE_TAGS`), that criterion is now EXCLUDED from local Collection
matching and replaced by `CategoryVocabulary`'s own tag-key set, ANDed with the query's remaining
(identity/legality) criteria. `CardFunctionOption.collectionTagKeys` remains the correct vocabulary
for the general Advanced Search "Card function" facet (a genuinely different key space, audited
2026-08-24, `feature/decks/CLAUDE.md`) and is never read from category Browse again. For a section
whose category criterion is production-text-based (`CardType`/`OracleTerms`/`ManaCost`/
`ManaProduction` — already derived from the same `TagDictionary` rule the classifier itself reads),
the pre-existing "match if either the full query or the tag is present" tolerance (Deck Wizard v4
G14) is kept unchanged: it protects an untagged-but-structurally-matching card the tagging engine
has not caught up to yet, and does not share H2's CardFunctionOption-vocabulary risk.

### Per-key membership decisions (cited against `TagDictionary.kt`)

| RoleKey | Decision | Citation |
|---|---|---|
| `counters_payoff` | **Widened** to `{counters_payoff, plus_counters}` | `TagDictionary.kt:646-648` (`counters_payoff`, ROLE, `allOf=["+1/+1 counter"]`) and `:365-367` (`plus_counters`, STRATEGY, `allOf=["+1/+1 counter"]`) run the IDENTICAL rule; only category/confidence (0.85 vs 0.95) differ, so a card can auto-confirm one before the other clears the user's own `autoThreshold`. |
| `landfall_payoff` | **Widened** to `{landfall_payoff, landfall}` | `TagDictionary.kt:640-642` (`allOf=["landfall"]`) vs `:201` (`kw("landfall", "Landfall")`, the printed ability word). Any card with the Landfall keyword's own oracle text ("Landfall — Whenever...") already contains the substring "landfall", so the keyword tag is a strict subset of the payoff rule. |
| `tribe_payoff` | **Kept narrow** (`{tribe_payoff}` only) | `TagDictionary.kt:715-720` (`tribe_payoff`, oracle patterns naming a chosen/shared creature type) vs `:799` (`plain("tribal", STRATEGY)` — NO `DetectionRule` at all, a structurally/manually-assigned flag). Not the same detection path; widening would let a merely-tribal-flavored card (e.g. any creature carrying the coarse `tribal` tag) count as a specific tribal-payoff card. |
| `death_payoff` | **Kept narrow** (`{death_payoff}` only) | `TagDictionary.kt:613-617` (`allOf=["creature you control dies"]`) vs `death_triggers` STRATEGY tag (`:514-518`, broader "whenever a/any creature dies", including triggers off an opponent's creature). Genuinely different scope, not proven identical. |
| `reanimation` | **Kept narrow** (`{reanimation}` only) | `TagDictionary.kt:623-629` (`allOf=["creature card","graveyard","to the battlefield"]`) vs `reanimator` STRATEGY (`:450-453`, phrase-anchored `allOf` combinations). Overlapping but not proven identical for every phrasing (e.g. `reanimator`'s "onto the battlefield" rule 3 does not require "creature card" to co-occur). |
| `mill_engine` | **Kept narrow** (`{mill_engine}` only) | `TagDictionary.kt:655-657` (`anyOf=["target player mills","each opponent mills","that player mills"]`, opponent-targeted) vs `mill` STRATEGY (`:455-458`, broader — includes self-mill). Different scope. |
| Every other `DIRECT_TAG_ROLES` key | **Default** (own bare key) | No multi-key `CardFunctionOption` mapping existed, or the extra key(s) were themselves structural/non-tag facts already handled elsewhere (e.g. `mana-producer`→`{mana_rock, mana_dork}` is two ALREADY-distinct roles unioned under one Scryfall function, not a vocabulary drift case). |

### The 5 legacy roles — deliberately excluded, accepted debt

`ramp`, `card_draw`, `removal_spot`, `removal_mass`, `tutor` (`ArchetypeRoleClassifier.LEGACY_ROLE_MAP`)
are detected by the pre-existing `RoleClassifier`'s own tag **+ private oracle-text fallback**, reused
wholesale, not re-implemented in `ArchetypeRoleClassifier`. `ramp`/`card_draw`/`tutor` default to a
singleton `CategoryVocabulary` set of their own bare key (agreeing with `RoleClassifier`'s tag signal);
`removal_spot`/`removal_mass` have no matching CardTag at all (only a generic, ambiguous `"removal"`
tag) and return `emptySet()`.

**Known, accepted asymmetry:** `RoleClassifier`'s oracle-text fallback (private `ROLE_PATTERNS`
regex table) is invisible to `CategoryVocabulary`/Browse — a tag-less-but-oracle-detected ramp,
card-draw, tutor, or removal card is counted by the analysis (`classify()` / `deckRoleCounts`) but
never surfaced by "Browse for X" against the Collection tab. This is NOT closed by this run (closing
it would mean exposing `RoleClassifier`'s private regex table publicly, a larger refactor out of this
run's scope). `CategoryVocabularyParityTest.legacyOracleOnlyRampCard_isInvisibleToVocabularyMembership`
proves this gap exists (a synthetic land-fetch ramp spell with no tag: `classify()` attributes `ramp`
at confidence 0.9, `CategoryVocabulary.cardTagKeysFor("ramp")` membership check returns `false` for
the same card) — the parity suite does not silently skip this case behind a scope filter; it makes the
one-directional guarantee explicit and machine-checked. The suite's OTHER assertion
(`confirmedTagMembership_isAlwaysASubsetOfClassifiedRole`) is correspondingly a one-directional
subset check, not full bidirectional equality, and its own comment says so.

### Before/after (corpus + real collection)

X0 changed only `counters_payoff`/`landfall_payoff` membership and the Browse-side matching logic —
no `ArchetypeData` band, weight, anchor, or prototype value moved. Verified:

| Suite | Before (commit `aa7548a3`, isolated worktree) | After (commit `f8310560`/`6842d875`) |
|---|---|---|
| `:shared:core-domain:jvmTest` (`feature.decks.*`) | 575 tests, 1 failed (`MockCollectionRichReconstructionTest`, "Custom-vs-fixture macro mismatches ... resolved null" for both Edgar/Meren) | 575 tests, 1 failed — **byte-identical failure message** |
| `:app:testDebugUnitTest` (`feature.decks.*`) | not separately re-run at baseline (unnecessary — the shared-module comparison above already isolates X0's effect on scoring/attribution) | 572 tests, 1 failed, 2 skipped (unrelated, pre-existing — see Deviations) |
| `WizardCommanderHarnessV2Test` (real collection, 180 specs) | 77/84/87/90/95 (min/p25/median/p75/max), post-W6b baseline recorded in `docs/deck-wizard-state.md` | 77/85/87/90/95, 180/180 HARD metrics pass, runtime min 44/median 112/max 246 ms |
| `WizardHarnessV3RealCollectionTest` | determinism 180/180, variety not separately re-measured at this exact baseline | determinism 180/180, median Jaccard 0.610 (≤ 0.65 ceiling) |

No fixture's macro, score, P3, or P4 value moved. The vocabulary widening/narrowing changed local
Collection-Browse *and* classifier-attribution behavior for two specific tag pairs, but no committed
fixture happens to exercise a card where that changed which side of a role-count threshold it landed
on.

## Decision 2 — F18 (Edgar Markov AGGRO): recalibration attempted, not forced

### What was tried

1. **Vocabulary/matcher coverage (X0 above).** Verified to have zero effect on Edgar's/Meren's
   inference — confirmed via an isolated `git worktree` at the pre-X0 commit reproducing the
   byte-identical failure message.
2. **Commander tag prior (`CommanderArchetypeBias.commanderMacroPrior`).** Edgar's fixture-authored
   commander tags are `[tokens]` (a `ThemeId` tag, not one of `DeckIdentitySeedTags.archetypeForTag`'s
   macro-reverse-mapped tags) — this prior contributes **zero bonus** to any macro for this fixture.
   The fixture's own card data is immutable per this campaign's rules; the prior mechanism itself was
   already exercised and closed in the prior campaign (W6b, `docs/deck-wizard-state.md` §6, using
   `CardTag.TOKENS` specifically because it IS what a real Edgar Markov earns in production — this
   run found nothing new to add there).
3. **`CommanderPlanResolver`'s Custom-branch baseline skeleton.** Not the mechanism this specific
   test exercises — `MockCollectionRichReconstructionTest`'s "Custom build resolves the fixture's own
   expected macro" test reads `customOutcome.result.analysis.strategy.archetype`, the POST-BUILD
   `InferDeckArchetypeUseCase` inference over the built deck, not the pre-build resolver's internal
   skeleton target. No lever here for this specific failure mode.
4. **Prototype/anchor derivation (`InferDeckArchetypeUseCase`).** Diagnosed in detail below.

### Diagnosis (measured, this run, post-X0)

Dumped via a throwaway diagnostic test (not committed) running `BuildCommanderDeckUseCase` against
`MockCollectionRich`'s owned pool for Edgar Markov's Custom build:

```
commanderTags=[tokens]
nonLandCount=65  avgMv=2.938
axes: clock=0.577  interaction=0.197  inevitability=0.636  linearity=0.446
inference.macro=null  confidence=0.0262  runnerUp=AGGRO
resemblance: MIDRANGE=39.1%  AGGRO=34.5%  COMBO=20.6%  CONTROL=5.8%  PRISON=0.0%
```

AGGRO's own Commander prototype is `(clock=0.85, interaction=0.20, inevitability=0.15, linearity=0.55)`.
The deck's `interaction` (0.197) and `linearity` (0.446) both land close to AGGRO's own coordinates.
The dominant mismatch is `inevitability`: the deck measures 0.636 against AGGRO's prototype target of
0.15 — a 0.486 gap, by far the largest single-axis contribution to AGGRO's distance from the deck.

**Self-consistency check** (the same method `InferDeckArchetypeUseCase`'s own KDoc already uses to
derive the `clock`/`interaction` anchors — run AGGRO's own `ArchetypeData` Commander band through the
SAME density/anchor formula and compare to AGGRO's own prototype coordinate): AGGRO's Commander band
(`card_draw` ideal 8, `finisher` ideal 9, no `tutor`/`recursion` band) over its own 65 nonland slots =
`(8+9)/65 = 0.262` density, `/ INEVITABILITY_ANCHOR_COMMANDER (0.484) = 0.541`. AGGRO's OWN textbook
band, run through this exact formula, lands at `inevitability≈0.54` — nowhere near its own prototype's
`0.15`. Unlike `clock`/`interaction` (whose anchors were explicitly SOLVED so AGGRO's own band hits
AGGRO's own prototype, per that file's documented derivation), `INEVITABILITY_ANCHOR_COMMANDER` was
derived from **COMBO's** own band (COMBO owns the highest inevitability prototype, 0.90) with no
cross-check against AGGRO. This is a real, citable inconsistency — but fixing it is not a narrow,
low-risk change:

- The `spec §2.1` prototype table (AGGRO inevitability = 0.15) is transcribed verbatim from ADR-007's
  own spec — changing it requires overriding a source ADR-007 explicitly marks normative, not an
  anchor-derivation judgment call this file already owns.
- The anchor constant IS this file's own judgment call, but it is a SINGLE shared value across all 5
  macros for one format; re-deriving it to better fit AGGRO's own band would very plausibly move
  every OTHER macro's inevitability reading (verified by hand: MIDRANGE's own Commander band, run
  through the same formula, lands at `0.951` — even further from its own prototype `0.45` than AGGRO's
  gap — so the axis's inevitability weighting/anchor scheme likely needs a genuine redesign, not a
  single-constant nudge, to be self-consistent across macros).
- A redesign at that scope risks moving macro resolution for fixtures elsewhere in the 22-fixture
  corpus in ways this run cannot fully re-verify within the "never tune until Edgar passes, never let
  corpus accuracy regress" discipline (ADR-007 §4) without a dedicated follow-up workstream.

### Stop condition invoked

Per the campaign's decision (D1) and its own explicit stop clause: **no principled, narrowly-scoped
change resolves Edgar's Custom build to AGGRO within this run.** This matches the PRIOR campaign's own
conclusion (`docs/deck-wizard-state.md` §6, W6b, 2026-09-15): "Closing it further requires touching
Deck Analysis Engine v3 calibration (prototype weights/anchors), explicitly out of scope for this
campaign... reported honestly ... rather than forced." This run's own, more precise diagnosis
(the `inevitability` axis's anchor derivation is provably inconsistent across at least 2 of the 5
macros when self-checked against their own `ArchetypeData` bands) narrows the FUTURE fix's scope
(the `inevitability` axis specifically, not a full re-derivation of all 4 axes) without attempting
that fix now.

**No calibration constant, anchor, weight, or prototype value was changed in this run.**
`MockCollectionRichReconstructionTest`'s "Custom build resolves the fixture's own expected macro" test
remains red, unmodified, for the same reason it was red before this run (Edgar AND now also Meren,
whose margin is 0.068 < 0.08 — both genuine near-misses, not identical to the state doc's prior
single-fixture description, which predates X0 and predates a re-measurement at this exact commit).
`CommanderPlanResolverTest`'s D6 assertion was not touched. No fixture card data was touched.

## Re-baseline

Since no calibration value moved, there is nothing to re-baseline: every golden/corpus/calibration/
skeleton-differentiation suite's expected values are unchanged from before this run (confirmed by the
before/after table above — 575/575 minus the one pre-existing failure, both before and after).

## Known debt (carried + new)

- **F18/Edgar (and now explicitly Meren) remain open**, reported to the user per D1's own stop clause.
  Future work: redesign the `inevitability` axis's per-macro weighting/anchor scheme (not a single
  constant), re-validated against the full 22-fixture corpus before landing.
- **Legacy-role Browse/analysis asymmetry** (`ramp`/`card_draw`/`removal_spot`/`removal_mass`/`tutor`):
  documented above, proven by a dedicated test, not closed.
- `RoleClassifier`'s private oracle fallback tables remain a black box to `CategoryVocabulary` — any
  future desire to close the legacy-role gap needs to either widen `RoleClassifier`'s public surface or
  duplicate its regex patterns (with the drift risk that implies), a real trade-off not resolved here.

## Amendment (R2b, 2026-09-16) — `inevitability` axis re-derivation

**User decision:** fix `inevitability` now, in a dedicated run, before the remaining wizard
workstreams (`docs/plans/deck-wizard-commander-v5-plan.md` §3 X6b). Same discipline as Decision 2:
derive from `ArchetypeData` bands + arithmetic, never tune until Edgar passes; if corpus accuracy
regresses, revert.

### Corrected self-check arithmetic

R2's own diagnosis undercounted every macro's density: `ArchetypeSkeletonResolver.resolve`
(`roles = generic.roleTargets + archetype.roleTargets`, override wins on key collision) means a
macro that does not override `recursion`/`tutor` still inherits `GENERIC`'s own band for that key —
R2's hand arithmetic dropped that inherited term (e.g. it read AGGRO Commander as
`card_draw(8)+finisher(9)=17`, omitting `GENERIC`'s own `recursion(2)`/`tutor(2)`). The table below
uses the corrected, resolver-accurate per-macro sums.

### Per-macro derivation — Commander (nonland = 100 − macro's own lands ideal)

| Macro | card_draw | recursion | tutor | finisher | sum(cd+rec+tut) | nonland | density (no finisher) | prototype | OLD anchor (0.484) → value → residual | NEW anchor (0.394) → value → residual |
|---|---|---|---|---|---|---|---|---|---|---|
| AGGRO | 8 | 2 (generic) | 2 (generic) | 9 | 12 | 65 | 0.1846 | 0.15 | 0.6677 → **+0.518** | 0.4683 → **+0.318** |
| MIDRANGE | 12 (generic) | 4 | 2 (generic) | 11 | 18 | 63 | 0.2857 | 0.45 | 0.9513 → **+0.501** | 0.7247 → **+0.275** |
| CONTROL | 13 | 2 (generic) | 2 (generic) | 6 | 17 | 62 | 0.2742 | 0.80 | 0.7667 → −0.033 | 0.6955 → −0.105 |
| COMBO (anchor source) | 12 | 2 (generic) | 8 | 5 | 22 | 62 | 0.3548 | 0.90 | 0.9000 → 0.000 | 0.9000 → 0.000 |
| PRISON | 6 | 2 (generic) | 4 | 5 | 12 | 64 | 0.1875 | 0.70 | 0.5490 → −0.151 | 0.4756 → **−0.224** |

`NEW anchor = (12+2+8)/62 / 0.90 = 0.35484/0.90 = 0.39427 ≈ 0.394` (same COMBO-band-per-format
derivation method the file already documents, applied to the reduced 3-term sum).

### Per-macro derivation — 60-card (nonland = 60 − macro's own lands ideal)

| Macro | card_draw | recursion | tutor | finisher | sum(cd+rec+tut) | nonland | density (no finisher) | prototype | OLD anchor (0.859) → value → residual | NEW anchor (0.668) → value → residual |
|---|---|---|---|---|---|---|---|---|---|---|
| AGGRO | 2 | 0 (no band) | 0 (no band) | 12 | 2 | 39 | 0.0513 | 0.10 | 0.4181 → **+0.318** | 0.0768 → −0.023 |
| MIDRANGE | 4 | 0 | 0 | 13 | 4 | 36 | 0.1111 | 0.40 | 0.5501 → **+0.150** | 0.1664 → **−0.234** |
| CONTROL | 11 | 0 | 0 | 5 | 11 | 34 | 0.3235 | 0.75 | 0.5481 → −0.202 | 0.4845 → **−0.266** |
| COMBO (anchor source) | 13 | 0 | 8 | 6 | 21 | 37 | 0.5676 | 0.85 | 0.8494 → 0.000 | 0.8500 → 0.000 |
| PRISON | 4 | 0 | 4 | 4 | 8 | 38 | 0.2105 | 0.60 | 0.3678 → −0.232 | 0.3153 → **−0.285** |

`NEW anchor = (13+0+8)/37 / 0.85 = 0.56757/0.85 = 0.66773 ≈ 0.668`.

### Density-definition finding, applied

Per this run's brief, the density formula itself was checked before touching the anchor scheme:
`finisher` density **anti-correlates** with the inevitability prototype ranking across the 5 macros
— AGGRO/MIDRANGE (low/mid prototype) carry the HIGHEST finisher counts (creature-based board
closers, already the `clock` axis's own signal), while COMBO/PRISON/CONTROL (the 3 highest
prototypes) carry the lowest. No single scalar transform (multiplicative anchor, or even a two-point
affine fit through the two extremal macros — verified by hand, worse on 3 of 5 macros) can make a
formula containing a term that moves the *wrong direction* relative to its own target
self-consistent. `finisher` stays a legitimate skeleton/placement role elsewhere (unchanged); it is
removed ONLY from this axis's own density sum, on the model-fidelity grounds spec §2.1's own
framing already supports ("if the game goes long, do I win" describes resource resilience — the
grindy engine, not a closer already counted by `clock`).

**Considered and rejected:** an unconstrained 4-weight least-squares fit across the 5 macros
(`numpy.linalg.lstsq`) was computed for reference — it produces a NEGATIVE `finisher` weight
(−5.26, Commander) to cancel the anti-correlation. Rejected: a negative coefficient on a named spec
ingredient is empirical curve-fitting to 5 data points, not a citable modelling decision, and ADR-007
§4 forbids fitting to any corpus (the prototype table itself, treated as a curve target, is exactly
that anti-pattern). The finisher-removal fix keeps the SAME derivation method already used for
`clock`/`interaction` (a documented judgment call about which named/extra ingredients belong in the
sum, then ONE macro's own band solves the anchor) — it is not a new mechanism.

**Not touched:** `CONTROL`'s known inability to reach its own 60-card `inevitability` prototype
(ADR-007 "Known debt") is the SAME structural gap (`recursion`/`tutor` unbanded for 60-card CONTROL)
— this amendment's residual for CONTROL worsens slightly (−0.202 → −0.266, 60-card) as a direct,
expected consequence of removing `finisher`'s partial compensation, not a new defect. Left as
carried, already-deferred debt.

### Corpus before/after (`DeckAnalysisV3CorpusTest.everyFixture_matchesSpecExpectations`, 22 fixtures)

| | Before (R2 baseline, commit `08fd04ed`) | After (this amendment) |
|---|---|---|
| ORIGINAL (1–16/17) macro correct | 6/16 | **8/16** |
| ORIGINAL themes correct | 16/16 | 16/16 |
| ORIGINAL posture correct | 14/17 | **15/17** |
| ORIGINAL in-band (65–95) | 15/16 | 15/16 |
| NEW 60-card (18–22) macro correct | 3/5 | 3/5 |
| NEW 60-card themes/posture/in-band | 5/5 / 5/5 / 5/5 | 5/5 / 5/5 / 5/5 |

Per-fixture: fixture 4 (Omnath, was `null`) and fixture 16 (Tron, was `null`) now resolve their
expected `MIDRANGE`. Fixture 8 (Chainer)'s posture now correctly detects `ATTRITION`. **Zero
fixtures flip from correct to incorrect** (verified line-by-line against both raw CSVs); the negative
fixture (17) still correctly resolves `null` (P4 unchanged at 25). Edgar (1) stays `null`, confidence
rose `0.0088 → 0.0476` (still below the 0.08 `MACRO_AMBIGUITY_MARGIN`). No score, P1–P5, or theme-set
value moved for any fixture that was already correct before this amendment.

### Edgar / Meren (`MockCollectionRichReconstructionTest`, wizard-built decks against the real collection)

| Fixture | Before (R2) | After |
|---|---|---|
| Edgar Markov (expected AGGRO) | resolved `null`, confidence 0.0262 | resolved `null`, confidence 0.0150 — **still red, still honestly reported** |
| Meren (expected MIDRANGE) | resolved `null`, margin 0.068 < 0.08 | resolved `MIDRANGE`, confidence 0.140 — **now correct** |
| Karlov (expected MIDRANGE) | correct | correct, confidence 0.209 |
| Urza (expected COMBO) | correct | correct, confidence 0.140 |

`MockCollectionRichReconstructionTest`'s "Custom build resolves the fixture's own expected macro"
test stays red for Edgar alone (was red for Edgar AND Meren before). Its "own strategy pin
reconstructs within tolerance" test stays green (wizard scores 86/87/85/88 vs fixture 82/87/82/83 —
all within the 8-point tolerance, unchanged shape). `CommanderPlanResolverTest`'s D6 assertion:
untouched, still green.

### Harness (real collection, `testdata/wizard-harness/`, gitignored)

| Metric | R2 baseline | After |
|---|---|---|
| Harness v2 — HARD metrics | 180/180 pass | 180/180 pass |
| Harness v2 — score min/p25/median/p75/max | 77/85/87/90/95 | 77/84/87/90/96 |
| Harness v2 — runtime ms min/median/max | not separately re-measured at R2 | 53/121/253 |
| Harness v3 — determinism | 180/180 | 180/180 |
| Harness v3 — variety, median Jaccard | 0.610 | 0.610 (unchanged) |

### Test re-baseline (not fixtures)

Three synthetic unit tests in `InferDeckArchetypeUseCaseTest` (`controlShapedDeckClassifiesAsControl`,
`standardControlShapedDeckClassifiesAsControl`, `resemblanceProfileAlwaysHasAllFiveMacrosSummingToOne`)
built their CONTROL-shaped decks with only 4 `DRAW_ENGINE`-tagged filler cards, relying on 4
`WIN_CON`-tagged "finisher" cards to close the gap to CONTROL's own `inevitability` prototype — a
gap this amendment's own table shows was there even before (CONTROL residual was already negative at
R2). With `finisher` removed from the axis, these 3 synthetic decks no longer carry enough
`card_draw` density and resolve `null` instead of `CONTROL`. These are hand-built synthetic decks
(NOT `analysisv3/Fixture*` or `MockCollectionRich`/`Thin` — the immutable fixture rule does not cover
them), so the filler count was raised 4 → 10 to give the deck a genuine, textbook-sized Commander
control card-advantage suite (`ep.658` baseline cites 12 card advantage) that carries the axis on its
own. No assertion's expected macro changed; only the input deck's own composition was widened to
still be a legitimate CONTROL shell under the corrected formula.

### Gate

`:shared:core-domain:jvmTest --tests "com.mmg.manahub.feature.decks.*"`: 576 tests, 1 failed (Edgar,
same single failure as the R2 baseline — no new failures). `:shared:core-domain:compileKotlinWasmJs`
+ `compileTestKotlinWasmJs`: green. `:app:testDebugUnitTest --tests "com.mmg.manahub.feature.decks.*"`:
572 tests, 0 failed, 2 skipped (pre-existing, unrelated). `:app:assembleDebug`: green.

### Decision: keep, not revert

Corpus macro accuracy improved (6/16 → 8/16, zero regressions), posture accuracy improved (14/17 →
15/17), Meren newly resolves correctly, harness numbers are unchanged within noise, and no relative/
ordering invariant moved. Per D1's own success gate, this clears the bar to keep. Edgar remains red —
an honest result, not the target function: AGGRO's own residual improved (+0.518 → +0.318) but did
not clear zero, because AGGRO's `card_draw`/`recursion`/`tutor` band is genuinely non-trivial (12,
inherited `recursion`/`tutor` included) against a very low prototype (0.15) — closing this fully
would need a further, dedicated redesign (weighting `recursion`/`tutor` differently from `card_draw`,
or a per-format review of whether `GENERIC`'s inherited `recursion(2)`/`tutor(2)` bleed-through
belongs in every macro's reachability at all), left as the next `inevitability` follow-up rather than
forced here.
