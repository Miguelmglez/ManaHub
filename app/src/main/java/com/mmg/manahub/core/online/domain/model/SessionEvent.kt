package com.mmg.manahub.core.online.domain.model

sealed class SessionEvent {
    data class ParticipantUpdated(val participant: OnlineParticipant) : SessionEvent()
    data class StateUpdated(val state: SessionState) : SessionEvent()
    data class PlayerStateUpdated(val playerState: SessionPlayerState) : SessionEvent()
    data class SessionStatusChanged(val status: OnlineSessionStatus) : SessionEvent()
    data class LifeDeltaReceived(val slotIndex: Int, val newLife: Int) : SessionEvent()
    data class PhaseChangedReceived(val newPhase: String, val activePlayerSlot: Int, val turnNumber: Int) : SessionEvent()
    data class CounterUpdatedReceived(val slotIndex: Int, val counterType: String, val newValue: Int) : SessionEvent()
    data class CommanderDamageReceived(val targetSlot: Int, val sourceSlot: Int, val newDamage: Int) : SessionEvent()
    data class Error(val message: String) : SessionEvent()
}
