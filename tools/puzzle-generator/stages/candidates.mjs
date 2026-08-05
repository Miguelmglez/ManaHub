/**
 * candidates.mjs
 *
 * Filters the raw oracle_cards snapshot down to cards eligible to ever be a GUESS_CARD answer.
 * Popularity is used as a fairness proxy -- an obscure card makes for an unfair/unfun puzzle.
 */

/** Cards with an edhrec_rank higher (worse) than this are excluded -- popularity floor. */
const MAX_EDHREC_RANK = 15000;

const EXCLUDED_LAYOUTS = new Set(['token', 'double_faced_token', 'emblem', 'art_series', 'scheme', 'vanguard', 'planar']);
const EXCLUDED_TYPE_SUBSTRINGS = ['Basic Land'];

/**
 * @param {Array<object>} oracleCards raw Scryfall oracle_cards entries
 * @returns {Array<object>} eligible candidates, deduped by oracle_id (one printing per logical card)
 */
export function buildCandidatePool(oracleCards) {
  const seenOracleIds = new Set();
  const candidates = [];

  for (const card of oracleCards) {
    if (!card.oracle_id || seenOracleIds.has(card.oracle_id)) continue;
    if (card.set_type === 'funny' || card.digital === true) continue;
    if (EXCLUDED_LAYOUTS.has(card.layout)) continue;
    if (EXCLUDED_TYPE_SUBSTRINGS.some((s) => card.type_line?.includes(s))) continue;
    if (typeof card.edhrec_rank !== 'number' || card.edhrec_rank > MAX_EDHREC_RANK) continue;
    if (!card.name) continue;

    seenOracleIds.add(card.oracle_id);
    candidates.push(card);
  }

  console.error(`candidates: ${candidates.length} eligible (of ${oracleCards.length} total oracle_cards)`);
  return candidates;
}
