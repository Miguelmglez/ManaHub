import type { CacheEnvelope } from "../types";

/** 7-day KV TTL for aggregate snapshots (commander + 60-card), per Phase 3.2. */
export const AGGREGATE_TTL_MS = 7 * 24 * 60 * 60 * 1000;

/** 30-day KV TTL for individual per-deck minimal caches, per Phase 3.2. */
export const DECK_TTL_MS = 30 * 24 * 60 * 60 * 1000;

/** Grace window past the TTL during which a stale entry is still served (SWR). */
export const STALE_WHILE_REVALIDATE_MS = 24 * 60 * 60 * 1000;

export type Freshness = "fresh" | "stale" | "expired" | "missing";

/**
 * Classifies a cache envelope's freshness against `now`.
 * - fresh: within [ttlMs] — serve directly, no revalidation.
 * - stale: within [ttlMs, ttlMs + staleMs] — serve immediately AND trigger a background
 *   revalidation (stale-while-revalidate).
 * - expired: beyond the SWR grace window — must be refetched synchronously.
 * - missing: no envelope at all.
 */
export function classifyFreshness(
  envelope: CacheEnvelope<unknown> | null,
  now: number,
  ttlMs: number = AGGREGATE_TTL_MS,
  staleMs: number = STALE_WHILE_REVALIDATE_MS,
): Freshness {
  if (!envelope) return "missing";
  const age = now - envelope.cachedAt;
  if (age < ttlMs) return "fresh";
  if (age < ttlMs + staleMs) return "stale";
  return "expired";
}

/** Reads and JSON-parses a cache envelope from KV; returns null on miss or parse failure. */
export async function getCached<T>(
  kv: KVNamespace,
  key: string,
): Promise<CacheEnvelope<T> | null> {
  const raw = await kv.get(key);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as CacheEnvelope<T>;
  } catch {
    return null;
  }
}

/** Writes a value to KV wrapped in a [CacheEnvelope], stamped with [now]. */
export async function putCached<T>(
  kv: KVNamespace,
  key: string,
  data: T,
  now: number,
  expirationTtlSeconds?: number,
): Promise<void> {
  const envelope: CacheEnvelope<T> = { data, cachedAt: now };
  await kv.put(key, JSON.stringify(envelope), {
    expirationTtl: expirationTtlSeconds,
  });
}
