package com.mmg.manahub.feature.decks.domain.usecase
// COMMENTS_REVIEWED: 2026-09-09

import com.mmg.manahub.core.domain.usecase.decks.BasicLandCalculator
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeFormat
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeId
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeRoleClassifier
import com.mmg.manahub.feature.decks.domain.engine.BuildAnchor
import com.mmg.manahub.feature.decks.domain.engine.ColorStrategyAffinity
import com.mmg.manahub.feature.decks.domain.engine.ColorStrategyEntry
import com.mmg.manahub.feature.decks.domain.engine.CopyPolicy
import com.mmg.manahub.feature.decks.domain.engine.WizardPlanResolver
import com.mmg.manahub.feature.decks.domain.engine.CuratedStrategy
import com.mmg.manahub.feature.decks.domain.engine.CuratedStrategyCatalog
import com.mmg.manahub.feature.decks.domain.engine.DeckIdentitySeedTags
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.RoleKey
import com.mmg.manahub.feature.decks.domain.engine.RoleTarget
import com.mmg.manahub.feature.decks.domain.engine.StrategyPick
import com.mmg.manahub.feature.decks.domain.engine.SynergyGraph
import com.mmg.manahub.feature.decks.domain.engine.ThemeId
import com.mmg.manahub.feature.decks.domain.engine.TribeDeriver
import com.mmg.manahub.feature.decks.domain.engine.availableIn
import com.mmg.manahub.feature.decks.domain.engine.isLegalForFormat
import com.mmg.manahub.feature.decks.domain.template.OwnedCard

// ═══════════════════════════════════════════════════════════════════════════════
//  RecommendWizardStrategiesUseCase — Deck Wizard Commander v3 plan, Phase 4.1; generalized to
//  60-card anchors in the Deck Wizard 60-card wave (v6), plan §5 Phase 2.1.
//
//  Replaces DeriveCommanderStrategiesUseCase (which derived a RAW candidate list of archetypes/
//  themes/tribes for the OLD 3-axis StrategyPickerSheet — dead weight now that the STRATEGY step is
//  a single-select over CuratedStrategyCatalog.ALL, D4). This class ranks the catalog itself.
// ═══════════════════════════════════════════════════════════════════════════════

/** One catalog entry the STRATEGY step's "Recommended" / "Partial fit" list can show, ranked by
 * [score] (higher first). [tribe] is the concrete `"tribe:<subtype>"` key this recommendation would
 * pin ([CuratedStrategy.requiresTribe] entries only) — `null` when either the entry needs no tribe,
 * or [CuratedStrategy.requiresTribe] is true but no tribe could be derived from the commander (that
 * entry is still returned, ranked at the very end regardless of its raw score — plan §8's "Other
 * plans" default — so the UI never offers a Tribal pick this use case cannot resolve to a concrete
 * creature type without the caller running the tribe sub-picker first). [reasons] are ≤ 2 short,
 * already-English display strings for the picker's reason chips — never localized further. */
data class StrategyRecommendation(
    val strategy: CuratedStrategy,
    val score: Double,
    val reasons: List<RecommendationReason>,
    val tribe: String?,
)

/** A single already-formatted reason chip label (e.g. "Commander makes tokens", "You own 34 fitting
 * cards"). A plain label rather than a sealed hierarchy — the picker renders [label] verbatim via
 * [com.mmg.manahub.core.ui.components.CardTagChip]-style chips, and every reason this use case ever
 * produces is a complete, ready-to-show sentence fragment; there is no reason-specific behavior a
 * caller needs to branch on. */
data class RecommendationReason(val label: String)

