# ADR-007 — Deck Analysis Engine v3

**Status:** Accepted · **Date:** 2026-08-27 · **Supersedes:** the v2 engine's archetype resolution and SYNERGY pillar

## Context

The v2 engine had two compounding defects, measured on a purpose-built 22-deck regression corpus.

**The resolved archetype gated ~70% of the total score** (P1 reads `lands` at weight 0.20, P2 reads
`curve`/`shape` at 0.15, P3 reads `roleTargets` at 0.35) — and the resolver fell through to a hardcoded
`MIDRANGE` default in the majority of real decks. Baseline measurement: **14 of 16** archetype-canonical
fixtures collapsed to MIDRANGE, including textbook Aggro, a Combo deck, and a deliberate Prison deck.
Only **1 of 16** landed in the documented 65–95 "well-built" band.

**The SYNERGY pillar measured tag homogeneity, not synergy** — no card-to-card model, no producer→payoff
direction. A well-built UW Control deck scored 27/100. Separately, `SPELLSLINGER` fired as a spurious
theme on **15 of 17** fixtures, including tribal vampires, tokens and stax.

The deepest symptom: a deliberately incoherent "goodstuff pile" resolved MIDRANGE at **confidence 1.0**.
The engine could not distinguish coherence from card quality.

## Decisions

### 1. Taxonomy: 7 macros → 5, plus a posture layer

`AGGRO · MIDRANGE · CONTROL · COMBO · PRISON`. `RAMP` and `TEMPO` became **postures**, not macros —
they overlapped `CONTROL`/`AGGRO` and split the vote, which was an undocumented cause of the MIDRANGE
bias. `GENERIC` was removed entirely; ambiguity is now modelled as `macro: ArchetypeId?` = `null`.

Posture is a second layer (`RAMP · TEMPO · ATTRITION · TOOLBOX · VOLTRON · GROUP_HUG · GROUP_SLUG`)
applied between archetype and themes, reusing the existing `adds`/`relaxes`/`landsDelta`/`curveDelta`
machinery — a data change plus one resolver hook, not a new mechanism.

### 2. Macro resolution: prototype vectors, not thresholds

Five archetypes as points in a 4-D `(clock, interaction, inevitability, linearity)` space;
`macro = argmax` of similarity, `confidence` = normalised margin to the runner-up. MIDRANGE now
**competes as the centre of the space** — which is its actual definition in Magic — instead of being an
unscored fallback.

### 3. Themes come from live synergy axes, not densities

Axes are **derived**, never hand-authored: an axis exists iff some role produces it and some role
consumes it. A theme is live when its axis health clears a threshold. This is the single change that
killed the MIDRANGE bias — six token generators with zero payoffs no longer trip the TOKENS theme.

Result: **theme accuracy went from ~0 useful (15/17 spurious) to 16/16 exact set equality**, and has
held through every subsequent workstream. It is the most robust outcome of this work.

### 4. Calibration is normative, never empirical

**The corpus is a regression harness, not a training set.** Every band, weight and anchor cites an
`ArchetypeData` band ideal plus a spec prototype value plus arithmetic. Fitting to the corpus would
measure "do you look like my fixtures", not "are you well built".

This was enforced repeatedly: a calibration run discarded its first `linearity` definition specifically
*because* it scored better on one fixture while breaking the negative-fixture invariant.

### 5. Archetype-dependent weights

`AnalysisWeights` is a function of `macro`, plus a live-theme modifier (±0.05 between P3 and P4,
capped at 0.10). A `null` macro uses the MIDRANGE row, since the spec frames MIDRANGE as the centre of
the space.

### 6. Resemblance profile — there is never a "no plan" bucket

The resolver always emits the ranked, normalised similarity across all five macros
(`46% Control · 25% Midrange · 24% Prison · 3% Combo · 0% Aggro`), normalised as **advantage over the
worst-fitting macro** so the spread is legible. Chosen over softmax because it adds no constant to
calibrate and preserves genuine ties.

Rationale, in the user's framing: an ambiguous deck should be told **what it resembles** so the player
can steer toward a coherent plan — not handed a shrug. A silent fallback to `null` would have been the
old MIDRANGE disease under a new name.

### 7. The commander is a prior — it biases, never overrides

`commanderTags` had been threaded into the resolver and read **nowhere**. It now contributes a bonus
capped at exactly `MACRO_AMBIGUITY_MARGIN` (0.08), so it can never manufacture a win over a candidate
the deck's own composition already separated by more than that margin. Colour identity is a weaker
tiebreak (~0.03) via the existing curated `ColorStrategyAffinity` table.

The design test it passes: Grand Arbiter's prior correctly identifies PRISON via `CardTag.STAX`, but
PRISON's prototype demands a `linearity` the model cannot produce (see Debt below) — a gap ~5× the
bonus — and **the prior correctly refuses to paper over it**. A prior that fixed a fixture by
overpowering a structurally broken coordinate would be masking the defect.

### 8. P4 SYNERGY: composite formula, band-shaped, and unmeasurable ≠ zero

