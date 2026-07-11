/**
 * Incremental-build state machine for the 60-card Archidekt aggregate path (Phase 3.2).
 *
 * On a cache miss the Worker cannot fetch + parse 20-25 full deck details within one
 * free-tier invocation's CPU budget, so it collects a batch of deck ids per invocation and
 * persists progress in KV under `build:<canonicalKey>` until it reaches the target sample
 * size, at which point the caller materializes the snapshot and the build state is
 * discarded. Pure reducer — no KV/network access in this module, so it's fully unit
 * testable; the route layer is the only thing that touches KV.
 */

export const TARGET_SAMPLE_SIZE_MIN = 20;
export const TARGET_SAMPLE_SIZE_MAX = 25;
export const DETAIL_FETCH_BATCH_SIZE = 5;

export interface BuildState {
  canonicalKey: string;
  /** Candidate deck ids found by intersecting per-signature-card searches. */
  collectedDeckIds: number[];
  target: number;
  /** Deck ids whose detail payload has already been fetched + cached in this build. */
  fetchedDetailIds: number[];
}

export function startBuild(canonicalKey: string, target: number): BuildState {
  return { canonicalKey, collectedDeckIds: [], target, fetchedDetailIds: [] };
}

/** Merges a newly-fetched batch of candidate deck ids into the build state (de-duplicated). */
export function addCandidateIds(state: BuildState, newIds: readonly number[]): BuildState {
  const merged = new Set(state.collectedDeckIds);
  for (const id of newIds) merged.add(id);
  return { ...state, collectedDeckIds: Array.from(merged) };
}

/** True once enough candidate deck ids have been found (does NOT imply details are fetched). */
export function isComplete(state: BuildState): boolean {
  return state.collectedDeckIds.length >= state.target;
}

/** The next batch of deck ids (up to [DETAIL_FETCH_BATCH_SIZE]) whose detail hasn't been fetched yet. */
export function nextDetailBatch(
  state: BuildState,
  batchSize: number = DETAIL_FETCH_BATCH_SIZE,
): number[] {
  const fetched = new Set(state.fetchedDetailIds);
  const target = Math.min(state.target, state.collectedDeckIds.length);
  const pending = state.collectedDeckIds.slice(0, target).filter((id) => !fetched.has(id));
  return pending.slice(0, batchSize);
}

/** Records a batch of deck ids as having their detail fetched + cached. */
export function markDetailsFetched(state: BuildState, ids: readonly number[]): BuildState {
  const merged = new Set(state.fetchedDetailIds);
  for (const id of ids) merged.add(id);
  return { ...state, fetchedDetailIds: Array.from(merged) };
}

/** True once every targeted candidate's detail has been fetched — ready to materialize. */
export function readyToMaterialize(state: BuildState): boolean {
  const target = Math.min(state.target, state.collectedDeckIds.length);
  return target > 0 && state.fetchedDetailIds.length >= target;
}

export function progressOf(state: BuildState): { collected: number; target: number } {
  return { collected: state.fetchedDetailIds.length, target: state.target };
}
