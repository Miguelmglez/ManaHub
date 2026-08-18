import { fetchHeaders } from "../etiquette";
import type { DecklistCard, Env, NormalizedDecklist, SourceResult, SupportedFormat } from "../../types";

interface SpicerackDecklistRow {
  event_name?: string;
  event_url?: string;
  player_name?: string;
  standing?: string;
  format?: string;
  decklist_as_text?: string;
}

/**
 * Spicerack decklist source — https://docs.spicerack.gg/api-reference/public-decklist-database
 * (beta, `X-API-Key` header, org-admin-issued key). Written against the documented contract
 * (competitive-feature-research.md §2e); NOT yet exercised against a live key — no-ops with a
 * `skipped` result until `env.SPICERACK_API_KEY` is set via `wrangler secret put` (research-doc
 * §7 action item 3, which also needs Miguel to confirm a consumer-app key is even obtainable).
 */
export async function fetchSpicerackDecklists(
  format: SupportedFormat,
  env: Env,
  fetchImpl: typeof fetch = fetch,
): Promise<SourceResult<NormalizedDecklist[]>> {
  const apiKey = env.SPICERACK_API_KEY;
  if (!apiKey) {
    return {
      ok: false,
      skipped: true,
      reason: "SPICERACK_API_KEY not configured (competitive-feature-research.md §7 action item 3)",
    };
  }

  const params = new URLSearchParams({
    num_days: "14",
    event_format: format,
    decklist_as_text: "true",
  });

  let response: Response;
  try {
    response = await fetchImpl(`https://api.spicerack.gg/api/export-decklists/?${params.toString()}`, {
      headers: { ...fetchHeaders(), "X-API-Key": apiKey },
    });
  } catch (e) {
    return { ok: false, skipped: false, error: `Spicerack fetch threw: ${(e as Error).message}` };
  }
  if (!response.ok) {
    return { ok: false, skipped: false, error: `Spicerack fetch failed: HTTP ${response.status}` };
  }

  const rows = (await response.json()) as SpicerackDecklistRow[];
  const decklists = rows
    .map((row): NormalizedDecklist | null => {
      const cards = parseDecklistText(row.decklist_as_text);
      if (!row.player_name || cards.length === 0) return null;
      return {
        source: "spicerack",
        eventName: row.event_name ?? "Unknown event",
        eventUrl: row.event_url ?? null,
        player: row.player_name,
        format,
        result: row.standing ?? null,
        colors: [],
        cards,
      };
    })
    .filter((d): d is NormalizedDecklist => d !== null);

  return { ok: true, data: decklists };
}

/** Parses a "4 Lightning Bolt\n2 Counterspell\n..." style plain-text decklist into card rows. */
function parseDecklistText(text: string | undefined): DecklistCard[] {
  if (!text) return [];
  const cards: DecklistCard[] = [];
  for (const line of text.split("\n")) {
    const match = line.trim().match(/^(\d+)x?\s+(.+)$/);
    if (!match) continue;
    const quantity = Number(match[1]);
    const name = match[2]?.trim();
    if (!name || !Number.isFinite(quantity) || quantity <= 0) continue;
    cards.push({ name, quantity });
  }
  return cards;
}
