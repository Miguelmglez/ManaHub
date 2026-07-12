import { classifyFreshness, getCached, putCached, AGGREGATE_TTL_MS } from "../lib/cache";
import { fetchHeaders, USER_AGENT } from "../lib/etiquette";
import { normalizeCommanderAggregate, slugifyCommanderName } from "../lib/edhrec";
import { incrementCounter, isoWeek } from "../lib/trending";
import type { CommanderAggregateSnapshot, EdhrecCommanderPayload, Env } from "../types";

export interface CommanderAggregateDeps {
  fetchImpl: typeof fetch;
  now: () => number;
}

export const defaultDeps: CommanderAggregateDeps = {
  // Wrapped (not a bare `fetch` reference) — assigning the global `fetch` function directly
  // as an object property detaches it from its required implicit receiver. Calling it later as
  // `deps.fetchImpl(...)` then invokes it with `this` bound to `deps`, which workerd's real
  // `fetch` rejects with `TypeError: Illegal invocation` (Node's fetch tolerates this, so
  // Miniflare/vitest never caught it locally — see project memory
  // `project_community_aggregate_worker.md`).
  fetchImpl: (...args: Parameters<typeof fetch>) => fetch(...args),
  now: () => Date.now(),
};

/**
 * Handles `GET /v1/aggregate?commander=<name>&format=<fmt>` (Commander path, EDHREC-backed
 * per D16). KV cache key `agg:commander:<slug>`, 7-day TTL, stale-while-revalidate — a stale
 * hit is served immediately and a background refresh is kicked off (best-effort; failures
 * are swallowed so a flaky upstream never breaks an otherwise-servable stale response).
 */
export async function handleCommanderAggregate(
  commanderName: string,
  env: Env,
  deps: CommanderAggregateDeps = defaultDeps,
): Promise<CommanderAggregateSnapshot> {
  const slug = slugifyCommanderName(commanderName);
  const key = `agg:commander:${slug}`;
  const now = deps.now();

  const cached = await getCached<CommanderAggregateSnapshot>(env.COMMUNITY_KV, key);
  const freshness = classifyFreshness(cached, now);

  if (freshness === "fresh") {
    // Every request (fresh or otherwise) increments the anonymous weekly trending counter.
    await recordTrending(env, commanderName, deps);
    return cached!.data;
  }

  if (freshness === "stale" && cached) {
    await recordTrending(env, commanderName, deps);
    // Best-effort background revalidation — never let a refresh failure break the stale
    // response that's about to be returned to the caller.
    fetchAndStore(slug, commanderName, env, deps, now).catch(() => undefined);
    return cached.data;
  }

  // missing or expired: fetch synchronously.
  const snapshot = await fetchAndStore(slug, commanderName, env, deps, now);
  await recordTrending(env, commanderName, deps);
  return snapshot;
}

async function fetchAndStore(
  slug: string,
  commanderName: string,
  env: Env,
  deps: CommanderAggregateDeps,
  now: number,
): Promise<CommanderAggregateSnapshot> {
  const response = await deps.fetchImpl(`https://json.edhrec.com/pages/commanders/${slug}.json`, {
    headers: fetchHeaders(),
  });
  if (!response.ok) {
    throw new Error(`EDHREC fetch failed for "${commanderName}": HTTP ${response.status}`);
  }
  const payload = (await response.json()) as EdhrecCommanderPayload;
  const snapshot = normalizeCommanderAggregate(commanderName, payload, now);
  await putCached(env.COMMUNITY_KV, `agg:commander:${slug}`, snapshot, now, AGGREGATE_TTL_MS / 1000);
  return snapshot;
}

async function recordTrending(
  env: Env,
  commanderName: string,
  deps: CommanderAggregateDeps,
): Promise<void> {
  try {
    await incrementCounter(env.COMMUNITY_DB, isoWeek(new Date(deps.now())), "commander", commanderName);
  } catch {
    // Trending is a nice-to-have signal — never fail the aggregate request over a D1 hiccup.
  }
}

// Re-exported so index.ts doesn't need to import etiquette directly just for the constant.
export { USER_AGENT };
