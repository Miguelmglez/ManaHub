package com.mmg.manahub.core.domain.update

import com.mmg.manahub.core.domain.config.AppUpdatePolicy

/**
 * How strongly the running build must be updated.
 */
enum class AppUpdateRequirement {
    /** The build is current (or no policy is configured). */
    None,

    /** A newer build exists; updating is offered but not required. */
    Optional,

    /** The build is below the minimum supported version and must not be used. */
    Forced,
}

/**
 * Compares the running build's version code with the remote [AppUpdatePolicy].
 *
 * Default policy values (`0`) always yield [AppUpdateRequirement.None], which keeps the check
 * fail-open when Remote Config is unavailable.
 */
class EvaluateAppUpdateRequirementUseCase {

    /**
     * @param currentVersionCode version code of the running build.
     * @param policy the activated remote update policy.
     */
    operator fun invoke(currentVersionCode: Long, policy: AppUpdatePolicy): AppUpdateRequirement =
        when {
            currentVersionCode < policy.minSupportedVersionCode -> AppUpdateRequirement.Forced
            currentVersionCode < policy.latestVersionCode -> AppUpdateRequirement.Optional
            else -> AppUpdateRequirement.None
        }
}
