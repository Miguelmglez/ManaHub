// hydrate-card-metadata
//
// Fills public.card_search_index with server-trusted Scryfall metadata for every scryfall_id
// referenced by a live collection row or a wishlist (missing, stale after 30 days, or an
// unresolved row whose next_retry_at has passed). Clients never write that table, so a user
// cannot poison card names seen in other users' searches.
//
// Invoked by pg_cron (public.trigger_card_metadata_hydration) with an `x-cron-secret` header
// validated against Vault, or manually with the service-role key as Bearer token. Deployed with
// verify_jwt=false because pg_cron sends no JWT; both paths are checked below.
//
// One run at a time: a DB lease (card_hydration_acquire/release) — not pg_advisory_lock, which
// PostgREST's pooled, per-request transactions cannot hold across the whole run.
//
// Scryfall etiquette: POST /cards/collection (<=75 ids), sequential, >=100 ms between requests,
// descriptive User-Agent + Accept, one Retry-After-honoring retry on 429 then the run ends,
// bounded ids and wall-clock per run.
import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "jsr:@supabase/supabase-js@2";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "";
const SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";

const SCRYFALL_COLLECTION_URL = "https://api.scryfall.com/cards/collection";
const SCRYFALL_HEADERS = {
  "User-Agent": "ManaHub/1.0 (card metadata indexer)",
  "Accept": "application/json",
  "Content-Type": "application/json",
};
const BATCH_SIZE = 75;
const MAX_IDS_PER_RUN = 1500;
const MIN_REQUEST_GAP_MS = 150;
const MAX_5XX_RETRIES = 2;
const MAX_RETRY_AFTER_MS = 30_000;
const RUN_BUDGET_MS = 110_000;
const LEASE_TTL_SECONDS = 180;
const NOT_FOUND_RETRY = "30 days";
const REJECTED_RETRY = "7 days";
const CRON_SECRET_PATTERN = /^[0-9a-f]{64}$/;

const COLOR_BITS: Record<string, number> = { W: 1, U: 2, B: 4, R: 8, G: 16 };

const admin = createClient(SUPABASE_URL, SERVICE_ROLE_KEY, {
  auth: { autoRefreshToken: false, persistSession: false },
});

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

