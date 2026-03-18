package core.data.remote.mapper

import core.data.local.entity.UserCardEntity
import core.domain.model.UserCard
import core.domain.model.UserCardWithCard
import core.data.local.dao.UserCardWithCard as UserCardWithCardEntity
import core.data.local.mapper.toDomainCard as cardToDomain

fun UserCardEntity.toDomain(): UserCard = UserCard(
    id = id, scryfallId = scryfallId, quantity = quantity, isFoil = isFoil,
    condition = condition, language = language, isForTrade = isForTrade,
    isInWishlist = isInWishlist, minTradeValue = minTradeValue,
    notes = notes, acquiredAt = acquiredAt, addedAt = addedAt,
)

fun UserCardWithCardEntity.toDomain(): UserCardWithCard = UserCardWithCard(
    userCard = userCard.toDomain(),
    card     = card.cardToDomain(),
)

fun UserCard.toEntity(): UserCardEntity = UserCardEntity(
    id = id, scryfallId = scryfallId, quantity = quantity, isFoil = isFoil,
    condition = condition, language = language, isForTrade = isForTrade,
    isInWishlist = isInWishlist, minTradeValue = minTradeValue,
    notes = notes, acquiredAt = acquiredAt, addedAt = addedAt,
)
