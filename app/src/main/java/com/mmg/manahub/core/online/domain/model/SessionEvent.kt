package com.mmg.manahub.core.online.domain.model

sealed class SessionEvent {
    data class ParticipantUpdated(val participant: OnlineParticipant) : SessionEvent()
    data class StateUpdated(val state: SessionState) : SessionEvent()
    data class PlayerStateUpdated(val playerState: SessionPlayerState) : SessionEvent()
    data class SessionStatusChanged(val status: OnlineSessionStatus) : SessionEvent()
    data class LifeDeltaReceived(val slotIndex: Int, val newLife: Int) : SessionEvent()
    data class PhaseChangedReceived(val newPhase: String) : SessionEvent()
    data class Error(val message: String) : SessionEvent()
}
