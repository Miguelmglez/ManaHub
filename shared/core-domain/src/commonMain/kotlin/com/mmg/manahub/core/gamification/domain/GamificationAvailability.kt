package com.mmg.manahub.core.gamification.domain

import com.mmg.manahub.core.FeatureFlags
import com.mmg.manahub.core.domain.config.KillSwitch
import com.mmg.manahub.core.domain.config.RemoteConfig
import com.mmg.manahub.core.domain.config.RemoteConfigRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Effective state of the gamification gate, with the reason when it is closed.
 *
 * The [telemetryKey] values are stable snake_case strings for the `gamification_gate_state` custom key.
 */
enum class GamificationGateState(val telemetryKey: String) {
    /** Compile flag on, not killed, user has not opted out. */
    AVAILABLE("available"),

    /** [FeatureFlags.Gamification.ENABLED] is false (release gate). */
    COMPILE_DISABLED("compile_disabled"),

    /** Remote kill switch [KillSwitch.GAMIFICATION] is active. */
    KILLED("killed"),

    /** The user turned gamification off in Settings. */
    USER_OPTED_OUT("user_opted_out"),

    /** An input failed to read; the gate fails closed. */
    ERROR("error"),

    /** The local store belongs to a signed-out account or lacks verified guest provenance. */
    ACCOUNT_SCOPE_BLOCKED("account_scope_blocked"),
}

/**
 * The single gate every gamification surface (UI, engine, workers, sync) must consume.
 *
 * Combines the compile-time release gate, the remote kill switch and the user opt-out preference.
 */
interface GamificationAvailability {

    /** Current gate state; distinct-until-changed. */
    val gateState: Flow<GamificationGateState>

    /** `true` only when [gateState] is [GamificationGateState.AVAILABLE]; distinct-until-changed. */
    val availableFlow: Flow<Boolean>

    /** Whether the Settings opt-out switch may be shown: compile flag on and not killed. */
    val settingsVisibleFlow: Flow<Boolean>
}

/**
 * Default [GamificationAvailability].
 *
 * @param remoteConfigRepository source of the [KillSwitch.GAMIFICATION] state (fail-open on missing values).
 * @param userOptInFlow the user preference; `true` unless the user opted out.
 * @param compileEnabled the release gate; overridable only so tests can cover the enabled truth table.
 */
class DefaultGamificationAvailability(
    private val remoteConfigRepository: RemoteConfigRepository,
    private val userOptInFlow: Flow<Boolean>,
    private val accountScopeReadyFlow: Flow<Boolean> = flowOf(true),
    private val compileEnabled: Boolean = FeatureFlags.Gamification.ENABLED,
) : GamificationAvailability {

    // A null input means that source failed; the combine maps it to ERROR (fail closed).
    private val killedInput: Flow<Boolean?> =
        remoteConfigRepository.config
            .map<RemoteConfig, Boolean?> { it.isKilled(KillSwitch.GAMIFICATION) }
            .catch { e -> if (e is CancellationException) throw e else emit(null) }

    private val optInInput: Flow<Boolean?> =
        userOptInFlow
            .map<Boolean, Boolean?> { it }
            .catch { e -> if (e is CancellationException) throw e else emit(null) }

    override val gateState: Flow<GamificationGateState> =
        // With the compile flag off nothing else is read, so a hidden feature never touches DataStore.
        if (!compileEnabled) {
            flowOf(GamificationGateState.COMPILE_DISABLED)
        } else {
            combine(killedInput, optInInput, accountScopeReadyFlow) { killed, optedIn, accountReady ->
                when {
                    killed == null || optedIn == null -> GamificationGateState.ERROR
                    killed -> GamificationGateState.KILLED
                    !optedIn -> GamificationGateState.USER_OPTED_OUT
                    !accountReady -> GamificationGateState.ACCOUNT_SCOPE_BLOCKED
                    else -> GamificationGateState.AVAILABLE
                }
            }.distinctUntilChanged()
        }

    override val availableFlow: Flow<Boolean> =
        gateState.map { it == GamificationGateState.AVAILABLE }.distinctUntilChanged()

    override val settingsVisibleFlow: Flow<Boolean> =
        if (!compileEnabled) {
            flowOf(false)
        } else {
            killedInput.map { killed -> killed == false }.distinctUntilChanged()
        }
}
