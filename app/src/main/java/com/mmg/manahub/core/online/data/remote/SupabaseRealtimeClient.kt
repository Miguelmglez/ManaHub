package com.mmg.manahub.core.online.data.remote

import com.mmg.manahub.core.di.IoDispatcher
import com.mmg.manahub.core.online.data.remote.dto.OnlineSessionDto
import com.mmg.manahub.core.online.data.remote.dto.SessionParticipantDto
import com.mmg.manahub.core.online.data.remote.dto.SessionPlayerStateDto
import com.mmg.manahub.core.online.data.remote.dto.SessionStateDto
import com.mmg.manahub.core.online.domain.model.OnlineSessionStatus
import com.mmg.manahub.core.online.domain.model.SessionEvent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.broadcast
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseRealtimeClient @Inject constructor(
    private val supabaseClient: SupabaseClient,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private data class SessionHandle(
        val channel: RealtimeChannel,
        val eventFlow: MutableSharedFlow<SessionEvent>,
        val scope: CoroutineScope,
    )

    private val sessions = ConcurrentHashMap<String, SessionHandle>()

    suspend fun connect(sessionId: String) {
        if (sessions.containsKey(sessionId)) return

        // replay = 10 buffers recent events so that a collector arriving slightly after
        // connect() (e.g. after a snapshot HTTP call) does not miss them.
        val eventFlow = MutableSharedFlow<SessionEvent>(
            replay = 10,
            extraBufferCapacity = Channel.UNLIMITED,
        )
        val sessionScope = CoroutineScope(ioDispatcher + SupervisorJob())
        val ch = supabaseClient.realtime.channel("online_session:$sessionId")

        // INSERT: new participant joins the session
        ch.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "session_participants"
            filter("session_id", FilterOperator.EQ, sessionId)
        }.also { flow ->
            sessionScope.launch {
                flow.collect { action ->
                    runCatching { action.decodeRecord<SessionParticipantDto>() }
                        .onSuccess { eventFlow.emit(SessionEvent.ParticipantUpdated(it.toDomain())) }
                }
            }
        }

        // UPDATE: participant changes ready state, theme, or status
        ch.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
            table = "session_participants"
            filter("session_id", FilterOperator.EQ, sessionId)
        }.also { flow ->
            sessionScope.launch {
                flow.collect { action ->
                    runCatching { action.decodeRecord<SessionParticipantDto>() }
                        .onSuccess { eventFlow.emit(SessionEvent.ParticipantUpdated(it.toDomain())) }
                }
            }
        }

        ch.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
            table = "session_state"
            filter("session_id", FilterOperator.EQ, sessionId)
        }.also { flow ->
            sessionScope.launch {
                flow.collect { action ->
                    runCatching { action.decodeRecord<SessionStateDto>() }
                        .onSuccess { eventFlow.emit(SessionEvent.StateUpdated(it.toDomain())) }
                }
            }
        }

        ch.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
            table = "session_player_state"
            filter("session_id", FilterOperator.EQ, sessionId)
        }.also { flow ->
            sessionScope.launch {
                flow.collect { action ->
                    runCatching { action.decodeRecord<SessionPlayerStateDto>() }
                        .onSuccess { eventFlow.emit(SessionEvent.PlayerStateUpdated(it.toDomain())) }
                }
            }
        }

        ch.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
            table = "online_sessions"
            filter("id", FilterOperator.EQ, sessionId)
        }.also { flow ->
            sessionScope.launch {
                flow.collect { action ->
                    runCatching { action.decodeRecord<OnlineSessionDto>() }
                        .onSuccess { dto ->
                            val status = runCatching { OnlineSessionStatus.valueOf(dto.status) }
                                .getOrDefault(OnlineSessionStatus.ABANDONED)
                            eventFlow.emit(SessionEvent.SessionStatusChanged(status))
                        }
                }
            }
        }

        ch.broadcastFlow<LifeDeltaPayload>(event = "life_delta").also { flow ->
            sessionScope.launch {
                flow.collect { payload ->
                    eventFlow.emit(SessionEvent.LifeDeltaReceived(payload.slotIndex, payload.newLife))
                }
            }
        }

        ch.broadcastFlow<PhaseChangedPayload>(event = "phase_change").also { flow ->
            sessionScope.launch {
                flow.collect { payload ->
                    eventFlow.emit(SessionEvent.PhaseChangedReceived(payload.newPhase))
                }
            }
        }

        // Store handle before subscribe so observeSession() returns the real flow immediately
        sessions[sessionId] = SessionHandle(ch, eventFlow, sessionScope)

        // Run subscribe on sessionScope so a Ktor exception during cancellation of the
        // calling coroutine doesn't crash the app. CancellationException inside sessionScope
        // (from an early disconnect()) is treated as a clean exit.
        val subscriptionReady = CompletableDeferred<Unit>()
        sessionScope.launch {
            runCatching { ch.subscribe(blockUntilSubscribed = true) }
                .fold(
                    onSuccess = { subscriptionReady.complete(Unit) },
                    onFailure = { ex ->
                        if (ex is CancellationException) subscriptionReady.complete(Unit)
                        else subscriptionReady.completeExceptionally(ex)
                    },
                )
        }
        // Suspends until the server confirms the subscription (or the calling coroutine
        // is cancelled — in which case CancellationException propagates normally and the
        // subscription itself finishes on sessionScope).
        subscriptionReady.await()
    }

    suspend fun disconnect(sessionId: String) {
        sessions.remove(sessionId)?.let { handle ->
            handle.scope.cancel()
            runCatching { supabaseClient.realtime.removeChannel(handle.channel) }
        }
    }

    fun observeSession(sessionId: String): Flow<SessionEvent> =
        sessions[sessionId]?.eventFlow ?: emptyFlow()

    suspend fun broadcastLifeDelta(sessionId: String, slotIndex: Int, newLife: Int) {
        sessions[sessionId]?.channel?.broadcast(
            event = "life_delta",
            message = LifeDeltaPayload(slotIndex, newLife),
        )
    }

    suspend fun broadcastPhaseChange(sessionId: String, newPhase: String) {
        sessions[sessionId]?.channel?.broadcast(
            event = "phase_change",
            message = PhaseChangedPayload(newPhase),
        )
    }

    @Serializable
    private data class LifeDeltaPayload(
        @SerialName("slot_index") val slotIndex: Int,
        @SerialName("new_life")   val newLife: Int,
    )

    @Serializable
    private data class PhaseChangedPayload(
        @SerialName("new_phase") val newPhase: String,
    )
}
