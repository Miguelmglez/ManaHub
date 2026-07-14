import type {
  AggregateCardEntry,
  CommanderAggregateSnapshot,
  EdhrecCommanderPayload,
} from "../types";

/** EDHREC slugs are lowercase-hyphenated names; partner commanders join with `-` (ADR-004 §3). */
export function slugifyCommanderName(name: string): string {
  return name
    .toLowerCase()
    .normalize("NFKD")
    .replace(/[̀-ͯ]/g, "")
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
}

/**
 * Normalizes a raw EDHREC commander payload into the Worker's snapshot contract.
 *
 * Per ADR-004 §3: `inclusionPct` is NOT a field on the upstream cardview — it is computed
 * as `num_decks / potential_decks`. `synergy` is used directly (EDHREC's own value is
 * already staple-dampened, e.g. Sol Ring ≈ -0.14, per D16/3.2 — no additional dampening
 * applied for the Commander path).
 */
export function normalizeCommanderAggregate(
  commanderName: string,
  payload: EdhrecCommanderPayload,
  cachedAt: number,
): CommanderAggregateSnapshot {
  const cards: AggregateCardEntry[] = [];
  let gameChangersCount = 0;

  for (const list of payload.container.json_dict.cardlists) {
    if (list.tag === "gamechangers") {
      gameChangersCount = list.cardviews.length;
    }
    for (const view of list.cardviews) {
      const inclusionPct =
        view.potential_decks > 0 ? view.num_decks / view.potential_decks : 0;
      cards.push({
        name: view.name,
        scryfallUid: view.id || null,
        inclusionPct,
        synergy: view.synergy,
        category: list.tag,
        numDecks: view.num_decks,
      });
    }
  }

  return {
    status: "materialized",
    source: "edhrec",
    commander: commanderName,
    numDecksSampled: payload.container.json_dict.card?.num_decks ?? 0,
    avgTypeDistribution: {
      creature: payload.creature,
      instant: payload.instant,
      sorcery: payload.sorcery,
      artifact: payload.artifact,
      enchantment: payload.enchantment,
      battle: payload.battle,
      planeswalker: payload.planeswalker,
      land: payload.land,
    },
    manaCurve: payload.panels.mana_curve,
    themeTags: payload.panels.taglinks.map((t) => ({ name: t.value, count: t.count })),
    similarCommanders: payload.similar,
    gameChangersCount,
    cards,
    cachedAt,
  };
}

/**
 * Rising-commander signal from EDHREC's own monthly `rank_over_time` (there is no weekly
 * granularity upstream — ADR-004 §3): true when the most recent two months show a
 * decreasing rank number (lower rank = more popular).
 */
export function isRisingFromRankOverTime(
  rankOverTime: EdhrecCommanderPayload["panels"]["rank_over_time"],
): boolean {
  const months = Object.keys(rankOverTime).sort();
  if (months.length < 2) return false;
  const lastMonth = months[months.length - 1]!;
  const prevMonth = months[months.length - 2]!;
  const last = rankOverTime[lastMonth];
  const prev = rankOverTime[prevMonth];
  if (!last || !prev) return false;
  return last.rank < prev.rank;
}
