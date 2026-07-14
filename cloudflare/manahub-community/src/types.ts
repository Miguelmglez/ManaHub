/**
 * Shared types for the manahub-community Worker.
 *
 * See docs/adr/ADR-004-community-api-contracts.md for the verified upstream shapes this
 * file is derived from, and docs/claude-code-prompt-deck-doctor-community.md Phase 3 for
 * the target snapshot contract the client (CommunityAggregateApi, commonMain) expects.
 */

export interface Env {
  COMMUNITY_KV: KVNamespace;
  COMMUNITY_DB: D1Database;
}

/** Cloudflare KV envelope wrapping any cached snapshot with its capture time. */
export interface CacheEnvelope<T> {
  data: T;
  cachedAt: number;
}

// ---------------------------------------------------------------------------------------
// Archidekt (verified shapes — ADR-004 §1/§2)
// ---------------------------------------------------------------------------------------

export interface ArchidektOwner {
  id: number;
  username: string;
  avatar: string;
}

export interface ArchidektTag {
  id: number;
  tag: number;
  deck: number;
  name: string;
  position: string;
}

export interface ArchidektDeckSummary {
  id: number;
  name: string;
  size: number;
  deckFormat: number;
  owner: ArchidektOwner | null;
  viewCount: number;
  createdAt: string;
  updatedAt: string;
  colors: Record<string, number>;
  tags: ArchidektTag[];
  edhBracket: number | null;
  private: boolean;
  unlisted: boolean;
  theorycrafted: boolean;
}

export interface ArchidektSearchResponse {
  count: number;
  next: string | null;
  results: ArchidektDeckSummary[];
  /** Only present when Archidekt's statement timeout fires (count === -1). */
  message?: string;
}

export interface ArchidektOracleCard {
  id: number;
  uid: string;
  name: string;
  cmc: number;
  /** Full color names ("Blue", "Green", ...) per ADR-004 §2 — NOT WUBRG letters. */
  colorIdentity: string[];
  colors: string[];
  manaCost: string;
  types: string[];
  text: string;
  legalities: Record<string, string | null>;
}

export interface ArchidektCardEntry {
  quantity: number;
  categories: string[] | null;
  card: { uid: string; oracleCard: ArchidektOracleCard | null } | null;
}

export interface ArchidektDeckDetail {
  id: number;
  name: string;
  deckFormat: number;
  edhBracket: number | null;
  private: boolean;
  unlisted: boolean;
  theorycrafted: boolean;
  deckTags: ArchidektTag[];
  owner: ArchidektOwner | null;
  viewCount: number;
  createdAt: string;
  updatedAt: string;
  cards: ArchidektCardEntry[];
}

// ---------------------------------------------------------------------------------------
// EDHREC (verified shapes — ADR-004 §3)
// ---------------------------------------------------------------------------------------

export interface EdhrecCardview {
  id: string;
  name: string;
  sanitized: string;
  slug: string;
  url: string;
  synergy: number;
  num_decks: number;
  potential_decks: number;
  trend_zscore: number;
}

export interface EdhrecCardlist {
  tag: string;
  header: string;
  cardviews: EdhrecCardview[];
}

export interface EdhrecCommanderCard {
  num_decks: number;
  rank: number;
  cmc: number;
  color_identity: string[];
  name: string;
  type_line: string;
  combos: boolean;
  is_commander: boolean;
  legal_commander: boolean;
  precon: string | null;
}

export interface EdhrecRankPoint {
  commander_count: number;
  perc_of_decks_overall: number;
  perc_of_decks_overall_ma: number;
  rank: number;
  rank_ma: number;
}

export interface EdhrecCommanderPayload {
  header: string;
  creature: number;
  instant: number;
  sorcery: number;
  artifact: number;
  enchantment: number;
  battle: number;
  planeswalker: number;
  land: number;
  basic: number;
  nonbasic: number;
  similar: string[];
  bracket_counts: Record<string, number>;
  budget_counts: Record<string, number>;
  tag_counts: Record<string, number>;
  container: {
    json_dict: {
      card: EdhrecCommanderCard;
      cardlists: EdhrecCardlist[];
    };
  };
  panels: {
    mana_curve: Record<string, number>;
    rank_over_time: Record<string, EdhrecRankPoint>;
    taglinks: { count: number; slug: string; value: string }[];
  };
}

// ---------------------------------------------------------------------------------------
// Normalized snapshot contract (what the Worker returns to the client, both formats)
// ---------------------------------------------------------------------------------------

export interface AggregateCardEntry {
  name: string;
  scryfallUid: string | null;
  /** 0..1 fraction of sampled decks including this card. */
  inclusionPct: number;
  /** Staple-dampened synergy score (EDHREC-native for Commander, computed for 60-card). */
  synergy: number;
  category: string;
  numDecks: number;
}

export type BuildStatus = "materialized" | "building";

export interface CommanderAggregateSnapshot {
  status: "materialized";
  source: "edhrec";
  commander: string;
  numDecksSampled: number;
  avgTypeDistribution: {
    creature: number;
    instant: number;
    sorcery: number;
    artifact: number;
    enchantment: number;
    battle: number;
    planeswalker: number;
    land: number;
  };
  manaCurve: Record<string, number>;
  themeTags: { name: string; count: number }[];
  similarCommanders: string[];
  gameChangersCount: number;
  cards: AggregateCardEntry[];
  cachedAt: number;
}

export interface SixtyAggregateSnapshotMaterialized {
  status: "materialized";
  source: "archidekt";
  canonicalKey: string;
  format: number;
  numDecksSampled: number;
  deckSummaries: {
    id: number;
    name: string;
    owner: string;
    viewCount: number;
    colors: Record<string, number>;
    edhBracket: number | null;
  }[];
  colorProfile: Record<string, number>;
  cards: AggregateCardEntry[];
  cachedAt: number;
}

export interface SixtyAggregateSnapshotBuilding {
  status: "building";
  source: "archidekt";
  canonicalKey: string;
  progress: { collected: number; target: number };
}

export type SixtyAggregateSnapshot =
  | SixtyAggregateSnapshotMaterialized
  | SixtyAggregateSnapshotBuilding;

export interface SimilarDecksResponse {
  status: "ok";
  commander: string;
  similar: string[];
}

export interface TrendingResponse {
  status: "ok";
  week: string;
  topCommanders: { name: string; count: number }[];
  topCards: { name: string; count: number }[];
}

export interface ErrorResponse {
  status: "error";
  message: string;
}