/**
 * Ranks [CuratedStrategyCatalog.ALL] (filtered to [format] via [CuratedStrategy.availableIn]) for a
 * specific [commander]/[identity]/owned collection — the STRATEGY step's single ranked list (D4).
 *
 * Signal sources, PRIMARY dominating (see each private `xScore` function for the exact math):
 * 1. **PRIMARY — the commander's own axis profile + role confidences** matched against the entry's
 *    resolved skeleton. Reuses [WizardPlanResolver]'s own `THEME_TARGET_AXES` table (promoted to
 *    `internal` this phase — see that file's own note) rather than hand-rolling a second theme→axis
 *    map, and [WizardPlanResolver.resolve] itself for the entry's resolved
 *    [com.mmg.manahub.feature.decks.domain.engine.ResolvedArchetypeSkeleton] (role bands).
 * 2. `card_strategy_tags` bridge ([ownTags]/[ownTribes]) via [DeckIdentitySeedTags] — same bridge
 *    [DeriveCommanderStrategiesUseCase] used, mapped onto SET membership against the entry instead
 *    of a raw candidate list.
 * 3. EDHREC aggregate theme names ([edhrecThemeNames]) — best-effort; every signal 1/2/4/5 already
 *    produces a valid, non-crashing ranking with this list empty (the Worker is frequently off).
 * 4. [ColorStrategyAffinity.forColors] — weak weight, a generic "this color pair likes this shell"
 *    prior.
 * 5. **Owned support** — the fraction of the entry's resolved skeleton's live (non-anti) role bands
 *    the [ownedCollection] can actually fill in [identity], via the SAME [ArchetypeRoleClassifier]
 *    every candidate profile in [com.mmg.manahub.feature.decks.domain.template
 *    .BuildCommanderDeckUseCase] is built from — this file adds a small additive, PRIVATE
 *    aggregation (`ownedRoleCounts`) rather than exposing `BuildCommanderDeckUseCase`'s own
 *    `CandidateProfile`/pool-construction publicly; that class's candidate pool is Commander-slot
 *    scoring machinery (pip feasibility, curve buckets, community prior) this use case has no need
 *    for, so promoting the whole thing would be a much larger, unrelated surface change for a
 *    5-line filter this file can reproduce cheaply (computed ONCE per [invoke] call, reused across
 *    every catalog entry — not re-derived per entry).
 *
 * Never throws: every signal degrades to zero contribution when its input is empty/absent (empty
 * [ownedCollection], empty [ownTags]/[ownTribes]/[edhrecThemeNames], a colorless [identity]).
 */
class RecommendWizardStrategiesUseCase {

    /** Deck Wizard 60-card wave (v6), plan §5 Phase 2.1: [BuildAnchor]-based entry point. The
     * [BuildAnchor.Commander] branch is a thin delegate onto the pre-v6 [Card]-based overload
     * below (byte-identical, unchanged body — rule 0.3); [BuildAnchor.Sixty] is scored by
     * [invokeSixty], the same five signals generalized from one commander card to a seed pool. */
    operator fun invoke(
        format: DeckFormat,
        anchor: BuildAnchor,
        ownedCollection: List<OwnedCard> = emptyList(),
        ownTags: List<CardTag> = emptyList(),
        ownTribes: List<String> = emptyList(),
        edhrecThemeNames: List<String> = emptyList(),
    ): List<StrategyRecommendation> = when (anchor) {
        is BuildAnchor.Commander -> invoke(
            format = format,
            commander = anchor.card,
            identity = anchor.card.colorIdentity.toManaColorSet(),
            ownedCollection = ownedCollection,
            ownTags = ownTags,
            ownTribes = ownTribes,
            edhrecThemeNames = edhrecThemeNames,
        )
        is BuildAnchor.Sixty -> invokeSixty(format, anchor, ownedCollection, ownTags, edhrecThemeNames)
    }

