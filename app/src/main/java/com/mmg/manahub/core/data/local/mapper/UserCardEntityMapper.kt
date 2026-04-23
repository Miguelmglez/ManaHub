package com.mmg.manahub.core.data.local.mapper

import com.mmg.manahub.core.data.local.entity.UserCardCollectionEntity
import com.mmg.manahub.core.domain.model.UserCard
import com.mmg.manahub.core.domain.model.UserCardWithCard
import com.mmg.manahub.core.data.local.dao.UserCardWithCard as UserCardWithCardEntity

fun UserCardCollectionEntity.toDomain(): UserCard = UserCard(
    id = id,
    scryfallId = scryfallId,
    quantity = quantity,
    isFoil = isFoil,
    isAlternativeArt = isAlternativeArt,
    condition = condition,
    language = language,
    isForTrade = isForTrade,
    isInWishlist = isInWishlist,
    updatedAt = updatedAt,
    createdAt = createdAt,
)

fun UserCardWithCardEntity.toDomain(): UserCardWithCard = UserCardWithCard(
    userCard = userCard.toDomain(),
    card = card.toDomainCard(),
)

fun UserCard.toEntity(userId: String?): UserCardCollectionEntity = UserCardCollectionEntity(
    id = id,
    userId = userId,
    scryfallId = scryfallId,
    quantity = quantity,
    isFoil = isFoil,
    isAlternativeArt = isAlternativeArt,
    condition = condition,
    language = language,
    isForTrade = isForTrade,
    isInWishlist = isInWishlist,
    isDeleted = false,
    updatedAt = updatedAt,
    createdAt = createdAt,
)
