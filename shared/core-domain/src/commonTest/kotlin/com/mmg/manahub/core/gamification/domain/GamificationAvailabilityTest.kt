package com.mmg.manahub.core.gamification.domain

import com.mmg.manahub.core.FeatureFlags
import com.mmg.manahub.core.domain.config.DefaultsOnlyRemoteConfigRepository
import com.mmg.manahub.core.domain.config.KillSwitch
import com.mmg.manahub.core.domain.config.RemoteConfig
import com.mmg.manahub.core.domain.config.RemoteConfigRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class GamificationAvailabilityTest {

    private val killed = RemoteConfig(killedFeatures = setOf(KillSwitch.GAMIFICATION))

    @Test
    fun guestCannotOpenAccountOwnedStoreAfterSignOut() = runTest {
        val accountReady = MutableStateFlow(true)
        val subject = DefaultGamificationAvailability(
            remoteConfigRepository = DefaultsOnlyRemoteConfigRepository(),
            userOptInFlow = flowOf(true),
            accountScopeReadyFlow = accountReady,
            compileEnabled = true,
        )

        accountReady.value = false
        assertEquals(GamificationGateState.ACCOUNT_SCOPE_BLOCKED, subject.gateState.first())
        assertFalse(subject.availableFlow.first())
    }

    private fun availability(
        compileEnabled: Boolean = true,
        config: RemoteConfig = RemoteConfig.DEFAULT,
        optIn: Flow<Boolean> = flowOf(true),
    ) = DefaultGamificationAvailability(
        remoteConfigRepository = DefaultsOnlyRemoteConfigRepository(config),
        userOptInFlow = optIn,
        compileEnabled = compileEnabled,
    )

    @Test
    fun releaseFlagIsOffWhileTheFeatureIsHidden() {
        assertFalse(FeatureFlags.Gamification.ENABLED)
    }

    @Test
    fun killSwitchUsesTheDocumentedRemoteKeyAndFailsOpen() {
        assertEquals("kill_gamification", KillSwitch.GAMIFICATION.remoteKey)
        assertFalse(KillSwitch.GAMIFICATION.killedByDefault)
    }

    @Test
    fun truthTable() = runTest {
        data class Row(val compile: Boolean, val killed: Boolean, val optIn: Boolean, val expected: GamificationGateState)
        val rows = listOf(
            Row(false, false, true, GamificationGateState.COMPILE_DISABLED),
            Row(false, true, false, GamificationGateState.COMPILE_DISABLED),
            Row(true, true, true, GamificationGateState.KILLED),
            Row(true, true, false, GamificationGateState.KILLED),
            Row(true, false, false, GamificationGateState.USER_OPTED_OUT),
            Row(true, false, true, GamificationGateState.AVAILABLE),
        )
        for (row in rows) {
            val subject = availability(
                compileEnabled = row.compile,
                config = if (row.killed) killed else RemoteConfig.DEFAULT,
                optIn = flowOf(row.optIn),
            )
            assertEquals(row.expected, subject.gateState.first(), "row $row")
            assertEquals(row.expected == GamificationGateState.AVAILABLE, subject.availableFlow.first(), "row $row")
        }
    }

    @Test
    fun compileDisabledNeverReadsThePreference() = runTest {
        var read = false
        val subject = availability(compileEnabled = false, optIn = flow { read = true; emit(true) })

        assertFalse(subject.availableFlow.first())
        assertFalse(subject.settingsVisibleFlow.first())
        assertFalse(read)
    }

    @Test
    fun preferenceErrorFailsClosed() = runTest {
        val subject = availability(optIn = flow { throw IllegalStateException("corrupt store") })

        assertEquals(GamificationGateState.ERROR, subject.gateState.first())
        assertFalse(subject.availableFlow.first())
    }

    @Test
    fun settingsVisibilityIgnoresTheUserPreference() = runTest {
        assertEquals(true, availability(optIn = flowOf(false)).settingsVisibleFlow.first())
        assertEquals(false, availability(config = killed).settingsVisibleFlow.first())
    }

    @Test
    fun reactsLiveToKillSwitchAndOptOutAndDedupes() = runTest {
        val remote = MutableRemoteConfig()
        val optIn = MutableStateFlow(true)
        val subject = DefaultGamificationAvailability(remote, optIn, compileEnabled = true)
        val seen = mutableListOf<Boolean>()
        val job = launch { subject.availableFlow.take(3).toList(seen) }
        runCurrent()

        optIn.value = false
        runCurrent()
        // Opted-out -> killed -> opted-in-but-killed stays unavailable, so no duplicate emission.
        remote.config.value = killed
        runCurrent()
        optIn.value = true
        runCurrent()
        remote.config.value = RemoteConfig.DEFAULT
        runCurrent()

        job.join()
        assertEquals(listOf(true, false, true), seen)
    }

    private class MutableRemoteConfig : RemoteConfigRepository {
        override val config = MutableStateFlow(RemoteConfig.DEFAULT)
        override suspend fun refresh() = Unit
    }
}
