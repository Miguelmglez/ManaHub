package core.domain.usecase.collection

import core.domain.repository.UserCardRepository
import javax.inject.Inject

class RemoveCardUseCase @Inject constructor(
    private val repository: UserCardRepository,
) {
    suspend operator fun invoke(userCardId: Long) =
        repository.deleteCard(userCardId)
}