    operator fun invoke(
        format: DeckFormat,
        commander: Card,
        identity: Set<ManaColor>,
        ownedCollection: List<OwnedCard> = emptyList(),
        ownTags: List<CardTag> = emptyList(),
        ownTribes: List<String> = emptyList(),
        edhrecThemeNames: List<String> = emptyList(),
    ): List<StrategyRecommendation> {
        val archetypeFormat = ArchetypeFormat.of(format) ?: return emptyList()
        val candidates = CuratedStrategyCatalog.ALL.filter { it.availableIn(format) }
        if (candidates.isEmpty()) return emptyList()

        val commanderRoleConfidence = ArchetypeRoleClassifier.classify(commander)
        // Intrinsic (non-tribal) commander axis signal — computed once; a per-entry tribe
        // substitution is layered in only for the entries that actually derive one below.
        val baseCommanderAxes = SynergyGraph.cardAxisProfile(commander, archetypeFormat)
            .let { it.produces.keys + it.consumes.keys }

        // W6b: extracted to TribeDeriver.derivedLordTribe (shared verbatim with
        // WizardPlanResolver's Custom build path, see that function's own KDoc).
        val derivedTribe = TribeDeriver.derivedLordTribe(commander)

        val tagArchetypes = ownTags.mapNotNull { DeckIdentitySeedTags.archetypeForTag(it) }.toSet()
        val tagThemes = ownTags.mapNotNull { DeckIdentitySeedTags.themeForTag(it) }.toSet()
        val tagTribeWords = ownTribes.toSet()
        val edhrecThemes = edhrecThemeNames.mapNotNull { ThemeId.fromDisplayName(it) }.toSet()
        val colorAffinity = ColorStrategyAffinity.forColors(identity)

        val ownedRoleCounts = ownedRoleCounts(ownedCollection, identity, format, commander.scryfallId)

        val scored = candidates.map { entry ->
            val entryTribe = if (entry.requiresTribe) derivedTribe else null
            val entryTribeAxis = entryTribe?.let { WizardPlanResolver.tribeAxisKey(it) }
            val commanderAxes = if (entryTribeAxis != null) {
                SynergyGraph.cardAxisProfile(commander, archetypeFormat, entryTribeAxis, entryTribe)
                    .let { it.produces.keys + it.consumes.keys }
            } else {
                baseCommanderAxes
            }
            val entryAxes = entry.themes.flatMap { WizardPlanResolver.THEME_TARGET_AXES[it].orEmpty() }
                .toSet() + setOfNotNull(entryTribeAxis)

            val skeleton = WizardPlanResolver.resolve(
                format = format,
                commander = commander,
                pick = StrategyPick.Curated(entry, entryTribe),
                identity = identity,
            ).skeleton
            val liveRoles = skeleton.roleTargets.filterKeys { it !in skeleton.antiRoles }

            val axisScore = axisAlignmentScore(entryAxes, commanderAxes)
            val roleScore = roleAlignmentScore(liveRoles.keys, commanderRoleConfidence)
            val primaryScore = PRIMARY_AXIS_WEIGHT * axisScore + PRIMARY_ROLE_WEIGHT * roleScore

            val tagScore = tagBridgeScore(entry, tagArchetypes, tagThemes, entryTribe, tagTribeWords)
            val edhrecScore = if (entry.themes.any { it in edhrecThemes }) 1.0 else 0.0
            val colorScore = colorAffinityScore(entry, colorAffinity)
            val ownedCoverage = ownedCoverageScore(liveRoles, ownedRoleCounts)

            val total = PRIMARY_WEIGHT * primaryScore +
                TAG_WEIGHT * tagScore +
                EDHREC_WEIGHT * edhrecScore +
                COLOR_WEIGHT * colorScore +
                OWNED_WEIGHT * ownedCoverage

            val reasons = buildReasons(entry, axisScore, roleScore, tagScore > 0.0, edhrecScore > 0.0, ownedCoverage, ownedRoleCounts, liveRoles.keys)
            val unresolved = entry.requiresTribe && entryTribe == null
            ScoredEntry(StrategyRecommendation(entry, total, reasons, entryTribe), unresolved)
        }

        val (resolved, unresolved) = scored.partition { !it.unresolved }
        return (
            resolved.sortedWith(compareByDescending<ScoredEntry> { it.recommendation.score }.thenBy { it.recommendation.strategy.id }) +
                unresolved.sortedWith(compareByDescending<ScoredEntry> { it.recommendation.score }.thenBy { it.recommendation.strategy.id })
            ).map { it.recommendation }
    }

