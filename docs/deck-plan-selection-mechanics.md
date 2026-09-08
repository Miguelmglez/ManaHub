# Deck plan selection mechanics

> Investigation doc (Suggestions Tab UI Polish plan, W12, 2026-08-25) — how the Deck Analysis
> engine picks a deck's "plan" (archetype + themes + the curated strategy chip shown in the
> Studio header), and a concrete, code-grounded hypothesis for why it disproportionately resolves
> to **Midrange**. Read-only investigation; no code changes were made as part of this doc. Every
> claim below cites the file:line it was read from — nothing here is from memory or speculation.

## 1. The full decision path

A deck's "plan" is resolved once per full analysis pass, by `EvaluateDeckUseCase.invoke`
(`shared/core-domain/src/commonMain/kotlin/com/mmg/manahub/feature/decks/domain/usecase/EvaluateDeckUseCase.kt:133-234`),
and reused for both the legacy `DeckHealth.evaluation` warnings layer and the current
`DeckHealth.analysis` (Deck Analysis Engine v2) pipeline — there is exactly ONE resolution per
pass, not two independent ones. The call chain:

```
EvaluateDeckUseCase.invoke()
  └─ resolveArchetype()                              [EvaluateDeckUseCase.kt:237-257]
       ├─ pin path:  Deck.archetypeOverride/themesOverride, if set  → confidence = 1.0, isManualOverride = true
       └─ infer path: InferDeckArchetypeUseCase()                   → confidence = the winning score, isManualOverride = false
  └─ EvaluateDeckUseCaseV2(resolution)                [EvaluateDeckUseCaseV2.kt:39-59]
       └─ AnalysisEngine.evaluate(archetype = resolution.macro, themes = resolution.themes, ...)
            └─ CuratedStrategyCatalog.nearestFor(archetype, themes)  [AnalysisEngine.kt:117]
                 → the CuratedStrategy shown as the "Deck plan" chip, or null ("Custom")
```

### 1.1 Pin vs. inference (`resolveArchetype`, `EvaluateDeckUseCase.kt:236-257`)

```kotlin
val pinnedMacro = archetypeOverride?.let { raw -> ArchetypeId.entries.firstOrNull { it.name == raw } }
val pinnedThemes = themesOverride.mapNotNull { raw -> ThemeId.entries.firstOrNull { it.name == raw } }.take(2)
if (pinnedMacro != null || pinnedThemes.isNotEmpty()) {
    return ArchetypeResolution(macro = pinnedMacro ?: ArchetypeId.GENERIC, themes = pinnedThemes, isManualOverride = true, confidence = 1f)
}
val inferred = inferDeckArchetypeUseCase(mainboard, format, commanderTags)
return ArchetypeResolution(inferred.macro, inferred.themes, isManualOverride = false, confidence = inferred.confidence)
```

A user's manual pin (`CuratedStrategyPickerSheet` → `Deck.archetypeOverride`/`themesOverride`,
persisted enum-name strings) **always wins outright** — the inference path
(`InferDeckArchetypeUseCase`) is never even called when a pin exists. `Deck.strategyLocked`
(wizard-built decks) further prevents the pin from being changed without an explicit "Unlock
strategy" action (`feature/decks/CLAUDE.md`'s "Deck Engine Unification (D4)" section) — this is
UI-level, not part of the resolution logic itself. Auto-detect (`onClearArchetypeOverride` in
`DeckStudioScreen.kt`) clears the pin and forces a fresh inference pass.

### 1.2 Inference (`InferDeckArchetypeUseCase.invoke`, `InferDeckArchetypeUseCase.kt:49-134`)

Given the mainboard, this computes:

1. **5 macro archetype scores** — AGGRO, CONTROL, COMBO, RAMP, TEMPO (`InferDeckArchetypeUseCase.kt:98-104`).
   Each is a hand-weighted sum of role-density ratios (`RoleClassifier`-derived counts ÷ non-land
   card count) and curve-shape bonuses. **`ArchetypeId.MIDRANGE` and `ArchetypeId.GENERIC` never
   appear in this scored set at all** — there is no `midrangeScore` or `genericScore` competing in
   the argmax. This is the single most important fact in this investigation (see §2).
