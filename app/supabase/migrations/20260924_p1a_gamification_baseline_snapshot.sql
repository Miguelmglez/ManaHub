-- ============================================================================
-- 20260924_p1a_gamification_baseline_snapshot.sql
--
-- SNAPSHOT OF LIVE STATE, ALREADY APPLIED. Do not apply as a change.
--
-- The gamification schema was created in production on 2026-06-13 by migrations
-- gamification_sync_phase4_schema / _rpcs / _revoke_anon, which never reached the repo
-- (drift audit G-24). This file reproduces the live DDL of project uimogilwuixgkgfcfmyb
-- as read on 2026-09-24 (pg_attribute, pg_constraint, pg_indexes, pg_policy,
-- pg_get_functiondef, proacl), so the schema can be reviewed and rebuilt.
--
-- Idempotent: running it on the live DB is a no-op (IF NOT EXISTS, guarded policy
-- creation, CREATE OR REPLACE with the byte-identical live bodies, REVOKE/GRANT that match
-- the live ACL). It is the BASE the later 20260924_p1b/p1c migrations build on; do not
-- re-run it after them (CREATE OR REPLACE below would restore these exact bodies, which
-- is harmless today because p1b does not replace any of them, but keep the order anyway).
--
-- Live table ACL at snapshot time (Supabase default privileges, not re-granted here on
-- purpose so a re-run can never undo p1c's hygiene):
--   postgres, anon, authenticated, service_role = arwdDxtm on all five tables.
-- Live function ACL: postgres, authenticated, service_role = EXECUTE (no PUBLIC, no anon).
-- Row counts at snapshot: xp_transactions 456 (3 users), achievement_progress 94,
--   entitlements 34, streaks 3, player_progression 3.
-- ============================================================================

-- ---------------------------------------------------------------- tables
CREATE TABLE IF NOT EXISTS public.player_progression (
  user_id    uuid    NOT NULL,
  total_xp   bigint  NOT NULL DEFAULT 0,
  level      integer NOT NULL DEFAULT 1,
  updated_at bigint  NOT NULL DEFAULT 0,
  CONSTRAINT player_progression_pkey PRIMARY KEY (user_id),
  CONSTRAINT player_progression_user_id_fkey
    FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS public.xp_transactions (
  user_id         uuid    NOT NULL,
  idempotency_key text    NOT NULL,
  amount          integer NOT NULL,
  source_category text    NOT NULL,
  source_ref      text,
  created_at      bigint  NOT NULL,
  CONSTRAINT xp_transactions_pkey PRIMARY KEY (user_id, idempotency_key),
  CONSTRAINT xp_transactions_user_id_fkey
    FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS public.achievement_progress (
  user_id        uuid    NOT NULL,
  achievement_id text    NOT NULL,
  current_value  integer NOT NULL DEFAULT 0,
  tier_reached   integer NOT NULL DEFAULT 0,
  unlocked_at    bigint,
  celebrated_at  bigint,
  updated_at     bigint  NOT NULL DEFAULT 0,
  CONSTRAINT achievement_progress_pkey PRIMARY KEY (user_id, achievement_id),
  CONSTRAINT achievement_progress_user_id_fkey
    FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS public.entitlements (
  user_id       uuid   NOT NULL,
  unlockable_id text   NOT NULL,
  unlocked_at   bigint NOT NULL,
  source        text   NOT NULL,
  updated_at    bigint NOT NULL DEFAULT 0,
  CONSTRAINT entitlements_pkey PRIMARY KEY (user_id, unlockable_id),
  CONSTRAINT entitlements_user_id_fkey
    FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS public.streaks (
  user_id          uuid    NOT NULL,
  type             text    NOT NULL,
  current          integer NOT NULL DEFAULT 0,
  longest          integer NOT NULL DEFAULT 0,
  last_active_date text    NOT NULL DEFAULT ''::text,
  freeze_tokens    integer NOT NULL DEFAULT 0,
  updated_at       bigint  NOT NULL DEFAULT 0,
  CONSTRAINT streaks_pkey PRIMARY KEY (user_id, type),
  CONSTRAINT streaks_user_id_fkey
    FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE
);

-- ---------------------------------------------------------------- indexes
-- (user_id is the leading PK column everywhere, so every FK is indexed.)
CREATE INDEX IF NOT EXISTS idx_xp_transactions_user_created
  ON public.xp_transactions USING btree (user_id, created_at);
CREATE INDEX IF NOT EXISTS idx_achievement_progress_user_updated
  ON public.achievement_progress USING btree (user_id, updated_at);
CREATE INDEX IF NOT EXISTS idx_entitlements_user_updated
  ON public.entitlements USING btree (user_id, updated_at);
CREATE INDEX IF NOT EXISTS idx_streaks_user_updated
  ON public.streaks USING btree (user_id, updated_at);

-- ---------------------------------------------------------------- RLS
ALTER TABLE public.player_progression   ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.xp_transactions      ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.achievement_progress ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.entitlements         ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.streaks              ENABLE ROW LEVEL SECURITY;

-- Live policies are FOR ALL, roles PUBLIC (p1c narrows them TO authenticated).
DO $$
DECLARE
  t text;
BEGIN
  FOREACH t IN ARRAY ARRAY['player_progression', 'xp_transactions', 'achievement_progress',
                           'entitlements', 'streaks'] LOOP
    IF NOT EXISTS (SELECT 1 FROM pg_policies
                    WHERE schemaname = 'public' AND tablename = t AND policyname = t || '_owner') THEN
      EXECUTE format(
        'CREATE POLICY %I ON public.%I FOR ALL '
        'USING ((SELECT auth.uid()) = user_id) WITH CHECK ((SELECT auth.uid()) = user_id)',
        t || '_owner', t);
    END IF;
  END LOOP;
END;
$$;

-- ---------------------------------------------------------------- functions
CREATE OR REPLACE FUNCTION public.level_for_total_xp(p_total bigint)
 RETURNS integer
 LANGUAGE plpgsql
 IMMUTABLE
 SET search_path TO 'public'
AS $function$
DECLARE
    -- thresholds[L] = cumulative XP required to REACH level L (1-indexed).
    thresholds bigint[] := ARRAY[
        0,100,383,903,1703,2821,4291,6143,8406,11106,14268,17916,22073,26760,
        31998,37807,44207,51216,58853,67135,76079,85702,96021,107051,118809,
        131309,144566,158596,173412,189029,205461,222721,240823,259780,279605,
        300311,321911,344417,367842,392197,417495,443748,470967,499164,528350,
        558537,589736,621958,655213,689513,724868,761289,798787,837372,877054,
        917843,959750,1002784,1046955,1092274,1138750,1186393,1235212,1285217,
        1336417,1388822,1442441,1497283,1553357,1610673,1669239,1729065,1790159,
        1852530,1916187,1981139,2047394,2114961,2183849,2254066,2325620,2398520,
        2472774,2548391,2625378,2703744,2783497,2864645,2947196,3031158,3116539,
        3203347,3291590,3381276,3472412,3565007,3659067,3754601,3851616,3950120
    ]::bigint[];
    n_levels  int := array_length(thresholds, 1);  -- 100
    lvl       int;
    cumulative bigint;
    next_threshold bigint;
BEGIN
    IF p_total <= 0 THEN
        RETURN 1;
    END IF;

    -- Fast path: scan precomputed thresholds (levels 1..100).
    lvl := 1;
    WHILE lvl + 1 <= n_levels AND thresholds[lvl + 1] <= p_total LOOP
        lvl := lvl + 1;
    END LOOP;
    IF lvl < n_levels THEN
        RETURN lvl;
    END IF;

    -- Slow path: at or beyond the last precomputed level — extend lazily with the
    -- identical client formula floor(100 * i^1.5 + 0.5).
    cumulative := thresholds[n_levels];   -- XP to reach level n_levels
    lvl := n_levels;
    LOOP
        next_threshold := cumulative + floor(100.0 * power(lvl, 1.5) + 0.5)::bigint;
        IF next_threshold > p_total THEN
            RETURN lvl;
        END IF;
        cumulative := next_threshold;
        lvl := lvl + 1;
    END LOOP;
END;
$function$;

CREATE OR REPLACE FUNCTION public.batch_upsert_xp_transactions(p_rows jsonb)
 RETURNS void
 LANGUAGE plpgsql
 SET search_path TO 'public'
AS $function$
DECLARE
    v_uid        uuid := auth.uid();
    v_total      bigint;
    v_level      int;
    v_now        bigint := (extract(epoch from now()) * 1000)::bigint;
    v_updated_at bigint;
BEGIN
    -- Insert each ledger row for the caller. idempotency_key dedupes (set-union).
    INSERT INTO public.xp_transactions
        (user_id, idempotency_key, amount, source_category, source_ref, created_at)
    SELECT
        v_uid,
        r->>'idempotency_key',
        (r->>'amount')::int,
        r->>'source_category',
        r->>'source_ref',
        (r->>'created_at')::bigint
    FROM jsonb_array_elements(p_rows) AS r
    ON CONFLICT (user_id, idempotency_key) DO NOTHING;

    -- Recompute derived progression from the FULL ledger sum (not incrementally).
    SELECT COALESCE(SUM(amount), 0) INTO v_total
    FROM public.xp_transactions
    WHERE user_id = v_uid;

    v_level := public.level_for_total_xp(v_total);

    -- updated_at = max created_at in this batch if present, else now (millis).
    SELECT COALESCE(MAX((r->>'created_at')::bigint), v_now) INTO v_updated_at
    FROM jsonb_array_elements(p_rows) AS r;

    INSERT INTO public.player_progression (user_id, total_xp, level, updated_at)
    VALUES (v_uid, v_total, v_level, v_updated_at)
    ON CONFLICT (user_id) DO UPDATE
        SET total_xp   = EXCLUDED.total_xp,
            level      = EXCLUDED.level,
            updated_at = GREATEST(EXCLUDED.updated_at, public.player_progression.updated_at);
END;
$function$;

CREATE OR REPLACE FUNCTION public.merge_achievement_progress(p_rows jsonb)
 RETURNS void
 LANGUAGE plpgsql
 SET search_path TO 'public'
AS $function$
DECLARE
    v_uid uuid := auth.uid();
BEGIN
    INSERT INTO public.achievement_progress
        (user_id, achievement_id, current_value, tier_reached,
         unlocked_at, celebrated_at, updated_at)
    SELECT
        v_uid,
        r->>'achievement_id',
        COALESCE((r->>'current_value')::int, 0),
        COALESCE((r->>'tier_reached')::int, 0),
        (r->>'unlocked_at')::bigint,
        (r->>'celebrated_at')::bigint,
        COALESCE((r->>'updated_at')::bigint, 0)
    FROM jsonb_array_elements(p_rows) AS r
    ON CONFLICT (user_id, achievement_id) DO UPDATE SET
        current_value = GREATEST(EXCLUDED.current_value, public.achievement_progress.current_value),
        tier_reached  = GREATEST(EXCLUDED.tier_reached,  public.achievement_progress.tier_reached),
        -- earliest non-null unlock wins (LEAST ignores nulls -> if one side null,
        -- LEAST returns the non-null; if both non-null, the smaller/earlier wins).
        unlocked_at   = LEAST(EXCLUDED.unlocked_at, public.achievement_progress.unlocked_at),
        celebrated_at = LEAST(EXCLUDED.celebrated_at, public.achievement_progress.celebrated_at),
        updated_at    = GREATEST(EXCLUDED.updated_at, public.achievement_progress.updated_at);
END;
$function$;

CREATE OR REPLACE FUNCTION public.merge_entitlements(p_rows jsonb)
 RETURNS void
 LANGUAGE plpgsql
 SET search_path TO 'public'
AS $function$
DECLARE
    v_uid uuid := auth.uid();
BEGIN
    INSERT INTO public.entitlements
        (user_id, unlockable_id, unlocked_at, source, updated_at)
    SELECT
        v_uid,
        r->>'unlockable_id',
        (r->>'unlocked_at')::bigint,
        r->>'source',
        COALESCE((r->>'updated_at')::bigint, 0)
    FROM jsonb_array_elements(p_rows) AS r
    ON CONFLICT (user_id, unlockable_id) DO UPDATE SET
        -- keep the earliest grant (both non-null -> LEAST = earlier)
        unlocked_at = LEAST(EXCLUDED.unlocked_at, public.entitlements.unlocked_at),
        updated_at  = GREATEST(EXCLUDED.updated_at, public.entitlements.updated_at);
        -- source preserved (existing row's source kept).
END;
$function$;

CREATE OR REPLACE FUNCTION public.merge_streaks(p_rows jsonb)
 RETURNS void
 LANGUAGE plpgsql
 SET search_path TO 'public'
AS $function$
DECLARE
    v_uid uuid := auth.uid();
BEGIN
    INSERT INTO public.streaks
        (user_id, type, current, longest, last_active_date, freeze_tokens, updated_at)
    SELECT
        v_uid,
        r->>'type',
        COALESCE((r->>'current')::int, 0),
        COALESCE((r->>'longest')::int, 0),
        COALESCE(r->>'last_active_date', ''),
        COALESCE((r->>'freeze_tokens')::int, 0),
        COALESCE((r->>'updated_at')::bigint, 0)
    FROM jsonb_array_elements(p_rows) AS r
    ON CONFLICT (user_id, type) DO UPDATE SET
        -- longest is monotonic regardless of which side is newer.
        longest = GREATEST(EXCLUDED.longest, public.streaks.longest),
        -- volatile fields: take the incoming row only if its last_active_date is
        -- strictly later (lexicographic compare is valid for ISO yyyy-MM-dd).
        current = CASE WHEN EXCLUDED.last_active_date > public.streaks.last_active_date
                       THEN EXCLUDED.current ELSE public.streaks.current END,
        freeze_tokens = CASE WHEN EXCLUDED.last_active_date > public.streaks.last_active_date
                       THEN EXCLUDED.freeze_tokens ELSE public.streaks.freeze_tokens END,
        last_active_date = GREATEST(EXCLUDED.last_active_date, public.streaks.last_active_date),
        updated_at = GREATEST(EXCLUDED.updated_at, public.streaks.updated_at);
END;
$function$;

-- Legacy unpaginated change feeds (ADR-008 violation, G-01). Kept for shipped clients;
-- superseded by the *_page RPCs in 20260924_p1b_gamification_sync_cursors.sql.
CREATE OR REPLACE FUNCTION public.get_xp_transactions_changes_since(p_since bigint)
 RETURNS SETOF xp_transactions
 LANGUAGE sql
 STABLE
 SET search_path TO 'public'
AS $function$
    SELECT *
    FROM public.xp_transactions
    WHERE user_id = auth.uid()
      AND created_at > p_since
    ORDER BY created_at ASC;
$function$;

CREATE OR REPLACE FUNCTION public.get_achievement_progress_changes_since(p_since bigint)
 RETURNS SETOF achievement_progress
 LANGUAGE sql
 STABLE
 SET search_path TO 'public'
AS $function$
    SELECT *
    FROM public.achievement_progress
    WHERE user_id = auth.uid()
      AND updated_at > p_since
    ORDER BY updated_at ASC;
$function$;

CREATE OR REPLACE FUNCTION public.get_entitlements_changes_since(p_since bigint)
 RETURNS SETOF entitlements
 LANGUAGE sql
 STABLE
 SET search_path TO 'public'
AS $function$
    SELECT *
    FROM public.entitlements
    WHERE user_id = auth.uid()
      AND updated_at > p_since
    ORDER BY updated_at ASC;
$function$;

CREATE OR REPLACE FUNCTION public.get_streaks_changes_since(p_since bigint)
 RETURNS SETOF streaks
 LANGUAGE sql
 STABLE
 SET search_path TO 'public'
AS $function$
    SELECT *
    FROM public.streaks
    WHERE user_id = auth.uid()
      AND updated_at > p_since
    ORDER BY updated_at ASC;
$function$;

-- No client caller (G-24); kept for compatibility.
CREATE OR REPLACE FUNCTION public.get_progression_changes_since(p_since bigint)
 RETURNS SETOF player_progression
 LANGUAGE sql
 STABLE
 SET search_path TO 'public'
AS $function$
    SELECT *
    FROM public.player_progression
    WHERE user_id = auth.uid()
      AND updated_at > p_since
    ORDER BY updated_at ASC;
$function$;

-- ---------------------------------------------------------------- function ACL
REVOKE ALL ON FUNCTION public.level_for_total_xp(bigint)                     FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.batch_upsert_xp_transactions(jsonb)            FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.merge_achievement_progress(jsonb)              FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.merge_entitlements(jsonb)                      FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.merge_streaks(jsonb)                           FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.get_xp_transactions_changes_since(bigint)      FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.get_achievement_progress_changes_since(bigint) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.get_entitlements_changes_since(bigint)         FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.get_streaks_changes_since(bigint)              FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.get_progression_changes_since(bigint)          FROM PUBLIC, anon;

GRANT EXECUTE ON FUNCTION public.level_for_total_xp(bigint)                     TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.batch_upsert_xp_transactions(jsonb)            TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.merge_achievement_progress(jsonb)              TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.merge_entitlements(jsonb)                      TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.merge_streaks(jsonb)                           TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.get_xp_transactions_changes_since(bigint)      TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.get_achievement_progress_changes_since(bigint) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.get_entitlements_changes_since(bigint)         TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.get_streaks_changes_since(bigint)              TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.get_progression_changes_since(bigint)          TO authenticated, service_role;

