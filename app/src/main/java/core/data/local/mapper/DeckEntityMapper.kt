package core.data.local.mapper

import core.data.local.entity.DeckEntity
import core.data.local.dao.DeckWithCards as DeckWithCardsEntity
import core.domain.model.Deck
import core.domain.model.DeckSlot
import core.domain.model.DeckWithCards

fun DeckEntity.toDomainDeck(): Deck = Deck(
    id = id, name = name, description = description,
    format = format, coverCardId = coverCardId,
    createdAt = createdAt, updatedAt = updatedAt,
)

fun DeckWithCardsEntity.toDomainDeckWithCards(): DeckWithCards = DeckWithCards(
    deck      = deck.toDomainDeck(),
    mainboard = cards.filter { !it.isSideboard }.map { DeckSlot(it.scryfallId, it.quantity) },
    sideboard = cards.filter {  it.isSideboard }.map { DeckSlot(it.scryfallId, it.quantity) },
)

fun Deck.toEntity(): DeckEntity = DeckEntity(
    id = id, name = name, description = description,
    format = format, coverCardId = coverCardId,
    createdAt = createdAt, updatedAt = updatedAt,
)
