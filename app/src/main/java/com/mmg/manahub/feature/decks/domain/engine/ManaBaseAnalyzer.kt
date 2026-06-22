package com.mmg.manahub.feature.decks.domain.engine

import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.domain.usecase.decks.BasicLandCalculator
import com.mmg.manahub.feature.decks.domain.engine.ManaBaseAnalyzer.Companion.CHEAP_RAMP_CMC
import com.mmg.manahub.feature.decks.domain.engine.ManaBaseAnalyzer.Companion.RAMP_PIECES_PER_LAND
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil
import kotlin.math.roundToInt

// ═══════════════════════════════════════════════════════════════════════════════
//  ManaBaseAnalyzer  (Deck Doctor Phase 5 — plan section C3)
//
//  A pure, stateless, injectable engine-level analyzer (sibling to RoleClassifier).
//  It answers three mana-base questions WITHOUT any simulation or network call —
//  everything is derived from the cards already resolved in the mainboard:
//
//   1. PIP DISTRIBUTION / DEVOTION — how many coloured mana symbols (`{W}`, `{U}`…)
//      the nonland spells demand, quantity-weighted, per colour.
//   2. COLOUR PRODUCTION OF THE LAND BASE — for each colour, how many land COPIES
//      can produce it (the "sources" count).
//   3. CASTABILITY / FIXING CHECK — compares sources-per-colour against a STATIC
//      Frank-Karsten-style source-count table. The Karsten requirement is keyed on the
//      HEAVIEST SINGLE-CARD pip intensity of each colour (the sources to reliably cast one
//      card with N coloured pips), NOT the deck-wide pip sum — so a normal 2-colour deck is
//      not falsely flagged. Emits [DeckWarning.ColorSourceShortage] and
//      [DeckWarning.UnfixedSplash] when a colour is under-supported.
//
//  It also computes a DYNAMIC LAND IDEAL: the skeleton's flat land target shifted
//  down by cheap ramp, low average CMC and card-draw density (the classic
//  "lands = base − f(ramp, cmc, draw)" heuristic), bounded to the skeleton band.
//
//  All numbers are documented constants below so the heuristics are auditable.
// ═══════════════════════════════════════════════════════════════════════════════

@Singleton
class ManaBaseAnalyzer @Inject constructor() {

    // ── Public result ────────────────────────────────────────────────────────────

    /**
     * Full mana-base read of a deck.
     *
     * @param pipsByColor quantity-weighted coloured-pip demand per colour (devotion).
     * @param sourcesByColor land COPIES that can produce each colour.
     * @param requiredByColor the Karsten source threshold each *demanded* colour needs,
     *        derived from the heaviest single-card pip intensity of that colour.
     * @param shortages colours present in the deck's demand whose sources fall short.
     * @param idealLands the dynamic land target (skeleton base shifted by ramp/curve/draw,
     *        clamped to the skeleton's `[LAND.min, LAND.max]`).
     */
    data class ManaBaseReport(
        val pipsByColor: Map<ManaColor, Int>,
        val sourcesByColor: Map<ManaColor, Int>,
        val requiredByColor: Map<ManaColor, Int>,
        val shortages: List<DeckWarning>,
        val idealLands: Int,
    )

    /**
     * Analyzes the [mainboard] (lands + spells, quantity-aware) for the given [profile].
     *
     * The [profile] supplies the format skeleton (for the land band & the dynamic-land
     * inputs that were already computed by [DeckScorer.profile]: `avgCmc`, `roleCounts`,
     * `nonLandCount`). The analyzer never mutates anything; it returns a [ManaBaseReport].
     */
    fun analyze(mainboard: List<DeckEntry>, profile: DeckProfile): ManaBaseReport {
        val nonLand = mainboard.filterNot { BasicLandCalculator.isLand(it.card) }
        val lands = mainboard.filter { BasicLandCalculator.isLand(it.card) }

        val pips = pipDistribution(nonLand)
        // Per-colour Karsten intensity = the HEAVIEST single-card coloured-pip demand of
        // that colour (e.g. the reddest card being {R}{R}{R} → red intensity 3). This is
        // the correct Karsten input: his source counts answer "how many sources to cast A
        // CARD with N pips", NOT the deck-wide pip sum (which over-demands for every
        // multi-colour deck). A colour is "demanded" if it appears on ≥1 nonland card.
        val intensityByColor = maxSinglePipIntensity(nonLand)
        val sources = landSources(lands, profile.colorIdentity)
        val totalLands = lands.sumOf { it.quantity }

        // Required sources per DEMANDED colour. We only check colours the deck actually
        // wants to cast (intensity > 0): an off-colour land producing a colour nothing
        // needs is irrelevant. The Karsten threshold scales with the heaviest single-card
        // pip intensity of the colour.
        val required = mutableMapOf<ManaColor, Int>()
        val shortages = mutableListOf<DeckWarning>()
        intensityByColor.forEach { (color, intensity) ->
            if (intensity <= 0) return@forEach
            val have = sources[color] ?: 0
            val need = requiredSources(intensity, totalLands)
            required[color] = need
            when {
                // A colour the deck WANTS but the land base barely (or never) makes is an
                // un-fixed splash: far below half the requirement (or zero sources). This is
                // the loud "you literally cannot cast your splash" signal.
                have == 0 || have < ceil(need * UNFIXED_SPLASH_RATIO).toInt() ->
                    shortages += DeckWarning.UnfixedSplash(color)
                // Present but short of the reliable-casting threshold: a softer shortage.
                have < need ->
                    shortages += DeckWarning.ColorSourceShortage(color, have, need)
            }
        }

        return ManaBaseReport(
            pipsByColor = pips,
            sourcesByColor = sources,
            requiredByColor = required,
            shortages = shortages,
            idealLands = dynamicLandIdeal(profile),
        )
    }

