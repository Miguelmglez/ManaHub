import { describe, expect, it } from "vitest";
import {
  buildCanonicalKey,
  intersectById,
  isTimeout,
  pickSignatureCards,
  sanitizeSummaries,
  SIXTY_CARD_SANITIZE,
  COMMANDER_SANITIZE,
  oracleColorIdentity,
} from "../src/lib/archidekt";
import type { ArchidektDeckDetail, ArchidektDeckSummary, ArchidektSearchResponse } from "../src/types";
import searchTimeoutFixture from "./fixtures/archidekt-search-timeout.json" with { type: "json" };
import searchPageFixture from "./fixtures/archidekt-search-page.json" with { type: "json" };

function deck(id: number, overrides: Partial<ArchidektDeckSummary> = {}): ArchidektDeckSummary {
  return {
    id,
    name: `Deck ${id}`,
    size: 100,
    deckFormat: 3,
    owner: { id: 1, username: "u", avatar: "" },
    viewCount: 5,
    createdAt: "",
    updatedAt: "",
    colors: {},
    tags: [],
    edhBracket: null,
    private: false,
    unlisted: false,
    theorycrafted: false,
    ...overrides,
  };
}

describe("isTimeout", () => {
  it("detects Archidekt's statement-timeout signal (count < 0)", () => {
    expect(isTimeout(searchTimeoutFixture as ArchidektSearchResponse)).toBe(true);
  });

  it("returns false for a normal page", () => {
    expect(isTimeout(searchPageFixture as ArchidektSearchResponse)).toBe(false);
  });
});

describe("sanitizeSummaries", () => {
  const raw = (searchPageFixture as ArchidektSearchResponse).results;

  it("excludes private, theorycrafted, out-of-band-size, and zero-view decks", () => {
    const result = sanitizeSummaries(raw, COMMANDER_SANITIZE);
    const ids = result.map((d) => d.id);
    // Fixture: 24261502 (size 100, public) and 15845995 (size 100, public) survive;
    // 24264353 is theorycrafted, 22912077 is private, 19510753 is size 12 (below band).
    expect(ids).toContain(24261502);
    expect(ids).toContain(15845995);
    expect(ids).not.toContain(24264353);
    expect(ids).not.toContain(22912077);
    expect(ids).not.toContain(19510753);
  });

  it("applies the 60-card size band (60-80), excluding a 100-card Commander deck", () => {
    const result = sanitizeSummaries(raw, SIXTY_CARD_SANITIZE);
    expect(result).toHaveLength(0);
  });
});

describe("intersectById", () => {
  it("returns the intersection across multiple per-signature-card searches", () => {
    const searchA = [deck(1), deck(2), deck(3)];
    const searchB = [deck(2), deck(3), deck(4)];
    const searchC = [deck(2), deck(3), deck(5)];
    expect(intersectById([searchA, searchB, searchC]).map((d) => d.id)).toEqual([2, 3]);
  });

  it("returns the single list unmodified for a single search", () => {
    const searchA = [deck(1), deck(2)];
    expect(intersectById([searchA]).map((d) => d.id)).toEqual([1, 2]);
  });

  it("returns empty for zero searches", () => {
    expect(intersectById([])).toEqual([]);
  });

  it("returns empty when there is no overlap", () => {
    expect(intersectById([[deck(1)], [deck(2)]])).toEqual([]);
  });
});

describe("pickSignatureCards", () => {
  it("picks the lowest-global-frequency cards, deterministic tie-break by name", () => {
    const freq = new Map([
      ["Sol Ring", 100],
      ["Krenko, Mob Boss", 5],
      ["Goblin Bombardment", 5],
      ["Rare Tech Card", 1],
    ]);
    const result = pickSignatureCards(
      ["Sol Ring", "Krenko, Mob Boss", "Goblin Bombardment", "Rare Tech Card"],
      freq,
      3,
    );
    // Rare Tech Card (1) first, then the two tied at 5 broken alphabetically.
    expect(result).toEqual(["Rare Tech Card", "Goblin Bombardment", "Krenko, Mob Boss"]);
  });

  it("treats unknown cards as frequency 0 (most distinctive)", () => {
    const result = pickSignatureCards(["Sol Ring", "Brand New Card"], new Map([["Sol Ring", 100]]), 1);
    expect(result).toEqual(["Brand New Card"]);
  });

  it("de-duplicates the input before ranking", () => {
    const result = pickSignatureCards(["A", "A", "B"], new Map(), 2);
    expect(result).toEqual(["A", "B"]);
  });
});

describe("buildCanonicalKey", () => {
  it("is order-independent and slug-safe", () => {
    const keyA = buildCanonicalKey(["Krenko, Mob Boss", "Goblin Bombardment"]);
    const keyB = buildCanonicalKey(["Goblin Bombardment", "Krenko, Mob Boss"]);
    expect(keyA).toBe(keyB);
    expect(keyA).toMatch(/^[a-z0-9-]+\+[a-z0-9-]+$/);
  });
});

describe("oracleColorIdentity", () => {
  it("maps Archidekt's full color names to WUBRG letters", () => {
    const entry: ArchidektDeckDetail["cards"][number] = {
      quantity: 1,
      categories: null,
      card: {
        uid: "x",
        oracleCard: {
          id: 1,
          uid: "x",
          name: "Test",
          cmc: 3,
          colorIdentity: ["Blue", "Green"],
          colors: ["Blue", "Green"],
          manaCost: "",
          types: [],
          text: "",
          legalities: {},
        },
      },
    };
    expect(oracleColorIdentity(entry)).toEqual(["U", "G"]);
  });

  it("returns an empty array for a colorless card", () => {
    const entry: ArchidektDeckDetail["cards"][number] = {
      quantity: 1,
      categories: null,
      card: { uid: "x", oracleCard: null },
    };
    expect(oracleColorIdentity(entry)).toEqual([]);
  });
});
