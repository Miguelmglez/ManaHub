package com.mmg.manahub.core.domain.update

import kotlinx.coroutines.flow.StateFlow

/**
 * App-wide update status combining the store's in-app update signal with the remote update policy.
 */
sealed interface AppUpdateState {

    /** No update to offer. */
    data object None : AppUpdateState

    /**
     * An optional update exists.
     *
     * @property inAppFlowAvailable true when the store can run an in-app update; false when only
     * the remote policy knows about it and the store listing must be opened instead.
     */
    data class Available(val inAppFlowAvailable: Boolean) : AppUpdateState

    /** An optional update is downloading in the background. */
    data object Downloading : AppUpdateState

    /** An optional update finished downloading and needs a restart to install. */
    data object Downloaded : AppUpdateState

    /**
     * The running build is below the minimum supported version and must be updated.
     *
     * @property message custom remote copy; blank means use the built-in message.
     * @property minSupportedVersionCode remote minimum version that triggered the block.
     */
    data class Forced(val message: String, val minSupportedVersionCode: Long) : AppUpdateState
}

/**
 * Read + command surface over the platform update mechanism, consumed by presentation code.
 */
interface AppUpdateStatusProvider {
    /** Current update status. */
    val state: StateFlow<AppUpdateState>

    /** Starts the most appropriate update path for the current [state] (in-app flow or store). */
    fun requestUpdate()

    /** Installs an already downloaded update, restarting the app. */
    fun completeUpdate()
}
