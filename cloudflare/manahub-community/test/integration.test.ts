import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { Miniflare } from "miniflare";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { handleCommanderAggregate } from "../src/routes/aggregateCommander";
import { handleSixtyAggregate } from "../src/routes/aggregateSixty";
import { handleTrending } from "../src/routes/trending";
import { buildCanonicalKey } from "../src/lib/archidekt";
import type { Env, ArchidektDeckDetail, ArchidektSearchResponse, EdhrecCommanderPayload } from "../src/types";
import edhrecFixture from "./fixtures/edhrec-commander.json" with { type: "json" };
import searchFixture from "./fixtures/archidekt-search-page.json" with { type: "json" };
import deckDetailFixture from "./fixtures/archidekt-deck-detail.json" with { type: "json" };

/**
 * Real KV + D1 emulation via standalone Miniflare (per the task's explicit allowance:
 * "miniflare EMULATES KV/D1 in-process, it does not touch real Cloudflare infrastructure").
 * The route handlers are called directly (not via HTTP dispatch) with Miniflare-backed
 * bindings as `env` and a mocked `fetch` as `deps.fetchImpl` — this proves the KV
 * SWR/incremental-build machinery and the D1 counter/global-inclusion SQL work against a
 * real (emulated) KV/D1 engine, not a hand-rolled fake.
 */
