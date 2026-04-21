package com.mmg.manahub.feature.friends.domain.usecase

import com.mmg.manahub.feature.friends.domain.repository.FriendRepository
import javax.inject.Inject

class SearchUserByGameTagUseCase @Inject constructor(private val repo: FriendRepository) {
    suspend operator fun invoke(gameTag: String) = repo.searchByGameTag(gameTag)
}
