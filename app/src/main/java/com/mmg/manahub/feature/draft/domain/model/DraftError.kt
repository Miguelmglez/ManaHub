package com.mmg.manahub.feature.draft.domain.model

sealed interface DraftError {
    /** The set has no booster.json published — boosterVersion == null. */
    data object SetNotDraftable : DraftError
    /** The tier list is missing or has no entries for this set. */
    data object RatingsMissing : DraftError
    /** The set card pool has not been downloaded yet. */
    data object SetNotDownloaded : DraftError
    /** Device is offline and there is no cached data available. */
    data object OfflineNoCache : DraftError
    data class Unexpected(val message: String) : DraftError
}