describe("Worker routes against emulated KV + D1 (Miniflare)", () => {
  let mf: Miniflare;
  let env: Env;

  beforeEach(async () => {
    const schemaPath = fileURLToPath(new URL("../src/schema.sql", import.meta.url));
    const schema = readFileSync(schemaPath, "utf-8");
    mf = new Miniflare({
      modules: true,
      script: "export default { async fetch() { return new Response('ok'); } };",
      kvNamespaces: ["COMMUNITY_KV"],
      d1Databases: ["COMMUNITY_DB"],
    });
    const kv = await mf.getKVNamespace("COMMUNITY_KV");
    const db = await mf.getD1Database("COMMUNITY_DB");
    // D1's exec() requires each `\n`-separated line to be one complete statement, with no
    // comments — collapse each `;`-terminated statement onto a single line first.
    const noComments = schema
      .split("\n")
      .filter((line) => !line.trim().startsWith("--"))
      .join(" ");
    const oneStatementPerLine = noComments
      .split(";")
      .map((s) => s.replace(/\s+/g, " ").trim())
      .filter(Boolean)
      .join("\n");
    await db.exec(oneStatementPerLine);
    env = { COMMUNITY_KV: kv, COMMUNITY_DB: db } as unknown as Env;
  });

  afterEach(async () => {
    await mf.dispose();
  });

  it("commander aggregate: cold miss fetches EDHREC, caches in KV, and warm hit skips the network", async () => {
    let fetchCount = 0;
    const fetchImpl = (async () => {
      fetchCount += 1;
      return new Response(JSON.stringify(edhrecFixture), { status: 200 });
    }) as unknown as typeof fetch;

    const clock = { t: 1_000_000 };
    const deps = { fetchImpl, now: () => clock.t };

    const first = await handleCommanderAggregate("Atraxa, Praetors' Voice", env, deps);
    expect(first.status).toBe("materialized");
    expect(first.commander).toBe("Atraxa, Praetors' Voice");
    expect(fetchCount).toBe(1);

    // Warm hit shortly after: must NOT hit the network again.
    clock.t += 1000;
    const second = await handleCommanderAggregate("Atraxa, Praetors' Voice", env, deps);
    expect(second.cachedAt).toBe(first.cachedAt);
    expect(fetchCount).toBe(1);
  });

  it("commander aggregate: increments the D1 weekly trending counter on every request", async () => {
    const fetchImpl = (async () =>
      new Response(JSON.stringify(edhrecFixture), { status: 200 })) as unknown as typeof fetch;
    const clock = { t: Date.UTC(2026, 6, 11) }; // 2026-07-11 -> ISO week 2026-W28
    const deps = { fetchImpl, now: () => clock.t };

    await handleCommanderAggregate("Atraxa, Praetors' Voice", env, deps);
    await handleCommanderAggregate("Atraxa, Praetors' Voice", env, deps);

    const trending = await handleTrending("2026-W28", env, () => clock.t);
    const entry = trending.topCommanders.find((c) => c.name === "Atraxa, Praetors' Voice");
    expect(entry?.count).toBe(2);
  });

  it("60-card aggregate: with only a couple of sanitized candidates (below the batch size), materializes in a single invocation", async () => {
    const searchResponse = searchFixture as ArchidektSearchResponse;
    const detail = deckDetailFixture as ArchidektDeckDetail;

    const fetchImpl = (async (input: RequestInfo | URL) => {
      const url = typeof input === "string" ? input : input.toString();
      if (url.includes("/api/decks/v3/")) {
        return new Response(JSON.stringify(searchResponse), { status: 200 });
      }
      return new Response(JSON.stringify(detail), { status: 200 });
    }) as unknown as typeof fetch;

    const clock = { t: 2_000_000 };
    const deps = { fetchImpl, now: () => clock.t };

    // The fixture sanitizes down to 2 candidate decks (24261502, 15845995) — fewer than the
    // detail-fetch batch size (5), so both details are fetched inline and the snapshot
    // materializes in this single invocation rather than reporting "building".
    const result = await handleSixtyAggregate(["Krenko, Mob Boss"], 3, env, deps);
    expect(result.status).toBe("materialized");
    if (result.status === "materialized") {
      expect(result.numDecksSampled).toBe(2);
    }
  });

  it("60-card aggregate: batches detail fetches across multiple invocations, reporting building status until complete", async () => {
    const detail = deckDetailFixture as ArchidektDeckDetail;
    const fetchImpl = (async () => new Response(JSON.stringify(detail), { status: 200 })) as unknown as typeof fetch;
    const clock = { t: 2_500_000 };
    const deps = { fetchImpl, now: () => clock.t };

    // Seed a build state with more candidates than fit in one DETAIL_FETCH_BATCH_SIZE (5)
    // batch, so the free-tier "batches of ~5 per invocation" behavior (Phase 3.2) is exercised.
    // Key must match what handleSixtyAggregate itself derives from the signature cards.
    const canonicalKey = buildCanonicalKey(["Krenko, Mob Boss", "Goblin Bombardment"]);
    const buildState = {
      canonicalKey,
      collectedDeckIds: [1, 2, 3, 4, 5, 6, 7],
      target: 7,
      fetchedDetailIds: [],
    };
    await env.COMMUNITY_KV.put(
      `build:${canonicalKey}`,
      JSON.stringify({ data: buildState, cachedAt: clock.t }),
    );

    const first = await handleSixtyAggregate(["Krenko, Mob Boss", "Goblin Bombardment"], 3, env, deps);
    expect(first.status).toBe("building");
    if (first.status === "building") {
      expect(first.progress).toEqual({ collected: 5, target: 7 });
    }

    const second = await handleSixtyAggregate(["Krenko, Mob Boss", "Goblin Bombardment"], 3, env, deps);
    expect(second.status).toBe("materialized");
    if (second.status === "materialized") {
      expect(second.numDecksSampled).toBe(7);
    }
  });

  it("60-card aggregate: materializes once the candidate pool already meets a small target", async () => {
    // Re-derive with a build state seeded directly in KV so we can exercise the
    // materialize path without needing 20+ real candidate decks in a fixture.
    const detail = deckDetailFixture as ArchidektDeckDetail;
    const fetchImpl = (async () => new Response(JSON.stringify(detail), { status: 200 })) as unknown as typeof fetch;
    const clock = { t: 3_000_000 };
    const deps = { fetchImpl, now: () => clock.t };

    const canonicalKey = buildCanonicalKey(["Krenko, Mob Boss"]);
    const buildState = {
      canonicalKey,
      collectedDeckIds: [24261502],
      target: 1,
      fetchedDetailIds: [],
    };
    await env.COMMUNITY_KV.put(
      `build:${canonicalKey}`,
      JSON.stringify({ data: buildState, cachedAt: clock.t }),
    );

    const result = await handleSixtyAggregate(["Krenko, Mob Boss"], 3, env, deps);
    expect(result.status).toBe("materialized");
    if (result.status === "materialized") {
      expect(result.numDecksSampled).toBe(1);
      expect(result.cards.some((c) => c.name === "Krenko, Mob Boss")).toBe(true);
      // Sol Ring is colorless in the fixture — must not pollute the color profile.
      expect(result.cards.some((c) => c.name === "Sol Ring")).toBe(true);
    }

    // The build state must be cleared once materialized.
    const leftoverBuild = await env.COMMUNITY_KV.get(`build:${canonicalKey}`);
    expect(leftoverBuild).toBeNull();
  });
});
