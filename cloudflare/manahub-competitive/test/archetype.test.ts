import { describe, expect, it } from "vitest";
import { classifyArchetypes, computeCardPlayRates } from "../src/lib/archetype";
import type { NormalizedDecklist } from "../src/types";

function deck(overrides: Partial<NormalizedDecklist> = {}): NormalizedDecklist {
  return {
    source: "mtgo",
    eventName: "Test League",
    eventUrl: null,
    player: "player1",
    format: "modern",
    result: "5-0",
    colors: ["R", "W"],
    cards: [
      { name: "Monastery Swiftspear", quantity: 4 },
      { name: "Lightning Bolt", quantity: 4 },
      { name: "Boros Charm", quantity: 3 },
      { name: "Mountain", quantity: 10 },
      { name: "Plains", quantity: 6 },
    ],
    ...overrides,
  };
}

describe("classifyArchetypes", () => {
  it("returns nothing for an empty batch", () => {
    expect(classifyArchetypes([])).toEqual([]);
  });

  it("groups decks sharing the same top-3 key-card signature into one archetype", () => {
    const decks = [deck({ player: "a" }), deck({ player: "b" }), deck({ player: "c" })];
    const groups = classifyArchetypes(decks);
    expect(groups).toHaveLength(1);
    expect(groups[0]!.deckCount).toBe(3);
    expect(groups[0]!.metaSharePct).toBe(100);
    expect(groups[0]!.label).toContain("Boros");
    // Lightning Bolt and Monastery Swiftspear tie at 4 copies — alphabetical tiebreak picks
    // "Lightning Bolt" first.
    expect(groups[0]!.label).toContain("Lightning Bolt");
  });

  it("splits decks with a different key-card signature into a separate archetype", () => {
    const aggro = deck({ player: "a" });
    const control = deck({
      player: "b",
      colors: ["U", "W"],
      cards: [
        { name: "Counterspell", quantity: 4 },
        { name: "Supreme Verdict", quantity: 4 },
        { name: "Teferi, Hero of Dominaria", quantity: 3 },
        { name: "Island", quantity: 8 },
        { name: "Plains", quantity: 4 },
      ],
    });
    const groups = classifyArchetypes([aggro, control]);
    expect(groups).toHaveLength(2);
    expect(groups.every((g) => g.deckCount === 1)).toBe(true);
    expect(groups.every((g) => g.metaSharePct === 50)).toBe(true);
  });

  it("excludes basic lands from the key-card signature and labels mono-color decks correctly", () => {
    const monoRed = deck({
      colors: ["R"],
      cards: [
        { name: "Monastery Swiftspear", quantity: 4 },
        { name: "Lightning Bolt", quantity: 4 },
        { name: "Mountain", quantity: 16 },
        { name: "Snow-Covered Mountain", quantity: 4 },
      ],
    });
    const [group] = classifyArchetypes([monoRed]);
    expect(group!.keyCards).not.toContain("Mountain");
    expect(group!.keyCards).not.toContain("Snow-Covered Mountain");
    expect(group!.label).toContain("Mono-Red");
  });

  it("caps the number of returned groups", () => {
    const decks: NormalizedDecklist[] = Array.from({ length: 20 }, (_, i) =>
      deck({ player: `p${i}`, cards: [{ name: `Unique Card ${i}`, quantity: 4 }] }),
    );
    expect(classifyArchetypes(decks, 5)).toHaveLength(5);
  });
});

describe("computeCardPlayRates", () => {
  it("returns nothing for an empty batch", () => {
    expect(computeCardPlayRates([])).toEqual([]);
  });

  it("computes the percentage of decks including each nonland card, sorted descending", () => {
    const decks = [
      deck({ player: "a" }),
      deck({ player: "b" }),
      deck({ player: "c", cards: [{ name: "Lightning Bolt", quantity: 4 }] }),
    ];
    const rates = computeCardPlayRates(decks);
    const bolt = rates.find((r) => r.name === "Lightning Bolt");
    expect(bolt?.playRatePct).toBeCloseTo(100);
    const swiftspear = rates.find((r) => r.name === "Monastery Swiftspear");
    expect(swiftspear?.playRatePct).toBeCloseTo((2 / 3) * 100);
    expect(rates.some((r) => r.name === "Mountain")).toBe(false);
  });
});
