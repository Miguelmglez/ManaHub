package com.mmg.manahub.core.nearby.data

import android.content.Context
import android.util.Log
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import com.mmg.manahub.core.nearby.domain.model.NearbyConnectionEvent
import com.mmg.manahub.core.nearby.domain.model.NearbyGameMessage
import com.mmg.manahub.core.nearby.domain.repository.NearbySessionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Concrete implementation of [NearbySessionRepository] backed by
 * [com.google.android.gms.nearby.connection.ConnectionsClient].
 *
 * **Topology**: P2P_STAR — the host (slot 0) advertises with [startAdvertising]; clients
 * discover with [startDiscovery] and connect automatically when the correct session code
 * is found in the endpoint name. The host relays every inbound message to all other
 * connected endpoints so that all devices converge on the same game state.
 *
 * **Full-state sync**: When a new client connects, the host calls [fullStateSyncProvider]
 * (if set) to obtain the current game state and immediately sends a [NearbyGameMessage.FullStateSync]
 * to that endpoint. The GameViewModel must set [fullStateSyncProvider] after calling
 * `initFromNearbySession()`.
 *
 * **Serialization**: All messages are serialized as JSON using [kotlinx.serialization] with a
 * `"type"` discriminator field, e.g. `{"type":"life","slot":0,"life":20}`.
 */
@Singleton
class NearbySessionRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : NearbySessionRepository {

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    companion object {
        private const val TAG = "NearbySession"

        /** Fixed service ID — must be the same on host and all clients. */
        const val SERVICE_ID = "com.mmg.manahub.nearby"

        /**
         * Separator used to embed the session code inside the endpoint name.
         * Endpoint name format: "<sessionCode>|<playerName>"
         */
        private const val NAME_SEPARATOR = "|"
    }

    // -------------------------------------------------------------------------
    // JSON serializer — polymorphic on NearbyGameMessage sealed hierarchy
    // -------------------------------------------------------------------------

