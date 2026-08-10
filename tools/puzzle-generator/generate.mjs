#!/usr/bin/env node
/**
 * generate.mjs
 *
 * Daily Puzzle generator -- Phase 0 (GUESS_CARD only). Pipeline:
 *   snapshot -> candidates -> typeGate -> historyDedup -> emit -> schemaValidate -> upload
 *
 * Usage:
 *   node tools/puzzle-generator/generate.mjs --days 30              # generate + upload 30 days
 *   node tools/puzzle-generator/generate.mjs --days 3 --dry-run      # generate + validate only, no upload
 *   node tools/puzzle-generator/generate.mjs --from 2026-09-01 --days 7
 *
 * Requires `npx wrangler` authenticated for the manahub-assets bucket (same requirement as
 * scripts/draftsim/upload_to_r2.mjs) unless --dry-run is passed.
 *
 * Schema version: 1
 */

import { spawnSync } from 'node:child_process';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

import { loadOracleCardsSnapshot } from './stages/snapshot.mjs';
import { buildCandidatePool } from './stages/candidates.mjs';
import { passesGuessCardGate } from './stages/typeGate.mjs';
import { buildRecentAnswerSet, excludeRecentlyUsed } from './stages/historyDedup.mjs';
import { buildGuessCardDocument, writeDocumentFile } from './stages/emit.mjs';
import { validatePuzzleDocument } from './stages/schemaValidate.mjs';
import { fetchIndex, writeIndexFile } from './stages/r2Index.mjs';
import { normalizeCardName } from './stages/normalize.mjs';

const __dirname = dirname(fileURLToPath(import.meta.url));
const CACHE_DIR = join(__dirname, '.cache');
const OUTPUT_DIR = join(__dirname, 'output');
const BUCKET = 'manahub-assets';

function parseArgs(argv) {
  const args = { days: 1, from: new Date().toISOString().slice(0, 10), dryRun: false };
  for (let i = 0; i < argv.length; i++) {
    if (argv[i] === '--days') args.days = Number(argv[++i]);
    else if (argv[i] === '--from') args.from = argv[++i];
    else if (argv[i] === '--dry-run') args.dryRun = true;
  }
  if (!Number.isInteger(args.days) || args.days < 1) throw new Error('--days must be a positive integer');
  if (!/^\d{4}-\d{2}-\d{2}$/.test(args.from)) throw new Error('--from must be ISO yyyy-MM-dd');
  return args;
}

function addDays(isoDate, n) {
  const d = new Date(isoDate);
  d.setUTCDate(d.getUTCDate() + n);
  return d.toISOString().slice(0, 10);
}

function uploadFile(key, filePath) {
  console.error(`upload: ${key} <- ${filePath}`);
  const r = spawnSync(
    'npx',
    ['wrangler', 'r2', 'object', 'put', `${BUCKET}/${key}`, '--file', filePath, '--remote', '--content-type', 'application/json'],
    { stdio: ['ignore', 'inherit', 'inherit'], shell: process.platform === 'win32' },
  );
  if (r.status !== 0) throw new Error(`wrangler upload failed for ${key} (status ${r.status})`);
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const today = new Date().toISOString().slice(0, 10);

  const runDatePin = today; // snapshot cache key: pin to the day this run started, not --from
  const oracleCards = await loadOracleCardsSnapshot(CACHE_DIR, runDatePin);
  const candidates = buildCandidatePool(oracleCards).filter(passesGuessCardGate);
  console.error(`generate: ${candidates.length} candidates pass the GUESS_CARD type gate`);

  const index = fetchIndex();
  const existingDates = new Set(index.entries.map((e) => e.date));

  const documents = [];
  let pool = [...candidates];

  for (let i = 0; i < args.days; i++) {
    const date = addDays(args.from, i);

    if (existingDates.has(date)) {
      console.error(`generate: ${date} already published -- skipping (published puzzles are frozen forever)`);
      continue;
    }

    const recentAnswers = buildRecentAnswerSet(index.answerHistory, date);
    pool = excludeRecentlyUsed(pool, recentAnswers);

    if (pool.length === 0) {
      console.error(`generate: candidate pool exhausted before reaching ${date} -- stopping early`);
      break;
    }

    // Deterministic-enough pick: first remaining candidate after dedup filtering. Popularity
    // ordering already happened implicitly via the edhrec_rank ceiling in candidates.mjs; no
    // further ranking is needed for Phase 0.
    const chosen = pool.shift();
    const doc = buildGuessCardDocument(date, chosen);
    validatePuzzleDocument(doc);

    documents.push({ date, doc, cardName: chosen.name });
    index.entries.push({ date, type: 'GUESS_CARD' });
    index.answerHistory.push({ date, normalizedName: normalizeCardName(chosen.name) });
  }

  if (documents.length === 0) {
    console.error('generate: nothing new to generate.');
    return;
  }

  for (const { date, doc } of documents) {
    writeDocumentFile(OUTPUT_DIR, date, doc);
  }
  const indexFile = join(OUTPUT_DIR, 'index.json');
  writeIndexFile(indexFile, index);

  console.error(`generate: ${documents.length} new puzzle(s) generated:`);
  for (const { date, cardName } of documents) console.error(`  ${date} -> ${cardName}`);

  if (args.dryRun) {
    console.error('generate: --dry-run set, skipping upload.');
    return;
  }

  for (const { date } of documents) {
    uploadFile(`puzzle/${date}.json`, join(OUTPUT_DIR, `${date}.json`));
  }
  uploadFile('puzzle/index.json', indexFile);

  console.error('generate: done.');
}

main().catch((err) => {
  console.error(err.stack ?? String(err));
  process.exit(1);
});
