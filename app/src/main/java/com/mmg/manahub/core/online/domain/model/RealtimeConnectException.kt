package com.mmg.manahub.core.online.domain.model

/**
 * Typed failure of a Realtime `connect()` attempt. Each subclass has a stable class name so
 * consumers can log it as a breadcrumb without touching the message; every failure path leaves
 * the session handle unregistered so a later `connect()` starts from a clean channel.
 */
sealed class RealtimeConnectException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** The server never confirmed the channel subscription within the subscribe timeout. */
class RealtimeSubscribeTimeoutException : RealtimeConnectException("realtime_subscribe_timeout")

/** The session was disconnected (an early `disconnect()` or a teardown) while still subscribing. */
class RealtimeDisconnectedException : RealtimeConnectException("realtime_disconnected_while_subscribing")

/** The SDK threw while subscribing; [cause] carries the original exception. */
class RealtimeSubscribeFailedException(cause: Throwable) : RealtimeConnectException("realtime_subscribe_failed", cause)
