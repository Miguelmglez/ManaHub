import { jsonResponse, preflightResponse } from "./lib/cors";
import { isoWeek, pruneOldMetaWeeks } from "./lib/weekly";
import { handleMetaWeekly } from "./routes/meta";
import { handleLimitedRatings } from "./routes/limited";
import { SUPPORTED_FORMATS, type Env, type ErrorResponse, type SupportedFormat } from "./types";

const META_PATH = /^\/meta\/([a-z]+)\/weekly$/;
const LIMITED_PATH = /^\/limited\/([A-Za-z0-9]+)\/ratings$/;

/**
 * manahub-competitive — Cloudflare Worker.
 *
 * Read-only public GET endpoints (CORS on, no auth, no PII):
 *   GET /meta/{format}/weekly       Weekly archetype/meta snapshot for a supported format
 *   GET /limited/{set}/ratings      17lands Limited card-ratings mirror (?format=PremierDraft)
 *   GET /health                     Liveness check
 *
 * See docs/competitive-feature-research.md for the design + source landscape. Mirrors
 * manahub-community's Worker structure/conventions (manual switch-router, KV+D1, cron).
 */
export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    if (request.method === "OPTIONS") return preflightResponse();
    if (request.method !== "GET" && request.method !== "HEAD") {
      return jsonResponse({ status: "error", message: "Method not allowed" } satisfies ErrorResponse, 405);
    }

    const url = new URL(request.url);
    try {
      if (url.pathname === "/health") {
        return jsonResponse({ status: "ok", worker: "manahub-competitive", time: Date.now() });
      }

      const metaMatch = url.pathname.match(META_PATH);
      if (metaMatch) return await routeMeta(metaMatch[1]!, env);

      const limitedMatch = url.pathname.match(LIMITED_PATH);
      if (limitedMatch) return await routeLimited(limitedMatch[1]!, url, env);

      return jsonResponse({ status: "error", message: "Not found" } satisfies ErrorResponse, 404);
    } catch (e) {
      // Log the real error server-side only — never echo raw exception details to an
      // unauthenticated caller (mirrors manahub-community's index.ts).
      console.error("manahub-competitive request failed:", e);
      return jsonResponse({ status: "error", message: "Competitive data temporarily unavailable" } satisfies ErrorResponse, 502);
    }
  },

  /**
   * Daily cron (see wrangler.toml `[triggers]`): prunes weekly history rows older than 8 weeks
   * and refreshes the meta snapshot for every supported format. Per-format try/catch — one
   * format failing must not abort the rest (mirrors manahub-community's scheduled handler).
   */
  async scheduled(_event: ScheduledEvent, env: Env): Promise<void> {
    const currentWeek = isoWeek(new Date());
    await pruneOldMetaWeeks(env.COMPETITIVE_DB, currentWeek).catch(() => undefined);
    for (const format of SUPPORTED_FORMATS) {
      try {
        await handleMetaWeekly(format, env);
      } catch {
        // Best-effort per-format refresh — one bad format must not abort the cron.
      }
    }
  },
};

async function routeMeta(formatParam: string, env: Env): Promise<Response> {
  if (!isSupportedFormat(formatParam)) {
    return jsonResponse(
      { status: "error", message: `Unsupported format. Use one of: ${SUPPORTED_FORMATS.join(", ")}` } satisfies ErrorResponse,
      400,
    );
  }
  return jsonResponse(await handleMetaWeekly(formatParam, env));
}

async function routeLimited(expansion: string, url: URL, env: Env): Promise<Response> {
  const format = url.searchParams.get("format") ?? "PremierDraft";
  return jsonResponse(await handleLimitedRatings(expansion, format, env));
}

function isSupportedFormat(value: string): value is SupportedFormat {
  return (SUPPORTED_FORMATS as readonly string[]).includes(value);
}
