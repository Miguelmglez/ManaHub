import { handleCommanderAggregate, type CommanderAggregateDeps, defaultDeps } from "./aggregateCommander";
import type { Env, SimilarDecksResponse } from "../types";

/**
 * Handles `GET /v1/similar?commander=<name>&limit=10`. Pre-ranks via EDHREC's own `similar`
 * list (client re-ranks by Jaccard + color-profile proximity per Phase 4 — out of scope
 * here, the Worker just surfaces EDHREC's ordering, already reasonably ranked upstream).
 */
export async function handleSimilar(
  commanderName: string,
  limit: number,
  env: Env,
  deps: CommanderAggregateDeps = defaultDeps,
): Promise<SimilarDecksResponse> {
  const snapshot = await handleCommanderAggregate(commanderName, env, deps);
  return {
    status: "ok",
    commander: commanderName,
    similar: snapshot.similarCommanders.slice(0, limit),
  };
}
