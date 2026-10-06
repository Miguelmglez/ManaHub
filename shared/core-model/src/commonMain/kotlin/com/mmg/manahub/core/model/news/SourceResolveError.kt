package com.mmg.manahub.core.model.news

/** Why a pasted source could not be resolved or followed; each maps to one actionable message. */
enum class SourceResolveError {
    INVALID_INPUT,
    NOT_HTTPS,
    UNREACHABLE,
    NO_FEED_FOUND,
    YOUTUBE_CHANNEL_NOT_FOUND,
    EMPTY_FEED,
    ALREADY_FOLLOWING,
}

/** Carries a [SourceResolveError] through `Result.failure`. The message is the enum name only (no URL). */
class SourceResolveException(val error: SourceResolveError) : Exception(error.name)
