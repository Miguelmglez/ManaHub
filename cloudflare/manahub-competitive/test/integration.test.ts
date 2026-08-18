import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { Miniflare } from "miniflare";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import worker from "../src/index";
import { handleMetaWeekly } from "../src/routes/meta";
import { handleLimitedRatings } from "../src/routes/limited";
import type { Env } from "../src/types";
import ratingsFixture from "./fixtures/17lands-card-ratings.json" with { type: "json" };

/** Real KV + D1 emulation via standalone Miniflare (mirrors manahub-community's integration test). */
describe("Worker routes against emulated KV + D1 (Miniflare)", () => {
  let mf: Miniflare;
  let env: Env;

  beforeEach(async () => {
    const schemaPath = fileURLToPath(new URL("../src/schema.sql", import.meta.url));
    const schema = readFileSync(schemaPath, "utf-8");
    mf = new Miniflare({
      modules: true,
      script: "export default { async fetch() { return new Response('ok'); } };",
      kvNamespaces: ["COMPETITIVE_KV"],
      d1Databases: ["COMPETITIVE_DB"],
    });
    const kv = await mf.getKVNamespace("COMPETITIVE_KV");
    const db = await mf.getD1Database("COMPETITIVE_DB");
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
    env = { COMPETITIVE_KV: kv, COMPETITIVE_DB: db } as unknown as Env;
  });

  afterEach(async () => {
    await mf.dispose();
  });

  it("meta weekly: with no secrets configured, reports every source skipped and an honest empty snapshot", async () => {
    const clock = { t: Date.UTC(2026, 6, 11) };
    const snapshot = await handleMetaWeekly("modern", env, { now: () => clock.t, fetchImpl: fetch });

    expect(snapshot.status).toBe("ok");
    expect(snapshot.numDecksSampled).toBe(0);
    expect(snapshot.archetypes).toEqual([]);
    expect(snapshot.sources.every((s) => s.active === false)).toBe(true);
    const mtgo = snapshot.sources.find((s) => s.name === "mtgo");
    expect(mtgo?.reason).toContain("action item 6");
    const topdeck = snapshot.sources.find((s) => s.name === "topdeck");
    expect(topdeck?.reason).toContain("TOPDECK_API_KEY");
  });

  it("meta weekly: warm hit does not recompute (cached in KV)", async () => {
    const clock = { t: Date.UTC(2026, 6, 11) };
    const deps = { now: () => clock.t, fetchImpl: fetch };
    const first = await handleMetaWeekly("modern", env, deps);
    clock.t += 1000;
    const second = await handleMetaWeekly("modern", env, deps);
    expect(second.cachedAt).toBe(first.cachedAt);
  });

  it("limited ratings: cold miss fetches 17lands, caches in KV, warm hit skips the network", async () => {
    let fetchCount = 0;
    const fetchImpl = (async () => {
      fetchCount += 1;
      return new Response(JSON.stringify(ratingsFixture), { status: 200 });
    }) as unknown as typeof fetch;
    const clock = { t: 1_000_000 };
    const deps = { now: () => clock.t, seventeenLands: { fetchImpl } };

    const first = await handleLimitedRatings("BLB", "PremierDraft", env, deps);
    expect(first.status).toBe("ok");
    expect(first.cards).toHaveLength(2);
    expect(fetchCount).toBe(1);

    clock.t += 1000;
    const second = await handleLimitedRatings("BLB", "PremierDraft", env, deps);
    expect(second.cachedAt).toBe(first.cachedAt);
    expect(fetchCount).toBe(1);
  });

  it("index.ts fetch handler: /health, unsupported format, and 404 routing", async () => {
    const health = await worker.fetch(new Request("https://example.com/health"), env);
    expect(health.status).toBe(200);
    expect((await health.json()) as { status: string }).toMatchObject({ status: "ok" });

    const badFormat = await worker.fetch(new Request("https://example.com/meta/commander/weekly"), env);
    expect(badFormat.status).toBe(400);

    const notFound = await worker.fetch(new Request("https://example.com/nope"), env);
    expect(notFound.status).toBe(404);

    const preflight = await worker.fetch(
      new Request("https://example.com/meta/modern/weekly", { method: "OPTIONS" }),
      env,
    );
    expect(preflight.status).toBe(204);
  });
});
