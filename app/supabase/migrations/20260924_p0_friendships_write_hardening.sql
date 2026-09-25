-- ============================================================================
-- 20260924_p0_friendships_write_hardening.sql   (P0 SECURITY HOTFIX — apply FIRST)
--
-- NOT APPLIED. Written 2026-09-24 against the live schema of project
-- uimogilwuixgkgfcfmyb (read-only inspection); review, then apply.
--
-- Closes two paths that let a client forge an ACCEPTED friendship (friends audit F-01):
--   1. INSERT: the policy never pinned `status`, so
--      POST /friendships {"user_id_1":"<me>","user_id_2":"<victim>","status":"ACCEPTED"}
--      created a silent friendship (fn_notify_friendships notifies user_id_1 = the attacker,
--      and the self-notify guard drops it, so the victim is never told).
--   2. UPDATE: `authenticated` holds UPDATE on every column. The recipient of ANY pending
--      request (e.g. one sent by their own second account) could
--      PATCH {"user_id_1":"<victim>","status":"ACCEPTED"} — USING and WITH CHECK only test
--      user_id_2 — and turn it into a friendship with an arbitrary victim.
--
-- Fix, all additive and compatible with shipped clients:
--   * INSERT WITH CHECK adds status = 'PENDING'.
--   * UPDATE WITH CHECK becomes status = 'ACCEPTED'. The old 'REJECTED' branch was dead:
--     friendships_status_check only allows PENDING/ACCEPTED, and clients reject with DELETE.
--   * BEFORE INSERT OR UPDATE trigger (defence in depth; it survives a future blanket GRANT,
--     which a column-level REVOKE would not — see the user_profiles 2026-08-04 regression):
--       - INSERT by a client role: id/status/created_at are forced server-side.
--       - UPDATE by a client role: id, user_id_1, user_id_2 and created_at are immutable.
--     SECURITY DEFINER callers (accept_invite runs as postgres) and service_role are
--     exempt: current_user is then not a client role.
--   * All four policies scoped TO authenticated; anon loses write/TRUNCATE/TRIGGER grants.
--
-- Client paths verified compatible (2026-09-24):
--   FriendshipClient.sendFriendRequest   POST  {user_id_1, user_id_2}   (no status/id/created_at)
--   FriendshipClient.updateFriendshipStatus PATCH {status:"ACCEPTED"}   (accept)
--   FriendshipClient.deleteFriendship    DELETE (reject, cancel, remove)
--   accept_invite (SECURITY DEFINER, owner postgres) inserts/updates ACCEPTED rows.
--   No Edge Function writes friendships. Web uses the same FriendRemoteDataSource.
--
-- Idempotent: ALTER POLICY / CREATE OR REPLACE / DROP TRIGGER IF EXISTS / REVOKE.
-- Run get_advisors (security + performance) after applying.
-- ============================================================================

ALTER POLICY "Users can insert friend requests" ON public.friendships
  TO authenticated
  WITH CHECK (
    (SELECT auth.uid()) = user_id_1
    AND status = 'PENDING'
    AND public.is_profile_complete()
  );

ALTER POLICY "Users can accept or reject received requests" ON public.friendships
  TO authenticated
  USING (
    (SELECT auth.uid()) = user_id_2
    AND status = 'PENDING'
    AND public.is_profile_complete()
  )
  WITH CHECK (
    (SELECT auth.uid()) = user_id_2
    AND status = 'ACCEPTED'
    AND public.is_profile_complete()
  );

ALTER POLICY "Users can see their own friendships" ON public.friendships
  TO authenticated;

ALTER POLICY "Users can delete their friendships" ON public.friendships
  TO authenticated;

-- INVOKER on purpose: current_user must be the caller's role, not the function owner.
CREATE OR REPLACE FUNCTION public.friendships_guard_client_write()
RETURNS trigger
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = public
AS $$
BEGIN
  -- Only PostgREST client roles are constrained; DEFINER RPCs and service_role pass through.
  IF current_user NOT IN ('authenticated', 'anon') THEN
    RETURN NEW;
  END IF;

  IF TG_OP = 'INSERT' THEN
    NEW.id         := gen_random_uuid();
    NEW.status     := 'PENDING';
    NEW.created_at := now();
  ELSIF NEW.id IS DISTINCT FROM OLD.id
     OR NEW.user_id_1 IS DISTINCT FROM OLD.user_id_1
     OR NEW.user_id_2 IS DISTINCT FROM OLD.user_id_2
     OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
    RAISE EXCEPTION 'FRIENDSHIP_IMMUTABLE_COLUMNS' USING ERRCODE = '42501';
  END IF;

  RETURN NEW;
END;
$$;

-- Trigger functions need no EXECUTE grant at fire time; nobody may call it directly.
REVOKE ALL ON FUNCTION public.friendships_guard_client_write() FROM PUBLIC, anon, authenticated;

DROP TRIGGER IF EXISTS trg_friendships_guard_client_write ON public.friendships;
CREATE TRIGGER trg_friendships_guard_client_write
  BEFORE INSERT OR UPDATE ON public.friendships
  FOR EACH ROW EXECUTE FUNCTION public.friendships_guard_client_write();

-- anon never has a uid, so no policy can pass for it; drop the grants outright.
REVOKE INSERT, UPDATE, DELETE, TRUNCATE, TRIGGER, REFERENCES ON public.friendships FROM anon;
-- Not reachable through PostgREST, but never needed by a client.
REVOKE TRUNCATE, TRIGGER, REFERENCES ON public.friendships FROM authenticated;


-- ----------------------------------------------------------------------------
-- Post-apply checks (read-only):
--   SELECT polname, pg_get_expr(polwithcheck, polrelid)
--     FROM pg_policy WHERE polrelid = 'public.friendships'::regclass;
--   SELECT tgname FROM pg_trigger
--    WHERE tgrelid = 'public.friendships'::regclass AND NOT tgisinternal;
--   SELECT grantee, string_agg(privilege_type, ',') FROM information_schema.table_privileges
--    WHERE table_schema = 'public' AND table_name = 'friendships' GROUP BY grantee;
-- Behavioural check (rolled back): as an authenticated user with a complete profile,
--   INSERT ... status 'ACCEPTED'  -> row lands as PENDING;
--   UPDATE ... SET user_id_1 = <x> -> 42501 FRIENDSHIP_IMMUTABLE_COLUMNS;
--   UPDATE ... SET status = 'ACCEPTED' on a received PENDING row -> succeeds.
-- Forensics: app/supabase/queries/20260924_friendships_forensics_readonly.sql
-- ----------------------------------------------------------------------------
