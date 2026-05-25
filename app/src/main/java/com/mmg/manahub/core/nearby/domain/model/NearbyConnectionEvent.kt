package com.mmg.manahub.core.nearby.domain.model

/**
 * Represents lifecycle events emitted by the Nearby Connections layer.
 *
 * Consumers (typically ViewModels) observe these events to update UI state
 * (e.g. show "player X connected", handle disconnection, surface errors).
 */
sealed class NearbyConnectionEvent {

    /**
     * A remote endpoint has successfully connected and been authenticated.
     *
     * @param endpointId  The opaque Nearby Connections endpoint identifier.
     * @param playerName  The human-readable name advertised by the remote device.
     */
    data class EndpointConnected(
        val endpointId: String,
        val playerName: String,
    ) : NearbyConnectionEvent()

    /**
     * A previously connected endpoint has disconnected (either gracefully or due to signal loss).
     *
     * @param endpointId The opaque Nearby Connections endpoint identifier.
     */
    data class EndpointDisconnected(val endpointId: String) : NearbyConnectionEvent()

    /**
     * A connection attempt to or from a remote endpoint failed.
     *
     * @param endpointId The opaque Nearby Connections endpoint identifier.
     * @param statusCode The Google Play Services [com.google.android.gms.common.api.Status] code.
     */
    data class ConnectionFailed(
        val endpointId: String,
        val statusCode: Int,
    ) : NearbyConnectionEvent()
}
