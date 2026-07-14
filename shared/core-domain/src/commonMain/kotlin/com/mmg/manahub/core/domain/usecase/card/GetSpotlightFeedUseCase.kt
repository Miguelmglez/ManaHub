package com.mmg.manahub.core.domain.usecase.card

import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.model.MagicSet
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.random.Random

class GetSpotlightFeedUseCase(
    private val cardRepository: CardRepository
) {
    suspend operator fun invoke(setIndex: Int): DataResult<SpotlightFeedResult> {
        val setsResult = cardRepository.getPlayableSets()
        if (setsResult is DataResult.Error) {
            return DataResult.Error(setsResult.message)
        }
        
        val sets = (setsResult as DataResult.Success).data
        if (setIndex >= sets.size) {
            return DataResult.Error("No more sets available")
        }
        
        val set = sets[setIndex]
        val cards = mutableListOf<Card>()
        var page = 1
        var hasMore = true
        
        while (hasMore) {
            val pageResult = cardRepository.searchCardsPaginated("set:${set.code}", page)
            if (pageResult is DataResult.Success) {
                cards.addAll(pageResult.data.cards)
                hasMore = pageResult.data.hasMore
                page++
            } else {
                // If a page fails, we stop fetching and use what we have, or return error if we have nothing.
                if (cards.isEmpty()) {
                    val errorMessage = if (pageResult is DataResult.Error) pageResult.message else "Failed to fetch set cards"
                    return DataResult.Error(errorMessage)
                }
                break
            }
        }
        
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val seed = today.toEpochDays().toLong()
        val random = Random(seed)
        
        cards.shuffle(random)
        
        return DataResult.Success(SpotlightFeedResult(
            cards = cards,
            sourceSet = set,
            nextSetIndex = setIndex + 1
        ))
    }
}

data class SpotlightFeedResult(
    val cards: List<Card>,
    val sourceSet: MagicSet,
    val nextSetIndex: Int
)
