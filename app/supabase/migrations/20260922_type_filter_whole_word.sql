-- ============================================================================
-- 20260922_type_filter_whole_word.sql
--
-- search_friend_cards type filters (p_types_all / p_types_any / p_types_exclude)
-- match WHOLE types only: 'Rat' never matches 'Pirate', 'Ant' never 'Giant'.
-- Same spec as the Kotlin matcher: lowercase the type line, turn the em dash and
-- '//' face separators into spaces, collapse whitespace, pad with one space each
-- side, then LIKE '% <term> %'. Hyphenated types stay one token; multi-word types
-- ("Time Lord") still match as a space-bounded phrase. type_line already joins
-- every DFC face, so both faces are covered.
--
-- Type elements must match ^[A-Za-z][A-Za-z' -]{0,39}$ (INVALID_ARGUMENT /
-- array_element_invalid). Signature and return type unchanged.
--
-- Applied via the Supabase MCP `apply_migration` tool against project
-- uimogilwuixgkgfcfmyb; this file mirrors that change.
-- ============================================================================

ALTER TABLE public.card_search_index
  ADD COLUMN IF NOT EXISTS type_line_norm text GENERATED ALWAYS AS (
    ' ' || btrim(regexp_replace(
             replace(replace(lower(COALESCE(type_line, '')), '—', ' '), '//', ' '),
             '\s+', ' ', 'g')) || ' '
  ) STORED;

CREATE OR REPLACE FUNCTION public.search_friend_cards(
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

  IF EXISTS (
    SELECT 1 FROM unnest(
      COALESCE(p_types_all, '{}') || COALESCE(p_types_any, '{}') || COALESCE(p_types_exclude, '{}')) AS e
    WHERE e !~ '^[A-Za-z][A-Za-z'' -]{0,39}$'
  ) THEN
    RAISE EXCEPTION 'INVALID_ARGUMENT' USING ERRCODE = '22023', DETAIL = 'array_element_invalid';
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

  SELECT NULLIF(array_agg('% ' || replace(replace(replace(lower(btrim(e)), '\', '\\'), '%', '\%'), '_', '\_') || ' %'), '{}')
    INTO v_types_all FROM unnest(p_types_all) AS e;
  SELECT NULLIF(array_agg('% ' || replace(replace(replace(lower(btrim(e)), '\', '\\'), '%', '\%'), '_', '\_') || ' %'), '{}')
    INTO v_types_any FROM unnest(p_types_any) AS e;
  SELECT NULLIF(array_agg('% ' || replace(replace(replace(lower(btrim(e)), '\', '\\'), '%', '\%'), '_', '\_') || ' %'), '{}')
    INTO v_types_excl FROM unnest(p_types_exclude) AS e;

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
         AND (v_types_all  IS NULL OR csi.type_line_norm LIKE ALL (v_types_all))
         AND (v_types_any  IS NULL OR csi.type_line_norm LIKE ANY (v_types_any))
         AND (v_types_excl IS NULL OR NOT (csi.type_line_norm LIKE ANY (v_types_excl)))
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
