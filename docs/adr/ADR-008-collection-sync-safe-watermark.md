# ADR-008 — Collection sync: paginated pulls and the safe-watermark invariant

- **Status:** Accepted
- **Date:** 2026-09-06
- **Related:** ADR-005 (backend call budget), memories `project_collection_sync_data_loss_2026-09`,
  `project_sync_pagination_fix_2026-09` (backend-supabase-expert)

## Context

A production user reported collection entries present in Supabase but absent from the app. The audit
found real, permanent, silent data loss — not corruption.

Forensic evidence for the reporting user: Supabase held 1377 rows; the device held **exactly 1000**.
`md5` of the device's sorted row ids equalled `md5` of the remote rows with
`updated_at <= 1787480891311` — the 1000th row by `updated_at ASC` — and matched again when hashing
`id:quantity:is_deleted`. The device held a byte-identical copy of the first 1000 remote rows. The
other 377 had never arrived and never would.

Three independent defects combined:

1. **Silent truncation.** `get_collection_changes_since(p_since)` was `RETURNS SETOF` with no `LIMIT`
   and no pagination. PostgREST truncates responses at `db-max-rows` (Supabase default **1000**). The
   client received 1000 rows with no signal that anything was withheld.
2. **A watermark claimed for time that was never pulled.** `SyncManager` snapshotted
   `syncStartTime = now()` before the pull and saved it as the new watermark afterwards regardless of
   what actually arrived or applied. Every row above the truncation point fell below the new
   watermark, and the next incremental pull (`updated_at > watermark`) could never ask for it again.
3. **Ownership gated on cache metadata.** `user_card_collection.scryfall_id` had a `RESTRICT` FK to
   `cards`, so an ownership row could not be inserted until its card metadata was cached. The remote
   `cards` table is empty, so a fresh install had to resolve every distinct id from Scryfall first;
   rows whose fetch failed were skipped, and defect 2 then made that skip permanent. This is also why
   the collection count "climbed progressively" on a new device.

## Decision 1 — A sync watermark must never advance past unapplied data

The watermark is a claim that everything up to that instant has been durably applied locally. It may
only be advanced when that claim is true.

```
newWatermark = min(syncStartTime, minUnappliedUpdatedAt - 1).coerceAtLeast(lastSync)
```

`minUnappliedUpdatedAt` is the lowest `updated_at` among rows that were fetched but not applied, or
that are known to be unfetched (an aborted page loop). When every page drained and every row applied
it degrades to `syncStartTime`, preserving the in-flight-write protection that motivated the original
design. `coerceAtLeast(lastSync)` keeps it monotonic.

Corollary, and the trap to avoid: **never set the watermark to `max(updatedAt seen)`.** That looks
equivalent and is exactly the original bug — "seen" is not "applied", and a truncated page makes the
maximum seen an arbitrary point in the middle of the real change set.

## Decision 2 — Every `RETURNS SETOF` RPC must be keyset-paginated below `db-max-rows`

A change-feed RPC returning an unbounded set is a latent data-loss bug that stays invisible until one
user crosses the row cap. Pull RPCs use keyset pagination on `(updated_at, id)` — tie-safe, since a
plain `updated_at` cursor can straddle rows sharing a millisecond — with the window filter and the
cursor as **two separate predicates**:

```sql
WHERE user_id = (select auth.uid())
  AND updated_at > p_since                                       -- window
  AND (p_after_updated_at IS NULL
       OR (updated_at, id) > (p_after_updated_at, p_after_id))    -- cursor
ORDER BY updated_at ASC, id ASC
LIMIT least(coalesce(p_limit, 500), 500)
```

Page size is 500 with a **server-side** cap, so a client bug can never re-enter the 1000-row regime.
The client advances its cursor from the last row of each page (never from a computed maximum) and
terminates on a short page, with a hard page cap as a runaway guard.

Applied to collection and decks. The gamification `*_changes_since` RPCs share the defect and are
scheduled behind them; `xp_transactions` is the highest-risk remaining one because it grows
monotonically and never tombstones.

## Decision 3 — Ownership records never depend on cache metadata

The `RESTRICT` FK from `user_card_collection.scryfall_id` to `cards` is removed (Room v52 → v53). What
the user owns is irreplaceable; card metadata is a cache that can be refetched at any time. Coupling
the two let a Scryfall hiccup destroy ownership data.

Two independent mechanisms now protect the insert, either sufficient alone:

- the FK is gone, so the row lands regardless of cache state;
- unresolved ids get a placeholder `CardEntity` (`is_stale = true`,
  `stale_reason = "pending_hydration"`) so the row is also **visible and counted** immediately —
  `UserCardWithCard.card` is nullable at the Room layer but the domain mapper drops null-card rows,
  and `observeCount` is a plain `COUNT(*)`, so without a placeholder the count and the list would
  disagree. A background worker replaces placeholders with real metadata.

Consumers that would skew on a placeholder — collection stats, price refresh, deck analysis — must
exclude the pending-hydration marker rather than averaging in `cmc = 0` and empty colors.

## Decision 4 — A retryable write path must be idempotent, gated on its own completion

`batch_upsert_collection`'s tuple-collision branch added the incoming quantity to the surviving row.
Because a sync that fails after PUSH leaves the watermark untouched, the identical payload is
re-pushed on the next cycle and quantities inflate silently.

The branch is now gated on the **state that only its own completion produces** — the losing id
existing *as a tombstone* (`is_deleted = true`) — and it writes that tombstone unconditionally
(`ON CONFLICT (id) DO UPDATE … SET is_deleted = true`, not `DO NOTHING`).

Both halves are load-bearing. A first attempt at this gated on the id merely *existing*, which
silently discarded quantity whenever a still-live row was re-pointed onto another live row's tuple —
a two-device convergence. And tightening the gate alone, while leaving `DO NOTHING`, leaves an
already-existing row live, so the next push adds its quantity a second time.

**General rule:** an idempotency gate must test the state its own completion produces, never the mere
existence of the record it operates on.

## Decision 5 — Recovery is a self-healing integrity check, not a migration-time hack

After each successful sync the client compares `get_collection_integrity().total_rows` (tombstones
included, both sides) against its local count. On mismatch it clears the watermark and forces a full
paginated re-pull. Repairs are rate-limited to one per 24 h so a permanent client/server disagreement
cannot become an infinite full-pull loop.

This recovers already-damaged installs without manual intervention and keeps protecting every user
afterwards, which a one-shot upgrade flag would not.

**Release ordering is a hard constraint:** the repair re-pushes every local row, so it must not ship
before Decision 4 is live in production. Verify the deployed function body; do not assume.

## Consequences

- Sync costs one extra round-trip per 500 rows plus one integrity call per cycle. Acceptable.
- `sync()` can now throw `CancellationException` instead of returning `SyncResult.ERROR`; call sites
  were audited.
- Dropping the FK means an ownership row can reference an uncached card — intended, and the reason
  Decision 3 pairs it with placeholders.
- The v52 → v53 table recreate is the highest-risk step: `fallbackToDestructiveMigrationFrom` covers
  only v1–24, so an index name that differs from Room's generated one crash-loops every user at
  launch. The instrumented migration test asserting the exact index names is non-negotiable.
