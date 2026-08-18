import { fetchHeaders } from "../etiquette";
import type { DecklistCard, Env, NormalizedDecklist, SourceResult, SupportedFormat } from "../../types";

interface TopDeckStandingRow {
  standing?: number;
  player?: string;
  decklist?: { mainboard?: Record<string, number> } | string | null;
}

interface TopDeckTournamentSummary {
  TID: string;
  name: string;
  game: string;
  format?: string;
}

interface TopDeckTournamentDetail {
  TID: string;
  name: string;
  standings?: TopDeckStandingRow[];
}

/**
 * TopDeck.gg decklist source — https://topdeck.gg/docs/tournaments-v2 (free API key,
 * `Authorization` header, ~100 req/min). Written against the documented contract
 * (competitive-feature-research.md §2d); NOT yet exercised against a live key — no-ops with a
 * `skipped` result until `env.TOPDECK_API_KEY` is set via `wrangler secret put` (research-doc §7
 * action item 2). Mandatory per TopDeck's terms: any UI that renders this data MUST show visible
 * attribution linking back to TopDeck.gg.
 */
export async function fetchTopDeckDecklists(
  format: SupportedFormat,
  env: Env,
  fetchImpl: typeof fetch = fetch,
): Promise<SourceResult<NormalizedDecklist[]>> {
  const apiKey = env.TOPDECK_API_KEY;
  if (!apiKey) {
    return {
      ok: false,
      skipped: true,
      reason: "TOPDECK_API_KEY not configured (competitive-feature-research.md §7 action item 2)",
    };
  }

  let searchResponse: Response;
  try {
    searchResponse = await fetchImpl("https://api.topdeck.gg/v2/tournaments", {
      method: "POST",
      headers: { ...fetchHeaders(), Authorization: apiKey, "Content-Type": "application/json" },
      body: JSON.stringify({ game: "Magic: The Gathering", format, last: 10 }),
    });
  } catch (e) {
    return { ok: false, skipped: false, error: `TopDeck.gg search threw: ${(e as Error).message}` };
  }
  if (!searchResponse.ok) {
    return { ok: false, skipped: false, error: `TopDeck.gg search failed: HTTP ${searchResponse.status}` };
  }

  const tournaments = (await searchResponse.json()) as TopDeckTournamentSummary[];
  const decklists: NormalizedDecklist[] = [];

  for (const tournament of tournaments.slice(0, 10)) {
    try {
      const detailResponse = await fetchImpl(`https://api.topdeck.gg/v2/tournaments/${tournament.TID}`, {
        headers: { ...fetchHeaders(), Authorization: apiKey },
      });
      if (!detailResponse.ok) continue;
      const detail = (await detailResponse.json()) as TopDeckTournamentDetail;
      for (const row of detail.standings ?? []) {
        const cards = normalizeMainboard(row.decklist);
        if (!row.player || cards.length === 0) continue;
        decklists.push({
          source: "topdeck",
          eventName: tournament.name,
          eventUrl: `https://topdeck.gg/event/${tournament.TID}`,
          player: row.player,
          format,
          result: row.standing ? `#${row.standing}` : null,
          colors: [],
          cards,
        });
      }
    } catch {
      // One bad tournament must not abort the whole fetch — best-effort per-item try/catch,
      // matching manahub-community's cron pre-warm pattern.
    }
  }

  return { ok: true, data: decklists };
}

function normalizeMainboard(decklist: TopDeckStandingRow["decklist"]): DecklistCard[] {
  if (!decklist || typeof decklist === "string" || !decklist.mainboard) return [];
  return Object.entries(decklist.mainboard).map(([name, quantity]) => ({ name, quantity }));
}
