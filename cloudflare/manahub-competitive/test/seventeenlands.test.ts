import { describe, expect, it } from "vitest";
import { fetchLimitedRatings } from "../src/lib/sources/seventeenlands";
import ratingsFixture from "./fixtures/17lands-card-ratings.json" with { type: "json" };

describe("fetchLimitedRatings (17lands)", () => {
  it("fetches, normalizes fields, and converts fractional rates to percentages", async () => {
    let requestedUrl = "";
    const fetchImpl = (async (input: RequestInfo | URL) => {
      requestedUrl = typeof input === "string" ? input : input.toString();
      return new Response(JSON.stringify(ratingsFixture), { status: 200 });
    }) as unknown as typeof fetch;

    const result = await fetchLimitedRatings("BLB", "PremierDraft", { fetchImpl });
    expect(result.ok).toBe(true);
    if (!result.ok) return;

    expect(requestedUrl).toContain("expansion=BLB");
    expect(requestedUrl).toContain("format=PremierDraft");

    const bolt = result.data.find((c) => c.name === "Test Bolt");
    expect(bolt).toBeDefined();
    expect(bolt!.gihWinRatePct).toBeCloseTo(58.2, 1);
    expect(bolt!.playRatePct).toBeCloseTo(45.0, 1);
  });

  it("reports a non-skipped error when the upstream responds with a non-OK status", async () => {
    const fetchImpl = (async () => new Response("nope", { status: 503 })) as unknown as typeof fetch;
    const result = await fetchLimitedRatings("BLB", "PremierDraft", { fetchImpl });
    expect(result.ok).toBe(false);
    if (result.ok) return;
    expect(result.skipped).toBe(false);
  });

  it("reports an error rather than throwing when the response body is not JSON", async () => {
    const fetchImpl = (async () => new Response("<html>not json</html>", { status: 200 })) as unknown as typeof fetch;
    const result = await fetchLimitedRatings("BLB", "PremierDraft", { fetchImpl });
    expect(result.ok).toBe(false);
  });
});