    // ── 1. Pip distribution / devotion ───────────────────────────────────────────

    /**
     * Quantity-weighted coloured-pip demand of the nonland spells.
     *
     * Parses [Card.manaCost] symbol-by-symbol: a `{W}{W}` cost contributes 2 white pips
     * per copy; a hybrid (`{W/U}`) symbol counts as one pip for EVERY colour it can be paid
     * with (a hybrid card raises demand for both halves, the correct conservative reading
     * for a fixing check). Phyrexian (`{W/P}`) symbols contribute NO coloured pip (always
     * payable with 2 life). Generic (`{2}`), colourless (`{C}`), `{X}` and snow (`{S}`)
     * symbols contribute no coloured pip.
     *
     * NOTE: this is the deck-wide devotion read. The Karsten fixing check uses
     * [maxSinglePipIntensity] (heaviest single-card pip count), NOT this sum.
     */
    fun pipDistribution(nonLand: List<DeckEntry>): Map<ManaColor, Int> {
        val pips = mutableMapOf<ManaColor, Int>()
        nonLand.forEach { entry ->
            val cost = entry.card.manaCost ?: return@forEach
            symbolsOf(cost).forEach { symbol ->
                coloredPipsIn(symbol).forEach { color ->
                    pips[color] = (pips[color] ?: 0) + entry.quantity
                }
            }
        }
        return pips
    }

    /**
     * The HEAVIEST single-card coloured-pip intensity per colour across the nonland spells.
     *
     * For each colour this is `max over all nonland cards of (that colour's pip count on
     * THAT one card)` — e.g. a deck whose reddest card is `{R}{R}{R}` has red intensity 3,
     * a `{1}{W}` curve has white intensity 1. This is the correct input to the Karsten
     * source table (which answers "how many sources to reliably cast A CARD with N pips"),
     * unlike the deck-wide [pipDistribution] sum, which inflates demand on every 2+ colour
     * deck. A colour is "demanded" iff its intensity is ≥ 1.
     *
     * Phyrexian pips (`{W/P}`) are excluded by [coloredPipsIn] (always payable with 2 life),
     * so they add no colour intensity here.
     */
    fun maxSinglePipIntensity(nonLand: List<DeckEntry>): Map<ManaColor, Int> {
        val maxIntensity = mutableMapOf<ManaColor, Int>()
        nonLand.forEach { entry ->
            val cost = entry.card.manaCost ?: return@forEach
            // Coloured pips contributed by THIS one card, per colour.
            val perCard = mutableMapOf<ManaColor, Int>()
            symbolsOf(cost).forEach { symbol ->
                coloredPipsIn(symbol).forEach { color ->
                    perCard[color] = (perCard[color] ?: 0) + 1
                }
            }
            perCard.forEach { (color, count) ->
                maxIntensity[color] = maxOf(maxIntensity[color] ?: 0, count)
            }
        }
        return maxIntensity
    }

    /** Splits a mana cost string like "{2}{W/U}{B}" into its raw `{...}` tokens. */
    private fun symbolsOf(cost: String): List<String> =
        SYMBOL_REGEX.findAll(cost).map { it.groupValues[1] }.toList()

