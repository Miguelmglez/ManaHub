package core.domain.usecase.collection

import core.domain.repository.UserCardRepository
import core.domain.model.UserCardWithCard
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetCollectionUseCase @Inject constructor(
    private val repository: UserCardRepository,
) {
    operator fun invoke(): Flow<List<UserCardWithCard>> =
        repository.observeCollection()
}