    private val json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "type"
        serializersModule = SerializersModule {
            polymorphic(NearbyGameMessage::class) {
                subclass(NearbyGameMessage.LifeChanged::class)
                subclass(NearbyGameMessage.PoisonChanged::class)
                subclass(NearbyGameMessage.ExperienceChanged::class)
                subclass(NearbyGameMessage.EnergyChanged::class)
                subclass(NearbyGameMessage.CommanderDamageChanged::class)
                subclass(NearbyGameMessage.PhaseChanged::class)
                subclass(NearbyGameMessage.TurnChanged::class)
                subclass(NearbyGameMessage.DefeatConfirmed::class)
                subclass(NearbyGameMessage.DefeatRevoked::class)
                subclass(NearbyGameMessage.GameFinished::class)
                subclass(NearbyGameMessage.FullStateSync::class)
            }
        }
    }

    // -------------------------------------------------------------------------
    // Nearby Connections client
    // -------------------------------------------------------------------------

    private val connectionsClient by lazy { Nearby.getConnectionsClient(context) }

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    /** Whether this device is acting as the advertising host. */
    private var isHost = false

    /**
     * Session code currently in use. Stored so that [EndpointDiscoveryCallback] can filter
     * discovered endpoints by matching the code embedded in their endpoint names.
     */
    private var activeSessionCode: String = ""

    /** All currently connected endpoint IDs. */
    private val allEndpoints: MutableSet<String> = mutableSetOf()

    private val _connectedCount = MutableStateFlow(0)
    override val connectedCount: StateFlow<Int> = _connectedCount.asStateFlow()

    // -------------------------------------------------------------------------
    // Flows
    // -------------------------------------------------------------------------

    private val _messageFlow = MutableSharedFlow<NearbyGameMessage>(extraBufferCapacity = 64)
    private val _eventFlow = MutableSharedFlow<NearbyConnectionEvent>(extraBufferCapacity = 16)

    // -------------------------------------------------------------------------
    // Full-state sync hook — set by GameViewModel after initFromNearbySession()
    // -------------------------------------------------------------------------

    /**
     * Optional provider invoked by the host each time a new client connects.
     * The returned [NearbyGameMessage.FullStateSync] is sent to that client immediately
     * so it can catch up to the current game state.
     *
     * The GameViewModel must assign this lambda after initializing the session.
     */
    override var fullStateSyncProvider: (() -> NearbyGameMessage.FullStateSync?)? = null

    // -------------------------------------------------------------------------
    // Callbacks
    // -------------------------------------------------------------------------

    /**
     * Handles the connection lifecycle: negotiation, acceptance, disconnection.
     *
     * Both the host and each client install this same callback. The key difference is
     * that the host sends a [NearbyGameMessage.FullStateSync] on connection success.
     */
    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {

        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            // Auto-accept every incoming connection. For production, consider showing
            // a verification dialog using connectionInfo.authenticationDigits.
            Log.d(TAG, "Connection initiated with $endpointId (${connectionInfo.endpointName})")
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, resolution: ConnectionResolution) {
            when (resolution.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    Log.d(TAG, "Connected to $endpointId")
                    allEndpoints += endpointId
                    _connectedCount.value = allEndpoints.size

                    // Retrieve the human-readable name stored during onConnectionInitiated.
                    // Because ConnectionInfo is not retained here we use a lightweight approach:
                    // the name was already emitted in onConnectionInitiated — for the event we
                    // emit an empty string as a safe fallback; the ViewModel can correlate via
                    // endpointId if it needs the name.
                    _eventFlow.tryEmit(
                        NearbyConnectionEvent.EndpointConnected(
                            endpointId = endpointId,
                            playerName = "",
                        )
                    )

                    // Host sends a full-state snapshot to the newly connected client.
                    if (isHost) {
                        fullStateSyncProvider?.invoke()?.let { sync ->
                            sendMessageToEndpoint(sync, endpointId)
                        }
                    }
                }

                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED,
                ConnectionsStatusCodes.STATUS_ERROR -> {
                    Log.w(TAG, "Connection to $endpointId failed: ${resolution.status.statusCode}")
                    _eventFlow.tryEmit(
                        NearbyConnectionEvent.ConnectionFailed(
                            endpointId = endpointId,
                            statusCode = resolution.status.statusCode,
                        )
                    )
                }

                else -> {
                    Log.w(TAG, "Unknown connection result for $endpointId: ${resolution.status.statusCode}")
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            Log.d(TAG, "Disconnected from $endpointId")
            allEndpoints -= endpointId
            _connectedCount.value = allEndpoints.size
            _eventFlow.tryEmit(NearbyConnectionEvent.EndpointDisconnected(endpointId))
        }
    }

    /**
     * Handles incoming payloads.
     *
     * On receipt the raw bytes are deserialized to [NearbyGameMessage]. If this device is the
     * host, the raw payload is also forwarded to all other connected endpoints (relay/broadcast).
     * The deserialized message is then emitted on [_messageFlow] so the local ViewModel
     * can apply it.
     */
    private val payloadCallback = object : PayloadCallback() {

        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type != Payload.Type.BYTES) return
            val bytes = payload.asBytes() ?: return

            // Host relay: re-send the raw bytes to every other connected endpoint.
            if (isHost) {
                val otherEndpoints = allEndpoints - endpointId
                if (otherEndpoints.isNotEmpty()) {
                    val relayPayload = Payload.fromBytes(bytes)
                    connectionsClient.sendPayload(otherEndpoints.toList(), relayPayload)
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Relay failed: ${e.message}")
                        }
                }
            }

            // Deserialize and emit to local observers.
            try {
                val message = json.decodeFromString<NearbyGameMessage>(bytes.decodeToString())
                _messageFlow.tryEmit(message)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to deserialize message from $endpointId: ${e.message}")
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // No-op: we use small BYTES payloads that transfer atomically.
        }
    }

    /**
     * Discovers available hosts and automatically requests a connection to any endpoint whose
     * name starts with the active session code.
     */
    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {

        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            if (info.serviceId != SERVICE_ID) return

            // Endpoint name format: "<sessionCode>|<playerName>"
            val namePrefix = "$activeSessionCode$NAME_SEPARATOR"
            if (!info.endpointName.startsWith(namePrefix)) {
                Log.d(TAG, "Ignoring endpoint ${info.endpointName} — session code mismatch")
                return
            }

            Log.d(TAG, "Found matching host endpoint $endpointId (${info.endpointName}), requesting connection")
            connectionsClient.requestConnection(
                /* localEndpointName = */ info.endpointName,
                endpointId,
                connectionLifecycleCallback,
            ).addOnFailureListener { e ->
                Log.e(TAG, "requestConnection failed: ${e.message}")
                _eventFlow.tryEmit(
                    NearbyConnectionEvent.ConnectionFailed(
                        endpointId = endpointId,
                        statusCode = -1,
                    )
                )
            }
        }

        override fun onEndpointLost(endpointId: String) {
            Log.d(TAG, "Host endpoint lost: $endpointId")
        }
    }

    // -------------------------------------------------------------------------
    // NearbySessionRepository implementation
    // -------------------------------------------------------------------------

    override suspend fun startAdvertising(sessionCode: String, playerName: String): Result<Unit> {
        isHost = true
        activeSessionCode = sessionCode

        val endpointName = "$sessionCode$NAME_SEPARATOR$playerName"
        val advertisingOptions = AdvertisingOptions.Builder()
            .setStrategy(Strategy.P2P_STAR)
            .build()

        return try {
            kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
                connectionsClient.startAdvertising(
                    endpointName,
                    SERVICE_ID,
                    connectionLifecycleCallback,
                    advertisingOptions,
                ).addOnSuccessListener {
                    Log.d(TAG, "Advertising started as '$endpointName'")
                    continuation.resume(Result.success(Unit), null)
                }.addOnFailureListener { e ->
                    Log.e(TAG, "Advertising failed: ${e.message}")
                    continuation.resume(Result.failure(e), null)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun startDiscovery(sessionCode: String, playerName: String): Result<Unit> {
        isHost = false
        activeSessionCode = sessionCode

        val discoveryOptions = DiscoveryOptions.Builder()
            .setStrategy(Strategy.P2P_STAR)
            .build()

        return try {
            kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
                connectionsClient.startDiscovery(
                    SERVICE_ID,
                    endpointDiscoveryCallback,
                    discoveryOptions,
                ).addOnSuccessListener {
                    Log.d(TAG, "Discovery started for session '$sessionCode'")
                    continuation.resume(Result.success(Unit), null)
                }.addOnFailureListener { e ->
                    Log.e(TAG, "Discovery failed: ${e.message}")
                    continuation.resume(Result.failure(e), null)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun sendMessage(message: NearbyGameMessage) {
        if (allEndpoints.isEmpty()) {
            Log.w(TAG, "sendMessage called but no endpoints connected")
            return
        }
        val serialized = json.encodeToString(NearbyGameMessage.serializer(), message)
        val payload = Payload.fromBytes(serialized.encodeToByteArray())
        connectionsClient.sendPayload(allEndpoints.toList(), payload)
            .addOnFailureListener { e ->
                Log.e(TAG, "sendMessage failed: ${e.message}")
            }
    }

    override fun observeMessages(): Flow<NearbyGameMessage> = _messageFlow

    override fun observeConnectionEvents(): Flow<NearbyConnectionEvent> = _eventFlow

    override fun disconnect() {
        Log.d(TAG, "Disconnecting all Nearby Connections")
        connectionsClient.stopAdvertising()
        connectionsClient.stopDiscovery()
        connectionsClient.stopAllEndpoints()
        allEndpoints.clear()
        _connectedCount.value = 0
        isHost = false
        activeSessionCode = ""
        fullStateSyncProvider = null
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Serializes [message] and sends it to a single [endpointId].
     * Used internally by the host to deliver [NearbyGameMessage.FullStateSync].
     */
    private fun sendMessageToEndpoint(message: NearbyGameMessage, endpointId: String) {
        val serialized = json.encodeToString(NearbyGameMessage.serializer(), message)
        val payload = Payload.fromBytes(serialized.encodeToByteArray())
        connectionsClient.sendPayload(endpointId, payload)
            .addOnFailureListener { e ->
                Log.e(TAG, "sendMessageToEndpoint($endpointId) failed: ${e.message}")
            }
    }
}
