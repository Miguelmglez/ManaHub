import { isoWeek, topForWeek } from "../lib/trending";
import type { Env, TrendingResponse } from "../types";

/**
 * Handles `GET /v1/trending?week=<iso-week>`. Defaults to the current ISO week when
 * [weekParam] is omitted. Pure D1 read — no upstream fetch, no PII (see schema.sql).
 */
export async function handleTrending(
  weekParam: string | null,
  env: Env,
  now: () => number = () => Date.now(),
): Promise<TrendingResponse> {
  const week = weekParam ?? isoWeek(new Date(now()));
  const [topCommanders, topCards] = await Promise.all([
    topForWeek(env.COMMUNITY_DB, week, "commander", 10),
    topForWeek(env.COMMUNITY_DB, week, "card", 10),
  ]);
  return { status: "ok", week, topCommanders, topCards };
}