    /**
     * Deck Wizard 60-card wave (v6), plan §5 Phase 2.1: the [BuildAnchor.Sixty] scoring path — same
     * five signal sources as the Commander [invoke] above, PRIMARY generalized from one card's own
     * axis/role profile to the seed pool's:
     * 1. PRIMARY — the UNION of every seed's own axis profile (produces ∪ consumes), and role
     *    confidences SUMMED (not averaged) across seeds, so a role two seeds both touch reads as a
     *    stronger signal than a role only one touches. Empty [BuildAnchor.Sixty.seeds] (the Colors
     *    entry flow) leaves both naturally at zero — [baseSeedAxes]/[seedRoleConfidence] are empty,
     *    and [axisAlignmentScore]/[roleAlignmentScore] already return `0.0` for an empty input, so
     *    no explicit branch is needed here.
     * 2. `ownTags`-bridge — [tagBridgeScore], reused unchanged (format-agnostic already).
     * 3. [edhrecThemeNames] — not used for 60-card; the caller passes an empty list and
     *    `edhrecScore` is naturally `0.0`, same math as the Commander path.
     * 4. [ColorStrategyAffinity.curatedFor] resolved against [BuildAnchor.Sixty.identity] — the
     *    DOMINANT signal for the Colors entry flow (empty seeds): weighted x2 there, since no
     *    seed-derived PRIMARY signal exists yet to compete with it (documented judgment call, S1.2).
     * 5. Owned support — [ownedRoleCountsSixty], the 60-card pool predicate
     *    ([isLegalForFormat] + `colorIdentity ⊆ identity`, seeds excluded), each owned card
     *    contributing [CopyPolicy.maxPlaceable] copies instead of the Commander path's flat `+1`
     *    (an owned 4-of removal supports its role band with 4, not 1 — S2's own consistency rule
     *    extended to this signal). The Commander path's own [ownedRoleCounts] is untouched (rule
     *    0.3: it must stay `+1`-per-card byte-identical).
     *
     * [tribe] resolution reuses [WizardPlanResolver.dominantSeedTribe] (the SAME rule the Sixty
     * build itself uses for a Custom pick's internal tribe axis) rather than a second derivation.
     */
    private fun invokeSixty(
        format: DeckFormat,
        anchor: BuildAnchor.Sixty,
        ownedCollection: List<OwnedCard>,
        ownTags: List<CardTag>,
        edhrecThemeNames: List<String>,
    ): List<StrategyRecommendation> {
        val archetypeFormat = ArchetypeFormat.of(format) ?: return emptyList()
        val candidates = CuratedStrategyCatalog.ALL.filter { it.availableIn(format) }
        if (candidates.isEmpty()) return emptyList()

        val seeds = anchor.seeds
        val seedIds = seeds.map { it.scryfallId }.toSet()
        val derivedTribe = WizardPlanResolver.dominantSeedTribe(seeds)

        val baseSeedAxes = seeds.flatMap { SynergyGraph.cardAxisProfile(it, archetypeFormat).let { p -> p.produces.keys + p.consumes.keys } }.toSet()
        val seedRoleConfidence: Map<RoleKey, Float> = buildMap {
            seeds.forEach { seed ->
                ArchetypeRoleClassifier.classify(seed).forEach { (role, confidence) -> this[role] = (this[role] ?: 0f) + confidence }
            }
        }

        val tagArchetypes = ownTags.mapNotNull { DeckIdentitySeedTags.archetypeForTag(it) }.toSet()
        val tagThemes = ownTags.mapNotNull { DeckIdentitySeedTags.themeForTag(it) }.toSet()
        val edhrecThemes = edhrecThemeNames.mapNotNull { ThemeId.fromDisplayName(it) }.toSet()
        val curatedForResults = ColorStrategyAffinity.curatedFor(anchor.identity, format)
        // S1.2 (2.1): curatedFor is the ONLY color/strategy-shell signal in the Colors flow (no
        // seeds to derive a PRIMARY signal from) -- weighted x2 there so it can meaningfully
        // separate the ranking on its own; a documented judgment call, not derived from data.
        val colorAffinityWeight = if (seeds.isEmpty()) 2.0 else 1.0

        val ownedRoleCounts = ownedRoleCountsSixty(ownedCollection, anchor.identity, format, seedIds)

        val scored = candidates.map { entry ->
            val entryTribe = if (entry.requiresTribe) derivedTribe else null
            val entryTribeAxis = entryTribe?.let { WizardPlanResolver.tribeAxisKey(it) }
            val seedAxes = if (entryTribeAxis != null) {
                seeds.flatMap { SynergyGraph.cardAxisProfile(it, archetypeFormat, entryTribeAxis, entryTribe).let { p -> p.produces.keys + p.consumes.keys } }.toSet()
            } else {
                baseSeedAxes
            }
            val entryAxes = entry.themes.flatMap { WizardPlanResolver.THEME_TARGET_AXES[it].orEmpty() }
                .toSet() + setOfNotNull(entryTribeAxis)

            val skeleton = WizardPlanResolver.resolve(
                format = format,
                anchor = anchor,
                pick = StrategyPick.Curated(entry, entryTribe),
            ).skeleton
            val liveRoles = skeleton.roleTargets.filterKeys { it !in skeleton.antiRoles }

            val axisScore = axisAlignmentScore(entryAxes, seedAxes)
            val roleScore = roleAlignmentScore(liveRoles.keys, seedRoleConfidence)
            val primaryScore = PRIMARY_AXIS_WEIGHT * axisScore + PRIMARY_ROLE_WEIGHT * roleScore

            val tagScore = tagBridgeScore(entry, tagArchetypes, tagThemes, entryTribe, emptySet())
            val edhrecScore = if (entry.themes.any { it in edhrecThemes }) 1.0 else 0.0
            val colorScore = colorAffinityScoreSixty(entry.id, curatedForResults) * colorAffinityWeight
            val ownedCoverage = ownedCoverageScore(liveRoles, ownedRoleCounts)

            val total = PRIMARY_WEIGHT * primaryScore +
                TAG_WEIGHT * tagScore +
                EDHREC_WEIGHT * edhrecScore +
                COLOR_WEIGHT * colorScore +
                OWNED_WEIGHT * ownedCoverage

            val reasons = buildReasonsSixty(entry, anchor.identity, axisScore, roleScore, tagScore > 0.0, colorScore > 0.0, ownedCoverage, ownedRoleCounts, liveRoles.keys)
            val unresolved = entry.requiresTribe && entryTribe == null
            ScoredEntry(StrategyRecommendation(entry, total, reasons, entryTribe), unresolved)
        }

        val (resolved, unresolved) = scored.partition { !it.unresolved }
        return (
            resolved.sortedWith(compareByDescending<ScoredEntry> { it.recommendation.score }.thenBy { it.recommendation.strategy.id }) +
                unresolved.sortedWith(compareByDescending<ScoredEntry> { it.recommendation.score }.thenBy { it.recommendation.strategy.id })
            ).map { it.recommendation }
    }

