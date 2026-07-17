package com.mmg.manahub.feature.decks.domain.template

import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.core.model.TagCategory
import com.mmg.manahub.core.model.UserCardWithCard
import com.mmg.manahub.feature.decks.domain.engine.DeckEntry
import com.mmg.manahub.feature.decks.domain.engine.DeckScorer
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.SeedStrategy
import com.mmg.manahub.feature.decks.domain.engine.TribeDeriver
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Deck Builder v2 (`docs/plans/deck-builder-v2-plan.md` §3.5) — Discoveries replacement.
 * [DeckMagicEngine.discoverSynergies] (old) is UNTOUCHED and stays reachable behind
 * `DeckFeatureFlags.DECK_STUDIO_BROWSE_INSPIRATIONS_ENABLED`; this is a flag-switched sibling
 * (`DeckFeatureFlags.DISCOVERIES_V2_ENABLED`), never a replacement of the old file.
 */
sealed class DiscoveryClusterKey {
    /** A [TagCategory.STRATEGY] or [TagCategory.ARCHETYPE] identity tag cluster. */
    data class Strategy(val tag: CardTag) : DiscoveryClusterKey()

    /** A runtime-derived `tribe:<x>` cluster ([TribeDeriver]) — NEVER persisted, mirrors
     * [com.mmg.manahub.feature.decks.domain.engine.DeckScorer]'s own tribe-key contract. */
    data class Tribe(val tribeKey: String, val displayLabel: String) : DiscoveryClusterKey()
}

/**
 * One v2 discovery cluster, ranked and color-coherent (plan §3.5).
 *
 * @param members the cluster's owned cards, ranked by [DeckScorer.fit] against a profile seeded
 *   with the cluster's own identity (never Room/collection insertion order — fixes root-cause
 *   1.2.7). Also doubles as the "Build this" seed-candidate list (D11) — the wizard pre-fills
 *   [com.mmg.manahub.feature.decks.domain.template.DeckWizardSpec.seeds] from this list, capped
 *   by the wizard the same way [com.mmg.manahub.feature.decks.presentation.DeckStudioViewModel
 *   .startFromDiscovery] already caps the old handoff.
 * @param strategyHint the [SeedStrategy] to pre-fill the wizard with — resolved for a
 *   [DiscoveryClusterKey.Strategy] cluster via [SeedStrategy.forTag] or [SeedStrategy.TRIBAL] for a
 *   [DiscoveryClusterKey.Tribe] cluster (the archetype layer
 *   has no per-tribe skeleton — see `DeckTemplateResolver.mapStrategyToArchetype`'s KDoc — so the
 *   SEEDS are what make a tribal "Build this" concretely tribe-specific, not this hint alone).
 * @param themeHint free-form label, set ONLY for a tribe cluster (e.g. "Vampires") — forward
 *   -compatible plumbing carried on [DeckWizardSpec.themeHint], not yet consumed by the synthetic
 *   template path (see that field's own KDoc).
 */
data class DeckDiscoveryV2(
    val key: DiscoveryClusterKey,
    val label: String,
    val memberCount: Int,
    val dominantColors: Set<ManaColor>,
    val members: List<Card>,
    val strategyHint: SeedStrategy?,
    val themeHint: String?,
)

/**
 * Deck Builder v2 (plan §3.5): clusters the user's OWN collection on identity signals only
 * (STRATEGY/ARCHETYPE tags + derived `tribe:<x>` keys — NEVER TYPE/KEYWORD, fixing root-cause
 * 1.2.7's "clusters read as card types" complaint), ranks each cluster's members by
 * [DeckScorer.fit] against a profile seeded with the cluster's own identity, and requires color
 * coherence within the cluster's own dominant colors (an off-color card carrying only an
 * incidental tag/tribe match is dropped, not just de-prioritized).
 *
 * ## Copies, not distinct cards (deliberate interpretation of "≥6 strategies / ≥8 tribes")
 * The plan's minimum cluster sizes ("≥6 for strategies, ≥8 copies for tribes ... TRIBE_ABS_THRESHOLD
 * alignment") are read here as OWNED-COPY counts for BOTH cluster kinds (not distinct-card counts)
 * — this is what "23 Vampires" actually means to a user (their binder, not their unique prints),
 * and it mirrors [DeckScorer]'s own copies-based [TRIBE_ABS_THRESHOLD] semantics exactly. Distinct
 * from [CollectionProfileUseCase.dominantTribes]/`dominantStrategies`, which count distinct cards
 * (that use case is only ever handed a `List<Card>` with no per-card quantity, by the same
 * ownership-split precedent [CollectionProfileUseCase] documents) — this use case is handed
 * [UserCardWithCard] instead specifically because copies matter here.
 *
 * ## Ownership split (mirrors Motor A/B / [CollectionProfileUseCase] precedent)
 * Takes an ALREADY-SNAPSHOTTED collection — no repository injected, no internal `observe*()` call.
 * Pure, deterministic given the same input.
 */
