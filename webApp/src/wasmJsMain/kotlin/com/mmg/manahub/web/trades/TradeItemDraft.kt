package com.mmg.manahub.web.trades

import com.mmg.manahub.core.data.remote.dto.TradeItemRequestDto

/**
 * A single line item being assembled in [CreateProposalScreen]/[CounterProposalScreen] before it
 * is submitted as a [TradeItemRequestDto]. Deliberately scoped-down vs. Android's
 * `TradeProposalViewModel.TradeItemDraft`: no review-collection placeholder support (the "gift
 * trade" warning dialog stays an explicit, flagged follow-up per `project_trades_hub_negotiation.md`)
 * and no distinct "pending vs. confirmed" two-phase add (a tap adds directly to the side list,
 * merging into an existing draft at the same variant tuple rather than requiring a separate
 * confirm step).
 *
 * [userCardIdRef] is set to the source [com.mmg.manahub.core.model.UserCard.id] when the item comes
 * from the CURRENT USER'S OWN collection (so the server can reference the exact collection row),
 * and left null when it comes from a friend's collection via
 * [com.mmg.manahub.core.domain.repository.FriendRepository.getFriendCollection] -- that RPC does not
 * expose the friend's own `user_card_collection` row ids, and `TradeItemRequestDto.userCardIdRef`
 * is documented as nullable for exactly this reason (Android's own `FriendCard`-sourced synthetic
 * offer/wishlist entries pass an empty string that collapses to null the same way).
 */
data class TradeItemDraft(
    val cardId: String,
    val cardName: String,
    val imageUrl: String?,
    val typeLine: String? = null,
    val setCode: String? = null,
    val setName: String? = null,
    val rarity: String? = null,
    val priceUsd: Double? = null,
    val priceEur: Double? = null,
    val quantity: Int,
    val isFoil: Boolean,
    val condition: String,
    val language: String,
    val userCardIdRef: String?,
    /** The most copies this draft item is allowed to reach (owned/available quantity). */
    val maxQuantity: Int,
)

/** Matches drafts by their variant identity (same key Android's merge logic uses). */
private fun TradeItemDraft.variantKey() = Quad(cardId, isFoil, condition, language)
private data class Quad(val a: String, val b: Boolean, val c: String, val d: String)

/**
 * Adds one copy of [addition] into [items], merging into an existing draft at the same variant
 * tuple (capped at the merged draft's [TradeItemDraft.maxQuantity]) instead of appending a
 * duplicate line.
 */
fun mergeTradeItemDraft(items: List<TradeItemDraft>, addition: TradeItemDraft): List<TradeItemDraft> {
    val existingIndex = items.indexOfFirst { it.variantKey() == addition.variantKey() && it.userCardIdRef == addition.userCardIdRef }
    if (existingIndex == -1) return items + addition
    val existing = items[existingIndex]
    val merged = existing.copy(quantity = (existing.quantity + addition.quantity).coerceAtMost(existing.maxQuantity.coerceAtLeast(1)))
    return items.toMutableList().apply { set(existingIndex, merged) }
}

fun TradeItemDraft.toRequestDto(fromUserId: String, toUserId: String) = TradeItemRequestDto(
    fromUserId = fromUserId,
    toUserId = toUserId,
    userCardIdRef = userCardIdRef?.takeIf { it.isNotBlank() },
    quantity = quantity,
    isFoil = isFoil,
    condition = condition,
    language = language,
    cardId = cardId,
    isReviewCollectionPlaceholder = false,
)
