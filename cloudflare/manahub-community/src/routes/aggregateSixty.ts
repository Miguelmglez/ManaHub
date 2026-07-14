import { classifyFreshness, getCached, putCached, AGGREGATE_TTL_MS, DECK_TTL_MS } from "../lib/cache";
import { fetchHeaders } from "../lib/etiquette";
import {
  buildCanonicalKey,
  intersectById,
  isTimeout,
  oracleColorIdentity,
  sanitizeSummaries,
  COMMANDER_SANITIZE,
  SIXTY_CARD_SANITIZE,
} from "../lib/archidekt";
import {
  addCandidateIds,
  markDetailsFetched,
  nextDetailBatch,
  progressOf,
  readyToMaterialize,
  startBuild,
  TARGET_SAMPLE_SIZE_MIN,
  type BuildState,
} from "../lib/buildState";
import { computeSynergy60, globalInclusionFor } from "../lib/staple";
import { incrementCounter, isoWeek } from "../lib/trending";
import type {
  AggregateCardEntry,
  ArchidektDeckDetail,
  ArchidektSearchResponse,
  Env,
  SixtyAggregateSnapshot,
} from "../types";

export interface SixtyAggregateDeps {
  fetchImpl: typeof fetch;
  now: () => number;
}

export const defaultDeps: SixtyAggregateDeps = {
  // Wrapped (not a bare `fetch` reference) — see the identical note in aggregateCommander.ts:
  // assigning global `fetch` directly as a property detaches it from its implicit receiver and
  // throws `Illegal invocation` in real workerd when called as `deps.fetchImpl(...)`.
  fetchImpl: (...args: Parameters<typeof fetch>) => fetch(...args),
  now: () => Date.now(),
};

/**
 * Handles `GET /v1/aggregate?cards=<c1>,<c2>,<c3>&format=<fmt>` (60-card path,
 * Archidekt-backed per D16). The client supplies its own 2-3 canonical signature cards
 * (lowest global frequency, deterministic ordering) so same-archetype users share a cache
 * entry. On a cache miss this drives one step of the incremental build state machine per
 * invocation (Phase 3.2's free-tier CPU budget constraint) and returns `{status:"building"}`
 * until enough decks are collected, at which point the snapshot is materialized.
 */
export async function handleSixtyAggregate(
  signatureCards: string[],
  format: number,
  env: Env,
  deps: SixtyAggregateDeps = defaultDeps,
): Promise<SixtyAggregateSnapshot> {
  const canonicalKey = buildCanonicalKey(signatureCards);
  const aggKey = `agg:${format}:${canonicalKey}`;
  const now = deps.now();

  const cached = await getCached<SixtyAggregateSnapshot>(env.COMMUNITY_KV, aggKey);
  const freshness = classifyFreshness(cached, now);

  if (freshness === "fresh" || freshness === "stale") {
    if (freshness === "stale") {
      // Best-effort background revalidation — a failed refresh must not break the response.
      advanceBuild(signatureCards, format, canonicalKey, env, deps).catch(() => undefined);
    }
    await recordCardTrending(env, signatureCards, deps);
    return cached!.data;
  }

  await recordCardTrending(env, signatureCards, deps);
  return advanceBuild(signatureCards, format, canonicalKey, env, deps);
}

async function recordCardTrending(
  env: Env,
  signatureCards: readonly string[],
  deps: SixtyAggregateDeps,
): Promise<void> {
  try {
    const week = isoWeek(new Date(deps.now()));
    for (const card of signatureCards) {
      await incrementCounter(env.COMMUNITY_DB, week, "card", card);
    }
  } catch {
    // Never fail the aggregate request over a D1 hiccup.
  }
}

/** Runs one step of the incremental build (or materializes if ready) and persists the state. */
async function advanceBuild(
  signatureCards: string[],
  format: number,
  canonicalKey: string,
  env: Env,
  deps: SixtyAggregateDeps,
): Promise<SixtyAggregateSnapshot> {
  const buildKey = `build:${canonicalKey}`;
  let state = (await getCached<BuildState>(env.COMMUNITY_KV, buildKey))?.data ?? null;

  if (!state) {
    state = startBuild(canonicalKey, TARGET_SAMPLE_SIZE_MIN);
    const searches: Awaited<ReturnType<typeof searchOneCard>>[] = [];
    for (const card of signatureCards) {
      searches.push(await searchOneCard(card, format, env, deps));
    }
    const timedOut = searches.some((s) => s.timedOut);
    const candidates = timedOut ? [] : intersectById(searches.map((s) => s.decks));
    state = addCandidateIds(state, candidates.map((d) => d.id));
  }

  if (!readyToMaterialize(state)) {
    const batch = nextDetailBatch(state);
    for (const deckId of batch) {
      const detail = await fetchDeckDetail(deckId, env, deps);
      if (detail) {
        await putCached(env.COMMUNITY_KV, `deck:${deckId}`, detail, deps.now(), DECK_TTL_MS / 1000);
      }
    }
    state = markDetailsFetched(state, batch);
  }

  if (readyToMaterialize(state)) {
    const snapshot = await materialize(state, format, env, deps);
    await env.COMMUNITY_KV.delete(buildKey);
    await putCached(env.COMMUNITY_KV, `agg:${format}:${canonicalKey}`, snapshot, deps.now(), AGGREGATE_TTL_MS / 1000);
    return snapshot;
  }

  await putCached(env.COMMUNITY_KV, buildKey, state, deps.now());
  return {
    status: "building",
    source: "archidekt",
    canonicalKey,
    progress: progressOf(state),
  };
}

