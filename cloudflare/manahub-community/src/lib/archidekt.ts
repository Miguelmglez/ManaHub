import { mapColorNamesToLetters } from "./colorMap";
import type {
  ArchidektDeckDetail,
  ArchidektDeckSummary,
  ArchidektSearchResponse,
} from "../types";

/**
 * Archidekt signals a server-side statement timeout with `count: -1` and an empty result
 * set (verified live 2026-07-11 — reliably triggered by a single popular cardName, e.g.
 * "Sol Ring"; a combined multi-cardName query times out even more reliably — ADR-004 §1).
 */
export function isTimeout(response: ArchidektSearchResponse): boolean {
  return response.count < 0;
}

export interface SanitizeOptions {
  minSize: number;
  maxSize: number;
  viewCountFloor: number;
}

/** 60-card sanitization band per Phase 3.2. */
export const SIXTY_CARD_SANITIZE: SanitizeOptions = {
  minSize: 60,
  maxSize: 80,
  viewCountFloor: 1,
};

/** Commander sanitization band per Phase 3.2. */
export const COMMANDER_SANITIZE: SanitizeOptions = {
  minSize: 99,
  maxSize: 110,
  viewCountFloor: 1,
};

/**
 * Filters raw Archidekt deck summaries down to decks worth aggregating: excludes private,
 * theorycrafted, and cube/pile decks outside the expected size band, and requires at least
 * [viewCountFloor] views as a weak "someone besides the owner looked at this" signal.
 */
export function sanitizeSummaries(
  summaries: readonly ArchidektDeckSummary[],
  opts: SanitizeOptions,
): ArchidektDeckSummary[] {
  return summaries.filter(
    (deck) =>
      !deck.private &&
      !deck.theorycrafted &&
      deck.size >= opts.minSize &&
      deck.size <= opts.maxSize &&
      deck.viewCount >= opts.viewCountFloor,
  );
}

/**
 * Intersects deck-id sets from multiple per-signature-card searches to approximate an AND
 * across signature cards (Archidekt's own multi-cardName param times out — ADR-004 §1 —
 * so the Worker issues one search per card and intersects here instead).
 *
 * Returns the intersection preserving the first list's relative ordering. An empty input
 * list of searches returns an empty result (never "everything").
 */
export function intersectById(
  searches: readonly ArchidektDeckSummary[][],
): ArchidektDeckSummary[] {
  if (searches.length === 0) return [];
  const first = searches[0]!;
  const rest = searches.slice(1);
  if (rest.length === 0) return [...first];
  const restIdSets = rest.map((list) => new Set(list.map((d) => d.id)));
  return first.filter((deck) => restIdSets.every((ids) => ids.has(deck.id)));
}

/**
 * Picks the [count] most distinctive signature cards from a mainboard — lowest global
 * frequency first, deterministic ordering (ties broken alphabetically) — used to build the
 * canonical cache key so same-archetype decks share a snapshot (Phase 3.2).
 */
export function pickSignatureCards(
  mainboardCardNames: readonly string[],
  globalFrequency: ReadonlyMap<string, number>,
  count = 3,
): string[] {
  const unique = Array.from(new Set(mainboardCardNames));
  const sorted = unique.slice().sort((a, b) => {
    const freqA = globalFrequency.get(a) ?? 0;
    const freqB = globalFrequency.get(b) ?? 0;
    if (freqA !== freqB) return freqA - freqB;
    return a.localeCompare(b);
  });
  return sorted.slice(0, count);
}

/** Builds the deterministic canonical cache key from a set of signature cards. */
export function buildCanonicalKey(signatureCards: readonly string[]): string {
  return signatureCards
    .slice()
    .sort((a, b) => a.localeCompare(b))
    .map((name) => name.toLowerCase().replace(/[^a-z0-9]+/g, "-"))
    .join("+");
}

/** Maps an Archidekt oracle card's full-name color identity to WUBRG letters (ADR-004 §2). */
export function oracleColorIdentity(
  card: ArchidektDeckDetail["cards"][number],
): string[] {
  const identity = card.card?.oracleCard?.colorIdentity ?? [];
  return mapColorNamesToLetters(identity);
}
