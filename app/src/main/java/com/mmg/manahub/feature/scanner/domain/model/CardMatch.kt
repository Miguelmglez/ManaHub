package com.mmg.manahub.feature.scanner.domain.model

/**
 * Result of a nearest-neighbour search over a card embedding index.
 *
 * This modeled the cosine-similarity nearest-neighbour lookup used by the pre-OCR scanner
 * pipeline. That pipeline (embedding database + on-device TFLite model) was removed in WS5 of
 * `docs/plans/scanner-reliability-plan.md` (2026-08-25) in favour of ML Kit Text Recognition —
 * see `CardRecognizer` and [com.mmg.manahub.feature.scanner.domain.model.RecognitionResult]. No
 * code in the app constructs a [CardMatch] any more; it is currently unused and kept only because
 * deleting it was out of scope for that cleanup pass — flag before removing it outright.
 *
 * @property scryfallId          Scryfall UUID of the best-matching card.
 * @property similarity          Cosine similarity between the query embedding and the best match
 *                               in [0, 1]. Both vectors are L2-normalised, so this equals the
 *                               dot product.
 * @property secondBestSimilarity Cosine similarity of the second-best candidate; used to detect
 *                               ambiguous matches.
 *                               Defaults to 0 when there is only one candidate in the DB.
 */
data class CardMatch(
    val scryfallId: String,
    val similarity: Float,
    val secondBestSimilarity: Float = 0f,
)
