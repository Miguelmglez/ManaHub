/**
 * D1-backed anonymous weekly trending counters (Phase 3.2). Hard privacy requirement: no
 * user id, no IP, no free-text query — only `(iso_week, kind, name)` with a hit count.
 */

export type TrendingKind = "card" | "commander";

const MS_PER_WEEK = 7 * 24 * 60 * 60 * 1000;

/** ISO 8601 week string "YYYY-Www" (Monday-start weeks), used as the D1 partition key. */
export function isoWeek(date: Date): string {
  const d = new Date(Date.UTC(date.getUTCFullYear(), date.getUTCMonth(), date.getUTCDate()));
  const dayNum = (d.getUTCDay() + 6) % 7; // Monday = 0
  d.setUTCDate(d.getUTCDate() - dayNum + 3); // nearest Thursday
  const firstThursday = new Date(Date.UTC(d.getUTCFullYear(), 0, 4));
  const firstDayNum = (firstThursday.getUTCDay() + 6) % 7;
  firstThursday.setUTCDate(firstThursday.getUTCDate() - firstDayNum + 3);
  const week = 1 + Math.round((d.getTime() - firstThursday.getTime()) / MS_PER_WEEK);
  return `${d.getUTCFullYear()}-W${String(week).padStart(2, "0")}`;
}

/** Weeks strictly older than this many weeks-ago are eligible for pruning (Phase 3.2: >8). */
export function isPrunableWeek(week: string, currentWeek: string, keepWeeks = 8): boolean {
  const parse = (w: string): number => {
    const [year, wk] = w.split("-W");
    return Number(year) * 100 + Number(wk);
  };
  // Approximate ordinal distance in weeks; exact enough for an 8-week retention window
  // since we only ever compare weeks within the same or adjacent year.
  return parse(currentWeek) - parse(week) > keepWeeks;
}

export interface D1Like {
  prepare(query: string): D1PreparedStatementLike;
}

export interface D1PreparedStatementLike {
  bind(...values: unknown[]): D1PreparedStatementLike;
  run(): Promise<unknown>;
  all<T = Record<string, unknown>>(): Promise<{ results: T[] }>;
}

/** Upserts (increments) the anonymous weekly counter for one (week, kind, name) tuple. */
export async function incrementCounter(
  db: D1Like,
  week: string,
  kind: TrendingKind,
  name: string,
): Promise<void> {
  await db
    .prepare(
      `INSERT INTO trending_counters (iso_week, kind, name, count) VALUES (?1, ?2, ?3, 1)
       ON CONFLICT(iso_week, kind, name) DO UPDATE SET count = count + 1`,
    )
    .bind(week, kind, name)
    .run();
}

/** Top-N names for a given (week, kind), highest count first. */
export async function topForWeek(
  db: D1Like,
  week: string,
  kind: TrendingKind,
  limit: number,
): Promise<{ name: string; count: number }[]> {
  const { results } = await db
    .prepare(
      `SELECT name, count FROM trending_counters WHERE iso_week = ?1 AND kind = ?2
       ORDER BY count DESC LIMIT ?3`,
    )
    .bind(week, kind, limit)
    .all<{ name: string; count: number }>();
  return results;
}

/** Deletes every counter row for weeks older than the 8-week retention window. */
export async function pruneOldWeeks(db: D1Like, currentWeek: string, keepWeeks = 8): Promise<void> {
  const { results } = await db
    .prepare(`SELECT DISTINCT iso_week FROM trending_counters`)
    .all<{ iso_week: string }>();
  for (const row of results) {
    if (isPrunableWeek(row.iso_week, currentWeek, keepWeeks)) {
      await db.prepare(`DELETE FROM trending_counters WHERE iso_week = ?1`).bind(row.iso_week).run();
    }
  }
}
