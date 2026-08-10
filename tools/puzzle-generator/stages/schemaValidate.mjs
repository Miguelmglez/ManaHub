/**
 * schemaValidate.mjs
 *
 * Hand-rolled validator for the puzzle document contract -- no ajv/schema-validation library.
 * This repo's Node tooling (tools/collation, tools/embedding-generator) has zero npm
 * dependencies anywhere; adding one here would be the first dependency in the whole tree and
 * would break the "just run `node script.mjs`" ergonomics every other tool relies on. A single,
 * small, stable schema shape doesn't need more than descriptive thrown Errors -- the same
 * hand-rolled-assertion convention build-booster.mjs already uses.
 *
 * The SAME validator runs over manually-authored batches and generator output alike (design doc
 * §5.1: "same gate for humans and machines").
 */

const SCHEMA_VERSION = 1;

function assert(condition, message) {
  if (!condition) throw new Error(`Invalid puzzle document: ${message}`);
}

function assertString(value, field) {
  assert(typeof value === 'string' && value.length > 0, `"${field}" must be a non-empty string`);
}

function assertNumber(value, field) {
  assert(typeof value === 'number' && Number.isFinite(value), `"${field}" must be a finite number`);
}

/**
 * @param {object} doc a candidate puzzle document, e.g. `{schemaVersion, date, type, payload}`
 * @throws {Error} descriptive error on the first violation found
 */
export function validatePuzzleDocument(doc) {
  assert(doc && typeof doc === 'object', 'document must be an object');
  assert(doc.schemaVersion === SCHEMA_VERSION, `"schemaVersion" must be ${SCHEMA_VERSION}, got ${doc.schemaVersion}`);
  assertString(doc.date, 'date');
  assert(/^\d{4}-\d{2}-\d{2}$/.test(doc.date), `"date" must be ISO yyyy-MM-dd, got "${doc.date}"`);
  assertString(doc.type, 'type');
  assert(doc.payload && typeof doc.payload === 'object', '"payload" must be an object');

  if (doc.type === 'GUESS_CARD') {
    validateGuessCardPayload(doc.payload);
  }
  // Unknown types are intentionally NOT rejected here -- the client contract degrades
  // gracefully on an unrecognized `type` (PuzzleType.fromWire -> UNKNOWN), so the generator only
  // enforces the shape it knows about. A brand-new type shipped without updating this validator
  // simply skips payload-shape checks rather than blocking publication.
}

function validateGuessCardPayload(payload) {
  assertString(payload.answerNameHash, 'payload.answerNameHash');
  assert(/^[0-9a-f]{64}$/.test(payload.answerNameHash), 'payload.answerNameHash must be a 64-char lowercase hex SHA-256 digest');
  assertString(payload.dailySalt, 'payload.dailySalt');
  assertString(payload.canonicalScryfallId, 'payload.canonicalScryfallId');
  assertNumber(payload.maxGuesses, 'payload.maxGuesses');
  assert(payload.maxGuesses >= 1 && payload.maxGuesses <= 20, 'payload.maxGuesses must be in [1, 20]');

  const attrs = payload.attributes;
  assert(attrs && typeof attrs === 'object', 'payload.attributes must be an object');
  assertNumber(attrs.cmc, 'payload.attributes.cmc');
  assert(Array.isArray(attrs.colorIdentity), 'payload.attributes.colorIdentity must be an array');
  assertString(attrs.rarity, 'payload.attributes.rarity');
  assertString(attrs.typeLine, 'payload.attributes.typeLine');
  assertString(attrs.setCode, 'payload.attributes.setCode');
  assertNumber(attrs.releasedYear, 'payload.attributes.releasedYear');
  assert(
    attrs.power === null || typeof attrs.power === 'string',
    'payload.attributes.power must be a string or null',
  );
  assert(
    attrs.toughness === null || typeof attrs.toughness === 'string',
    'payload.attributes.toughness must be a string or null',
  );
}
