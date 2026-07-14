import { describe, expect, it } from "vitest";
import { computeSynergy60, globalInclusionFor, STATIC_STAPLE_BOOTSTRAP } from "../src/lib/staple";

describe("computeSynergy60", () => {
  it("is inclusionPct minus globalInclusionPct", () => {
    expect(computeSynergy60(0.9, 0.85)).toBeCloseTo(0.05, 5);
  });

  it("clamps to [-1, 1]", () => {
    expect(computeSynergy60(1, -5)).toBe(1);
    expect(computeSynergy60(-5, 1)).toBe(-1);
  });

  it("dampens a staple's synergy near zero when inclusion tracks the global rate", () => {
    expect(computeSynergy60(0.85, 0.85)).toBeCloseTo(0, 5);
  });
});

describe("globalInclusionFor", () => {
  it("prefers D1-backed data over the static bootstrap", () => {
    const d1 = new Map([["Sol Ring", 0.99]]);
    expect(globalInclusionFor("Sol Ring", d1)).toBe(0.99);
  });

  it("falls back to the static bootstrap for known staples with no D1 data yet", () => {
    expect(globalInclusionFor("Sol Ring", new Map())).toBe(STATIC_STAPLE_BOOTSTRAP["Sol Ring"]);
  });

  it("defaults to 0 for an unknown card with no D1 data", () => {
    expect(globalInclusionFor("Some Niche Card", new Map())).toBe(0);
  });
});