    /**
     * Deck Wizard Commander v4 plan, W3/E3 — splits an [invoke] result into "Recommended" (first
     * pair element) and "Partial fit" (second): a commander genuinely supports a handful of plans,
     * not the whole catalog, so this is a real cut, not a `take(6)` cosmetic split.
     *
     * A candidate is Recommended only when it clears BOTH gates below (a hard cap of
     * [RECOMMENDED_CAP] applies after):
     * 1. **[ABSOLUTE_MIN_SCORE]** — excludes candidates whose score is pure generic noise (no
     *    commander-specific axis/role/tag/EDHREC/owned-coverage signal at all, only
     *    [colorAffinityScore]'s color-pair prior). Calibrated from the real corpus, not guessed:
     *    over the 7 fixture commanders in `RecommendCommanderStrategiesUseCaseTest`, a commander
     *    with literally zero non-color signal (Urza with no owned collection, no `card_strategy_tags`,
     *    no EDHREC data) tops out at 0.9 (`COLOR_WEIGHT * colorAffinityScore` alone) — every fixture
     *    with ANY real signal clears 1.0 by a wide margin (Omnath's weakest real match is 1.5). This
     *    is what makes a "no strong signal" commander yield an EMPTY Recommended list (§ below)
     *    instead of 5 color-affinity ties dressed up as recommendations.
     * 2. **Relative-spread threshold** — `topScore - RELATIVE_SPREAD_FRACTION * (topScore - bottomScore)`.
     *    Keeps only the top slice of THIS commander's own score range, so a landslide winner (Edgar
     *    Markov: tribal at 9.18 vs. everything else under 3.8) yields a short, high-confidence list
     *    instead of padding to 5 with plans the commander doesn't actually push toward, while a
     *    genuinely close race (Karlov: aristocrats 3.78 / lifegain 3.78, both real Orzhov plans)
     *    keeps both. `RELATIVE_SPREAD_FRACTION = 0.2` was chosen by checking it against every fixture:
     *    tight enough to cut Karlov's third-place `tokens` (3.48, a real but weaker fit) while loose
     *    enough to keep Meren's `lifegain` (3.225) alongside its `aristocrats` top pick (3.477).
     *
     * [CuratedStrategy.requiresTribe] entries the recommender could not resolve a concrete tribe for
     * (the same `unresolved` set [invoke] already pushes to the very end) are never eligible for
     * Recommended — re-derived here from the public [StrategyRecommendation.tribe]/
     * [CuratedStrategy.requiresTribe] fields rather than threading a private flag through the return
     * type. An empty Recommended list is a valid, INTENTIONAL result (a commander with no real
     * signal) — callers must not treat it as an error or fall back to padding it themselves; "Custom"
     * stays separately offered by the caller in every case (D6, unchanged).
     */
    fun splitRecommended(recommendations: List<StrategyRecommendation>): Pair<List<StrategyRecommendation>, List<StrategyRecommendation>> {
        val resolvable = recommendations.filter { !(it.strategy.requiresTribe && it.tribe == null) }
        if (resolvable.isEmpty()) return emptyList<StrategyRecommendation>() to recommendations

        val top = resolvable.first().score
        val bottom = resolvable.last().score
        val relativeThreshold = top - RELATIVE_SPREAD_FRACTION * (top - bottom)

        val recommended = resolvable
            .filter { it.score >= ABSOLUTE_MIN_SCORE && it.score >= relativeThreshold }
            .take(RECOMMENDED_CAP)
        val recommendedIds = recommended.map { it.strategy.id }.toSet()
        val partialFit = recommendations.filter { it.strategy.id !in recommendedIds }
        return recommended to partialFit
    }

