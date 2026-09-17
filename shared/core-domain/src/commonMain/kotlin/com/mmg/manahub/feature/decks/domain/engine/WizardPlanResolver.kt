package com.mmg.manahub.feature.decks.domain.engine
// COMMENTS_REVIEWED: 2026-09-16

import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.DeckFormat

// ═══════════════════════════════════════════════════════════════════════════════
//  WizardPlanResolver — Deck Wizard 60-card wave (v6), Phase 1.2. Renamed from
//  CommanderPlanResolver (Deck Wizard Commander v3 plan, Phase 1.1): the same "resolve a build's
//  target skeleton the exact same way AnalysisEngine.evaluate resolves it for the same pin"
//  contract, generalized from a Commander-only anchor to [BuildAnchor] (Commander card OR a
//  60-card colors+seeds pick, S1). The Commander branch is byte-identical to the pre-v6 behavior
//  (plan rule 0.3) — see [resolveCommander].
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * A wizard build's strategy choice — either a catalog pick (D4: "one [CuratedStrategy]
 * single select") or [Custom] (D6: generic baseline bands + the anchor's own axes, no pin).
 */
sealed interface StrategyPick {
    /** @property tribe the caller's tribe sub-pick, raw `"tribe:<subtype>"` key (same shape
     *           [CuratedStrategy.toPin] expects) — required only when [strategy]
     *           .[CuratedStrategy.requiresTribe] is true; not validated here. */
    data class Curated(val strategy: CuratedStrategy, val tribe: String? = null) : StrategyPick

    /** D6: no catalog pin — the generic baseline skeleton, plus the anchor's own axes as
     * synergy targets (still a real plan, not "no targets"). */
    data object Custom : StrategyPick
}

/**
 * A wizard build's anchor (S1) — what the build is targeted AROUND before a strategy is picked.
 * [WizardPlanResolver] and [com.mmg.manahub.feature.decks.domain.template.BuildWizardDeckUseCase]
 * branch on this instead of assuming a commander everywhere.
 */
sealed interface BuildAnchor {
    /** The pre-v6 Commander shape: one commander card, its own [Card.colorIdentity] IS the deck's
     * identity (derived inside the resolver, not carried here — see [resolveCommander]). */
    data class Commander(val card: Card) : BuildAnchor

    /** A 60-card build anchored on picked colors + (possibly zero) user-picked seed cards. An
     * empty [identity] is the colorless build (S9) — a real, supported identity, not "no pick
     * yet"; callers must not reach this anchor before the user has made a real color/seed choice. */
    data class Sixty(val identity: Set<ManaColor>, val seeds: List<Card>) : BuildAnchor
}

/** The commander card for a [BuildAnchor.Commander] anchor, `null` for [BuildAnchor.Sixty] —
 * convenience for a call site that needs "is there a commander" without a `when`. */
val BuildAnchor.commanderOrNull: Card?
    get() = (this as? BuildAnchor.Commander)?.card

/**
 * A wizard build's fully-resolved target (Phase 1.1, generalized Phase 1.2). [skeleton] is ALWAYS
 * non-null-equivalent (a real [ResolvedArchetypeSkeleton], generic-baseline for
 * [StrategyPick.Custom] — fixes F2).
 *
 * @property targetAxes the union of: each resolved theme's mapped [AxisKey] ([THEME_TARGET_AXES] —
 *           see that table's own KDoc for the ones intentionally left unmapped), the anchor's own
 *           produced/consumed axes ([SynergyGraph.cardAxisProfile] — the commander's for
 *           [BuildAnchor.Commander], the union over every seed's for [BuildAnchor.Sixty]), and the
 *           tribe's axis (when a tribe is resolved — see [internalTribe]).
 * @property curveTargets [CurveTargets.forSkeleton] — see that object's KDoc: this is NEW derived
 *           logic (a documented judgment call, not extracted from any existing engine number).
 * @property landBand [skeleton]'s own [ResolvedArchetypeSkeleton.lands] band, hoisted for
 *           convenience (the land-fill stage reads this without re-destructuring the skeleton
 *           every time).
 * @property manaFixBand [ResolvedArchetypeSkeleton.manaFixTarget] — `null` when the skeleton has no
 *           `mana_fix` role band (colorless-only identity — see [ArchetypeSkeletonResolver
 *           .resolveWithColorCount]'s own fail-closed convention).
 * @property internalTribe (W6b, F18) the raw `"tribe:<subtype>"` key this plan's tribe axis credit
 *           is based on — a [StrategyPick.Curated]'s own `pin.tribe`, or (Custom only) the anchor's
 *           derived lord tribe ([TribeDeriver.derivedLordTribe] for a commander,
 *           [dominantSeedTribe] for 60-card seeds). `null` when neither applies. The SINGLE source
 *           of truth [com.mmg.manahub.feature.decks.domain.template.BuildWizardDeckUseCase] reads
 *           for its OWN placement-time `dominantTribeAxis`/`dominantTribeKey` — see that class's own
 *           call site for why this must not be re-derived independently from `pin.tribe` a second
 *           time.
 */