2. **Up to 2 confident themes** — up to 18 `ThemeId` scores (`themeScores`, `InferDeckArchetypeUseCase.kt:136-183`),
   each a single role/type-line density normalized against a "clearly present" anchor
   (`Float.themeNorm`, `InferDeckArchetypeUseCase.kt:186`), filtered to `>= THEME_CONFIDENCE_THRESHOLD`
   (0.45), sorted descending, capped at 2 (`MAX_THEMES`).
3. **The final resolution** (`InferDeckArchetypeUseCase.kt:122-133`), in this exact priority order:
   - If the best macro score `>= MACRO_CONFIDENCE_THRESHOLD` (0.55) → that macro wins, paired with
     whatever confident themes were separately detected.
   - **Else if at least one theme is confident (`>= 0.45`) → the macro defaults to `MIDRANGE`**,
     unconditionally, regardless of which theme(s) fired or how the rest of the deck looks. The
     confidence reported is the winning THEME's score, not a macro score (there is no macro score
     to report — none was computed).
   - Else (`nonLandCount == 0`, or no macro cleared 0.55 AND no theme cleared 0.45) → `GENERIC`,
     empty themes.

### 1.3 Curated strategy resolution (`CuratedStrategyCatalog.nearestFor`, `CuratedStrategyCatalog.kt:498-509`)

The `(macro, themes)` pair from either path above is mapped to a `CuratedStrategy` (the actual chip
shown in the UI) in 3 steps:

1. **Exact match**: an entry whose `archetype == macro` AND `themes.toSet() == themeSet` — this is
   how a themed pick (e.g. Midrange + Tokens) resolves to a specific named strategy like "Tokens".
2. **Pure-archetype fallback**: if no exact match and `macro` is not `null`/`GENERIC`, the first
   entry with `archetype == macro` and NO themes (e.g. plain "Midrange").
3. **`null`** ("Custom" sentinel in the UI) — a `null` macro, or `GENERIC` paired with any themes
   (deliberately excluded from the pure-archetype fallback per that function's own KDoc, since a
   themed `GENERIC` pin is an unusual/incoherent state that should read as Custom rather than being
   silently coerced to "Balanced").

`AnalysisEngine.kt:117` calls the 2-arg overload (`nearestFor(archetype, themes)`, no `format`
filter) — this is the pre-existing, documented "known debt" chip/picker mismatch noted in
`feature/decks/CLAUDE.md`'s Category Sections rework block (unrelated to the Midrange question,
not re-investigated here).

## 2. Why Midrange specifically keeps winning

Two independent, compounding factors, both directly readable from the code (not inferred from
outcomes):

### 2.1 Midrange is a fallback, never a competitor

`macroScores` (`InferDeckArchetypeUseCase.kt:98-104`) is a fixed map of exactly 5 entries: AGGRO,
CONTROL, COMBO, RAMP, TEMPO. There is no scored candidate for MIDRANGE or GENERIC. This means:

- Midrange **cannot lose an argmax it never enters**. Every one of the 5 real macro scores has to
  independently clear a 0.55 bar (out of a weighted sum of ratios individually capped at 1.0 by
  `.coerceIn(0f, 1f)` on each term) — a real deck's own density/curve numbers rarely land a
  specific archetype's hand-tuned weighted formula that high (see §2.2). Whenever ALL 5 fall short
  of 0.55, the resolver does not conclude "this deck doesn't strongly fit AGGRO/CONTROL/COMBO/RAMP/
  TEMPO, so it might be balanced/GENERIC" — it instead falls through to the THEME check, and if
  ANY of 18 themes cleared its own, considerably lower 0.45 bar, the macro becomes MIDRANGE
  unconditionally (`InferDeckArchetypeUseCase.kt:125-130`).
