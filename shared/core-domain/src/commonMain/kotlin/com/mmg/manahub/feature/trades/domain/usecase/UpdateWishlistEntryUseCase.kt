package com.mmg.manahub.feature.trades.domain.usecase

import com.mmg.manahub.core.domain.auth.AuthRepository
import com.mmg.manahub.core.domain.auth.SessionState
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.UpdateEntryOutcome
import com.mmg.manahub.core.domain.repository.WishlistRepository
import com.mmg.manahub.core.model.DataResult

/**
 * Card Versions & Languages, Phase 1A. Edits an existing wishlist entry's
 * card/printing/foil/condition/language/quantity in place.
 *
 * Mirrors [AddToWishlistUseCase]'s shape: derives the current user id from [authRepo] rather than
 * requiring the caller to pass one (the authenticated userId is only needed for the remote-first
 * push of an already-synced row whose attributes changed — see
 * [WishlistRepository.updateEntryWithMerge]'s KDoc). Also ensures the target
 * [com.mmg.manahub.core.model.Card] is cached locally so the language selector's chosen printing
 * is resolvable before the write, mirroring [com.mmg.manahub.core.domain.usecase.collection.UpdateCollectionEntryUseCase].
 */
class UpdateWishlistEntryUseCase(
    private val repo: WishlistRepository,
    private val cardRepository: CardRepository,
    private val authRepo: AuthRepository,
) {
    suspend operator fun invoke(
        entryId: String,
        newCardId: String,
        isFoil: Boolean?,
        condition: String?,
        language: String?,
        quantity: Int,
    ): Result<UpdateEntryOutcome> {
        val cardResult = cardRepository.getCardById(newCardId)
        if (cardResult is DataResult.Error) return Result.failure(IllegalStateException(cardResult.message))

        val userId = (authRepo.sessionState.value as? SessionState.Authenticated)?.user?.id
        return repo.updateEntryWithMerge(
            entryId = entryId,
            newCardId = newCardId,
            isFoil = isFoil,
            condition = condition,
            language = language,
            quantity = quantity,
            userId = userId,
        )
    }
}
