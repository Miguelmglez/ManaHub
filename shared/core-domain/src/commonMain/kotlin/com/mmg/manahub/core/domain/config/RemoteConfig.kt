package com.mmg.manahub.core.domain.config

/**
 * Server-driven app update thresholds.
 *
 * All values default to "no requirement" so a missing or failed fetch never forces an update.
 */
data class AppUpdatePolicy(
    /** Builds below this version code must update before using the app. `0` disables forcing. */
    val minSupportedVersionCode: Long = 0L,
    /** Newest published version code; builds below it are offered an optional update. */
    val latestVersionCode: Long = 0L,
    /** Custom copy for the forced-update screen; blank falls back to the built-in message. */
    val forceUpdateMessage: String = "",
)

/**
 * Snapshot of the currently activated remote configuration.
 */
data class RemoteConfig(
    /** Kill switches whose remote value is `true`. */
    val killedFeatures: Set<KillSwitch> = emptySet(),
    /** Update thresholds used by [com.mmg.manahub.core.domain.update.EvaluateAppUpdateRequirementUseCase]. */
    val appUpdatePolicy: AppUpdatePolicy = AppUpdatePolicy(),
) {
    /** Returns true when [killSwitch] is remotely disabling its feature. */
    fun isKilled(killSwitch: KillSwitch): Boolean = killSwitch in killedFeatures

    companion object {
        /** Fail-open configuration: nothing killed, no update required. */
        val DEFAULT: RemoteConfig = RemoteConfig(
            killedFeatures = KillSwitch.entries.filter { it.killedByDefault }.toSet(),
        )
    }
}

/**
 * Remote Config key names and their defaults — the single source of truth for every platform.
 */
object RemoteConfigKeys {
    /** Long: minimum version code allowed to run. */
    const val MIN_SUPPORTED_VERSION_CODE: String = "min_supported_version_code"

    /** Long: latest published version code. */
    const val LATEST_VERSION_CODE: String = "latest_version_code"

    /** String: optional custom forced-update message. */
    const val FORCE_UPDATE_MESSAGE: String = "force_update_message"

    /**
     * Default value for every known key, built from [AppUpdatePolicy] and the [KillSwitch] registry.
     */
    fun defaults(): Map<String, Any> {
        val policy = AppUpdatePolicy()
        return buildMap {
            put(MIN_SUPPORTED_VERSION_CODE, policy.minSupportedVersionCode)
            put(LATEST_VERSION_CODE, policy.latestVersionCode)
            put(FORCE_UPDATE_MESSAGE, policy.forceUpdateMessage)
            KillSwitch.entries.forEach { put(it.remoteKey, it.killedByDefault) }
        }
    }
}
