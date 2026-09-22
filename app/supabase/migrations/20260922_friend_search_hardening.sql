-- ============================================================================
-- 20260922_friend_search_hardening.sql
--
-- QA/security batch on top of 20260922_card_search_index.sql and
-- 20260922_search_friend_cards.sql:
--   * card_search_index: 'failed' status + next_retry_at back-off; unresolved
--     marks never downgrade an existing 'ok' row (a sort_key flip mid-pagination
--     duplicated row_ids on the client).
--   * Single-run lease for the hydrator (pg_advisory_lock cannot span a run made
--     of many pooled PostgREST requests).
--   * Cron secret verified by SHA-256 digest; cron every 5 min (idle ticks cost
--     one cheap query and never call the Edge Function).
--   * search_friend_cards: shared access check in schema `private` (not exposed
--     by PostgREST), PROFILE_INCOMPLETE, 4xx-mapped errcodes, type/format arrays
--     capped at 10, new unindexed_count column + friend_list_unindexed_count RPC.
--
-- Applied via the Supabase MCP `apply_migration` tool against project
-- uimogilwuixgkgfcfmyb; this file mirrors that change.
-- ============================================================================

-- ---- card_search_index back-off ---------------------------------------------
ALTER TABLE public.card_search_index DROP CONSTRAINT IF EXISTS card_search_index_status_check;
ALTER TABLE public.card_search_index
  ADD CONSTRAINT card_search_index_status_check CHECK (status IN ('ok', 'not_found', 'failed'));
ALTER TABLE public.card_search_index ADD COLUMN IF NOT EXISTS next_retry_at timestamptz;

CREATE OR REPLACE FUNCTION public.card_search_index_pending(p_limit integer DEFAULT 1500)
 RETURNS text[]
 LANGUAGE sql
 STABLE
 SET search_path = public
AS $$
  WITH referenced AS (
    SELECT ucc.scryfall_id AS id FROM user_card_collection ucc WHERE ucc.is_deleted = false
    UNION
    SELECT w.card_id FROM wishlists w
  ), due AS (
    SELECT r.id, csi.hydrated_at
    FROM referenced r
    LEFT JOIN card_search_index csi ON csi.scryfall_id = r.id
    WHERE r.id ~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
      AND (csi.scryfall_id IS NULL
           OR (csi.status = 'ok' AND csi.hydrated_at < now() - interval '30 days')
           OR (csi.status <> 'ok' AND COALESCE(csi.next_retry_at, csi.hydrated_at + interval '30 days') <= now()))
    ORDER BY csi.hydrated_at NULLS FIRST, r.id
    LIMIT LEAST(GREATEST(COALESCE(p_limit, 1500), 1), 3000)
  )
  SELECT COALESCE(array_agg(d.id ORDER BY d.hydrated_at NULLS FIRST, d.id), '{}') FROM due d;
$$;
REVOKE EXECUTE ON FUNCTION public.card_search_index_pending(integer) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.card_search_index_pending(integer) TO service_role;

-- 'ok' rows are only re-stamped (defers their next refresh), never downgraded.
CREATE OR REPLACE FUNCTION public.card_search_index_mark_unresolved(
    p_ids text[], p_status text, p_retry_after interval)
 RETURNS integer
 LANGUAGE plpgsql
 SET search_path = public
AS $$
DECLARE
  v_count integer;
BEGIN
  IF p_status NOT IN ('not_found', 'failed') OR COALESCE(cardinality(p_ids), 0) > 100
     OR p_retry_after IS NULL OR p_retry_after < interval '1 hour' THEN
    RAISE EXCEPTION 'INVALID_ARGUMENT' USING ERRCODE = '22023';
  END IF;

  INSERT INTO card_search_index AS csi (scryfall_id, status, hydrated_at, next_retry_at)
  SELECT DISTINCT id, p_status, now(), now() + p_retry_after
    FROM unnest(p_ids) AS id
   WHERE id IS NOT NULL
  ON CONFLICT (scryfall_id) DO UPDATE
    SET status        = CASE WHEN csi.status = 'ok' THEN 'ok' ELSE EXCLUDED.status END,
        hydrated_at   = now(),
        next_retry_at = CASE WHEN csi.status = 'ok' THEN NULL ELSE EXCLUDED.next_retry_at END;
  GET DIAGNOSTICS v_count = ROW_COUNT;
  RETURN v_count;
