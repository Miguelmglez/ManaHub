import { describe, expect, it, vi } from "vitest";
import { incrementCounter, isoWeek, isPrunableWeek, pruneOldWeeks, topForWeek, type D1Like } from "../src/lib/trending";

describe("isoWeek", () => {
  it("computes an ISO 8601 week string", () => {
    // 2026-07-11 is a Saturday in ISO week 28.
    expect(isoWeek(new Date(Date.UTC(2026, 6, 11)))).toBe("2026-W28");
  });

  it("handles the year boundary (late-Dec dates can fall in week 01 of the next year)", () => {
    // 2025-12-29 is a Monday, ISO week 1 of 2026.
    expect(isoWeek(new Date(Date.UTC(2025, 11, 29)))).toBe("2026-W01");
  });
});

describe("isPrunableWeek", () => {
  it("keeps the last 8 weeks", () => {
    expect(isPrunableWeek("2026-W20", "2026-W28", 8)).toBe(false);
    expect(isPrunableWeek("2026-W28", "2026-W28", 8)).toBe(false);
  });

  it("prunes anything older than 8 weeks", () => {
    expect(isPrunableWeek("2026-W19", "2026-W28", 8)).toBe(true);
    expect(isPrunableWeek("2026-W01", "2026-W28", 8)).toBe(true);
  });
});

/** Minimal in-memory fake of the D1 surface this module needs, for pure unit tests. */
function fakeD1(): D1Like & { rows: { iso_week: string; kind: string; name: string; count: number }[] } {
  const rows: { iso_week: string; kind: string; name: string; count: number }[] = [];
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
          if (query.startsWith("INSERT INTO trending_counters")) {
            const [week, kind, name] = bound as [string, string, string];
            const existing = rows.find((r) => r.iso_week === week && r.kind === kind && r.name === name);
            if (existing) existing.count += 1;
            else rows.push({ iso_week: week, kind, name, count: 1 });
          } else if (query.startsWith("DELETE FROM trending_counters")) {
            const [week] = bound as [string];
            for (let i = rows.length - 1; i >= 0; i--) {
              if (rows[i]!.iso_week === week) rows.splice(i, 1);
            }
          }
          return {};
        },
        async all<T>() {
          if (query.startsWith("SELECT name, count")) {
            const [week, kind, limit] = bound as [string, string, number];
            const results = rows
              .filter((r) => r.iso_week === week && r.kind === kind)
              .sort((a, b) => b.count - a.count)
              .slice(0, limit)
              .map((r) => ({ name: r.name, count: r.count }));
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

describe("trending counters (D1)", () => {
  it("increments an anonymous weekly counter with no user id / IP fields", async () => {
    const db = fakeD1();
    await incrementCounter(db, "2026-W28", "commander", "Atraxa, Praetors' Voice");
    await incrementCounter(db, "2026-W28", "commander", "Atraxa, Praetors' Voice");
    expect(db.rows).toEqual([{ iso_week: "2026-W28", kind: "commander", name: "Atraxa, Praetors' Voice", count: 2 }]);
    // Privacy invariant: no row carries anything beyond week/kind/name/count.
    expect(Object.keys(db.rows[0]!)).toEqual(["iso_week", "kind", "name", "count"]);
  });

  it("topForWeek returns the highest counts first, scoped to (week, kind)", async () => {
    const db = fakeD1();
    await incrementCounter(db, "2026-W28", "commander", "A");
    await incrementCounter(db, "2026-W28", "commander", "B");
    await incrementCounter(db, "2026-W28", "commander", "B");
    await incrementCounter(db, "2026-W28", "card", "Sol Ring");
    const top = await topForWeek(db, "2026-W28", "commander", 10);
    expect(top).toEqual([
      { name: "B", count: 2 },
      { name: "A", count: 1 },
    ]);
  });

  it("pruneOldWeeks removes rows older than the retention window, keeps recent ones", async () => {
    const db = fakeD1();
    await incrementCounter(db, "2026-W01", "commander", "old");
    await incrementCounter(db, "2026-W28", "commander", "recent");
    await pruneOldWeeks(db, "2026-W28", 8);
    expect(db.rows.map((r) => r.name)).toEqual(["recent"]);
  });
});

describe("throttle etiquette timing", () => {
  it("waits the remaining interval when called too soon", async () => {
    const { throttle } = await import("../src/lib/etiquette");
    let clock = 0;
    const now = () => clock;
    const sleep = vi.fn(async (ms: number) => {
      clock += ms;
    });
    const result = await throttle(0, now, sleep, 200);
    expect(sleep).toHaveBeenCalledWith(200);
    expect(result).toBe(200);
  });

  it("does not sleep when enough time has already elapsed", async () => {
    const { throttle } = await import("../src/lib/etiquette");
    const now = () => 500;
    const sleep = vi.fn(async () => undefined);
    const result = await throttle(0, now, sleep, 200);
    expect(sleep).not.toHaveBeenCalled();
    expect(result).toBe(500);
  });
});
