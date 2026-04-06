package com.mmg.magicfolder.feature.collection

import com.mmg.magicfolder.core.domain.model.Card
import com.mmg.magicfolder.core.domain.model.UserCardWithCard

/**
 * A card as it appears in the collection list: all copies of the same
 * scryfallId collapsed into a single entry with aggregated data.
 */
data class CollectionCardGroup(
    val card:           Card,
    val totalQuantity:  Int,
    val hasFoil:        Boolean,
    val distinctCopies: Int,   // number of distinct UserCard rows
    val latestAddedAt:  Long,  // newest addedAt across all copies — for DATE_ADDED sort
)

fun List<UserCardWithCard>.groupByCard(): List<CollectionCardGroup> =
    groupBy { it.card.scryfallId }
        .map { (_, copies) ->
            CollectionCardGroup(
                card           = copies.first().card,
                totalQuantity  = copies.sumOf { it.userCard.quantity },
                hasFoil        = copies.any  { it.userCard.isFoil },
                distinctCopies = copies.size,
                latestAddedAt  = copies.maxOf { it.userCard.addedAt },
            )
        }