END;
$$;
REVOKE EXECUTE ON FUNCTION public.card_search_index_mark_unresolved(text[], text, interval) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.card_search_index_mark_unresolved(text[], text, interval) TO service_role;

-- ---- single-run lease ---------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.card_hydration_lease (
    id         smallint    PRIMARY KEY CHECK (id = 1),
    holder     uuid        NOT NULL,
    expires_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
ALTER TABLE public.card_hydration_lease ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.card_hydration_lease FROM PUBLIC, anon, authenticated;
GRANT ALL ON public.card_hydration_lease TO service_role;
-- Deliberately no policies: only service_role (bypasses RLS) and the cron owner touch it.

CREATE OR REPLACE FUNCTION public.card_hydration_acquire(p_holder uuid, p_ttl_seconds integer)
 RETURNS boolean
 LANGUAGE plpgsql
 SET search_path = public
AS $$
DECLARE
  v_ok boolean;
BEGIN
  INSERT INTO card_hydration_lease AS l (id, holder, expires_at)
  VALUES (1, p_holder, now() + make_interval(secs => LEAST(GREATEST(p_ttl_seconds, 30), 600)))
  ON CONFLICT (id) DO UPDATE
    SET holder = EXCLUDED.holder, expires_at = EXCLUDED.expires_at, updated_at = now()
    WHERE l.expires_at <= now() OR l.holder = p_holder
  RETURNING true INTO v_ok;
  RETURN COALESCE(v_ok, false);
END;
$$;
REVOKE EXECUTE ON FUNCTION public.card_hydration_acquire(uuid, integer) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.card_hydration_acquire(uuid, integer) TO service_role;

CREATE OR REPLACE FUNCTION public.card_hydration_release(p_holder uuid)
 RETURNS void
 LANGUAGE sql
 SET search_path = public
AS $$
  UPDATE card_hydration_lease SET expires_at = now(), updated_at = now() WHERE holder = p_holder;
$$;
REVOKE EXECUTE ON FUNCTION public.card_hydration_release(uuid) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.card_hydration_release(uuid) TO service_role;

-- ---- cron secret by digest ----------------------------------------------------
DROP FUNCTION IF EXISTS public.verify_card_hydration_secret(text);
CREATE OR REPLACE FUNCTION public.verify_card_hydration_secret(p_secret_sha256 text)
 RETURNS boolean
 LANGUAGE sql
 STABLE
 SET search_path = public
AS $$
  SELECT COALESCE(p_secret_sha256 ~ '^[0-9a-f]{64}$', false)
     AND COALESCE(
       (SELECT encode(sha256(convert_to(ds.decrypted_secret, 'UTF8')), 'hex') = p_secret_sha256
          FROM vault.decrypted_secrets ds
         WHERE ds.name = 'hydrate_card_metadata_cron_secret'
         LIMIT 1),
       false);
$$;
REVOKE EXECUTE ON FUNCTION public.verify_card_hydration_secret(text) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.verify_card_hydration_secret(text) TO service_role;

CREATE OR REPLACE FUNCTION public.trigger_card_metadata_hydration()
 RETURNS bigint
 LANGUAGE plpgsql
 SET search_path = public
AS $$
DECLARE
  v_secret text;
BEGIN
  IF cardinality(card_search_index_pending(1)) = 0
     OR EXISTS (SELECT 1 FROM card_hydration_lease WHERE expires_at > now()) THEN
    RETURN NULL;
  END IF;

  SELECT ds.decrypted_secret INTO v_secret
    FROM vault.decrypted_secrets ds
   WHERE ds.name = 'hydrate_card_metadata_cron_secret'
   LIMIT 1;

  IF v_secret IS NULL THEN
    RAISE WARNING 'hydrate_card_metadata_cron_secret missing from vault';
    RETURN NULL;
  END IF;

  RETURN net.http_post(
    url                  := 'https://uimogilwuixgkgfcfmyb.supabase.co/functions/v1/hydrate-card-metadata',
    headers              := jsonb_build_object('Content-Type', 'application/json', 'x-cron-secret', v_secret),
    body                 := '{}'::jsonb,
    timeout_milliseconds := 150000
  );
END;
$$;
REVOKE EXECUTE ON FUNCTION public.trigger_card_metadata_hydration() FROM PUBLIC, anon, authenticated;

SELECT cron.alter_job(jobid, schedule := '*/5 * * * *')
  FROM cron.job WHERE jobname = 'hydrate-card-metadata';

-- ---- private helpers (schema not exposed by PostgREST) ------------------------
CREATE SCHEMA IF NOT EXISTS private;
REVOKE ALL ON SCHEMA private FROM PUBLIC, anon;
GRANT USAGE ON SCHEMA private TO authenticated;

-- Error tokens (MESSAGE) are the contract; ERRCODEs chosen so PostgREST answers 4xx.
CREATE OR REPLACE FUNCTION private.assert_friend_list_access(p_friend_user_id uuid, p_list text)
 RETURNS void
 LANGUAGE plpgsql
 STABLE
 SET search_path = public
AS $$
DECLARE
  v_me          uuid := (SELECT auth.uid());
  v_is_friend   boolean;
  v_list_public boolean;
BEGIN
  IF v_me IS NULL THEN
    RAISE EXCEPTION 'NOT_AUTHENTICATED' USING ERRCODE = '28000';
  END IF;
  IF p_friend_user_id IS NULL OR p_friend_user_id = v_me THEN
    RAISE EXCEPTION 'SELF_LOOKUP' USING ERRCODE = 'P0001';
  END IF;
  IF p_list IS NULL OR p_list NOT IN ('collection', 'wishlist', 'trade') THEN
    RAISE EXCEPTION 'INVALID_LIST' USING ERRCODE = '22023';
  END IF;
  -- The list tables' RLS hides every friend row from an incomplete profile.
  IF NOT is_profile_complete() THEN
    RAISE EXCEPTION 'PROFILE_INCOMPLETE' USING ERRCODE = '42501';
  END IF;

  SELECT EXISTS (
    SELECT 1 FROM friendships f
    WHERE f.status = 'ACCEPTED'
      AND ((f.user_id_1 = v_me AND f.user_id_2 = p_friend_user_id)
        OR (f.user_id_2 = v_me AND f.user_id_1 = p_friend_user_id))
  ) INTO v_is_friend;

  SELECT CASE p_list
           WHEN 'collection' THEN up.collection_public
           WHEN 'wishlist'   THEN up.wishlist_public
           ELSE up.trade_list_public
         END
    INTO v_list_public
    FROM user_profiles up
   WHERE up.id = p_friend_user_id;

  IF NOT COALESCE(v_is_friend, false) AND NOT COALESCE(v_list_public, false) THEN
    RAISE EXCEPTION 'ACCESS_DENIED' USING ERRCODE = '42501';
  END IF;
END;
$$;
REVOKE EXECUTE ON FUNCTION private.assert_friend_list_access(uuid, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION private.assert_friend_list_access(uuid, text) TO authenticated;

-- Rows of the list whose card has no status='ok' metadata (invisible to filtered search).
CREATE OR REPLACE FUNCTION private.friend_list_unindexed_count(p_friend_user_id uuid, p_list text)
 RETURNS integer
 LANGUAGE sql
 STABLE
 SET search_path = public
AS $$
  SELECT count(*)::integer
    FROM (
      SELECT ucc.scryfall_id AS sid
        FROM user_card_collection ucc
       WHERE p_list = 'collection' AND ucc.user_id = p_friend_user_id AND ucc.is_deleted = false
      UNION ALL
      SELECT w.card_id FROM wishlists w
       WHERE p_list = 'wishlist' AND w.user_id = p_friend_user_id
      UNION ALL
      SELECT ucc.scryfall_id
        FROM open_for_trade oft
        JOIN user_card_collection ucc ON ucc.id = oft.user_card_id
       WHERE p_list = 'trade' AND oft.user_id = p_friend_user_id AND ucc.is_deleted = false
    ) s
   WHERE NOT EXISTS (
     SELECT 1 FROM card_search_index csi WHERE csi.scryfall_id = s.sid AND csi.status = 'ok');
$$;
REVOKE EXECUTE ON FUNCTION private.friend_list_unindexed_count(uuid, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION private.friend_list_unindexed_count(uuid, text) TO authenticated;

-- Readable even when a filtered search returns zero rows (no row to carry unindexed_count).
CREATE OR REPLACE FUNCTION public.friend_list_unindexed_count(p_friend_user_id uuid, p_list text)
 RETURNS integer
 LANGUAGE plpgsql
 STABLE
 SECURITY INVOKER
 SET search_path = public
AS $$
BEGIN
  PERFORM private.assert_friend_list_access(p_friend_user_id, p_list);
  RETURN private.friend_list_unindexed_count(p_friend_user_id, p_list);
END;
$$;
REVOKE EXECUTE ON FUNCTION public.friend_list_unindexed_count(uuid, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.friend_list_unindexed_count(uuid, text) TO authenticated;

-- ---- search_friend_cards v2 (return type changed -> DROP) ------------------------
DROP FUNCTION IF EXISTS public.search_friend_cards(
  uuid, text, text, boolean, text, text[], text[], text[], text[], text, text[], text,
  integer, integer, integer, integer, integer, integer, text[], text[], text[], boolean,
  text[], text[], boolean, integer, text, uuid);

CREATE FUNCTION public.search_friend_cards(
    p_friend_user_id  uuid,
    p_list            text,
    p_name            text    DEFAULT NULL,
    p_name_exact      boolean DEFAULT false,
    p_oracle_text     text    DEFAULT NULL,
    p_types_all       text[]  DEFAULT NULL,
    p_types_any       text[]  DEFAULT NULL,
    p_types_exclude   text[]  DEFAULT NULL,
    p_colors          text[]  DEFAULT NULL,
    p_colors_mode     text    DEFAULT 'at_least',
    p_identity        text[]  DEFAULT NULL,
    p_identity_mode   text    DEFAULT 'at_most',
    p_mv_min          integer DEFAULT NULL,
    p_mv_max          integer DEFAULT NULL,
    p_power_min       integer DEFAULT NULL,
    p_power_max       integer DEFAULT NULL,
    p_toughness_min   integer DEFAULT NULL,
    p_toughness_max   integer DEFAULT NULL,
    p_rarities        text[]  DEFAULT NULL,
    p_set_codes       text[]  DEFAULT NULL,
    p_formats         text[]  DEFAULT NULL,
    p_format_legal    boolean DEFAULT true,
    p_languages       text[]  DEFAULT NULL,
    p_conditions      text[]  DEFAULT NULL,
    p_foil_only       boolean DEFAULT false,
    p_limit           integer DEFAULT 50,
    p_after_sort_key  text    DEFAULT NULL,
    p_after_row_id    uuid    DEFAULT NULL
)
 RETURNS TABLE(
    source_list      text,
    row_id           uuid,
    scryfall_id      text,
    quantity         integer,
    is_foil          boolean,
    condition        text,
    language         text,
    card_name        text,
    set_code         text,
    rarity           text,
    sort_key         text,
    has_more         boolean,
    unindexed_count  integer
 )
 LANGUAGE plpgsql
 STABLE
 SECURITY INVOKER
 SET search_path = public
AS $$
#variable_conflict use_column
DECLARE
  c_max_text       CONSTANT integer := 100;
  c_max_array      CONSTANT integer := 50;
  c_max_term_array CONSTANT integer := 10;
  c_wubrg          CONSTANT text[]  := ARRAY['W', 'U', 'B', 'R', 'G'];

  v_limit     integer := LEAST(GREATEST(COALESCE(p_limit, 50), 1), 100);
  v_foil_only boolean := COALESCE(p_foil_only, false);
  v_legal     boolean := COALESCE(p_format_legal, true);

  v_name        text := NULLIF(btrim(lower(p_name)), '');
  v_name_pat    text;
  v_oracle_pat  text;
  v_types_all   text[];
  v_types_any   text[];
  v_types_excl  text[];
  v_rarities    text[];
  v_sets        text[];
  v_formats     text[];
  v_languages   text[] := NULLIF(p_languages, '{}');
  v_conditions  text[] := NULLIF(p_conditions, '{}');

  v_colors_mode   text := lower(COALESCE(p_colors_mode, 'at_least'));
  v_identity_mode text := lower(COALESCE(p_identity_mode, 'at_most'));
  v_colors_on     boolean := COALESCE(cardinality(p_colors), 0) > 0;
  v_identity_on   boolean := COALESCE(cardinality(p_identity), 0) > 0;
  v_colors_mask   integer := 0;
  v_colors_c      boolean := false;
  v_identity_mask integer := 0;
  v_identity_c    boolean := false;

  v_meta      boolean;
  v_unindexed integer := 0;
BEGIN
  PERFORM private.assert_friend_list_access(p_friend_user_id, p_list);

  IF length(p_name) > c_max_text OR length(p_oracle_text) > c_max_text THEN
    RAISE EXCEPTION 'INVALID_ARGUMENT' USING ERRCODE = '22023', DETAIL = 'text_too_long';
  END IF;

  IF COALESCE(cardinality(p_types_all), 0)        > c_max_term_array
     OR COALESCE(cardinality(p_types_any), 0)     > c_max_term_array
     OR COALESCE(cardinality(p_types_exclude), 0) > c_max_term_array
     OR COALESCE(cardinality(p_formats), 0)       > c_max_term_array
     OR COALESCE(cardinality(p_colors), 0)        > c_max_array
     OR COALESCE(cardinality(p_identity), 0)      > c_max_array
     OR COALESCE(cardinality(p_rarities), 0)      > c_max_array
     OR COALESCE(cardinality(p_set_codes), 0)     > c_max_array
     OR COALESCE(cardinality(p_languages), 0)     > c_max_array
     OR COALESCE(cardinality(p_conditions), 0)    > c_max_array THEN
    RAISE EXCEPTION 'INVALID_ARGUMENT' USING ERRCODE = '22023', DETAIL = 'array_too_long';
  END IF;

  IF EXISTS (
    SELECT 1 FROM unnest(
      COALESCE(p_types_all, '{}') || COALESCE(p_types_any, '{}') || COALESCE(p_types_exclude, '{}')
      || COALESCE(p_rarities, '{}') || COALESCE(p_set_codes, '{}') || COALESCE(p_formats, '{}')
      || COALESCE(p_languages, '{}') || COALESCE(p_conditions, '{}')) AS e
    WHERE e IS NULL OR length(e) > c_max_text
  ) THEN
    RAISE EXCEPTION 'INVALID_ARGUMENT' USING ERRCODE = '22023', DETAIL = 'array_element_invalid';
  END IF;

  IF (p_after_sort_key IS NULL) <> (p_after_row_id IS NULL) OR length(p_after_sort_key) > 400 THEN
    RAISE EXCEPTION 'INVALID_ARGUMENT' USING ERRCODE = '22023', DETAIL = 'cursor';
  END IF;

  IF v_colors_mode NOT IN ('any_of', 'at_most', 'exactly', 'at_least')
     OR v_identity_mode NOT IN ('any_of', 'at_most', 'exactly', 'at_least') THEN
    RAISE EXCEPTION 'INVALID_ARGUMENT' USING ERRCODE = '22023', DETAIL = 'color_mode';
  END IF;

  IF EXISTS (
    SELECT 1 FROM unnest(COALESCE(p_colors, '{}') || COALESCE(p_identity, '{}')) AS e
    WHERE e IS NULL OR upper(e) NOT IN ('W', 'U', 'B', 'R', 'G', 'C')
  ) THEN
    RAISE EXCEPTION 'INVALID_ARGUMENT' USING ERRCODE = '22023', DETAIL = 'color_letter';
  END IF;

  IF EXISTS (SELECT 1 FROM unnest(COALESCE(p_formats, '{}')) AS e WHERE e !~ '^[a-z]{2,20}$') THEN
    RAISE EXCEPTION 'INVALID_ARGUMENT' USING ERRCODE = '22023', DETAIL = 'format';
  END IF;

  -- LIKE wildcards escaped; the default LIKE escape character is '\'.
  IF v_name IS NOT NULL AND NOT COALESCE(p_name_exact, false) THEN
    v_name_pat := '%' || replace(replace(replace(v_name, '\', '\\'), '%', '\%'), '_', '\_') || '%';
  END IF;

  IF NULLIF(btrim(p_oracle_text), '') IS NOT NULL THEN
    v_oracle_pat := '%' || replace(replace(replace(btrim(p_oracle_text), '\', '\\'), '%', '\%'), '_', '\_') || '%';
  END IF;

  SELECT NULLIF(array_agg('%' || replace(replace(replace(btrim(e), '\', '\\'), '%', '\%'), '_', '\_') || '%'), '{}')
    INTO v_types_all FROM unnest(p_types_all) AS e WHERE btrim(e) <> '';
  SELECT NULLIF(array_agg('%' || replace(replace(replace(btrim(e), '\', '\\'), '%', '\%'), '_', '\_') || '%'), '{}')
    INTO v_types_any FROM unnest(p_types_any) AS e WHERE btrim(e) <> '';
  SELECT NULLIF(array_agg('%' || replace(replace(replace(btrim(e), '\', '\\'), '%', '\%'), '_', '\_') || '%'), '{}')
    INTO v_types_excl FROM unnest(p_types_exclude) AS e WHERE btrim(e) <> '';

  SELECT NULLIF(array_agg(lower(btrim(e))), '{}') INTO v_rarities FROM unnest(p_rarities) AS e;
  SELECT NULLIF(array_agg(lower(btrim(e))), '{}') INTO v_sets FROM unnest(p_set_codes) AS e;
  SELECT NULLIF(array_agg(e), '{}') INTO v_formats FROM unnest(p_formats) AS e;

  IF v_colors_on THEN
    SELECT COALESCE(sum(DISTINCT (1 << (array_position(c_wubrg, upper(e)) - 1))), 0),
           bool_or(upper(e) = 'C')
      INTO v_colors_mask, v_colors_c
      FROM unnest(p_colors) AS e;
    v_colors_c := COALESCE(v_colors_c, false);
  END IF;
  IF v_identity_on THEN
    SELECT COALESCE(sum(DISTINCT (1 << (array_position(c_wubrg, upper(e)) - 1))), 0),
           bool_or(upper(e) = 'C')
      INTO v_identity_mask, v_identity_c
      FROM unnest(p_identity) AS e;
    v_identity_c := COALESCE(v_identity_c, false);
  END IF;

  v_meta := v_name IS NOT NULL OR v_oracle_pat IS NOT NULL
         OR v_types_all IS NOT NULL OR v_types_any IS NOT NULL OR v_types_excl IS NOT NULL
         OR v_colors_on OR v_identity_on
         OR p_mv_min IS NOT NULL OR p_mv_max IS NOT NULL
         OR p_power_min IS NOT NULL OR p_power_max IS NOT NULL
         OR p_toughness_min IS NOT NULL OR p_toughness_max IS NOT NULL
         OR v_rarities IS NOT NULL OR v_sets IS NOT NULL OR v_formats IS NOT NULL;

  IF v_meta THEN
    v_unindexed := private.friend_list_unindexed_count(p_friend_user_id, p_list);
  END IF;

  RETURN QUERY
  WITH src AS (
    SELECT 'collection'::text AS sl, ucc.id AS rid, ucc.scryfall_id AS sid, ucc.quantity AS qty,
           ucc.is_foil AS foil, ucc.condition AS cond, ucc.language AS lng
      FROM user_card_collection ucc
     WHERE p_list = 'collection' AND ucc.user_id = p_friend_user_id AND ucc.is_deleted = false
    UNION ALL
    SELECT 'wishlist'::text, w.id, w.card_id, w.quantity, w.is_foil, w.condition, w.language
      FROM wishlists w
     WHERE p_list = 'wishlist' AND w.user_id = p_friend_user_id
    UNION ALL
    SELECT 'trade'::text, oft.id, ucc.scryfall_id, oft.quantity, ucc.is_foil, ucc.condition, ucc.language
      FROM open_for_trade oft
      JOIN user_card_collection ucc ON ucc.id = oft.user_card_id
     WHERE p_list = 'trade' AND oft.user_id = p_friend_user_id AND ucc.is_deleted = false
  ),
  keyed AS (
    SELECT s.sl, s.rid, s.sid, s.qty, s.foil, s.cond, s.lng,
           csi.name AS cname, csi.set_code AS cset, csi.rarity AS crarity,
           (CASE WHEN csi.status = 'ok' THEN '0' || lower(csi.name) ELSE '1' || s.sid END) COLLATE "C" AS skey
      FROM src s
      LEFT JOIN card_search_index csi ON csi.scryfall_id = s.sid
     WHERE (NOT v_foil_only OR s.foil)
       AND (v_conditions IS NULL OR s.cond = ANY (v_conditions))
       AND (v_languages  IS NULL OR s.lng  = ANY (v_languages))
       AND (NOT v_meta OR (
             csi.status = 'ok'
         AND (v_name IS NULL
              OR (v_name_pat IS NOT NULL AND csi.search_name LIKE v_name_pat)
              OR (v_name_pat IS NULL AND v_name = ANY (csi.face_names)))
         AND (v_oracle_pat IS NULL OR csi.oracle_text ILIKE v_oracle_pat)
         AND (v_types_all  IS NULL OR csi.type_line ILIKE ALL (v_types_all))
         AND (v_types_any  IS NULL OR csi.type_line ILIKE ANY (v_types_any))
         AND (v_types_excl IS NULL OR NOT (csi.type_line ILIKE ANY (v_types_excl)))
         AND (NOT v_colors_on OR CASE v_colors_mode
               WHEN 'any_of'  THEN (csi.color_mask & v_colors_mask) <> 0 OR (v_colors_c AND csi.color_mask = 0)
               WHEN 'at_most' THEN CASE WHEN v_colors_mask = 0 THEN csi.color_mask = 0
                                        ELSE (csi.color_mask & ~v_colors_mask) = 0 END
               WHEN 'exactly' THEN CASE WHEN v_colors_mask = 0 THEN csi.color_mask = 0
                                        ELSE csi.color_mask = v_colors_mask END
               ELSE CASE WHEN v_colors_mask = 0 THEN csi.color_mask = 0
                         ELSE (csi.color_mask & v_colors_mask) = v_colors_mask END
             END)
         AND (NOT v_identity_on OR CASE v_identity_mode
               WHEN 'any_of'  THEN (csi.identity_mask & v_identity_mask) <> 0 OR (v_identity_c AND csi.identity_mask = 0)
               WHEN 'at_most' THEN CASE WHEN v_identity_mask = 0 THEN csi.identity_mask = 0
                                        ELSE (csi.identity_mask & ~v_identity_mask) = 0 END
               WHEN 'exactly' THEN CASE WHEN v_identity_mask = 0 THEN csi.identity_mask = 0
                                        ELSE csi.identity_mask = v_identity_mask END
               ELSE CASE WHEN v_identity_mask = 0 THEN csi.identity_mask = 0
                         ELSE (csi.identity_mask & v_identity_mask) = v_identity_mask END
             END)
         AND (p_mv_min IS NULL OR csi.mana_value >= p_mv_min)
         AND (p_mv_max IS NULL OR csi.mana_value <= p_mv_max)
         AND (p_power_min IS NULL OR csi.power_num >= p_power_min)
         AND (p_power_max IS NULL OR csi.power_num <= p_power_max)
         AND (p_toughness_min IS NULL OR csi.toughness_num >= p_toughness_min)
         AND (p_toughness_max IS NULL OR csi.toughness_num <= p_toughness_max)
         AND (v_rarities IS NULL OR csi.rarity = ANY (v_rarities))
         AND (v_sets     IS NULL OR csi.set_code = ANY (v_sets))
         AND (v_formats  IS NULL OR EXISTS (
               SELECT 1 FROM unnest(v_formats) AS fmt
                WHERE (COALESCE(csi.legalities ->> fmt, '') = 'legal') = v_legal))
       ))
  ),
  page AS (
    SELECT k.*
      FROM keyed k
     WHERE p_after_sort_key IS NULL
        OR (k.skey, k.rid) > (p_after_sort_key COLLATE "C", p_after_row_id)
     ORDER BY k.skey, k.rid
     LIMIT v_limit + 1
  )
  SELECT p.sl, p.rid, p.sid, p.qty, p.foil, p.cond, p.lng, p.cname, p.cset, p.crarity,
         p.skey::text, (count(*) OVER ()) > v_limit, v_unindexed
    FROM page p
   ORDER BY p.skey, p.rid
   LIMIT v_limit;
END;
$$;

REVOKE EXECUTE ON FUNCTION public.search_friend_cards(
  uuid, text, text, boolean, text, text[], text[], text[], text[], text, text[], text,
  integer, integer, integer, integer, integer, integer, text[], text[], text[], boolean,
  text[], text[], boolean, integer, text, uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.search_friend_cards(
  uuid, text, text, boolean, text, text[], text[], text[], text[], text, text[], text,
  integer, integer, integer, integer, integer, integer, text[], text[], text[], boolean,
  text[], text[], boolean, integer, text, uuid) TO authenticated;
