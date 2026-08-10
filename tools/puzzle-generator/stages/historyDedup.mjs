/**
 * historyDedup.mjs
 *
 * Excludes candidates whose normalized name was already used as an answer in the trailing ~90
 * days, per the ALREADY-PUBLISHED `answerHistory` in R2's puzzle/index.json -- the single source
 * of truth for dedup (design doc §5.1: "no generator-local state that can diverge").
 */

import { normalizeCardName } from './normalize.mjs';

const DEDUP_WINDOW_DAYS = 90;

/**
 * @param {Array<{date:string,normalizedName:string}>} answerHistory
 * @param {string} asOfDate ISO yyyy-MM-dd -- history entries older than DEDUP_WINDOW_DAYS before
 *   this date are no longer considered "recently used"
 * @returns {Set<string>} normalized names to exclude
 */
export function buildRecentAnswerSet(answerHistory, asOfDate) {
  const cutoff = new Date(asOfDate);
  cutoff.setUTCDate(cutoff.getUTCDate() - DEDUP_WINDOW_DAYS);
  const cutoffIso = cutoff.toISOString().slice(0, 10);

  const recent = new Set();
  for (const entry of answerHistory) {
    if (entry?.date >= cutoffIso && entry?.normalizedName) {
      recent.add(entry.normalizedName);
    }
  }
  return recent;
}

/**
 * @param {Array<object>} candidates cards that already passed candidates.mjs + typeGate.mjs
 * @param {Set<string>} recentAnswerNames from buildRecentAnswerSet
 * @returns {Array<object>} candidates not used in the dedup window
 */
export function excludeRecentlyUsed(candidates, recentAnswerNames) {
  return candidates.filter((card) => !recentAnswerNames.has(normalizeCardName(card.name)));
}
