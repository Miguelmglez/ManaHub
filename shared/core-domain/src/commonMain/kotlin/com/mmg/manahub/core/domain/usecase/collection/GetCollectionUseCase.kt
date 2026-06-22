package com.mmg.manahub.core.domain.usecase.collection

import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.core.model.UserCardWithCard
import kotlinx.coroutines.flow.Flow

/**
 * Observes the user's full (non-deleted) collection as a stream of resolved cards.
 *
 * Pure (KMP `commonMain`): depends only on the shared [UserCardRepository] interface and the
 * core-model [UserCardWithCard]. Hilt builds it via `SharedDomainUseCaseModule` (the `@Inject`
 * constructor was removed because `javax.inject` is JVM-only).
 */
class GetCollectionUseCase(
    private val repository: UserCardRepository,
) {
    operator fun invoke(): Flow<List<UserCardWithCard>> =
        repository.observeCollection()
}
