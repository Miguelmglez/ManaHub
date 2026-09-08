# SYNERGY pillar mechanics

> Investigation doc (Suggestions Tab UI Polish plan, W13, 2026-08-25) — how the Deck Analysis
> engine's SYNERGY pillar evaluates, and a code-grounded root cause for why a well-built, on-plan
> deck (the documented UW Control fixture) can score as low as 27/100. Read-only investigation; no
> code changes were made as part of this doc. Every claim cites the file:line it was read from.

## 1. The exact algorithm, step by step

`AnalysisEngine.evaluateSynergy(nonLand, nonLandCount, profile)`
(`shared/core-domain/src/commonMain/kotlin/com/mmg/manahub/feature/decks/domain/engine/AnalysisEngine.kt`,
function starting ~line 463) does the following, entirely over the ALREADY-BUILT
`profile.tagFingerprint` (built earlier by `DeckScorer.profile`/`fingerprint`, §2 below — this
pillar never re-derives it):

1. **Per-entry alignment check.** For every non-land `DeckEntry`:
   ```kotlin
   val keys = ((entry.card.tags + entry.card.userTags).map { it.key } + TribeDeriver.tribeKeys(entry.card)).toSet()
   val alignedKeys = keys.filter { (profile.tagFingerprint[it] ?: 0f) >= SYNERGY_ALIGNMENT_THRESHOLD }.toSet()
   ```
   Every one of the card's OWN tag keys (auto + user, ALL categories — not filtered here) plus its
   derived tribe keys is checked against the deck-level fingerprint; a key "aligns" only if the
   fingerprint has an entry for it AND that entry's weight is `>= SYNERGY_ALIGNMENT_THRESHOLD`
   (**0.4**, `AnalysisEngine.kt:86`). A card can align on zero, one, or several keys.
2. **`alignedCopies`**: the sum of `entry.quantity` for every entry with at least one aligned key
   (an entry either counts fully or not at all — not weighted by HOW MANY keys aligned).
3. **`density = alignedCopies / nonLandCount`** — the fraction of the deck's non-land copies that
   aligned on at least one key.
4. **`subscore = (density.coerceIn(0f,1f) * 100).roundToInt()`** — a straight linear mapping,
   no curve/band shaping unlike every other pillar's `bandRatioScore`.
5. **`Finding.LowSynergyDensity`** fires only when `nonLandCount > SYNERGY_DENSITY_MIN_SAMPLE` (10)
   AND `density < SYNERGY_DENSITY_FLOOR` (0.35) — purely advisory, does not change the subscore.
