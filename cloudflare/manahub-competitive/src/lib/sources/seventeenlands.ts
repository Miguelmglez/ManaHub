import { fetchHeaders } from "../etiquette";
import type { LimitedCardRating, SeventeenLandsCardRatingRaw, SourceResult } from "../../types";

export interface SeventeenLandsDeps {
  fetchImpl: typeof fetch;
}

export const defaultSeventeenLandsDeps: SeventeenLandsDeps = {
  // Wrapped, not a bare reference — see manahub-community's project memory on `this`-binding
  // pitfalls when a raw `fetch` function value is stored as an object property.
  fetchImpl: (...args: Parameters<typeof fetch>) => fetch(...args),
};

/**
 * Fetches per-card Limited ratings from 17lands' public (undocumented but long-stable, used by
 * every community draft tool) card_ratings endpoint. No API key required. Real, working source —
 * see docs/competitive-feature-research.md §3a.
 */
export async function fetchLimitedRatings(
  expansion: string,
  format: string,
  deps: SeventeenLandsDeps = defaultSeventeenLandsDeps,
): Promise<SourceResult<LimitedCardRating[]>> {
  const params = new URLSearchParams({ expansion: expansion.toUpperCase(), format });
  const url = `https://www.17lands.com/card_ratings/data?${params.toString()}`;

  let response: Response;
  try {
    response = await deps.fetchImpl(url, { headers: fetchHeaders() });
  } catch (e) {
    return { ok: false, skipped: false, error: `17lands fetch threw: ${(e as Error).message}` };
  }
  if (!response.ok) {
    return { ok: false, skipped: false, error: `17lands fetch failed: HTTP ${response.status}` };
  }

  let raw: SeventeenLandsCardRatingRaw[];
  try {
    raw = (await response.json()) as SeventeenLandsCardRatingRaw[];
  } catch (e) {
    return { ok: false, skipped: false, error: `17lands response was not valid JSON: ${(e as Error).message}` };
  }
  if (!Array.isArray(raw)) {
    return { ok: false, skipped: false, error: "17lands response was not an array" };
  }

  return { ok: true, data: raw.map(normalizeRating) };
}

function normalizeRating(raw: SeventeenLandsCardRatingRaw): LimitedCardRating {
  return {
    name: raw.name,
    color: raw.color ?? "",
    rarity: raw.rarity ?? "",
    avgSeen: toNumberOrNull(raw.avg_seen),
    avgPick: toNumberOrNull(raw.avg_pick),
    playRatePct: toPercentOrNull(raw.play_rate),
    gpWinRatePct: toPercentOrNull(raw.win_rate),
    ohWinRatePct: toPercentOrNull(raw.opening_hand_win_rate),
    gdWinRatePct: toPercentOrNull(raw.drawn_win_rate),
    gihWinRatePct: toPercentOrNull(raw.ever_drawn_win_rate),
    iwdPct: toPercentOrNull(raw.drawn_improvement_win_rate),
    sampleSize: toNumberOrNull(raw.game_count),
    imageUrl: raw.url ?? null,
  };
}

function toNumberOrNull(value: number | undefined): number | null {
  return typeof value === "number" && Number.isFinite(value) ? value : null;
}

/** 17lands returns win-rate style fields as a 0..1 fraction; convert to a 0..100 percentage. */
function toPercentOrNull(value: number | undefined): number | null {
  return typeof value === "number" && Number.isFinite(value) ? value * 100 : null;
}