data class WizardPlan(
    val skeleton: ResolvedArchetypeSkeleton,
    val targetAxes: Set<AxisKey>,
    val curveTargets: List<CurveTargets.CurveBucketTarget>,
    val landBand: RoleTarget,
    val manaFixBand: RoleTarget?,
    val internalTribe: String? = null,
)

object WizardPlanResolver {

    /**
     * Resolves [format]/[anchor]/[pick] into a [WizardPlan].
     *
     * The skeleton resolution MUST mirror [AnalysisEngine.evaluate]'s own call to
     * [ArchetypeSkeletonResolver.resolveWithColor] exactly — same `archetypeFormat =
     * ArchetypeFormat.of(format)` derivation, same `deckFormat = format` passthrough — or a build
     * targets a DIFFERENT skeleton than the analysis grades it against (the exact two-engine bug
     * this whole campaign exists to close, F2). See `WizardPlanResolverTest`'s catalog-wide
     * equality assertion.
     *
     * @param format the deck's [DeckFormat] — must resolve an [ArchetypeFormat] via
     *        [ArchetypeFormat.of] (every format except Draft); any other format is a caller bug.
     */
    fun resolve(format: DeckFormat, anchor: BuildAnchor, pick: StrategyPick): WizardPlan {
        val archetypeFormat = ArchetypeFormat.of(format)
            ?: error("WizardPlanResolver requires a format with an archetype skeleton, got $format")

        val pin: StrategyPin = when (pick) {
            is StrategyPick.Curated -> pick.strategy.toPin(pick.tribe)
            StrategyPick.Custom -> StrategyPin(archetype = null, posture = null, themes = emptyList(), tribe = null)
        }

        return when (anchor) {
            is BuildAnchor.Commander -> resolveCommander(format, archetypeFormat, anchor.card, pick, pin)
            is BuildAnchor.Sixty -> resolveSixty(format, archetypeFormat, anchor, pick, pin)
        }
    }

    /** Deck Wizard 60-card wave (v6), Phase 1.2: the pre-v6 4-arg signature, kept so every existing
     * call site/test compiles unchanged (plan §5 Phase 1.2) — a thin delegate onto
     * [resolve]/[BuildAnchor.Commander]. [identity] is now UNUSED: the sole production call site
     * ([com.mmg.manahub.feature.decks.domain.template.BuildWizardDeckUseCase]) always derived it as
     * `commander.colorIdentity.toManaColorSet()` anyway, so [resolveCommander] now derives it the
     * same way internally rather than trusting a second, independently-computed copy. Removed in
     * Phase 7. */
    @Suppress("UNUSED_PARAMETER")
    fun resolve(format: DeckFormat, commander: Card, pick: StrategyPick, identity: Set<ManaColor>): WizardPlan =
        resolve(format, BuildAnchor.Commander(commander), pick)

