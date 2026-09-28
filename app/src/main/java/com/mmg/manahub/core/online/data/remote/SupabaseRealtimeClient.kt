package com.mmg.manahub.core.online.data.remote

import com.mmg.manahub.core.di.IoDispatcher
import com.mmg.manahub.core.online.data.remote.dto.OnlineSessionDto
import com.mmg.manahub.core.online.data.remote.dto.SessionParticipantDto
import com.mmg.manahub.core.online.data.remote.dto.SessionPlayerStateDto
import com.mmg.manahub.core.online.data.remote.dto.SessionStateDto
import com.mmg.manahub.core.online.domain.model.RealtimeConnectException
import com.mmg.manahub.core.online.domain.model.RealtimeDisconnectedException
import com.mmg.manahub.core.online.domain.model.RealtimeSubscribeFailedException
import com.mmg.manahub.core.online.domain.model.RealtimeSubscribeTimeoutException
import com.mmg.manahub.core.online.domain.model.SessionEvent
import com.mmg.manahub.core.util.recordNonFatal
import com.mmg.manahub.core.util.recordSafeNonFatal
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.HasRecord
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.broadcast
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
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
    private class SessionHandle(
        val channel: RealtimeChannel,
        val eventFlow: MutableSharedFlow<SessionEvent>,
        val scope: CoroutineScope,
        val ready: CompletableDeferred<Unit>,
    )

    private val mutex = Mutex()
    // ConcurrentHashMap for lock-free reads in observeSession/broadcast; the mutex guards check-then-act
    private val sessions = ConcurrentHashMap<String, SessionHandle>()

    /**
     * Subscribes to the session channel and suspends until the server confirms it.
     *
     * Concurrent calls for the same id share one attempt. Throws a [RealtimeConnectException]
     * subclass on timeout, SDK failure, or an early [disconnect]; every failure leaves no handle
     * registered, so the caller can keep polling and retry later on a fresh channel.
     */
    suspend fun connect(sessionId: String) {
        val handle = mutex.withLock {
            sessions[sessionId] ?: buildHandle(sessionId).also { sessions[sessionId] = it }
        }
        val outcome: Result<Unit>? = withTimeoutOrNull(SUBSCRIBE_TIMEOUT_MS) {
            try {
                Result.success(handle.ready.await())
            } catch (e: RealtimeConnectException) {
                Result.failure(e)
            }
        }
        val failure = if (outcome == null) RealtimeSubscribeTimeoutException() else outcome.exceptionOrNull()
        if (failure != null) {
            teardown(sessionId, handle)
            throw failure
        }
    }

    /** Leaves the channel and drops it from the SDK registry so a later [connect] starts clean. */
    suspend fun disconnect(sessionId: String) {
        mutex.withLock {
            val handle = sessions.remove(sessionId) ?: return
            releaseChannel(handle)
        }
    }

    fun observeSession(sessionId: String): Flow<SessionEvent> =
        sessions[sessionId]?.eventFlow ?: emptyFlow()

    /**
     * Drops the replay buffer for [sessionId]. Callers invoke this right after applying an
     * authoritative snapshot: a late collector would otherwise replay deltas that the snapshot
     * already accounts for and regress life totals back to pre-snapshot values.
     */
    fun clearReplay(sessionId: String) {
        sessions[sessionId]?.eventFlow?.resetReplayCache()
    }

    suspend fun broadcastLifeDelta(sessionId: String, slotIndex: Int, newLife: Int): Boolean =
        broadcast(sessionId, "life_delta", LifeDeltaPayload(slotIndex, newLife))

    suspend fun broadcastPhaseChange(sessionId: String, newPhase: String, activePlayerSlot: Int, turnNumber: Int): Boolean =
        broadcast(sessionId, "phase_change", PhaseChangedPayload(newPhase, activePlayerSlot, turnNumber))

    suspend fun broadcastCounterUpdate(sessionId: String, slotIndex: Int, counterType: String, newValue: Int): Boolean =
        broadcast(sessionId, "counter_update", CounterUpdatePayload(slotIndex, counterType, newValue))

    suspend fun broadcastCommanderDamage(sessionId: String, targetSlot: Int, sourceSlot: Int, newDamage: Int): Boolean =
        broadcast(sessionId, "commander_damage", CommanderDamagePayload(targetSlot, sourceSlot, newDamage))

    suspend fun broadcastDefeatConfirmed(sessionId: String, slotIndex: Int): Boolean =
        broadcast(sessionId, "defeat_confirmed", DefeatConfirmedPayload(slotIndex))

    suspend fun broadcastLandToggled(sessionId: String, slotIndex: Int, played: Boolean): Boolean =
        broadcast(sessionId, "land_toggled", LandToggledPayload(slotIndex, played))

    /** Returns false when there is no live handle or the SDK throws (REST fallback non-2xx, offline). */
    private suspend inline fun <reified T : Any> broadcast(sessionId: String, event: String, payload: T): Boolean {
        val channel = sessions[sessionId]?.channel ?: return false
        return runCatching { channel.broadcast(event = event, message = payload) }
            .onFailure { e ->
                if (e is CancellationException) throw e
                recordSafeNonFatal("online_broadcast_${event}_failed", e)
            }
            .isSuccess
    }

    private fun buildHandle(sessionId: String): SessionHandle {
        // replay lets a collector arriving after connect() (e.g. after the snapshot call) catch up;
        // the buffer is bounded and drops the OLDEST event so a slow collector cannot grow it forever
        val eventFlow = MutableSharedFlow<SessionEvent>(
            replay = 10,
            extraBufferCapacity = 64,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
        val scope = CoroutineScope(ioDispatcher + SupervisorJob())
        val ch = supabaseClient.realtime.channel("online_session:$sessionId")

        ch.collectRecord<PostgresAction.Insert, SessionParticipantDto>(scope, eventFlow, "session_participants", "session_id", sessionId) {
            SessionEvent.ParticipantUpdated(it.toDomain())
        }
        ch.collectRecord<PostgresAction.Update, SessionParticipantDto>(scope, eventFlow, "session_participants", "session_id", sessionId) {
            SessionEvent.ParticipantUpdated(it.toDomain())
        }
        ch.collectRecord<PostgresAction.Update, SessionStateDto>(scope, eventFlow, "session_state", "session_id", sessionId) {
            SessionEvent.StateUpdated(it.toDomain())
        }
        ch.collectRecord<PostgresAction.Update, SessionPlayerStateDto>(scope, eventFlow, "session_player_state", "session_id", sessionId) {
            SessionEvent.PlayerStateUpdated(it.toDomain())
        }
        ch.collectRecord<PostgresAction.Update, OnlineSessionDto>(scope, eventFlow, "online_sessions", "id", sessionId) {
            SessionEvent.SessionStatusChanged(it.toDomain().status)
        }

        ch.collectBroadcast<LifeDeltaPayload>(scope, eventFlow, "life_delta") {
            SessionEvent.LifeDeltaReceived(it.slotIndex, it.newLife)
        }
        ch.collectBroadcast<PhaseChangedPayload>(scope, eventFlow, "phase_change") {
            SessionEvent.PhaseChangedReceived(it.newPhase, it.activePlayerSlot, it.turnNumber)
        }
        ch.collectBroadcast<CounterUpdatePayload>(scope, eventFlow, "counter_update") {
            SessionEvent.CounterUpdatedReceived(it.slotIndex, it.counterType, it.newValue)
        }
        ch.collectBroadcast<CommanderDamagePayload>(scope, eventFlow, "commander_damage") {
            SessionEvent.CommanderDamageReceived(it.targetSlot, it.sourceSlot, it.newDamage)
        }
        ch.collectBroadcast<DefeatConfirmedPayload>(scope, eventFlow, "defeat_confirmed") {
            SessionEvent.DefeatConfirmedReceived(it.slotIndex)
        }
        ch.collectBroadcast<LandToggledPayload>(scope, eventFlow, "land_toggled") {
            SessionEvent.LandToggledReceived(it.slotIndex, it.played)
        }

        // The SDK tears down every channel's callbacks on an internal disconnect (session loss,
        // socket drop) and silently rejoins, leaving our flows attached to a channel with no
        // listeners — surface the reset so the consumer can rebuild its subscription.
        scope.launch {
            var wasDisconnected = false
            supabaseClient.realtime.status.collect { status ->
                when (status) {
                    Realtime.Status.DISCONNECTED -> wasDisconnected = true
                    Realtime.Status.CONNECTED -> if (wasDisconnected) {
                        wasDisconnected = false
                        recordNonFatal("online_realtime_reset_detected")
                        eventFlow.tryEmit(SessionEvent.Error(REALTIME_RESET))
                    }
                    else -> Unit
                }
            }
        }

        // Subscribe on the session scope so a cancelled caller never aborts the SDK mid-join
        val ready = CompletableDeferred<Unit>()
        scope.launch {
            runCatching { ch.subscribe(blockUntilSubscribed = true) }
                .fold(
                    onSuccess = { ready.complete(Unit) },
                    onFailure = { ex ->
                        val failure = if (ex is CancellationException) RealtimeDisconnectedException()
                        else RealtimeSubscribeFailedException(ex)
                        ready.completeExceptionally(failure)
                    },
                )
        }
        return SessionHandle(ch, eventFlow, scope, ready)
    }

    private inline fun <reified A, reified D : Any> RealtimeChannel.collectRecord(
        scope: CoroutineScope,
        eventFlow: MutableSharedFlow<SessionEvent>,
        table: String,
        filterColumn: String,
        sessionId: String,
        crossinline toEvent: (D) -> SessionEvent,
    ) where A : PostgresAction, A : HasRecord {
        val flow = postgresChangeFlow<A>(schema = "public") {
            this.table = table
            filter(filterColumn, FilterOperator.EQ, sessionId)
        }
        scope.launch {
            flow.collect { action ->
                runCatching { action.decodeRecord<D>() }
                    .onSuccess { eventFlow.emit(toEvent(it)) }
                    .onFailure { e ->
                        if (e is CancellationException) throw e
                        recordSafeNonFatal("online_cdc_decode_failed_$table", e)
                        eventFlow.tryEmit(SessionEvent.Error("decode_$table"))
                    }
            }
        }
    }

    private inline fun <reified P : Any> RealtimeChannel.collectBroadcast(
        scope: CoroutineScope,
        eventFlow: MutableSharedFlow<SessionEvent>,
        event: String,
        crossinline toEvent: (P) -> SessionEvent,
    ) {
        val flow = broadcastFlow<P>(event = event)
        scope.launch {
            flow.collect { payload -> eventFlow.emit(toEvent(payload)) }
        }
    }

    /** Idempotent: a handle already released by [disconnect] is not released twice. */
    private suspend fun teardown(sessionId: String, handle: SessionHandle) {
        mutex.withLock {
            if (sessions[sessionId] !== handle) return
            sessions.remove(sessionId)
            releaseChannel(handle)
        }
    }

    private suspend fun releaseChannel(handle: SessionHandle) {
        handle.scope.cancel()
        handle.ready.completeExceptionally(RealtimeDisconnectedException())
        // Unconditional: a disconnect racing an in-flight subscribe must still send phx_leave
        runCatching { handle.channel.unsubscribe() }
            .onFailure { if (it is CancellationException) throw it; recordSafeNonFatal("online_realtime_unsubscribe_failed", it) }
        // Without removeChannel the SDK hands back the cached joined channel on the next connect
        runCatching { supabaseClient.realtime.removeChannel(handle.channel) }
            .onFailure { if (it is CancellationException) throw it; recordSafeNonFatal("online_realtime_remove_channel_failed", it) }
    }

    @Serializable
    private data class LifeDeltaPayload(
        @SerialName("slot_index") val slotIndex: Int,
        @SerialName("new_life")   val newLife: Int,
    )

    @Serializable
    private data class PhaseChangedPayload(
        @SerialName("new_phase")          val newPhase: String,
        @SerialName("active_player_slot") val activePlayerSlot: Int,
        @SerialName("turn_number")        val turnNumber: Int,
    )

    @Serializable
    private data class CounterUpdatePayload(
        @SerialName("slot_index")    val slotIndex: Int,
        @SerialName("counter_type") val counterType: String,
        @SerialName("new_value")    val newValue: Int,
    )

    @Serializable
    private data class CommanderDamagePayload(
        @SerialName("target_slot") val targetSlot: Int,
        @SerialName("source_slot") val sourceSlot: Int,
        @SerialName("new_damage")  val newDamage: Int,
    )

    @Serializable
    private data class DefeatConfirmedPayload(
        @SerialName("slot_index") val slotIndex: Int,
    )

    @Serializable
    private data class LandToggledPayload(
        @SerialName("slot_index") val slotIndex: Int,
        @SerialName("played")     val played: Boolean,
    )

    companion object {
        /** [SessionEvent.Error] message emitted when the SDK reset the socket and our callbacks were dropped. */
        const val REALTIME_RESET = "realtime_reset"
        private const val SUBSCRIBE_TIMEOUT_MS = 10_000L
    }
}
