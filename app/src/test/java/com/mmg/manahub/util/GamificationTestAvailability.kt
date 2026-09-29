package com.mmg.manahub.util

import com.mmg.manahub.core.domain.config.DefaultsOnlyRemoteConfigRepository
import com.mmg.manahub.core.gamification.domain.DefaultGamificationAvailability
import com.mmg.manahub.core.gamification.domain.GamificationAvailability
import kotlinx.coroutines.flow.Flow

/** A real [GamificationAvailability] with the release gate forced on, driven only by [userOptIn]. */
fun testGamificationAvailability(userOptIn: Flow<Boolean>): GamificationAvailability =
    DefaultGamificationAvailability(
        remoteConfigRepository = DefaultsOnlyRemoteConfigRepository(),
        userOptInFlow = userOptIn,
        compileEnabled = true,
    )
