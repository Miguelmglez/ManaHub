#!/usr/bin/env node
/**
 * normalize.test.mjs
 *
 * Fixture-based regression test for normalizeCardName(). Run with `node normalize.test.mjs`.
 *
 * This is the JS-side half of the "MUST stay byte-identical" contract with the Kotlin client's
 * PuzzleNameNormalizer (shared/core-model/src/commonMain/kotlin/com/mmg/manahub/core/model/puzzle/
 * PuzzleNameNormalizer.kt) -- both sides should be exercised against the SAME fixture list
 * (mirrored manually, since Kotlin and Node can't share a literal source of truth). Found via a
 * 2026-08-06 edge-case audit: an earlier version of the Kotlin side deleted non-alphanumeric
 * characters instead of replacing them with a space, and had no AE/OE ligature handling -- both
 * would have made any real hyphenated or ligature-containing card name (Ranger-Captain of Eos,
 * AErathi Berserker) permanently unguessable if ever selected as an answer.
 */

import { normalizeCardName } from './normalize.mjs';

const cases = [
  ['Lightning Bolt', 'lightning bolt'],
  ['Fire // Ice', 'fire'],
  ["Urza's Tower", 'urzas tower'],
  ['Séance', 'seance'],
  ['Ærathi Berserker', 'aerathi berserker'],
  ['Æther Vial', 'aether vial'],
  // Hyphenated names with no surrounding spaces -- must split into separate words, never collapse.
  ['Ranger-Captain of Eos', 'ranger captain of eos'],
  ['Ghost-Lit Redeemer', 'ghost lit redeemer'],
  ['Blood-Chin Rager', 'blood chin rager'],
  ['  Sword of Fire and Ice  ', 'sword of fire and ice'],
];

let failures = 0;
for (const [input, expected] of cases) {
  const got = normalizeCardName(input);
  if (got !== expected) {
    failures++;
    console.error(`FAIL: normalizeCardName(${JSON.stringify(input)}) = ${JSON.stringify(got)}, expected ${JSON.stringify(expected)}`);
  }
}

if (failures > 0) {
  console.error(`${failures}/${cases.length} fixture(s) failed.`);
  process.exit(1);
}
console.log(`All ${cases.length} normalizeCardName fixtures passed.`);
