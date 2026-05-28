package com.mmg.manahub.core.nearby.domain.repository

import com.mmg.manahub.core.nearby.domain.model.NearbyConnectionEvent
import com.mmg.manahub.core.nearby.domain.model.NearbyGameMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Contract for the Nearby Connections session layer.
 *
 * This interface belongs to the **domain** layer and must remain free of any
 * Android framework or Google Play Services imports. Implementations live in
 * the data layer ([com.mmg.manahub.core.nearby.data.NearbySessionRepositoryImpl]).
 *
 * **Topology**: STRATEGY_STAR — the host (slot 0) advertises; clients discover and
 * connect to the host. The host re-broadcasts every received message to all other
 * endpoints to keep everyone in sync.
 */
interface NearbySessionRepository {

    /**
     * Starts advertising this device as a game host so that clients can discover it.
     *
     * The endpoint name is derived from [sessionCode] and [playerName] so that discovering
     * clients can filter by session code before requesting a connection.
     *
     * Must be called only on the host device (slot 0).
     *
     * @param sessionCode The lobby session code shared out-of-band (e.g. via Supabase lobby).
     * @param playerName  The display name of the hosting player.
     * @return [Result.success] if advertising started; [Result.failure] with the underlying
     *         exception otherwise.
     */
    suspend fun startAdvertising(sessionCode: String, playerName: String): Result<Unit>

    /**
     * Starts scanning for a host advertising the given [sessionCode] and automatically
     * requests a connection when the matching endpoint is found.
     *
     * Must be called only on client devices (slot 1+).
     *
     * @param sessionCode The lobby session code to filter discovered endpoints.
     * @param playerName  The display name of the connecting player.
     * @return [Result.success] if discovery started; [Result.failure] with the underlying
     *         exception otherwise.
     */
    suspend fun startDiscovery(sessionCode: String, playerName: String): Result<Unit>

    /**
     * Serializes [message] to JSON and sends it to **all** currently connected endpoints.
     *
     * Fire-and-forget — delivery errors are surfaced only via [observeConnectionEvents].
     */
    fun sendMessage(message: NearbyGameMessage)

    /**
     * Cold flow that emits every [NearbyGameMessage] received from remote endpoints.
     *
     * On the host, messages received from one client are automatically relayed to all
     * other clients **before** being emitted here, so the host ViewModel receives the
     * same events as every client.
     */
    fun observeMessages(): Flow<NearbyGameMessage>

    /**
     * Cold flow that emits [NearbyConnectionEvent]s (connect, disconnect, failure) as they
     * occur in the Nearby Connections lifecycle callbacks.
     */
    fun observeConnectionEvents(): Flow<NearbyConnectionEvent>

    /**
     * The number of remote endpoints currently in the connected state.
     * Updated atomically as endpoints connect or disconnect.
     */
    val connectedCount: StateFlow<Int>

    /**
     * Optional provider invoked by the host each time a new client connects.
     * The returned [NearbyGameMessage.FullStateSync] is sent to that client immediately
     * so it can catch up to the current game state.
     *
     * The GameViewModel must assign this lambda after initializing the session.
     */
    var fullStateSyncProvider: (() -> NearbyGameMessage.FullStateSync?)?

    /**
     * Stops advertising/discovery, disconnects all endpoints, and releases all Nearby
     * Connections resources. Safe to call multiple times.
     */
    fun disconnect()
}
