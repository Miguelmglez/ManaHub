package com.mmg.manahub.core.domain.config

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Source of remotely controlled configuration (kill switches + update policy).
 *
 * Implementations are fail-open: a missing or failed fetch keeps [RemoteConfig.DEFAULT] (or the
 * last activated values) and never disables a feature or forces an update on its own.
 */
interface RemoteConfigRepository {
    /** Currently activated configuration; updates live when the backend pushes a change. */
    val config: StateFlow<RemoteConfig>

    /** Fetches and activates the latest values. Never throws; failures keep the current config. */
    suspend fun refresh()
}

/**
 * Defaults-only implementation for targets without a remote config backend (web) and for tests.
 */
class DefaultsOnlyRemoteConfigRepository(
    initial: RemoteConfig = RemoteConfig.DEFAULT,
) : RemoteConfigRepository {
    private val _config = MutableStateFlow(initial)

    override val config: StateFlow<RemoteConfig> = _config.asStateFlow()

    override suspend fun refresh() = Unit
}
