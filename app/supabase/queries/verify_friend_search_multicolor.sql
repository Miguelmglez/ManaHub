-- Run after the migration as an authenticated, profile-complete test user with an accepted friend.
-- This read-only check exercises the RPC and its keyset cursor; an absent friend fails explicitly.
DO $$
DECLARE
  v_friend uuid;
  v_list text;
  v_case record;
  v_row record;
  v_cursor_key text;
  v_cursor_id uuid;
  v_seen uuid[];
  v_page_count integer;
  v_has_more boolean;
  v_mask integer;
  v_selected_mask integer;
  v_requires_m boolean;
  v_matches boolean;
  v_expected integer;
BEGIN
  SELECT CASE WHEN f.user_id_1 = auth.uid() THEN f.user_id_2 ELSE f.user_id_1 END
    INTO v_friend
    FROM public.friendships f
   WHERE f.status = 'ACCEPTED'
     AND auth.uid() IN (f.user_id_1, f.user_id_2)
   LIMIT 1;

  IF v_friend IS NULL THEN
    RAISE EXCEPTION 'An accepted friend is required for this check';
  END IF;

  FOR v_list IN SELECT unnest(ARRAY['collection', 'wishlist', 'trade']) LOOP
    FOR v_case IN
      SELECT * FROM (VALUES
        ('colors',   ARRAY['M']::text[],       'at_least'),
        ('colors',   ARRAY['M']::text[],       'at_most'),
        ('colors',   ARRAY['M']::text[],       'exactly'),
        ('colors',   ARRAY['M']::text[],       'any_of'),
        ('colors',   ARRAY['M','W']::text[],   'at_least'),
        ('colors',   ARRAY['M','W']::text[],   'at_most'),
        ('colors',   ARRAY['M','W']::text[],   'exactly'),
        ('colors',   ARRAY['M','W']::text[],   'any_of'),
        ('colors',   ARRAY['M','W','U']::text[],'exactly'),
        ('identity', ARRAY['M']::text[],       'at_least'),
        ('identity', ARRAY['M']::text[],       'at_most'),
        ('identity', ARRAY['M']::text[],       'exactly'),
        ('identity', ARRAY['M']::text[],       'any_of'),
        ('identity', ARRAY['M','W']::text[],   'at_least'),
        ('identity', ARRAY['M','W']::text[],   'at_most'),
        ('identity', ARRAY['M','W']::text[],   'exactly'),
        ('identity', ARRAY['M','W']::text[],   'any_of'),
        ('identity', ARRAY['M','W','U']::text[],'exactly'),
        ('colors',   ARRAY['W']::text[],       'at_least')
      ) AS cases(field_name, tokens, mode_name)
    LOOP
      v_cursor_key := NULL;
      v_cursor_id := NULL;
      v_seen := '{}';
      LOOP
        v_page_count := 0;
        v_has_more := false;
        FOR v_row IN
          SELECT *
            FROM public.search_friend_cards(
              p_friend_user_id => v_friend,
              p_list => v_list,
              p_colors => CASE WHEN v_case.field_name = 'colors' THEN v_case.tokens END,
              p_colors_mode => CASE WHEN v_case.field_name = 'colors' THEN v_case.mode_name ELSE 'at_least' END,
              p_identity => CASE WHEN v_case.field_name = 'identity' THEN v_case.tokens END,
              p_identity_mode => CASE WHEN v_case.field_name = 'identity' THEN v_case.mode_name ELSE 'at_most' END,
              p_limit => 2,
              p_after_sort_key => v_cursor_key,
              p_after_row_id => v_cursor_id
            )
        LOOP
          IF v_row.row_id = ANY(v_seen) THEN
            RAISE EXCEPTION 'Duplicate row across pages: %, %, %', v_list, v_case.field_name, v_case.tokens;
          END IF;
          v_seen := array_append(v_seen, v_row.row_id);
          v_page_count := v_page_count + 1;
          v_has_more := v_row.has_more;
          v_cursor_key := v_row.sort_key;
          v_cursor_id := v_row.row_id;

          SELECT CASE WHEN v_case.field_name = 'colors' THEN c.color_mask ELSE c.identity_mask END
            INTO v_mask
            FROM public.card_search_index c
           WHERE c.scryfall_id = v_row.scryfall_id AND c.status = 'ok';
          IF v_mask IS NULL THEN
            RAISE EXCEPTION 'Filtered search returned unindexed metadata';
          END IF;

          v_selected_mask := CASE
            WHEN 'W' = ANY(v_case.tokens) AND 'U' = ANY(v_case.tokens) THEN 3
            WHEN 'W' = ANY(v_case.tokens) THEN 1
            ELSE 0
          END;
          v_requires_m := 'M' = ANY(v_case.tokens);
          v_matches := (NOT v_requires_m OR (v_mask & (v_mask - 1)) <> 0)
            AND (v_selected_mask = 0 OR CASE v_case.mode_name
              WHEN 'any_of' THEN (v_mask & v_selected_mask) <> 0
              WHEN 'at_most' THEN (v_mask & ~v_selected_mask) = 0
              WHEN 'exactly' THEN v_mask = v_selected_mask
              ELSE (v_mask & v_selected_mask) = v_selected_mask
            END);
          IF NOT v_matches THEN
            RAISE EXCEPTION 'Mask mismatch: %, %, %, %, %',
              v_list, v_case.field_name, v_case.tokens, v_case.mode_name, v_mask;
          END IF;
        END LOOP;
        EXIT WHEN NOT v_has_more;
        IF v_page_count = 0 OR cardinality(v_seen) > 10000 THEN
          RAISE EXCEPTION 'Pagination did not advance';
        END IF;
      END LOOP;

      SELECT count(*)::integer
        INTO v_expected
        FROM (
          SELECT ucc.scryfall_id AS sid
            FROM public.user_card_collection ucc
           WHERE v_list = 'collection' AND ucc.user_id = v_friend AND NOT ucc.is_deleted
          UNION ALL
          SELECT w.card_id FROM public.wishlists w
           WHERE v_list = 'wishlist' AND w.user_id = v_friend
          UNION ALL
          SELECT ucc.scryfall_id FROM public.open_for_trade oft
            JOIN public.user_card_collection ucc ON ucc.id = oft.user_card_id
           WHERE v_list = 'trade' AND oft.user_id = v_friend AND NOT ucc.is_deleted
        ) src
        JOIN public.card_search_index c ON c.scryfall_id = src.sid AND c.status = 'ok'
       WHERE (
         (CASE WHEN v_case.field_name = 'colors' THEN c.color_mask ELSE c.identity_mask END
         & (CASE WHEN v_case.field_name = 'colors' THEN c.color_mask ELSE c.identity_mask END - 1)) <> 0
         OR NOT ('M' = ANY(v_case.tokens))
       )
       AND (
         (NOT ('W' = ANY(v_case.tokens)) AND NOT ('U' = ANY(v_case.tokens)))
         OR CASE v_case.mode_name
           WHEN 'any_of' THEN
             (CASE WHEN v_case.field_name = 'colors' THEN c.color_mask ELSE c.identity_mask END
              & CASE WHEN 'U' = ANY(v_case.tokens) THEN 3 ELSE 1 END) <> 0
           WHEN 'at_most' THEN
             (CASE WHEN v_case.field_name = 'colors' THEN c.color_mask ELSE c.identity_mask END
              & ~CASE WHEN 'U' = ANY(v_case.tokens) THEN 3 ELSE 1 END) = 0
           WHEN 'exactly' THEN
             (CASE WHEN v_case.field_name = 'colors' THEN c.color_mask ELSE c.identity_mask END)
               = CASE WHEN 'U' = ANY(v_case.tokens) THEN 3 ELSE 1 END
           ELSE
             (CASE WHEN v_case.field_name = 'colors' THEN c.color_mask ELSE c.identity_mask END
              & CASE WHEN 'U' = ANY(v_case.tokens) THEN 3 ELSE 1 END)
               = CASE WHEN 'U' = ANY(v_case.tokens) THEN 3 ELSE 1 END
         END
       );
      IF cardinality(v_seen) <> v_expected THEN
        RAISE EXCEPTION 'Count mismatch: %, %, %, %, returned %, expected %',
          v_list, v_case.field_name, v_case.tokens, v_case.mode_name,
          cardinality(v_seen), v_expected;
      END IF;
      IF v_case.tokens = ARRAY['M','W']::text[]
         AND v_case.mode_name = 'exactly'
         AND cardinality(v_seen) <> 0 THEN
        RAISE EXCEPTION 'Contradictory M + exactly W returned rows';
      END IF;
    END LOOP;

    -- An old unfiltered call must still return every row, including rows without metadata.
    v_seen := '{}';
    v_cursor_key := NULL;
    v_cursor_id := NULL;
    LOOP
      v_has_more := false;
      v_page_count := 0;
      FOR v_row IN
        SELECT * FROM public.search_friend_cards(
          p_friend_user_id => v_friend, p_list => v_list, p_limit => 2,
          p_after_sort_key => v_cursor_key, p_after_row_id => v_cursor_id
        )
      LOOP
        IF v_row.row_id = ANY(v_seen) THEN
          RAISE EXCEPTION 'Duplicate row in unfiltered browse';
        END IF;
        v_seen := array_append(v_seen, v_row.row_id);
        v_page_count := v_page_count + 1;
        v_has_more := v_row.has_more;
        v_cursor_key := v_row.sort_key;
        v_cursor_id := v_row.row_id;
      END LOOP;
      EXIT WHEN NOT v_has_more;
      IF v_page_count = 0 OR cardinality(v_seen) > 10000 THEN
        RAISE EXCEPTION 'Unfiltered pagination did not advance';
      END IF;
    END LOOP;
    SELECT count(*)::integer INTO v_expected
      FROM (
        SELECT ucc.id FROM public.user_card_collection ucc
         WHERE v_list = 'collection' AND ucc.user_id = v_friend AND NOT ucc.is_deleted
        UNION ALL
        SELECT w.id FROM public.wishlists w
         WHERE v_list = 'wishlist' AND w.user_id = v_friend
        UNION ALL
        SELECT oft.id FROM public.open_for_trade oft
          JOIN public.user_card_collection ucc ON ucc.id = oft.user_card_id
         WHERE v_list = 'trade' AND oft.user_id = v_friend AND NOT ucc.is_deleted
      ) src;
    IF cardinality(v_seen) <> v_expected THEN
      RAISE EXCEPTION 'Unfiltered count mismatch: %, returned %, expected %',
        v_list, cardinality(v_seen), v_expected;
    END IF;
  END LOOP;
END;
$$;
