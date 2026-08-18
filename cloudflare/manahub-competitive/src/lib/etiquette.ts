/**
 * Shared upstream-fetch etiquette for every source (17lands, MTGO, TopDeck.gg, Spicerack):
 * a descriptive User-Agent identifying the Worker + a contact, and a minimum spacing between
 * requests to the same provider within one invocation. Mirrors manahub-community's lib/etiquette.ts.
 */
export const USER_AGENT = "ManaHub-CompetitiveWorker/1.0 (contact: miguel.mglez@gmail.com)";

export const MIN_FETCH_INTERVAL_MS = 200;

/**
 * Waits (if needed) so that at least [minIntervalMs] has elapsed since [lastFetchAt],
 * then returns the new "last fetch" timestamp. Pure w.r.t. its clock/sleep dependencies
 * so it's testable without real wall-clock delays.
 */
export async function throttle(
  lastFetchAt: number,
  now: () => number,
  sleep: (ms: number) => Promise<void>,
  minIntervalMs: number = MIN_FETCH_INTERVAL_MS,
): Promise<number> {
  const elapsed = now() - lastFetchAt;
  if (elapsed < minIntervalMs) {
    await sleep(minIntervalMs - elapsed);
  }
  return now();
}

export function fetchHeaders(): HeadersInit {
  return { "User-Agent": USER_AGENT, Accept: "application/json" };
}