6. **Sections** (Category Sections rework, W2): one `CardSection` per aligned fingerprint key (a
   dual-aligned card appears in every section it qualifies for) plus a mandatory `"offplan"`
   section listing every entry that aligned on NOTHING — this is DERIVED, purely presentational
   output from the SAME `alignedEntries` list; explicitly never reconciled back into `density`
   (`AnalysisEngine.kt`'s own comment: "subscore math is UNCHANGED... never reconcile one from the
   other").

## 2. Where `profile.tagFingerprint` actually comes from

Built by `DeckScorer.fingerprint(nonLand, seedTags)`
(`shared/core-domain/src/commonMain/kotlin/com/mmg/manahub/feature/decks/domain/engine/DeckScorer.kt:76-132`),
called once per full evaluation from `DeckScorer.profile()` (`DeckScorer.kt:66`) — this is the
SAME profile `AnalysisEngine.evaluate` receives as an already-built input (`AnalysisEngine.kt:94-97`'s
own KDoc: reused purely as a cheap data source, never rebuilt for P4).

1. **Raw accumulation** (`DeckScorer.kt:77-82`): for every non-land entry, for every one of the
   card's own tags (auto + user) whose `category` is in `IDENTITY_CATEGORIES`, add the entry's
   quantity to `raw[tag.key]`.
   ```kotlin
   val IDENTITY_CATEGORIES = setOf(TagCategory.STRATEGY, TagCategory.ARCHETYPE, TagCategory.TRIBAL)   // DeckScorer.kt:595
   ```
   **This is the critical gate** — a card whose only tags are ROLE-category (`removal`, `ramp`,
   `card_draw`, `counterspell`, etc. — the vocabulary `PLAN_ROLES`/`SectionSearchQuery` read)
   contributes NOTHING to the fingerprint, however central that card is to the deck's actual game
   plan. Only `STRATEGY`/`ARCHETYPE`/`TRIBAL` tags ever reach `raw`.
2. **Per-tribe augmentation** (`DeckScorer.kt:93-114`): `TribeDeriver.subtypeKeys` derives
   `tribe:<subtype>` keys from creature type lines; a subtype clears into `raw` when it covers
   `>= TRIBE_ABS_THRESHOLD` (8) copies OR `>= TRIBE_SHARE_THRESHOLD` (0.15) of creature copies
   (`DeckScorer.kt:624-625`). When any tribe clears, the generic `tribal` STRATEGY key is DROPPED
   from `raw` (superseded by the specific tribe keys, `DeckScorer.kt:114`).
3. **Normalization + seed floor** (`DeckScorer.kt:121-131`): every `raw` value is divided by the
   single largest raw value (so the deck's OWN dominant identity key always normalizes to exactly
   `1.0`), then every `seedTags` key (the commander + top-8 highest-identity-tag mainboard cards'
   tags, `EvaluateDeckUseCase`'s own KDoc) is floored to at least `SEED_FLOOR` (**0.6**,
   `DeckScorer.kt:602`) if it wasn't already higher.

**Consequence for `evaluateSynergy`'s alignment check**: a key can only ever pass the `>= 0.4`
alignment bar in step 1 if that key is EITHER the deck's dominant `STRATEGY`/`ARCHETYPE`/`TRIBAL`
tag (or close to it, post-normalization) OR a seed tag (floored to 0.6). Everything else in the
fingerprint — every non-dominant identity tag that only appears on a handful of cards — normalizes
to a small fraction and never clears 0.4.

## 3. Root cause: why a well-built UW Control deck scores ~27

This is not a mis-tuned threshold — it is a **vocabulary gap**: the `ARCHETYPE`-category `CardTag`
that should represent "this card is a Control staple" is **never auto-applied to any card**.

```kotlin
// shared/core-model/.../CardTag.kt:32-37
val AGGRO     = CardTag("aggro",     TagCategory.ARCHETYPE)
val CONTROL   = CardTag("control",   TagCategory.ARCHETYPE)
val COMBO     = CardTag("combo",     TagCategory.ARCHETYPE)
val MIDRANGE  = CardTag("midrange",  TagCategory.ARCHETYPE)
val RAMP      = CardTag("ramp",      TagCategory.ARCHETYPE)
val TEMPO     = CardTag("tempo",     TagCategory.ARCHETYPE)
```

Grepping `TagDictionary.kt` (`shared/core-data/src/commonMain/kotlin/com/mmg/manahub/core/data/tagging/TagDictionary.kt`,
the source of every `DetectionRule` that auto-applies a tag to a card) for `AGGRO`, `COMBO`,
`MIDRANGE`, `RAMP`, `TEMPO`, and `CONTROL` returns **zero matches for all six** — none of the 6
`ARCHETYPE`-category tags has a single detection rule. They are declared in the model but
structurally dead for auto-tagging; the only way a card could ever carry one is a manual user-tag
(an edge case, not how a fresh import/scan populates tags). Since `IDENTITY_CATEGORIES` includes
`ARCHETYPE`, this means **the entire `ARCHETYPE` tag category contributes nothing to
`profile.tagFingerprint` in practice** for essentially every real deck.

By contrast, the 12 `STRATEGY`-category tags that DO have real detection rules and DO populate the
fingerprint are all **synergy-PACKAGE/theme concepts**: `tokens`, `plus_counters`, `proliferate`,
`graveyard`, `enchantress`, `tribal`, `burn`, `lifegain`, `sacrifice`, `blink`, `infinite_combo`,
`stax` (`CardTag.kt:40-51`). Every one of these describes a deck that revolves around a specific
recurring payoff/engine pattern — exactly the kind of deck the SYNERGY pillar CAN see and reward.

**A Control deck's identity is not a synergy package — it's a role composition** (counterspells +
spot/mass removal + card draw + a small win-condition suite), which is precisely what the
`PLAN_ROLES` pillar already measures via `RoleKey`s (`removal_spot`, `removal_mass`,
`counterspell`, `card_draw`, etc. — ROLE-category tags, explicitly EXCLUDED from
`IDENTITY_CATEGORIES`). SYNERGY and PLAN_ROLES are reading two disjoint tag vocabularies by design
— that split is fine for a deck whose identity IS a theme, but for a deck whose identity is purely
"correctly weighted roles with no thematic hook," SYNERGY has almost nothing to measure: most of
the deck's cards carry ROLE tags only, contribute nothing to the fingerprint, and — critically —
even checking their OWN tags against the fingerprint in `evaluateSynergy`'s alignment step
(§1.1) finds no match, because there was never a fingerprint entry for `removal_spot`/
`counterspell`/`card_draw` etc. in the first place (those keys never entered `raw` per §2.1).

The handful of Control staples that DO happen to carry a real `STRATEGY` tag incidentally (e.g. a
stax piece tagged `stax`, a graveyard-hate effect tagged `graveyard`) can align — but they are a
small minority of a typical Control shell, not its bulk. This produces exactly the documented
outcome: `alignedCopies` stays low, `density = alignedCopies / nonLandCount` lands well under the
0.35 `LowSynergyDensity` floor, and the linear `subscore = density * 100` lands in the
"score reads as broken" range (27) for a deck an experienced player would call excellently built.

**This is a genuine structural gap, not a calibration slider problem.** Retuning
`SYNERGY_ALIGNMENT_THRESHOLD` (0.4) or `SYNERGY_DENSITY_FLOOR` (0.35) up or down would not fix
this — the underlying `raw` fingerprint for a Control deck is sparse regardless of the threshold,
because the tag vocabulary that would represent "Control-ness" doesn't exist in practice.

## 4. What data the pillar is NOT currently using (survey-level, no design proposal)

- **Role-composition coherence.** The pillar never asks "does this deck's ROLE distribution match
  a coherent shape" (e.g. heavy on `removal`+`counterspell`+`card_draw`, light on `threat_early`) —
  that signal exists (it's what `PLAN_ROLES`/`InferDeckArchetypeUseCase`'s macro scores already
  read) but SYNERGY never consults it. A genuinely "archetype-shape-aware" synergy signal would
  need to bridge these two currently-disjoint tag spaces.
- **Mainboard-level card-to-card synergy graphs.** Nothing in this pillar (or anywhere in
  `AnalysisEngine`) models pairwise/combo relationships between specific cards (e.g. "this
  sacrifice outlet + this token generator" or "this specific reanimation target + this specific
  reanimation spell") — the fingerprint is a flat bag of tag-key weights, never a graph or
  co-occurrence structure.
- **Oracle-text co-occurrence / semantic clustering.** No embedding, text-similarity, or
  co-occurrence-frequency signal is used anywhere in this pillar; alignment is a hard `>=`
  threshold on a hand-tagged category, nothing statistical.
- **The already-computed `ARCHETYPE`/macro resolution itself.** `AnalysisEngine.evaluate` already
  has `archetype`/`themes` (the SAME resolution `EvaluateDeckUseCase`/`InferDeckArchetypeUseCase`
  compute — see `docs/deck-plan-selection-mechanics.md`, W12) available as a parameter
  (`AnalysisEngine.kt:104-116`), but `evaluateSynergy` never reads `archetype`/`themes` at all — it
  only ever reads `profile.tagFingerprint`. A macro-archetype-aware signal (e.g. "does this deck's
  ROLE distribution match its own resolved archetype's expected shape") is plausible future work
  but genuinely absent today.
- **Card power/redundancy weighting.** `DeckScorer.fit()`'s own `synergyScore`/power/redundancy
  machinery (a related but SEPARATE, still-legacy scoring path — `DeckScorer.kt:136-165`) is not
  read by `AnalysisEngine.evaluateSynergy` at all; the two synergy-flavored scores in this codebase
  are independent, not layered.

## 5. Recommended next steps (explicitly OUT of scope for this doc — no changes made here)

1. **Give the 6 `ARCHETYPE` tags real `DetectionRule`s** (or retire them if a tag-based
   representation of "this card plays a Control/Aggro/Ramp/etc. role" is fundamentally the wrong
   shape) — this alone would let a Control deck's own cards populate a `control` fingerprint entry
   and start aligning, the most direct fix for the specific fixture this doc investigated.
2. **Alternatively (or additionally), fold `PLAN_ROLES`' role-composition signal into SYNERGY** —
   e.g. a deck whose role mix closely matches its resolved archetype's expected role bands could
   count as "coherent" even with a sparse `STRATEGY`/`ARCHETYPE` tag fingerprint, closing the gap
   for role-shape-only archetypes without inventing new tag vocabulary.
3. Any retune should be validated against a role-shape-only fixture (Control, Aggro without a
   thematic hook, generic Ramp) alongside the EXISTING theme-heavy fixtures
   (`DeckAnalysisEngineCalibrationTest`/`DeckAnalysisEngineGoldenTest`) so a fix for one shape
   doesn't regress the other — SYNERGY currently works reasonably well for theme decks; the gap is
   specific to archetype-shape-only decks.
4. Whatever direction is chosen, keep the documented calibration discipline this file's own KDoc
   already establishes (`AnalysisEngine.kt`'s `evaluateSynergy` KDoc): SYNERGY is explicitly called
   out to callers/UI as the weakest-calibrated pillar, and that framing (the SYNERGY sub-caption,
   `SynergyAlignmentCaption`) should stay until a real retune lands, not be quietly removed.
