package com.mmg.manahub.feature.friends.domain.usecase

import com.mmg.manahub.core.domain.repository.FriendRepository

class SearchUserByGameTagUseCase(private val repo: FriendRepository) {
    suspend operator fun invoke(gameTag: String) = repo.searchByGameTag(gameTag)
}
