/**
 * r2Index.mjs
 *
 * Reads/writes the private `puzzle/index.json` R2 object directly via `wrangler r2 object
 * get/put`, reusing the developer's own `wrangler login` session -- the exact same
 * zero-new-credentials pattern already used by scripts/draftsim/upload_to_r2.mjs and
 * tools/embedding-generator. No R2 S3 API keys, no .env file.
 *
 * historyDedup.mjs needs the FULL `answerHistory` array, which the public Worker route
 * (`GET /puzzle/index`) deliberately strips before responding -- so this reads the R2 object
 * directly rather than going through the Worker.
 */

import { spawnSync } from 'node:child_process';
import { existsSync, readFileSync, unlinkSync, mkdtempSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { tmpdir } from 'node:os';

const BUCKET = 'manahub-assets';
const INDEX_KEY = 'puzzle/index.json';

const EMPTY_INDEX = { schemaVersion: 1, entries: [], answerHistory: [] };

/** @returns {{schemaVersion:number, entries:Array<{date:string,type:string}>, answerHistory:Array<{date:string,normalizedName:string}>}} */
export function fetchIndex() {
  const tmpDir = mkdtempSync(join(tmpdir(), 'manahub-puzzle-index-'));
  const tmpFile = join(tmpDir, 'index.json');
  try {
    const r = spawnSync(
      'npx',
      ['wrangler', 'r2', 'object', 'get', `${BUCKET}/${INDEX_KEY}`, '--file', tmpFile, '--remote'],
      { stdio: ['ignore', 'pipe', 'pipe'], shell: process.platform === 'win32' },
    );
    if (r.status !== 0 || !existsSync(tmpFile)) {
      console.error('r2Index: no existing index.json in R2 yet -- starting from an empty index.');
      return { ...EMPTY_INDEX };
    }
    const parsed = JSON.parse(readFileSync(tmpFile, 'utf-8'));
    return {
      schemaVersion: parsed.schemaVersion ?? 1,
      entries: Array.isArray(parsed.entries) ? parsed.entries : [],
      answerHistory: Array.isArray(parsed.answerHistory) ? parsed.answerHistory : [],
    };
  } finally {
    if (existsSync(tmpFile)) unlinkSync(tmpFile);
  }
}

/**
 * Writes the updated index to a local file for the emit/upload stages -- does NOT upload itself,
 * so the caller controls when the actual R2 write happens (after schema validation of the new
 * batch, alongside the new day files, in `generate.mjs`).
 * @param {string} outputPath
 * @param {object} index
 */
export function writeIndexFile(outputPath, index) {
  writeFileSync(outputPath, JSON.stringify(index, null, 2), 'utf-8');
}
