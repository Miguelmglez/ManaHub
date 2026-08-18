import { classifyFreshness, getCached, putCached, LIMITED_TTL_MS } from "../lib/cache";
import { defaultSeventeenLandsDeps, fetchLimitedRatings, type SeventeenLandsDeps } from "../lib/sources/seventeenlands";
import type { Env, LimitedRatingsSnapshot } from "../types";

export interface LimitedDeps {
  now: () => number;
  seventeenLands: SeventeenLandsDeps;
}

export const defaultLimitedDeps: LimitedDeps = {
  now: () => Date.now(),
  seventeenLands: defaultSeventeenLandsDeps,
};

/**
 * Handles `GET /limited/{set}/ratings`. KV cache key `limited:<set>:<format>`, 24h TTL
 * (17lands refreshes daily), stale-while-revalidate. Real, working source from day one — see
 * docs/competitive-feature-research.md §3a.
 */
export async function handleLimitedRatings(
  expansion: string,
  format: string,
  env: Env,
  deps: LimitedDeps = defaultLimitedDeps,
): Promise<LimitedRatingsSnapshot> {
  const key = `limited:${expansion.toLowerCase()}:${format}`;
  const now = deps.now();

  const cached = await getCached<LimitedRatingsSnapshot>(env.COMPETITIVE_KV, key);
  const freshness = classifyFreshness(cached, now, LIMITED_TTL_MS);

  if (freshness === "fresh") return cached!.data;
  if (freshness === "stale" && cached) {
    fetchAndStore(expansion, format, env, deps, now).catch(() => undefined);
    return cached.data;
  }
  return fetchAndStore(expansion, format, env, deps, now);
}

async function fetchAndStore(
  expansion: string,
  format: string,
  env: Env,
  deps: LimitedDeps,
  now: number,
): Promise<LimitedRatingsSnapshot> {
  const key = `limited:${expansion.toLowerCase()}:${format}`;
  const result = await fetchLimitedRatings(expansion, format, deps.seventeenLands);
  if (!result.ok) {
    const reason = result.skipped ? result.reason : result.error;
    throw new Error(`17lands fetch failed for "${expansion}": ${reason}`);
  }

  const snapshot: LimitedRatingsSnapshot = {
    status: "ok",
    source: "17lands",
    attribution: "Data: 17Lands.com",
    expansion: expansion.toUpperCase(),
    format,
    cards: result.data,
    cachedAt: now,
  };

  await putCached(env.COMPETITIVE_KV, key, snapshot, now, LIMITED_TTL_MS / 1000);
  return snapshot;
}
