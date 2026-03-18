package com.mmg.magicfolder.core.data.remote.mapper

import com.mmg.magicfolder.core.data.local.entity.DeckEntity
import com.mmg.magicfolder.core.data.local.dao.DeckWithCards as DeckWithCardsEntity
import com.mmg.magicfolder.core.domain.model.Deck
import com.mmg.magicfolder.core.domain.model.DeckSlot
import com.mmg.magicfolder.core.domain.model.DeckWithCards

fun DeckEntity.toDomain(): Deck = Deck(
    id = id, name = name, description = description,
    format = format, coverCardId = coverCardId,
    createdAt = createdAt, updatedAt = updatedAt,
)

fun DeckWithCardsEntity.toDomain(): DeckWithCards = DeckWithCards(
    deck      = deck.toDomain(),
    mainboard = cards.filter { !it.isSideboard }.map { DeckSlot(it.scryfallId, it.quantity) },
    sideboard = cards.filter {  it.isSideboard }.map { DeckSlot(it.scryfallId, it.quantity) },
)

fun Deck.toEntity(): DeckEntity = DeckEntity(
    id = id, name = name, description = description,
    format = format, coverCardId = coverCardId,
    createdAt = createdAt, updatedAt = updatedAt,
)