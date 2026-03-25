package com.mmg.magicfolder.core.domain.usecase.card

import com.mmg.magicfolder.core.domain.model.Card
import com.mmg.magicfolder.core.domain.model.CardTag
import com.mmg.magicfolder.core.domain.model.computeAutoTags
import javax.inject.Inject

/**
 * Derives a set of [CardTag]s from a card's oracle text and type line.
 *
 * Pure logic — no I/O, no coroutine needed.
 * Inject wherever you need to tag a card; or call [Card.computeAutoTags] directly.
 */
class AutoTagCardUseCase @Inject constructor() {
    operator fun invoke(card: Card): List<CardTag> = card.computeAutoTags()
}
