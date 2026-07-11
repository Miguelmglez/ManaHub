import { describe, expect, it } from "vitest";
import { mapColorNamesToLetters } from "../src/lib/colorMap";

describe("mapColorNamesToLetters", () => {
  it("maps full Archidekt color names to WUBRG letters", () => {
    expect(mapColorNamesToLetters(["White", "Blue", "Black", "Red", "Green"])).toEqual([
      "W",
      "U",
      "B",
      "R",
      "G",
    ]);
  });

  it("silently drops unknown values rather than throwing", () => {
    expect(mapColorNamesToLetters(["Blue", "Colorless", ""])).toEqual(["U"]);
  });

  it("returns an empty array for an empty input", () => {
    expect(mapColorNamesToLetters([])).toEqual([]);
  });
});
