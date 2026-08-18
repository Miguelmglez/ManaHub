/**
 * Shared types for the manahub-competitive Worker.
 *
 * See docs/competitive-feature-research.md for the source landscape (legal status, contracts,
 * rate limits) this file is derived from.
 */

export interface Env {
  COMPETITIVE_KV: KVNamespace;
  COMPETITIVE_DB: D1Database;
  /** TopDeck.gg developer API key. Absent until research-doc §7 action item 2 is done. */
  TOPDECK_API_KEY?: string;
  /** Spicerack org-admin API key. Absent until research-doc §7 action item 3 is done. */
  SPICERACK_API_KEY?: string;
  /** Daybreak Census service id (MTGO league data). Absent until §7 action item 1 is done. */
  DAYBREAK_SERVICE_ID?: string;
}

/** Cloudflare KV envelope wrapping any cached snapshot with its capture time. */
export interface CacheEnvelope<T> {
  data: T;
  cachedAt: number;
}

export const SUPPORTED_FORMATS = ["standard", "modern", "pioneer", "legacy", "vintage", "pauper"] as const;
export type SupportedFormat = (typeof SUPPORTED_FORMATS)[number];

// ---------------------------------------------------------------------------------------
// Per-source raw decklist contract (what each src/lib/sources/*.ts fetcher normalizes into)
// ---------------------------------------------------------------------------------------

export interface DecklistCard {
  name: string;
  quantity: number;
}

/** One tournament/league decklist, normalized to a common shape regardless of source. */
export interface NormalizedDecklist {
  source: "mtgo" | "topdeck" | "spicerack";
  /** Event name/slug the deck came from, for attribution/deep-linking. */
  eventName: string;
  eventUrl: string | null;
  player: string | null;
  format: SupportedFormat;
  /** Recorded finish, e.g. "5-0", "1st", "Top 8" — display-only. */
  result: string | null;
  colors: string[];
  cards: DecklistCard[];
}

/** Discriminated result every source fetcher returns — never throws for a "not configured" case. */
export type SourceResult<T> =
  | { ok: true; data: T }
  | { ok: false; skipped: true; reason: string }
  | { ok: false; skipped: false; error: string };

export interface SourceStatus {
  name: "mtgo" | "topdeck" | "spicerack" | "17lands";
  active: boolean;
  reason: string | null;
}

// ---------------------------------------------------------------------------------------
// 17lands Limited card ratings (real, live source)
// ---------------------------------------------------------------------------------------

/** Raw shape of one row from https://www.17lands.com/card_ratings/data (verified live, ADR-pending). */
export interface SeventeenLandsCardRatingRaw {
  name: string;
  color: string;
  rarity: string;
  avg_seen?: number;
  avg_pick?: number;
  play_rate?: number;
  win_rate?: number;
  opening_hand_win_rate?: number;
  drawn_win_rate?: number;
  ever_drawn_win_rate?: number;
  drawn_improvement_win_rate?: number;
  game_count?: number;
  url?: string;
}

export interface LimitedCardRating {
  name: string;
  color: string;
  rarity: string;
  /** Average Last Seen At (pack position). */
  avgSeen: number | null;
  /** Average Taken At (pick position). */
  avgPick: number | null;
  playRatePct: number | null;
  gpWinRatePct: number | null;
  ohWinRatePct: number | null;
  gdWinRatePct: number | null;
  gihWinRatePct: number | null;
  iwdPct: number | null;
  sampleSize: number | null;
  imageUrl: string | null;
}

export interface LimitedRatingsSnapshot {
  status: "ok";
  source: "17lands";
  attribution: "Data: 17Lands.com";
  expansion: string;
  format: string;
  cards: LimitedCardRating[];
  cachedAt: number;
}

// ---------------------------------------------------------------------------------------
// Weekly competitive-meta snapshot (normalized output of src/lib/archetype.ts)
// ---------------------------------------------------------------------------------------

export interface ArchetypeShare {
  key: string;
  label: string;
  colors: string[];
  keyCards: string[];
  deckCount: number;
  metaSharePct: number;
  /** Percentage-point change vs. the previous ISO week, null if no prior data exists. */
  deltaPct: number | null;
  /** One representative decklist for this archetype, wired to the netdeck-import CTA. */
  representativeDeck: NormalizedDecklist | null;
}

export interface TrendingCard {
  name: string;
  playRatePct: number;
  deltaPct: number | null;
}

export interface MetaSnapshot {
  status: "ok";
  format: SupportedFormat;
  week: string;
  numDecksSampled: number;
  archetypes: ArchetypeShare[];
  trendingCards: TrendingCard[];
  sources: SourceStatus[];
  attribution: string[];
  cachedAt: number;
}

export interface ErrorResponse {
  status: "error";
  message: string;
}
