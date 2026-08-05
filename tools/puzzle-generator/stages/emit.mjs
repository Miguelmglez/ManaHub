/**
 * emit.mjs
 *
 * Builds a GUESS_CARD puzzle document for a chosen answer card and writes the day's `{date}.json`
 * batch file to the local output dir (uploaded to R2 by generate.mjs's upload step, never
 * committed -- see .gitignore).
 */

import { randomBytes, createHash } from 'node:crypto';
import { mkdirSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { normalizeCardName } from './normalize.mjs';

const SCHEMA_VERSION = 1;
const DEFAULT_MAX_GUESSES = 7;

/**
 * @param {string} date ISO yyyy-MM-dd
 * @param {object} card a Scryfall oracle_cards entry that passed candidates + typeGate
 * @returns {object} the puzzle document, ready for schemaValidate.mjs
 */
export function buildGuessCardDocument(date, card) {
  const dailySalt = randomBytes(16).toString('hex');
  const normalizedName = normalizeCardName(card.name);
  const answerNameHash = createHash('sha256').update(`${normalizedName}:${dailySalt}`).digest('hex');

  const artCard = card.image_uris ? card : card.card_faces[0];

  return {
    schemaVersion: SCHEMA_VERSION,
    date,
    type: 'GUESS_CARD',
    payload: {
      answerNameHash,
      dailySalt,
      canonicalScryfallId: card.id,
      maxGuesses: DEFAULT_MAX_GUESSES,
      attributes: {
        cmc: card.cmc,
        colorIdentity: card.color_identity,
        rarity: card.rarity,
        typeLine: card.type_line,
        setCode: card.set,
        releasedYear: Number(card.released_at.slice(0, 4)),
        power: card.power ?? artCard.power ?? null,
        toughness: card.toughness ?? artCard.toughness ?? null,
      },
    },
  };
}

/**
 * @param {string} outputDir
 * @param {string} date
 * @param {object} document validated puzzle document
 */
export function writeDocumentFile(outputDir, date, document) {
  mkdirSync(outputDir, { recursive: true });
  const file = join(outputDir, `${date}.json`);
  writeFileSync(file, JSON.stringify(document, null, 2), 'utf-8');
  console.error(`emit: wrote ${file}`);
}