function json(data: unknown, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function timingSafeEqual(a: string, b: string): boolean {
  if (!a || !b || a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return diff === 0;
}

async function sha256Hex(input: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(input));
  return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

async function isAuthorized(req: Request): Promise<boolean> {
  const bearer = (req.headers.get("authorization") ?? "").replace(/^Bearer\s+/i, "");
  if (bearer && timingSafeEqual(bearer, SERVICE_ROLE_KEY)) return true;

  const cronSecret = req.headers.get("x-cron-secret") ?? "";
  if (!CRON_SECRET_PATTERN.test(cronSecret)) return false;
  // Only the digest crosses the wire, so the DB-side equality leaks nothing an attacker can steer.
  const { data, error } = await admin.rpc("verify_card_hydration_secret", {
    p_secret_sha256: await sha256Hex(cronSecret),
  });
  return !error && data === true;
}

// deno-lint-ignore no-explicit-any
type ScryCard = Record<string, any>;

function maskOf(letters: string[] | undefined): number {
  return (letters ?? []).reduce((m, c) => m | (COLOR_BITS[c?.toUpperCase()] ?? 0), 0);
}

function intOrNull(v: unknown): number | null {
  return typeof v === "string" && /^-?\d+$/.test(v) ? parseInt(v, 10) : null;
}

function toRow(card: ScryCard) {
  const faces: ScryCard[] = Array.isArray(card.card_faces) ? card.card_faces : [];
  const first = faces[0] ?? {};

  const colors: string[] = Array.isArray(card.colors)
    ? card.colors
    : [...new Set(faces.flatMap((f) => (Array.isArray(f.colors) ? f.colors : [])))];

  const typeLine: string | null = card.type_line ??
    (faces.length ? faces.map((f) => f.type_line ?? "").join(" // ") : null);

  const oracleText: string | null = card.oracle_text ??
    (faces.length ? faces.map((f) => f.oracle_text ?? "").join("\n//\n") : null);

  const faceNames = [card.name, ...faces.map((f) => f.name)]
    .filter((n): n is string => typeof n === "string" && n.length > 0)
    .map((n) => n.toLowerCase());

  const searchParts = [
    card.name,
    card.printed_name,
    ...faces.map((f) => f.name),
    ...faces.map((f) => f.printed_name),
  ].filter((n): n is string => typeof n === "string" && n.length > 0);

  const cmc: number | null = typeof card.cmc === "number"
    ? card.cmc
    : (typeof first.cmc === "number" ? first.cmc : null);

  return {
    scryfall_id: card.id,
    status: "ok",
    oracle_id: card.oracle_id ?? first.oracle_id ?? null,
    name: card.name,
    printed_name: card.printed_name ?? null,
    search_name: [...new Set(searchParts)].join(" ").toLowerCase(),
    face_names: [...new Set(faceNames)],
    type_line: typeLine,
    oracle_text: oracleText,
    colors,
    color_identity: Array.isArray(card.color_identity) ? card.color_identity : [],
    color_mask: maskOf(colors),
    identity_mask: maskOf(card.color_identity),
    cmc,
    mana_value: cmc === null ? null : Math.floor(cmc),
    power_num: intOrNull(card.power ?? first.power),
    toughness_num: intOrNull(card.toughness ?? first.toughness),
    rarity: typeof card.rarity === "string" ? card.rarity.toLowerCase() : null,
    set_code: typeof card.set === "string" ? card.set.toLowerCase() : null,
    lang: card.lang ?? null,
    legalities: card.legalities ?? {},
    hydrated_at: new Date().toISOString(),
    next_retry_at: null,
  };
}

type FetchResult =
  | { kind: "ok"; cards: ScryCard[]; notFound: string[] }
  | { kind: "rejected"; status: number }
  | { kind: "stop"; reason: string };

class Run {
  readonly startedAt = Date.now();
  requests = 0;
  hydrated = 0;
  notFound = 0;
  rejected = 0;
  stoppedReason: string | null = null;

  overBudget(): boolean {
    return Date.now() - this.startedAt > RUN_BUDGET_MS;
  }

  async post(body: string): Promise<Response> {
    if (this.requests > 0) await sleep(MIN_REQUEST_GAP_MS);
    this.requests++;
    return await fetch(SCRYFALL_COLLECTION_URL, { method: "POST", headers: SCRYFALL_HEADERS, body });
  }

  async fetchBatch(ids: string[]): Promise<FetchResult> {
    const body = JSON.stringify({ identifiers: ids.map((id) => ({ id })) });
    let honored429 = false;
    let serverErrors = 0;
    while (true) {
      const res = await this.post(body);
      if (res.ok) {
        const payload = await res.json();
        const cards: ScryCard[] = Array.isArray(payload.data) ? payload.data : [];
        const notFound: string[] = (Array.isArray(payload.not_found) ? payload.not_found : [])
          .map((nf: ScryCard) => nf?.id)
          .filter((id: unknown): id is string => typeof id === "string");
        return { kind: "ok", cards, notFound };
      }
      await res.body?.cancel();

      if (res.status === 429) {
        // One Retry-After-honoring retry, then the run ends: hammering a throttled API only deepens it.
        const retryAfterMs = Number(res.headers.get("retry-after")) * 1000;
        const waitMs = Number.isFinite(retryAfterMs) && retryAfterMs > 0
          ? Math.min(retryAfterMs, MAX_RETRY_AFTER_MS)
          : 1000;
        if (honored429 || Date.now() - this.startedAt + waitMs > RUN_BUDGET_MS) {
          return { kind: "stop", reason: "rate_limited" };
        }
        honored429 = true;
        await sleep(waitMs);
        continue;
      }
      if (res.status >= 500) {
        if (serverErrors >= MAX_5XX_RETRIES) return { kind: "stop", reason: `http_${res.status}` };
        serverErrors++;
        await sleep(1000 * 2 ** serverErrors);
        continue;
      }
      return { kind: "rejected", status: res.status };
    }
  }

  // Marks ids unresolved without ever downgrading an existing status='ok' row (the RPC enforces it).
  async markUnresolved(ids: string[], status: "not_found" | "failed", retryAfter: string): Promise<boolean> {
    if (ids.length === 0) return true;
    const { error } = await admin.rpc("card_search_index_mark_unresolved", {
      p_ids: ids,
      p_status: status,
      p_retry_after: retryAfter,
    });
    return !error;
  }

  // A non-429 4xx is bisected down to the offending id(s), which alone back off for REJECTED_RETRY.
  async process(ids: string[]): Promise<void> {
    if (this.stoppedReason) return;
    if (this.overBudget()) {
      this.stoppedReason = "time_budget";
      return;
    }

    const result = await this.fetchBatch(ids);
    if (result.kind === "stop") {
      this.stoppedReason = result.reason;
      return;
    }
    if (result.kind === "rejected") {
      if (ids.length === 1) {
        if (!(await this.markUnresolved(ids, "failed", REJECTED_RETRY))) this.stoppedReason = "mark_failed";
        this.rejected++;
        return;
      }
      const mid = Math.ceil(ids.length / 2);
      await this.process(ids.slice(0, mid));
      await this.process(ids.slice(mid));
      return;
    }

    // Deduplicated: ON CONFLICT cannot touch the same row twice in one statement.
    const byId = new Map<string, Record<string, unknown>>();
    for (const c of result.cards) {
      if (typeof c.id === "string" && typeof c.name === "string") byId.set(c.id, toRow(c));
    }
    const rows = [...byId.values()];
    if (rows.length > 0) {
      const { error } = await admin.from("card_search_index").upsert(rows, { onConflict: "scryfall_id" });
      if (error) {
        this.stoppedReason = "upsert_failed";
        return;
      }
    }
    const missing = result.notFound.filter((id) => !byId.has(id));
    if (!(await this.markUnresolved(missing, "not_found", NOT_FOUND_RETRY))) {
      this.stoppedReason = "mark_failed";
      return;
    }
    this.hydrated += rows.length;
    this.notFound += missing.length;
  }
}

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") return json({ error: "METHOD_NOT_ALLOWED" }, 405);
  if (!SUPABASE_URL || !SERVICE_ROLE_KEY) return json({ error: "SERVER_MISCONFIGURED" }, 500);
  if (!(await isAuthorized(req))) return json({ error: "UNAUTHORIZED" }, 401);

  const holder = crypto.randomUUID();
  const { data: acquired, error: leaseError } = await admin.rpc("card_hydration_acquire", {
    p_holder: holder,
    p_ttl_seconds: LEASE_TTL_SECONDS,
  });
  if (leaseError) return json({ error: "LEASE_FAILED" }, 500);
  if (acquired !== true) return json({ skipped: "already_running" });

  try {
    const { data: pending, error: pendingError } = await admin.rpc("card_search_index_pending", {
      p_limit: MAX_IDS_PER_RUN,
    });
    if (pendingError) return json({ error: "PENDING_QUERY_FAILED" }, 500);

    // text[] scalar, not SETOF: PostgREST's db-max-rows would silently cap a set at 1000.
    const ids: string[] = Array.isArray(pending) ? pending.filter((id) => typeof id === "string") : [];
    const run = new Run();
    for (let i = 0; i < ids.length && !run.stoppedReason; i += BATCH_SIZE) {
      await run.process(ids.slice(i, i + BATCH_SIZE));
    }

    return json({
      pending: ids.length,
      requests: run.requests,
      hydrated: run.hydrated,
      not_found: run.notFound,
      rejected_ids: run.rejected,
      stopped_reason: run.stoppedReason,
      elapsed_ms: Date.now() - run.startedAt,
    });
  } finally {
    await admin.rpc("card_hydration_release", { p_holder: holder });
  }
});
