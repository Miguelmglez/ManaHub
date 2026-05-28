package com.mmg.manahub.core.online.data.remote

import com.mmg.manahub.core.di.IoDispatcher
import com.mmg.manahub.core.online.data.remote.dto.ActiveSessionDto
import com.mmg.manahub.core.online.data.remote.dto.CreateSessionResponseDto
import com.mmg.manahub.core.online.data.remote.dto.JoinSessionResponseDto
import com.mmg.manahub.core.online.data.remote.dto.SessionSnapshotDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

// Validates session join code matches backend format: 6 digits.
private val SESSION_CODE_REGEX = Regex("^[0-9]{6}$")

@Singleton
class OnlineSessionRemoteDataSource @Inject constructor(
    private val supabaseClient: SupabaseClient,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun getMyActiveSession(): Result<ActiveSessionDto?> = withContext(ioDispatcher) {
        runCatching {
            supabaseClient.postgrest.rpc("get_my_active_session")
                .decodeAs<List<ActiveSessionDto>>()
                .firstOrNull()
        }
    }

    suspend fun getMyActiveSessions(): Result<List<ActiveSessionDto>> = withContext(ioDispatcher) {
        runCatching {
            supabaseClient.postgrest.rpc("get_my_active_sessions")
                .decodeAs<List<ActiveSessionDto>>()
        }
    }

    suspend fun abandonMyActiveSession(sessionId: String): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            supabaseClient.postgrest.rpc(
                "abandon_my_active_session",
                buildJsonObject { put("p_session_id", sessionId) },
            )
            Unit
        }
    }

    suspend fun createSession(
        mode: String,
        playerCount: Int,
        layoutKey: String?,
        displayName: String,
        themeKey: String,
    ): Result<CreateSessionResponseDto> = withContext(ioDispatcher) {
        runCatching {
            supabaseClient.postgrest.rpc(
                "create_online_session",
                buildJsonObject {
                    put("p_mode", mode)
                    put("p_player_count", playerCount)
                    layoutKey?.let { put("p_layout_key", it) }
                    put("p_display_name", displayName)
                    put("p_theme_key", themeKey)
                },
            ).decodeAs<CreateSessionResponseDto>()
        }
    }

    suspend fun joinSession(
        code: String,
        displayName: String,
        themeKey: String,
    ): Result<JoinSessionResponseDto> = withContext(ioDispatcher) {
        runCatching {
            val sanitized = code.trim()
            require(SESSION_CODE_REGEX.matches(sanitized)) { "Invalid session code format" }
            require(displayName.isNotBlank() && displayName.length <= 32) { "Display name must be 1–32 characters" }
            supabaseClient.postgrest.rpc(
                "join_session",
                buildJsonObject {
                    put("p_code", sanitized)
                    put("p_display_name", displayName.trim())
                    put("p_theme_key", themeKey)
                },
            ).decodeAs<JoinSessionResponseDto>()
        }
    }

    suspend fun getSnapshot(sessionId: String): Result<SessionSnapshotDto> =
        withContext(ioDispatcher) {
            runCatching {
                supabaseClient.postgrest.rpc(
                    "get_session_snapshot",
                    buildJsonObject { put("p_session_id", sessionId) },
                ).decodeAs<SessionSnapshotDto>()
            }
        }

    suspend fun startSession(sessionId: String): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            supabaseClient.postgrest.rpc(
                "start_session",
                buildJsonObject { put("p_session_id", sessionId) },
            )
            Unit
        }
    }

    suspend fun leaveSession(sessionId: String): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            supabaseClient.postgrest.rpc(
                "leave_session",
                buildJsonObject { put("p_session_id", sessionId) },
            )
            Unit
        }
    }

    suspend fun updateLife(sessionId: String, slotIndex: Int, newLife: Int): Result<Unit> =
        withContext(ioDispatcher) {
            runCatching {
                supabaseClient.postgrest.rpc(
                    "update_player_life",
                    buildJsonObject {
                        put("p_session_id", sessionId)
                        put("p_slot_index", slotIndex)
                        put("p_new_life", newLife)
                    },
                )
                Unit
            }
        }

    suspend fun updateCommanderDamage(
        sessionId: String,
        targetSlot: Int,
        sourceSlot: Int,
        delta: Int,
    ): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            supabaseClient.postgrest.rpc(
                "update_commander_damage",
                buildJsonObject {
                    put("p_session_id", sessionId)
                    put("p_target_slot", targetSlot)
                    put("p_source_slot", sourceSlot)
                    put("p_delta", delta)
                },
            )
            Unit
        }
    }

    suspend fun updateCounter(
        sessionId: String,
        slotIndex: Int,
        counterType: String,
        delta: Int,
    ): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            supabaseClient.postgrest.rpc(
                "update_player_counter",
                buildJsonObject {
                    put("p_session_id", sessionId)
                    put("p_slot_index", slotIndex)
                    put("p_counter_type", counterType)
                    put("p_delta", delta)
                },
            )
            Unit
        }
    }

    suspend fun advancePhase(sessionId: String): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            supabaseClient.postgrest.rpc(
                "advance_phase",
                buildJsonObject { put("p_session_id", sessionId) },
            )
            Unit
        }
    }

    suspend fun nextTurn(sessionId: String): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            supabaseClient.postgrest.rpc(
                "next_turn",
                buildJsonObject { put("p_session_id", sessionId) },
            )
            Unit
        }
    }

    suspend fun confirmDefeat(sessionId: String, slotIndex: Int): Result<Unit> =
        withContext(ioDispatcher) {
            runCatching {
                supabaseClient.postgrest.rpc(
                    "confirm_defeat",
                    buildJsonObject {
                        put("p_session_id", sessionId)
                        put("p_slot_index", slotIndex)
                    },
                )
                Unit
            }
        }

    suspend fun revokeDefeat(sessionId: String, slotIndex: Int): Result<Unit> =
        withContext(ioDispatcher) {
            runCatching {
                supabaseClient.postgrest.rpc(
                    "revoke_defeat",
                    buildJsonObject {
                        put("p_session_id", sessionId)
                        put("p_slot_index", slotIndex)
                    },
                )
                Unit
            }
        }

    suspend fun setReady(sessionId: String, isReady: Boolean): Result<Unit> =
        withContext(ioDispatcher) {
            runCatching {
                supabaseClient.postgrest.rpc(
                    "set_participant_ready",
                    buildJsonObject {
                        put("p_session_id", sessionId)
                        put("p_is_ready", isReady)
                    },
                )
                Unit
            }
        }
}