    private data class ScoredEntry(val recommendation: StrategyRecommendation, val unresolved: Boolean)

    private fun axisAlignmentScore(entryAxes: Set<String>, commanderAxes: Set<String>): Double {
        if (entryAxes.isEmpty()) return 0.0
        return entryAxes.intersect(commanderAxes).size.toDouble() / entryAxes.size
    }

    private fun roleAlignmentScore(liveRoles: Set<RoleKey>, commanderRoleConfidence: Map<RoleKey, Float>): Double {
        if (liveRoles.isEmpty()) return 0.0
        return liveRoles.sumOf { role -> (commanderRoleConfidence[role] ?: 0f).toDouble().coerceIn(0.0, 1.0) } / liveRoles.size
    }

    private fun tagBridgeScore(
        entry: CuratedStrategy,
        tagArchetypes: Set<ArchetypeId>,
        tagThemes: Set<ThemeId>,
        entryTribe: String?,
        tagTribeWords: Set<String>,
    ): Double {
        var score = 0.0
        if (entry.archetypes.any { it in tagArchetypes }) score += 1.0
        if (entry.themes.any { it in tagThemes }) score += 1.0
        if (entryTribe != null && entryTribe.removePrefix(TribeDeriver.TRIBE_PREFIX) in tagTribeWords) score += 1.0
        return score
    }

    private fun colorAffinityScore(entry: CuratedStrategy, colorAffinity: List<ColorStrategyEntry>): Double {
        return colorAffinity.filter { ce ->
            (ce.archetype != null && ce.archetype in entry.archetypes) ||
                (ce.posture != null && ce.posture in entry.postures) ||
                ce.themes.any { it in entry.themes }
        }.maxOfOrNull { it.weight.toDouble() } ?: 0.0
    }

    /** Deck Wizard 60-card wave (v6): [entry]'s own weight from [ColorStrategyAffinity.curatedFor]
     * (already resolved to real catalog ids) — a direct id lookup, unlike the Commander path's own
     * [colorAffinityScore] archetype/posture/theme intersection, since `curatedFor` already did that
     * resolution once for the whole identity. */
    private fun colorAffinityScoreSixty(entryId: String, curatedFor: List<Pair<CuratedStrategy, Float>>): Double =
        curatedFor.firstOrNull { it.first.id == entryId }?.second?.toDouble() ?: 0.0

