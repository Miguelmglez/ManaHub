package com.mmg.manahub.core.domain.update

import com.mmg.manahub.core.domain.config.AppUpdatePolicy
import com.mmg.manahub.core.domain.config.DefaultsOnlyRemoteConfigRepository
import com.mmg.manahub.core.domain.config.RemoteConfig
import com.mmg.manahub.core.domain.config.RemoteConfigKeys
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EvaluateAppUpdateRequirementUseCaseTest {

    private val evaluate = EvaluateAppUpdateRequirementUseCase()

    @Test
    fun defaultPolicy_isNone() {
        assertEquals(AppUpdateRequirement.None, evaluate(1L, AppUpdatePolicy()))
    }

    @Test
    fun defaultsOnlyRepository_yieldsNone() {
        val policy = DefaultsOnlyRemoteConfigRepository().config.value.appUpdatePolicy
        assertEquals(AppUpdateRequirement.None, evaluate(1L, policy))
    }

    @Test
    fun belowMinSupported_isForced() {
        assertEquals(AppUpdateRequirement.Forced, evaluate(4L, AppUpdatePolicy(minSupportedVersionCode = 5L)))
    }

    @Test
    fun equalToMinSupported_isNotForced() {
        assertEquals(AppUpdateRequirement.None, evaluate(5L, AppUpdatePolicy(minSupportedVersionCode = 5L)))
    }

    @Test
    fun aboveMinSupported_isNotForced() {
        assertEquals(AppUpdateRequirement.None, evaluate(6L, AppUpdatePolicy(minSupportedVersionCode = 5L)))
    }

    @Test
    fun belowLatest_isOptional() {
        assertEquals(AppUpdateRequirement.Optional, evaluate(9L, AppUpdatePolicy(latestVersionCode = 10L)))
    }

    @Test
    fun equalToLatest_isNone() {
        assertEquals(AppUpdateRequirement.None, evaluate(10L, AppUpdatePolicy(latestVersionCode = 10L)))
    }

    @Test
    fun aboveLatest_isNone() {
        assertEquals(AppUpdateRequirement.None, evaluate(11L, AppUpdatePolicy(latestVersionCode = 10L)))
    }

    @Test
    fun forcedWinsOverOptional() {
        val policy = AppUpdatePolicy(minSupportedVersionCode = 5L, latestVersionCode = 10L)
        assertEquals(AppUpdateRequirement.Forced, evaluate(4L, policy))
        assertEquals(AppUpdateRequirement.Optional, evaluate(5L, policy))
        assertEquals(AppUpdateRequirement.None, evaluate(10L, policy))
    }

    @Test
    fun remoteDefaults_areFailOpen() {
        val defaults = RemoteConfigKeys.defaults()
        assertEquals(0L, defaults[RemoteConfigKeys.MIN_SUPPORTED_VERSION_CODE])
        assertEquals(0L, defaults[RemoteConfigKeys.LATEST_VERSION_CODE])
        assertEquals("", defaults[RemoteConfigKeys.FORCE_UPDATE_MESSAGE])
        assertTrue(RemoteConfig.DEFAULT.killedFeatures.isEmpty())
    }
}