    /**
     * The set of WUBRG colours a single mana SYMBOL (token without braces) demands.
     * "W" → {W}; "W/U" (hybrid) → {W,U}; "2/W" (twobrid) → {W}.
     * "W/P" (Phyrexian) → {} — a Phyrexian pip is ALWAYS payable with 2 life, so it imposes
     * ZERO coloured-source pressure and must not count as colour demand (M3).
     * Generic/colourless/X/S → empty.
     */
    private fun coloredPipsIn(symbol: String): Set<ManaColor> {
        val parts = symbol.split('/').map { it.uppercase() }
        // Phyrexian: any `/`-split part is "P" → no coloured demand (payable with life).
        if (parts.any { it == "P" }) return emptySet()
        return parts.mapNotNull { part -> COLOR_BY_SYMBOL[part] }.toSet()
    }

    // ── 2. Land colour production ─────────────────────────────────────────────────

    /**
     * Sources per colour: the number of land COPIES that can produce each colour.
     *
     * `Card` has no `producedMana` field, so production is derived from the best available
     * local signals, in priority order:
     *  1. Basic-land typeLine subtypes (Plains/Island/Swamp/Mountain/Forest) via
     *     [BasicLandCalculator.getProducedColors] — exact for basics and typed duals/shocks.
     *  2. Oracle "add {X}" mana symbols — catches nonbasic lands whose colours live in the
     *     oracle text (e.g. "{T}: Add {U} or {B}").
     *  3. Oracle "any color" / "one mana of any color" — a rainbow land produces every
     *     colour in the deck's [deckIdentity] (a Command Tower in a UB deck makes U and B).
     *  4. Fallback to the land's own [Card.colorIdentity] when nothing else matched — a
     *     nonbasic with no parseable text still contributes its identity colours.
     *
     * Documented heuristic limits: it cannot see fetchlands that fetch by name, or lands
     * gated behind conditions; those under-count rather than over-count, which keeps the
     * fixing check conservative (it errs toward flagging a shortage, never hiding one).
     */
    fun landSources(lands: List<DeckEntry>, deckIdentity: Set<ManaColor>): Map<ManaColor, Int> {
        val sources = mutableMapOf<ManaColor, Int>()
        lands.forEach { entry ->
            producedColors(entry.card, deckIdentity).forEach { color ->
                sources[color] = (sources[color] ?: 0) + entry.quantity
            }
        }
        return sources
    }

    /** Colours a single land card can produce (see [landSources] for the heuristic). */
    fun producedColors(land: Card, deckIdentity: Set<ManaColor>): Set<ManaColor> {
        val result = mutableSetOf<ManaColor>()

        // 1. Basic-land typeLine subtypes.
        BasicLandCalculator.getProducedColors(land)
            .mapNotNull { COLOR_BY_SYMBOL[it] }
            .forEach { result += it }

        val oracle = land.oracleText.orEmpty()
        if (oracle.isNotBlank()) {
            // 2 & 3. Production lives INSIDE an "Add …" clause. We iterate every "Add …"
            //    sentence (up to its period) and resolve EACH clause independently:
            //      · if the clause itself says "any color" → rainbow over the deck identity
            //        (a five-colour land in a two-colour deck only helps those two colours);
            //      · otherwise pull every `{...}` symbol from that clause ("Add {U} or {B}.").
            //    Restricting "any color" to the Add clause prevents flavour/ability text like
            //    "protection from any color" from inventing a rainbow source (M2), and the
            //    word-boundary anchor on ADD_CLAUSE_REGEX prevents "Additional"/"Adds" from
            //    matching mid-word (M1). A land that both says "Add {U}" and "any color"
            //    elsewhere now resolves each Add clause on its own merits.
            ADD_CLAUSE_REGEX.findAll(oracle).forEach { match ->
                val clause = match.groupValues[1]
                if (ANY_COLOR_REGEX.containsMatchIn(clause)) {
                    val pool = deckIdentity.ifEmpty { ManaColor.values().filter { it != ManaColor.C }.toSet() }
                    result += pool.filter { it != ManaColor.C }
                } else {
                    symbolsOf(clause).forEach { sym ->
                        coloredPipsIn(sym).forEach { result += it }
                    }
                }
            }
        }

        // 4. Fallback: a nonbasic land with no parseable production still contributes its
        //    own colour identity (e.g. a tapland whose text we could not parse).
        if (result.isEmpty()) {
            land.colorIdentity.mapNotNull { COLOR_BY_SYMBOL[it.uppercase()] }.forEach { result += it }
        }
        result.remove(ManaColor.C)
        return result
    }

