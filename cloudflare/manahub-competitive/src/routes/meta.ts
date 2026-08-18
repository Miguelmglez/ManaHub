import { classifyFreshness, getCached, putCached, META_TTL_MS } from "../lib/cache";
import { classifyArchetypes, computeCardPlayRates } from "../lib/archetype";
import { fetchMtgoDecklists } from "../lib/sources/mtgo";
import { fetchTopDeckDecklists } from "../lib/sources/topdeck";
import { fetchSpicerackDecklists } from "../lib/sources/spicerack";
import {
  getArchetypeWeekly,
  getCardPlayRateWeekly,
  isoWeek,
  previousIsoWeek,
  upsertArchetypeWeekly,
  upsertCardPlayRateWeekly,
} from "../lib/weekly";
import type { ArchetypeShare, Env, MetaSnapshot, NormalizedDecklist, SourceStatus, SupportedFormat, TrendingCard } from "../types";

export interface MetaDeps {
  now: () => number;
  fetchImpl: typeof fetch;
}

export const defaultMetaDeps: MetaDeps = {
  now: () => Date.now(),
  fetchImpl: (...args: Parameters<typeof fetch>) => fetch(...args),
};

/**
 * Handles `GET /meta/{format}/weekly`. KV cache key `meta:<format>:weekly`, 24h TTL,
 * stale-while-revalidate (mirrors manahub-community's handleCommanderAggregate). Gathers
 * decklists from every source (MTGO/TopDeck/Spicerack), classifies them into archetypes, and
 * reports each source's active/skipped status transparently so the client can render an honest
 * "no data yet" state rather than a silent empty list.
 */
export async function handleMetaWeekly(
  format: SupportedFormat,
  env: Env,
  deps: MetaDeps = defaultMetaDeps,
): Promise<MetaSnapshot> {
  const key = `meta:${format}:weekly`;
  const now = deps.now();

  const cached = await getCached<MetaSnapshot>(env.COMPETITIVE_KV, key);
  const freshness = classifyFreshness(cached, now, META_TTL_MS);

  if (freshness === "fresh") return cached!.data;
  if (freshness === "stale" && cached) {
    computeAndStore(format, env, deps, now).catch(() => undefined);
    return cached.data;
  }
  return computeAndStore(format, env, deps, now);
}

async function computeAndStore(
  format: SupportedFormat,
  env: Env,
  deps: MetaDeps,
  now: number,
): Promise<MetaSnapshot> {
  const key = `meta:${format}:weekly`;
  const week = isoWeek(new Date(now));

  const [mtgoResult, topdeckResult, spicerackResult] = await Promise.all([
    fetchMtgoDecklists(format),
    fetchTopDeckDecklists(format, env, deps.fetchImpl).catch(
      (e): { ok: false; skipped: false; error: string } => ({ ok: false, skipped: false, error: String(e) }),
    ),
    fetchSpicerackDecklists(format, env, deps.fetchImpl).catch(
      (e): { ok: false; skipped: false; error: string } => ({ ok: false, skipped: false, error: String(e) }),
    ),
  ]);

  const sources: SourceStatus[] = [
    toSourceStatus("mtgo", mtgoResult),
    toSourceStatus("topdeck", topdeckResult),
    toSourceStatus("spicerack", spicerackResult),
  ];

  const decks: NormalizedDecklist[] = [
    ...(mtgoResult.ok ? mtgoResult.data : []),
    ...(topdeckResult.ok ? topdeckResult.data : []),
    ...(spicerackResult.ok ? spicerackResult.data : []),
  ];

  const previousWeek = previousIsoWeek(new Date(now));
  let previousArchetypeShares = new Map<string, number>();
  let previousCardPlayRates = new Map<string, number>();
  try {
    [previousArchetypeShares, previousCardPlayRates] = await Promise.all([
      getArchetypeWeekly(env.COMPETITIVE_DB, previousWeek, format),
      getCardPlayRateWeekly(env.COMPETITIVE_DB, previousWeek, format),
    ]);
  } catch {
    // Week-over-week deltas are a nice-to-have — never fail the snapshot over a D1 hiccup.
  }

  const archetypes: ArchetypeShare[] = classifyArchetypes(decks).map((group) => ({
    ...group,
    deltaPct: previousArchetypeShares.has(group.key)
      ? round1(group.metaSharePct - previousArchetypeShares.get(group.key)!)
      : null,
  }));

  const trendingCards: TrendingCard[] = computeCardPlayRates(decks).map((card) => ({
    ...card,
    deltaPct: previousCardPlayRates.has(card.name)
      ? round1(card.playRatePct - previousCardPlayRates.get(card.name)!)
      : null,
  }));

  const attribution: string[] = [];
  if (topdeckResult.ok) attribution.push("Data: TopDeck.gg");
  if (spicerackResult.ok) attribution.push("Data: Spicerack.gg");
  if (mtgoResult.ok) attribution.push("Data from Magic Online");

  const snapshot: MetaSnapshot = {
    status: "ok",
    format,
    week,
    numDecksSampled: decks.length,
    archetypes,
    trendingCards,
    sources,
    attribution,
    cachedAt: now,
  };

  await putCached(env.COMPETITIVE_KV, key, snapshot, now, META_TTL_MS / 1000);
  await recordWeeklyHistory(env, week, format, archetypes, trendingCards);
  return snapshot;
}

async function recordWeeklyHistory(
  env: Env,
  week: string,
  format: SupportedFormat,
  archetypes: ArchetypeShare[],
  trendingCards: TrendingCard[],
): Promise<void> {
  try {
    for (const archetype of archetypes) {
      await upsertArchetypeWeekly(
        env.COMPETITIVE_DB,
        week,
        format,
        archetype.key,
        archetype.label,
        archetype.deckCount,
        archetype.metaSharePct,
      );
    }
    for (const card of trendingCards) {
      await upsertCardPlayRateWeekly(env.COMPETITIVE_DB, week, format, card.name, card.playRatePct);
    }
  } catch {
    // History is a nice-to-have signal for future deltas — never fail the primary request.
  }
}

function toSourceStatus(
  name: SourceStatus["name"],
  result: { ok: true; data: unknown } | { ok: false; skipped: true; reason: string } | { ok: false; skipped: false; error: string },
): SourceStatus {
  if (result.ok) return { name, active: true, reason: null };
  if (result.skipped) return { name, active: false, reason: result.reason };
  return { name, active: false, reason: result.error };
}

function round1(value: number): number {
  return Math.round(value * 10) / 10;
}
