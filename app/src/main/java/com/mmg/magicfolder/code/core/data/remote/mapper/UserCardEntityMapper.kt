package com.mmg.magicfolder.code.core.data.remote.mapper


import com.mmg.magicfolder.code.core.data.local.entity.UserCardEntity
import com.mmg.magicfolder.code.core.domain.model.UserCard
import com.mmg.magicfolder.code.core.domain.model.UserCardWithCard
import com.mmg.magicfolder.code.core.data.local.entity.UserCardWithCard as UserCardWithCardEntity

fun UserCardEntity.toDomain(): UserCard = UserCard(
    id = id, scryfallId = scryfallId, quantity = quantity, isFoil = isFoil,
    condition = condition, language = language, isForTrade = isForTrade,
    isInWishlist = isInWishlist, minTradeValue = minTradeValue,
    notes = notes, acquiredAt = acquiredAt, addedAt = addedAt,
)

fun UserCardWithCardEntity.toDomain(): UserCardWithCard = UserCardWithCard(
    userCard = userCard.toDomain(),
    card     = card.toDomain(),
)

fun UserCard.toEntity(): UserCardEntity = UserCardEntity(
    id = id, scryfallId = scryfallId, quantity = quantity, isFoil = isFoil,
    condition = condition, language = language, isForTrade = isForTrade,
    isInWishlist = isInWishlist, minTradeValue = minTradeValue,
    notes = notes, acquiredAt = acquiredAt, addedAt = addedAt,
)