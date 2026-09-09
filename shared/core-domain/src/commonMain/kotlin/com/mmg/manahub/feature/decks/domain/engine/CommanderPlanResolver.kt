package com.mmg.manahub.feature.decks.domain.engine
// COMMENTS_REVIEWED: 2026-09-08

import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.DeckFormat

// ═══════════════════════════════════════════════════════════════════════════════
//  CommanderPlanResolver — Deck Wizard Commander v3 plan, Phase 1.1.
//
//  Fixes F2 (plan §0): a Commander build's target skeleton is now ALWAYS resolved the exact same
//  way AnalysisEngine.evaluate resolves it for the same pin — Custom included (generic baseline,
//  never `null`). This is a CONTRACT type only in this phase: no placement engine reads
//  [CommanderPlan] yet (that is Phase 2's `PlacementScorer`/`BuildCommanderDeckUseCase`).
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * A Commander wizard build's strategy choice — either a catalog pick (D4: "one [CuratedStrategy]
 * single select") or [Custom] (D6: generic baseline bands + the commander's own axes, no pin).
 */
sealed interface StrategyPick {
    /** @property tribe the caller's tribe sub-pick, raw `"tribe:<subtype>"` key (same shape
     *           [CuratedStrategy.toPin] expects) — required only when [strategy]
     *           .[CuratedStrategy.requiresTribe] is true; not validated here. */
    data class Curated(val strategy: CuratedStrategy, val tribe: String? = null) : StrategyPick

    /** D6: no catalog pin — the generic baseline skeleton, plus the commander's own axes as
     * synergy targets (still a real plan, not "no targets"). */
    data object Custom : StrategyPick
}

/**
 * A Commander build's fully-resolved target (Phase 1.1). [skeleton] is ALWAYS non-null-equivalent
 * (a real [ResolvedArchetypeSkeleton], generic-baseline for [StrategyPick.Custom] — fixes F2).
 *
 * @property targetAxes the union of: each resolved theme's mapped [AxisKey] ([THEME_TARGET_AXES] —
 *           see that table's own KDoc for the ones intentionally left unmapped), the commander's
 *           own produced/consumed axes ([SynergyGraph.cardAxisProfile]), and the tribe's axis (when
 *           [StrategyPick.Curated.tribe] is set). Phase 2's `PlacementScorer` is the first real
 *           consumer — nothing reads this yet.
 * @property curveTargets [CurveTargets.forSkeleton] — see that object's KDoc: this is NEW derived
 *           logic (a documented judgment call, not extracted from any existing engine number).
 * @property landBand [skeleton]'s own [ResolvedArchetypeSkeleton.lands] band, hoisted for
 *           convenience (Phase 2's land-fill stage reads this without re-destructuring the
 *           skeleton every time).
 * @property manaFixBand [ResolvedArchetypeSkeleton.manaFixTarget] — `null` when the skeleton has no
 *           `mana_fix` role band (colorless-only identity — see [ArchetypeSkeletonResolver
 *           .resolveWithColorCount]'s own fail-closed convention).
 */
data class CommanderPlan(
    val skeleton: ResolvedArchetypeSkeleton,
    val targetAxes: Set<AxisKey>,
    val curveTargets: List<CurveTargets.CurveBucketTarget>,
    val landBand: RoleTarget,
    val manaFixBand: RoleTarget?,
)

object CommanderPlanResolver {

    /**
     * Resolves [format]/[commander]/[pick]/[identity] into a [CommanderPlan].
     *
     * The skeleton resolution MUST mirror [AnalysisEngine.evaluate]'s own call to
     * [ArchetypeSkeletonResolver.resolveWithColor] exactly — same `archetypeFormat =
     * ArchetypeFormat.of(format)` derivation, same `deckFormat = format` passthrough — or a
     * Commander build targets a DIFFERENT skeleton than the analysis grades it against (the exact
     * two-engine bug this whole campaign exists to close, F2). See
     * `CommanderPlanResolverTest`'s catalog-wide equality assertion.
     *
     * @param format the deck's [DeckFormat] — must be [DeckFormat.isCommanderFormat] (COMMANDER or
     *        COMMANDER_CASUAL); any other format is a caller bug (this campaign's scope is
     *        Commander-only, plan §1).
     */
    fun resolve(
        format: DeckFormat,
        commander: Card,
        pick: StrategyPick,
        identity: Set<ManaColor>,
    ): CommanderPlan {
        val archetypeFormat = ArchetypeFormat.of(format)
            ?: error("CommanderPlanResolver requires a Commander-shaped DeckFormat, got $format")

        val pin: StrategyPin = when (pick) {
            is StrategyPick.Curated -> pick.strategy.toPin(pick.tribe)
            StrategyPick.Custom -> StrategyPin(archetype = null, posture = null, themes = emptyList(), tribe = null)
        }

        // SAME call shape as AnalysisEngine.evaluate's own resolveWithColor invocation — see this
        // function's KDoc.
        val skeleton = ArchetypeSkeletonResolver.resolveWithColor(
            format = archetypeFormat,
            archetype = pin.archetype,
            posture = pin.posture,
            themes = pin.themes,
            identity = identity,
            deckFormat = format,
        )

        val tribeAxis = pin.tribe?.let { tribeAxisKey(it) }
        val commanderAxisProfile = SynergyGraph.cardAxisProfile(
            card = commander,
            format = archetypeFormat,
            dominantTribeAxis = tribeAxis,
            dominantTribeKey = pin.tribe,
        )
        val commanderAxes = commanderAxisProfile.produces.keys + commanderAxisProfile.consumes.keys
        val themeAxes = pin.themes.flatMap { THEME_TARGET_AXES[it].orEmpty() }.toSet()
        val targetAxes = themeAxes + commanderAxes + setOfNotNull(tribeAxis)

        return CommanderPlan(
            skeleton = skeleton,
            targetAxes = targetAxes,
            curveTargets = CurveTargets.forSkeleton(skeleton),
            landBand = skeleton.lands,
            manaFixBand = skeleton.manaFixTarget(),
        )
    }