- The comment at `InferDeckArchetypeUseCase.kt:126-129` names this explicitly as a deliberate rule
  ("MIDRANGE vs GENERIC: prefer GENERIC unless a theme is confident... a confident theme with no
  clear macro signal defaults the macro to MIDRANGE") — this is documented, INTENTIONAL behavior,
  not an oversight in the sense of "nobody meant to do this." The oversight (if any) is in how
  often the theme threshold fires relative to the macro threshold — see §2.2.

### 2.2 The two thresholds are not equivalently hard to clear

`MACRO_CONFIDENCE_THRESHOLD = 0.55f` vs. `THEME_CONFIDENCE_THRESHOLD = 0.45f`
(`InferDeckArchetypeUseCase.kt:190,193`) look close numerically, but they are measuring very
different kinds of scores:

- A **macro score** is a weighted SUM of 2-3 sub-signals (e.g. `aggroScore = 0.5 * threatEarlyDensity
  + 0.3 * curveLowBonus + 0.2 * creatureRatio`, `InferDeckArchetypeUseCase.kt:69`) — to reach 0.55
  overall, MULTIPLE independent signals all have to run reasonably high simultaneously (a genuinely
  aggressive curve AND a high density of the specific `threat_early` role tag AND a high creature
  ratio). Real, well-built-but-not-hyper-focused Commander decks (60-90 cards of "generically
  good stuff" around a loose plan) routinely have moderate values on all 3 terms rather than a
  spike on all 3 — the multiplicative/additive structure means a deck has to be UNAMBIGUOUSLY
  aggro-shaped on every axis to clear 0.55.
- A **theme score** is, for most themes, `density(singleRoleKey).themeNorm(anchor)` — ONE ratio,
  normalized against a single anchor value chosen so that "clearly present" reads as 1.0
  (`InferDeckArchetypeUseCase.kt:186`). Several anchors are quite low: `TOKENS` anchors at 0.14,
  `MILL`/`PLUS1_COUNTERS` at 0.12, `WHEELS` at 0.07, `VEHICLES` at 0.09, `LANDFALL`/`LIFEGAIN` at
  0.09 — meaning as few as ~7-14% of a deck's non-land cards carrying ONE role tag is enough to
  clear the (already lower) 0.45 bar for that theme, since `themeNorm` only needs
  `density / anchor >= 0.45`, i.e. `density >= 0.45 * anchor` (e.g. for TOKENS: `density >= 0.45 *
  0.14 ≈ 0.063`, roughly 6-7 token-generating cards in a 99-card Commander deck). Many real decks
  will incidentally clear at least ONE of the 18 theme bars even when built around no explicit
  theme at all (a handful of token generators, a few counters payoffs, some artifacts) — because
  18 independent low single-signal checks have a much higher chance that AT LEAST ONE fires than
  a single deck clearing one specific 3-signal-AND-like macro bar.

**Net effect**: the inference is structurally biased toward "no macro clears 0.55, but some theme
clears 0.45" — and that exact branch is hardcoded to MIDRANGE. This is a real, code-verifiable
asymmetry, not a hunch about outcomes.

### 2.3 The curated catalog then reinforces the same bias

Once the inference resolves to `(MIDRANGE, [someTheme])`, `nearestFor`'s exact-match step
(`CuratedStrategyCatalog.kt:505`) looks for a catalog entry with `archetype == MIDRANGE`. Grepping
`CuratedStrategyCatalog.kt` shows **the large majority of the 23 themed curated strategies pair
`archetype = ArchetypeId.MIDRANGE`** (at minimum lines 184-186, 243, 274, 312, 320, 328, 337, 345,
355, 421, 437 — 11+ of the file's themed entries). So the moment the inference lands on MIDRANGE +
a theme, there is very likely a real, non-null, EXACT curated match waiting — meaning the UI
doesn't even show an ambiguous "Custom" fallback that might prompt a user to notice something is
off; it shows a clean, named strategy chip (e.g. "Tokens", still under the Midrange archetype),
which reads as a confident, correct pick even though the macro half of it was never actually
scored as Midrange-shaped — it's simply the unscored default.

## 3. Every scoring/threshold constant involved (for a future tuning pass)

| Constant | Value | File:line | Role |
|---|---|---|---|
| `MACRO_CONFIDENCE_THRESHOLD` | 0.55 | `InferDeckArchetypeUseCase.kt:190` | Minimum winning macro score to avoid the MIDRANGE/GENERIC fallback |
| `THEME_CONFIDENCE_THRESHOLD` | 0.45 | `InferDeckArchetypeUseCase.kt:193` | Minimum theme score to count as "confident" |
| `MAX_THEMES` | 2 | `InferDeckArchetypeUseCase.kt:196` | Cap on simultaneously-detected themes |
| `TEMPO_COUNTERSPELL_MIN` | 6 | `InferDeckArchetypeUseCase.kt:199` | TEMPO hard-gate (raw counterspell-role copies) |
| `TRIBAL_SHARE_THRESHOLD` | 0.35 | `InferDeckArchetypeUseCase.kt:202` | Dominant-tribe share of creature copies → TRIBAL theme |
| Per-theme anchors | 0.07 – 0.25 | `InferDeckArchetypeUseCase.kt:157-180` | Divisor in `density.themeNorm(anchor)` per theme — the lower the anchor, the easier that theme clears its 0.45 bar |
| `aggroScore` weights | 0.5 / 0.3 / 0.2 | `InferDeckArchetypeUseCase.kt:69` | threat_early density / curve-low bonus / creature ratio |
| `controlScore` weights | 0.5 / 0.2 / 0.3 | `InferDeckArchetypeUseCase.kt:73-74` | interaction density / (1 − creature ratio) / curve-high bonus |
| `comboScore` weights | 0.4 / 0.3 / 0.3 | `InferDeckArchetypeUseCase.kt:82-83` | tutor density / protection density / COMBO+INFINITE tag density |
| `rampScore` weights | 0.5 / 0.5 | `InferDeckArchetypeUseCase.kt:87` | ramp density / curve-top-heavy bonus |
| `tempoScore` weights | 0.4 / 0.6 (gated) | `InferDeckArchetypeUseCase.kt:92-96` | threat_early density / counterspell density, only active past the counterspell hard-gate |
| **No `midrangeScore`/`genericScore`** | — | (absent) | MIDRANGE and GENERIC never compete in the macro argmax — the root cause identified in §2.1 |

## 4. Recommended next steps (explicitly OUT of scope for this doc — no changes made here)

These are candidate directions for a dedicated future tuning pass, not decisions:

1. **Give MIDRANGE its own computed score** and let it compete in the same argmax as the other 5
   macros, instead of being a hardcoded fallback. A plausible shape: "moderate curve, moderate
   interaction, moderate creature count" — i.e. reward a deck for NOT being extreme on any other
   macro's axis, rather than defining it purely by absence.
2. **Re-balance the two threshold scales** so a single-signal theme match and a multi-signal macro
   match are comparably hard to clear — either raise theme anchors (fewer decks trip a theme by
   accident) or restructure macro scores to not require ALL sub-signals to be simultaneously high
   (e.g. `max` instead of a weighted sum for some macros).
3. **Consider a genuine confidence gap check** before defaulting to MIDRANGE — e.g. require the
   winning theme to clear its threshold by a real margin (mirrors `AnalysisEngine.computeScoreLimiter`'s
   own "dominant pillar" margin-based reasoning, `AnalysisEngine.kt`'s `DOMINANT_PILLAR_MARGIN`
   precedent already in this codebase) rather than a flat >= check.
4. Any retune should be validated against `DeckAnalysisEngineGoldenTest`/`DeckAnalysisEngineCalibrationTest`'s
   existing archetype-detection fixtures plus new fixtures specifically representing "no strong
   macro, one incidental theme" decks — the exact shape this doc identifies as the over-triggering
   case.
