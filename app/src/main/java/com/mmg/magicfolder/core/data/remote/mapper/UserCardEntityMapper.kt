package com.mmg.magicfolder.core.data.remote.mapper

import com.mmg.magicfolder.core.data.local.entity.UserCardEntity
import com.mmg.magicfolder.core.domain.model.UserCard
import com.mmg.magicfolder.core.domain.model.UserCardWithCard
import com.mmg.magicfolder.core.data.local.dao.UserCardWithCard as UserCardWithCardEntity
import com.mmg.magicfolder.core.data.local.mapper.toDomainCard as cardToDomain

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
