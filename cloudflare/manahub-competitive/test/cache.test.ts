import { describe, expect, it } from "vitest";
import { classifyFreshness, META_TTL_MS, STALE_WHILE_REVALIDATE_MS } from "../src/lib/cache";

describe("classifyFreshness (stale-while-revalidate)", () => {
  const cachedAt = 1_000_000;

  it("is missing when there is no envelope", () => {
    expect(classifyFreshness(null, cachedAt)).toBe("missing");
  });

  it("is fresh within the TTL", () => {
    expect(classifyFreshness({ data: {}, cachedAt }, cachedAt + 1000)).toBe("fresh");
    expect(classifyFreshness({ data: {}, cachedAt }, cachedAt + META_TTL_MS - 1)).toBe("fresh");
  });

  it("is stale within the SWR grace window past the TTL", () => {
    const now = cachedAt + META_TTL_MS + 1;
    expect(classifyFreshness({ data: {}, cachedAt }, now)).toBe("stale");
    const edge = cachedAt + META_TTL_MS + STALE_WHILE_REVALIDATE_MS - 1;
    expect(classifyFreshness({ data: {}, cachedAt }, edge)).toBe("stale");
  });

  it("is expired past the SWR grace window", () => {
    const now = cachedAt + META_TTL_MS + STALE_WHILE_REVALIDATE_MS + 1;
    expect(classifyFreshness({ data: {}, cachedAt }, now)).toBe("expired");
  });

  it("respects custom ttl/stale windows", () => {
    expect(classifyFreshness({ data: {}, cachedAt }, cachedAt + 50, 100, 50)).toBe("fresh");
    expect(classifyFreshness({ data: {}, cachedAt }, cachedAt + 120, 100, 50)).toBe("stale");
    expect(classifyFreshness({ data: {}, cachedAt }, cachedAt + 200, 100, 50)).toBe("expired");
  });
});
