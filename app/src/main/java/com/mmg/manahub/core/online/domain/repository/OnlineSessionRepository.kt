package com.mmg.manahub.core.online.domain.repository

import com.mmg.manahub.core.online.domain.model.ActiveSession
import com.mmg.manahub.core.online.domain.model.CreateSessionResult
import com.mmg.manahub.core.online.domain.model.JoinSessionResult
import com.mmg.manahub.core.online.domain.model.SessionEvent
import com.mmg.manahub.core.online.domain.model.SessionSnapshot
import kotlinx.coroutines.flow.Flow

/**
 * Repository for online session RPCs.
 *
 * Every method below [getMyActiveSession]/[getMyActiveSessions] (which are real-account-only)
 * accepts an optional [String] `guestToken` parameter, defaulted to null. Pass the token captured
 * from [createSession]/[joinSession] when the caller is a guest (no Supabase Auth session — see
 * the 2026-08 anonymous-sign-in removal); leave it null for a real signed-in account, which
 * continues to resolve identity via `auth.uid()` exactly as before.
 */
interface OnlineSessionRepository {
    suspend fun getMyActiveSession(): Result<ActiveSession?>
    suspend fun getMyActiveSessions(): Result<List<ActiveSession>>
    suspend fun abandonMyActiveSession(sessionId: String, guestToken: String? = null): Result<Unit>
    suspend fun createSession(mode: String, playerCount: Int, layoutKey: String?, displayName: String, themeKey: String): Result<CreateSessionResult>
    suspend fun joinSession(code: String, displayName: String, themeKey: String): Result<JoinSessionResult>
    suspend fun getSnapshot(sessionId: String, guestToken: String? = null): Result<SessionSnapshot>
    suspend fun startSession(sessionId: String, guestToken: String? = null): Result<Unit>
    suspend fun leaveSession(sessionId: String, guestToken: String? = null): Result<Unit>
    suspend fun updateLife(sessionId: String, slotIndex: Int, newLife: Int, guestToken: String? = null): Result<Unit>
    suspend fun updateCommanderDamage(sessionId: String, targetSlot: Int, sourceSlot: Int, delta: Int, guestToken: String? = null): Result<Unit>
    suspend fun updateCounter(sessionId: String, slotIndex: Int, counterType: String, delta: Int, guestToken: String? = null): Result<Unit>
    suspend fun advancePhase(sessionId: String, guestToken: String? = null): Result<Unit>
    suspend fun nextTurn(sessionId: String, guestToken: String? = null): Result<Unit>
    suspend fun confirmDefeat(sessionId: String, slotIndex: Int, guestToken: String? = null): Result<Unit>
    suspend fun revokeDefeat(sessionId: String, slotIndex: Int, guestToken: String? = null): Result<Unit>
    suspend fun setReady(sessionId: String, isReady: Boolean, guestToken: String? = null): Result<Unit>
    fun observeSession(sessionId: String): Flow<SessionEvent>
    suspend fun connectRealtime(sessionId: String)
    suspend fun disconnectRealtime(sessionId: String)
    suspend fun broadcastLifeDelta(sessionId: String, slotIndex: Int, newLife: Int)
    suspend fun broadcastPhaseChange(sessionId: String, newPhase: String, activePlayerSlot: Int, turnNumber: Int)
    suspend fun broadcastCounterUpdate(sessionId: String, slotIndex: Int, counterType: String, newValue: Int)
    suspend fun broadcastCommanderDamage(sessionId: String, targetSlot: Int, sourceSlot: Int, newDamage: Int)
    suspend fun broadcastDefeatConfirmed(sessionId: String, slotIndex: Int)
    suspend fun broadcastLandToggled(sessionId: String, slotIndex: Int, played: Boolean)
}
