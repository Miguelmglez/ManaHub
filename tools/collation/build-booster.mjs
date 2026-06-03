#!/usr/bin/env node
/**
 * build-booster.mjs
 *
 * Generates a booster.json collation file for a given MTG set.
 * Data source: MTGJSON per-set API  https://mtgjson.com/api/v5/{SET_CODE}.json
 *
 * Usage:
 *   node build-booster.mjs <SET_CODE>        – build one set (e.g. TDM)
 *   node build-booster.mjs --test            – run built-in smoke test for TDM
 *
 * Output:
 *   tools/collation/output/{setCode_lower}.booster.json
 *
 * Schema version: 1
 */

import { writeFileSync, mkdirSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

const MTGJSON_BASE = 'https://mtgjson.com/api/v5';
const SCHEMA_VERSION = 1;
const __dirname = dirname(fileURLToPath(import.meta.url));
const OUTPUT_DIR = join(__dirname, 'output');

/** Sheet names MTGJSON uses that indicate a foil sheet */
const FOIL_SHEET_NAMES = new Set(['foil', 'foilCard', 'foilBasicLand']);

/**
 * MTGJSON sheet-name → canonical sheet-name mapping.
 * Sheets not listed here are classified by rarity heuristic.
 */
const SHEET_NAME_MAP = {
  // commons
  common: 'common',
  commons: 'common',
  // uncommons
  uncommon: 'uncommon',
  uncommons: 'uncommon',
  // rare
  rare: 'rare',
  rares: 'rare',
  // mythic rare
  mythicRare: 'mythicRare',
  mythic: 'mythicRare',
  mythicRares: 'mythicRare',
  // foils
  foil: 'foil',
  foilCard: 'foil',
  foilBasicLand: 'foil',
  // land
  land: 'land',
  basicLand: 'land',
  basicLands: 'land',
};

// ---------------------------------------------------------------------------
// Fetch helper
// ---------------------------------------------------------------------------

async function fetchJson(url) {
  const res = await fetch(url);
  if (!res.ok) {
    throw new Error(`HTTP ${res.status} fetching ${url}`);
  }
  return res.json();
}

// ---------------------------------------------------------------------------
// Sheet-name normalisation
// ---------------------------------------------------------------------------

/**
 * Resolve a raw MTGJSON sheet name to a canonical name.
 * Falls back to 'uncommon' for exotic/unknown sheet types.
 */
function normaliseSheetName(raw) {
  if (SHEET_NAME_MAP[raw]) return SHEET_NAME_MAP[raw];
  // Lower-case heuristic fallback
  const lower = raw.toLowerCase();
  if (lower.includes('common')) return 'common';
  if (lower.includes('uncommon')) return 'uncommon';
  if (lower.includes('mythic')) return 'mythicRare';
  if (lower.includes('rare')) return 'rare';
  if (lower.includes('foil')) return 'foil';
  if (lower.includes('land')) return 'land';
  // Exotic slots (Special Guests, The List, wildcard, borderless, etc.)
  return 'uncommon';
}

/**
 * Determine whether a sheet is a foil sheet.
 */
function isFoilSheet(rawName) {
  return FOIL_SHEET_NAMES.has(rawName) || rawName.toLowerCase().includes('foil');
}

// ---------------------------------------------------------------------------
// Core builder
// ---------------------------------------------------------------------------

/**
 * Build a booster.json object from an MTGJSON set payload.
 *
 * @param {object} setData  – value of `data` from the MTGJSON set response
 * @returns {object}        – validated booster.json object
 */
function buildBoosterJson(setData) {
  const setCode = setData.code?.toLowerCase() ?? 'unknown';

  // Build a UUID → scryfallId lookup from setData.cards.
  // MTGJSON booster sheets reference cards by internal UUID (not scryfallId).
  const uuidToScryfallId = new Map();
  for (const card of (setData.cards ?? [])) {
    const sid = card?.identifiers?.scryfallId;
    if (card?.uuid && sid) uuidToScryfallId.set(card.uuid, sid);
  }

  // 1. Locate booster config: prefer 'play', then 'default'
  const boosterRoot = setData.booster;
  if (!boosterRoot) {
    throw new Error(`Set ${setCode.toUpperCase()} has no booster data in MTGJSON.`);
  }

  let boosterKey = null;
  if (boosterRoot.play) {
    boosterKey = 'play';
  } else if (boosterRoot.default) {
    boosterKey = 'default';
  } else {
    const available = Object.keys(boosterRoot).join(', ');
    throw new Error(
      `Set ${setCode.toUpperCase()}: no 'play' or 'default' booster key found. ` +
      `Available keys: ${available}. ` +
      `Do not use 'collector' or 'draft' keys.`
    );
  }

  const boosterConfig = boosterRoot[boosterKey];

  // 2. Parse variants
  const rawBoosters = boosterConfig.boosters ?? [];
  if (rawBoosters.length === 0) {
    throw new Error(`Set ${setCode.toUpperCase()}: booster.${boosterKey}.boosters is empty.`);
  }

  const variants = rawBoosters.map((b, i) => {
    const weight = b.weight;
    if (!Number.isInteger(weight) || weight <= 0) {
      throw new Error(
        `Set ${setCode.toUpperCase()}: variant[${i}].weight must be a positive integer, got ${weight}.`
      );
    }
    // Normalise content keys to canonical sheet names
    const contents = {};
    for (const [rawSheetName, count] of Object.entries(b.contents ?? {})) {
      const canonical = normaliseSheetName(rawSheetName);
      // Merge into 'rareMythic' if we later have both rare+mythicRare in contents
      const contentKey = (canonical === 'rare' || canonical === 'mythicRare') ? 'rareMythic' : canonical;
      contents[contentKey] = (contents[contentKey] ?? 0) + count;
    }
    return { weight, contents };
  });

  // 3. Parse sheets
  const rawSheets = boosterConfig.sheets ?? {};
  const sheetEntries = {};   // canonical-name → { foil, balanceColors, cards: Map<scryfallId, weight> }
  let unresolvedCount = 0;
  let totalCards = 0;
  const unresolvedBySheet = {};

  for (const [rawName, sheetDef] of Object.entries(rawSheets)) {
    const canonical = normaliseSheetName(rawName);
    // Merge rare + mythicRare into a single 'rareMythic' sheet
    const mergedName = (canonical === 'rare' || canonical === 'mythicRare') ? 'rareMythic' : canonical;

    if (!sheetEntries[mergedName]) {
      sheetEntries[mergedName] = {
        foil: isFoilSheet(rawName),
        balanceColors: mergedName === 'common',
        cards: new Map(),
      };
    }

    const entry = sheetEntries[mergedName];
    // If any source sheet is a foil sheet, mark the merged entry as foil
    if (isFoilSheet(rawName)) entry.foil = true;

    // MTGJSON sheet.cards is { "<mtgjsonUuid>": { "weight": <n> } }
    // The UUID here is the internal MTGJSON UUID; look up scryfallId via uuidToScryfallId.
    const sheetCards = sheetDef.cards ?? {};
    for (const [cardUuid, cardInfo] of Object.entries(sheetCards)) {
      totalCards++;
      const scryfallId = uuidToScryfallId.get(cardUuid);
      if (!scryfallId) {
        unresolvedCount++;
        unresolvedBySheet[mergedName] = (unresolvedBySheet[mergedName] ?? 0) + 1;
        continue;
      }
      const existingWeight = entry.cards.get(scryfallId);
      const cardWeight = typeof cardInfo?.weight === 'number' ? cardInfo.weight : 1;
      if (existingWeight !== undefined) {
        entry.cards.set(scryfallId, existingWeight + cardWeight);
      } else {
        entry.cards.set(scryfallId, cardWeight);
      }
    }
  }

  // 4. Validate unresolved threshold: abort if > 5% of cards in any sheet are unresolved
  for (const [sheetName, unresolved] of Object.entries(unresolvedBySheet)) {
    const entry = sheetEntries[sheetName];
    const total = entry.cards.size + unresolved;
    if (total > 0 && unresolved / total > 0.05) {
      throw new Error(
        `Abort: sheet '${sheetName}' has ${unresolved}/${total} unresolved scryfallIds ` +
        `(${((unresolved / total) * 100).toFixed(1)}% > 5% threshold).`
      );
    }
  }

  if (unresolvedCount > 0 && totalCards > 0 && unresolvedCount / totalCards > 0.05) {
    throw new Error(
      `Abort: overall unresolved scryfallIds = ${unresolvedCount}/${totalCards} ` +
      `(${((unresolvedCount / totalCards) * 100).toFixed(1)}% > 5% threshold).`
    );
  }

  // 5. Serialise sheets: Map → sorted array of { id, weight }
  const sheets = {};
  for (const [name, entry] of Object.entries(sheetEntries)) {
    sheets[name] = {
      foil: entry.foil,
      balanceColors: entry.balanceColors,
      cards: Array.from(entry.cards.entries())
        .map(([id, weight]) => ({ id, weight }))
        .sort((a, b) => a.id.localeCompare(b.id)),
    };
  }

  return {
    setCode,
    schemaVersion: SCHEMA_VERSION,
    boosters: variants,
    sheets,
    _meta: {
      source: `MTGJSON booster.${boosterKey}`,
      generatedAt: new Date().toISOString(),
    },
  };
}

// ---------------------------------------------------------------------------
// Main
// ---------------------------------------------------------------------------

async function buildSet(setCodeArg) {
  const setCode = setCodeArg.toUpperCase();
  process.stdout.write(`Fetching MTGJSON data for ${setCode}…\n`);

  const url = `${MTGJSON_BASE}/${setCode}.json`;
  let payload;
  try {
    payload = await fetchJson(url);
  } catch (err) {
    throw new Error(`Failed to fetch ${url}: ${err.message}`);
  }

  const setData = payload?.data;
  if (!setData) {
    throw new Error(`MTGJSON response for ${setCode} has no 'data' field.`);
  }

  const result = buildBoosterJson(setData);

  mkdirSync(OUTPUT_DIR, { recursive: true });
  const outPath = join(OUTPUT_DIR, `${setCode.toLowerCase()}.booster.json`);
  writeFileSync(outPath, JSON.stringify(result, null, 2), 'utf8');
  process.stdout.write(`Written: ${outPath}\n`);
  return result;
}

// ---------------------------------------------------------------------------
// Test mode
// ---------------------------------------------------------------------------

async function runTests() {
  const TEST_SET = 'TDM';
  process.stdout.write(`\n=== build-booster.mjs --test ===\n`);
  process.stdout.write(`Running smoke test for ${TEST_SET}…\n\n`);

  const failures = [];

  let result;
  try {
    result = await buildSet(TEST_SET);
  } catch (err) {
    failures.push(`Pipeline error: ${err.message}`);
    printReport(failures);
    process.exit(1);
  }

  // (a) Zero unresolved scryfallIds — already enforced by the builder (abort on >5%)
  //     Additionally verify no card has an empty id string
  for (const [sheetName, sheet] of Object.entries(result.sheets)) {
    const emptyIds = sheet.cards.filter(c => !c.id || c.id.trim() === '').length;
    if (emptyIds > 0) {
      failures.push(`Sheet '${sheetName}' has ${emptyIds} card(s) with empty scryfallId.`);
    }
  }

  // (b) 'play' key was selected
  if (!result._meta?.source?.includes('play')) {
    failures.push(`Expected booster source to contain 'play', got: ${result._meta?.source}`);
  }

  // (c) Each sheet has at least 1 card
  for (const [sheetName, sheet] of Object.entries(result.sheets)) {
    if (sheet.cards.length === 0) {
      failures.push(`Sheet '${sheetName}' has 0 cards.`);
    }
  }

  // (d) All variant weights are positive integers
  for (let i = 0; i < result.boosters.length; i++) {
    const w = result.boosters[i].weight;
    if (!Number.isInteger(w) || w <= 0) {
      failures.push(`Variant[${i}].weight = ${w} is not a positive integer.`);
    }
  }

  // (e) Schema version is 1
  if (result.schemaVersion !== SCHEMA_VERSION) {
    failures.push(`schemaVersion = ${result.schemaVersion}, expected ${SCHEMA_VERSION}.`);
  }

  // (f) setCode is lowercase
  if (result.setCode !== TEST_SET.toLowerCase()) {
    failures.push(`setCode = '${result.setCode}', expected '${TEST_SET.toLowerCase()}'.`);
  }

  printReport(failures);
  if (failures.length > 0) process.exit(1);

  process.stdout.write(`\nAll tests passed.\n`);
  process.exit(0);
}

function printReport(failures) {
  if (failures.length === 0) return;
  process.stdout.write(`\nTest failures:\n`);
  for (const f of failures) {
    process.stdout.write(`  FAIL: ${f}\n`);
  }
}

// ---------------------------------------------------------------------------
// Entry point
// ---------------------------------------------------------------------------

const args = process.argv.slice(2);

if (args[0] === '--test') {
  runTests().catch(err => {
    process.stderr.write(`Unhandled error: ${err.message}\n`);
    process.exit(1);
  });
} else if (args.length === 1) {
  buildSet(args[0]).catch(err => {
    process.stderr.write(`Error: ${err.message}\n`);
    process.exit(1);
  });
} else {
  process.stderr.write(
    `Usage:\n` +
    `  node build-booster.mjs <SET_CODE>   Build booster.json for a set (e.g. TDM)\n` +
    `  node build-booster.mjs --test       Smoke-test using TDM\n`
  );
  process.exit(1);
}
