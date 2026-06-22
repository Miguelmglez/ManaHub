package com.mmg.manahub.core.domain.usecase.collection

import com.mmg.manahub.core.domain.repository.UserCardRepository

/**
 * Soft-deletes a single collection entry by its id.
 *
 * Pure (KMP `commonMain`): depends only on the shared [UserCardRepository] interface. Hilt builds
 * it via `SharedDomainUseCaseModule` (the `@Inject` constructor was removed because `javax.inject`
 * is JVM-only).
 */
class RemoveCardUseCase(
    private val repository: UserCardRepository,
) {
    suspend operator fun invoke(userCardId: String) =
        repository.deleteCard(userCardId)
}