    /** Fraction of [liveRoles] the owned pool can fill, averaged 0f..1f per role (each role clamped
     * individually so one massively-overowned role cannot mask a real gap in another). */
    private fun ownedCoverageScore(
        liveRoles: Map<RoleKey, RoleTarget>,
        ownedRoleCounts: Map<RoleKey, Int>,
    ): Double {
        if (liveRoles.isEmpty()) return 0.0
        return liveRoles.entries.sumOf { (role, target) ->
            val ideal = target.ideal.coerceAtLeast(1)
            val have = ownedRoleCounts[role] ?: 0
            (have.toDouble() / ideal).coerceIn(0.0, 1.0)
        } / liveRoles.size
    }

    /** Owned, identity-legal, non-land, non-commander pool -> per-[RoleKey] classified count.
     * Computed ONCE per [invoke] call and reused across every catalog entry's [ownedCoverageScore]
     * (mirrors the "compute the candidate pool once" discipline
     * [com.mmg.manahub.feature.decks.domain.template.BuildCommanderDeckUseCase] uses for its own
     * candidate pool, without depending on that class's private types). */
    private fun ownedRoleCounts(
        ownedCollection: List<OwnedCard>,
        identity: Set<ManaColor>,
        format: DeckFormat,
        commanderId: String,
    ): Map<RoleKey, Int> {
        if (ownedCollection.isEmpty()) return emptyMap()
        val identitySymbols = identity.map { it.symbol }.toSet()
        val pool = ownedCollection
            .filter { it.quantity > 0 }
            .map { it.card }
            .distinctBy { it.name }
            .filter { it.scryfallId != commanderId }
            .filterNot { BasicLandCalculator.isLand(it) }
            .filter { identitySymbols.containsAll(it.colorIdentity) }
            .filter { isLegalForCommanderFormat(it, format) }

        val counts = mutableMapOf<RoleKey, Int>()
        pool.forEach { card ->
            ArchetypeRoleClassifier.classify(card).forEach { (role, confidence) ->
                if (confidence > 0f) counts[role] = (counts[role] ?: 0) + 1
            }
        }
        return counts
    }

    private fun isLegalForCommanderFormat(card: Card, format: DeckFormat): Boolean =
        if (format == DeckFormat.COMMANDER) card.legalityCommander == "legal" else card.legalityCommander != "banned"

    /** Deck Wizard 60-card wave (v6), plan §5 Phase 2.1 (S2): the 60-card counterpart of
     * [ownedRoleCounts] — same pool shape (owned, identity-legal, non-land, one printing per name)
     * but filtered by [isLegalForFormat] (the shared legality predicate every 60-card format uses,
     * not [isLegalForCommanderFormat]) and EXCLUDING [excludeIds] (the seeds, already counted via
     * the PRIMARY signal above — counting them again here would double-credit them). Each card
     * contributes [CopyPolicy.maxPlaceable] copies rather than a flat `+1`, so an owned 4-of
     * genuinely supports its role band 4x as much as a 1-of. [ownedRoleCounts] itself is UNTOUCHED
     * (rule 0.3: must stay `+1`-per-card byte-identical for Commander). */
    private fun ownedRoleCountsSixty(
        ownedCollection: List<OwnedCard>,
        identity: Set<ManaColor>,
        format: DeckFormat,
        excludeIds: Set<String>,
    ): Map<RoleKey, Int> {
        if (ownedCollection.isEmpty()) return emptyMap()
        val identitySymbols = identity.map { it.symbol }.toSet()
        val ownedByName = ownedCollection
            .filter { it.quantity > 0 }
            .groupBy { it.card.name }
            .mapValues { (_, owned) -> owned.sumOf { it.quantity } }
        val pool = ownedCollection
            .filter { it.quantity > 0 }
            .map { it.card }
            .distinctBy { it.name }
            .filter { it.scryfallId !in excludeIds }
            .filterNot { BasicLandCalculator.isLand(it) }
            .filter { identitySymbols.containsAll(it.colorIdentity) }
            .filter { isLegalForFormat(it, format) }

        val counts = mutableMapOf<RoleKey, Int>()
        pool.forEach { card ->
            val copies = CopyPolicy.maxPlaceable(card, format, ownedByName[card.name])
            ArchetypeRoleClassifier.classify(card).forEach { (role, confidence) ->
                if (confidence > 0f) counts[role] = (counts[role] ?: 0) + copies
            }
        }
        return counts
    }

