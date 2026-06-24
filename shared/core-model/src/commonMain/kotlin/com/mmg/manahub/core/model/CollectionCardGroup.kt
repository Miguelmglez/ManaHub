package com.mmg.manahub.core.model

/**
 * A card as it appears in the collection list: all copies of the same
 * scryfallId collapsed into a single entry with aggregated data.
 */
data class CollectionCardGroup(
    val card: Card,
    val totalQuantity: Int,
    val hasFoil: Boolean,
    val distinctCopies: Int,
    val latestAddedAt: Long,
)

/**
 * Groups a list of [UserCardWithCard] entries by their scryfallId, producing
 * a collapsed [CollectionCardGroup] for each unique card.
 */
fun List<UserCardWithCard>.groupByCard(): List<CollectionCardGroup> =
    groupBy { it.card.scryfallId }
        .map { (_, copies) ->
            CollectionCardGroup(
                card = copies.first().card,
                totalQuantity = copies.sumOf { it.userCard.quantity },
                hasFoil = copies.any { it.userCard.isFoil },
                distinctCopies = copies.size,
                latestAddedAt = copies.maxOf { it.userCard.createdAt },
            )
        }
