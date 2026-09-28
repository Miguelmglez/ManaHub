# ADR-010 — Deck Wizard: One Build Engine for Every Format, and a Copy Policy

**Status:** Accepted · **Date:** 2026-09-17 · **Related:** ADR-007 (Deck Analysis Engine v3),
ADR-009 (category vocabulary), `docs/deck-wizard-state.md` (§3 architecture contract, §4 D17-D26,
§5 numbers, §7 what stays open)

## Context

Until 2026-09-16 the Deck Wizard had two build pipelines. Commander and Commander Casual built through
`BuildCommanderDeckUseCase` — `PlacementScorer` on the v3 analysis primitives, verified and refined by
`DeckAnalysisPipeline`, so a build and its own Studio analysis could never disagree. The three
"Start from cards / colors / strategy" options, and every 60-card format, still ran the legacy Motor A
pipeline (`BuildDeckFromTemplateUseCase` + `DeckTemplateResolver` + `MainboardTrimmer` +
`CandidatePoolGenerator`) with its own vocabulary (`DeckTemplate`, `DeckWizardSpec`,
`SuggestionCategory`), its own strategy sources (`SuggestStrategiesForSeedsUseCase`, a raw
`ArchetypeId`/`ThemeId` browser, `RankOwnedCardsForProfileUseCase`) and no legality: any 60-card
format silently fell back to CASUAL semantics in `DeckWizardViewModel.init`, so a "Standard" deck was
built with no legality at all. Studio offered "Build from seed" only to Commander decks.

The engine also had no notion of copies: `DeckEntry(quantity = 1)`, pool `distinctBy { name }`,
`NON_COMMANDER_SLOTS = 99`, `require(format.isCommanderFormat)`.

## Decision

1. **One engine.** `BuildCommanderDeckUseCase` becomes `BuildWizardDeckUseCase` with a sealed
   `BuildAnchor` — `Commander(card)` or `Sixty(identity: Set<ManaColor>, seeds: List<Card>)` — resolved
   by `WizardPlanResolver` (formerly `CommanderPlanResolver`) into the same skeleton, target axes and
   bands `AnalysisEngine.evaluate` resolves for the same deck. Everything below the plan reads
   `plan.skeleton` / `plan.targetAxes` only; the only code allowed to branch on "is this a commander
   build" is the commander entry itself, `NON_COMMANDER_SLOTS`, the commander's pips in the basics
   stage, and `CopyPolicy`. The Commander path is byte-identical: the regression gate is harness v2
   `180/180 HARD`, score `78/85/88/90/96` (min/p25/median/p75/max), harness v3 determinism
   `180/176`, plus the mock segments and the golden/calibration/corpus analysis suites — reproduced
   at every phase gate of the wave.
2. **Copies are an engine concept.** `CopyPolicy.maxPlaceable(card, format, owned)` = basic land →
   unlimited (ownership-exempt, R12) · Commander-shaped format → 1 · Vintage `restricted` → 1 · else
   `min(format.maxCopies, owned)`. The engine places ONLY owned copies; owned quantity is summed BY
   NAME across printings before the pool is deduped to one printing per name. The placement loop,
   `finalize` and `refine` work in copy units (`fold` once per placed copy so role/axis/curve
   counters count copies; `tentativeByRole` holds one id per tentative copy; a candidate leaves the
   pool the moment its copies reach `maxPlaceable`). Copy k ≥ 2 earns
   `CONSISTENCY_CREDIT × powerNormalized` (0.06) after the normal marginal gain.
3. **Seeds are user decisions, not ownership claims.** Seeds may be owned or not (kept, D7); they are
   capped by `CopyPolicy.maxSeedCopies` (legality only — 4, or 1 for Vintage-restricted, unlimited for
   basics) and by a total seed cap of `targetDeckSize − commander slot` in copies. The seed search
   locks format legality (`SectionSearchQuery.legalityCriterion`, Pauper included).
4. **One strategy vocabulary.** 60-card strategies come only from
   `CuratedStrategyCatalog.availableIn(format)`, ranked by `RecommendWizardStrategiesUseCase` with the
   same signal shape as Commander (seeds' axis profiles replace the commander's; owned support scaled
   by `maxPlaceable`; `ColorStrategyAffinity.curatedFor` dominates when seedless). Custom = the generic
   SIXTY skeleton + `SixtyFormatProfile` + the seeds' own axes. Colorless is an exclusive choice with
   identity `{}`, Wastes, and its own affinity row — never mixed with WUBRG.
5. **Mana base is a plan section.** `AnalysisEngine` emits `produces:<X>` for every identity color
   even at count 0 and a `lands` section banded by `skeleton.lands`; Studio inherits both; sections
   never feed the score.
6. **One persist, one destination.** `DeckRepository.persistWizardBuild` (renamed, still one Room
   `@Transaction`) writes into `launchedFromDeckId` for every format; the 60-card path never writes
   `commanderCardId` and never renames the deck. No Result screen — every format opens Deck Studio.
7. **Legacy deleted.** The Motor A wizard path, its vocabulary, the seed/taxonomy strategy sources,
   the DIRECTION/IDENTITY/RESULT phases, the `fillLands` and "Use community data" toggles, and the
   legacy harness segments are gone. `DeckScorer`, `SuggestCutsUseCase`, `SuggestionCategoryResolver`
   (Studio) and `SuggestAddsFromCollectionUseCase` (harness) stay because they have live callers.

## Consequences

- Every non-Draft format builds through the same code the Studio Analysis tab scores with; a wizard
  build and its own analysis cannot disagree on legality, targets or sections. `DRAFT` is refused
  explicitly (`DeckWizardEvent.Exit`), never faked.
- `finalize` keeps `Map<RoleKey, List<String>>` where a repeated id means that many copies: a
  `Map<String, Int>` overload of the same name clashes on the JVM signature and turns every
  generic-inferred call site ambiguous. The ViewModel expands its `Map<RoleKey, Map<String, Int>>`
  choice state at the boundary.
- Two things the wave measured and did NOT solve, recorded rather than hidden (state doc §5/§7):
  60-card builds almost never place 4-ofs — sweeping `CONSISTENCY_CREDIT` over `{0, .03, .06, .10}`
  on the wizard harness never moved the AGGRO/MIDRANGE `fourOfCount` median off 0, because the loop's
  own role/axis differentiation pressure (0.40 weights) dominates any bonus of that magnitude; and
  `ManaBaseAnalyzer`'s `KARSTEN_60` table flags `ColorSourceShortage` on most 2+-color 60-card builds
  (44/68 real-collection specs). Both need their own decision, not a constant tweak.
- The harness gains `WizardHarnessSixtyMockSegmentsTest` (22 specs + colorless, all HARD metrics
  green) and `WizardSixtyHarnessV1Test` (68 real-collection specs; 24 full pass, 44 excluded on the
  Karsten metric alone, 0 unexplained).
