package com.mmg.manahub.core.online.domain.usecase

import com.mmg.manahub.core.online.domain.repository.OnlineSessionRepository
import javax.inject.Inject

class JoinSessionUseCase @Inject constructor(
    private val repository: OnlineSessionRepository,
) {
    suspend operator fun invoke(
        code: String,
        displayName: String,
        themeKey: String,
    ): Result<Pair<String, Int>> = repository.joinSession(code, displayName, themeKey)
}
