package com.mmg.manahub.core.domain.usecase.collection

import com.mmg.manahub.core.domain.model.UserCardWithCard
import com.mmg.manahub.core.domain.repository.UserCardRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetCollectionUseCase @Inject constructor(
    private val repository: UserCardRepository,
) {
    operator fun invoke(): Flow<List<UserCardWithCard>> =
        repository.observeCollection()
}