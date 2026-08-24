package com.mmg.manahub.feature.scanner.domain.model

import android.graphics.PointF
import com.mmg.manahub.core.model.Card

/**
 * Represents the outcome of a single frame processed by [CardRecognizer].
 *
 * The sealed interface is the single source of truth shared between [CardRecognizer]
 * (producer) and [ScannerViewModel] (consumer).
 */
sealed interface RecognitionResult {

    /** No card quadrilateral detected in the frame. */
    data object NoCard : RecognitionResult

    /**
     * A card outline was detected but the embedding lookup returned no match
     * (either the database is empty or no card exceeded the similarity threshold).
     *
     * @property corners Four corner points in frame pixel coordinates.
     */
    data class Detected(val corners: List<PointF>) : RecognitionResult

    /**
     * A card was detected and successfully identified via cosine nearest-neighbour search.
     *
     * @property card       Resolved domain [Card] from [CardRepository].
     * @property similarity Cosine similarity in [0, 1] between the query embedding and the
     *                      best match in [EmbeddingDatabase].
     * @property ambiguous  True when the gap between the best and second-best similarity
     *                      is less than 0.08 (the [EmbeddingDatabase.AMBIGUITY_GAP] threshold).
     * @property corners    Four corner points in frame pixel coordinates.
     * @property languageFallback  W2.11 (scanner-reliability-plan.md, 2026-08-24). True when a
     *                      non-English language was selected but no printing exists in that
     *                      language, so [card] is the English printing instead. Purely
     *                      informational -- the card IS added (never silently refused); the UI
     *                      shows a "no <lang> printing found -- added as EN" badge.
     */
    data class Identified(
        val card: Card,
        val similarity: Float,
        val ambiguous: Boolean,
        val corners: List<PointF>,
        val languageFallback: Boolean = false,
    ) : RecognitionResult

    /**
     * W2.10 (scanner-reliability-plan.md, 2026-08-24). The shared Scryfall rate limiter
     * ([com.mmg.manahub.core.data.network.RateLimitedQueue]) exhausted its retries for the
     * current resolution attempt. OCR keeps running (the on-screen guide keeps highlighting
     * text) but [CardRecognizer] suspends every further Scryfall lookup until
     * [retryAfterMs] has elapsed, so the scanner stops amplifying an active cooldown instead of
     * queuing up more doomed requests behind it.
     *
     * @property retryAfterMs Milliseconds remaining in the shared cooldown at the moment retries
     *                      were exhausted (mirrors
     *                      `com.mmg.manahub.core.data.network.RateLimitExhaustedException.retryAfterMs`).
     */
    data class RateLimited(val retryAfterMs: Long) : RecognitionResult
}
