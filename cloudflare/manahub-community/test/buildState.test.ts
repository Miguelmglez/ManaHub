import { describe, expect, it } from "vitest";
import {
  addCandidateIds,
  isComplete,
  markDetailsFetched,
  nextDetailBatch,
  progressOf,
  readyToMaterialize,
  startBuild,
} from "../src/lib/buildState";

describe("incremental build state machine", () => {
  it("starts empty and not complete", () => {
    const state = startBuild("key", 20);
    expect(isComplete(state)).toBe(false);
    expect(readyToMaterialize(state)).toBe(false);
    expect(progressOf(state)).toEqual({ collected: 0, target: 20 });
  });

  it("de-duplicates candidate ids across batches", () => {
    let state = startBuild("key", 20);
    state = addCandidateIds(state, [1, 2, 3]);
    state = addCandidateIds(state, [2, 3, 4]);
    expect(state.collectedDeckIds.sort()).toEqual([1, 2, 3, 4]);
  });

  it("is complete once enough candidates are collected, but not ready to materialize until details are fetched", () => {
    let state = startBuild("key", 2);
    state = addCandidateIds(state, [1, 2]);
    expect(isComplete(state)).toBe(true);
    expect(readyToMaterialize(state)).toBe(false);
  });

  it("nextDetailBatch returns only un-fetched ids, capped at the batch size", () => {
    let state = startBuild("key", 10);
    state = addCandidateIds(state, [1, 2, 3, 4, 5, 6, 7]);
    const batch1 = nextDetailBatch(state, 5);
    expect(batch1).toEqual([1, 2, 3, 4, 5]);

    state = markDetailsFetched(state, batch1);
    const batch2 = nextDetailBatch(state, 5);
    expect(batch2).toEqual([6, 7]);
  });

  it("nextDetailBatch never exceeds the target even if more candidates exist", () => {
    let state = startBuild("key", 3);
    state = addCandidateIds(state, [1, 2, 3, 4, 5]);
    const batch = nextDetailBatch(state, 10);
    expect(batch).toEqual([1, 2, 3]);
  });

  it("readyToMaterialize is true once fetchedDetailIds covers the target", () => {
    let state = startBuild("key", 2);
    state = addCandidateIds(state, [1, 2, 3]);
    state = markDetailsFetched(state, [1, 2]);
    expect(readyToMaterialize(state)).toBe(true);
  });

  it("readyToMaterialize is false with zero candidates (never materializes an empty snapshot)", () => {
    const state = startBuild("key", 0);
    expect(readyToMaterialize(state)).toBe(false);
  });

  it("progressOf reports fetched-detail count, not just candidate count", () => {
    let state = startBuild("key", 5);
    state = addCandidateIds(state, [1, 2, 3, 4, 5]);
    state = markDetailsFetched(state, [1, 2]);
    expect(progressOf(state)).toEqual({ collected: 2, target: 5 });
  });
});
