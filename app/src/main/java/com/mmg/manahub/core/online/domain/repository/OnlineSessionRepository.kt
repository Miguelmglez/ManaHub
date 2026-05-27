package com.mmg.manahub.core.online.domain.repository

import com.mmg.manahub.core.online.domain.model.ActiveSession
import com.mmg.manahub.core.online.domain.model.SessionEvent
import com.mmg.manahub.core.online.domain.model.SessionSnapshot
import kotlinx.coroutines.flow.Flow

interface OnlineSessionRepository {
    suspend fun getMyActiveSession(): Result<ActiveSession?>
    suspend fun getMyActiveSessions(): Result<List<ActiveSession>>
    suspend fun abandonMyActiveSession(sessionId: String): Result<Unit>
    suspend fun createSession(mode: String, playerCount: Int, layoutKey: String?, displayName: String, themeKey: String): Result<Pair<String, String>>
    suspend fun joinSession(code: String, displayName: String, themeKey: String): Result<Pair<String, Int>>
    suspend fun getSnapshot(sessionId: String): Result<SessionSnapshot>
    suspend fun startSession(sessionId: String): Result<Unit>
    suspend fun leaveSession(sessionId: String): Result<Unit>
    suspend fun updateLife(sessionId: String, slotIndex: Int, newLife: Int): Result<Unit>
    suspend fun updateCommanderDamage(sessionId: String, targetSlot: Int, sourceSlot: Int, delta: Int): Result<Unit>
    suspend fun updateCounter(sessionId: String, slotIndex: Int, counterType: String, delta: Int): Result<Unit>
    suspend fun advancePhase(sessionId: String): Result<Unit>
    suspend fun nextTurn(sessionId: String): Result<Unit>
    suspend fun confirmDefeat(sessionId: String, slotIndex: Int): Result<Unit>
    suspend fun revokeDefeat(sessionId: String, slotIndex: Int): Result<Unit>
    suspend fun setReady(sessionId: String, isReady: Boolean): Result<Unit>
    fun observeSession(sessionId: String): Flow<SessionEvent>
    suspend fun connectRealtime(sessionId: String)
    suspend fun disconnectRealtime(sessionId: String)
    suspend fun broadcastLifeDelta(sessionId: String, slotIndex: Int, newLife: Int)
    suspend fun broadcastPhaseChange(sessionId: String, newPhase: String, activePlayerSlot: Int, turnNumber: Int)
    suspend fun broadcastCounterUpdate(sessionId: String, slotIndex: Int, counterType: String, newValue: Int)
    suspend fun broadcastCommanderDamage(sessionId: String, targetSlot: Int, sourceSlot: Int, newDamage: Int)
}
