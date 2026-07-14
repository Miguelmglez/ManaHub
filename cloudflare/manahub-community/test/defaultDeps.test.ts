import { describe, expect, it } from "vitest";
import { defaultDeps as commanderDefaultDeps } from "../src/routes/aggregateCommander";
import { defaultDeps as sixtyDefaultDeps } from "../src/routes/aggregateSixty";

/**
 * Regression guard for the "Illegal invocation" production incident (2026-07-12): assigning
 * the global `fetch` function directly as a plain object property (`{ fetchImpl: fetch }`)
 * detaches it from its required implicit receiver. Calling it later as `deps.fetchImpl(...)`
 * then invokes it with `this` bound to the `deps` object, which real workerd's `fetch`
 * rejects with `TypeError: Illegal invocation`.
 *
 * IMPORTANT — this test does NOT reproduce the throw itself. Node's global `fetch` (undici)
 * tolerates a detached receiver, and this project's test harness runs route handlers directly
 * in Node via standalone `miniflare` (KV/D1 emulation only, not a full workerd JS isolate — see
 * vitest.config.ts `environment: "node"`), so the actual `Illegal invocation` behavior is NOT
 * observable locally. That gap is why 61/61 local tests passed while the real deployment threw.
 * A true reproduction would require `@cloudflare/vitest-pool-workers` (runs tests inside real
 * workerd) — see project memory `project_community_aggregate_worker.md` for why that migration
 * was deferred rather than done as part of this narrow fix.
 *
 * What THIS test guards, honestly: it asserts `defaultDeps.fetchImpl` is a wrapper function,
 * never a direct reference to the global `fetch`. That is the exact invariant the incident
 * violated — if someone "simplifies" this back to `fetchImpl: fetch`, this test fails even
 * though it can't prove workerd would throw.
 */
describe("defaultDeps.fetchImpl must never be a bare reference to global fetch", () => {
  it("aggregateCommander: defaultDeps.fetchImpl is a wrapper, not the global fetch itself", () => {
    expect(commanderDefaultDeps.fetchImpl).not.toBe(fetch);
    expect(typeof commanderDefaultDeps.fetchImpl).toBe("function");
  });

  it("aggregateSixty: defaultDeps.fetchImpl is a wrapper, not the global fetch itself", () => {
    expect(sixtyDefaultDeps.fetchImpl).not.toBe(fetch);
    expect(typeof sixtyDefaultDeps.fetchImpl).toBe("function");
  });
});
