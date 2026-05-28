package com.mmg.manahub.core.online.domain.usecase

import com.mmg.manahub.core.online.domain.model.ActiveSession
import com.mmg.manahub.core.online.domain.repository.OnlineSessionRepository
import javax.inject.Inject

class GetMyActiveSessionUseCase @Inject constructor(
    private val repository: OnlineSessionRepository,
) {
    suspend operator fun invoke(): Result<ActiveSession?> = repository.getMyActiveSession()
}
