import { describe, expect, it } from "vitest";
import { isRisingFromRankOverTime, normalizeCommanderAggregate, slugifyCommanderName } from "../src/lib/edhrec";
import type { EdhrecCommanderPayload } from "../src/types";
import fixture from "./fixtures/edhrec-commander.json" with { type: "json" };

describe("slugifyCommanderName", () => {
  it("lowercases and hyphenates", () => {
    expect(slugifyCommanderName("Atraxa, Praetors' Voice")).toBe("atraxa-praetors-voice");
  });

  it("handles partner commanders joined with a dash by the caller", () => {
    expect(slugifyCommanderName("Tevesh Szat, Doom of Fools")).toBe("tevesh-szat-doom-of-fools");
  });
});

describe("normalizeCommanderAggregate", () => {
  const payload = fixture as unknown as EdhrecCommanderPayload;
  const snapshot = normalizeCommanderAggregate("Atraxa, Praetors' Voice", payload, 12345);

  it("computes inclusionPct as num_decks/potential_decks (not a passthrough field)", () => {
    const solRing = snapshot.cards.find((c) => c.name === "Sol Ring");
    expect(solRing).toBeDefined();
    expect(solRing!.inclusionPct).toBeCloseTo(40000 / 42455, 5);
  });

  it("uses EDHREC's synergy directly (already staple-dampened)", () => {
    const solRing = snapshot.cards.find((c) => c.name === "Sol Ring");
    expect(solRing!.synergy).toBeCloseTo(-0.14, 5);
  });

  it("counts the Game Changers cardlist", () => {
    expect(snapshot.gameChangersCount).toBe(1);
  });

  it("carries the average type distribution and mana curve", () => {
    expect(snapshot.avgTypeDistribution.land).toBe(35);
    expect(snapshot.manaCurve["3"]).toBe(16);
  });

  it("carries similar commanders and theme tags", () => {
    expect(snapshot.similarCommanders).toContain("Atraxa, Grand Unifier");
    expect(snapshot.themeTags.find((t) => t.name === "Infect")?.count).toBe(4864);
  });

  it("stamps cachedAt from the caller-supplied clock", () => {
    expect(snapshot.cachedAt).toBe(12345);
  });
});

describe("isRisingFromRankOverTime", () => {
  it("is true when the most recent month's rank improved (lower number)", () => {
    expect(isRisingFromRankOverTime((fixture as unknown as EdhrecCommanderPayload).panels.rank_over_time)).toBe(true);
  });

  it("is false with fewer than two data points", () => {
    expect(isRisingFromRankOverTime({})).toBe(false);
    expect(
      isRisingFromRankOverTime({
        "2026-01-01": { commander_count: 1, perc_of_decks_overall: 0, perc_of_decks_overall_ma: 0, rank: 5, rank_ma: 5 },
      }),
    ).toBe(false);
  });

  it("is false when rank got worse (higher number)", () => {
    expect(
      isRisingFromRankOverTime({
        "2026-01-01": { commander_count: 1, perc_of_decks_overall: 0, perc_of_decks_overall_ma: 0, rank: 3, rank_ma: 3 },
        "2026-02-01": { commander_count: 1, perc_of_decks_overall: 0, perc_of_decks_overall_ma: 0, rank: 8, rank_ma: 8 },
      }),
    ).toBe(false);
  });
});