    /** [BuildAnchor.Commander] branch — byte-identical to the pre-v6 [CommanderPlanResolver]'s own
     * body (plan rule 0.3); only the identity source moved from a caller-supplied parameter to
     * [Card.colorIdentity] itself (see the 4-arg [resolve] overload's own KDoc for why that is a
     * no-op for the one real caller). */
    private fun resolveCommander(
        format: DeckFormat,
        archetypeFormat: ArchetypeFormat,
        commander: Card,
        pick: StrategyPick,
        pin: StrategyPin,
    ): WizardPlan {
        val identity = commander.colorIdentity.toManaColorSet()

        // F18 (E12, W6 Task 1): a Custom pick's INTERNAL build target may lean toward what the
        // commander actually does (e.g. Edgar Markov -> AGGRO), via the tag-tier of the SAME
        // CommanderArchetypeBias the Analysis engine already applies post-build (color-identity
        // tier deliberately excluded here -- see CommanderArchetypeBias.tagArchetype's own KDoc for
        // why). This is a build hint ONLY — `pin` above (what gets PERSISTED as the deck's
        // StrategyPin) stays Custom with `archetype = null` regardless of what this bias resolves
        // to. A Curated pick already carries a real archetype from its own catalog entry, so the
        // bias only ever applies here.
        val buildArchetype = when (pick) {
            is StrategyPick.Curated -> pin.archetype
            StrategyPick.Custom -> CommanderArchetypeBias.tagArchetype(commander.tags + commander.userTags)
        }

        // SAME call shape as AnalysisEngine.evaluate's own resolveWithColor invocation — see
        // resolve()'s own KDoc (archetype excepted, per the F18 build-hint above).
        val skeleton = ArchetypeSkeletonResolver.resolveWithColor(
            format = archetypeFormat,
            archetype = buildArchetype,
            posture = pin.posture,
            themes = pin.themes,
            identity = identity,
            deckFormat = format,
        )

        // F18 (W6b): a Custom pick has no persisted tribe (pin.tribe stays null, per F18's own
        // KDoc above), but its INTERNAL axis target may still credit the commander's own tribe —
        // same TribeDeriver.derivedLordTribe RecommendWizardStrategiesUseCase already uses for
        // its Tribal recommendation, so both call sites resolve the SAME tribe for the SAME
        // commander. A Curated pick's tribe already comes from pin.tribe; this is Custom-only.
        val internalTribe = pin.tribe ?: when (pick) {
            is StrategyPick.Curated -> null
            StrategyPick.Custom -> TribeDeriver.derivedLordTribe(commander)
        }
        val tribeAxis = internalTribe?.let { tribeAxisKey(it) }
        val commanderAxisProfile = SynergyGraph.cardAxisProfile(
            card = commander,
            format = archetypeFormat,
            dominantTribeAxis = tribeAxis,
            dominantTribeKey = internalTribe,
        )
        val commanderAxes = commanderAxisProfile.produces.keys + commanderAxisProfile.consumes.keys
        val themeAxes = pin.themes.flatMap { THEME_TARGET_AXES[it].orEmpty() }.toSet()
        val targetAxes = themeAxes + commanderAxes + setOfNotNull(tribeAxis)

        return WizardPlan(
            skeleton = skeleton,
            targetAxes = targetAxes,
            curveTargets = CurveTargets.forSkeleton(skeleton),
            landBand = skeleton.lands,
            manaFixBand = skeleton.manaFixTarget(),
            internalTribe = internalTribe,
        )
    }

