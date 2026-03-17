package com.mmg.magicfolder.code.core.domain.usecase.collection

import com.mmg.magicfolder.code.core.domain.repository.UserCardRepository
import javax.inject.Inject

class RemoveCardUseCase @Inject constructor(
    private val repository: UserCardRepository,
) {
    suspend operator fun invoke(userCardId: Long) =
        repository.deleteCard(userCardId)
}