package com.mmg.manahub.core.data.local.mapper

import com.mmg.manahub.core.data.local.entity.UserCardEntity
import com.mmg.manahub.core.domain.model.UserCard
import com.mmg.manahub.core.domain.model.UserCardWithCard
import com.mmg.manahub.core.data.local.dao.UserCardWithCard as UserCardWithCardEntity

fun UserCardEntity.toDomain(): UserCard = UserCard(
    id = id, scryfallId = scryfallId, quantity = quantity, isFoil = isFoil,
    isAlternativeArt = isAlternativeArt,
    condition = condition, language = language, isForTrade = isForTrade,
    isInWishlist = isInWishlist, minTradeValue = minTradeValue,
    notes = notes, acquiredAt = acquiredAt, addedAt = addedAt,
    syncStatus = syncStatus, remoteId = remoteId,
)

fun UserCardWithCardEntity.toDomain(): UserCardWithCard = UserCardWithCard(
    userCard = userCard.toDomain(),
    card     = card.toDomainCard(),
)

fun UserCard.toEntity(): UserCardEntity = UserCardEntity(
    id = id, scryfallId = scryfallId, quantity = quantity, isFoil = isFoil,
    isAlternativeArt = isAlternativeArt,
    condition = condition, language = language, isForTrade = isForTrade,
    isInWishlist = isInWishlist, minTradeValue = minTradeValue,
    notes = notes, acquiredAt = acquiredAt, addedAt = addedAt,
    syncStatus = syncStatus, remoteId = remoteId,
)
