import type { DecklistCard, NormalizedDecklist, TrendingCard } from "../types";

/**
 * Lightweight v1 archetype classifier — NOT a port of Badaro/MTGOArchetypeParser (see
 * docs/competitive-feature-research.md §2c). Groups decks that share the same top-3 nonland
 * "key card" signature into one archetype bucket and labels it by color pair + top card.
 *
 * This trades naming precision (a real archetype parser knows "Boros Energy" vs. "Boros Aggro"
 * from curated per-format rule data) for zero external data dependencies. TODO: replace with a
 * port of MTGOArchetypeParser + MTGOFormatData once the MTGO/TopDeck/Spicerack sources are live
 * and there's real deck volume to validate classification quality against.
 */

const BASIC_LAND_NAMES = new Set(["Plains", "Island", "Swamp", "Mountain", "Forest", "Wastes"]);

function isBasicLand(name: string): boolean {
  const stripped = name.startsWith("Snow-Covered ") ? name.slice("Snow-Covered ".length) : name;
  return BASIC_LAND_NAMES.has(stripped);
}

const GUILD_LABELS: Record<string, string> = {
  WU: "Azorius",
  UB: "Dimir",
  BR: "Rakdos",
  RG: "Gruul",
  GW: "Selesnya",
  WB: "Orzhov",
  UR: "Izzet",
  BG: "Golgari",
  RW: "Boros",
  GU: "Simic",
};

const COLOR_ORDER = ["W", "U", "B", "R", "G"];

function colorLabel(colors: string[]): string {
  const normalized = Array.from(new Set(colors.map((c) => c.toUpperCase()))).filter((c) =>
    COLOR_ORDER.includes(c),
  );
  normalized.sort((a, b) => COLOR_ORDER.indexOf(a) - COLOR_ORDER.indexOf(b));
  if (normalized.length === 0) return "Colorless";
  if (normalized.length === 1) return `Mono-${colorName(normalized[0]!)}`;
  if (normalized.length === 2) {
    const key = normalized.join("");
    return GUILD_LABELS[key] ?? GUILD_LABELS[normalized.slice().reverse().join("")] ?? `${normalized.join("")}`;
  }
  return `${normalized.length}-Color (${normalized.join("")})`;
}

function colorName(letter: string): string {
  switch (letter) {
    case "W":
      return "White";
    case "U":
      return "Blue";
    case "B":
      return "Black";
    case "R":
      return "Red";
    case "G":
      return "Green";
    default:
      return letter;
  }
}

function keyCardsForDeck(cards: DecklistCard[], topN = 4): string[] {
  return cards
    .filter((c) => !isBasicLand(c.name))
    .slice()
    .sort((a, b) => b.quantity - a.quantity || a.name.localeCompare(b.name))
    .slice(0, topN)
    .map((c) => c.name);
}

function signatureKey(keyCards: string[]): string {
  return keyCards
    .slice(0, 3)
    .map((c) => c.toLowerCase())
    .sort()
    .join("|");
}

export interface ArchetypeGroup {
  key: string;
  label: string;
  colors: string[];
  keyCards: string[];
  deckCount: number;
  metaSharePct: number;
  representativeDeck: NormalizedDecklist | null;
}

/** Groups a batch of normalized decklists into archetype buckets, sorted by size descending. */
export function classifyArchetypes(decks: NormalizedDecklist[], maxGroups = 12): ArchetypeGroup[] {
  if (decks.length === 0) return [];

  const groups = new Map<string, { decks: NormalizedDecklist[]; keyCards: string[] }>();
  for (const deck of decks) {
    const keyCards = keyCardsForDeck(deck.cards);
    if (keyCards.length === 0) continue;
    const key = signatureKey(keyCards);
    const existing = groups.get(key);
    if (existing) {
      existing.decks.push(deck);
    } else {
      groups.set(key, { decks: [deck], keyCards });
    }
  }

  const total = decks.length;
  const result: ArchetypeGroup[] = [];
  for (const [key, { decks: groupDecks, keyCards }] of groups) {
    const representativeDeck = groupDecks[0] ?? null;
    const colors = representativeDeck?.colors ?? [];
    result.push({
      key,
      label: `${colorLabel(colors)} — ${keyCards[0] ?? "Unknown"}`,
      colors,
      keyCards,
      deckCount: groupDecks.length,
      metaSharePct: (groupDecks.length / total) * 100,
      representativeDeck,
    });
  }

  result.sort((a, b) => b.deckCount - a.deckCount);
  return result.slice(0, maxGroups);
}

/** Computes per-card play rate (% of sampled decks including at least one copy). */
export function computeCardPlayRates(decks: NormalizedDecklist[], maxCards = 20): TrendingCard[] {
  if (decks.length === 0) return [];
  const counts = new Map<string, number>();
  for (const deck of decks) {
    const seen = new Set<string>();
    for (const card of deck.cards) {
      if (isBasicLand(card.name)) continue;
      seen.add(card.name);
    }
    for (const name of seen) {
      counts.set(name, (counts.get(name) ?? 0) + 1);
    }
  }
  const total = decks.length;
  const rows: TrendingCard[] = Array.from(counts.entries()).map(([name, count]) => ({
    name,
    playRatePct: (count / total) * 100,
    deltaPct: null,
  }));
  rows.sort((a, b) => b.playRatePct - a.playRatePct || a.name.localeCompare(b.name));
  return rows.slice(0, maxCards);
}
