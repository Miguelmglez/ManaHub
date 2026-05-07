package com.mmg.manahub.feature.scanner

/**
 * Result of a nearest-neighbour search in [EmbeddingDatabase].
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
