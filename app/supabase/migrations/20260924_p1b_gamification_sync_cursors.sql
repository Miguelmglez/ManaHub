-- ============================================================================
-- 20260924_p1b_gamification_sync_cursors.sql   (drift audit G-01, ADR-008)
--
-- NOT APPLIED. Requires 20260924_p1a (baseline, already live). Additive and backward
-- compatible: no RPC, column or table is dropped or renamed; the five legacy
-- *_changes_since RPCs and the four push RPCs keep their names, signatures and bodies.
-- Shipped clients decode the legacy SETOF rows with supabase-kt's default
-- Json { ignoreUnknownKeys = true }, so the new columns cannot break them.
--
-- What it adds
--   1. xp_transactions.server_seq — server-assigned, monotonic per user, never from payload.
--   2. changed_at (epoch ms) on player_progression, achievement_progress, entitlements,
--      streaks — server-assigned, bumped ONLY when a merge-relevant column changes
--      (updated_at is client metadata and does not count), never from payload.
--   3. Keyset-paged pull RPCs with NEW names (page size capped at 500 server-side):
--        get_xp_transactions_page, get_achievement_progress_page, get_entitlements_page,
--        get_streaks_page, get_player_progression_page.
--
-- Why triggers (not only the merge RPCs) assign the cursors
--   The owner policies are FOR ALL, so a client can also write these tables directly
--   through PostgREST. A BEFORE trigger is the only place every write path passes.
--   The four push RPCs are therefore unchanged: they fire the trigger.
--
-- Why server_seq is trigger-assigned from a sequence and not GENERATED ... AS IDENTITY
--   A keyset cursor is only skip-free if, per user, cursor order == commit order. An
--   identity/serial default is evaluated before BEFORE triggers, i.e. before any lock, so
--   two concurrent pushes of the same user (two devices) could commit seq 101 before 100;
--   a reader that saw 101 would advance past 100 forever. The trigger takes a per-user
--   transaction advisory lock FIRST and only then draws nextval / reads the clock, so a
--   later transaction of the same user always gets a strictly larger cursor than any
--   earlier committed one. changed_at uses the same lock plus
--   GREATEST(clock_ms, max(changed_at)+1) so two transactions never share a millisecond.
--   Different users never contend except on a (harmless) 64-bit hash collision.
--
-- Chunked pushes (<= 500 rows per call) keep today's semantics: batch_upsert_xp_transactions
-- recomputes progression from the full ledger sum on every call and keeps
-- updated_at = GREATEST(...); the merges are commutative max/min merges; the ledger insert
-- is ON CONFLICT DO NOTHING. Each call takes the per-user lock once (re-entrant per row).
-- Limits that remain (by design, documented): deletes are not propagated (no tombstones;
-- the ledger is append-only by client contract), and a batch must not repeat a primary key
-- (ON CONFLICT DO UPDATE cannot touch a row twice) — the Room-keyed client never does.
--
-- Invariants: SECURITY INVOKER everywhere, SET search_path = public, (select auth.uid()),
-- REVOKE EXECUTE FROM PUBLIC/anon + explicit GRANT, FK columns already indexed (user_id
-- leads every PK). Run get_advisors after applying: expect "unused index" INFO on the four
-- new cursor indexes until the P3 client ships; no new security lints.
-- ============================================================================

-- ============================================================ 1. ledger server_seq
CREATE SEQUENCE IF NOT EXISTS public.xp_transactions_server_seq_seq AS bigint;
-- Supabase default privileges hand anon/authenticated rwU on new sequences; tighten.
REVOKE ALL ON SEQUENCE public.xp_transactions_server_seq_seq FROM PUBLIC, anon, authenticated;
-- The INVOKER trigger calls nextval as the pushing role (USAGE allows nextval, not setval).
GRANT USAGE ON SEQUENCE public.xp_transactions_server_seq_seq TO authenticated, service_role;

ALTER TABLE public.xp_transactions ADD COLUMN IF NOT EXISTS server_seq bigint;
ALTER SEQUENCE public.xp_transactions_server_seq_seq OWNED BY public.xp_transactions.server_seq;

-- Backfill before the trigger exists, in (created_at, idempotency_key) order.
DROP TRIGGER IF EXISTS trg_xp_transactions_server_seq ON public.xp_transactions;