    // ── 3. Karsten castability table ──────────────────────────────────────────────

    /**
     * Static Frank-Karsten source-count threshold: how many sources of a colour a deck
     * needs to reliably cast A SINGLE CARD whose cost contains [singleCardPips] pips of
     * that colour, by the turn it wants to be cast.
     *
     * Source: Frank Karsten, "How Many Sources Do You Need to Consistently Cast Your
     * Spells?" (TCGplayer, 2018/2022 update). Karsten publishes, per format size, the
     * sources required to hit a single-, double-, triple-pip cost on curve with ~90 %
     * consistency. We pick the row by deck size (60 vs ~99) and the column by the card's
     * own pip intensity — NOT the deck-wide pip sum (that would over-demand for every
     * multi-colour deck; see [maxSinglePipIntensity]).
     *
     * Karsten's headline numbers (sources, rounded):
     *   60-card deck:  1 pip → 14,  2 pips → 20,  3 pips → 23
     *   99-card deck:  1 pip → 19,  2 pips → 28,  3 pips → 32
     *
     * [singleCardPips] is a single card's coloured-pip count for the colour, mapped to a
     * tier: ≤1 ⇒ single (index 0), ==2 ⇒ double (index 1), ≥3 ⇒ triple+ (index 2). This
     * keeps the check a simple, documented table lookup — no simulation.
     */
    fun requiredSources(singleCardPips: Int, totalLands: Int): Int {
        val large = totalLands >= LARGE_DECK_LAND_THRESHOLD
        val tier = when {
            singleCardPips <= SINGLE_CARD_SINGLE_PIP -> 0
            singleCardPips == SINGLE_CARD_DOUBLE_PIP -> 1
            else -> 2
        }
        return if (large) KARSTEN_99[tier] else KARSTEN_60[tier]
    }

    // ── Dynamic land ideal ────────────────────────────────────────────────────────

    /**
     * Dynamic land target: the skeleton's flat land ideal shifted DOWN by cheap ramp,
     * a low average CMC, and card-draw density, then clamped to the skeleton's land band.
     *
     *   idealLands = skeletonBase
     *                − rampReduction(cheap ramp pieces)
     *                − curveReduction(avg CMC below the curve anchor)
     *                − drawReduction(draw density)
     *
     * Rationale (the classic "37 − f(ramp, cmc, draw)" heuristic):
     *  · Every [RAMP_PIECES_PER_LAND] cheap (CMC ≤ [CHEAP_RAMP_CMC]) ramp pieces let the
     *    deck shave ~1 land — 8 cheap rocks/dorks ≈ −[8 / RAMP_PIECES_PER_LAND] lands.
     *  · A low average CMC means cheaper spells flood faster, so fewer lands are needed.
     *  · High draw density refills lands, allowing a leaner base.
     *
     * The shift is bounded to `[LAND.min, LAND.ideal]` (never recommends MORE lands than
     * the skeleton's flat ideal, and never below the format floor) so it can only relax,
     * never inflate, the land count — which is exactly what ramp justifies.
     */
    fun dynamicLandIdeal(profile: DeckProfile): Int {
        val skeleton = profile.skeleton
        val base = skeleton.idealFor(DeckRole.LAND)
        if (base <= 0) return 0
        val landSlot = skeleton.slots.first { it.role == DeckRole.LAND }

        // Cheap ramp pieces only (a 6-mana ramp spell does not let you cut a land).
        // roleCounts is quantity × confidence, so this is already a weighted count; we
        // approximate "cheap" via the deck's RAMP weight scaled by how low its curve is.
        val rampWeight = profile.roleCounts[DeckRole.RAMP] ?: 0f
        val cheapRamp = cheapRampApprox(profile, rampWeight)
        val rampReduction = (cheapRamp / RAMP_PIECES_PER_LAND)

        // Curve: every full point of avg CMC below the anchor relaxes the base slightly.
        val curveAnchor = skeleton.cmcBand.endInclusive
        val curveReduction = ((curveAnchor - profile.avgCmc).coerceAtLeast(0.0) * CURVE_LANDS_PER_CMC)

        // Draw density: draw weight relative to the nonland count.
        val drawWeight = profile.roleCounts[DeckRole.CARD_ADVANTAGE] ?: 0f
        val drawDensity = if (profile.nonLandCount <= 0) 0f else drawWeight / profile.nonLandCount
        val drawReduction = (drawDensity * DRAW_DENSITY_LANDS).toDouble()

        val shifted = base - (rampReduction + curveReduction + drawReduction)
        // Defensive: `coerceIn(min, max)` throws if min > max. All current skeletons have
        // landMin ≤ landIdeal (== base), but a future skeleton with min > base would crash
        // the analyzer here — clamp the lower bound to `base` so it can only ever relax.
        return shifted.roundToInt().coerceIn(minOf(landSlot.min, base), base)
    }

