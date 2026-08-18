import { describe, expect, it } from "vitest";
import {
  getArchetypeWeekly,
  isoWeek,
  isPrunableWeek,
  previousIsoWeek,
  pruneOldMetaWeeks,
  upsertArchetypeWeekly,
  type D1Like,
} from "../src/lib/weekly";

describe("isoWeek", () => {
  it("computes an ISO 8601 week string", () => {
    expect(isoWeek(new Date(Date.UTC(2026, 6, 11)))).toBe("2026-W28");
  });

  it("handles the year boundary", () => {
    expect(isoWeek(new Date(Date.UTC(2025, 11, 29)))).toBe("2026-W01");
  });
});

describe("previousIsoWeek", () => {
  it("returns exactly one ISO week earlier", () => {
    expect(previousIsoWeek(new Date(Date.UTC(2026, 6, 11)))).toBe("2026-W27");
  });
});

describe("isPrunableWeek", () => {
  it("keeps the last 8 weeks, prunes older", () => {
    expect(isPrunableWeek("2026-W20", "2026-W28", 8)).toBe(false);
    expect(isPrunableWeek("2026-W19", "2026-W28", 8)).toBe(true);
  });
});

/** Minimal in-memory fake of the D1 surface format_archetype_weekly needs. */
function fakeD1(): D1Like & { rows: { iso_week: string; format: string; archetype_key: string; meta_share_pct: number }[] } {
  const rows: { iso_week: string; format: string; archetype_key: string; meta_share_pct: number }[] = [];
  return {
    rows,
    prepare(query: string) {
      let bound: unknown[] = [];
      return {
        bind(...values: unknown[]) {
          bound = values;
          return this;
        },
        async run() {
          if (query.startsWith("INSERT INTO format_archetype_weekly")) {
            const [week, format, key, , , metaSharePct] = bound as [string, string, string, string, number, number];
            const existing = rows.find((r) => r.iso_week === week && r.format === format && r.archetype_key === key);
            if (existing) existing.meta_share_pct = metaSharePct;
            else rows.push({ iso_week: week, format, archetype_key: key, meta_share_pct: metaSharePct });
          } else if (query.startsWith("DELETE FROM format_archetype_weekly")) {
            const [week] = bound as [string];
            for (let i = rows.length - 1; i >= 0; i--) {
              if (rows[i]!.iso_week === week) rows.splice(i, 1);
            }
          }
          return {};
        },
        async all<T>() {
          if (query.startsWith("SELECT archetype_key, meta_share_pct")) {
            const [week, format] = bound as [string, string];
            const results = rows
              .filter((r) => r.iso_week === week && r.format === format)
              .map((r) => ({ archetype_key: r.archetype_key, meta_share_pct: r.meta_share_pct }));
            return { results: results as T[] };
          }
          if (query.startsWith("SELECT DISTINCT iso_week")) {
            const weeks = Array.from(new Set(rows.map((r) => r.iso_week))).map((iso_week) => ({ iso_week }));
            return { results: weeks as T[] };
          }
          return { results: [] };
        },
      };
    },
  };
}

describe("weekly archetype history (D1)", () => {
  it("upserts and reads back a weekly archetype share, scoped by (week, format)", async () => {
    const db = fakeD1();
    await upsertArchetypeWeekly(db, "2026-W28", "modern", "boros-aggro", "Boros — Bolt", 12, 24.5);
    const shares = await getArchetypeWeekly(db, "2026-W28", "modern");
    expect(shares.get("boros-aggro")).toBe(24.5);
  });

  it("overwrites on conflict rather than duplicating", async () => {
    const db = fakeD1();
    await upsertArchetypeWeekly(db, "2026-W28", "modern", "boros-aggro", "Boros — Bolt", 12, 24.5);
    await upsertArchetypeWeekly(db, "2026-W28", "modern", "boros-aggro", "Boros — Bolt", 15, 30.0);
    const shares = await getArchetypeWeekly(db, "2026-W28", "modern");
    expect(shares.get("boros-aggro")).toBe(30.0);
    expect(db.rows).toHaveLength(1);
  });

  it("pruneOldMetaWeeks removes rows older than the retention window", async () => {
    const db = fakeD1();
    await upsertArchetypeWeekly(db, "2026-W01", "modern", "old", "Old", 1, 10);
    await upsertArchetypeWeekly(db, "2026-W28", "modern", "recent", "Recent", 1, 10);
    await pruneOldMetaWeeks(db, "2026-W28", 8);
    expect(db.rows.map((r) => r.archetype_key)).toEqual(["recent"]);
  });
});
