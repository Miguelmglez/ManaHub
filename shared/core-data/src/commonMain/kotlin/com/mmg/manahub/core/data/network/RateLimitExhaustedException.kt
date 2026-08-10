package com.mmg.manahub.core.data.network

/**
 * Thrown by [RateLimitedQueue.execute] when a retryable failure (e.g. HTTP 429/503) exhausts all
 * configured retries.
 *
 * Distinguishes rate-limit exhaustion from a generic network failure so callers can surface a
 * "service busy, retrying shortly" message instead of a plain error, and so a UI retry CTA can be
 * disabled with a countdown instead of letting the user re-trigger the storm the app is already
 * recovering from.
 *
 * ## Why the message embeds a sentinel
 * Most of this codebase's data layer flattens exceptions down to a plain `String` early (see
 * `DataResult.Error(message: String)`), so a caller several layers up the stack (a `ViewModel`, a
 * `@Composable`) frequently has ONLY that string to work with -- not the original exception object.
 * [message] therefore embeds a stable [SENTINEL_PREFIX] followed by [retryAfterMs], mirroring the
 * pre-existing `"SCRYFALL_404"` sentinel-string convention already used the same way
 * (`CardRepositoryImpl`/`AddCardScreen`). Callers that DO have the exception object should prefer
 * [retryAfterMs] directly; callers that only have a flattened message string use
 * [retryAfterMsOrNull] to parse it back out.
 *
 * @property retryAfterMs Milliseconds remaining in the shared cooldown ([RateLimitedQueue]) at the
 *   moment retries were exhausted. Never negative.
 */
class RateLimitExhaustedException(
    val retryAfterMs: Long,
    cause: Throwable? = null,
) : RuntimeException("$SENTINEL_PREFIX$retryAfterMs", cause) {

    companion object {
        /**
         * Stable, non-PII prefix marking a flattened error message as a rate-limit-exhaustion
         * sentinel. Never rename -- any persisted/logged message using the old prefix would silently
         * stop being recognised.
         */
        const val SENTINEL_PREFIX: String = "RATE_LIMIT_EXHAUSTED:"

        /**
         * Parses the remaining cooldown (ms) back out of a flattened error message, or `null` when
         * [message] is not a rate-limit-exhaustion sentinel (including a generic error, or `null`).
         */
        fun retryAfterMsOrNull(message: String?): Long? =
            message
                ?.takeIf { it.startsWith(SENTINEL_PREFIX) }
                ?.substringAfter(SENTINEL_PREFIX)
                ?.toLongOrNull()
    }
}
