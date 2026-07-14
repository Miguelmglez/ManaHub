/**
 * Shared upstream-fetch etiquette for BOTH providers (Archidekt + EDHREC), per Phase 3.2:
 * a descriptive User-Agent identifying the Worker + a contact, and a minimum spacing
 * between requests to the same provider within one invocation.
 */
export const USER_AGENT = "ManaHub-CommunityWorker/1.0 (contact: miguel.mglez@gmail.com)";

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
