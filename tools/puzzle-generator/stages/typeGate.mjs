/**
 * typeGate.mjs
 *
 * Type-specific validity gates, run after `candidates` (popularity/pool filtering) and before
 * `historyDedup`. Design doc §5.5 gate #1 for GUESS_CARD: "in the popularity pool; has an image;
 * not used in the last ~90 days." Popularity is handled by `candidates.mjs`; recency is handled by
 * `historyDedup.mjs`; this stage is the remaining "has an image (and resolvable attributes)" check.
 *
 * Rejects (returns false), never repairs -- a candidate that fails here is simply skipped in favor
 * of the next one, same principle the schema validator downstream follows.
 */

/** @param {object} card a candidate from candidates.mjs @returns {boolean} */
export function passesGuessCardGate(card) {
  const artCard = card.image_uris ? card : card.card_faces?.[0];
  if (!artCard?.image_uris?.normal) return false;

  if (typeof card.cmc !== 'number') return false;
  if (!Array.isArray(card.color_identity)) return false;
  if (!card.rarity) return false;
  if (!card.type_line) return false;
  if (!card.set) return false;
  if (!card.released_at || !/^\d{4}-\d{2}-\d{2}$/.test(card.released_at)) return false;

  return true;
}