class DiscoverSynergiesV2UseCase(
    private val deckScorer: DeckScorer,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    suspend operator fun invoke(
        collection: List<UserCardWithCard>,
        limit: Int = DEFAULT_LIMIT,
    ): List<DeckDiscoveryV2> = withContext(ioDispatcher) {
        // Dedupe by NAME, not scryfallId, before clustering: two printings of the same card (a
        // regular + a foil/promo copy) must collapse to ONE Card object here, or they end up as
        // two separate cluster members with the same name -- inflating `copies` (buildDiscovery
        // sums quantityByName, already grouped by name, once per surviving member) and producing a
        // duplicate-name row in `members`/`mainboardSeed`. `quantityByName` itself stays keyed by
        // name and already sums across printings, so it is unaffected by this change.
        val cards = collection.distinctBy { it.card.name }.map { it.card }
        if (cards.isEmpty()) return@withContext emptyList()

        val quantityByName = collection
            .groupBy { it.card.name }
            .mapValues { (_, rows) -> rows.sumOf { it.userCard.quantity }.coerceAtLeast(1) }

        val strategyClusters = mutableMapOf<CardTag, MutableList<Card>>()
        val tribeClusters = mutableMapOf<String, MutableList<Card>>()
        cards.forEach { card ->
            (card.tags + card.userTags)
                .filter { it.category == TagCategory.STRATEGY || it.category == TagCategory.ARCHETYPE }
                .distinct()
                .forEach { tag -> strategyClusters.getOrPut(tag) { mutableListOf() } += card }
            TribeDeriver.subtypeKeys(card).forEach { key -> tribeClusters.getOrPut(key) { mutableListOf() } += card }
        }

        val discoveries = mutableListOf<DeckDiscoveryV2>()

        strategyClusters.forEach { (tag, members) ->
            buildDiscovery(
                members = members,
                quantityByName = quantityByName,
                key = DiscoveryClusterKey.Strategy(tag),
                label = tag.displayLabel,
                minCopies = MIN_STRATEGY_CLUSTER_COPIES,
                strategyHint = SeedStrategy.forTag(tag),
                themeHint = null,
            )?.let { discoveries += it }
        }

        tribeClusters.forEach { (tribeKey, members) ->
            val tribeWord = tribeKey.removePrefix(TribeDeriver.TRIBE_PREFIX)
            if (tribeWord.isEmpty()) return@forEach
            val label = pluralizeTribeWord(tribeWord).replaceFirstChar { it.uppercase() }
            buildDiscovery(
                members = members,
                quantityByName = quantityByName,
                key = DiscoveryClusterKey.Tribe(tribeKey, label),
                label = label,
                minCopies = MIN_TRIBE_CLUSTER_COPIES,
                strategyHint = SeedStrategy.TRIBAL,
                themeHint = label,
            )?.let { discoveries += it }
        }

        discoveries
            .sortedWith(compareByDescending<DeckDiscoveryV2> { it.memberCount }.thenBy { it.label })
            .take(limit)
    }

    /**
     * @param minCopies the OWNED-COPY threshold (see class KDoc) evaluated AFTER color-coherence
     *   filtering — a cluster that only clears the threshold before off-color members are dropped
     *   is honestly not viable and must not surface.
     */
    private fun buildDiscovery(
        members: List<Card>,
        quantityByName: Map<String, Int>,
        key: DiscoveryClusterKey,
        label: String,
        minCopies: Int,
        strategyHint: SeedStrategy?,
        themeHint: String?,
    ): DeckDiscoveryV2? {
        val dominantColors = dominantColors(members)
        val coherent = members.filter { isColorCoherent(it, dominantColors) }
        val copies = coherent.sumOf { quantityByName[it.name] ?: 1 }
        if (copies < minCopies || coherent.isEmpty()) return null

        val mainboardSeed = coherent.map { card ->
            DeckEntry(
                card = card,
                quantity = (quantityByName[card.name] ?: 1).coerceAtMost(MAX_SEED_COPIES_PER_CARD),
                isOwned = true,
                isSideboard = false,
            )
        }
        val profile = deckScorer.profile(
            mainboard = mainboardSeed,
            // A neutral, generic format for RELATIVE ranking within the cluster only — the wizard
            // itself (Step 1) is where the user actually commits to a format.
            format = DeckFormat.CASUAL,
            colorIdentity = dominantColors,
            seedTags = if (key is DiscoveryClusterKey.Strategy) listOf(key.tag) else emptyList(),
        )
        val ranked = coherent
            .map { card -> card to deckScorer.fit(card, profile, isOwned = true).score }
            .sortedWith(compareByDescending<Pair<Card, Float>> { it.second }.thenBy { it.first.name })
            .map { it.first }
            .take(MEMBER_CAP)

        return DeckDiscoveryV2(
            key = key,
            label = label,
            memberCount = coherent.size,
            dominantColors = dominantColors,
            members = ranked,
            strategyHint = strategyHint,
            themeHint = themeHint,
        )
    }

    /** Top [MAX_DOMINANT_COLORS] colors by pip-presence across the cluster's members' color
     * identity. Empty when every member is colorless (nothing to filter on). */
    private fun dominantColors(members: List<Card>): Set<ManaColor> {
        val counts = mutableMapOf<ManaColor, Int>()
        members.forEach { card ->
            card.colorIdentity.forEach { symbol ->
                ManaColor.entries.firstOrNull { it.symbol == symbol }
                    ?.let { counts[it] = (counts[it] ?: 0) + 1 }
            }
        }
        if (counts.isEmpty()) return emptySet()
        return counts.entries
            .sortedByDescending { it.value }
            .take(MAX_DOMINANT_COLORS)
            .map { it.key }
            .toSet()
    }

    /** A colorless card always passes; a colored card must be a SUBSET of [dominantColors]
     * (mirrors [BuildDeckFromTemplateUseCase.analyzeCollection]'s Casual color-filter convention). */
    private fun isColorCoherent(card: Card, dominantColors: Set<ManaColor>): Boolean {
        if (dominantColors.isEmpty()) return true
        val cardColors = card.colorIdentity.mapNotNull { symbol -> ManaColor.entries.firstOrNull { it.symbol == symbol } }
        return cardColors.isEmpty() || cardColors.all { it in dominantColors }
    }

    /** Best-effort English pluralization -- duplicated per the existing [SuggestionCategoryResolver]/
     * [CollectionProfileUseCase] precedent (display-only, not worth a shared public API surface). */
    private fun pluralizeTribeWord(word: String): String = when {
        word.endsWith("y") && word.length > 1 && word[word.length - 2] !in "aeiou" ->
            word.dropLast(1) + "ies"
        word.endsWith("fe") -> word.dropLast(2) + "ves"
        word.endsWith("f") -> word.dropLast(1) + "ves"
        word.endsWith("s") || word.endsWith("x") || word.endsWith("z") ||
            word.endsWith("ch") || word.endsWith("sh") -> word + "es"
        else -> word + "s"
    }

    private companion object {
        const val DEFAULT_LIMIT = 8
        const val MEMBER_CAP = 12
        const val MAX_DOMINANT_COLORS = 3
        const val MIN_STRATEGY_CLUSTER_COPIES = 6
        /** Mirrors [DeckScorer]'s own PRIVATE `TRIBE_ABS_THRESHOLD` (8) -- that companion object is
         * `private`, so this is a deliberate, documented local duplicate of the same value rather
         * than a cross-module visibility change to a pre-existing engine constant (see class KDoc
         * "TRIBE_ABS_THRESHOLD alignment"). Keep in sync if the engine's threshold ever changes. */
        const val MIN_TRIBE_CLUSTER_COPIES = 8
        const val MAX_SEED_COPIES_PER_CARD = 4
    }
}
