package com.mmg.manahub.core.online.domain.usecase

import com.mmg.manahub.core.online.domain.repository.OnlineSessionRepository
import javax.inject.Inject

class CreateSessionUseCase @Inject constructor(
    private val repository: OnlineSessionRepository,
) {
    suspend operator fun invoke(
        mode: String,
        playerCount: Int,
        layoutKey: String? = null,
        displayName: String = "",
        themeKey: String = "Crimson",
    ): Result<Pair<String, String>> = repository.createSession(mode, playerCount, layoutKey, displayName, themeKey)
}