UPDATE public.xp_transactions x
   SET server_seq = o.base + o.rn
  FROM (
    SELECT t.user_id,
           t.idempotency_key,
           row_number() OVER (ORDER BY t.created_at, t.idempotency_key, t.user_id) AS rn,
           (SELECT COALESCE(max(server_seq), 0) FROM public.xp_transactions) AS base
      FROM public.xp_transactions t
     WHERE t.server_seq IS NULL
  ) o
 WHERE x.user_id = o.user_id
   AND x.idempotency_key = o.idempotency_key;

-- Never move the sequence backwards (a re-run must not re-issue drawn values).
DO $$
DECLARE
  v_max  bigint;
  v_last bigint;
  v_called boolean;
BEGIN
  SELECT max(server_seq) INTO v_max FROM public.xp_transactions;
  SELECT last_value, is_called INTO v_last, v_called FROM public.xp_transactions_server_seq_seq;
  IF v_max IS NOT NULL AND (NOT v_called OR v_max > v_last) THEN
    PERFORM setval('public.xp_transactions_server_seq_seq', v_max, true);
  END IF;
END;
$$;

ALTER TABLE public.xp_transactions ALTER COLUMN server_seq SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS idx_xp_transactions_user_server_seq
  ON public.xp_transactions USING btree (user_id, server_seq);

CREATE OR REPLACE FUNCTION public.xp_transactions_assign_server_seq()
RETURNS trigger
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = public
AS $$
BEGIN
  IF TG_OP = 'UPDATE' THEN
    NEW.server_seq := OLD.server_seq;
    IF (to_jsonb(NEW) - 'server_seq') IS NOT DISTINCT FROM (to_jsonb(OLD) - 'server_seq') THEN
      RETURN NEW;
    END IF;
  END IF;

  -- Lock BEFORE drawing the value: per user, seq order must equal commit order.
  PERFORM pg_advisory_xact_lock(hashtextextended('gamification_sync:' || NEW.user_id::text, 0));
  NEW.server_seq := nextval('public.xp_transactions_server_seq_seq');
  RETURN NEW;
END;
$$;

REVOKE ALL ON FUNCTION public.xp_transactions_assign_server_seq() FROM PUBLIC, anon, authenticated;

CREATE TRIGGER trg_xp_transactions_server_seq
  BEFORE INSERT OR UPDATE ON public.xp_transactions
  FOR EACH ROW EXECUTE FUNCTION public.xp_transactions_assign_server_seq();

-- ============================================================ 2. changed_at on mutable tables
CREATE OR REPLACE FUNCTION public.gamification_assign_changed_at()
RETURNS trigger
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = public
AS $$
DECLARE
  v_max bigint;
BEGIN
  IF TG_OP = 'UPDATE' THEN
    NEW.changed_at := OLD.changed_at;
    -- updated_at is client-stamped metadata; only merge-relevant columns count as a change.
    IF (to_jsonb(NEW) - 'changed_at' - 'updated_at')
       IS NOT DISTINCT FROM (to_jsonb(OLD) - 'changed_at' - 'updated_at') THEN
      RETURN NEW;
    END IF;
  END IF;

  -- Lock BEFORE reading the clock: per user, changed_at order must equal commit order.
  PERFORM pg_advisory_xact_lock(hashtextextended('gamification_sync:' || NEW.user_id::text, 0));
  EXECUTE format('SELECT max(changed_at) FROM public.%I WHERE user_id = $1', TG_TABLE_NAME)
     INTO v_max
    USING NEW.user_id;
  NEW.changed_at := GREATEST((extract(epoch FROM clock_timestamp()) * 1000)::bigint,
                             COALESCE(v_max, 0) + 1);
  RETURN NEW;
END;
$$;

REVOKE ALL ON FUNCTION public.gamification_assign_changed_at() FROM PUBLIC, anon, authenticated;

DO $$
DECLARE
  t text;
BEGIN
  FOREACH t IN ARRAY ARRAY['player_progression', 'achievement_progress', 'entitlements', 'streaks'] LOOP
    EXECUTE format('ALTER TABLE public.%I ADD COLUMN IF NOT EXISTS changed_at bigint', t);
    EXECUTE format('DROP TRIGGER IF EXISTS %I ON public.%I', 'trg_' || t || '_changed_at', t);
    -- Backfill: one migration-time instant for every existing row; the pk breaks the ties.
    EXECUTE format(
      'UPDATE public.%I SET changed_at = (extract(epoch FROM now()) * 1000)::bigint '
      'WHERE changed_at IS NULL', t);
    EXECUTE format(
      'ALTER TABLE public.%I ALTER COLUMN changed_at '
      'SET DEFAULT ((extract(epoch FROM clock_timestamp()) * 1000)::bigint)', t);
    EXECUTE format('ALTER TABLE public.%I ALTER COLUMN changed_at SET NOT NULL', t);
    EXECUTE format(
      'CREATE TRIGGER %I BEFORE INSERT OR UPDATE ON public.%I '
      'FOR EACH ROW EXECUTE FUNCTION public.gamification_assign_changed_at()',
      'trg_' || t || '_changed_at', t);
  END LOOP;
