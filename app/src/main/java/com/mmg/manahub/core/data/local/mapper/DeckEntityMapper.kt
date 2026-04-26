package com.mmg.manahub.core.data.local.mapper

import com.mmg.manahub.core.data.local.entity.DeckEntity
import com.mmg.manahub.core.data.local.dao.DeckWithCards as DeckWithCardsEntity
import com.mmg.manahub.core.domain.model.Deck
import com.mmg.manahub.core.domain.model.DeckSlot
import com.mmg.manahub.core.domain.model.DeckWithCards

fun DeckEntity.toDomainDeck(): Deck = Deck(
    id = id,
    userId = userId,
    name = name,
    description = description,
    format = format,
    coverCardId = coverCardId,
    isDeleted = isDeleted,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun DeckWithCardsEntity.toDomainDeckWithCards(): DeckWithCards = DeckWithCards(
    deck = deck.toDomainDeck(),
    mainboard = cards.filter { !it.isSideboard }.map { DeckSlot(it.scryfallId, it.quantity) },
    sideboard = cards.filter { it.isSideboard }.map { DeckSlot(it.scryfallId, it.quantity) },
)

fun Deck.toEntity(): DeckEntity = DeckEntity(
    id = id,
    userId = userId,
    name = name,
    description = description,
    format = format,
    coverCardId = coverCardId,
    isDeleted = isDeleted,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
