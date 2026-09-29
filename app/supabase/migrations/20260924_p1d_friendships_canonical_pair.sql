-- ============================================================================
-- 20260924_p1d_friendships_canonical_pair.sql   (friends audit F-07)
--
-- NOT APPLIED. Apply after 20260924_p0 (the P0 guard trigger is what lets accept_invite
-- keep writing ACCEPTED rows while client inserts are pinned to PENDING).
--
-- 1. One friendship row per unordered pair: UNIQUE on
--    (LEAST(user_id_1, user_id_2), GREATEST(user_id_1, user_id_2)).
--    friendships_pair_unique (ordered) stays: harmless, and older function versions name it.
--    Read-only duplicate check on 2026-09-24: 2 rows, 0 reverse-direction pairs -> no
--    dedupe needed, not blocked. The guard below aborts the migration (no data touched) if
--    duplicates appeared since then; in that case STOP and ask for dedupe approval (D15).
-- 2. accept_invite becomes one atomic INSERT ... ON CONFLICT on that index (the old
--    SELECT-then-INSERT raced when two users redeemed each other's codes concurrently).
--    Same signature, return shape, error codes and grants; behaviour unchanged otherwise:
--    an existing row in either direction is flipped to ACCEPTED, else (inviter, me) is inserted.
--
-- Client-visible change (shipped clients keep working): a friend request toward someone
-- who already has a row with you in EITHER direction (pending or accepted) now fails with
-- HTTP 409 / SQLSTATE 23505 on friendships_canonical_pair_uidx instead of creating a
-- reverse duplicate. Shipped Android already shows its generic "couldn't send" toast for
-- any non-2xx; the P7 client change maps 409 to "Already friends / request pending".
-- No insert-to-accept trigger: it would return 201 with an empty body for a request that
-- silently became a friendship, and the client would cache a phantom outgoing request.
-- ============================================================================

DO $$
DECLARE
  v_dupes bigint;
BEGIN
  SELECT count(*) INTO v_dupes
    FROM (SELECT 1
            FROM public.friendships
           GROUP BY LEAST(user_id_1, user_id_2), GREATEST(user_id_1, user_id_2)
          HAVING count(*) > 1) d;
  IF v_dupes > 0 THEN
    RAISE EXCEPTION 'F-07 BLOCKED: % duplicate friendship pair(s) exist; dedupe needs approval', v_dupes;
  END IF;
END;
$$;

CREATE UNIQUE INDEX IF NOT EXISTS friendships_canonical_pair_uidx
  ON public.friendships USING btree (LEAST(user_id_1, user_id_2), GREATEST(user_id_1, user_id_2));

CREATE OR REPLACE FUNCTION public.accept_invite(p_referral_code text)
 RETURNS TABLE(inviter_id uuid, inviter_nickname text)
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path = public
AS $function$
DECLARE
  v_inviter_id       UUID;
  v_inviter_nickname TEXT;
  v_me               UUID := auth.uid();
BEGIN
  IF v_me IS NULL THEN
    RAISE EXCEPTION 'NOT_AUTHENTICATED' USING ERRCODE = 'P0000';
  END IF;

  SELECT id, nickname
    INTO v_inviter_id, v_inviter_nickname
    FROM user_profiles
   WHERE referral_code = p_referral_code;

  IF v_inviter_id IS NULL THEN
    RAISE EXCEPTION 'INVALID_CODE' USING ERRCODE = 'P0001';
  END IF;

  IF v_inviter_id = v_me THEN
    RAISE EXCEPTION 'SELF_INVITE' USING ERRCODE = 'P0002';
  END IF;

  -- One row per unordered pair: reuse an existing row in either direction.
  BEGIN
    INSERT INTO friendships (user_id_1, user_id_2, status)
    VALUES (v_inviter_id, v_me, 'ACCEPTED')
    ON CONFLICT ((LEAST(user_id_1, user_id_2)), (GREATEST(user_id_1, user_id_2)))
    DO UPDATE SET status = 'ACCEPTED'
          WHERE friendships.status IS DISTINCT FROM 'ACCEPTED';
  EXCEPTION WHEN unique_violation THEN
    -- A concurrent same-direction insert can trip the ordered friendships_pair_unique
    -- (not the ON CONFLICT arbiter); the row exists now, so just accept it.
    UPDATE friendships
       SET status = 'ACCEPTED'
     WHERE LEAST(user_id_1, user_id_2) = LEAST(v_inviter_id, v_me)
       AND GREATEST(user_id_1, user_id_2) = GREATEST(v_inviter_id, v_me)
       AND status IS DISTINCT FROM 'ACCEPTED';
  END;

  RETURN QUERY SELECT v_inviter_id, v_inviter_nickname;
END;
$function$;

REVOKE ALL ON FUNCTION public.accept_invite(text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.accept_invite(text) TO authenticated, service_role;

-- Post-apply checks (read-only):
--   SELECT indexdef FROM pg_indexes WHERE indexname = 'friendships_canonical_pair_uidx';
--   SELECT proacl FROM pg_proc WHERE proname = 'accept_invite';
-- Behavioural (rolled back, two throwaway users A/B with complete profiles):
--   A inserts A->B (PENDING); B inserts B->A -> 23505 on friendships_canonical_pair_uidx;
--   B redeems A's code -> the same row flips to ACCEPTED, count(*) for the pair stays 1.
