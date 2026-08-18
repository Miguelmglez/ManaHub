/**
 * snapshot.mjs
 *
 * Downloads and disk-caches the Scryfall `oracle_cards` bulk data file, pinned by the calendar
 * date the pipeline run started on -- re-running the generator multiple times on the same day
 * reuses the cached file (the bulk file is tens of MB; no need to re-fetch it per invocation),
 * and pinning by date makes a given day's generated batch reproducible.
 *
 * Cache dir is gitignored (tools/puzzle-generator/.cache/) -- same rationale as
 * tools/tag-pipeline/.cache/ (large downloaded Scryfall data must never be committed).
 */

import { mkdirSync, existsSync, readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { gunzipSync } from 'node:zlib';

const BULK_DATA_INDEX_URL = 'https://api.scryfall.com/bulk-data';
const USER_AGENT = 'ManaHubPuzzleGenerator/1 (+https://github.com/manahub)';

/**
 * @param {string} cacheDir
 * @param {string} pinnedDate ISO yyyy-MM-dd, the date to pin this snapshot's filename to
 * @returns {Promise<Array<object>>} the parsed oracle_cards array
 */
export async function loadOracleCardsSnapshot(cacheDir, pinnedDate) {
  mkdirSync(cacheDir, { recursive: true });
  const cacheFile = join(cacheDir, `oracle_cards_${pinnedDate}.json`);

  if (existsSync(cacheFile)) {
    console.error(`snapshot: using cached ${cacheFile}`);
    return JSON.parse(readFileSync(cacheFile, 'utf-8'));
  }

  console.error('snapshot: resolving oracle_cards bulk data download URL...');
  const indexRes = await fetch(BULK_DATA_INDEX_URL, { headers: { 'User-Agent': USER_AGENT } });
  if (!indexRes.ok) throw new Error(`HTTP ${indexRes.status} fetching ${BULK_DATA_INDEX_URL}`);
  const index = await indexRes.json();
  const entry = index.data?.find((d) => d.type === 'oracle_cards');
  // Scryfall is standardizing on `jsonl_download_uri` (gzip'd JSON-Lines) and no longer always
  // populates the legacy `download_uri` (plain JSON-array) field -- mirrors the same fallback
  // order already used by tools/tag-pipeline's ScryfallBulkClient.kt.
  const jsonlUri = entry?.jsonl_download_uri;
  const legacyUri = entry?.download_uri;
  if (!jsonlUri && !legacyUri) throw new Error('oracle_cards entry not found in bulk-data index');

  let cards;
  if (jsonlUri) {
    console.error(`snapshot: downloading ${jsonlUri} ...`);
    const dataRes = await fetch(jsonlUri, { headers: { 'User-Agent': USER_AGENT } });
    if (!dataRes.ok) throw new Error(`HTTP ${dataRes.status} fetching ${jsonlUri}`);
    const gz = Buffer.from(await dataRes.arrayBuffer());
    const jsonl = gunzipSync(gz).toString('utf-8');
    cards = jsonl
      .split('\n')
      .filter((line) => line.trim().length > 0)
      .map((line) => JSON.parse(line));
  } else {
    console.error(`snapshot: downloading ${legacyUri} ...`);
    const dataRes = await fetch(legacyUri, { headers: { 'User-Agent': USER_AGENT } });
    if (!dataRes.ok) throw new Error(`HTTP ${dataRes.status} fetching ${legacyUri}`);
    cards = JSON.parse(await dataRes.text());
  }

  const text = JSON.stringify(cards);
  writeFileSync(cacheFile, text, 'utf-8');
  console.error(`snapshot: cached ${cacheFile} (${(text.length / 1_000_000).toFixed(1)} MB, ${cards.length} cards)`);

  return cards;
}