END;
$$;

-- player_progression has one row per user (PK user_id): no extra index needed.
CREATE INDEX IF NOT EXISTS idx_achievement_progress_user_changed
  ON public.achievement_progress USING btree (user_id, changed_at, achievement_id);
CREATE INDEX IF NOT EXISTS idx_entitlements_user_changed
  ON public.entitlements USING btree (user_id, changed_at, unlockable_id);
CREATE INDEX IF NOT EXISTS idx_streaks_user_changed
  ON public.streaks USING btree (user_id, changed_at, type);

-- ============================================================ 3. paged pull RPCs
-- Ledger: server_seq is both window and cursor (append-only, tie-free).
CREATE OR REPLACE FUNCTION public.get_xp_transactions_page(
  p_after_seq bigint  DEFAULT 0,
  p_limit     integer DEFAULT 500
)
RETURNS TABLE (
  server_seq      bigint,
  idempotency_key text,
  amount          integer,
  source_category text,
  source_ref      text,
  created_at      bigint
)
LANGUAGE sql
STABLE
SECURITY INVOKER
SET search_path = public
AS $$
  SELECT x.server_seq, x.idempotency_key, x.amount, x.source_category, x.source_ref, x.created_at
    FROM public.xp_transactions x
   WHERE x.user_id = (SELECT auth.uid())
     AND x.server_seq > COALESCE(p_after_seq, 0)
   ORDER BY x.server_seq ASC
   LIMIT LEAST(GREATEST(COALESCE(p_limit, 500), 1), 500);
$$;

-- Mutable tables: ADR-008 two-predicate form — window (changed_at > p_since) and keyset
-- cursor ((changed_at, pk) > (p_after_changed_at, p_after_<pk>)) are independent.
CREATE OR REPLACE FUNCTION public.get_achievement_progress_page(
  p_since                bigint  DEFAULT 0,
  p_after_changed_at     bigint  DEFAULT NULL,
  p_after_achievement_id text    DEFAULT NULL,
  p_limit                integer DEFAULT 500
)
RETURNS TABLE (
  achievement_id text,
  current_value  integer,
  tier_reached   integer,
  unlocked_at    bigint,
  celebrated_at  bigint,
  updated_at     bigint,
  changed_at     bigint
)
LANGUAGE plpgsql
STABLE
SECURITY INVOKER
SET search_path = public
AS $$
#variable_conflict use_column
BEGIN
  IF (p_after_changed_at IS NULL) <> (p_after_achievement_id IS NULL) THEN
    RAISE EXCEPTION 'INVALID_ARGUMENT' USING ERRCODE = '22023', DETAIL = 'cursor';
  END IF;

  RETURN QUERY
  SELECT a.achievement_id, a.current_value, a.tier_reached, a.unlocked_at, a.celebrated_at,
         a.updated_at, a.changed_at
    FROM public.achievement_progress a
   WHERE a.user_id = (SELECT auth.uid())
     AND a.changed_at > COALESCE(p_since, 0)
     AND (p_after_changed_at IS NULL
          OR (a.changed_at, a.achievement_id) > (p_after_changed_at, p_after_achievement_id))
   ORDER BY a.changed_at ASC, a.achievement_id ASC
   LIMIT LEAST(GREATEST(COALESCE(p_limit, 500), 1), 500);
END;
$$;

CREATE OR REPLACE FUNCTION public.get_entitlements_page(
  p_since               bigint  DEFAULT 0,
  p_after_changed_at    bigint  DEFAULT NULL,
  p_after_unlockable_id text    DEFAULT NULL,
  p_limit               integer DEFAULT 500
)
RETURNS TABLE (
  unlockable_id text,
  unlocked_at   bigint,
  source        text,
  updated_at    bigint,
  changed_at    bigint
)
LANGUAGE plpgsql
STABLE
SECURITY INVOKER
SET search_path = public
AS $$
#variable_conflict use_column
BEGIN
  IF (p_after_changed_at IS NULL) <> (p_after_unlockable_id IS NULL) THEN
    RAISE EXCEPTION 'INVALID_ARGUMENT' USING ERRCODE = '22023', DETAIL = 'cursor';
  END IF;

  RETURN QUERY
  SELECT e.unlockable_id, e.unlocked_at, e.source, e.updated_at, e.changed_at
    FROM public.entitlements e
   WHERE e.user_id = (SELECT auth.uid())
     AND e.changed_at > COALESCE(p_since, 0)
     AND (p_after_changed_at IS NULL
          OR (e.changed_at, e.unlockable_id) > (p_after_changed_at, p_after_unlockable_id))
   ORDER BY e.changed_at ASC, e.unlockable_id ASC
   LIMIT LEAST(GREATEST(COALESCE(p_limit, 500), 1), 500);