`0.40·coverage + 0.35·axisHealth + 0.15·connectivity + 0.10·consistency`, minus an anti-synergy penalty
capped at 0.15, then band-shaped through coverage bands that vary **by macro and by whether any theme is
live** — so 30% coverage reads as excellent for a Control deck and poor for a tribal deck: the same
number, two correct verdicts.

**When a deck has literally zero synergy edges, P4 is not applicable rather than 0**, and its weight
redistributes across the other pillars. Reporting 0 was false precision — it asserted "maximally
incoherent" when the truth was "this pillar cannot see this deck". The threshold is deliberately crisp
(zero edges) rather than a tunable minimum, which would be another constant to calibrate.

### 9. Interaction is not off-plan

The `"offplan"` garbage bucket split into `"interaction"` (removal/counterspell/protection — legitimate),
`"standalone"` (individually strong, no edges) and `"offplan"` (no edges **and** no plan role).
A Swords to Plowshares must never read as off-plan.

## Spec amendments made during implementation (all user-approved)

| § | Amendment | Evidence |
|---|---|---|
| 5.2 | `counterspell` removed from SPELLS payoffs | every control deck read as spellslinger; both Control fixtures resolved PRISON |
| 3 | Posture detected regardless of macro confidence | Omnath's ~24 ramp copies were never evaluated because macro was ambiguous |
| 9 | Fixture density is normative (63 non-land + commander + 36 lands) | corpus was ~2.5× under-dense, making absolute-count bands unsatisfiable |
| 5.2 | New `LOCK` axis (`stax_piece`, payoff-optional) | PRISON was structurally unmeasurable in both formats |
| 7 | Unmeasurable ≠ zero | a correct midrange deck scored below the incoherent pile |

## Results

| Metric | v2 baseline | v3 |
|---|---|---|
| Theme set correct | ~0 useful (15/17 spurious SPELLSLINGER) | **16/16** |
| Macro correct | 2/16 (14/16 collapsed to MIDRANGE) | 6/16 + honest ambiguity + resemblance profile |
| In band 65–95 | 1/16 | **15/16** Commander, 5/5 new 60-card |
| Negative fixture | MIDRANGE at confidence 1.0 | genuine top-two tie, zero live axes, P4 25 |
| P4 | flat and uninformative | discriminating, no false-positive conflicts |

The corpus grew from 17 to 22 fixtures; 60-card coverage went from 3 fixtures (0/3 macro, 1/3 in band)
to 8 (3/8 macro, 6/8 in band), adding the first COMBO and PRISON witnesses in that format.

## Known debt

- **CONTROL cannot reach its own 60-card `inevitability` prototype (0.75).** Even at the maximum of its
  own bands the achievable density maps to 0.711. Two witnesses (Grixis, Azorius). Disproved as an
  anchor-calibration problem by re-deriving from CONTROL's own band. Cause: 60-card CONTROL has no
  `tutor` and no `recursion` band, so two of four ingredients are structurally absent. Fixing it means
  choosing between lowering the prototype and changing what the engine asks a control deck to be — both
  normative, deferred.
- **PRISON still does not resolve** even with the `LOCK` axis (Grand Arbiter `linearity` 0.000 → 0.219,
  Death and Taxes → 0.300, both axes live, neither yet argmax).
- **Macro accuracy is 6/16.** Residual error is prototype-vector and matcher-coverage, not resolver
  logic. P4 quality is partly downstream of it, since macro selects P4's coverage band.
- **`DeckFormat.DRAFT` has no skeleton** — P1–P4 hardcoded to 100, so a Draft deck's total is
  effectively "is it legal". Not routed through the new resolver.
- **The Deck Wizard shares `ArchetypeId`/`PostureId`/`ThemeId` with the engine**, so the taxonomy change
  required compat shims across ~30 files and dropped its "Balanced" picker row. The Wizard is inactive
  and slated for a full rework — see memory `project_deck_wizard_rework_debt_2026-08-26`.
- **Commander Spellbook and Scryfall Tagger enrichment deferred** (plan §6 tasks 3–4, both optional).
  Any future implementation must route through a rate-limited queue per ADR-005 and must leave the score
  fully computable with no network.

## The reusable lesson

Three separate defects in this work shared one shape: **a model structurally unable to express what it
was asked to measure, rather than one merely mis-tuned.** Guessed projection anchors made the macro
argmax confidently wrong; theme-shaped axes were blind to macro identity; PRISON's prototype demanded a
coordinate no stax deck could produce. In each case the tempting fix was a threshold — and in each case
a threshold would have been useless.

The tell was available each time: check which inputs the model can even *see* before tuning what it
outputs. Theme accuracy sitting at 16/16 while macro sat at 4/16, on the same run, proved the matchers
were fine and only the projection was wrong.

Corollary on evidence: when a metric reads zero, build a **second independent witness in a different
format** before concluding. One deck measuring zero looks like a mis-authored fixture; the same exact
zero from another archetype in another format is a property of the model. These demand opposite
responses and are indistinguishable from a single data point.
