/**
 * 60-card staple dampening (Phase 3.2): synergy = inclusionPct - globalInclusionPct, where
 * globalInclusionPct comes from a D1 table maintained across every materialized 60-card
 * snapshot. Bootstrapped with a static list of ubiquitous staples so the very first
 * snapshots (before the D1 table has accumulated data) don't score Sol Ring-tier cards as
 * "highly synergistic" just because they're common everywhere.
 *
 * These are rough global-inclusion estimates for well-known constructed staples — NOT
 * derived from a live crawl. They exist only to avoid a cold-start false-positive; D1 data
 * supersedes them for any card the table has real counts for (see trending.ts /
 * globalInclusionFor).
 */
export const STATIC_STAPLE_BOOTSTRAP: Record<string, number> = {
  "Sol Ring": 0.85,
  "Command Tower": 0.75,
  "Arcane Signet": 0.55,
  "Swords to Plowshares": 0.3,
  "Lightning Bolt": 0.25,
  "Counterspell": 0.15,
};

/** synergy = inclusionPct - globalInclusionPct, clamped to a sane display range. */
export function computeSynergy60(
  inclusionPct: number,
  globalInclusionPct: number,
): number {
  const raw = inclusionPct - globalInclusionPct;
  return Math.max(-1, Math.min(1, raw));
}

/** Looks up the D1-backed global inclusion, falling back to the static bootstrap, then 0. */
export function globalInclusionFor(
  cardName: string,
  d1Inclusions: ReadonlyMap<string, number>,
): number {
  return d1Inclusions.get(cardName) ?? STATIC_STAPLE_BOOTSTRAP[cardName] ?? 0;
}
