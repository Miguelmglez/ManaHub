package com.mmg.manahub.core.online.data.remote.dto

import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.core.online.domain.model.OnlineSessionStatus
import com.mmg.manahub.core.online.domain.model.ParticipantStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Decode tests for [SessionSnapshotDto] against the shapes the `get_session_snapshot` RPC really
 * returns: a guest-hosted session has `host_user_id = null`, and status columns can carry values
 * newer than this client build.
 */
class SessionSnapshotDtoTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val crashlytics = mockk<FirebaseCrashlytics>(relaxed = true)

    @Before
    fun setUp() {
        mockkStatic(FirebaseCrashlytics::class)
        every { FirebaseCrashlytics.getInstance() } returns crashlytics
    }

    @After
    fun tearDown() {
        unmockkStatic(FirebaseCrashlytics::class)
    }

    private fun snapshotJson(sessionStatus: String = "LOBBY", participantStatus: String = "JOINED") = """
        {
          "session": {
            "id": "session-1",
            "code": "123456",
            "host_user_id": null,
            "game_mode": "COMMANDER",
            "player_count": 4,
            "layout_key": null,
            "status": "$sessionStatus",
            "tournament_id": null,
            "tournament_match_id": null,
            "created_at": "2026-01-01T00:00:00Z",
            "started_at": null,
            "finished_at": null,
            "last_activity_at": "2026-01-01T00:00:00Z"
          },
          "session_state": null,
          "player_states": [],
          "participants": [
            {
              "id": "p-1",
              "session_id": "session-1",
              "slot_index": 0,
              "user_id": null,
              "display_name": "Guest",
              "theme_key": null,
              "is_host": true,
              "is_ready": false,
              "status": "$participantStatus",
              "last_seen_at": "2026-01-01T00:00:00Z"
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `given guest-hosted session with null host_user_id when decoded then hostUserId is null`() {
        // Act
        val snapshot = json.decodeFromString<SessionSnapshotDto>(snapshotJson()).toDomain()

        // Assert
        assertNull(snapshot.session.hostUserId)
        assertEquals(OnlineSessionStatus.LOBBY, snapshot.session.status)
        // A lobby snapshot has no session_state row: null, never a synthesized "LOBBY" GamePhase
        assertNull(snapshot.sessionState)
    }

    @Test
    fun `given unknown session status when decoded then maps to UNKNOWN and records a non-fatal`() {
        // Act
        val snapshot = json.decodeFromString<SessionSnapshotDto>(snapshotJson(sessionStatus = "ARCHIVED")).toDomain()

        // Assert
        assertEquals(OnlineSessionStatus.UNKNOWN, snapshot.session.status)
        verify(exactly = 1) { crashlytics.recordException(any()) }
    }

    @Test
    fun `given unknown participant status when decoded then maps to UNKNOWN not LEFT`() {
        // Act
        val snapshot = json.decodeFromString<SessionSnapshotDto>(snapshotJson(participantStatus = "SPECTATING")).toDomain()

        // Assert
        assertEquals(ParticipantStatus.UNKNOWN, snapshot.participants.single().status)
        verify(exactly = 1) { crashlytics.recordException(any()) }
    }

    @Test
    fun `given known statuses when decoded then no non-fatal is recorded`() {
        // Act
        json.decodeFromString<SessionSnapshotDto>(snapshotJson()).toDomain()

        // Assert
        verify(exactly = 0) { crashlytics.recordException(any()) }
    }
}
