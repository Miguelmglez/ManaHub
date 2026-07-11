/**
 * Archidekt's embedded oracle data returns full color names ("Blue", "Green", ...), not
 * WUBRG letters — verified live 2026-07-11, see docs/adr/ADR-004-community-api-contracts.md
 * §2. ManaHub's internal card model (and this Worker's normalized snapshot) uses compact
 * WUBRG-letter strings, so every Archidekt color list must pass through this map.
 */
const NAME_TO_LETTER: Record<string, string> = {
  White: "W",
  Blue: "U",
  Black: "B",
  Red: "R",
  Green: "G",
};

/** Maps a list of Archidekt color names to WUBRG letters, silently dropping unknown values. */
export function mapColorNamesToLetters(names: readonly string[]): string[] {
  const letters: string[] = [];
  for (const name of names) {
    const letter = NAME_TO_LETTER[name];
    if (letter) letters.push(letter);
  }
  return letters;
}