async function searchOneCard(
  cardName: string,
  format: number,
  env: Env,
  deps: SixtyAggregateDeps,
): Promise<{ decks: ReturnType<typeof sanitizeSummaries>; timedOut: boolean }> {
  const url = new URL("https://archidekt.com/api/decks/v3/");
  url.searchParams.set("cardName", cardName);
  url.searchParams.set("deckFormat", String(format));
  const response = await deps.fetchImpl(url.toString(), { headers: fetchHeaders() });
  if (!response.ok) return { decks: [], timedOut: true };
  const payload = (await response.json()) as ArchidektSearchResponse;
  if (isTimeout(payload)) return { decks: [], timedOut: true };
  const band = format === 3 ? COMMANDER_SANITIZE : SIXTY_CARD_SANITIZE;
  return { decks: sanitizeSummaries(payload.results, band), timedOut: false };
}

async function fetchDeckDetail(
  deckId: number,
  env: Env,
  deps: SixtyAggregateDeps,
): Promise<ArchidektDeckDetail | null> {
  const existing = await getCached<ArchidektDeckDetail>(env.COMMUNITY_KV, `deck:${deckId}`);
  if (existing) return existing.data;
  const response = await deps.fetchImpl(`https://archidekt.com/api/decks/${deckId}/`, {
    headers: fetchHeaders(),
  });
  if (!response.ok) return null;
  return (await response.json()) as ArchidektDeckDetail;
}

async function materialize(
  state: BuildState,
  format: number,
  env: Env,
  deps: SixtyAggregateDeps,
): Promise<SixtyAggregateSnapshot> {
  const target = Math.min(state.target, state.collectedDeckIds.length);
  const deckIds = state.collectedDeckIds.slice(0, target);

  const inclusionCounts = new Map<string, number>();
  const deckSummaries: {
    id: number;
    name: string;
    owner: string;
    viewCount: number;
    colors: Record<string, number>;
    edhBracket: number | null;
  }[] = [];
  const colorProfile: Record<string, number> = { W: 0, U: 0, B: 0, R: 0, G: 0 };
  let sampled = 0;

  for (const deckId of deckIds) {
    const cached = await getCached<ArchidektDeckDetail>(env.COMMUNITY_KV, `deck:${deckId}`);
    if (!cached) continue;
    const detail = cached.data;
    sampled += 1;
    deckSummaries.push({
      id: detail.id,
      name: detail.name,
      owner: detail.owner?.username ?? "Unknown",
      viewCount: detail.viewCount,
      colors: {},
      edhBracket: detail.edhBracket,
    });
    const seenInThisDeck = new Set<string>();
    for (const entry of detail.cards) {
      const name = entry.card?.oracleCard?.name;
      if (!name || seenInThisDeck.has(name)) continue;
      seenInThisDeck.add(name);
      inclusionCounts.set(name, (inclusionCounts.get(name) ?? 0) + 1);
      for (const letter of oracleColorIdentity(entry)) {
        if (letter in colorProfile) colorProfile[letter] = (colorProfile[letter] ?? 0) + 1;
      }
    }
  }

  const globalInclusions = await loadGlobalInclusions(env, Array.from(inclusionCounts.keys()));
  const cards: AggregateCardEntry[] = Array.from(inclusionCounts.entries()).map(([name, count]) => {
    const inclusionPct = sampled > 0 ? count / sampled : 0;
    const global = globalInclusionFor(name, globalInclusions);
    return {
      name,
      scryfallUid: null,
      inclusionPct,
      synergy: computeSynergy60(inclusionPct, global),
      category: "mainboard",
      numDecks: count,
    };
  });

  await persistGlobalInclusion(env, inclusionCounts, sampled, deps);

  return {
    status: "materialized",
    source: "archidekt",
    canonicalKey: state.canonicalKey,
    format,
    numDecksSampled: sampled,
    deckSummaries,
    colorProfile,
    cards,
    cachedAt: deps.now(),
  };
}

async function loadGlobalInclusions(
  env: Env,
  cardNames: readonly string[],
): Promise<Map<string, number>> {
  const result = new Map<string, number>();
  if (cardNames.length === 0) return result;
  try {
    const placeholders = cardNames.map((_, i) => `?${i + 1}`).join(",");
    const { results } = await env.COMMUNITY_DB
      .prepare(
        `SELECT card_name, total_decks_seen, decks_including FROM global_card_inclusion WHERE card_name IN (${placeholders})`,
      )
      .bind(...cardNames)
      .all<{ card_name: string; total_decks_seen: number; decks_including: number }>();
    for (const row of results) {
      if (row.total_decks_seen > 0) {
        result.set(row.card_name, row.decks_including / row.total_decks_seen);
      }
    }
  } catch {
    // Fall back to the static bootstrap table (see staple.ts) on any D1 failure.
  }
  return result;
}

async function persistGlobalInclusion(
  env: Env,
  inclusionCounts: ReadonlyMap<string, number>,
  sampled: number,
  deps: SixtyAggregateDeps,
): Promise<void> {
  if (sampled === 0) return;
  try {
    for (const [name, count] of inclusionCounts) {
      await env.COMMUNITY_DB
        .prepare(
          `INSERT INTO global_card_inclusion (card_name, total_decks_seen, decks_including, updated_at)
           VALUES (?1, ?2, ?3, ?4)
           ON CONFLICT(card_name) DO UPDATE SET
             total_decks_seen = total_decks_seen + ?2,
             decks_including = decks_including + ?3,
             updated_at = ?4`,
        )
        .bind(name, sampled, count, deps.now())
        .run();
    }
  } catch {
    // Global inclusion tracking is a quality-of-scoring enhancement, not correctness-critical.
  }
}
