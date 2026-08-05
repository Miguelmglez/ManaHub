/**
 * normalize.mjs
 *
 * Card-name normalization used to hash the daily puzzle's answer and to dedup answer history.
 *
 * MUST stay byte-identical to the Kotlin client's normalizer
 * (`shared/core-model/src/commonMain/kotlin/com/mmg/manahub/core/model/puzzle/PuzzleNameNormalizer.kt`)
 * -- Kotlin code cannot be imported into this Node pipeline, so any change to the algorithm on
 * either side must be mirrored on the other or the client and the generator will silently
 * disagree on whether a guess matches the answer. Both sides are covered by a fixture list of
 * tricky names (double-faced cards, apostrophes, accented characters) -- see this file's test
 * counterpart and `PuzzleNameNormalizerTest` on the Kotlin side.
 *
 * Algorithm: trim -> take the front face of a double-faced/split name ("Fire // Ice" -> "Fire")
 * -> lowercase -> expand non-decomposable Latin ligatures (æ "ae", œ "oe" -- Unicode NFD
 * does NOT decompose these into base+combining-mark pairs, unlike accented letters, so they need an
 * explicit substitution or names like "AErathi Berserker" normalize to a mismatching value) ->
 * strip diacritics -> strip apostrophes/commas/periods -> collapse remaining non-alphanumeric runs
 * to a single space -> trim.
 */

const DIACRITICS_PATTERN = new RegExp('[\\u0300-\\u036f]', 'g');
const QUOTE_LIKE_PATTERN = new RegExp("['\\u2019,.]", 'g');
const LIGATURES = { 'æ': 'ae', 'œ': 'oe' };
const LIGATURE_PATTERN = new RegExp('[æœ]', 'g');

/** @param {string} rawName @returns {string} */
export function normalizeCardName(rawName) {
  if (!rawName) return '';

  let s = rawName.trim();

  const dfcSeparatorIndex = s.indexOf(' // ');
  if (dfcSeparatorIndex !== -1) {
    s = s.slice(0, dfcSeparatorIndex);
  }

  s = s.toLowerCase();
  s = s.replace(LIGATURE_PATTERN, (ch) => LIGATURES[ch]);
  s = s.normalize('NFD').replace(DIACRITICS_PATTERN, ''); // strip diacritics (combining marks)
  s = s.replace(QUOTE_LIKE_PATTERN, ''); // drop apostrophes/commas/periods with no replacement space
  s = s.replace(/[^a-z0-9]+/g, ' ').trim();

  return s;
}