END;
$$;

CREATE OR REPLACE FUNCTION public.get_streaks_page(
  p_since            bigint  DEFAULT 0,
  p_after_changed_at bigint  DEFAULT NULL,
  p_after_type       text    DEFAULT NULL,
  p_limit            integer DEFAULT 500
)
RETURNS TABLE (
  type             text,
  current          integer,
  longest          integer,
  last_active_date text,
  freeze_tokens    integer,
  updated_at       bigint,
  changed_at       bigint
)
LANGUAGE plpgsql
STABLE
SECURITY INVOKER
SET search_path = public
AS $$
#variable_conflict use_column
BEGIN
  IF (p_after_changed_at IS NULL) <> (p_after_type IS NULL) THEN
    RAISE EXCEPTION 'INVALID_ARGUMENT' USING ERRCODE = '22023', DETAIL = 'cursor';
  END IF;

  RETURN QUERY
  SELECT s.type, s.current, s.longest, s.last_active_date, s.freeze_tokens,
         s.updated_at, s.changed_at
    FROM public.streaks s
   WHERE s.user_id = (SELECT auth.uid())
     AND s.changed_at > COALESCE(p_since, 0)
     AND (p_after_changed_at IS NULL
          OR (s.changed_at, s.type) > (p_after_changed_at, p_after_type))
   ORDER BY s.changed_at ASC, s.type ASC
   LIMIT LEAST(GREATEST(COALESCE(p_limit, 500), 1), 500);
END;
$$;

-- One row per user, so no keyset: the row is returned iff it changed after p_since.
CREATE OR REPLACE FUNCTION public.get_player_progression_page(
  p_since bigint DEFAULT 0
)
RETURNS TABLE (
  total_xp   bigint,
  level      integer,
  updated_at bigint,
  changed_at bigint
)
LANGUAGE sql
STABLE
SECURITY INVOKER
SET search_path = public
AS $$
  SELECT p.total_xp, p.level, p.updated_at, p.changed_at
    FROM public.player_progression p
   WHERE p.user_id = (SELECT auth.uid())
     AND p.changed_at > COALESCE(p_since, 0);
$$;

REVOKE ALL ON FUNCTION public.get_xp_transactions_page(bigint, integer)                   FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.get_achievement_progress_page(bigint, bigint, text, integer) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.get_entitlements_page(bigint, bigint, text, integer)        FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.get_streaks_page(bigint, bigint, text, integer)             FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.get_player_progression_page(bigint)                         FROM PUBLIC, anon;

GRANT EXECUTE ON FUNCTION public.get_xp_transactions_page(bigint, integer)                   TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.get_achievement_progress_page(bigint, bigint, text, integer) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.get_entitlements_page(bigint, bigint, text, integer)        TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.get_streaks_page(bigint, bigint, text, integer)             TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.get_player_progression_page(bigint)                         TO authenticated, service_role;


-- ----------------------------------------------------------------------------
-- Post-apply checks (read-only):
--   SELECT count(*) FILTER (WHERE server_seq IS NULL), count(DISTINCT server_seq), count(*)
--     FROM public.xp_transactions;                                   -- 0, 456, 456 today
--   SELECT last_value FROM public.xp_transactions_server_seq_seq;     -- = max(server_seq)
--   SELECT tgrelid::regclass, tgname FROM pg_trigger
--    WHERE tgname LIKE 'trg_%_changed_at' OR tgname = 'trg_xp_transactions_server_seq';
--   SELECT proname, proacl FROM pg_proc WHERE proname LIKE 'get_%_page';
-- Behavioural (rolled back, as an authenticated user): push the same merge row twice ->
-- changed_at unchanged on the 2nd call; bump current_value -> changed_at increases;
-- PATCH changed_at/server_seq directly -> value ignored.
-- ----------------------------------------------------------------------------
