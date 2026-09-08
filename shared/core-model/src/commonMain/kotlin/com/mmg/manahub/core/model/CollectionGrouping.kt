package com.mmg.manahub.core.model

/**
 * How the Collection "Cards" tab groups its (already card-versions-collapsed)
 * [CollectionCardGroup] rows into sections.
 *
 * Deliberately SEPARATE from [GroupingMode] (the Deck Builder's grouping enum): the two screens
 * group different data shapes (deck slots vs. collection groups) and have different value sets
 * (e.g. [SET]/[RARITY] make sense for a collection, not a 100-card deck). [GroupingFlowSelector]
 * in `shared/core-ui` iterates `GroupingMode.entries` for the deck UI — adding collection-only
 * values there would leak into that unrelated screen.
 */
enum class CollectionGroupingMode {
    NONE, TYPE, COLOR, CMC, SET, RARITY,
    ;

    companion object {
        /** Resolves a persisted name back to the enum, defaulting to [NONE] for null/unknown values. */
        fun fromName(name: String?) = entries.find { it.name == name } ?: NONE
    }
}

/**
 * One section of grouped collection rows.
 *
 * @param labelToken a raw, UNLOCALIZED token identifying the section — e.g. `"Creatures"`,
 *   `"W"`, `"7+"`, a set code, a raw rarity string, or a [CardTag.key]. The presentation layer
 *   resolves this to a localized display string (mirrors how [CollectionCardGroup.groupKey] and
 *   the Deck Builder's `groupCards` tokens work — `commonMain` never renders text directly).
 * @param items the [CollectionCardGroup] rows in this section, in the SAME relative order they
 *   arrived in (grouping never re-sorts within a section — the caller is expected to have
 *   already sorted the full list before calling [groupCollection]).
 */
data class CollectionSection(
    val labelToken: String,
    val items: List<CollectionCardGroup>,
) {
    /** Sum of [CollectionCardGroup.totalQuantity] across every item in this section. */
    val totalCopies: Int
        get() = items.sumOf { it.totalQuantity }

    /**
     * Sum of `priceEur * totalQuantity` across items that have a known [Card.priceEur].
     * Items with an unknown price are excluded from the sum rather than treated as zero;
     * `null` only when EVERY item in the section lacks a price, so a mixed section still
     * reports a (partial) total instead of silently collapsing to null.
     */
    val totalValueEur: Double?
        get() {
            val known = items.filter { it.card.priceEur != null }
            if (known.isEmpty()) return null
            return known.sumOf { (it.card.priceEur ?: 0.0) * it.totalQuantity }
        }
}

/** Fixed section-order for [CollectionGroupingMode.TYPE] — mirrors the Deck Builder's `groupCards` precedent. */
private val TYPE_ORDER = listOf(
    "Creatures", "Instants", "Sorceries", "Enchantments", "Artifacts", "Planeswalkers", "Lands", "Other",
)

/** Fixed section-order for [CollectionGroupingMode.COLOR]. No "Other" bucket — every [Card] here is resolved. */
private val COLOR_ORDER = listOf("W", "U", "B", "R", "G", "Multicolor", "Colorless", "Land")

/**
 * Duplicates [com.mmg.manahub.feature.collection.presentation.CollectionViewModel]'s private
 * `rarityWeight` ordering table (`:app` cannot be depended on FROM `core-model`, so this can't be
 * shared directly) — mythic > rare > uncommon > everything else. Keep the two in sync by hand if
 * either changes; they intentionally agree so "sort by rarity" and "group by rarity" never disagree
 * on what counts as rarer.
 */
private fun rarityWeight(rarity: String) = when (rarity.lowercase()) {
    "mythic"   -> 4
    "rare"     -> 3
    "uncommon" -> 2
    else       -> 1
}

/**
 * Groups [groups] (already sorted by the caller) into [CollectionSection]s per [mode].
 *
 * Pure, commonMain-only, and stateless — safe to call from a ViewModel or from a unit test with
 * no platform dependency. Every mode preserves the RELATIVE ORDER of items as they arrived; only
 * bucket membership is computed here, never re-sorting.
 * An empty [groups] input always returns an empty list, regardless of [mode].
 */
fun groupCollection(
    groups: List<CollectionCardGroup>,
    mode: CollectionGroupingMode,
): List<CollectionSection> {
    if (groups.isEmpty()) return emptyList()

    return when (mode) {
        CollectionGroupingMode.NONE -> listOf(CollectionSection(labelToken = "", items = groups))

        CollectionGroupingMode.TYPE -> {
            val buckets = groups.groupBy { group -> typeBucket(group.card.typeLine) }
            TYPE_ORDER.mapNotNull { token ->
                val items = buckets[token] ?: return@mapNotNull null
                if (items.isEmpty()) null else CollectionSection(token, items)
            }
        }

        CollectionGroupingMode.COLOR -> {
            val buckets = groups.groupBy { group -> colorBucket(group.card) }
            COLOR_ORDER.mapNotNull { token ->
                val items = buckets[token] ?: return@mapNotNull null
                if (items.isEmpty()) null else CollectionSection(token, items)
            }
        }

        CollectionGroupingMode.CMC -> {
            val buckets = groups.groupBy { group -> cmcBucket(group.card) }
            val nonLandOrder = listOf("0", "1", "2", "3", "4", "5", "6", "7+")
            val nonLandSections = nonLandOrder.mapNotNull { token ->
                val items = buckets[token] ?: return@mapNotNull null
                if (items.isEmpty()) null else CollectionSection(token, items)
            }
            val landItems = buckets["Lands"]
            nonLandSections + (if (landItems.isNullOrEmpty()) emptyList() else listOf(CollectionSection("Lands", landItems)))
        }

        CollectionGroupingMode.SET -> {
            groups.groupBy { it.card.setCode }
                .map { (setCode, items) ->
                    val setName = items.first().card.setName.ifBlank { setCode }
                    Triple(setCode, setName, items)
                }
                .sortedBy { it.second.lowercase() }
                .map { (setCode, _, items) -> CollectionSection(setCode, items) }
        }

        CollectionGroupingMode.RARITY -> {
            groups.groupBy { it.card.rarity }
                .map { (rarity, items) -> CollectionSection(rarity, items) }
                .sortedWith(
                    compareByDescending<CollectionSection> { rarityWeight(it.labelToken) }
                        .thenBy { if (it.labelToken.lowercase() == "common") 1 else 0 }
                )
        }


    }
}

private fun typeBucket(typeLine: String): String = when {
    typeLine.contains("Creature")     -> "Creatures"
    typeLine.contains("Instant")      -> "Instants"
    typeLine.contains("Sorcery")      -> "Sorceries"
    typeLine.contains("Enchantment")  -> "Enchantments"
    typeLine.contains("Artifact")     -> "Artifacts"
    typeLine.contains("Planeswalker") -> "Planeswalkers"
    typeLine.contains("Land")         -> "Lands"
    else                              -> "Other"
}

private fun colorBucket(card: Card): String {
    if (card.typeLine.contains("Land")) return "Land"
    return when (card.colors.size) {
        0 -> "Colorless"
        1 -> card.colors.first()
        else -> "Multicolor"
    }
}

private fun cmcBucket(card: Card): String {
    if (card.typeLine.contains("Land")) return "Lands"
    val cmcInt = card.cmc.toInt().coerceAtLeast(0)
    return if (cmcInt >= 7) "7+" else cmcInt.toString()
}
