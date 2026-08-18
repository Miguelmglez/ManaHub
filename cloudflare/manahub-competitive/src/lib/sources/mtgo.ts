import type { NormalizedDecklist, SourceResult, SupportedFormat } from "../../types";

/**
 * MTGO decklist source — STAYS IN SKIP MODE for the whole of this implementation pass.
 *
 * docs/competitive-feature-research.md §2c/§7 flags mtgo.com/decklists as an SPA whose JSON
 * endpoint shape must be verified fresh in a browser network tab before coding a fetcher against
 * it (the community mirror that used to do this, Badaro/MTGODecklistCache, shut down in 2025-06
 * after mtgo.com changed its contract). That verification is research-doc §7 action item 6,
 * explicitly reserved for Miguel to do himself in parallel with this implementation.
 *
 * Guessing the endpoint here would risk shipping a fetcher against the wrong contract (or, worse,
 * an unverified scrape of an SPA that may not want automated JSON access at all) — so this
 * fetcher deliberately always returns `skipped`. Once Miguel confirms the verified
 * request/response shape (and/or a registered Daybreak Census service id, `env.DAYBREAK_SERVICE_ID`,
 * per action item 1), replace this function's body with a real fetch — no caller changes needed,
 * `SourceResult<NormalizedDecklist[]>` is already the contract `src/routes/meta.ts` expects.
 */
export async function fetchMtgoDecklists(
  _format: SupportedFormat,
): Promise<SourceResult<NormalizedDecklist[]>> {
  return {
    ok: false,
    skipped: true,
    reason:
      "mtgo.com decklist JSON contract not yet verified (competitive-feature-research.md §7 action item 6)",
  };
}