    /** [BuildAnchor.Sixty] branch (plan §5 Phase 1.2, S8): a Curated pick resolves exactly like
     * Commander's (catalog archetype/posture/themes/tribe); a Custom pick has no single card to
     * bias off of, so [buildArchetype] majority-votes [CommanderArchetypeBias.tagArchetype] over
     * every seed's own tags (empty seeds -> no votes -> `null` -> the generic baseline, same
     * fail-open behavior [CommanderArchetypeBias.tagArchetype] already has for an untagged
     * commander) and [internalTribe] falls back to [dominantSeedTribe] instead of a single card's
     * lord tribe. */
    private fun resolveSixty(
        format: DeckFormat,
        archetypeFormat: ArchetypeFormat,
        anchor: BuildAnchor.Sixty,
        pick: StrategyPick,
        pin: StrategyPin,
    ): WizardPlan {
        val buildArchetype = when (pick) {
            is StrategyPick.Curated -> pin.archetype
            StrategyPick.Custom -> CommanderArchetypeBias.tagArchetype(anchor.seeds.flatMap { it.tags + it.userTags })
        }

        val skeleton = ArchetypeSkeletonResolver.resolveWithColor(
            format = archetypeFormat,
            archetype = buildArchetype,
            posture = pin.posture,
            themes = pin.themes,
            identity = anchor.identity,
            deckFormat = format,
        )

        val internalTribe = pin.tribe ?: when (pick) {
            is StrategyPick.Curated -> null
            StrategyPick.Custom -> dominantSeedTribe(anchor.seeds)
        }
        val tribeAxis = internalTribe?.let { tribeAxisKey(it) }

        val seedAxes = anchor.seeds.flatMap { seed ->
            val profile = SynergyGraph.cardAxisProfile(
                card = seed,
                format = archetypeFormat,
                dominantTribeAxis = tribeAxis,
                dominantTribeKey = internalTribe,
            )
            profile.produces.keys + profile.consumes.keys
        }.toSet()
        val themeAxes = pin.themes.flatMap { THEME_TARGET_AXES[it].orEmpty() }.toSet()
        val targetAxes = themeAxes + seedAxes + setOfNotNull(tribeAxis)

        return WizardPlan(
            skeleton = skeleton,
            targetAxes = targetAxes,
            curveTargets = CurveTargets.forSkeleton(skeleton),
            landBand = skeleton.lands,
            manaFixBand = skeleton.manaFixTarget(),
            internalTribe = internalTribe,
        )
    }

    /** Plan §5 Phase 1.2 (Custom, [BuildAnchor.Sixty] only): the [TribeDeriver.subtypeKeys] tribe
     * shared by the most seeds, requiring at least 2 seeds to agree (a single seed's own subtype is
     * not "the deck's tribe" — a commander has no such floor because it IS the one card the whole
     * deck is built around). Ties broken alphabetically by the raw `"tribe:<subtype>"` key, same
     * deterministic convention as [CommanderArchetypeBias.tagArchetype]'s own tie-break. */
    internal fun dominantSeedTribe(seeds: List<Card>): String? {
        val counts = seeds.flatMap { TribeDeriver.subtypeKeys(it) }.groupingBy { it }.eachCount()
        val shared = counts.filterValues { it >= 2 }
        if (shared.isEmpty()) return null
        val topCount = shared.values.max()
        return shared.filterValues { it == topCount }.keys.minOrNull()
    }

    private fun List<String>.toManaColorSet(): Set<ManaColor> =
        mapNotNull { symbol -> ManaColor.entries.firstOrNull { it.symbol == symbol } }.toSet()

    /** [tribe] is the raw `"tribe:<subtype>"` key ([TribeDeriver.TRIBE_PREFIX]-prefixed, same shape
     * [CuratedStrategy.toPin]/[DeckIdentitySeedTags.tribeSeedTag] expect) — converts it to
     * [SynergyGraph]'s `"TRIBE:<subtype>"` axis form, mirroring `SynergyGraph.build`'s own
     * `dominantTribeAxis` derivation exactly (`TRIBE_AXIS_PREFIX + key.removePrefix(TRIBE_PREFIX)`).
     *
     * Phase 4 (Deck Wizard Commander v3): promoted from `private` to `internal` — module-visible so
     * [com.mmg.manahub.feature.decks.domain.usecase.RecommendWizardStrategiesUseCase] can derive
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
     * anchor's own [SynergyGraph.cardAxisProfile] axes and the resolved skeleton's role bands —
     * this table only widens [WizardPlan.targetAxes] beyond those two sources.
     *
     * Phase 4 (Deck Wizard Commander v3): promoted from `private` to `internal` — module-visible so
     * [com.mmg.manahub.feature.decks.domain.usecase.RecommendWizardStrategiesUseCase] can score a
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
