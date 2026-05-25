package com.mmg.manahub.core.online.domain.usecase

import com.mmg.manahub.core.online.domain.repository.OnlineSessionRepository
import javax.inject.Inject

class UpdateCommanderDamageUseCase @Inject constructor(
    private val repository: OnlineSessionRepository,
) {
    suspend operator fun invoke(
        sessionId: String,
        targetSlot: Int,
        sourceSlot: Int,
        delta: Int,
    ): Result<Unit> = repository.updateCommanderDamage(sessionId, targetSlot, sourceSlot, delta)
}
