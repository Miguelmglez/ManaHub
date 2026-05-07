package com.mmg.manahub.feature.scanner

import android.graphics.PointF
import com.mmg.manahub.core.domain.model.Card

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
     */
    data class Identified(
        val card: Card,
        val similarity: Float,
        val ambiguous: Boolean,
        val corners: List<PointF>,
    ) : RecognitionResult
}
