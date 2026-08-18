/**
 * D1-backed weekly archetype-share / card-play-rate history, used only to compute
 * week-over-week deltas for the meta snapshot. No PII — format + archetype-key/card-name + a
 * percentage, same privacy shape as manahub-community's trending_counters table.
 */

const MS_PER_WEEK = 7 * 24 * 60 * 60 * 1000;

/** ISO 8601 week string "YYYY-Www" (Monday-start weeks). Mirrors manahub-community's lib/trending.ts. */
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

/** The ISO week string for exactly one week before [date]. */
export function previousIsoWeek(date: Date): string {
  return isoWeek(new Date(date.getTime() - MS_PER_WEEK));
}

export function isPrunableWeek(week: string, currentWeek: string, keepWeeks = 8): boolean {
  const parse = (w: string): number => {
    const [year, wk] = w.split("-W");
    return Number(year) * 100 + Number(wk);
  };
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

export async function upsertArchetypeWeekly(
  db: D1Like,
  week: string,
  format: string,
  key: string,
  label: string,
  deckCount: number,
  metaSharePct: number,
): Promise<void> {
  await db
    .prepare(
      `INSERT INTO format_archetype_weekly (iso_week, format, archetype_key, label, deck_count, meta_share_pct)
       VALUES (?1, ?2, ?3, ?4, ?5, ?6)
       ON CONFLICT(iso_week, format, archetype_key) DO UPDATE SET
         label = excluded.label, deck_count = excluded.deck_count, meta_share_pct = excluded.meta_share_pct`,
    )
    .bind(week, format, key, label, deckCount, metaSharePct)
    .run();
}

export async function getArchetypeWeekly(
  db: D1Like,
  week: string,
  format: string,
): Promise<Map<string, number>> {
  const { results } = await db
    .prepare(`SELECT archetype_key, meta_share_pct FROM format_archetype_weekly WHERE iso_week = ?1 AND format = ?2`)
    .bind(week, format)
    .all<{ archetype_key: string; meta_share_pct: number }>();
  return new Map(results.map((r) => [r.archetype_key, r.meta_share_pct]));
}

export async function upsertCardPlayRateWeekly(
  db: D1Like,
  week: string,
  format: string,
  cardName: string,
  playRatePct: number,
): Promise<void> {
  await db
    .prepare(
      `INSERT INTO card_playrate_weekly (iso_week, format, card_name, play_rate_pct)
       VALUES (?1, ?2, ?3, ?4)
       ON CONFLICT(iso_week, format, card_name) DO UPDATE SET play_rate_pct = excluded.play_rate_pct`,
    )
    .bind(week, format, cardName, playRatePct)
    .run();
}

export async function getCardPlayRateWeekly(
  db: D1Like,
  week: string,
  format: string,
): Promise<Map<string, number>> {
  const { results } = await db
    .prepare(`SELECT card_name, play_rate_pct FROM card_playrate_weekly WHERE iso_week = ?1 AND format = ?2`)
    .bind(week, format)
    .all<{ card_name: string; play_rate_pct: number }>();
  return new Map(results.map((r) => [r.card_name, r.play_rate_pct]));
}

export async function pruneOldMetaWeeks(db: D1Like, currentWeek: string, keepWeeks = 8): Promise<void> {
  const [archetypeWeeks, cardWeeks] = await Promise.all([
    db.prepare(`SELECT DISTINCT iso_week FROM format_archetype_weekly`).all<{ iso_week: string }>(),
    db.prepare(`SELECT DISTINCT iso_week FROM card_playrate_weekly`).all<{ iso_week: string }>(),
  ]);
  for (const row of archetypeWeeks.results) {
    if (isPrunableWeek(row.iso_week, currentWeek, keepWeeks)) {
      await db.prepare(`DELETE FROM format_archetype_weekly WHERE iso_week = ?1`).bind(row.iso_week).run();
    }
  }
  for (const row of cardWeeks.results) {
    if (isPrunableWeek(row.iso_week, currentWeek, keepWeeks)) {
      await db.prepare(`DELETE FROM card_playrate_weekly WHERE iso_week = ?1`).bind(row.iso_week).run();
    }
  }
}