    private fun buildReasons(
        entry: CuratedStrategy,
        axisScore: Double,
        roleScore: Double,
        hasTagMatch: Boolean,
        hasEdhrecMatch: Boolean,
        ownedCoverage: Double,
        ownedRoleCounts: Map<RoleKey, Int>,
        liveRoles: Set<RoleKey>,
    ): List<RecommendationReason> {
        val reasons = mutableListOf<RecommendationReason>()
        if (axisScore > 0.0 || roleScore > 0.0) {
            reasons += RecommendationReason("Commander fits ${entry.displayName}")
        } else if (hasTagMatch) {
            reasons += RecommendationReason("Your tags point to ${entry.displayName}")
        } else if (hasEdhrecMatch) {
            reasons += RecommendationReason("Popular on EDHREC for this commander")
        }
        if (ownedCoverage > 0.0) {
            val ownedCount = liveRoles.sumOf { ownedRoleCounts[it] ?: 0 }
            reasons += RecommendationReason("You own $ownedCount fitting cards")
        }
        return reasons.take(2)
    }

    /** Deck Wizard 60-card wave (v6): "Your cards lean <X>" for the PRIMARY (seed) signal, "Fits
     * <colors>" for the color-affinity signal (S1.2's own reason labels, appended to the existing
     * Commander-path vocabulary) — everything else reuses [buildReasons]' own reason text/order. */
    private fun buildReasonsSixty(
        entry: CuratedStrategy,
        identity: Set<ManaColor>,
        axisScore: Double,
        roleScore: Double,
        hasTagMatch: Boolean,
        hasColorMatch: Boolean,
        ownedCoverage: Double,
        ownedRoleCounts: Map<RoleKey, Int>,
        liveRoles: Set<RoleKey>,
    ): List<RecommendationReason> {
        val reasons = mutableListOf<RecommendationReason>()
        if (axisScore > 0.0 || roleScore > 0.0) {
            reasons += RecommendationReason("Your cards lean ${entry.displayName}")
        } else if (hasTagMatch) {
            reasons += RecommendationReason("Your tags point to ${entry.displayName}")
        } else if (hasColorMatch) {
            val colorsLabel = identity.sortedBy { it.symbol }.joinToString("/") { it.displayName }.ifEmpty { "Colorless" }
            reasons += RecommendationReason("Fits $colorsLabel")
        }
        if (ownedCoverage > 0.0) {
            val ownedCount = liveRoles.sumOf { ownedRoleCounts[it] ?: 0 }
            reasons += RecommendationReason("You own $ownedCount fitting cards")
        }
        return reasons.take(2)
    }

    private companion object {
        const val PRIMARY_AXIS_WEIGHT = 0.6
        const val PRIMARY_ROLE_WEIGHT = 0.4
        const val PRIMARY_WEIGHT = 10.0
        const val TAG_WEIGHT = 2.0
        const val EDHREC_WEIGHT = 1.0
        const val COLOR_WEIGHT = 1.0
        const val OWNED_WEIGHT = 3.0

        /** Hard cap on the "Recommended" group (E3) regardless of how many candidates clear the
         * score gates in [splitRecommended]. */
        const val RECOMMENDED_CAP = 5

        /** See [splitRecommended]'s KDoc — the calibrated noise floor a commander with zero real
         * signal cannot clear. */
        const val ABSOLUTE_MIN_SCORE = 1.0

        /** See [splitRecommended]'s KDoc — fraction of a commander's own top-to-bottom score range
         * that still counts as "the same tier" as the top pick. */
        const val RELATIVE_SPREAD_FRACTION = 0.2
    }
}

/** Deck Wizard 60-card wave (v6), plan §5 Phase 2.1: kept so every pre-v6 call site/test that names
 * `RecommendCommanderStrategiesUseCase` (constructor calls included) compiles unchanged — removed
 * in Phase 7. */
typealias RecommendCommanderStrategiesUseCase = RecommendWizardStrategiesUseCase

private fun List<String>.toManaColorSet(): Set<ManaColor> =
    mapNotNull { symbol -> ManaColor.entries.firstOrNull { it.symbol == symbol } }.toSet()