    /** [tribe] is the raw `"tribe:<subtype>"` key ([TribeDeriver.TRIBE_PREFIX]-prefixed, same shape
     * [CuratedStrategy.toPin]/[DeckIdentitySeedTags.tribeSeedTag] expect) — converts it to
     * [SynergyGraph]'s `"TRIBE:<subtype>"` axis form, mirroring `SynergyGraph.build`'s own
     * `dominantTribeAxis` derivation exactly (`TRIBE_AXIS_PREFIX + key.removePrefix(TRIBE_PREFIX)`).
     *
     * Phase 4 (Deck Wizard Commander v3): promoted from `private` to `internal` — module-visible so
     * [com.mmg.manahub.feature.decks.domain.usecase.RecommendCommanderStrategiesUseCase] can derive
     * the SAME tribe axis key for a curated Tribal pick's scoring, without a second implementation. */
    internal fun tribeAxisKey(tribe: String): AxisKey = "TRIBE:${tribe.removePrefix(TribeDeriver.TRIBE_PREFIX)}"

    /**
     * [ThemeId] → the [SynergyGraph]'s static [AxisKey] it most directly targets — a DOCUMENTED
     * JUDGMENT CALL (same sourcing discipline as [CurveTargets.ZONE_SHARES]), since no existing
     * engine table maps a theme onto an axis directly (themes are role-band compositions;
     * [SynergyGraph]'s axis vocabulary is a SEPARATE, Phase-2-introduced layer — see that file's own
     * header). Entries intentionally OMITTED because no [SynergyGraph] static axis reads as a clean
     * match: `WHEELS` (no card-advantage-cycling axis exists), `CLONES_THEFT` (no clone/theft axis),
     * `VEHICLES`/`TRIBAL` (Vehicles' "crew" mechanic and Tribal's dynamic per-deck tribe are not a
     * single static axis — Tribal's target is carried separately via [tribeAxisKey]), `TREASURE`
     * (mana-production is not itself a modeled axis). A theme omitted here still contributes via the
     * commander's own [SynergyGraph.cardAxisProfile] axes and the resolved skeleton's role bands —
     * this table only widens [CommanderPlan.targetAxes] beyond those two sources.
     *
     * Phase 4 (Deck Wizard Commander v3): promoted from `private` to `internal` — module-visible so
     * [com.mmg.manahub.feature.decks.domain.usecase.RecommendCommanderStrategiesUseCase] can score a
     * catalog entry's theme-axis alignment against the commander's own axis profile without a second
     * hand-written copy of this table (per that use case's own KDoc — see its "signal (a)" note).
     */
    internal val THEME_TARGET_AXES: Map<ThemeId, Set<AxisKey>> = mapOf(
        ThemeId.TOKENS to setOf("TOKENS"),
        ThemeId.ARISTOCRATS to setOf("DEATH"),
        ThemeId.SPELLSLINGER to setOf("SPELLS"),
        ThemeId.REANIMATOR to setOf("GRAVEYARD"),
        ThemeId.LANDFALL to setOf("LANDFALL"),
        ThemeId.LIFEGAIN to setOf("LIFE"),
        ThemeId.PLUS1_COUNTERS to setOf("COUNTERS"),
        ThemeId.ARTIFACTS to setOf("ARTIFACTS"),
        ThemeId.ENCHANTRESS to setOf("ENCHANTMENTS"),
        ThemeId.BLINK to setOf("ETB"),
        ThemeId.MILL_OPPONENT to setOf("MILL_OPP"),
        ThemeId.SUPERFRIENDS to setOf("PLANESWALKERS"),
        ThemeId.SELF_MILL to setOf("GRAVEYARD"),
        ThemeId.EQUIPMENT to setOf("ATTACHED"),
        ThemeId.STORM to setOf("SPELLS"),
    )
}
