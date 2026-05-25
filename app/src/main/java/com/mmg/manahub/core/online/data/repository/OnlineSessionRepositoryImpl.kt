package com.mmg.manahub.core.online.data.repository

import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.core.online.data.remote.OnlineSessionRemoteDataSource
import com.mmg.manahub.core.online.data.remote.SupabaseRealtimeClient
import com.mmg.manahub.core.online.domain.model.ActiveSession
import com.mmg.manahub.core.online.domain.model.SessionEvent
import com.mmg.manahub.core.online.domain.model.SessionSnapshot
import com.mmg.manahub.core.online.domain.repository.OnlineSessionRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OnlineSessionRepositoryImpl @Inject constructor(
    private val remoteDataSource: OnlineSessionRemoteDataSource,
    private val realtimeClient: SupabaseRealtimeClient,
) : OnlineSessionRepository {

    private val crashlytics = FirebaseCrashlytics.getInstance()

    override suspend fun getMyActiveSession(): Result<ActiveSession?> =
        remoteDataSource.getMyActiveSession()
            .map { dto -> dto?.let { ActiveSession(it.sessionId, it.code, it.status, it.gameMode, it.playerCount) } }
            .onFailure { throwable ->
                crashlytics.apply {
                    log("repo_get_my_active_session_failed: ${throwable.message}")
                    setCustomKey("online_session_error_type", throwable::class.simpleName ?: "Unknown")
                    recordException(throwable)
                }
            }

    override suspend fun getMyActiveSessions(): Result<List<ActiveSession>> =
        remoteDataSource.getMyActiveSessions()
            .map { list -> list.map { ActiveSession(it.sessionId, it.code, it.status, it.gameMode, it.playerCount) } }
            .onFailure { throwable ->
                crashlytics.apply {
                    log("repo_get_my_active_sessions_failed: ${throwable.message}")
                    setCustomKey("online_session_error_type", throwable::class.simpleName ?: "Unknown")
                    recordException(throwable)
                }
            }

    override suspend fun abandonMyActiveSession(sessionId: String): Result<Unit> =
        remoteDataSource.abandonMyActiveSession(sessionId)
            .onFailure { throwable ->
                crashlytics.apply {
                    log("repo_abandon_active_session_failed: ${throwable.message}")
                    setCustomKey("online_session_error_type", throwable::class.simpleName ?: "Unknown")
                    recordException(throwable)
                }
            }

    override suspend fun createSession(
        mode: String,
        playerCount: Int,
        layoutKey: String?,
        displayName: String,
        themeKey: String,
    ): Result<Pair<String, String>> =
        remoteDataSource.createSession(mode, playerCount, layoutKey, displayName, themeKey)
            .map { Pair(it.sessionId, it.code) }
            .onFailure { throwable ->
                crashlytics.apply {
                    log("repo_create_session_failed: mode=$mode player_count=$playerCount")
                    setCustomKey("online_session_game_mode", mode)
                    setCustomKey("online_session_player_count", playerCount)
                    setCustomKey("online_session_error_type", throwable::class.simpleName ?: "Unknown")
                    recordException(throwable)
                }
            }

    override suspend fun joinSession(
        code: String,
        displayName: String,
        themeKey: String,
    ): Result<Pair<String, Int>> =
        remoteDataSource.joinSession(code, displayName, themeKey)
            .map { Pair(it.sessionId, it.slotIndex) }
            .onFailure { throwable ->
                crashlytics.apply {
                    log("repo_join_session_failed: ${throwable.message}")
                    setCustomKey("online_session_error_type", throwable::class.simpleName ?: "Unknown")
                    recordException(throwable)
                }
            }

    override suspend fun getSnapshot(sessionId: String): Result<SessionSnapshot> =
        remoteDataSource.getSnapshot(sessionId).map { it.toDomain() }
            .onFailure { throwable ->
                crashlytics.apply {
                    log("repo_get_snapshot_failed: ${throwable.message}")
                    setCustomKey("online_session_error_type", throwable::class.simpleName ?: "Unknown")
                    recordException(throwable)
                }
            }

    override suspend fun startSession(sessionId: String): Result<Unit> =
        remoteDataSource.startSession(sessionId)
            .onFailure { throwable ->
                crashlytics.apply {
                    log("repo_start_session_failed: ${throwable.message}")
                    setCustomKey("online_session_error_type", throwable::class.simpleName ?: "Unknown")
                    recordException(throwable)
                }
            }

    override suspend fun leaveSession(sessionId: String): Result<Unit> =
        remoteDataSource.leaveSession(sessionId)
            .onFailure { throwable ->
                // Non-fatal: leave failures are silent to the user but indicate a Realtime/RPC problem
                crashlytics.apply {
                    log("repo_leave_session_failed: ${throwable.message}")
                    setCustomKey("online_session_error_type", throwable::class.simpleName ?: "Unknown")
                    recordException(throwable)
                }
            }

    override suspend fun updateLife(
        sessionId: String,
        slotIndex: Int,
        newLife: Int,
    ): Result<Unit> = remoteDataSource.updateLife(sessionId, slotIndex, newLife)
            .onFailure { throwable ->
                crashlytics.apply {
                    log("repo_update_life_failed: slot=$slotIndex new_life=$newLife")
                    setCustomKey("online_session_slot_index", slotIndex)
                    setCustomKey("online_session_error_type", throwable::class.simpleName ?: "Unknown")
                    recordException(throwable)
                }
            }

    override suspend fun updateCommanderDamage(
        sessionId: String,
        targetSlot: Int,
        sourceSlot: Int,
        delta: Int,
    ): Result<Unit> = remoteDataSource.updateCommanderDamage(sessionId, targetSlot, sourceSlot, delta)
            .onFailure { throwable ->
                crashlytics.apply {
                    log("repo_update_commander_damage_failed: target=$targetSlot source=$sourceSlot delta=$delta")
                    setCustomKey("online_session_error_type", throwable::class.simpleName ?: "Unknown")
                    recordException(throwable)
                }
            }

    override suspend fun updateCounter(
        sessionId: String,
        slotIndex: Int,
        counterType: String,
        delta: Int,
    ): Result<Unit> = remoteDataSource.updateCounter(sessionId, slotIndex, counterType, delta)
            .onFailure { throwable ->
                crashlytics.apply {
                    log("repo_update_counter_failed: slot=$slotIndex type=$counterType delta=$delta")
                    setCustomKey("online_session_slot_index", slotIndex)
                    setCustomKey("online_session_error_type", throwable::class.simpleName ?: "Unknown")
                    recordException(throwable)
                }
            }

    override suspend fun advancePhase(sessionId: String): Result<Unit> =
        remoteDataSource.advancePhase(sessionId)
            .onFailure { throwable ->
                crashlytics.apply {
                    log("repo_advance_phase_failed: ${throwable.message}")
                    setCustomKey("online_session_error_type", throwable::class.simpleName ?: "Unknown")
                    recordException(throwable)
                }
            }

    override suspend fun nextTurn(sessionId: String): Result<Unit> =
        remoteDataSource.nextTurn(sessionId)
            .onFailure { throwable ->
                crashlytics.apply {
                    log("repo_next_turn_failed: ${throwable.message}")
                    setCustomKey("online_session_error_type", throwable::class.simpleName ?: "Unknown")
                    recordException(throwable)
                }
            }

    override suspend fun confirmDefeat(sessionId: String, slotIndex: Int): Result<Unit> =
        remoteDataSource.confirmDefeat(sessionId, slotIndex)
            .onFailure { throwable ->
                crashlytics.apply {
                    log("repo_confirm_defeat_failed: slot=$slotIndex ${throwable.message}")
                    setCustomKey("online_session_slot_index", slotIndex)
                    setCustomKey("online_session_error_type", throwable::class.simpleName ?: "Unknown")
                    recordException(throwable)
                }
            }

    override suspend fun revokeDefeat(sessionId: String, slotIndex: Int): Result<Unit> =
        remoteDataSource.revokeDefeat(sessionId, slotIndex)
            .onFailure { throwable ->
                crashlytics.apply {
                    log("repo_revoke_defeat_failed: slot=$slotIndex ${throwable.message}")
                    setCustomKey("online_session_slot_index", slotIndex)
                    setCustomKey("online_session_error_type", throwable::class.simpleName ?: "Unknown")
                    recordException(throwable)
                }
            }

    override suspend fun setReady(sessionId: String, isReady: Boolean): Result<Unit> =
        remoteDataSource.setReady(sessionId, isReady)
            .onFailure { throwable ->
                crashlytics.apply {
                    log("repo_set_ready_failed: is_ready=$isReady ${throwable.message}")
                    setCustomKey("online_session_error_type", throwable::class.simpleName ?: "Unknown")
                    recordException(throwable)
                }
            }

    override fun observeSession(sessionId: String): Flow<SessionEvent> =
        realtimeClient.observeSession(sessionId)

    override suspend fun connectRealtime(sessionId: String) =
        realtimeClient.connect(sessionId)

    override suspend fun disconnectRealtime(sessionId: String) =
        realtimeClient.disconnect(sessionId)

    override suspend fun broadcastLifeDelta(sessionId: String, slotIndex: Int, newLife: Int) =
        realtimeClient.broadcastLifeDelta(sessionId, slotIndex, newLife)

    override suspend fun broadcastPhaseChange(sessionId: String, newPhase: String) =
        realtimeClient.broadcastPhaseChange(sessionId, newPhase)
}