    /**
     * Approximates the count of CHEAP ramp pieces (the only ones that justify cutting a
     * land). We do not have per-card CMC here, so we scale the RAMP role weight by a
     * factor that shrinks as the deck's average CMC rises: a low-curve deck's ramp is
     * mostly cheap (Sol Ring, dorks, signets), a high-curve deck's "ramp" skews expensive.
     */
    private fun cheapRampApprox(profile: DeckProfile, rampWeight: Float): Float {
        if (rampWeight <= 0f) return 0f
        val cheapFraction = (1.0 - (profile.avgCmc - CHEAP_RAMP_CMC) / CHEAP_RAMP_CURVE_SPAN)
            .coerceIn(CHEAP_RAMP_MIN_FRACTION, 1.0)
        return (rampWeight * cheapFraction).toFloat()
    }

    private companion object {
        /** Matches one `{...}` mana symbol; group 1 is the inner token (e.g. "W", "W/U", "2"). */
        val SYMBOL_REGEX = Regex("""\{([^}]+)\}""")

        /**
         * An "Add …" oracle clause up to the sentence end (group 1 is everything after "Add"
         * on that sentence). We then pull every `{...}` symbol from group 1, so a clause like
         * "Add {U} or {B}." yields both symbols even though they are not adjacent.
         *
         * The `\bAdd\b` word boundary is required: the old `[Aa]dd` matched "Add" INSIDE
         * "Additional"/"Adds", which could capture an unrelated `{...}` symbol and invent an
         * off-colour source (M1) — e.g. "Additional cost: Pay {R}. {T}: Add {G}." must yield
         * only {G}, not {R}.
         */
        val ADD_CLAUSE_REGEX = Regex("\\bAdd\\b([^.]*)", RegexOption.IGNORE_CASE)

        /** Rainbow production: "any color" / "one mana of any color" / "mana of any type". */
        val ANY_COLOR_REGEX = Regex("any (one )?color|mana of any", RegexOption.IGNORE_CASE)

        val COLOR_BY_SYMBOL: Map<String, ManaColor> = mapOf(
            "W" to ManaColor.W, "U" to ManaColor.U, "B" to ManaColor.B,
            "R" to ManaColor.R, "G" to ManaColor.G,
        )

        // ── Karsten source thresholds (sources needed; see [requiredSources]) ─────
        // Index 0 = single-pip tier, 1 = double-pip tier, 2 = triple+-pip tier.
        val KARSTEN_60 = intArrayOf(14, 20, 23)
        val KARSTEN_99 = intArrayOf(19, 28, 32)

        /** A deck with ≥ this many lands is treated as the large (≈99-card) Karsten row. */
        const val LARGE_DECK_LAND_THRESHOLD = 33

        /** A single card's pip count at/below which it maps to the single-pip Karsten tier. */
        const val SINGLE_CARD_SINGLE_PIP = 1
        /** A single card's pip count that maps to the double-pip Karsten tier (≥3 ⇒ triple+). */
        const val SINGLE_CARD_DOUBLE_PIP = 2

        /**
         * A demanded colour with fewer than this fraction of its required sources (or zero)
         * is an UNFIXED splash rather than a mere shortage — you essentially cannot cast it.
         */
        const val UNFIXED_SPLASH_RATIO = 0.5

        // ── Dynamic-land-ideal tuning ─────────────────────────────────────────────
        /** Cheap ramp pieces that let the deck shave one land. 4 → 8 rocks ≈ −2 lands. */
        const val RAMP_PIECES_PER_LAND = 4f
        /** A ramp piece is "cheap" at/below this CMC. */
        const val CHEAP_RAMP_CMC = 2.0
        /** Curve span over which ramp shifts from all-cheap to mostly-expensive. */
        const val CHEAP_RAMP_CURVE_SPAN = 4.0
        /** Even a high-curve deck's ramp keeps at least this fraction "cheap". */
        const val CHEAP_RAMP_MIN_FRACTION = 0.3
        /** Lands relaxed per full point of avg CMC below the curve anchor. */
        const val CURVE_LANDS_PER_CMC = 0.5
        /** Lands relaxed at full (1.0) draw density (draw weight == nonland count). */
        const val DRAW_DENSITY_LANDS = 4f
    }
}
