package com.mmg.manahub.core.model

/**
 * A card as it appears in the collection list: every copy the user owns of a given
 * card identity (Card Versions & Languages, Phase 1C) WITHIN THE SAME SET, collapsed
 * into a single entry with aggregated data. Different printings inside that set
 * (languages, showcase/borderless variants — distinct [Card.scryfallId]s) collapse
 * into one item; the same card identity in a DIFFERENT set produces a separate group.
 */
data class CollectionCardGroup(
    val card: Card,
    val totalQuantity: Int,
    val hasFoil: Boolean,
    val distinctCopies: Int,
    val latestAddedAt: Long,
    /**
     * Stable identifier for this group — `"<setCode>|<identity>"` — suitable as a
     * Lazy list/grid key. Stable across recompositions as long as the underlying
     * identity+set pairing doesn't change (unlike [Card.scryfallId], which now maps
     * many-to-one into a group). `setCode` is sanitised to `[a-z0-9]{2,6}` upstream (Scryfall
     * set-code shape) and so can never itself contain the `|` delimiter — leading with it
     * (edge-case audit A7, 2026-07-15) keeps the key unambiguous even for a card `identity`
     * (an oracle name) that happens to contain a literal `|` character, which the previous
     * `"<identity>|<setCode>"` ordering did not guarantee.
     */
    val groupKey: String,
)

/**
 * Groups a list of [UserCardWithCard] entries by (card identity, set), producing one
 * [CollectionCardGroup] per set the user owns copies in.
 *
 * Card identity is [Card.oracleId] when present, falling back to [Card.name] (always the
 * English oracle name, see the [Card.oracleId] KDoc) for rows cached before oracleId was
 * introduced. Different printings of the identity within the SAME set (languages, showcase
 * variants — distinct [Card.scryfallId]s) collapse into one entry; the same identity in a
 * different set produces a separate group.
 *
 * The group's representative [CollectionCardGroup.card] is the entry the user added FIRST to
 * that set (by [UserCard.createdAt] ascending — edge-case audit A6, 2026-07-15). A "most
 * recently added" representative churned the group's displayed image/price/tap-navigation target
 * on every additional copy added (including a same-tuple quantity bump, which still mints a new
 * row via [com.mmg.manahub.core.domain.repository.UserCardRepository.addOrIncrement] in some
 * paths) — first-added is stable for the group's whole lifetime. [latestAddedAt] (used for
 * recency SORT ORDER of the groups themselves, not the representative) still tracks the most
 * recent addition.
 */
fun List<UserCardWithCard>.groupByCard(): List<CollectionCardGroup> =
    groupBy { entry ->
        val identity = entry.card.oracleId.ifBlank { entry.card.name }
        identity to entry.card.setCode
    }.map { (key, copies) ->
        val (identity, setCode) = key
        val representative = copies.minBy { it.userCard.createdAt }
        CollectionCardGroup(
            card = representative.card,
            totalQuantity = copies.sumOf { it.userCard.quantity },
            hasFoil = copies.any { it.userCard.isFoil },
            distinctCopies = copies.distinctBy {
                Quadruple(it.card.scryfallId, it.userCard.isFoil, it.userCard.condition, it.userCard.language)
            }.size,
            latestAddedAt = copies.maxOf { it.userCard.createdAt },
            groupKey = "$setCode|$identity",
        )
    }

/** Local 4-tuple — kotlin.Quadruple does not exist in the stdlib. */
private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
