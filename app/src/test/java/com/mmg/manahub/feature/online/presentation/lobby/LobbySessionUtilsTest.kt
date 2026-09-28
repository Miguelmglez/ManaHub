package com.mmg.manahub.feature.online.presentation.lobby

import com.mmg.manahub.core.online.domain.model.OnlineParticipant
import com.mmg.manahub.core.online.domain.model.OnlineSessionStatus
import com.mmg.manahub.core.online.domain.model.ParticipantStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LobbySessionUtilsTest {

    private fun participant(id: String, status: ParticipantStatus) = OnlineParticipant(
        id          = id,
        sessionId   = "session-1",
        slotIndex   = 0,
        userId      = null,
        displayName = "Player $id",
        themeKey    = null,
        isHost      = false,
        isReady     = false,
        status      = status,
        lastSeenAt  = "2026-01-01T00:00:00Z",
    )

    @Test
    fun `given UNKNOWN session status then it is not terminal`() {
        assertFalse(OnlineSessionStatus.UNKNOWN.isTerminal())
        assertTrue(OnlineSessionStatus.FINISHED.isTerminal())
        assertTrue(OnlineSessionStatus.ABANDONED.isTerminal())
    }

    @Test
    fun `given UNKNOWN participant status when merged then previous known status is kept`() {
        // Arrange
        val current = listOf(participant("p-1", ParticipantStatus.JOINED))
        val snapshot = listOf(participant("p-1", ParticipantStatus.UNKNOWN))

        // Act
        val (merged, _) = mergeParticipantsById(current, snapshot)

        // Assert
        assertEquals(ParticipantStatus.JOINED, merged.single().status)
    }

    @Test
    fun `given UNKNOWN participant status with no previous entry then participant stays visible`() {
        // Act
        val (merged, _) = mergeParticipantsById(emptyList(), listOf(participant("p-1", ParticipantStatus.UNKNOWN)))

        // Assert
        assertEquals(ParticipantStatus.UNKNOWN, merged.single().status)
    }
}
