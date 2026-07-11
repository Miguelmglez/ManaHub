import { jsonResponse, preflightResponse } from "./lib/cors";
import { isoWeek, pruneOldWeeks, topForWeek, incrementCounter } from "./lib/trending";
import { handleCommanderAggregate } from "./routes/aggregateCommander";
import { handleSixtyAggregate } from "./routes/aggregateSixty";
import { handleSimilar } from "./routes/similar";
import { handleTrending } from "./routes/trending";
import type { Env, ErrorResponse } from "./types";

/**
 * manahub-community — Cloudflare Worker.
 *
 * Read-only public GET endpoints (CORS on, no auth, no PII — Phase 3.1):
 *   GET /v1/aggregate?commander=<name>&format=<fmt>        Commander (EDHREC-backed)
 *   GET /v1/aggregate?cards=<c1>,<c2>,<c3>&format=<fmt>     60-card (Archidekt-backed)
 *   GET /v1/similar?commander=<name>&limit=10
 *   GET /v1/trending?week=<iso-week>
 *
 * See docs/claude-code-prompt-deck-doctor-community.md Phase 3 and
 * docs/adr/ADR-004-community-api-contracts.md for the design + verified upstream contracts.
 */
export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    if (request.method === "OPTIONS") return preflightResponse();
    if (request.method !== "GET" && request.method !== "HEAD") {
      return jsonResponse({ status: "error", message: "Method not allowed" } satisfies ErrorResponse, 405);
    }

    const url = new URL(request.url);
    try {
      switch (url.pathname) {
        case "/v1/aggregate":
          return await routeAggregate(url, env);
        case "/v1/similar":
          return await routeSimilar(url, env);
        case "/v1/trending":
          return jsonResponse(await handleTrending(url.searchParams.get("week"), env));
        default:
          return jsonResponse({ status: "error", message: "Not found" } satisfies ErrorResponse, 404);
      }
    } catch (e) {
      // Log the real error server-side only — never echo raw exception details (upstream
      // URLs, D1/KV error shapes) to an unauthenticated caller (android-security-auditor
      // finding, Phase 3.4).
      console.error("manahub-community request failed:", e);
      const message = "Community data temporarily unavailable";
      return jsonResponse({ status: "error", message } satisfies ErrorResponse, 502);
    }
  },

  /**
   * Weekly cron (Monday 06:00 UTC, see wrangler.toml `[triggers]`): prunes trending counter
   * rows older than 8 weeks and pre-warms the top-10 trending commanders' KV cache so the
   * first request of the week isn't a cold-start EDHREC fetch. NOT scheduled/deployed by
   * this change (see the wrangler.toml provisioning note) — this handler exists so the
   * behavior is ready the moment a human enables the trigger.
   */
  async scheduled(_event: ScheduledEvent, env: Env): Promise<void> {
    const currentWeek = isoWeek(new Date());
    await pruneOldWeeks(env.COMMUNITY_DB, currentWeek);
    const top = await topForWeek(env.COMMUNITY_DB, currentWeek, "commander", 10);
    for (const { name } of top) {
      try {
        await handleCommanderAggregate(name, env);
      } catch {
        // Pre-warming is best-effort — one bad commander name must not abort the cron.
      }
    }
  },
};

async function routeAggregate(url: URL, env: Env): Promise<Response> {
  const commander = url.searchParams.get("commander");
  const cardsParam = url.searchParams.get("cards");
  const format = Number(url.searchParams.get("format") ?? "3");

  if (commander) {
    return jsonResponse(await handleCommanderAggregate(commander, env));
  }
  if (cardsParam) {
    const cards = cardsParam
      .split(",")
      .map((c) => c.trim())
      .filter((c) => c.length > 0)
      .slice(0, 3);
    if (cards.length === 0) {
      return jsonResponse({ status: "error", message: "cards must list at least one card" } satisfies ErrorResponse, 400);
    }
    return jsonResponse(await handleSixtyAggregate(cards, format, env));
  }
  return jsonResponse({ status: "error", message: "Provide either commander= or cards=" } satisfies ErrorResponse, 400);
}

async function routeSimilar(url: URL, env: Env): Promise<Response> {
  const commander = url.searchParams.get("commander");
  if (!commander) {
    return jsonResponse({ status: "error", message: "commander is required" } satisfies ErrorResponse, 400);
  }
  const limit = Number(url.searchParams.get("limit") ?? "10");
  return jsonResponse(await handleSimilar(commander, limit, env));
}

// Re-exported for the (currently unscheduled) cron's manual invocation in tests.
export { incrementCounter };
